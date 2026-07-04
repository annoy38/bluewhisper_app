package com.bluewhisper.bluetooth.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SasGeneratorTest {

    private val keyA = ByteArray(294) { it.toByte() }          // stand-ins for RSA-2048 X.509 pubkeys
    private val keyB = ByteArray(294) { (it * 7 + 3).toByte() }

    @Test
    fun `code is DIGITS long and all numeric`() {
        val code = SasGenerator.generate(keyA, keyB)
        assertEquals(SasGenerator.DIGITS, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `both peers derive the same code regardless of local-remote order`() {
        // Peer 1 sees (local=A, remote=B); peer 2 sees (local=B, remote=A).
        val peer1 = SasGenerator.generate(keyA, keyB)
        val peer2 = SasGenerator.generate(keyB, keyA)
        assertEquals(peer1, peer2)
    }

    @Test
    fun `generation is deterministic`() {
        assertEquals(SasGenerator.generate(keyA, keyB), SasGenerator.generate(keyA, keyB))
    }

    @Test
    fun `different key pairs generally yield different codes (MITM detectable)`() {
        val honest = SasGenerator.generate(keyA, keyB)
        // A MITM presenting a substituted key on one side.
        val mitmKey = ByteArray(294) { (it * 13 + 1).toByte() }
        val attacked = SasGenerator.generate(keyA, mitmKey)
        assertNotEquals(honest, attacked)
    }
}
