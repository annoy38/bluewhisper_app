package com.bluewhisper.bluetooth.wire

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Frame types on the RFCOMM link. See TRANSPORT_CONTRACT.md §2.
 *
 * - [CONTROL]   handshake + key exchange (connect/accept/reject, SAS verdict, KEX packets,
 *               disconnect). Never AES-encrypted — these carry no session-secret content;
 *               KEX confidentiality comes from RSA-wrapping the AES key, and SAS security
 *               from the numeric compare, not from encryption.
 * - [MESSAGE]   chat text / typing / file-notice — AES-256-GCM encrypted.
 * - [FILE_META] file header before chunks — AES-256-GCM encrypted.
 * - [FILE_CHUNK]one encrypted slice of file bytes — AES-256-GCM encrypted.
 * - [FILE_END]  marks a completed file for the given transferId.
 */
enum class FrameType(val id: Byte) {
    CONTROL(0x01),
    MESSAGE(0x02),
    FILE_META(0x03),
    FILE_CHUNK(0x04),
    FILE_END(0x05);

    companion object {
        fun fromId(id: Byte): FrameType? = entries.firstOrNull { it.id == id }
    }
}

/** One framed unit: a [type] tag plus its raw [body] bytes. */
class Frame(val type: FrameType, val body: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return type == other.type && body.contentEquals(other.body)
    }

    override fun hashCode(): Int = 31 * type.hashCode() + body.contentHashCode()
}

/**
 * Length-prefixed framing over an RFCOMM byte stream:
 *
 * ```
 * [type: 1 byte][length: uint32 big-endian][body: length bytes]
 * ```
 *
 * RFCOMM delivers an ordered byte stream with no message boundaries, so the length
 * prefix is what re-establishes discrete frames. This also removes any need for a
 * delimiter character — the reserved '|' (US-2.1) is only relevant to the discovery
 * advertising name, not to this channel.
 */
object Framer {
    const val HEADER_SIZE = 5

    /** Guard against corrupt/hostile length prefixes. Files are chunked ≤64 KB, so 8 MB is ample. */
    const val MAX_BODY_SIZE = 8 * 1024 * 1024

    /** Writes one frame and flushes. Callers MUST serialize concurrent writes on [out]. */
    fun writeFrame(out: OutputStream, type: FrameType, body: ByteArray) {
        require(body.size <= MAX_BODY_SIZE) {
            "Frame body ${body.size} exceeds max $MAX_BODY_SIZE"
        }
        val len = body.size
        val header = ByteArray(HEADER_SIZE)
        header[0] = type.id
        header[1] = (len ushr 24).toByte()
        header[2] = (len ushr 16).toByte()
        header[3] = (len ushr 8).toByte()
        header[4] = len.toByte()
        out.write(header)
        if (len > 0) out.write(body)
        out.flush()
    }

    /**
     * Reads one frame, blocking until it fully arrives.
     *
     * @return the frame, or null on a clean end-of-stream at a frame boundary (peer closed).
     * @throws IOException on a malformed frame or a truncated stream mid-frame.
     */
    fun readFrame(input: InputStream): Frame? {
        val header = readFully(input, HEADER_SIZE) ?: return null
        val type = FrameType.fromId(header[0])
            ?: throw IOException("Unknown frame type: ${header[0]}")
        val len = ((header[1].toInt() and 0xFF) shl 24) or
                  ((header[2].toInt() and 0xFF) shl 16) or
                  ((header[3].toInt() and 0xFF) shl 8) or
                  (header[4].toInt() and 0xFF)
        // High bit set decodes to a negative Int; both that and oversize are rejected.
        if (len < 0 || len > MAX_BODY_SIZE) throw IOException("Invalid frame length: $len")
        val body = if (len == 0) ByteArray(0)
            else readFully(input, len) ?: throw IOException("Truncated frame body (wanted $len)")
        return Frame(type, body)
    }

    /** Reads exactly [n] bytes. Returns null only if EOF occurs before ANY byte (clean boundary). */
    private fun readFully(input: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) return if (off == 0) null else throw EOFException("EOF mid-read at $off/$n")
            off += r
        }
        return buf
    }
}
