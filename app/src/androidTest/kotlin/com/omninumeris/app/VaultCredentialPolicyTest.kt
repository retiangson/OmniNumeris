package com.omninumeris.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.application.CalculatorInsertionReview
import com.omninumeris.application.CalculatorInsertionSession
import com.omninumeris.application.InsertCalculatorResult
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.infrastructure.storage.protectedvault.ProtectedNotebookInsertion
import com.omninumeris.infrastructure.storage.protectedvault.ProtectedVault
import com.omninumeris.infrastructure.storage.protectedvault.VaultCredentialProbe
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

class VaultCredentialPolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun credentialBoundKeyRequiresDeviceAuthenticationAndExpires() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pin = InstrumentationRegistry.getArguments().getString("syntheticVaultPin").orEmpty()
        assumeTrue("Requires a disposable Android emulator with a synthetic PIN",
            Build.HARDWARE in setOf("ranchu", "goldfish") && pin.matches(Regex("[0-9]{6}")))
        val manager = context.getSystemService(KeyguardManager::class.java)
        assertTrue(manager.isDeviceSecure)
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        try {
            fun confirmCredential() {
                val ui = instrumentation.uiAutomation
                fun shell(command: String): String =
                    ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).bufferedReader().use { it.readText() }
                if (Build.VERSION.SDK_INT >= 30) {
                    check("verified successfully" in shell("locksettings verify --old $pin")) {
                        "Android did not verify the synthetic credential"
                    }
                    return
                }
                val request = requireNotNull(manager.createConfirmDeviceCredentialIntent("Vault test", "Confirm fixture PIN"))
                instrumentation.runOnMainSync { activity.startActivityForResult(request, 901) }
                fun waitForActivity(fragment: String) {
                    val deadline = SystemClock.uptimeMillis() + 12_000
                    do {
                        val activities = shell("dumpsys activity activities")
                        if (activities.lineSequence().any {
                                ("topResumedActivity=" in it || "mResumedActivity:" in it) && fragment in it
                            }) return
                        SystemClock.sleep(100)
                    } while (SystemClock.uptimeMillis() < deadline)
                    val focus = shell("dumpsys window").lineSequence().firstOrNull { "mCurrentFocus=" in it }
                    error("The expected activity did not resume: $fragment ($focus)")
                }
                waitForActivity("com.android.settings/.password.Confirm")
                val focusDeadline = SystemClock.uptimeMillis() + 12_000
                while (shell("dumpsys window").lineSequence().none {
                        "mCurrentFocus=" in it && "com.android.settings" in it && "Confirm" in it
                    }) {
                    check(SystemClock.uptimeMillis() < focusDeadline) { "Credential input window did not get focus" }
                    SystemClock.sleep(100)
                }
                SystemClock.sleep(500)
                shell("input keyevent ${pin.map { "KEYCODE_$it" }.joinToString(" ")} KEYCODE_ENTER")
                waitForActivity("${context.packageName}/.MainActivity")
            }
            val seconds = VaultCredentialProbe.VALIDITY_SECONDS
            val waitForExpiry = (seconds + 2L) * 1_000
            VaultCredentialProbe().use { probe ->
                val raw = ByteArray(32) { index -> index.toByte() }
                SystemClock.sleep(waitForExpiry)
                assertThrows(UserNotAuthenticatedException::class.java) { probe.wrap(raw) }
                confirmCredential()
                val wrapped = probe.wrap(raw)
                assertArrayEquals(raw, probe.unwrap(wrapped))
                SystemClock.sleep(waitForExpiry)
                assertThrows(UserNotAuthenticatedException::class.java) { probe.unwrap(wrapped) }
            }
            runBlocking {
                val vaultId = UUID.randomUUID()
                val vault = ProtectedVault.openWithDeviceCredential(context, vaultId, seconds) { true }
                try {
                    try {
                        ProtectedNotebookInsertion.create(vault, "fixture")
                        error("Protected notebook creation succeeded without a device credential")
                    } catch (_: UserNotAuthenticatedException) {
                    }
                    confirmCredential()
                    val notebookId = ProtectedNotebookInsertion.create(vault, "unchanged original")
                    val notes = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(notebookId)))
                    val writer = InsertCalculatorResult(notes)
                    val exact = Calculator.calculate(Calculation.Standard("1/3+1/6"))
                    val session = CalculatorInsertionSession(UUID.randomUUID(), notes.target(), exact)
                    val preview = (writer.preview(session) as CalculatorInsertionReview.Preview).proposal
                    val receipt = writer.approve(preview, session) as MathInsertionCommit.Inserted
                    assertEquals(listOf("1/2"), notes.activeResults())
                    SystemClock.sleep(waitForExpiry)
                    assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.Locked), writer.undo(receipt.undo))
                    assertEquals(null, vault.unlockWithLease(notebookId))
                    confirmCredential()
                    val fresh = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(notebookId)))
                    assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.NoEditableTarget),
                        InsertCalculatorResult(fresh).approve(preview, session))
                    assertEquals(MathInsertionUndoResult.Restored, InsertCalculatorResult(fresh).undo(receipt.undo))
                    assertEquals(emptyList<String>(), fresh.activeResults())
                    assertTrue(vault.search(notebookId, "unchanged original"))
                } finally {
                    vault.close()
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        .deleteEntry("omninumeris-credential-vault-v1-$vaultId")
                    assertTrue(File(context.noBackupFilesDir, "credential-protected-vault/$vaultId").deleteRecursively())
                }
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }
}
