package com.bluewhisper.presentation.screens.fileviewer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.data.local.SavedFileDao
import com.bluewhisper.data.local.SavedFileEntity
import com.bluewhisper.domain.model.FileState
import com.bluewhisper.domain.model.ReceivedFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject

data class FileViewerUiState(
    val file: ReceivedFile? = null,
    val remainingSeconds: Int = 10,
    val isCounting: Boolean = false,
    val isSaved: Boolean = false,
    val isVanished: Boolean = false,
    val saveError: String? = null,
    // FR-07.4: friendly path shown to user after save (e.g. "Downloads/BlueWhisper/Received/foo.jpg")
    val savedDisplayPath: String? = null
)

sealed class FileViewerEvent {
    object FileVanished : FileViewerEvent()
    data class FileSaved(val path: String) : FileViewerEvent()
    data class Error(val message: String) : FileViewerEvent()
}

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedFileDao: SavedFileDao,
    private val fileManager: FileManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileViewerEvent>(
        replay = 0, extraBufferCapacity = 4
    )
    val events: SharedFlow<FileViewerEvent> = _events.asSharedFlow()

    private var countdownJob: Job? = null

    // ── Load file from navigation argument or direct call ─────────
    // Navigation passes the ReceivedFile as a parcelable via SavedStateHandle
    fun loadFile(file: ReceivedFile) {
        if (_uiState.value.file != null) return  // already loaded
        _uiState.update { it.copy(file = file) }
    }

    // ── FR-07.2: Start 10-second countdown — ONLY on View tap ────
    fun startCountdown() {
        if (countdownJob != null) return          // already running
        if (_uiState.value.isSaved || _uiState.value.isVanished) return

        _uiState.update {
            it.copy(
                isCounting = true,
                remainingSeconds = 10,
                file = it.file?.copy(
                    state = FileState.VIEWING,
                    viewedAtEpoch = System.currentTimeMillis(),
                    vanishAtEpoch = System.currentTimeMillis() + 10_000
                )
            )
        }

        // FR-07.4: countdownJob in viewModelScope — survives screen close
        countdownJob = viewModelScope.launch {
            repeat(10) { tick ->
                delay(1000L)
                val remaining = 9 - tick
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining == 0) vanishFile()
            }
        }
    }

    // ── FR-07.5: Save — stops countdown, copies to permanent storage
    fun saveFile() {
        val file = _uiState.value.file ?: return
        if (_uiState.value.isVanished) return   // race: vanish won

        // Atomically stop countdown BEFORE any async work
        countdownJob?.cancel()
        countdownJob = null

        viewModelScope.launch {
            try {
                // FR-07.4 FIX: save to PUBLIC storage (MediaStore.Downloads on Q+,
                // public Downloads dir on legacy) so the file survives uninstall and
                // is visible in the system Files app.
                val saved = withContext(Dispatchers.IO) {
                    fileManager.savePublicCopy(
                        srcTempPath = file.tempPath,
                        fileName = file.fileName,
                        fileType = file.fileType
                    )
                }

                val entity = SavedFileEntity(
                    fileId = file.id,
                    fileName = file.fileName,
                    fileExtension = file.fileName.substringAfterLast('.', ""),
                    fileSizeBytes = file.fileSizeBytes,
                    fileType = file.fileType.name,
                    localPath = saved.absolutePath,
                    thumbnailPath = null,
                    // Tier 2 #6: real nickname threaded via FILE_META → ReceivedFile
                    senderNickname = file.senderNickname.ifBlank { "Unknown" },
                    receivedAtEpoch = file.receivedAtEpoch,
                    savedAtEpoch = System.currentTimeMillis(),
                    mediaStoreUri = saved.contentUri,
                    mimeType = saved.mimeType
                )
                savedFileDao.insertFile(entity)

                _uiState.update {
                    it.copy(
                        isSaved = true,
                        isCounting = false,
                        file = it.file?.copy(state = FileState.SAVED),
                        savedDisplayPath = saved.absolutePath
                    )
                }
                _events.emit(FileViewerEvent.FileSaved(saved.absolutePath))

            } catch (e: IOException) {
                _uiState.update { it.copy(saveError = "Not enough storage space") }
                _events.emit(FileViewerEvent.Error("Save failed: ${e.message}"))
            } catch (e: Exception) {
                _uiState.update { it.copy(saveError = "Save failed") }
                _events.emit(FileViewerEvent.Error("Save failed: ${e.message}"))
            }
        }
    }

    // ── FR-07.6: Vanish — secure delete from cache ─────────────
    private suspend fun vanishFile() {
        val file = _uiState.value.file ?: return
        if (_uiState.value.isSaved) return  // save won the race

        withContext(Dispatchers.IO) {
            fileManager.secureDelete(file.tempPath)
        }

        _uiState.update {
            it.copy(
                isVanished = true,
                isCounting = false,
                remainingSeconds = 0,
                file = it.file?.copy(state = FileState.VANISHED)
            )
        }
        _events.emit(FileViewerEvent.FileVanished)
    }

    // ── FR-07.7: Wipe unsaved file on disconnect ─────────────────
    fun vanishOnDisconnect() {
        if (_uiState.value.isSaved || _uiState.value.isVanished) return
        countdownJob?.cancel()
        countdownJob = null
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.file?.let { fileManager.secureDelete(it.tempPath) }
        }
        _uiState.update { it.copy(isVanished = true, isCounting = false) }
    }

    // ── Cleanup on ViewModel cleared (FR-08 safety net) ──────────
    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        if (!_uiState.value.isSaved && !_uiState.value.isVanished) {
            _uiState.value.file?.let { file ->
                // B-22 fix: viewModelScope is cancelled by super.onCleared(),
                // so a launch(viewModelScope) here would no-op. Spawn a
                // detached daemon thread instead — the work is bounded
                // (single file ≤ 25 MB) and we don't need to outlive process.
                Thread {
                    fileManager.secureDelete(file.tempPath)
                }.apply { isDaemon = true }.start()
            }
        }
    }
}
