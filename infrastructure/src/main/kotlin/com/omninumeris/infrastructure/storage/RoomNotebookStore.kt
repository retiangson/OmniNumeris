package com.omninumeris.infrastructure.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omninumeris.application.CalculatorInsertionProposal
import com.omninumeris.application.CalculatorValueKind
import com.omninumeris.application.LinkedMathHistoryRead
import com.omninumeris.application.LinkedMathSolutionHistory
import com.omninumeris.application.LinkedMathSolutionRecord
import com.omninumeris.application.MathSolutionSave
import com.omninumeris.application.TypedSolutionAnswer
import com.omninumeris.application.TypedSolutionKind
import com.omninumeris.application.TypedSolutionRecord
import com.omninumeris.application.MathAutomaticInsertionGrant
import com.omninumeris.application.MathInsertionAuthorization
import com.omninumeris.application.MathInsertionCommit
import com.omninumeris.application.MathInsertionMode
import com.omninumeris.application.MathInsertionProblem
import com.omninumeris.application.MathInsertionProposal
import com.omninumeris.application.MathInsertionUndo
import com.omninumeris.application.MathInsertionUndoResult
import com.omninumeris.application.MathNoteDestination
import com.omninumeris.application.MathNoteInsertion
import com.omninumeris.application.NoteInsertionProposal
import com.omninumeris.application.NotebookInkRegion
import com.omninumeris.application.NotebookInkSnapshot
import com.omninumeris.application.NotebookRead
import com.omninumeris.application.NotebookMathPreference
import com.omninumeris.application.NotebookPageMove
import com.omninumeris.application.NotebookPageMoveUndo
import com.omninumeris.application.NotebookPagePlacement
import com.omninumeris.application.NotebookPageThumbnail
import com.omninumeris.application.NotebookResultSource
import com.omninumeris.application.NotebookInkSelection
import com.omninumeris.application.NotebookStore
import com.omninumeris.application.notebookText
import com.omninumeris.domain.NotebookKind
import com.omninumeris.domain.NotebookPage
import com.omninumeris.domain.NotebookSummary
import com.omninumeris.domain.NotebookViewport
import com.omninumeris.domain.PlacedNotebookText
import com.omninumeris.domain.calculator.Radix
import com.omninumeris.domain.canvas.InkSample
import com.omninumeris.domain.canvas.InkPoint
import com.omninumeris.domain.canvas.InkStroke
import com.omninumeris.domain.canvas.InkTip
import com.omninumeris.domain.canvas.MAX_INK_SAMPLES
import com.omninumeris.domain.math.MathSourceAnchor
import com.omninumeris.domain.math.MathParser
import com.omninumeris.domain.math.MathSourceRevision
import java.nio.ByteBuffer
import java.util.UUID

/** App-private ordinary notebooks. Protected notebooks must use their own storage and authorization path. */
class RoomNotebookStore private constructor(database: NotebookDatabase) : NotebookStore, MathNoteInsertion, LinkedMathSolutionHistory {
    private val dao = database.notebooks()
    private val transaction = RoomTransactionRunner(database)

    override suspend fun list(): NotebookRead<List<NotebookSummary>> = read { dao.notebooks().map { it.summary() } }

    override suspend fun create(title: String, kind: NotebookKind): NotebookRead<NotebookPage> {
        val normalized = title.trim()
        if (normalized.isEmpty() || normalized.length > 100) return NotebookRead.Unavailable
        return read {
            transaction.inTransaction {
                val notebook = LocalNotebookRow(newId(), normalized, kind.name)
                val page = LocalPageRow(newId(), notebook.id, 0, newId(), false, 0)
                dao.insertNotebook(notebook)
                dao.insertPage(page)
                page.domain(notebook)
            }
        }
    }

    override suspend fun open(notebookId: UUID, position: Int): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(notebookId.toString()) ?: return@inTransaction null
            dao.pageAt(notebook.id, position)?.domain(notebook)
        }
    }

    override suspend fun openById(notebookId: UUID, pageId: UUID): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(notebookId.toString()) ?: return@inTransaction null
            dao.page(pageId.toString())?.takeIf { it.notebookId == notebook.id }?.domain(notebook)
        }
    }

    override suspend fun thumbnail(notebookId: UUID, position: Int): NotebookRead<NotebookPageThumbnail> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(notebookId.toString())?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val page = dao.pageAt(notebook.id, position) ?: return@inTransaction null
            val ink = dao.inkInRegion(page.id, 0f, 0f, NotebookPage.WIDTH, NotebookPage.HEIGHT, THUMBNAIL_STROKES)
                .map { row ->
                    val samples = row.stroke()?.samples ?: return@inTransaction null
                    val pointCount = minOf(samples.size, THUMBNAIL_POINTS_PER_STROKE)
                    List(pointCount) { index ->
                        val fraction = if (pointCount == 1) 0 else index * (samples.size - 1) / (pointCount - 1)
                        val point = samples[fraction]
                        InkPoint(point.x.toDouble(), point.y.toDouble())
                    }
                }
            val results = dao.fixedPageResults(page.id).map { InkPoint(it.x.toDouble(), it.y.toDouble()) }
            NotebookPageThumbnail(UUID.fromString(page.id), ink, results)
        }
    }
    override suspend fun addPage(current: NotebookPage, placement: NotebookPagePlacement): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val page = dao.page(current.id.toString()) ?: return@inTransaction null
            if (page.notebookId != current.notebook.id.toString() || page.revision != current.revision.toString() ||
                page.position != current.position || page.readOnly) {
                return@inTransaction null
            }
            val notebook = dao.notebook(page.notebookId)?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val pageCount = dao.pageCount(notebook.id)
            if (pageCount >= MAX_PAGE_COUNT) return@inTransaction null
            val duplicate = placement == NotebookPagePlacement.DuplicateCurrent
            val position = if (placement == NotebookPagePlacement.End) pageCount else page.position + 1
            if (position < pageCount) shiftPages(notebook.id, position, pageCount, 1)
            val created = LocalPageRow(newId(), notebook.id, position, newId(), false,
                if (duplicate) page.nextSlot else 0, inkRevision = if (duplicate && page.inkRevision != null) newId() else null)
            dao.insertPage(created)
            if (duplicate) copyPageContent(page, created)
            created.domain(notebook)
        }
    }

    override suspend fun resultSource(page: NotebookPage, resultId: UUID): NotebookResultSource =
        storage(NotebookResultSource.Unavailable) {
            transaction.inTransaction {
                val current = dao.page(page.id.toString())?.takeIf { it.notebookId == page.notebook.id.toString() }
                    ?: return@inTransaction NotebookResultSource.Unavailable
                val result = dao.result(resultId.toString())?.takeIf { it.pageId == current.id && !it.undone }
                    ?: return@inTransaction NotebookResultSource.Unavailable
                val anchor = result.inkSource?.inkAnchor(page.notebook.id)
                    ?: return@inTransaction NotebookResultSource.Unavailable
                val source = dao.page(anchor.pageId.toString())?.takeIf { it.notebookId == current.notebookId }
                    ?: return@inTransaction NotebookResultSource.Unavailable
                if (source.inkRevision != anchor.revision.revisionId.toString()) {
                    return@inTransaction NotebookResultSource.Changed
                }
                val notebook = dao.notebook(current.notebookId) ?: return@inTransaction NotebookResultSource.Unavailable
                val strokes = ArrayList<InkStroke>(anchor.strokeIds.size)
                for (ids in anchor.strokeIds.map(UUID::toString).chunked(INK_COPY_BATCH)) {
                    val rows = dao.inkByIds(source.id, ids)
                    if (rows.size != ids.size) return@inTransaction NotebookResultSource.Unavailable
                    rows.forEach { row ->
                        strokes.add(row.stroke() ?: return@inTransaction NotebookResultSource.Unavailable)
                    }
                }
                NotebookResultSource.Ink(source.domain(notebook), NotebookInkSelection.Selected(anchor, strokes.toList()))
            }
        }

    private fun String.inkAnchor(notebookId: UUID): MathSourceAnchor.Ink? {
        val parts = split('/', limit = MAX_MATH_SOURCE_STROKES + 3)
        if (parts.size !in 3..(MAX_MATH_SOURCE_STROKES + 2)) return null
        return try {
            val ids = parts.drop(2).map(UUID::fromString)
            if (ids.size != ids.toSet().size) return null
            MathSourceAnchor.Ink(MathSourceRevision(notebookId, UUID.fromString(parts[1])), UUID.fromString(parts[0]), ids)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun read(anchor: MathSourceAnchor): LinkedMathHistoryRead = storage(LinkedMathHistoryRead.Unavailable) {
        transaction.inTransaction {
            val selection = historySelection(anchor) ?: return@inTransaction LinkedMathHistoryRead.Unavailable
            val records = dao.inkSolutions(selection.first, selection.second, MAX_LINKED_SOLUTIONS).map { row ->
                val kind = TypedSolutionKind.entries.firstOrNull { it.name == row.answerKind }
                    ?: return@inTransaction LinkedMathHistoryRead.Unavailable
                val id = try {
                    UUID.fromString(row.solutionId)
                } catch (_: IllegalArgumentException) {
                    return@inTransaction LinkedMathHistoryRead.Unavailable
                }
                LinkedMathSolutionRecord(anchor, TypedSolutionRecord(id, row.original, row.confirmed,
                    TypedSolutionAnswer(kind, row.answerValue), row.engineVersion))
            }
            LinkedMathHistoryRead.Available(records)
        }
    }

    override suspend fun save(record: LinkedMathSolutionRecord): MathSolutionSave {
        val value = record.solution
        if (value.original.length > TypedSolutionRecord.MAX_ORIGINAL_LENGTH ||
            value.confirmed.length !in 1..MathParser.MAX_INPUT_LENGTH || value.answer.value.length > MAX_LINKED_ANSWER_LENGTH) {
            return MathSolutionSave.TooLarge
        }
        if (value.engineVersion != TypedSolutionRecord.CURRENT_ENGINE) return MathSolutionSave.Unavailable
        return storage(MathSolutionSave.Unavailable) {
            transaction.inTransaction {
                val (pageId, selection) = historySelection(record.anchor) ?: return@inTransaction MathSolutionSave.Unavailable
                val row = NotebookInkSolutionRow(pageId = pageId, selection = selection, solutionId = value.id.toString(),
                    original = value.original, confirmed = value.confirmed, answerKind = value.answer.kind.name,
                    answerValue = value.answer.value, engineVersion = value.engineVersion)
                val previous = dao.inkSolution(row.solutionId)
                if (previous != null) {
                    return@inTransaction if (previous.copy(sequence = 0) == row) MathSolutionSave.Saved else MathSolutionSave.Unavailable
                }
                dao.insertInkSolution(row)
                dao.trimInkSolutions(pageId, selection, MAX_LINKED_SOLUTIONS)
                MathSolutionSave.Saved
            }
        }
    }

    private suspend fun historySelection(anchor: MathSourceAnchor): Pair<String, String>? {
        if (anchor !is MathSourceAnchor.Ink || anchor.strokeIds.size > MAX_MATH_SOURCE_STROKES) return null
        val pageId = anchor.pageId.toString()
        val page = dao.page(pageId) ?: return null
        val revision = anchor.revision.revisionId.toString()
        if (page.notebookId != anchor.revision.contentId.toString() || page.inkRevision != revision) return null
        val ids = anchor.strokeIds.map(UUID::toString).sorted()
        if (ids.size != ids.toSet().size || dao.inkCount(pageId, ids) != ids.size) return null
        return pageId to (listOf(revision) + ids).joinToString("/")
    }
    override suspend fun deletePage(current: NotebookPage): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(current.notebook.id.toString())?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val page = dao.page(current.id.toString())?.takeIf {
                it.notebookId == notebook.id && it.revision == current.revision.toString() &&
                    it.position == current.position && !it.readOnly
            } ?: return@inTransaction null
            val count = dao.pageCount(notebook.id)
            if (count <= 1 || count > MAX_PAGE_COUNT) return@inTransaction null
            val earliest = dao.lastDeletedPage(notebook.id)?.position ?: 0
            if (earliest == Int.MIN_VALUE) return@inTransaction null
            val mode = if (page.mathMode == MathInsertionMode.Automatic.name) MathInsertionMode.Suggestion.name else page.mathMode
            check(dao.updatePageDeletion(page.id, earliest - 1, page.position, newId(), mode) == 1)
            shiftPages(notebook.id, page.position + 1, count, -1)
            checkNotNull(dao.pageAt(notebook.id, minOf(page.position, count - 2))).domain(notebook)
        }
    }

    override suspend fun restoreLastDeletedPage(notebookId: UUID): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(notebookId.toString())?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val page = dao.lastDeletedPage(notebook.id) ?: return@inTransaction null
            val count = dao.pageCount(notebook.id)
            if (count >= MAX_PAGE_COUNT) return@inTransaction null
            val position = minOf(requireNotNull(page.deletedFromPosition), count)
            shiftPages(notebook.id, position, count, 1)
            val revision = newId()
            check(dao.updatePageDeletion(page.id, position, null, revision, page.mathMode) == 1)
            page.copy(position = position, revision = revision, deletedFromPosition = null).domain(notebook)
        }
    }

    override suspend fun hasDeletedPages(notebookId: UUID): Boolean = storage(false) {
        dao.lastDeletedPage(notebookId.toString()) != null
    }

    private suspend fun shiftPages(notebookId: String, start: Int, count: Int, delta: Int) {
        if (start >= count) return
        // Stage above the active range to avoid conflicts with the unique page order index.
        val stagingOffset = count + 1
        dao.stagePageShift(notebookId, start, stagingOffset)
        dao.finishPageShift(notebookId, stagingOffset, delta)
    }

    private suspend fun copyPageContent(source: LocalPageRow, target: LocalPageRow) {
        val results = dao.fixedPageResults(source.id).map { row ->
            row.copy(id = newId(), pageId = target.id, beforeRevision = target.revision, appliedGrantId = null)
        }
        if (results.isNotEmpty()) dao.insertResultBatch(results)
        var after = 0L
        while (true) {
            val rows = dao.inkForCopy(source.id, after, INK_COPY_BATCH)
            if (rows.isEmpty()) break
            dao.insertInkBatch(rows.map { it.stroke.copy(id = newId(), pageId = target.id) })
            after = rows.last().copyCursor
        }
    }

    override suspend fun movePage(current: NotebookPage, offset: Int): NotebookRead<NotebookPageMove> = read {
        if (offset !in setOf(-1, 1)) return@read null
        transaction.inTransaction {
            val notebook = dao.notebook(current.notebook.id.toString())?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val page = dao.page(current.id.toString())?.takeIf {
                it.notebookId == notebook.id && it.revision == current.revision.toString() && it.position == current.position
            } ?: return@inTransaction null
            val neighbor = dao.pageAt(notebook.id, page.position + offset) ?: return@inTransaction null
            if (page.readOnly || neighbor.readOnly) return@inTransaction null
            swapPagePositions(page, neighbor)
            val moved = page.copy(position = neighbor.position).domain(notebook)
            NotebookPageMove(moved, NotebookPageMoveUndo(current.notebook.id, current.id, UUID.fromString(neighbor.id),
                neighbor.position, page.position))
        }
    }

    override suspend fun undoPageMove(undo: NotebookPageMoveUndo): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val notebook = dao.notebook(undo.notebookId.toString())?.takeIf { it.kind == NotebookKind.Fixed.name }
                ?: return@inTransaction null
            val page = dao.pageAt(notebook.id, undo.positionAfterMove)
                ?.takeIf { it.id == undo.pageId.toString() && !it.readOnly } ?: return@inTransaction null
            val neighbor = dao.pageAt(notebook.id, undo.positionBeforeMove)
                ?.takeIf { it.id == undo.neighborId.toString() && it.id != page.id && !it.readOnly } ?: return@inTransaction null
            if (kotlin.math.abs(page.position.toLong() - neighbor.position) != 1L) return@inTransaction null
            swapPagePositions(page, neighbor)
            page.copy(position = neighbor.position).domain(notebook)
        }
    }

    private suspend fun swapPagePositions(page: LocalPageRow, neighbor: LocalPageRow) {
        // This reserved position is only used inside the swap transaction.
        check(dao.updatePagePosition(page.id, page.position, Int.MAX_VALUE) == 1)
        check(dao.updatePagePosition(neighbor.id, neighbor.position, page.position) == 1)
        check(dao.updatePagePosition(page.id, Int.MAX_VALUE, neighbor.position) == 1)
    }

    override suspend fun setReadOnly(page: NotebookPage, readOnly: Boolean): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val current = dao.page(page.id.toString()) ?: return@inTransaction null
            if (current.notebookId != page.notebook.id.toString() || current.revision != page.revision.toString()) {
                return@inTransaction null
            }
            val notebook = dao.notebook(current.notebookId) ?: return@inTransaction null
            if (current.readOnly == readOnly) return@inTransaction current.domain(notebook)
            val mode = if (readOnly && current.mathMode == MathInsertionMode.Automatic.name) {
                MathInsertionMode.Suggestion.name
            } else current.mathMode
            val updated = current.copy(revision = newId(), readOnly = readOnly, mathMode = mode, automaticGrantId = null)
            dao.updatePagePolicy(updated.id, updated.revision, updated.readOnly, updated.mathMode, updated.automaticGrantId)
            updated.domain(notebook)
        }
    }

    override suspend fun mathPreference(page: NotebookPage): NotebookRead<NotebookMathPreference> = read {
        val current = dao.page(page.id.toString())
            ?.takeIf { it.notebookId == page.notebook.id.toString() && it.revision == page.revision.toString() }
            ?: return@read null
        val mode = MathInsertionMode.entries.firstOrNull { it.name == current.mathMode } ?: return@read null
        val grant = current.automaticGrantId?.takeIf { !current.readOnly && mode == MathInsertionMode.Automatic }
            ?.let { MathAutomaticInsertionGrant(UUID.fromString(it), current.destination()) }
        NotebookMathPreference(mode, grant)
    }

    override suspend fun setMathMode(page: NotebookPage, mode: MathInsertionMode): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val current = dao.page(page.id.toString())
                ?.takeIf { it.notebookId == page.notebook.id.toString() && it.revision == page.revision.toString() && !it.readOnly }
                ?: return@inTransaction null
            val notebook = dao.notebook(current.notebookId) ?: return@inTransaction null
            if (current.mathMode == mode.name && (mode != MathInsertionMode.Automatic || current.automaticGrantId != null)) {
                return@inTransaction current.domain(notebook)
            }
            val grant = if (mode == MathInsertionMode.Automatic) newId() else null
            val updated = current.copy(revision = newId(), mathMode = mode.name, automaticGrantId = grant)
            dao.updatePagePolicy(updated.id, updated.revision, updated.readOnly, updated.mathMode, updated.automaticGrantId)
            updated.domain(notebook)
        }
    }

    override suspend fun appendInk(page: NotebookPage, stroke: InkStroke): NotebookRead<NotebookPage> = read {
        transaction.inTransaction {
            val current = dao.page(page.id.toString())
                ?.takeIf { it.notebookId == page.notebook.id.toString() && it.revision == page.revision.toString() && !it.readOnly }
                ?: return@inTransaction null
            val notebook = dao.notebook(current.notebookId) ?: return@inTransaction null
            if (notebook.kind == NotebookKind.Fixed.name &&
                stroke.samples.any { it.x !in 0f..NotebookPage.WIDTH || it.y !in 0f..NotebookPage.HEIGHT }) {
                return@inTransaction null
            }
            val points = ByteBuffer.allocate(stroke.samples.size * INK_SAMPLE_BYTES)
            stroke.samples.forEach { points.putFloat(it.x).putFloat(it.y).putFloat(it.pressure) }
            val row = NotebookInkRow(
                stroke.id.toString(), current.id,
                stroke.samples.minOf(InkSample::x), stroke.samples.minOf(InkSample::y),
                stroke.samples.maxOf(InkSample::x), stroke.samples.maxOf(InkSample::y), stroke.tip.name, points.array(),
            )
            val updated = current.copy(revision = newId(), inkRevision = newId())
            dao.insertInk(row)
            dao.advanceInkRevision(updated.id, updated.revision, requireNotNull(updated.inkRevision))
            updated.domain(notebook)
        }
    }

    override suspend fun ink(page: NotebookPage, region: NotebookInkRegion): NotebookRead<NotebookInkSnapshot> = read {
        val (left, top, right, bottom) = region
        if (!listOf(left, top, right, bottom).all(Float::isFinite) || right <= left || bottom <= top) return@read null
        transaction.inTransaction {
            val current = dao.page(page.id.toString())
                ?.takeIf { it.notebookId == page.notebook.id.toString() && it.revision == page.revision.toString() }
                ?: return@inTransaction null
            val revision = UUID.fromString(current.inkRevision ?: current.revision)
            val rows = dao.inkInRegion(current.id, left, top, right, bottom, MAX_INK_REGION_STROKES + 1)
            if (rows.size > MAX_INK_REGION_STROKES) return@inTransaction null
            val strokes = rows.map { it.stroke() ?: return@inTransaction null }
            NotebookInkSnapshot(MathSourceRevision(page.notebook.id, revision), strokes)
        }
    }

    override suspend fun visible(pageId: UUID, left: Float, top: Float, right: Float, bottom: Float): NotebookRead<List<PlacedNotebookText>> {
        if (!listOf(left, top, right, bottom).all(Float::isFinite) || right <= left || bottom <= top) {
            return NotebookRead.Unavailable
        }
        return read {
            transaction.inTransaction {
                if (dao.page(pageId.toString()) == null) return@inTransaction null
                dao.visible(pageId.toString(), left - PlacedNotebookText.WIDTH, top - PlacedNotebookText.HEIGHT, right, bottom)
                    .map {
                        val radix = Radix.entries.firstOrNull { radix -> radix.name == it.kind.substringAfter('/', "") }
                        PlacedNotebookText(UUID.fromString(it.id), it.x, it.y, it.text,
                            it.kind == CalculatorValueKind.Approximate.name, radix, it.verifiedExpression, it.inkSource != null)
                    }
            }
        }
    }

    override suspend fun lastUndo(pageId: UUID): MathInsertionUndo? = storage(null) {
        transaction.inTransaction {
            val page = dao.page(pageId.toString()) ?: return@inTransaction null
            if (page.readOnly) return@inTransaction null
            dao.lastActiveResult(page.id)?.let { MathInsertionUndo(page.destination(), UUID.fromString(it.id)) }
        }
    }

    override suspend fun insert(proposal: NoteInsertionProposal, authorization: MathInsertionAuthorization): MathInsertionCommit {
        if (proposal is CalculatorInsertionProposal && authorization is MathInsertionAuthorization.Automatic) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.AutomaticGrantRevoked)
        }
        val source = (proposal as? MathInsertionProposal)?.source
        if (source != null && source !is MathSourceAnchor.Ink) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.SourceUnavailable)
        }
        if (source is MathSourceAnchor.Ink &&
            (source.strokeIds.size > MAX_MATH_SOURCE_STROKES || source.strokeIds.size != source.strokeIds.toSet().size)) {
            return MathInsertionCommit.Rejected(MathInsertionProblem.SourceUnavailable)
        }
        val resultText: String
        val kind: String
        val expression: String?
        when (proposal) {
            is CalculatorInsertionProposal -> {
                resultText = proposal.value.text
                kind = listOfNotNull(proposal.value.kind.name, proposal.value.radix?.name).joinToString("/")
                expression = null
            }
            is MathInsertionProposal -> {
                resultText = proposal.answer.notebookText()
                kind = "VerifiedMath"
                expression = proposal.expression
            }
        }
        val target = proposal.target
        if (target.positionId != target.destination.noteId) return MathInsertionCommit.Rejected(MathInsertionProblem.NoEditableTarget)
        return storage(MathInsertionCommit.Rejected(MathInsertionProblem.Unavailable)) {
            transaction.inTransaction {
                val page = dao.page(target.destination.noteId.toString())
                    ?.takeIf { it.notebookId == target.destination.notebookId.toString() }
                    ?: return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.NoEditableTarget)
                if (page.readOnly) return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.ReadOnly)
                val inkSource = if (source is MathSourceAnchor.Ink) {
                    val sourcePage = dao.page(source.pageId.toString())
                    val ids = source.strokeIds.map(UUID::toString).sorted()
                    if (sourcePage == null || sourcePage.notebookId != page.notebookId ||
                        source.revision.contentId.toString() != sourcePage.notebookId ||
                        dao.inkCount(sourcePage.id, ids) != ids.size) {
                        return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.SourceUnavailable)
                    }
                    if (sourcePage.inkRevision != source.revision.revisionId.toString()) {
                        return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.RevisionChanged)
                    }
                    (listOf(sourcePage.id, source.revision.revisionId.toString()) + ids).joinToString("/")
                } else null
                val grant = (authorization as? MathInsertionAuthorization.Automatic)?.grant
                val grantId = grant?.id?.toString()
                if (grant != null && ((proposal as? MathInsertionProposal)?.mode != MathInsertionMode.Automatic ||
                        grant.destination != target.destination ||
                        page.mathMode != MathInsertionMode.Automatic.name || page.automaticGrantId != grantId)) {
                    return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.AutomaticGrantRevoked)
                }
                val id = proposal.resultId.toString()
                val revision = target.revisionId.toString()
                val receipt = MathInsertionCommit.Inserted(MathInsertionUndo(page.destination(), proposal.resultId))
                val previous = dao.result(id)
                if (previous != null) {
                    return@inTransaction if (!previous.undone && previous.pageId == page.id && previous.beforeRevision == revision &&
                        previous.text == resultText && previous.kind == kind && previous.verifiedExpression == expression &&
                        previous.appliedGrantId == grantId && previous.inkSource == inkSource) receipt
                    else MathInsertionCommit.Rejected(MathInsertionProblem.OperationConflict)
                }
                if (page.revision != revision) return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.RevisionChanged)
                val notebook = dao.notebook(page.notebookId)
                    ?: return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.NoEditableTarget)
                val slot = if (notebook.kind == NotebookKind.Fixed.name) {
                    val occupied = dao.fixedPageResults(page.id).map { it.x to it.y }.toHashSet()
                    (0L until FIXED_SLOTS).firstOrNull { placement(it) !in occupied }
                } else page.nextSlot.takeIf { placement(it).second < NotebookViewport.LIMIT }
                if (slot == null) return@inTransaction MathInsertionCommit.Rejected(MathInsertionProblem.NoSpaceInTarget)
                val (x, y) = placement(slot)
                dao.insertResult(NotebookResultRow(id, page.id, revision, page.nextSlot, x, y, resultText, kind, false,
                    expression, grantId, inkSource))
                dao.updatePage(page.id, newId(), false, page.nextSlot + 1)
                receipt
            }
        }
    }

    override suspend fun undo(operation: MathInsertionUndo): MathInsertionUndoResult = storage(
        MathInsertionUndoResult.Rejected(MathInsertionProblem.Unavailable),
    ) {
        transaction.inTransaction {
            val page = dao.page(operation.destination.noteId.toString())
                ?.takeIf { it.notebookId == operation.destination.notebookId.toString() }
                ?: return@inTransaction MathInsertionUndoResult.Rejected(MathInsertionProblem.NoEditableTarget)
            if (page.readOnly) return@inTransaction MathInsertionUndoResult.Rejected(MathInsertionProblem.ReadOnly)
            val result = dao.result(operation.operationId.toString())?.takeIf { it.pageId == page.id }
                ?: return@inTransaction MathInsertionUndoResult.Rejected(MathInsertionProblem.OperationConflict)
            if (!result.undone) {
                dao.undo(result.id)
                dao.updatePage(page.id, newId(), false, page.nextSlot)
            }
            MathInsertionUndoResult.Restored
        }
    }

    private suspend fun <T : Any> read(block: suspend () -> T?): NotebookRead<T> = storage(NotebookRead.Unavailable) {
        block()?.let { NotebookRead.Available(it) } ?: NotebookRead.Unavailable
    }

    private suspend fun <T> storage(failure: T, block: suspend () -> T): T = try {
        block()
    } catch (_: SQLiteException) {
        failure
    } catch (_: SecurityException) {
        failure
    }

    private fun LocalNotebookRow.summary() = NotebookSummary(UUID.fromString(id), title, NotebookKind.valueOf(kind))
    private suspend fun LocalPageRow.domain(notebook: LocalNotebookRow) = NotebookPage(
        UUID.fromString(id), notebook.summary(), UUID.fromString(revision), readOnly, position, dao.pageCount(notebook.id),
    )
    private fun LocalPageRow.destination() = MathNoteDestination(UUID.fromString(notebookId), UUID.fromString(id))
    private fun NotebookInkRow.stroke(): InkStroke? {
        if (points.size !in INK_SAMPLE_BYTES..(INK_SAMPLE_BYTES * MAX_INK_SAMPLES) ||
            points.size % INK_SAMPLE_BYTES != 0) return null
        val tip = InkTip.entries.firstOrNull { it.name == tip } ?: return null
        val buffer = ByteBuffer.wrap(points)
        return try {
            InkStroke(UUID.fromString(id), List(points.size / INK_SAMPLE_BYTES) {
                InkSample(buffer.float, buffer.float, buffer.float)
            }, tip)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    private fun placement(slot: Long) = (24f + (slot % 3) * 264f) to (24f + (slot / 3) * 108f)

    companion object {
        private const val FIXED_SLOTS = 27L
        private const val MAX_PAGE_COUNT = Int.MAX_VALUE / 2
        private const val INK_SAMPLE_BYTES = 12
        private const val INK_COPY_BATCH = 16
        private const val THUMBNAIL_STROKES = 12
        private const val THUMBNAIL_POINTS_PER_STROKE = 48
        private const val MAX_INK_REGION_STROKES = 128
        private const val MAX_MATH_SOURCE_STROKES = 64
        private const val MAX_LINKED_SOLUTIONS = 32
        private const val MAX_LINKED_ANSWER_LENGTH = 4096
        private fun newId() = UUID.randomUUID().toString()

        private val addVerifiedExpression = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_result ADD COLUMN verifiedExpression TEXT DEFAULT NULL")
            }
        }

        private val addMathPreferences = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_page ADD COLUMN mathMode TEXT NOT NULL DEFAULT 'Manual'")
                db.execSQL("ALTER TABLE notebook_page ADD COLUMN automaticGrantId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notebook_result ADD COLUMN appliedGrantId TEXT DEFAULT NULL")
            }
        }

        private val addNotebookInk = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_page ADD COLUMN inkRevision TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE notebook_result ADD COLUMN inkSource TEXT DEFAULT NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notebook_ink (
                        id TEXT NOT NULL PRIMARY KEY, pageId TEXT NOT NULL, left REAL NOT NULL, top REAL NOT NULL,
                        right REAL NOT NULL, bottom REAL NOT NULL, tip TEXT NOT NULL, points BLOB NOT NULL,
                        FOREIGN KEY(pageId) REFERENCES notebook_page(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebook_ink_pageId_left_top ON notebook_ink(pageId, left, top)")
            }
        }

        private val addPageDeletion = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebook_page ADD COLUMN deletedFromPosition INTEGER DEFAULT NULL")
            }
        }

        private val addInkSolutionHistory = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notebook_ink_solution (
                        sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pageId TEXT NOT NULL, selection TEXT NOT NULL,
                        solutionId TEXT NOT NULL, original TEXT NOT NULL, confirmed TEXT NOT NULL, answerKind TEXT NOT NULL,
                        answerValue TEXT NOT NULL, engineVersion INTEGER NOT NULL,
                        FOREIGN KEY(pageId) REFERENCES notebook_page(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebook_ink_solution_pageId_selection ON notebook_ink_solution(pageId, selection)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notebook_ink_solution_solutionId ON notebook_ink_solution(solutionId)")
            }
        }

        fun open(context: Context): RoomNotebookStore = RoomNotebookStore(
            Room.databaseBuilder(context.applicationContext, NotebookDatabase::class.java, "ordinary-notebooks.db")
                .addMigrations(addVerifiedExpression, addMathPreferences, addNotebookInk, addPageDeletion, addInkSolutionHistory)
                .build(),
        )
    }
}
