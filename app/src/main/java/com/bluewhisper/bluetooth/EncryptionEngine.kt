package com.bluewhisper.bluetooth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import android.util.Base64

@Singleton
class EncryptionEngine @Inject constructor() {

    companion object {
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val RSA_KEY_SIZE = 2048
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "BlueWhisperSessionKey"
    }

    // Session state — RAM only, never persisted
    private var sessionKey: ByteArray? = null
    private var localKeyPair: KeyPair? = null
    private var remotePublicKey: PublicKey? = null
    private val nonceCounter = AtomicLong(0)

    // ── Key Generation ────────────────────────────────────────────
    fun generateSessionKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()
        localKeyPair = keyPair
        return keyPair
    }

    fun generateAesSessionKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        val key = keyGen.generateKey()
        sessionKey = key.encoded.copyOf()
        return sessionKey!!.copyOf()
    }

    // ── RSA Key Exchange ──────────────────────────────────────────
    fun encryptSessionKey(aesKey: ByteArray, recipientPublicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        return cipher.doFinal(aesKey)
    }

    fun decryptSessionKey(encryptedKey: ByteArray): ByteArray {
        val privateKey = localKeyPair?.private
            ?: throw IllegalStateException("No local key pair generated")
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decryptedKey = cipher.doFinal(encryptedKey)
        sessionKey = decryptedKey.copyOf()
        return decryptedKey
    }

    fun setRemotePublicKey(publicKeyBytes: ByteArray) {
        val keySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        remotePublicKey = keyFactory.generatePublic(keySpec)
    }

    fun getLocalPublicKeyBytes(): ByteArray {
        return localKeyPair?.public?.encoded
            ?: throw IllegalStateException("No local key pair generated")
    }

    fun getRemotePublicKey(): PublicKey? = remotePublicKey

    // ── AES-256-GCM Encryption ────────────────────────────────────
    fun encrypt(data: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("No session key. Call generateAesSessionKey() first.")
        val secretKey: SecretKey = SecretKeySpec(key, "AES")

        // Generate unique nonce: 8-byte counter + 4-byte random tail.
        // Counter alone guarantees uniqueness within a session; the random tail is
        // defence-in-depth against counter-state leakage / reuse across sessions.
        val nonce = ByteArray(GCM_IV_LENGTH)
        val counterBytes = nonceCounter.incrementAndGet().let {
            byteArrayOf(
                (it shr 56).toByte(), (it shr 48).toByte(),
                (it shr 40).toByte(), (it shr 32).toByte(),
                (it shr 24).toByte(), (it shr 16).toByte(),
                (it shr 8).toByte(), it.toByte()
            )
        }
        counterBytes.copyInto(nonce, 0, 0, 8)
        // B-21 fix: write the random tail directly into nonce. The previous
        // sliceArray(...).also { copyInto } pattern wrote the random bytes
        // into a copy that was discarded.
        val randomTail = ByteArray(GCM_IV_LENGTH - 8)
        SecureRandom().nextBytes(randomTail)
        randomTail.copyInto(nonce, 8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, paramSpec)
        val encrypted = cipher.doFinal(data)

        // Prepend nonce to encrypted data: [12 bytes nonce][encrypted+tag]
        return nonce + encrypted
    }

    fun decrypt(data: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("No session key")
        val secretKey: SecretKey = SecretKeySpec(key, "AES")

        if (data.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Data too short to contain nonce")
        }

        val nonce = data.sliceArray(0 until GCM_IV_LENGTH)
        val encrypted = data.sliceArray(GCM_IV_LENGTH until data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec)
        return cipher.doFinal(encrypted)
    }

    // ── Session Key Management ────────────────────────────────────
    fun hasSessionKey(): Boolean = sessionKey != null

    fun clearSession() {
        // Zero out session key bytes before nulling (security critical)
        sessionKey?.fill(0)
        sessionKey = null
        localKeyPair = null
        remotePublicKey = null
        nonceCounter.set(0)
    }

    // ── Base64 helpers for packet transmission ────────────────────
    fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeBase64(str: String): ByteArray =
        Base64.decode(str, Base64.NO_WRAP)
}
