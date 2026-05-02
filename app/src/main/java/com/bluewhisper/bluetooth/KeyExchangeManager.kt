package com.bluewhisper.bluetooth

import android.util.Log
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.PacketType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the full two-phase key exchange:
 *
 * Phase 1 (both sides simultaneously on connect):
 *   A → B : KEY_EXCHANGE packet carrying A's RSA public key (base64)
 *   B → A : KEY_EXCHANGE packet carrying B's RSA public key (base64)
 *
 * Phase 2 (initiator only — the one whose endpointId is lexically smaller):
 *   Initiator generates AES-256 session key
 *   Initiator → Responder : KEY_ACK packet carrying AES key encrypted with B's RSA public key
 *
 * After phase 2 both sides have the same AES session key and all subsequent
 * messages are encrypted with AES-256-GCM.
 */
@Singleton
class KeyExchangeManager @Inject constructor(
    private val encryptionEngine: EncryptionEngine,
    private val messageSerializer: MessageSerializer
) {
    companion object {
        private const val TAG = "BlueWhisper_KEX"
    }

    // Track exchange state
    private var localEndpointId: String = ""
    private var remoteEndpointId: String = ""
    private var sentPublicKey = false
    private var receivedRemotePublicKey = false

    /**
     * Call immediately after connection established.
     * Generates local RSA key pair and sends public key to peer.
     */
    fun initiate(
        localEndpointId: String,
        remoteEndpointId: String,
        sendPacket: (MessagePacket) -> Unit
    ) {
        this.localEndpointId = localEndpointId
        this.remoteEndpointId = remoteEndpointId
        sentPublicKey = false
        receivedRemotePublicKey = false

        try {
            // Generate RSA-2048 key pair
            val keyPair = encryptionEngine.generateSessionKeyPair()
            val publicKeyBase64 = encryptionEngine.encodeBase64(keyPair.public.encoded)

            // Send our public key
            val packet = messageSerializer.createKeyExchangePacket(0, publicKeyBase64)
            sendPacket(packet)
            sentPublicKey = true
            Log.d(TAG, "Sent local public key to $remoteEndpointId")
        } catch (e: Exception) {
            Log.e(TAG, "Key exchange initiation failed: ${e.message}")
        }
    }

    /**
     * Handle an incoming KEY_EXCHANGE packet (peer's public key).
     * After storing peer's public key, the "initiator" (lexically smaller endpointId)
     * generates the AES session key and sends it encrypted to the responder.
     *
     * Returns true if the exchange is now complete (session key established).
     */
    fun handleIncoming(
        packet: MessagePacket,
        sendPacket: (MessagePacket) -> Unit
    ): Boolean {
        if (packet.tp != PacketType.KEY_EXCHANGE) return false

        return try {
            // Decode and store peer's RSA public key
            val remotePublicKeyBytes = encryptionEngine.decodeBase64(packet.c)
            encryptionEngine.setRemotePublicKey(remotePublicKeyBytes)
            receivedRemotePublicKey = true
            Log.d(TAG, "Received remote public key from $remoteEndpointId")

            // The peer with the lexically smaller endpointId acts as "initiator"
            // and generates the AES session key to avoid both sides generating keys simultaneously
            val isInitiator = localEndpointId < remoteEndpointId
            if (isInitiator) {
                // Generate AES-256 session key
                val aesKey = encryptionEngine.generateAesSessionKey()

                // Encrypt AES key with peer's RSA public key
                val remotePublicKey = encryptionEngine.getRemotePublicKey()
                    ?: throw IllegalStateException("Remote public key not set")
                val encryptedAesKey = encryptionEngine.encryptSessionKey(aesKey, remotePublicKey)
                val encryptedBase64 = encryptionEngine.encodeBase64(encryptedAesKey)

                // Send encrypted AES key via KEY_ACK packet
                val ackPacket = MessagePacket(
                    i = 0,
                    c = encryptedBase64,
                    t = System.currentTimeMillis(),
                    tp = PacketType.ACK  // Reusing ACK type for KEY_ACK
                )
                sendPacket(ackPacket)
                Log.d(TAG, "Sent encrypted AES session key to $remoteEndpointId")
                true // Initiator is done — session key is set
            } else {
                // Responder waits for the KEY_ACK with the AES key
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleIncoming failed: ${e.message}")
            false
        }
    }

    /**
     * Handle KEY_ACK packet (only called on the responder side).
     * Decrypts the AES session key using our RSA private key.
     *
     * Returns true when session key is established.
     */
    fun handleKeyAck(packet: MessagePacket): Boolean {
        if (packet.tp != PacketType.ACK) return false

        return try {
            val encryptedAesKey = encryptionEngine.decodeBase64(packet.c)
            // Decrypt using our RSA private key
            encryptionEngine.decryptSessionKey(encryptedAesKey)
            Log.d(TAG, "AES session key decrypted and established")
            true
        } catch (e: Exception) {
            Log.e(TAG, "handleKeyAck failed: ${e.message}")
            false
        }
    }

    fun reset() {
        localEndpointId = ""
        remoteEndpointId = ""
        sentPublicKey = false
        receivedRemotePublicKey = false
    }
}
