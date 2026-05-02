package com.bluewhisper.bluetooth

import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.PacketType
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSerializer @Inject constructor(
    private val gson: Gson
) {

    // ── Serialize ─────────────────────────────────────────────────
    fun serialize(packet: MessagePacket): ByteArray {
        val json = gson.toJson(packet)
        return json.toByteArray(Charsets.UTF_8)
    }

    // ── Deserialize ───────────────────────────────────────────────
    fun deserialize(bytes: ByteArray): MessagePacket {
        val json = String(bytes, Charsets.UTF_8)
        return gson.fromJson(json, MessagePacket::class.java)
    }

    // ── Compress (DEFLATE) ────────────────────────────────────────
    fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()
        return outputStream.toByteArray()
    }

    // ── Decompress (INFLATE) ──────────────────────────────────────
    fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 3)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    // ── Full pipeline: Packet → ByteArray ready for encryption ───
    fun packMessage(packet: MessagePacket): ByteArray {
        val serialized = serialize(packet)
        return compress(serialized)
    }

    // ── Full pipeline: ByteArray (after decryption) → Packet ─────
    fun unpackMessage(bytes: ByteArray): MessagePacket {
        val decompressed = decompress(bytes)
        return deserialize(decompressed)
    }

    // ── File Metadata helpers ─────────────────────────────────────
    fun serializeFileMeta(meta: FileMetadata): String = gson.toJson(meta)

    fun deserializeFileMeta(json: String): FileMetadata =
        gson.fromJson(json, FileMetadata::class.java)

    // ── Create specific packet types ──────────────────────────────
    fun createTextPacket(index: Int, content: String) = MessagePacket(
        i = index,
        c = content,
        t = System.currentTimeMillis(),
        tp = PacketType.TEXT
    )

    fun createTypingPacket(index: Int) = MessagePacket(
        i = index,
        c = "",
        t = System.currentTimeMillis(),
        tp = PacketType.TYPING
    )

    fun createFileMetaPacket(index: Int, meta: FileMetadata) = MessagePacket(
        i = index,
        c = serializeFileMeta(meta),
        t = System.currentTimeMillis(),
        tp = PacketType.FILE_META
    )

    fun createKeyExchangePacket(index: Int, publicKeyBase64: String) = MessagePacket(
        i = index,
        c = publicKeyBase64,
        t = System.currentTimeMillis(),
        tp = PacketType.KEY_EXCHANGE
    )

    fun createDisconnectPacket(index: Int) = MessagePacket(
        i = index,
        c = "",
        t = System.currentTimeMillis(),
        tp = PacketType.DISCONNECT
    )
}
