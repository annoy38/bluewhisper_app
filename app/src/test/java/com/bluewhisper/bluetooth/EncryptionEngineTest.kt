package com.bluewhisper.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.AEADBadTagException

/**
 * PRD W9: encryption round-trip + nonce-uniqueness + GCM-tag-tamper detection.
 * Verifies NFR-03.3 (AES-256-GCM) and NFR-03.4 (unique nonce per message).
 */
@RunWith(RobolectricTestRunner::class)
class EncryptionEngineTest {

    private lateinit var engine: EncryptionEngine

    @Before
    fun setUp() {
        engine = EncryptionEngine()
        // Bootstrap a session AES key — bypasses RSA wrap, sufficient for AES tests.
        engine.generateAesSessionKey()
    }

    @Test
    fun `encrypt then decrypt round-trips arbitrary bytes`() {
        val plaintext = "BlueWhisper test payload 🌊".toByteArray(Charsets.UTF_8)
        val cipher = engine.encrypt(plaintext)
        val decrypted = engine.decrypt(cipher)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `nonce is unique across many messages`() {
        val seen = HashSet<String>()
        val ITERATIONS = 10_000
        repeat(ITERATIONS) {
            val cipher = engine.encrypt(byteArrayOf(0x01, 0x02, 0x03))
            // First 12 bytes of ciphertext are the GCM nonce per the engine contract.
            val nonce = cipher.copyOfRange(0, 12)
            val key = nonce.joinToString("") { b -> "%02x".format(b) }
            assertTrue("Nonce repeated at iteration $it", seen.add(key))
        }
        assertEquals(ITERATIONS, seen.size)
    }

    @Test
    fun `tampered ciphertext fails GCM authentication`() {
        val cipher = engine.encrypt("hi".toByteArray())
        // Flip a byte in the body (skip past the 12-byte nonce header)
        cipher[15] = (cipher[15].toInt() xor 0x01).toByte()
        try {
            engine.decrypt(cipher)
            fail("Expected GCM tag check to reject tampered ciphertext")
        } catch (_: AEADBadTagException) {
            // expected
        } catch (_: Exception) {
            // Some JCE providers wrap as a generic exception — still a rejection.
        }
    }

    @Test
    fun `clearSession releases key`() {
        assertTrue(engine.hasSessionKey())
        engine.clearSession()
        assertFalse(engine.hasSessionKey())
        try {
            engine.encrypt("x".toByteArray())
            fail("encrypt must throw after clearSession()")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `two cipher outputs of same plaintext differ`() {
        val plaintext = "deterministic input".toByteArray()
        val a = engine.encrypt(plaintext)
        val b = engine.encrypt(plaintext)
        // Must differ — otherwise the nonce was reused.
        assertNotEquals(a.toHex(), b.toHex())
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> "%02x".format(b) }
}
