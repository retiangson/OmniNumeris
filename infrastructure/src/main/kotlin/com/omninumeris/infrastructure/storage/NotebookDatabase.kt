package com.omninumeris.infrastructure.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "notebook")
internal data class LocalNotebookRow(@PrimaryKey val id: String, val title: String, val kind: String)

@Entity(
    tableName = "notebook_page",
    foreignKeys = [ForeignKey(entity = LocalNotebookRow::class, parentColumns = ["id"], childColumns = ["notebookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["notebookId", "position"], unique = true)],
)
internal data class LocalPageRow(
    @PrimaryKey val id: String,
    val notebookId: String,
    val position: Int,
    val revision: String,
    val readOnly: Boolean,
    val nextSlot: Long,
    @ColumnInfo(defaultValue = "'Manual'") val mathMode: String = "Manual",
    @ColumnInfo(defaultValue = "NULL") val automaticGrantId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val inkRevision: String? = null,
    @ColumnInfo(defaultValue = "NULL") val deletedFromPosition: Int? = null,
)

@Entity(
    tableName = "notebook_ink",
    foreignKeys = [ForeignKey(entity = LocalPageRow::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["pageId", "left", "top"])],
)
internal data class NotebookInkRow(
    @PrimaryKey val id: String,
    val pageId: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tip: String,
    val points: ByteArray,
)

internal data class NotebookInkCopyRow(val copyCursor: Long, @Embedded val stroke: NotebookInkRow)

@Entity(
    tableName = "notebook_ink_solution",
    foreignKeys = [ForeignKey(entity = LocalPageRow::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["pageId", "selection"]), Index(value = ["solutionId"], unique = true)],
)
internal data class NotebookInkSolutionRow(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val pageId: String,
    val selection: String,
    val solutionId: String,
    val original: String,
    val confirmed: String,
    val answerKind: String,
    val answerValue: String,
    val engineVersion: Int,
)

@Entity(
    tableName = "notebook_result",
    foreignKeys = [ForeignKey(entity = LocalPageRow::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["pageId", "slot"], unique = true), Index(value = ["pageId", "x", "y"])],
)
internal data class NotebookResultRow(
    @PrimaryKey val id: String,
    val pageId: String,
    val beforeRevision: String,
    val slot: Long,
    val x: Float,
    val y: Float,
    val text: String,
    val kind: String,
    val undone: Boolean,
    @ColumnInfo(defaultValue = "NULL") val verifiedExpression: String? = null,
    @ColumnInfo(defaultValue = "NULL") val appliedGrantId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val inkSource: String? = null,
)

@Dao
internal interface NotebookDao {
    @Insert suspend fun insertNotebook(row: LocalNotebookRow)
    @Insert suspend fun insertPage(row: LocalPageRow)
    @Insert suspend fun insertResult(row: NotebookResultRow)
    @Insert suspend fun insertInk(row: NotebookInkRow)
    @Insert suspend fun insertInkBatch(rows: List<NotebookInkRow>)
    @Insert suspend fun insertResultBatch(rows: List<NotebookResultRow>)
    @Insert suspend fun insertInkSolution(row: NotebookInkSolutionRow)
    @Query("SELECT * FROM notebook_ink_solution WHERE solutionId = :id")
    suspend fun inkSolution(id: String): NotebookInkSolutionRow?
    @Query("SELECT * FROM notebook_ink_solution WHERE pageId = :pageId AND selection = :selection ORDER BY sequence DESC LIMIT :limit")
    suspend fun inkSolutions(pageId: String, selection: String, limit: Int): List<NotebookInkSolutionRow>
    @Query("""
        DELETE FROM notebook_ink_solution
        WHERE pageId = :pageId AND selection = :selection AND sequence NOT IN
            (SELECT sequence FROM notebook_ink_solution WHERE pageId = :pageId AND selection = :selection
             ORDER BY sequence DESC LIMIT :limit)
    """)
    suspend fun trimInkSolutions(pageId: String, selection: String, limit: Int)
    @Query("SELECT * FROM notebook ORDER BY title COLLATE NOCASE, id") suspend fun notebooks(): List<LocalNotebookRow>
    @Query("SELECT * FROM notebook WHERE id = :id") suspend fun notebook(id: String): LocalNotebookRow?
    @Query("SELECT * FROM notebook_page WHERE notebookId = :notebookId AND position = :position AND deletedFromPosition IS NULL")
    suspend fun pageAt(notebookId: String, position: Int): LocalPageRow?
    @Query("SELECT COUNT(*) FROM notebook_page WHERE notebookId = :notebookId AND deletedFromPosition IS NULL")
    suspend fun pageCount(notebookId: String): Int
    @Query("UPDATE notebook_page SET position = :position WHERE id = :id AND position = :expected")
    suspend fun updatePagePosition(id: String, expected: Int, position: Int): Int
    @Query("UPDATE notebook_page SET position = position + :offset WHERE notebookId = :notebookId AND position >= :position AND deletedFromPosition IS NULL")
    suspend fun stagePageShift(notebookId: String, position: Int, offset: Int)
    @Query("UPDATE notebook_page SET position = position - :offset + :delta WHERE notebookId = :notebookId AND position >= :offset AND deletedFromPosition IS NULL")
    suspend fun finishPageShift(notebookId: String, offset: Int, delta: Int)
    @Query("SELECT * FROM notebook_page WHERE id = :id AND deletedFromPosition IS NULL") suspend fun page(id: String): LocalPageRow?
    @Query("SELECT * FROM notebook_page WHERE notebookId = :notebookId AND deletedFromPosition IS NOT NULL ORDER BY position LIMIT 1")
    suspend fun lastDeletedPage(notebookId: String): LocalPageRow?
    @Query("""
        UPDATE notebook_page SET position = :position, deletedFromPosition = :deletedFrom,
            revision = :revision, mathMode = :mode, automaticGrantId = NULL
        WHERE id = :id
    """)
    suspend fun updatePageDeletion(id: String, position: Int, deletedFrom: Int?, revision: String, mode: String): Int
    @Query("SELECT * FROM notebook_result WHERE id = :id") suspend fun result(id: String): NotebookResultRow?

    @Query("SELECT * FROM notebook_result WHERE pageId = :pageId AND undone = 0 ORDER BY slot DESC LIMIT 1")
    suspend fun lastActiveResult(pageId: String): NotebookResultRow?

    @Query("SELECT * FROM notebook_result WHERE pageId = :pageId AND undone = 0 ORDER BY slot LIMIT 27")
    suspend fun fixedPageResults(pageId: String): List<NotebookResultRow>

    @Query("SELECT * FROM notebook_result WHERE pageId = :pageId AND undone = 0 AND x > :left AND x < :right AND y > :top AND y < :bottom ORDER BY slot LIMIT 256")
    suspend fun visible(pageId: String, left: Float, top: Float, right: Float, bottom: Float): List<NotebookResultRow>

    @Query("UPDATE notebook_page SET revision = :revision, readOnly = :readOnly, nextSlot = :nextSlot WHERE id = :id")
    suspend fun updatePage(id: String, revision: String, readOnly: Boolean, nextSlot: Long)

    @Query("UPDATE notebook_page SET revision = :revision, readOnly = :readOnly, mathMode = :mode, automaticGrantId = :grantId WHERE id = :id")
    suspend fun updatePagePolicy(id: String, revision: String, readOnly: Boolean, mode: String, grantId: String?)

    @Query("UPDATE notebook_result SET undone = 1 WHERE id = :id") suspend fun undo(id: String)

    @Query("UPDATE notebook_page SET revision = :revision, inkRevision = :inkRevision WHERE id = :id")
    suspend fun advanceInkRevision(id: String, revision: String, inkRevision: String)

    @Query("SELECT COUNT(*) FROM notebook_ink WHERE pageId = :pageId AND id IN (:ids)")
    suspend fun inkCount(pageId: String, ids: List<String>): Int
    @Query("SELECT * FROM notebook_ink WHERE pageId = :pageId AND id IN (:ids) ORDER BY rowid")
    suspend fun inkByIds(pageId: String, ids: List<String>): List<NotebookInkRow>

    @Query("SELECT rowid AS copyCursor, * FROM notebook_ink WHERE pageId = :pageId AND rowid > :after ORDER BY rowid LIMIT :limit")
    suspend fun inkForCopy(pageId: String, after: Long, limit: Int): List<NotebookInkCopyRow>

    @Query("""
        SELECT * FROM notebook_ink WHERE pageId = :pageId
            AND right >= :left AND bottom >= :top AND left <= :right AND top <= :bottom
        ORDER BY rowid LIMIT :limit
    """)
    suspend fun inkInRegion(pageId: String, left: Float, top: Float, right: Float, bottom: Float, limit: Int): List<NotebookInkRow>
}

@Database(
    entities = [LocalNotebookRow::class, LocalPageRow::class, NotebookResultRow::class, NotebookInkRow::class, NotebookInkSolutionRow::class],
    version = 6,
    exportSchema = true,
)
internal abstract class NotebookDatabase : RoomDatabase() {
    abstract fun notebooks(): NotebookDao
}
