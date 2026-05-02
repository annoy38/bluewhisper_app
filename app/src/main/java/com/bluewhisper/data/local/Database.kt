package com.bluewhisper.data.local

import androidx.room.*
import com.bluewhisper.domain.model.FileType
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────
@Entity(tableName = "saved_files")
data class SavedFileEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val fileExtension: String,
    val fileSizeBytes: Long,
    val fileType: String,          // FileType enum name
    val localPath: String,         // legacy absolute path (API 26-28) OR best-effort path on Q+
    val thumbnailPath: String?,
    val senderNickname: String,
    val receivedAtEpoch: Long,
    val savedAtEpoch: Long,
    val isDeleted: Int = 0,        // 0 = active, 1 = soft deleted
    // FR-07.4: when saved via MediaStore.Downloads (API 29+), this holds the
    // content URI string. Share/open uses this directly. Null = legacy file path.
    val mediaStoreUri: String? = null,
    val mimeType: String? = null
)

// ── DAO ───────────────────────────────────────────────────────────
@Dao
interface SavedFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SavedFileEntity): Long

    @Query("SELECT * FROM saved_files WHERE isDeleted = 0 ORDER BY savedAtEpoch DESC")
    fun getAllFiles(): Flow<List<SavedFileEntity>>

    @Query("SELECT * FROM saved_files WHERE fileId = :fileId AND isDeleted = 0")
    suspend fun getFileById(fileId: String): SavedFileEntity?

    @Query("UPDATE saved_files SET isDeleted = 1 WHERE fileId = :fileId")
    suspend fun softDeleteFile(fileId: String): Int

    @Query("DELETE FROM saved_files")
    suspend fun hardDeleteAllFiles(): Int

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM saved_files WHERE isDeleted = 0")
    fun getTotalStorageUsedBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM saved_files WHERE isDeleted = 0")
    fun getFileCount(): Flow<Int>
}

// ── Converter for FileType ────────────────────────────────────────
class Converters {
    @TypeConverter
    fun fromFileType(value: FileType): String = value.name

    @TypeConverter
    fun toFileType(value: String): FileType = FileType.valueOf(value)
}

// ── Database ──────────────────────────────────────────────────────
@Database(
    entities = [SavedFileEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BlueWhisperDatabase : RoomDatabase() {
    abstract fun savedFileDao(): SavedFileDao

    companion object {
        const val DATABASE_NAME = "bluewhisper_db"
    }
}
