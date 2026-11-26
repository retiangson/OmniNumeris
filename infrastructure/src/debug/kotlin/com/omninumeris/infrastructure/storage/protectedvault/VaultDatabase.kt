package com.omninumeris.infrastructure.storage.protectedvault

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "notebook")
internal data class NotebookRow(
    @PrimaryKey val id: String,
    val wrappedKey: ByteArray,
    @ColumnInfo(defaultValue = "0") val revision: Long,
)

@Entity(
    tableName = "blob_pointer",
    foreignKeys = [ForeignKey(
        entity = NotebookRow::class,
        parentColumns = ["id"],
        childColumns = ["notebookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["blobId"], unique = true)],
)
internal data class BlobPointer(@PrimaryKey val notebookId: String, val blobId: String)

internal data class VaultRecord(val id: String, val wrappedKey: ByteArray, val revision: Long, val blobId: String)

@Dao
internal interface VaultDao {
    @Insert suspend fun insertNotebook(row: NotebookRow)
    @Insert suspend fun insertPointer(row: BlobPointer)

    @Query("""
        SELECT notebook.id, notebook.wrappedKey, notebook.revision, blob_pointer.blobId
        FROM notebook INNER JOIN blob_pointer ON notebook.id = blob_pointer.notebookId
        WHERE notebook.id = :id
    """)
    suspend fun record(id: String): VaultRecord?

    @Query("SELECT EXISTS(SELECT 1 FROM notebook WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM notebook")
    suspend fun notebookCount(): Int

    @Query("SELECT blobId FROM blob_pointer")
    suspend fun blobIds(): List<String>

    @Query("UPDATE notebook SET revision = :next WHERE id = :id AND revision = :previous")
    suspend fun advanceRevision(id: String, previous: Long, next: Long): Int

    @Query("UPDATE blob_pointer SET blobId = :blobId WHERE notebookId = :id")
    suspend fun movePointer(id: String, blobId: String): Int
}

@Database(entities = [NotebookRow::class, BlobPointer::class], version = 2, exportSchema = true)
internal abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}

internal val VAULT_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notebook ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
    }
}
