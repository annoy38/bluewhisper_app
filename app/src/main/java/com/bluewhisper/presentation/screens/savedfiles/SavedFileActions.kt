package com.bluewhisper.presentation.screens.savedfiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bluewhisper.data.local.SavedFileEntity
import java.io.File

/**
 * FR-10.3: Open / Share helpers for saved files.
 *
 *  - Q+ (mediaStoreUri != null): grant URI permissions on the MediaStore content URI directly.
 *  - Legacy (mediaStoreUri == null): wrap the absolute File path with a FileProvider URI.
 *
 * On any failure (no app to handle the intent, file gone, etc.) we show a Toast and log,
 * never crash.
 */
object SavedFileActions {

    private const val TAG = "BlueWhisper_SavedFile"

    fun openFile(context: Context, file: SavedFileEntity) {
        val uri = resolveUri(context, file) ?: run {
            toast(context, "File no longer available")
            return
        }
        val mime = file.mimeType ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "openFile failed: ${e.message}")
            toast(context, "No app available to open this file")
        }
    }

    fun shareFile(context: Context, file: SavedFileEntity) {
        val uri = resolveUri(context, file) ?: run {
            toast(context, "File no longer available")
            return
        }
        val mime = file.mimeType ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share file").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "shareFile failed: ${e.message}")
            toast(context, "No app available to share this file")
        }
    }

    private fun resolveUri(context: Context, file: SavedFileEntity): Uri? {
        // Q+ path — stored MediaStore content URI
        file.mediaStoreUri?.let {
            return try { Uri.parse(it) } catch (_: Exception) { null }
        }
        // Legacy path — wrap absolute File via FileProvider
        val f = File(file.localPath)
        if (!f.exists()) return null
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, f)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider.getUriForFile failed: ${e.message}")
            null
        }
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
