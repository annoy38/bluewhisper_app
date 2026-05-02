package com.bluewhisper.presentation.screens.chat

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.bluetooth.BTEvent
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.*
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

const val MAX_MESSAGES = 20

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageCount: Int = 0,
    val isLimitReached: Boolean = false,
    val remainingMessages: Int = MAX_MESSAGES,
    val remoteUser: ActiveSession? = null,
    val isConnected: Boolean = false,
    val isEncrypted: Boolean = false,
    val isTyping: Boolean = false,
    val currentInput: String = "",
    val activeFileTransfer: FileTransferState? = null,
    val receivedFiles: Map<String, ReceivedFile> = emptyMap(),
    val showFilePicker: Boolean = false,
    val toastMessage: String? = null,
    // FR-07.4: countdown lives in ChatViewModel so it survives viewer close/reopen
    val countdownFileId: String? = null,
    val countdownSeconds: Int = 10,
    val countdownActive: Boolean = false
)

data class FileTransferState(
    val payloadId: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val bytesTransferred: Long = 0,
    val isIncoming: Boolean,
    val status: TransferStatus = TransferStatus.IN_PROGRESS
)

enum class TransferStatus { IN_PROGRESS, SUCCESS, FAILED, CANCELLED }

sealed class ChatEvent {
    object Disconnected : ChatEvent()
    data class FileReceived(val fileId: String) : ChatEvent()
    data class Error(val msg: String) : ChatEvent()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val nearbyManager: NearbyConnectionsManager,
    private val fileManager: FileManager,
    private val gson: Gson,
    private val userProfileDataStore: UserProfileDataStore
) : ViewModel() {

    private val TAG = "BlueWhisper_Chat"

    // NFR-02.4: WakeLock held during file transfer, released within 5s of completion
    private val wakeLock: PowerManager.WakeLock by lazy {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BlueWhisper:FileTransfer")
            .apply { setReferenceCounted(false) }
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    // Concurrent maps — accessed from incoming-packet collector and file-payload collector
    private val pendingFileMeta  = java.util.concurrent.ConcurrentHashMap<Long, FileMetadata>()
    private val pendingPayloads  = java.util.concurrent.ConcurrentHashMap<Long, Payload>()
    private var typingDebounceJob: Job? = null
    private var typingHideJob: Job? = null
    // FR-07.4: countdown owned by ChatViewModel — survives FILE_VIEWER open/close.
    // Delegates to VanishTimer (Tier 4 #20) so the timer lifecycle is one
    // separable responsibility we can test in isolation.
    private val vanishTimer = VanishTimer(viewModelScope)

    init {
        observeConnectionState()
        observeIncomingPackets()
        observeFilePayloads()
        observeTransferUpdates()
        observeBTEvents()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            nearbyManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected ->
                        _uiState.update { it.copy(remoteUser = state.session, isConnected = true) }
                    is ConnectionState.Idle -> {
                        if (_uiState.value.isConnected) {
                            wipeSessionData()
                            _events.emit(ChatEvent.Disconnected)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeIncomingPackets() {
        viewModelScope.launch {
            nearbyManager.incomingPackets.collect { packet ->
                when (packet.tp) {
                    PacketType.TEXT      -> handleIncomingText(packet)
                    PacketType.TYPING    -> handleTypingIndicator()
                    PacketType.FILE_META -> handleFileMeta(packet)
                    PacketType.DISCONNECT -> { wipeSessionData(); _events.emit(ChatEvent.Disconnected) }
                    else -> Unit
                }
            }
        }
    }

    private fun observeFilePayloads() {
        viewModelScope.launch {
            nearbyManager.filePayloads.collect { (payloadId, payload) ->
                handleIncomingFilePayload(payloadId, payload)
            }
        }
    }

    private fun observeTransferUpdates() {
        viewModelScope.launch {
            nearbyManager.transferUpdates.collect { update ->
                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS ->
                        _uiState.update { state ->
                            state.copy(activeFileTransfer = state.activeFileTransfer
                                ?.takeIf { it.payloadId == update.payloadId }
                                ?.copy(bytesTransferred = update.bytesTransferred)
                                ?: state.activeFileTransfer)
                        }
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        // NFR-02.4: release WakeLock within 5s of completion
                        if (wakeLock.isHeld) wakeLock.release()
                        _uiState.update { it.copy(activeFileTransfer = it.activeFileTransfer?.copy(status = TransferStatus.SUCCESS)) }
                        viewModelScope.launch { delay(2000); _uiState.update { it.copy(activeFileTransfer = null) } }
                    }
                    PayloadTransferUpdate.Status.FAILURE -> {
                        // NFR-02.4: release WakeLock on failure too
                        if (wakeLock.isHeld) wakeLock.release()
                        _uiState.update { it.copy(activeFileTransfer = it.activeFileTransfer?.copy(status = TransferStatus.FAILED), toastMessage = "File transfer failed") }
                        viewModelScope.launch { delay(3000); _uiState.update { it.copy(activeFileTransfer = null) } }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeBTEvents() {
        viewModelScope.launch {
            nearbyManager.events.collect { event ->
                when (event) {
                    is BTEvent.Disconnected -> { wipeSessionData(); _events.emit(ChatEvent.Disconnected) }
                    is BTEvent.SessionKeyReady -> _uiState.update { it.copy(isEncrypted = true) }
                    else -> Unit
                }
            }
        }
    }

    // ── FR-04.1 Send text ─────────────────────────────────────────
    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isLimitReached) return
        if (content.length > 200) return   // FR-04.5
        // NFR-03.1 / NFR-03.2: NEVER send a message before key exchange is complete.
        // If encryption is not ready yet, silently discard — the UI shows a
        // "Securing connection…" overlay that prevents the send button from being tapped.
        if (!_uiState.value.isEncrypted) {
            Log.w(TAG, "sendMessage blocked — key exchange not complete (NFR-03.1)")
            return
        }
        val index = _uiState.value.messageCount
        val packet = MessagePacket(i = index, c = content.trim(), t = System.currentTimeMillis(), tp = PacketType.TEXT)
        if (nearbyManager.sendMessage(packet)) {
            addMessage(Message(index = index, content = content.trim(), direction = MessageDirection.SENT, type = MessageType.TEXT))
        }
    }

    // ── FR-04.4 Typing debounce ───────────────────────────────────
    fun onTypingChanged(input: String) {
        _uiState.update { it.copy(currentInput = input) }
        typingDebounceJob?.cancel()
        if (input.isNotEmpty()) {
            typingDebounceJob = viewModelScope.launch {
                delay(2000)
                nearbyManager.sendMessage(MessagePacket(i = _uiState.value.messageCount, c = "", t = System.currentTimeMillis(), tp = PacketType.TYPING))
            }
        }
    }

    private fun handleIncomingText(packet: MessagePacket) {
        if (_uiState.value.isLimitReached) return
        addMessage(Message(index = _uiState.value.messageCount, content = packet.c, timestampEpoch = packet.t, direction = MessageDirection.RECEIVED, type = MessageType.TEXT))
    }

    private fun handleTypingIndicator() {
        typingHideJob?.cancel()
        _uiState.update { it.copy(isTyping = true) }
        typingHideJob = viewModelScope.launch { delay(3000); _uiState.update { it.copy(isTyping = false) } }
    }

    // ── FR-06 File metadata ───────────────────────────────────────
    private fun handleFileMeta(packet: MessagePacket) {
        try {
            val meta = gson.fromJson(packet.c, FileMetadata::class.java)
            // If the payload arrived first (race / re-order), reconcile now.
            val bufferedPayload = pendingPayloads.remove(meta.payloadId)
            if (bufferedPayload != null) {
                _uiState.update { it.copy(activeFileTransfer = FileTransferState(payloadId = meta.payloadId, fileName = meta.fileName, fileSizeBytes = meta.fileSizeBytes, isIncoming = true)) }
                viewModelScope.launch { materializeIncomingFile(meta.payloadId, bufferedPayload, meta) }
            } else {
                pendingFileMeta[meta.payloadId] = meta
                _uiState.update { it.copy(activeFileTransfer = FileTransferState(payloadId = meta.payloadId, fileName = meta.fileName, fileSizeBytes = meta.fileSizeBytes, isIncoming = true)) }
            }
            Log.d(TAG, "File meta: ${meta.fileName}")
        } catch (e: Exception) { Log.e(TAG, "handleFileMeta: ${e.message}") }
    }

    // ── FR-06 File payload ────────────────────────────────────────
    private suspend fun handleIncomingFilePayload(payloadId: Long, payload: Payload) {
        // Tier 3 #15 (B-11): if FILE_META has not arrived yet, buffer the payload
        // and reconcile when meta lands. Previously the file was silently dropped.
        val meta = pendingFileMeta.remove(payloadId)
        if (meta == null) {
            pendingPayloads[payloadId] = payload
            Log.d(TAG, "Buffered payload $payloadId waiting for FILE_META")
            return
        }
        materializeIncomingFile(payloadId, payload, meta)
    }

    private suspend fun materializeIncomingFile(
        payloadId: Long,
        payload: Payload,
        meta: FileMetadata
    ) {
        try {
            val nearbyFile = payload.asFile()?.asJavaFile() ?: return
            val tempPath = withContext(Dispatchers.IO) { fileManager.copyPayloadToTemp(nearbyFile, meta.fileName) }
            // Resolve sender: prefer the nickname carried in FILE_META; fall back
            // to the connected session nickname so saved files always have origin.
            val sender = meta.senderNickname.takeIf { it.isNotBlank() }
                ?: _uiState.value.remoteUser?.remoteNickname.orEmpty()
            val receivedFile = ReceivedFile(
                payloadId = payloadId,
                fileName = meta.fileName,
                fileSizeBytes = meta.fileSizeBytes,
                fileType = meta.fileType,
                tempPath = tempPath,
                senderNickname = sender,
                state = FileState.RECEIVED_UNVIEWED
            )
            addReceivedFile(receivedFile)
            _events.emit(ChatEvent.FileReceived(receivedFile.id))
        } catch (e: Exception) {
            Log.e(TAG, "handleIncomingFilePayload: ${e.message}")
            _uiState.update { it.copy(toastMessage = "File receive failed") }
        }
    }

    // ── FR-06 Send file ───────────────────────────────────────────
    fun sendFile(uri: Uri) {
        if (_uiState.value.activeFileTransfer != null) return  // FR-06.5 one at a time
        val endpointId = nearbyManager.getConnectedEndpointId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val info = fileManager.getFileInfo(uri) ?: run { showToast("Could not read file"); return@launch }
            if (info.sizeBytes > FileManager.MAX_FILE_SIZE_BYTES) {
                showToast("File too large (${"%.1f".format(info.sizeBytes / (1024f * 1024f))}MB). Max 25MB.")
                return@launch
            }
            val pfd = fileManager.openFileDescriptor(uri) ?: run { showToast("Cannot open file"); return@launch }
            try {
                val filePayload = Payload.fromParcelFileDescriptor(pfd)
                // Resolve our own nickname so the receiver can attribute the file (FR-10).
                val myNickname = runCatching {
                    userProfileDataStore.userProfile.first().nickname
                }.getOrDefault("")
                val meta = FileMetadata(
                    payloadId = filePayload.id,
                    fileName = info.name,
                    fileSizeBytes = info.sizeBytes,
                    fileType = info.fileType,
                    senderNickname = myNickname
                )
                nearbyManager.sendMessage(MessagePacket(i = _uiState.value.messageCount, c = gson.toJson(meta), t = System.currentTimeMillis(), tp = PacketType.FILE_META))
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(activeFileTransfer = FileTransferState(payloadId = filePayload.id, fileName = info.name, fileSizeBytes = info.sizeBytes, isIncoming = false), showFilePicker = false) }
                }
                // NFR-02.4: acquire WakeLock to prevent CPU sleep during transfer.
                // Tier 3 #11: size the timeout to file bytes — Bluetooth Classic via
                // Nearby is ~150–500 KB/s, so a 25 MB file can take 50–170 s and
                // a 35 s timeout would drop the lock mid-transfer.
                if (!wakeLock.isHeld) wakeLock.acquire(computeWakeLockTimeoutMs(info.sizeBytes))
                nearbyManager.sendFile(endpointId, filePayload)
            } catch (e: Exception) {
                showToast("Send failed: ${e.message}")
            }
        }
    }

    // Tier 3 #11 (B-08): WakeLock timeout sized to file bytes.
    // Conservative throughput: ~100 KB/s lower-bound for BT Classic via Nearby.
    // Add 30 s slack for handshake; cap at 10 minutes for safety.
    internal fun computeWakeLockTimeoutMs(fileSizeBytes: Long): Long {
        val estimated = fileSizeBytes / 100L + 30_000L
        return estimated.coerceIn(30_000L, 600_000L)
    }

    fun showFilePicker() { if (_uiState.value.activeFileTransfer == null) _uiState.update { it.copy(showFilePicker = true) } }
    fun hideFilePicker() { _uiState.update { it.copy(showFilePicker = false) } }

    // ── FR-04.7 both directions count ────────────────────────────
    private fun addMessage(message: Message) {
        val newCount = _uiState.value.messageCount + 1
        _uiState.update { state ->
            state.copy(messages = state.messages + message, messageCount = newCount, isLimitReached = newCount >= MAX_MESSAGES, remainingMessages = MAX_MESSAGES - newCount)
        }
    }

    fun addReceivedFile(file: ReceivedFile) {
        _uiState.update { state ->
            val note = Message(index = state.messageCount, content = "📎 ${file.fileName} · ${fileManager.formatSize(file.fileSizeBytes)}", direction = MessageDirection.RECEIVED, type = MessageType.FILE_NOTIFICATION, fileReference = file.id)
            val newCount = state.messageCount + 1
            state.copy(
                receivedFiles = state.receivedFiles + (file.id to file),
                messages = state.messages + note,
                messageCount = newCount,
                isLimitReached = newCount >= MAX_MESSAGES,
                remainingMessages = MAX_MESSAGES - newCount
            )
        }
    }

    fun updateReceivedFileState(fileId: String, newState: FileState) {
        _uiState.update { state ->
            val updated = state.receivedFiles.toMutableMap()
            updated[fileId] = updated[fileId]?.copy(state = newState) ?: return@update state
            state.copy(receivedFiles = updated)
        }
    }

    // ── FR-07.4: Countdown owned by ChatViewModel ────────────────
    fun startFileCountdown(fileId: String) {
        if (_uiState.value.countdownActive && _uiState.value.countdownFileId == fileId) return
        _uiState.update { it.copy(countdownFileId = fileId, countdownSeconds = 10, countdownActive = true) }
        vanishTimer.start(
            fileId = fileId,
            onTick = { remaining ->
                _uiState.update { it.copy(countdownSeconds = remaining) }
            },
            onZero = {
                val file = _uiState.value.receivedFiles[fileId]
                if (file != null && file.state != FileState.SAVED) {
                    viewModelScope.launch(Dispatchers.IO) {
                        fileManager.secureDelete(file.tempPath)
                    }
                    updateReceivedFileState(fileId, FileState.VANISHED)
                }
                _uiState.update { it.copy(countdownActive = false, countdownFileId = null) }
            }
        )
    }

    fun stopFileCountdown(fileId: String) {
        if (_uiState.value.countdownFileId == fileId) {
            vanishTimer.cancelFor(fileId)
            _uiState.update { it.copy(countdownActive = false, countdownFileId = null) }
        }
    }

    fun getCountdownSeconds(fileId: String): Int =
        if (_uiState.value.countdownFileId == fileId) _uiState.value.countdownSeconds else 10

    fun disconnect() { nearbyManager.disconnect(); wipeSessionData() }
    fun clearToast() { _uiState.update { it.copy(toastMessage = null) } }

    // ── FR-08.1 FR-08.3 Wipe all session data from RAM ───────────
    private fun wipeSessionData() {
        typingDebounceJob?.cancel(); typingHideJob?.cancel()
        vanishTimer.cancel(); pendingFileMeta.clear(); pendingPayloads.clear()
        if (wakeLock.isHeld) wakeLock.release()  // NFR-02.4: always release on wipe
        viewModelScope.launch(Dispatchers.IO) { fileManager.deleteAllTempFiles() }
        _uiState.value = ChatUiState()
        Log.d(TAG, "Session wiped ✅")
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) { _uiState.update { it.copy(toastMessage = msg) } }

    // ── FR-08.2 Trigger 2: app swipe-away ─────────────────────────
    // When user swipes the app away, ViewModel.onCleared() is called.
    // If we are mid-session, trigger the full wipe chain.
    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isConnected) {
            // Disconnect from Nearby Connections (fires onDisconnected on peer)
            nearbyManager.stopAll()
            // Wipe local data immediately (can't use coroutines after onCleared safely)
            viewModelScope.launch(Dispatchers.IO) {
                fileManager.deleteAllTempFiles()
            }
            // Clear state synchronously
            typingDebounceJob?.cancel()
            typingHideJob?.cancel()
            pendingFileMeta.clear(); pendingPayloads.clear()
            Log.d(TAG, "onCleared: session wiped (FR-08.2 swipe-away trigger)")
        }
    }
}
