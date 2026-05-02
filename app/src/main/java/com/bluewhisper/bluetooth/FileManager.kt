package com.bluewhisper.bluetooth

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.ReceivedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BlueWhisper_Files"
        const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024  // 25 MB

        val SUPPORTED_MIME_TYPES = arrayOf(
            // Images
            "image/jpeg", "image/png", "image/gif", "image/webp",
            // Documents
            "application/pdf", "text/plain",
            // Audio
            "audio/mpeg", "audio/aac", "audio/ogg", "audio/mp4",
            // Video
            "video/mp4", "video/3gpp", "video/x-matroska"
        )
    }

    // ── Query file info from URI ──────────────────────────────────
    data class FileInfo(
        val name: String,
        val sizeBytes: Long,
        val mimeType: String,
        val fileType: FileType
    )

    fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            var name = "unknown_file"
            var size = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: "unknown_file"
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = name.substringAfterLast('.', "")
            val fileType = FileType.fromExtension(extension)

            FileInfo(name, size, mimeType, fileType)
        } catch (e: Exception) {
            Log.e(TAG, "getFileInfo failed: ${e.message}")
            null
        }
    }

    // ── Validate file before sending ─────────────────────────────
    sealed class ValidationResult {
        object OK : ValidationResult()
        data class TooLarge(val sizeMb: Float) : ValidationResult()
        data class UnsupportedType(val mimeType: String) : ValidationResult()
        object FileNotFound : ValidationResult()
    }

    fun validate(uri: Uri): ValidationResult {
        val info = getFileInfo(uri) ?: return ValidationResult.FileNotFound

        if (info.sizeBytes > MAX_FILE_SIZE_BYTES) {
            return ValidationResult.TooLarge(info.sizeBytes / (1024f * 1024f))
        }

        // Allow all our supported types (be generous — Nearby handles arbitrary bytes)
        return ValidationResult.OK
    }

    // ── Open file as ParcelFileDescriptor for Nearby Payload ─────
    fun openFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "Could not open file descriptor: ${e.message}")
            null
        }
    }

    // ── Create temp file for incoming transfer ────────────────────
    fun createTempFile(fileName: String): File {
        val tempDir = File(context.cacheDir, "received").also { it.mkdirs() }
        return File(tempDir, "${System.currentTimeMillis()}_$fileName")
    }

    // ── Copy incoming Nearby file payload to our temp location ───
    fun copyPayloadToTemp(payloadFile: File, targetName: String): String {
        val destFile = createTempFile(targetName)
        payloadFile.copyTo(destFile, overwrite = true)
        return destFile.absolutePath
    }

    // ── Secure delete temp file (zero-overwrite) ─────────────────
    fun secureDelete(path: String) {
        val file = File(path)
        if (!file.exists()) return
        try {
            FileOutputStream(file).use { fos ->
                val zeros = ByteArray(4096)
                var remaining = file.length()
                while (remaining > 0) {
                    val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
                    fos.write(zeros, 0, toWrite)
                    remaining -= toWrite
                }
            }
        } catch (e: Exception) { /* best effort */ }
        file.delete()
    }

    // ── Delete all temp received files (called on wipe) ──────────
    fun deleteAllTempFiles() {
        val tempDir = File(context.cacheDir, "received")
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { secureDelete(it.absolutePath) }
        }
        Log.d(TAG, "All temp files deleted (FR-08.3)")
    }

    // ── Format file size for display ─────────────────────────────
    fun formatSize(bytes: Long): String = when {
        bytes < 1024         -> "${bytes}B"
        bytes < 1024 * 1024  -> "${"%.1f".format(bytes / 1024f)}KB"
        else                 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
    }

    // ── FR-07.4: Public-storage save (visible to user, survives uninstall) ────
    data class SavedLocation(
        val absolutePath: String,    // best-effort legacy path; on Q+ may be a relative MediaStore path
        val contentUri: String?,     // MediaStore content URI as String (Q+); null on legacy
        val mimeType: String         // resolved MIME type used for the save
    )

    /**
     * Copy a temp file into PUBLIC storage so the user can find it in Files / Gallery
     * and it survives app uninstall.
     *
     *  - API 29+ : MediaStore.Downloads/BlueWhisper/Received/<fileName>
     *  - API 26-28: Environment.DIRECTORY_DOWNLOADS/BlueWhisper/Received/<fileName>
     *
     * Throws IOException on failure (caller surfaces "Save failed" toast).
     */
    fun savePublicCopy(
        srcTempPath: String,
        fileName: String,
        fileType: FileType
    ): SavedLocation {
        val src = File(srcTempPath)
        if (!src.exists()) throw java.io.IOException("Source temp file missing")
        val mime = mimeTypeForFile(fileName, fileType)
        val safeName = uniquifyFileName(fileName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(src, safeName, mime)
        } else {
            saveToLegacyDownloads(src, safeName, mime)
        }
    }

    private fun saveViaMediaStore(
        src: File,
        fileName: String,
        mime: String
    ): SavedLocation {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/BlueWhisper/Received/"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw java.io.IOException("MediaStore insert returned null")

        try {
            resolver.openOutputStream(uri).use { out ->
                if (out == null) throw java.io.IOException("Could not open output stream")
                FileInputStream(src).use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { /* best effort */ }
            throw java.io.IOException("MediaStore save failed: ${e.message}", e)
        }

        val displayPath = "Downloads/BlueWhisper/Received/$fileName"
        return SavedLocation(absolutePath = displayPath, contentUri = uri.toString(), mimeType = mime)
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyDownloads(
        src: File,
        fileName: String,
        mime: String
    ): SavedLocation {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val destDir = File(downloads, "BlueWhisper/Received").also { it.mkdirs() }
        val dest = File(destDir, fileName)
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return SavedLocation(absolutePath = dest.absolutePath, contentUri = null, mimeType = mime)
    }

    private fun uniquifyFileName(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext  = if (dot > 0) name.substring(dot) else ""
        return "${base}_${System.currentTimeMillis()}$ext"
    }

    private fun mimeTypeForFile(fileName: String, fileType: FileType): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"         -> "image/png"
            "gif"         -> "image/gif"
            "webp"        -> "image/webp"
            "mp3"         -> "audio/mpeg"
            "aac"         -> "audio/aac"
            "ogg"         -> "audio/ogg"
            "m4a"         -> "audio/mp4"
            "mp4"         -> "video/mp4"
            "3gp"         -> "video/3gpp"
            "mkv"         -> "video/x-matroska"
            "pdf"         -> "application/pdf"
            "txt"         -> "text/plain"
            else -> when (fileType) {
                FileType.IMAGE    -> "image/*"
                FileType.AUDIO    -> "audio/*"
                FileType.VIDEO    -> "video/*"
                FileType.DOCUMENT -> "application/octet-stream"
            }
        }
    }
}
