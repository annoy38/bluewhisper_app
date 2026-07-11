package com.bluewhisper.bluetooth

import android.content.Context
import android.util.Log
import com.bluewhisper.domain.model.*
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyConnectionsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionEngine: EncryptionEngine,
    private val messageSerializer: MessageSerializer,
    private val keyExchangeManager: KeyExchangeManager
) {

    companion object {
        private const val TAG = "BlueWhisper_BT"
        private const val SERVICE_ID = "com.bluewhisper.app"
        private const val SCAN_ACTIVE_MS = 20_000L
        private const val SCAN_PAUSE_MS = 8_000L
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── State Flows (observed by ViewModels) ──────────────────────
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<MessagePacket>(
        replay = 0, extraBufferCapacity = 64
    )
    val incomingPackets: SharedFlow<MessagePacket> = _incomingPackets.asSharedFlow()

    private val _filePayloads = MutableSharedFlow<Pair<Long, Payload>>(
        replay = 0, extraBufferCapacity = 8
    )
    val filePayloads: SharedFlow<Pair<Long, Payload>> = _filePayloads.asSharedFlow()

    private val _transferUpdates = MutableSharedFlow<PayloadTransferUpdate>(
        replay = 0, extraBufferCapacity = 32
    )
    val transferUpdates: SharedFlow<PayloadTransferUpdate> = _transferUpdates.asSharedFlow()

    private val _events = MutableSharedFlow<BTEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<BTEvent> = _events.asSharedFlow()

    private var scanningJob: Job? = null
    private var staleCheckJob: Job? = null      // FR-02.5 stale device removal
    private var connectedEndpointId: String? = null

    // FR-02.5: remove devices not seen for >10 seconds
    private fun startStaleDeviceCheck() {
        staleCheckJob?.cancel()
        staleCheckJob = scope.launch {
            while (isActive) {
                delay(2000)  // check every 2s
                val now = System.currentTimeMillis()
                val staleThreshold = 10_000L
                _nearbyDevices.value = _nearbyDevices.value
                    .filter { now - it.lastSeenEpoch < staleThreshold }
            }
        }
    }

    private fun stopStaleDeviceCheck() {
        staleCheckJob?.cancel()
        staleCheckJob = null
    }

    // FR-02.3: estimate signal strength from discovery timing
    // Nearby Connections does not expose RSSI directly — we use refresh frequency
    // as a proxy: devices found quickly on repeated scans = close, found late = far
    private fun estimateSignalStrength(firstSeenEpoch: Long): SignalStrength {
        val elapsed = System.currentTimeMillis() - firstSeenEpoch
        return when {
            elapsed < 2_000  -> SignalStrength.STRONG  // found in <2s = very close
            elapsed < 6_000  -> SignalStrength.MEDIUM  // 2–6s = nearby
            else             -> SignalStrength.WEAK    // >6s  = far
        }
    }
    private var connectedNickname: String? = null
    // Initiator election: the REQUESTER (discoverer) generates the AES key.
    // We track this by whether our connection is incoming (we are advertiser/responder)
    // or outgoing (we are discoverer/initiator).
    private var weAreInitiator: Boolean = false

    // ── Advertising ───────────────────────────────────────────────
    fun startAdvertising(nickname: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startAdvertising(
            nickname,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started as: $nickname")
            emitEvent(BTEvent.AdvertisingStarted)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}")
            emitEvent(BTEvent.Error("Advertising failed: ${e.message}"))
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        Log.d(TAG, "Advertising stopped")
    }

    // ── Discovery ─────────────────────────────────────────────────
    fun startDiscovery() {
        scanningJob?.cancel()
        startStaleDeviceCheck()          // FR-02.5
        scanningJob = scope.launch {
            while (isActive) {
                performSingleScan()
                delay(SCAN_PAUSE_MS)
            }
        }
    }

    private fun performSingleScan() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                Log.d(TAG, "Discovery started")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed: ${e.message}")
            }

        // Stop after SCAN_ACTIVE_MS (duty cycle)
        scope.launch {
            delay(SCAN_ACTIVE_MS)
            connectionsClient.stopDiscovery()
            Log.d(TAG, "Discovery paused (duty cycle)")
        }
    }

    fun stopDiscovery() {
        scanningJob?.cancel()
        scanningJob = null
        stopStaleDeviceCheck()           // FR-02.5
        connectionsClient.stopDiscovery()
        Log.d(TAG, "Discovery stopped")
    }

    // ── Connection Request ────────────────────────────────────────
    fun requestConnection(endpointId: String, myNickname: String) {
        _connectionState.value = ConnectionState.Requesting(endpointId)

        connectionsClient.requestConnection(
            myNickname,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection request sent to: $endpointId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Connection request failed: ${e.message}")
            _connectionState.value = ConnectionState.Idle
            emitEvent(BTEvent.Error("Connection failed: ${e.message}"))
        }
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
        Log.d(TAG, "Connection accepted: $endpointId")
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        Log.d(TAG, "Connection rejected: $endpointId")
        _connectionState.value = ConnectionState.Idle
    }

    fun disconnect() {
        connectedEndpointId?.let { endpointId ->
            // Send disconnect signal before disconnecting
            try {
                val packet = messageSerializer.createDisconnectPacket(0)
                sendPacketRaw(endpointId, packet)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send disconnect packet: ${e.message}")
            }
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
        cleanupSession()
    }

    fun stopAll() {
        stopDiscovery()
        stopAdvertising()
        connectionsClient.stopAllEndpoints()
        cleanupSession()
    }

    // ── Send Message ──────────────────────────────────────────────
    fun sendMessage(packet: MessagePacket): Boolean {
        val endpointId = connectedEndpointId ?: return false
        return sendPacketRaw(endpointId, packet)
    }

    private fun sendPacketRaw(endpointId: String, packet: MessagePacket): Boolean {
        return try {
            val packed = messageSerializer.packMessage(packet)
            val encrypted = if (encryptionEngine.hasSessionKey()) {
                encryptionEngine.encrypt(packed)
            } else packed  // Pre-key-exchange packets (KEY_EXCHANGE type)

            val payload = Payload.fromBytes(encrypted)
            connectionsClient.sendPayload(endpointId, payload)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            false
        }
    }

    // ── Send File ─────────────────────────────────────────────────
    fun sendFile(endpointId: String, payload: Payload): Boolean {
        return try {
            connectionsClient.sendPayload(endpointId, payload)
            true
        } catch (e: Exception) {
            Log.e(TAG, "File send failed: ${e.message}")
            false
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")

            // Check if we've seen this device before to estimate signal
            val existingDevice = _nearbyDevices.value.find { it.endpointId == endpointId }
            val firstSeen = existingDevice?.lastSeenEpoch ?: System.currentTimeMillis()

            // endpointName is "nickname|avatarId" — split it
            val parts = info.endpointName.split("|")
            val parsedNickname = parts.getOrNull(0) ?: info.endpointName

            val device = NearbyDevice(
                endpointId = endpointId,
                nickname = parsedNickname,
                avatarId = parseAvatarFromName(info.endpointName),
                signalStrength = estimateSignalStrength(firstSeen),  // FR-02.3
                lastSeenEpoch = System.currentTimeMillis()
            )

            _nearbyDevices.value = _nearbyDevices.value
                .filter { it.endpointId != endpointId }
                .plus(device)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            _nearbyDevices.value = _nearbyDevices.value
                .filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from: $endpointId (${info.endpointName})")

            // Discoverer (outgoing) = initiator; Advertiser (incoming) = responder
            // This is deterministic — exactly one side is the discoverer per connection
            weAreInitiator = !info.isIncomingConnection

            if (info.isIncomingConnection) {
                val parts = info.endpointName.split("|")
                val nickname = parts.getOrNull(0) ?: info.endpointName
                val avatarId = parts.getOrNull(1)?.toIntOrNull() ?: 1
                _connectionState.value = ConnectionState.IncomingRequest(
                    endpointId = endpointId,
                    requesterNickname = nickname,
                    requesterAvatarId = avatarId
                )
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to: $endpointId")
                    connectedEndpointId = endpointId

                    // Get nickname from current state
                    val nickname = when (val state = _connectionState.value) {
                        is ConnectionState.IncomingRequest -> state.requesterNickname
                        is ConnectionState.Requesting -> {
                            _nearbyDevices.value
                                .find { it.endpointId == endpointId }?.nickname ?: "Unknown"
                        }
                        else -> "Unknown"
                    }
                    connectedNickname = nickname

                    val avatarId = _nearbyDevices.value
                        .find { it.endpointId == endpointId }?.avatarId ?: 1

                    val session = ActiveSession(
                        remoteEndpointId = endpointId,
                        remoteNickname = nickname,
                        remoteAvatarId = avatarId
                    )

                    // CRITICAL: Stop scanning/advertising immediately (battery saving)
                    stopDiscovery()
                    stopAdvertising()

                    _connectionState.value = ConnectionState.Connected(session)
                    emitEvent(BTEvent.Connected(session))

                    // Initiate key exchange
                    initiateKeyExchange(endpointId)
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected by: $endpointId")
                    _connectionState.value = ConnectionState.Idle
                    emitEvent(BTEvent.ConnectionRejected)
                }

                else -> {
                    Log.e(TAG, "Connection error: ${result.status.statusCode}")
                    _connectionState.value = ConnectionState.Idle
                    emitEvent(BTEvent.Error("Connection error: ${result.status.statusMessage}"))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            emitEvent(BTEvent.Disconnected)
            cleanupSession()
        }
    }

    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    scope.launch {
                        try {
                            val bytes = payload.asBytes() ?: return@launch
                            val decrypted = if (encryptionEngine.hasSessionKey()) {
                                encryptionEngine.decrypt(bytes)
                            } else bytes

                            val packet = messageSerializer.unpackMessage(decrypted)
                            // Route key exchange packets internally — do not emit to chat
                            if (packet.tp == PacketType.KEY_EXCHANGE || packet.tp == PacketType.ACK) {
                                handleKeyExchangePacket(endpointId, packet)
                            } else {
                                _incomingPackets.emit(packet)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process incoming bytes: ${e.message}")
                        }
                    }
                }
                Payload.Type.FILE -> {
                    scope.launch {
                        _filePayloads.emit(Pair(payload.id, payload))
                    }
                }
                else -> Log.w(TAG, "Unknown payload type: ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            scope.launch {
                _transferUpdates.emit(update)
            }
        }
    }

    // ── Key Exchange ──────────────────────────────────────────────
    private fun initiateKeyExchange(endpointId: String) {
        // Fix: use weAreInitiator (set at connection time) instead of object hash.
        // The discoverer (requester) is the initiator — generates AES session key.
        // The advertiser (accepter) is the responder — decrypts the AES key.
        val localId  = if (weAreInitiator) "A" else "B"  // deterministic without object hash
        val remoteId = if (weAreInitiator) "B" else "A"
        keyExchangeManager.initiate(
            localEndpointId  = localId,
            remoteEndpointId = remoteId
        ) { packet -> sendPacketRaw(endpointId, packet) }
    }

    // Called from payloadCallback when KEY_EXCHANGE or ACK packets arrive
    private fun handleKeyExchangePacket(endpointId: String, packet: MessagePacket) {
        val localId  = if (weAreInitiator) "A" else "B"
        val remoteId = if (weAreInitiator) "B" else "A"
        when (packet.tp) {
            PacketType.KEY_EXCHANGE -> {
                val done = keyExchangeManager.handleIncoming(packet) { ackPacket ->
                    sendPacketRaw(endpointId, ackPacket)
                }
                if (done) {
                    Log.d(TAG, "Key exchange complete (initiator). Session key active.")
                    emitEvent(BTEvent.SessionKeyReady)
                }
            }
            PacketType.ACK -> {
                val done = keyExchangeManager.handleKeyAck(packet)
                if (done) {
                    Log.d(TAG, "Key exchange complete (responder). Session key active.")
                    emitEvent(BTEvent.SessionKeyReady)
                }
            }
            else -> Unit
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private fun cleanupSession() {
        connectedEndpointId = null
        connectedNickname = null
        weAreInitiator = false
        encryptionEngine.clearSession()
        keyExchangeManager.reset()
        _connectionState.value = ConnectionState.Idle
        // Tier 2 #9 (B-07): do NOT clear _nearbyDevices here. Disconnect must not
        // wipe the discovery list — Home would otherwise flash empty until the
        // next 8-second scan cycle completes. Stale devices are pruned by the
        // 10-second freshness sweep (FR-02.5) instead.
    }

    private fun emitEvent(event: BTEvent) {
        scope.launch { _events.emit(event) }
    }

    private fun parseAvatarFromName(name: String): Int {
        val parts = name.split("|")
        return parts.getOrNull(1)?.toIntOrNull() ?: 1
    }

    fun getConnectedEndpointId(): String? = connectedEndpointId

    // Format nickname for advertising: "nickname|avatarId"
    fun formatAdvertisingName(nickname: String, avatarId: Int) = "$nickname|$avatarId"
}

// ── Events ────────────────────────────────────────────────────────
sealed class BTEvent {
    object AdvertisingStarted : BTEvent()
    data class Connected(val session: ActiveSession) : BTEvent()
    object ConnectionRejected : BTEvent()
    object Disconnected : BTEvent()

    /**
     * Key exchange produced a shared secret and both peers should now compare a
     * short numeric [code] (US-6.1). The UI shows [code] and calls
     * Transport.confirmKeyMatch(accepted) with the user's verdict.
     */
    data class KeyConfirmationRequired(val code: String) : BTEvent()

    /** Either peer rejected the numeric code — session aborts, return Home (US-6.1). */
    object KeyConfirmationRejected : BTEvent()

    /** Key exchange failed or timed out — abort, error, return Home; no plaintext (US-6.4). */
    data class KeyExchangeFailed(val message: String) : BTEvent()

    /** Both peers confirmed; the AES session key is active and chat may open. */
    object SessionKeyReady : BTEvent()

    data class Error(val message: String) : BTEvent()
}
