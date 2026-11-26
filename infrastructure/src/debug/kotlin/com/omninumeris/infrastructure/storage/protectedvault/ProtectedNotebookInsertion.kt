package com.omninumeris.infrastructure.storage.protectedvault

import android.database.sqlite.SQLiteException
import com.omninumeris.application.CalculatorInsertionProposal
import com.omninumeris.application.MathInsertionAuthorization
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionProposal
import com.omninumeris.application.MathInsertionTarget
import com.omninumeris.application.MathInsertionUndo
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.application.MathNoteDestination
import com.omninumeris.application.MathNoteInsertion
import com.omninumeris.application.NoteInsertionProposal
import com.omninumeris.application.notebookText
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID
import org.json.JSONException
import org.json.JSONObject

/** Exercises insertion and undo inside the vault boundary on synthetic debug notebooks. */
class ProtectedNotebookInsertion(
    private val vault: ProtectedVault,
    private val lease: ProtectedNotebookLease,
) : MathNoteInsertion {
    suspend fun target(): MathInsertionTarget? = guarded(null, null) {
        vault.editDocument(lease) { bytes -> VaultDocumentEdit(decode(bytes).target()) }
    }

    suspend fun activeResults(): List<String>? = guarded(null, null) {
        vault.editDocument(lease) { bytes ->
            val operations = decode(bytes).getJSONObject("operations")
            VaultDocumentEdit(operations.keys().asSequence().map(operations::getJSONObject)
                .filterNot { it.getBoolean("undone") }.map { it.getString("text") }.toList())
        }
    }

    override suspend fun insert(
        proposal: NoteInsertionProposal,
        authorization: MathInsertionAuthorization,
    ): MathInsertionCommit = guarded(reject(MathInsertionProblem.Locked), reject(MathInsertionProblem.Unavailable)) {
        vault.editDocument(lease) { bytes ->
            val page = decode(bytes)
            val target = page.target()
            fun denied(problem: MathInsertionProblem) = VaultDocumentEdit(reject(problem))
            if (proposal.target.destination != target.destination || proposal.target.positionId != target.positionId) {
                return@editDocument denied(MathInsertionProblem.NoEditableTarget)
            }
            if (page.getBoolean("readOnly")) return@editDocument denied(MathInsertionProblem.ReadOnly)
            if (authorization != MathInsertionAuthorization.PreviewApproved) {
                return@editDocument denied(MathInsertionProblem.AutomaticGrantRevoked)
            }
            val operation = when (proposal) {
                is MathInsertionProposal -> {
                    if (proposal.source != null) return@editDocument denied(MathInsertionProblem.SourceUnavailable)
                    JSONObject().put("kind", "VerifiedMath").put("text", proposal.answer.notebookText())
                        .put("expression", proposal.expression)
                }
                is CalculatorInsertionProposal -> JSONObject().put("kind", proposal.value.kind.name)
                    .put("text", proposal.value.text).put("radix", proposal.value.radix?.name)
            }.put("before", proposal.target.revisionId.toString()).put("undone", false)
            val operations = page.getJSONObject("operations")
            val id = proposal.resultId.toString()
            val prior = operations.optJSONObject(id)
            val receipt = MathInsertionCommit.Inserted(MathInsertionUndo(target.destination, proposal.resultId))
            if (prior != null) {
                return@editDocument if (!prior.getBoolean("undone") &&
                    listOf("kind", "text", "radix", "expression", "before").all { prior.opt(it) == operation.opt(it) }) {
                    VaultDocumentEdit(receipt)
                } else denied(MathInsertionProblem.OperationConflict)
            }
            if (target.revisionId != proposal.target.revisionId) return@editDocument denied(MathInsertionProblem.RevisionChanged)
            if (operations.length() >= MAX_OPERATIONS) return@editDocument denied(MathInsertionProblem.NoSpaceInTarget)
            operations.put(id, operation)
            page.put("revision", UUID.randomUUID().toString())
            val replacement = page.toString().toByteArray()
            if (replacement.size > VaultFormat.MAX_DOCUMENT) {
                replacement.fill(0)
                return@editDocument denied(MathInsertionProblem.NoSpaceInTarget)
            }
            VaultDocumentEdit(receipt, replacement)
        }
    }

    override suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult =
        guarded(undoRejected(MathInsertionProblem.Locked), undoRejected(MathInsertionProblem.Unavailable)) {
            vault.editDocument(lease) { bytes ->
                val page = decode(bytes)
                if (operation.destination != page.target().destination) {
                    return@editDocument VaultDocumentEdit(undoRejected(MathInsertionProblem.NoEditableTarget))
                }
                if (page.getBoolean("readOnly")) {
                    return@editDocument VaultDocumentEdit(undoRejected(MathInsertionProblem.ReadOnly))
                }
                val entry = page.getJSONObject("operations").optJSONObject(operation.operationId.toString())
                    ?: return@editDocument VaultDocumentEdit(undoRejected(MathInsertionProblem.OperationConflict))
                if (entry.getBoolean("undone")) return@editDocument VaultDocumentEdit(MathInsertionUndoResult.Restored)
                entry.put("undone", true)
                page.put("revision", UUID.randomUUID().toString())
                VaultDocumentEdit(MathInsertionUndoResult.Restored, page.toString().toByteArray())
            }
        }

    private fun decode(bytes: ByteArray): JSONObject = JSONObject(bytes.toString(Charsets.UTF_8)).also {
        require(it.getInt("version") == VERSION)
    }

    private fun JSONObject.target() = MathInsertionTarget(
        MathNoteDestination(lease.notebookId, UUID.fromString(getString("pageId"))),
        UUID.fromString(getString("revision")),
        lease.placementId,
    )

    private suspend fun <T> guarded(locked: T, unavailable: T, action: suspend () -> T): T = try {
        action()
    } catch (_: VaultLeaseExpired) {
        locked
    } catch (_: SecurityException) {
        unavailable
    } catch (_: GeneralSecurityException) {
        unavailable
    } catch (_: IOException) {
        unavailable
    } catch (_: SQLiteException) {
        unavailable
    } catch (_: JSONException) {
        unavailable
    } catch (_: IllegalArgumentException) {
        unavailable
    }

    private fun reject(problem: MathInsertionProblem): MathInsertionCommit = MathInsertionCommit.Rejected(problem)
    private fun undoRejected(problem: MathInsertionProblem): MathInsertionUndoResult = MathInsertionUndoResult.Rejected(problem)

    companion object {
        private const val VERSION = 1
        private const val MAX_OPERATIONS = 256

        suspend fun create(vault: ProtectedVault, original: String, readOnly: Boolean = false): UUID {
            require(original.length <= 16_384)
            val payload = JSONObject().put("version", VERSION).put("original", original)
                .put("readOnly", readOnly).put("pageId", UUID.randomUUID().toString())
                .put("revision", UUID.randomUUID().toString()).put("operations", JSONObject())
                .toString().toByteArray()
            return try {
                vault.create(ProtectedNotebookSnapshot(payload, ByteArray(0)))
            } finally {
                payload.fill(0)
            }
        }
    }
}
