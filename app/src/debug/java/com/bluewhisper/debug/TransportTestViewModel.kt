package com.bluewhisper.debug

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.bluetooth.BTEvent
import com.bluewhisper.bluetooth.BluetoothTransport
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.bluetooth.OutgoingFile
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.NearbyDevice
import com.bluewhisper.domain.model.PacketType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DEBUG-ONLY harness for on-device validation of [BluetoothTransport] (classic
 * discovery + RFCOMM + KEX/SAS) before the production flip. Injects the concrete
 * transport directly and mirrors its flows into a scrolling log. Not part of the
 * release build (src/debug source set).
 */
@HiltViewModel
class TransportTestViewModel @Inject constructor(
    private val transport: BluetoothTransport,
    private val fileManager: FileManager,
) : ViewModel() {

    data class UiState(
        val log: List<String> = emptyList(),
        val devices: List<NearbyDevice> = emptyList(),
        val state: String = "Idle",
        val sasCode: String? = null,
        val incoming: ConnectionState.IncomingRequest? = null,
        val secure: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    var nickname: String = "Tester"

    init {
        viewModelScope.launch {
            transport.events.collect { e ->
                log("EVENT ${e::class.simpleName}${(e as? BTEvent.KeyConfirmationRequired)?.let { " code=${it.code}" } ?: ""}")
                when (e) {
                    is BTEvent.KeyConfirmationRequired -> _ui.update { it.copy(sasCode = e.code) }
                    is BTEvent.SessionKeyReady -> _ui.update { it.copy(sasCode = null, secure = true) }
                    is BTEvent.KeyConfirmationRejected,
                    is BTEvent.KeyExchangeFailed,
                    is BTEvent.Disconnected -> _ui.update { it.copy(sasCode = null, secure = false) }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            transport.connectionState.collect { s ->
                log("STATE ${s::class.simpleName}")
                _ui.update { it.copy(state = s::class.simpleName ?: "?", incoming = s as? ConnectionState.IncomingRequest) }
            }
        }
        viewModelScope.launch { transport.nearbyDevices.collect { d -> _ui.update { it.copy(devices = d) } } }
        viewModelScope.launch {
            transport.transferProgress.collect { p ->
                log("XFER ${if (p.isIncoming) "in " else "out"} ${p.bytesTransferred}/${p.totalBytes} ${p.status}")
            }
        }
        viewModelScope.launch { transport.incomingPackets.collect { pkt -> log("RECV[${pkt.tp}] ${pkt.c}") } }
        viewModelScope.launch { transport.incomingFiles.collect { f -> log("FILE RECV ${f.metadata.fileName} -> ${f.tempPath}") } }
        viewModelScope.launch { transport.debugLog.collect { line -> log(line) } }
    }

    fun advertise() { transport.startAdvertising(nickname, 1); log("startAdvertising($nickname)") }
    fun discover() { transport.startDiscovery(); log("startDiscovery()") }
    fun connect(peerId: String) { log("requestConnection($peerId)"); transport.requestConnection(peerId, nickname, 1) }
    fun accept() { _ui.value.incoming?.let { transport.acceptConnection(it.endpointId) } }
    fun decline() { _ui.value.incoming?.let { transport.rejectConnection(it.endpointId) } }
    fun confirmSas(ok: Boolean) { log("confirmKeyMatch($ok)"); transport.confirmKeyMatch(ok) }

    fun sendMessage(text: String) {
        val ok = transport.sendMessage(MessagePacket(i = 0, c = text, t = System.currentTimeMillis(), tp = PacketType.TEXT))
        log("sendMessage(\"$text\") -> $ok")
    }

    fun sendFile(uri: Uri) {
        val info = fileManager.getFileInfo(uri)
        if (info == null) { log("sendFile: could not read file"); return }
        val id = transport.sendFile(
            OutgoingFile(uri = uri, fileName = info.name, fileSizeBytes = info.sizeBytes, fileType = info.fileType, senderNickname = nickname)
        )
        log("sendFile(${info.name}, ${info.sizeBytes}B) -> transferId=$id")
    }

    fun stop() { transport.stopAll(); log("stopAll()") }

    private fun log(line: String) {
        _ui.update { it.copy(log = (it.log + line).takeLast(200)) }
    }
}
