package com.bluewhisper.bluetooth.wire

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class FramerTest {

    private fun roundTrip(vararg frames: Pair<FrameType, ByteArray>): List<Frame> {
        val out = ByteArrayOutputStream()
        frames.forEach { (t, b) -> Framer.writeFrame(out, t, b) }
        val input = ByteArrayInputStream(out.toByteArray())
        val read = mutableListOf<Frame>()
        while (true) {
            val f = Framer.readFrame(input) ?: break
            read += f
        }
        return read
    }

    @Test
    fun `single frame round-trips`() {
        val body = "hello".toByteArray()
        val read = roundTrip(FrameType.MESSAGE to body)
        assertEquals(1, read.size)
        assertEquals(FrameType.MESSAGE, read[0].type)
        assertArrayEquals(body, read[0].body)
    }

    @Test
    fun `multiple back-to-back frames preserve order and boundaries`() {
        val read = roundTrip(
            FrameType.CONTROL to byteArrayOf(0x10),
            FrameType.MESSAGE to "one".toByteArray(),
            FrameType.FILE_CHUNK to ByteArray(64 * 1024) { it.toByte() },
            FrameType.FILE_END to byteArrayOf(),
        )
        assertEquals(4, read.size)
        assertEquals(FrameType.CONTROL, read[0].type)
        assertEquals(FrameType.MESSAGE, read[1].type)
        assertEquals("one", String(read[1].body))
        assertEquals(64 * 1024, read[2].body.size)
        assertEquals(FrameType.FILE_END, read[3].type)
        assertEquals(0, read[3].body.size)
    }

    @Test
    fun `empty-body frame round-trips`() {
        val read = roundTrip(FrameType.FILE_END to ByteArray(0))
        assertEquals(1, read.size)
        assertEquals(0, read[0].body.size)
    }

    @Test
    fun `clean EOF at frame boundary returns null`() {
        val input = ByteArrayInputStream(ByteArray(0))
        assertNull(Framer.readFrame(input))
    }

    @Test
    fun `truncated body throws`() {
        val out = ByteArrayOutputStream()
        Framer.writeFrame(out, FrameType.MESSAGE, "abcdef".toByteArray())
        // Drop the last two body bytes to simulate a cut stream.
        val full = out.toByteArray()
        val truncated = full.copyOf(full.size - 2)
        assertThrows(IOException::class.java) {
            Framer.readFrame(ByteArrayInputStream(truncated))
        }
    }

    @Test
    fun `unknown frame type byte throws`() {
        // type=0x7F, length=0
        val bytes = byteArrayOf(0x7F, 0, 0, 0, 0)
        assertThrows(IOException::class.java) {
            Framer.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `oversize length prefix is rejected`() {
        // type=MESSAGE, length=0x7FFFFFFF (huge, > MAX_BODY_SIZE)
        val bytes = byteArrayOf(FrameType.MESSAGE.id, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertThrows(IOException::class.java) {
            Framer.readFrame(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun `writing an oversize body is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Framer.writeFrame(ByteArrayOutputStream(), FrameType.FILE_CHUNK, ByteArray(Framer.MAX_BODY_SIZE + 1))
        }
    }
}
