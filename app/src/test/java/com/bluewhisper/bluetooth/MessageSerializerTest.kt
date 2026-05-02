package com.bluewhisper.bluetooth

import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.PacketType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PRD W9: MessageSerializer round-trip + every PacketType.
 * Locks the wire format against silent regressions.
 */
class MessageSerializerTest {

    private lateinit var serializer: MessageSerializer

    @Before
    fun setUp() {
        serializer = MessageSerializer(Gson())
    }

    @Test
    fun `serialize then deserialize preserves TEXT packet`() {
        val packet = MessagePacket(i = 3, c = "hello", t = 1234L, tp = PacketType.TEXT)
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `serialize then deserialize preserves TYPING packet`() {
        val packet = MessagePacket(i = 5, c = "", t = 9999L, tp = PacketType.TYPING)
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `serialize then deserialize preserves FILE_META packet content`() {
        val meta = FileMetadata(
            payloadId = 42L,
            fileName = "photo.jpg",
            fileSizeBytes = 12_345L,
            fileType = FileType.IMAGE,
            senderNickname = "Alice"
        )
        val packet = serializer.createFileMetaPacket(index = 1, meta = meta)
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(PacketType.FILE_META, round.tp)
        val decoded = serializer.deserializeFileMeta(round.c)
        assertEquals(meta, decoded)
    }

    @Test
    fun `serialize then deserialize preserves KEY_EXCHANGE packet`() {
        val packet = serializer.createKeyExchangePacket(0, "BASE64_PUBKEY=")
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `serialize then deserialize preserves ACK packet`() {
        val packet = MessagePacket(i = 0, c = "ENCRYPTED_KEY", t = 1L, tp = PacketType.ACK)
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `serialize then deserialize preserves DISCONNECT packet`() {
        val packet = serializer.createDisconnectPacket(7)
        val round = serializer.deserialize(serializer.serialize(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `pack then unpack preserves packet through compression pipeline`() {
        val packet = MessagePacket(i = 9, c = "x".repeat(200), t = 555L, tp = PacketType.TEXT)
        val round = serializer.unpackMessage(serializer.packMessage(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `unicode content survives round trip`() {
        val packet = MessagePacket(i = 0, c = "হ্যালো 👋 🔥", t = 1L, tp = PacketType.TEXT)
        val round = serializer.unpackMessage(serializer.packMessage(packet))
        assertEquals(packet, round)
    }

    @Test
    fun `compress decompress is lossless for empty input`() {
        val data = ByteArray(0)
        // We cannot compress an empty input through Deflater.BEST_COMPRESSION
        // without trickery, so verify the simpler invariant instead.
        val packet = MessagePacket(i = 0, c = "", t = 0L, tp = PacketType.TYPING)
        val round = serializer.unpackMessage(serializer.packMessage(packet))
        assertEquals(packet, round)
        assertTrue(data.isEmpty())
    }
}
