package com.omninumeris.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteFullException
import android.os.Bundle
import android.os.Process
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.omninumeris.application.CalculatorInsertionReview
import com.omninumeris.application.CalculatorInsertionSession
import com.omninumeris.application.InsertCalculatorResult
import com.omninumeris.application.InsertVerifiedMath
import com.omninumeris.application.MathInputCandidate
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionDecision
import com.omninumeris.application.MathInsertionMode
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionSession
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.application.ReviewedMathInput
import com.omninumeris.application.SolveMath
import com.omninumeris.domain.math.MathParseResult
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.calculator.Calculation
import com.omninumeris.domain.calculator.Calculator
import com.omninumeris.infrastructure.storage.protectedvault.ProtectedNotebookInsertion
import com.omninumeris.infrastructure.storage.protectedvault.ProtectedNotebookSnapshot
import com.omninumeris.infrastructure.storage.protectedvault.ProtectedVault
import com.omninumeris.infrastructure.storage.protectedvault.VaultCommitStage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProtectedVaultTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun protectedCalculatorResultHonorsLockAndReadOnlyPolicy() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val vault = open(vaultId)
            try {
                val exact = Calculator.calculate(Calculation.Standard("1/3+1/6"))
                val lockedId = ProtectedNotebookInsertion.create(vault, "calculator note")
                val notes = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(lockedId)))
                val insertion = InsertCalculatorResult(notes)
                val session = CalculatorInsertionSession(UUID.randomUUID(), notes.target(), exact)
                val preview = (insertion.preview(session) as CalculatorInsertionReview.Preview).proposal
                val receipt = insertion.approve(preview, session) as MathInsertionCommit.Inserted
                assertEquals(listOf("1/2"), notes.activeResults())
                vault.lock(lockedId)
                assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.Locked), insertion.approve(preview, session))
                assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.Locked), insertion.undo(receipt.undo))

                val readOnlyId = ProtectedNotebookInsertion.create(vault, "read-only note", readOnly = true)
                val readOnly = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(readOnlyId)))
                val attempt = InsertCalculatorResult(readOnly)
                val deniedSession = CalculatorInsertionSession(UUID.randomUUID(), readOnly.target(), exact)
                val denied = (attempt.preview(deniedSession) as CalculatorInsertionReview.Preview).proposal
                assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.ReadOnly), attempt.approve(denied, deniedSession))
                assertEquals(emptyList<String>(), readOnly.activeResults())
                assertEquals(0L, vault.revision(readOnlyId))
            } finally {
                vault.close()
            }
        }
    }

    @Test
    fun protectedMathInsertionAndUndoRequireLiveUnlockSession() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val vault = open(vaultId)
            try {
                val id = ProtectedNotebookInsertion.create(vault, "original writing")
                val notes = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(id)))
                val insert = InsertVerifiedMath(notes)
                val reviewed = ReviewedMathInput(
                    (MathParser.parse("3+8") as MathParseResult.Parsed).input,
                    MathInputCandidate("3+8", "3+8", null),
                )
                val proof = SolveMath().solve(reviewed.input)
                val session = MathInsertionSession(UUID.randomUUID(), notes.target(), MathInsertionMode.Suggestion)
                val preview = (insert.onResult(session, reviewed, proof) as MathInsertionDecision.Preview).proposal
                vault.lock(id)
                assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.Locked), insert.approve(preview, session))
                val freshNotes = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(id)))
                val freshInsert = InsertVerifiedMath(freshNotes)
                assertEquals(MathInsertionCommit.Rejected(MathInsertionProblem.NoEditableTarget), freshInsert.approve(preview, session))
                val freshSession = session.copy(resultId = UUID.randomUUID(), target = freshNotes.target())
                val trusted = (freshInsert.onResult(freshSession, reviewed, proof) as MathInsertionDecision.Preview).proposal
                val receipt = freshInsert.approve(trusted, freshSession) as MathInsertionCommit.Inserted
                assertEquals(receipt, freshInsert.approve(trusted, freshSession))
                assertEquals(1L, vault.revision(id))
                assertEquals(listOf("11"), freshNotes.activeResults())
                vault.lock(id)
                assertEquals(MathInsertionUndoResult.Rejected(MathInsertionProblem.Locked), freshInsert.undo(receipt.undo))
                assertEquals(null, freshNotes.activeResults())
                val reopened = ProtectedNotebookInsertion(vault, requireNotNull(vault.unlockWithLease(id)))
                val undo = InsertVerifiedMath(reopened)
                assertEquals(MathInsertionUndoResult.Restored, undo.undo(receipt.undo))
                assertEquals(MathInsertionUndoResult.Restored, undo.undo(receipt.undo))
                assertEquals(2L, vault.revision(id))
                assertTrue(vault.search(id, "original writing"))
                assertEquals(emptyList<String>(), reopened.activeResults())
            } finally {
                vault.close()
            }
        }
    }

    @Test
    fun lockInvalidatesPendingWritesEvenAcrossReauthentication() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val vault = open(vaultId)
            try {
                val notebook = vault.create(snapshot("keep original"))
                val other = vault.create(snapshot("separate scope"))
                val stale = requireNotNull(vault.unlockWithLease(notebook))
                val independent = requireNotNull(vault.unlockWithLease(other))
                assertEquals(1L, vault.save(stale, 0, snapshot("keep original plus insertion")))

                vault.lock(notebook)
                expect<SecurityException> { vault.save(stale, 1, snapshot("stale undo")) }
                assertEquals(1L, vault.save(independent, 0, snapshot("separate scope changed")))
                val current = requireNotNull(vault.unlockWithLease(notebook))
                expect<SecurityException> { vault.save(stale, 1, snapshot("stale undo")) }
                assertTrue(vault.search(notebook, "plus insertion"))
                expect<IllegalStateException> { vault.save(current, 0, snapshot("stale revision")) }
                assertEquals(2L, vault.save(current, 1, snapshot("keep original")))
                assertTrue(vault.search(notebook, "keep original"))
                assertFalse(vault.search(notebook, "plus insertion"))

                vault.lockAll()
                expect<SecurityException> { vault.save(current, 2, snapshot("after lock all")) }
                expect<SecurityException> { vault.save(independent, 1, snapshot("after lock all")) }
            } finally {
                vault.close()
            }
        }
    }

    @Test
    fun locksCoverEveryProjectionAndInvalidateInFlightAuthorization() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val entered = CompletableDeferred<Unit>()
            val approved = CompletableDeferred<Boolean>()
            val vault = ProtectedVault.open(context, vaultId, authorize = {
                entered.complete(Unit)
                approved.await()
            })
            try {
                val id = vault.create(snapshot("amber"))
                val other = vault.create(snapshot("silver"))
                assertLocked(vault, id)
                val pending = async { vault.unlock(id) }
                entered.await()
                vault.lock(id)
                approved.complete(true)
                assertFalse(pending.await())
                assertLocked(vault, id)
                assertTrue(vault.unlock(id))
                assertTrue(vault.search(id, "amber"))
                assertFalse(vault.search(id, "silver"))
                assertArrayEquals(byteArrayOf(1, 2, 3), vault.thumbnail(id))
                assertArrayEquals("amber".toByteArray(), vault.inferenceInput(id))
                assertLocked(vault, other)
                vault.lockAll()
                assertLocked(vault, id)
            } finally {
                vault.close()
            }
        }
    }

    @Test
    fun tamperedBlobFailsAuthenticationAndSaveRequiresCurrentRevision() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val vault = open(vaultId)
            try {
                val id = vault.create(snapshot("first revision"))
                assertTrue(vault.unlock(id))
                assertEquals(1L, vault.save(id, 0, snapshot("second revision")))
                expect<IllegalStateException> { vault.save(id, 0, snapshot("stale revision")) }
                assertTrue(vault.search(id, "second revision"))
                val committed = blobs(vaultId).listFiles()!!.single()
                val original = committed.readBytes()
                val damaged = original.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
                committed.writeBytes(damaged)
                expect<SecurityException> { vault.inferenceInput(id) }
                assertArrayEquals(damaged, committed.readBytes())
                committed.writeBytes(original)
                assertTrue(vault.search(id, "second revision"))
            } finally {
                vault.close()
            }
        }
    }

    @Test
    fun backupAuthenticatesManifestAndRestoreNeverOverwrites() = runBlocking<Unit> {
        withCleanVaultId { sourceId ->
            withCleanVaultId { destinationId ->
                val source = open(sourceId)
                val destination = open(destinationId)
                val password = "prototype passphrase".toCharArray()
                try {
                    val id = source.create(snapshot("private test content"))
                    assertTrue(source.unlock(id))
                    source.save(id, 0, snapshot("portable revision"))
                    val archive = source.backup(id, password)
                    val damaged = archive.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
                    expect<SecurityException> { destination.restore(damaged, password) }
                    expect<SecurityException> { destination.restore(archive, "incorrect passphrase".toCharArray()) }
                    expect<SecurityException> { destination.restore(archive + byteArrayOf(0), password) }
                    expect<IllegalArgumentException> { destination.restore(traversalZip(), password) }
                    expect<IllegalArgumentException> { destination.restore(ByteArray(2_000_000), password) }
                    assertTrue(blobs(destinationId).listFiles()!!.isEmpty())
                    assertEquals(id, destination.restore(archive, password))
                    assertLocked(destination, id)
                    assertTrue(destination.unlock(id))
                    assertEquals(1L, destination.revision(id))
                    assertTrue(destination.search(id, "portable revision"))
                    destination.save(id, 1, snapshot("newer local revision"))
                    expect<IllegalStateException> { destination.restore(archive, password) }
                    assertTrue(destination.search(id, "newer local revision"))
                } finally {
                    password.fill('\u0000')
                    destination.close()
                    source.close()
                }
            }
        }
    }

    @Test
    fun interruptedWriteAndRestoreLeaveCommittedDataAndCollectOrphans() = runBlocking<Unit> {
        withCleanVaultId { sourceId ->
            withCleanVaultId { destinationId ->
                var fault: VaultCommitStage? = null
                val inject: (VaultCommitStage) -> Unit = { stage ->
                    if (stage == fault) {
                        if (stage == VaultCommitStage.BeforeBlobPublish) throw SQLiteFullException("Simulated full disk")
                        throw IOException("Simulated interruption before Room commit")
                    }
                }
                val source = open(sourceId, inject)
                val destination = open(destinationId, inject)
                val password = "recovery passphrase".toCharArray()
                val id: UUID
                val archive: ByteArray
                try {
                    id = source.create(snapshot("committed before interruption"))
                    assertTrue(source.unlock(id))
                    archive = source.backup(id, password)
                    fault = VaultCommitStage.BeforeBlobPublish
                    expect<SQLiteFullException> { source.save(id, 0, snapshot("failed by full disk")) }
                    fault = VaultCommitStage.BeforeMetadataCommit
                    expect<IOException> { source.save(id, 0, snapshot("failed after publish")) }
                    expect<IOException> { destination.restore(archive, password) }
                    assertEquals(0L, source.revision(id))
                    assertTrue(source.search(id, "committed before interruption"))
                    assertEquals(3, blobs(sourceId).listFiles()!!.size)
                    assertEquals(1, blobs(destinationId).listFiles()!!.size)
                } finally {
                    source.close()
                    destination.close()
                }
                val reopenedSource = open(sourceId)
                val reopenedDestination = open(destinationId)
                try {
                    assertEquals(1, blobs(sourceId).listFiles()!!.size)
                    assertTrue(blobs(destinationId).listFiles()!!.isEmpty())
                    assertLocked(reopenedSource, id)
                    assertTrue(reopenedSource.unlock(id))
                    assertTrue(reopenedSource.search(id, "committed before interruption"))
                    assertEquals(id, reopenedDestination.restore(archive, password))
                    assertTrue(reopenedDestination.unlock(id))
                    assertTrue(reopenedDestination.search(id, "committed before interruption"))
                } finally {
                    password.fill('\u0000')
                    reopenedDestination.close()
                    reopenedSource.close()
                }
            }
        }
    }

    @Test
    fun upgradesPriorSchemaWithCiphertextAndRelationshipsPreserved() = runBlocking<Unit> {
        withCleanVaultId { vaultId ->
            val vault = open(vaultId)
            val id = try {
                vault.create(snapshot("migration fixture"))
            } finally {
                vault.close()
            }
            val dbFile = File(root(vaultId), "vault.db")
            val old = File(root(vaultId), "fixture-v1.db")
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { current ->
                SQLiteDatabase.openOrCreateDatabase(old, null).use { fixture ->
                    fixture.setForeignKeyConstraintsEnabled(true)
                    instrumentation.context.assets.open("vault/schema-v1.sql").bufferedReader().useLines { lines ->
                        lines.filter(String::isNotBlank).forEach { fixture.execSQL(it) }
                    }
                    current.rawQuery("SELECT id, wrappedKey FROM notebook", null).use {
                        assertTrue(it.moveToFirst())
                        fixture.execSQL("INSERT INTO notebook VALUES (?, ?)", arrayOf(it.getString(0), it.getBlob(1)))
                    }
                    current.rawQuery("SELECT notebookId, blobId FROM blob_pointer", null).use {
                        assertTrue(it.moveToFirst())
                        fixture.execSQL("INSERT INTO blob_pointer VALUES (?, ?)", arrayOf(it.getString(0), it.getString(1)))
                    }
                    fixture.version = 1
                }
            }
            assertTrue(dbFile.delete())
            assertTrue(old.renameTo(dbFile))
            val migrated = open(vaultId)
            try {
                assertTrue(migrated.unlock(id))
                assertEquals(0L, migrated.revision(id))
                assertTrue(migrated.search(id, "migration fixture"))
                assertEquals(1L, migrated.save(id, 0, snapshot("after migration")))
                assertTrue(migrated.search(id, "after migration"))
            } finally {
                migrated.close()
            }
        }
    }

    @Test
    fun recoversAfterProcessDeathBetweenBlobAndMetadataCommit() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("vaultCrashPhase")
        assumeTrue(phase != null)
        val vaultId = UUID.fromString(requireNotNull(arguments.getString("vaultCrashId")))
        val fixture = File(root(vaultId), "crash-fixture.txt")
        if (phase == "publish") {
            check(!root(vaultId).exists())
            var killOnPublish = false
            val vault = open(vaultId) { stage ->
                if (killOnPublish && stage == VaultCommitStage.BeforeMetadataCommit) {
                    Process.killProcess(Process.myPid())
                    error("Process survived SIGKILL")
                }
            }
            try {
                val id = vault.create(snapshot("committed before process death"))
                assertTrue(vault.unlock(id))
                FileOutputStream(fixture).use {
                    it.write(id.toString().toByteArray(Charsets.US_ASCII))
                    it.fd.sync()
                }
                killOnPublish = true
                vault.save(id, 0, snapshot("interrupted revision"))
                error("Fault injection did not run")
            } finally {
                vault.close()
            }
        } else {
            require(phase == "recover")
            try {
                assertEquals(2, blobs(vaultId).listFiles()!!.size)
                val id = UUID.fromString(fixture.readText())
                val vault = open(vaultId)
                try {
                    assertEquals(1, blobs(vaultId).listFiles()!!.size)
                    assertLocked(vault, id)
                    assertTrue(vault.unlock(id))
                    assertEquals(0L, vault.revision(id))
                    assertTrue(vault.search(id, "committed before process death"))
                    assertFalse(vault.search(id, "interrupted revision"))
                } finally {
                    vault.close()
                }
            } finally {
                removeFixture(vaultId)
            }
        }
    }

    @Test
    fun portableBackupRestoresInFreshAndroidUser() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("vaultPortablePhase")
        assumeTrue(phase != null)
        val password = "portable fixture passphrase".toCharArray()
        try {
            withCleanVaultId { vaultId ->
                val vault = open(vaultId)
                try {
                    when (phase) {
                        "export" -> {
                            val id = vault.create(snapshot("portable across Android users"))
                            assertTrue(vault.unlock(id))
                            vault.save(id, 0, snapshot("portable across Android users"))
                            val archive = vault.backup(id, password)
                            instrumentation.sendStatus(0, Bundle().apply {
                                putString("vaultArchive", Base64.encodeToString(archive, Base64.NO_WRAP))
                                putString("vaultNotebook", id.toString())
                            })
                        }
                        "restore" -> {
                            val id = UUID.fromString(requireNotNull(arguments.getString("vaultNotebook")))
                            val archive = Base64.decode(requireNotNull(arguments.getString("vaultArchive")), Base64.NO_WRAP)
                            assertTrue(blobs(vaultId).listFiles()!!.isEmpty())
                            assertEquals(id, vault.restore(archive, password))
                            assertLocked(vault, id)
                            assertTrue(vault.unlock(id))
                            assertEquals(1L, vault.revision(id))
                            assertArrayEquals("portable across Android users".toByteArray(), vault.inferenceInput(id))
                            assertArrayEquals(byteArrayOf(1, 2, 3), vault.thumbnail(id))
                        }
                        else -> error("Unknown portable test phase")
                    }
                } finally {
                    vault.close()
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private suspend fun open(id: UUID, onCommit: (VaultCommitStage) -> Unit = {}) =
        ProtectedVault.open(context, id, authorize = { true }, onCommitStage = onCommit)

    private suspend fun assertLocked(vault: ProtectedVault, id: UUID) {
        expect<SecurityException> { vault.search(id, "needle") }
        expect<SecurityException> { vault.thumbnail(id) }
        expect<SecurityException> { vault.inferenceInput(id) }
    }

    private fun snapshot(text: String) = ProtectedNotebookSnapshot(text.toByteArray(), byteArrayOf(1, 2, 3))

    private fun traversalZip(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("../outside"))
            zip.write("test".toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun root(id: UUID) = File(context.noBackupFilesDir, "protected-vault/$id")
    private fun blobs(id: UUID) = File(root(id), "blobs")

    private suspend fun withCleanVaultId(block: suspend (UUID) -> Unit) {
        val id = UUID.randomUUID()
        try {
            block(id)
        } finally {
            removeFixture(id)
        }
    }

    private fun removeFixture(id: UUID) {
        root(id).deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry("omninumeris-prototype-v1-$id")
        }
    }

    private suspend inline fun <reified T : Throwable> expect(block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
