package com.bluewhisper.bluetooth.wire

import java.security.MessageDigest

/**
 * Short Authentication String for the key-exchange confirmation (US-6.1).
 *
 * Both peers derive an identical short numeric code from the two exchanged RSA
 * public keys. A man-in-the-middle who relays *different* keys to each side
 * produces a *different* code on each side, so the users comparing the code will
 * see a mismatch and can reject the session. The two key inputs are sorted before
 * hashing so both peers compute the same value regardless of who initiated.
 */
object SasGenerator {
    const val DIGITS = 6

    /** @return a zero-padded [DIGITS]-digit code, identical on both peers when keys match. */
    fun generate(localPublicKey: ByteArray, remotePublicKey: ByteArray): String {
        val (first, second) =
            if (compareUnsigned(localPublicKey, remotePublicKey) <= 0)
                localPublicKey to remotePublicKey
            else
                remotePublicKey to localPublicKey

        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(first)
            update(second)
        }.digest()

        // First 4 digest bytes as an unsigned value, mod 10^DIGITS, zero-padded.
        val n = ((digest[0].toLong() and 0xFF) shl 24) or
                ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or
                (digest[3].toLong() and 0xFF)

        return (n % pow10(DIGITS)).toString().padStart(DIGITS, '0')
    }

    private fun pow10(d: Int): Long {
        var r = 1L
        repeat(d) { r *= 10 }
        return r
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val min = minOf(a.size, b.size)
        for (i in 0 until min) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
