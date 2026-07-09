package com.bluewhisper.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.bluewhisper.bluetooth.wire.Control
import com.bluewhisper.domain.model.ActiveSession
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.NearbyDevice
import com.bluewhisper.domain.model.PacketType
import com.bluewhisper.domain.model.SignalStrength
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Bluetooth implementation of [Transport]: Classic inquiry discovery + Classic
 * RFCOMM data channel (see TRANSPORT_CONTRACT.md §3). No Google Play Services, no Wi-Fi
 * radio, no internet — makes US-1.6 literally true and is the most compatible path across
 * brands + old Android.
 *
 * NOT yet bound in DI — `NearbyConnectionsManager` remains the live implementation until
 * this is device-validated. Compiles alongside it.
 */
@Singleton
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionEngine: EncryptionEngine,
    private val messageSerializer: MessageSerializer,
    private val keyExchangeManager: KeyExchangeManager,
    private val fileManager: FileManager,
) : Transport, RfcommConnection.Listener {

    companion object {
        private const val TAG = "BlueWhisper_BT2"
        private const val APP_NAME = "BlueWhisper"
        private val APP_UUID: UUID = UUID.fromString("6b1de9a0-8b1e-4c6a-9b0e-b10e3341a5e7")
        private const val NAME_PREFIX = "BW|"
        private const val STALE_MS = 10_000L
        private const val SCAN_PAUSE_MS = 8_000L
        private const val KEX_TIMEOUT_MS = 20_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ── State ────────────────────────────────────────────────────────────────
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MessagePacket>(replay = 0, extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<MessagePacket> = _incomingPackets.asSharedFlow()

    private val _incomingFiles = MutableSharedFlow<IncomingFile>(replay = 0, extraBufferCapacity = 8)
    override val incomingFiles: SharedFlow<IncomingFile> = _incomingFiles.asSharedFlow()

    private val _transferProgress = MutableSharedFlow<FileTransferProgress>(replay = 0, extraBufferCapacity = 64)
    override val transferProgress: SharedFlow<FileTransferProgress> = _transferProgress.asSharedFlow()

    private val _events = MutableSharedFlow<BTEvent>(replay = 0, extraBufferCapacity = 16)
    override val events: SharedFlow<BTEvent> = _events.asSharedFlow()

    /**
     * DEBUG diagnostics stream. Mirrors internal Logcat lines so the on-device test
     * harness can display them on-screen (no USB cable / adb needed). Not part of the
     * [Transport] interface — only the debug harness, which injects the concrete class,
     * collects it.
     */
    private val _debugLog = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 128)
    val debugLog: SharedFlow<String> = _debugLog.asSharedFlow()

    // ── Session/connection bookkeeping ───────────────────────────────────────
    private var connection: RfcommConnection? = null
    private var session: ActiveSession? = null
    private var localNonce: String = ""
    private var peerNonce: String = ""
    private var localConfirmed = false
    private var remoteConfirmed = false
    private var boundPeerMac: String? = null
    private var kexTimeoutJob: Job? = null
    private val transferInProgress = AtomicBoolean(false)
    private val transferIdGen = AtomicLong(0)

    // ── Discovery ────────────────────────────────────────────────────────────
    private var discovering = false
    private var scanLoopJob: Job? = null
    private var staleJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var savedAdapterName: String? = null

    // ── Advertising ──────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    override fun startAdvertising(nickname: String, avatarId: Int) {
        val a = adapter ?: return
        if (!hasConnectPermission()) { emit(BTEvent.Error("Bluetooth permission missing")); return }
        try {
            if (savedAdapterName == null) savedAdapterName = a.name
            val wanted = "$NAME_PREFIX$nickname|$avatarId"
            a.setName(wanted)
            startServer()
            emit(BTEvent.AdvertisingStarted)
            dlog("advertise: requested adapter name='$wanted', current='${adapterNameSafe()}' — name change is ASYNC and may take a few seconds to broadcast over the air; the peer must ACCEPT the 'make discoverable' system prompt too")
            // NOTE: to be *found by inquiry*, the UI must also launch
            // ACTION_REQUEST_DISCOVERABLE (US-3.4) — the service cannot start an Activity.
        } catch (e: SecurityException) {
            emit(BTEvent.Error("Advertising blocked: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopAdvertising() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        acceptJob?.cancel(); acceptJob = null
        try { if (hasConnectPermission()) savedAdapterName?.let { adapter?.setName(it) } } catch (_: Exception) {}
        savedAdapterName = null
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        val a = adapter ?: return
        acceptJob?.cancel()
        acceptJob = scope.launch {
            try {
                serverSocket = a.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    onSocketAccepted(socket, incoming = true)
                }
            } catch (e: Exception) {
                if (isActive) Log.w(TAG, "accept loop ended: ${e.message}")
            }
        }
    }

    // ── Discovery (classic inquiry) ──────────────────────────────────────────
    override fun startDiscovery() {
        if (discovering) return
        discovering = true
        dlog(
            "discover: sdk=${android.os.Build.VERSION.SDK_INT} btEnabled=${adapter?.isEnabled} " +
                "scanPerm=${hasScanPermission()} connPerm=${hasConnectPermission()} locationServicesOn=${locationEnabled()} " +
                "myAdapterName='${adapterNameSafe()}'"
        )
        if (adapter?.isEnabled != true) dlog("  ⚠ Bluetooth is OFF — turn it on")
        if (!hasScanPermission()) dlog("  ⚠ BLUETOOTH_SCAN / location permission NOT granted — discovery will find nothing")
        if (!locationEnabled()) dlog("  ⚠ Location Services toggle is OFF — classic discovery returns NOTHING on most Android versions; turn it on")
        registerDiscoveryReceiver()
        staleJob = scope.launch {
            while (isActive) {
                delay(2_000)
                val now = System.currentTimeMillis()
                _nearbyDevices.value = _nearbyDevices.value.filter { now - it.lastSeenEpoch < STALE_MS }
            }
        }
        scanLoopJob = scope.launch { runInquiryDutyCycle() }
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        discovering = false
        scanLoopJob?.cancel(); scanLoopJob = null
        staleJob?.cancel(); staleJob = null
        try { if (hasScanPermission()) adapter?.cancelDiscovery() } catch (_: Exception) {}
        discoveryReceiver?.let { try { context.unregisterReceiver(it) } catch (_: Exception) {} }
        discoveryReceiver = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun runInquiryDutyCycle() {
        val a = adapter ?: return
        while (scope.isActive && discovering) {
            try {
                if (hasScanPermission()) {
                    if (a.isDiscovering) a.cancelDiscovery()
                    val started = a.startDiscovery() // fires ACTION_FOUND per device, then ACTION_DISCOVERY_FINISHED
                    dlog("inquiry cycle: startDiscovery() -> $started")
                    if (!started) dlog("  ⚠ startDiscovery() returned false — adapter busy, BT off, or permission/location missing")
                } else {
                    dlog("inquiry cycle skipped: scan permission not granted")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "startDiscovery blocked: ${e.message}")
                dlog("inquiry cycle: startDiscovery blocked (SecurityException): ${e.message}")
            }
            // One inquiry lasts ~12s; ACTION_DISCOVERY_FINISHED restarts via the pause below.
            delay(12_000 + SCAN_PAUSE_MS) // active window + battery pause (US-3.7)
        }
    }

    private fun registerDiscoveryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> onDeviceFound(intent)
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                        dlog("inquiry FINISHED — BlueWhisper peers so far: ${_nearbyDevices.value.size} (duty cycle re-arms)")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        discoveryReceiver = receiver
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun onDeviceFound(intent: Intent) {
        val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

        // The freshly-inquired name (from the inquiry response / EIR). `device.name` is the
        // locally-CACHED name and is frequently null on the FIRST ACTION_FOUND — the real name
        // resolves in a later callback. Prefer the intent extra, fall back to the cached name.
        val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
        val cachedName = try { if (hasConnectPermission()) device.name else null } catch (_: SecurityException) { null }
        val name = extraName ?: cachedName
        val mac = try { device.address } catch (_: SecurityException) { null }

        dlog("found: mac=$mac rssi=$rssi inquiryName='$extraName' cachedName='$cachedName'")

        if (mac == null) { dlog("  -> no MAC (permission?), ignoring"); return }
        if (name == null) { dlog("  -> name not resolved yet; will re-check on next inquiry cycle"); return }
        if (!name.startsWith(NAME_PREFIX)) { dlog("  -> not a BlueWhisper peer, ignoring"); return }

        val parts = name.removePrefix(NAME_PREFIX).split("|")
        val nickname = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return
        val avatarId = parts.getOrNull(1)?.toIntOrNull() ?: 1

        dlog("  -> ✅ BlueWhisper peer: nickname='$nickname' mac=$mac")
        val entry = NearbyDevice(
            endpointId = mac,
            nickname = nickname,
            avatarId = avatarId,
            signalStrength = signalFromRssi(rssi),
            lastSeenEpoch = System.currentTimeMillis(),
        )
        _nearbyDevices.value = _nearbyDevices.value.filter { it.endpointId != mac } + entry
    }

    private fun signalFromRssi(rssi: Int): SignalStrength = when {
        rssi == Short.MIN_VALUE.toInt() -> SignalStrength.MEDIUM // unknown
        rssi >= -60 -> SignalStrength.STRONG
        rssi >= -80 -> SignalStrength.MEDIUM
        else -> SignalStrength.WEAK
    }

    // ── Connection: requester side ───────────────────────────────────────────
    @SuppressLint("MissingPermission")
    override fun requestConnection(peerId: PeerId, myNickname: String, myAvatarId: Int) {
        val a = adapter ?: return
        if (connection != null) { emit(BTEvent.Error("Already connecting")); return }
        _connectionState.value = ConnectionState.Requesting(peerId)
        scope.launch {
            try {
                if (hasScanPermission()) a.cancelDiscovery()
                val device = a.getRemoteDevice(peerId)
                val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect() // blocking
                val conn = bindConnection(socket)
                localNonce = newNonce()
                conn.sendControl(Control.CONNECT_REQUEST, "$myNickname|$myAvatarId|$localNonce".toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "requestConnection failed: ${e.message}")
                _connectionState.value = ConnectionState.Idle
                emit(BTEvent.Error("Could not connect: ${e.message}"))
                teardown()
            }
        }
    }

    // ── Connection: advertiser side (accepted socket) ─────────────────────────
    private fun onSocketAccepted(socket: BluetoothSocket, incoming: Boolean) {
        // One peer only (US-15.7). If already engaged, refuse the newcomer.
        if (connection != null) {
            // Tie-break for a simultaneous mutual attempt is handled in handleConnectRequest.
            if (_connectionState.value !is ConnectionState.Requesting) {
                try { socket.close() } catch (_: Exception) {}
                return
            }
        }
        bindConnection(socket)
        // Advertiser waits for the CONNECT_REQUEST frame before showing Accept/Decline.
    }

    @SuppressLint("MissingPermission")
    private fun bindConnection(socket: BluetoothSocket): RfcommConnection {
        boundPeerMac = try { socket.remoteDevice?.address } catch (_: Exception) { null }
        val conn = RfcommConnection(
            input = socket.inputStream,
            output = socket.outputStream,
            encryptionEngine = encryptionEngine,
            messageSerializer = messageSerializer,
            fileManager = fileManager,
            scope = scope,
            listener = this,
            onCloseSocket = { try { socket.close() } catch (_: Exception) {} },
        )
        connection = conn
        conn.start()
        return conn
    }

    // ── RfcommConnection.Listener ─────────────────────────────────────────────
    override fun onControl(opcode: Byte, payload: ByteArray) {
        when (opcode) {
            Control.CONNECT_REQUEST -> handleConnectRequest(payload)
            Control.CONNECT_ACCEPT -> onRemoteAccepted()
            Control.CONNECT_REJECT -> { emit(BTEvent.ConnectionRejected); teardown() }
            Control.KEX -> routeKex(payload)
            Control.SAS_ACCEPT -> { remoteConfirmed = true; maybeSessionReady() }
            Control.SAS_REJECT -> { emit(BTEvent.KeyConfirmationRejected); teardown() }
            Control.DISCONNECT -> { emit(BTEvent.Disconnected); teardown() }
        }
    }

    private fun handleConnectRequest(payload: ByteArray) {
        val parts = String(payload, Charsets.UTF_8).split("|")
        val nickname = parts.getOrNull(0) ?: "Unknown"
        val avatarId = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val theirNonce = parts.getOrNull(2) ?: ""

        // Simultaneous mutual request (US-15.6): if we have our own outgoing pending,
        // keep exactly one connection deterministically — the peer with the higher nonce wins.
        if (_connectionState.value is ConnectionState.Requesting && localNonce.isNotEmpty()) {
            if (theirNonce <= localNonce) {
                // Our outgoing wins; ignore this inbound.
                return
            }
            // Their request wins; drop our outgoing and continue with this connection.
        }
        peerNonce = theirNonce
        val peerId = boundPeerMac ?: session?.remoteEndpointId ?: "peer"
        _connectionState.value = ConnectionState.IncomingRequest(
            endpointId = peerId,
            requesterNickname = nickname,
            requesterAvatarId = avatarId,
        )
    }

    override fun acceptConnection(peerId: PeerId) {
        val conn = connection ?: return
        val nickname = (_connectionState.value as? ConnectionState.IncomingRequest)?.requesterNickname ?: "Unknown"
        val avatarId = (_connectionState.value as? ConnectionState.IncomingRequest)?.requesterAvatarId ?: 1
        conn.sendControl(Control.CONNECT_ACCEPT)
        beginSession(peerId, nickname, avatarId)
    }

    override fun rejectConnection(peerId: PeerId) {
        connection?.sendControl(Control.CONNECT_REJECT)
        teardown()
    }

    /** Requester received CONNECT_ACCEPT. */
    private fun onRemoteAccepted() {
        val peerId = (_connectionState.value as? ConnectionState.Requesting)?.targetEndpointId ?: "peer"
        val dev = _nearbyDevices.value.find { it.endpointId == peerId }
        beginSession(peerId, dev?.nickname ?: "Unknown", dev?.avatarId ?: 1)
    }

    private fun beginSession(peerId: PeerId, nickname: String, avatarId: Int) {
        val newSession = ActiveSession(remoteEndpointId = peerId, remoteNickname = nickname, remoteAvatarId = avatarId)
        session = newSession
        // Both radios off once connected (battery + one-peer, US-15.7).
        stopDiscovery(); stopAdvertising()
        _connectionState.value = ConnectionState.Connected(newSession)
        emit(BTEvent.Connected(newSession))
        startKeyExchange()
    }

    // ── Key exchange + SAS confirmation ───────────────────────────────────────
    private fun startKeyExchange() {
        if (localNonce.isEmpty()) localNonce = newNonce()
        // Deterministic single initiator: whichever nonce sorts smaller (KeyExchangeManager rule).
        kexTimeoutJob?.cancel()
        kexTimeoutJob = scope.launch {
            delay(KEX_TIMEOUT_MS)
            if (!encryptionEngine.hasSessionKey() || !(localConfirmed && remoteConfirmed)) {
                emit(BTEvent.KeyExchangeFailed("Key exchange timed out"))
                teardown()
            }
        }
        keyExchangeManager.initiate(localNonce, peerNonce.ifEmpty { newNonce() }) { pkt ->
            connection?.sendKex(pkt)
        }
    }

    private fun routeKex(payload: ByteArray) {
        val pkt = try { messageSerializer.unpackMessage(payload) } catch (e: Exception) {
            emit(BTEvent.KeyExchangeFailed("Bad key packet")); teardown(); return
        }
        when (pkt.tp) {
            PacketType.KEY_EXCHANGE -> {
                val done = keyExchangeManager.handleIncoming(pkt) { ack -> connection?.sendKex(ack) }
                if (done) onKeyEstablished()
            }
            PacketType.ACK -> {
                if (keyExchangeManager.handleKeyAck(pkt)) onKeyEstablished()
            }
            else -> Unit
        }
    }

    /** AES key is set on this side; derive SAS and ask the user to confirm (US-6.1). */
    private fun onKeyEstablished() {
        val local = try { encryptionEngine.getLocalPublicKeyBytes() } catch (_: Exception) { return }
        val remote = encryptionEngine.getRemotePublicKey()?.encoded ?: return
        val code = com.bluewhisper.bluetooth.wire.SasGenerator.generate(local, remote)
        emit(BTEvent.KeyConfirmationRequired(code))
    }

    override fun confirmKeyMatch(accepted: Boolean) {
        if (accepted) {
            localConfirmed = true
            connection?.sendControl(Control.SAS_ACCEPT)
            maybeSessionReady()
        } else {
            connection?.sendControl(Control.SAS_REJECT)
            emit(BTEvent.KeyConfirmationRejected)
            teardown()
        }
    }

    private fun maybeSessionReady() {
        if (localConfirmed && remoteConfirmed) {
            kexTimeoutJob?.cancel(); kexTimeoutJob = null
            emit(BTEvent.SessionKeyReady)
        }
    }

    // ── Messaging & files ─────────────────────────────────────────────────────
    override fun sendMessage(packet: MessagePacket): Boolean = connection?.sendMessage(packet) ?: false

    override fun sendFile(file: OutgoingFile): Long? {
        val conn = connection ?: return null
        if (!encryptionEngine.hasSessionKey()) return null
        if (!transferInProgress.compareAndSet(false, true)) return null // one at a time (US-8.6)
        val id = transferIdGen.incrementAndGet()
        val input = try { context.contentResolver.openInputStream(file.uri) } catch (e: Exception) { null }
        if (input == null) { transferInProgress.set(false); return null }
        val meta = FileMetadata(
            payloadId = id,
            fileName = file.fileName,
            fileSizeBytes = file.fileSizeBytes,
            fileType = file.fileType,
            senderNickname = file.senderNickname,
        )
        scope.launch { conn.sendFile(id, meta, input, file.fileSizeBytes) }
        return id
    }

    override fun onMessage(packet: MessagePacket) {
        scope.launch { _incomingPackets.emit(packet) }
    }

    override fun onFileProgress(progress: FileTransferProgress) {
        if (!progress.isIncoming &&
            (progress.status == TransferStatus.SUCCESS || progress.status == TransferStatus.FAILURE)) {
            transferInProgress.set(false)
        }
        scope.launch { _transferProgress.emit(progress) }
    }

    override fun onIncomingFile(file: IncomingFile) {
        scope.launch { _incomingFiles.emit(file) }
    }

    override fun onClosed(reason: String?) {
        if (reason != null && session != null) {
            emit(BTEvent.Disconnected)
        }
        teardown()
    }

    // ── Teardown ──────────────────────────────────────────────────────────────
    override fun disconnect() {
        connection?.sendControl(Control.DISCONNECT)
        teardown()
    }

    override fun stopAll() {
        stopDiscovery()
        stopAdvertising()
        teardown()
    }

    override fun connectedPeerId(): PeerId? = session?.remoteEndpointId

    private fun teardown() {
        kexTimeoutJob?.cancel(); kexTimeoutJob = null
        connection?.close(); connection = null
        session = null
        localNonce = ""; peerNonce = ""
        localConfirmed = false; remoteConfirmed = false
        boundPeerMac = null
        transferInProgress.set(false)
        encryptionEngine.clearSession()
        keyExchangeManager.reset()
        _connectionState.value = ConnectionState.Idle
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private fun emit(event: BTEvent) { scope.launch { _events.emit(event) } }

    /** Log to Logcat AND to the on-screen debug stream (so device testing needs no adb cable). */
    private fun dlog(msg: String) {
        Log.d(TAG, msg)
        scope.launch { _debugLog.emit(msg) }
    }

    @SuppressLint("MissingPermission")
    private fun adapterNameSafe(): String? =
        try { if (hasConnectPermission()) adapter?.name else null } catch (_: Exception) { null }

    /** Whether the system Location Services toggle is ON — required for classic discovery on most Android versions. */
    private fun locationEnabled(): Boolean = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        lm != null && LocationManagerCompat.isLocationEnabled(lm)
    } catch (_: Exception) { false }

    private fun newNonce(): String = "%016x".format(SecureRandom().nextLong())

    private fun hasConnectPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
}
