package com.bluewhisper.bluetooth

import android.util.Log
import com.bluewhisper.bluetooth.wire.Control
import com.bluewhisper.bluetooth.wire.Frame
import com.bluewhisper.bluetooth.wire.FrameType
import com.bluewhisper.bluetooth.wire.Framer
import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.MessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Owns one connected RFCOMM link and speaks the framed BlueWhisper wire protocol
 * (see TRANSPORT_CONTRACT.md §2). It is deliberately stream-based rather than
 * socket-based so it can be exercised without a real Bluetooth device.
 *
 * Responsibilities:
 *  - a blocking read loop that decodes frames and dispatches them to [listener];
 *  - framed, write-serialized sending of control/chat/file frames;
 *  - app-layer AES-256-GCM on MESSAGE / FILE_META / FILE_CHUNK (US-9.1) — files are
 *    chunked and encrypted here, never handed to the transport as plaintext;
 *  - assembling an incoming file to a temp path and reporting progress.
 *
 * One transfer at a time (US-8.6): a single [incoming] slot and a whole-file write
 * lock keep a file's frames contiguous on the stream.
 */
class RfcommConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val encryptionEngine: EncryptionEngine,
    private val messageSerializer: MessageSerializer,
    private val fileManager: FileManager,
    private val scope: CoroutineScope,
    private val listener: Listener,
    /** Closes the underlying socket; invoked to unblock the read loop on [close]. */
    private val onCloseSocket: () -> Unit,
) {
    companion object {
        private const val TAG = "BlueWhisper_Rfcomm"
        private const val CHUNK_SIZE = 64 * 1024
    }

    /** Callbacks fire on the read-loop / send coroutines, not the main thread. */
    interface Listener {
        /** A CONTROL frame: [opcode] is a [Control] constant; [payload] is the rest of the body. */
        fun onControl(opcode: Byte, payload: ByteArray)
        /** A decrypted application packet (TEXT / TYPING). */
        fun onMessage(packet: MessagePacket)
        fun onFileProgress(progress: FileTransferProgress)
        /** A fully received, decrypted file at [IncomingFile.tempPath]. */
        fun onIncomingFile(file: IncomingFile)
        /** The link ended (peer closed, error, or [close]). [reason] is null on a clean close. */
        fun onClosed(reason: String?)
    }

    private val writeLock = Any()
    @Volatile private var closed = false
    private var incoming: IncomingState? = null

    private class IncomingState(
        val meta: FileMetadata,
        val tempFile: File,
        val out: FileOutputStream,
        var received: Long,
    )

    // ── Lifecycle ────────────────────────────────────────────────────────────
    fun start() {
        scope.launch { readLoop() }
    }

    fun close() {
        if (closed) return
        closed = true
        try { onCloseSocket() } catch (_: Exception) { /* best effort */ }
        abortIncoming()
    }

    // ── Sending ──────────────────────────────────────────────────────────────
    /** Send a CONTROL frame (unencrypted). */
    fun sendControl(opcode: Byte, payload: ByteArray = ByteArray(0)): Boolean =
        writeFrame(FrameType.CONTROL, byteArrayOf(opcode) + payload)

    /** Send a key-exchange [MessagePacket] as a CONTROL/KEX frame (unencrypted; RSA protects the key). */
    fun sendKex(packet: MessagePacket): Boolean =
        sendControl(Control.KEX, messageSerializer.packMessage(packet))

    /** Send a chat/typing packet as an AES-encrypted MESSAGE frame. Requires a session key. */
    fun sendMessage(packet: MessagePacket): Boolean {
        return try {
            val packed = messageSerializer.packMessage(packet)
            val body = if (encryptionEngine.hasSessionKey()) encryptionEngine.encrypt(packed) else packed
            writeFrame(FrameType.MESSAGE, body)
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage failed: ${e.message}")
            false
        }
    }

    /**
     * Stream a file: FILE_META → N encrypted FILE_CHUNK → FILE_END, reporting progress.
     * Blocking; the transport calls this on an IO coroutine. Holds the write lock for the
     * whole file so its frames stay contiguous (US-8.6). Always closes [source].
     */
    fun sendFile(transferId: Long, meta: FileMetadata, source: InputStream, totalBytes: Long) {
        synchronized(writeLock) {
            var sent = 0L
            try {
                val metaJson = messageSerializer.serializeFileMeta(meta).toByteArray(Charsets.UTF_8)
                Framer.writeFrame(output, FrameType.FILE_META, encryptionEngine.encrypt(metaJson))

                val buf = ByteArray(CHUNK_SIZE)
                while (true) {
                    val r = source.read(buf)
                    if (r < 0) break
                    val chunk = if (r == buf.size) buf else buf.copyOf(r)
                    Framer.writeFrame(output, FrameType.FILE_CHUNK, encryptionEngine.encrypt(chunk))
                    sent += r
                    listener.onFileProgress(
                        FileTransferProgress(transferId, sent, totalBytes, TransferStatus.IN_PROGRESS, isIncoming = false)
                    )
                }
                Framer.writeFrame(output, FrameType.FILE_END, longToBytes(transferId))
                output.flush()
                listener.onFileProgress(
                    FileTransferProgress(transferId, sent, totalBytes, TransferStatus.SUCCESS, isIncoming = false)
                )
            } catch (e: Exception) {
                Log.e(TAG, "sendFile failed: ${e.message}")
                listener.onFileProgress(
                    FileTransferProgress(transferId, sent, totalBytes, TransferStatus.FAILURE, isIncoming = false)
                )
            } finally {
                try { source.close() } catch (_: Exception) { /* best effort */ }
            }
        }
    }

    private fun writeFrame(type: FrameType, body: ByteArray): Boolean {
        return try {
            synchronized(writeLock) { Framer.writeFrame(output, type, body) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeFrame($type) failed: ${e.message}")
            false
        }
    }

    // ── Receiving ────────────────────────────────────────────────────────────
    private fun readLoop() {
        var reason: String? = null
        try {
            while (!closed) {
                val frame = Framer.readFrame(input) ?: break // clean peer close
                dispatch(frame)
            }
        } catch (e: Exception) {
            if (!closed) {
                reason = e.message ?: "read error"
                Log.w(TAG, "read loop ended: $reason")
            }
        } finally {
            abortIncoming()
            listener.onClosed(if (closed) null else reason)
        }
    }

    private fun dispatch(frame: Frame) {
        when (frame.type) {
            FrameType.CONTROL -> {
                if (frame.body.isEmpty()) return
                listener.onControl(frame.body[0], frame.body.copyOfRange(1, frame.body.size))
            }
            FrameType.MESSAGE -> {
                if (!encryptionEngine.hasSessionKey()) {
                    Log.w(TAG, "MESSAGE before session key — dropping"); return
                }
                val decrypted = encryptionEngine.decrypt(frame.body)
                listener.onMessage(messageSerializer.unpackMessage(decrypted))
            }
            FrameType.FILE_META -> beginIncoming(frame.body)
            FrameType.FILE_CHUNK -> appendIncoming(frame.body)
            FrameType.FILE_END -> finishIncoming()
        }
    }

    private fun beginIncoming(encryptedBody: ByteArray) {
        try {
            abortIncoming() // defensive: one at a time
            val json = String(encryptionEngine.decrypt(encryptedBody), Charsets.UTF_8)
            val meta = messageSerializer.deserializeFileMeta(json)
            val tempFile = fileManager.createTempFile(meta.fileName)
            incoming = IncomingState(meta, tempFile, FileOutputStream(tempFile), received = 0L)
            listener.onFileProgress(
                FileTransferProgress(meta.payloadId, 0L, meta.fileSizeBytes, TransferStatus.IN_PROGRESS, isIncoming = true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "beginIncoming failed: ${e.message}")
        }
    }

    private fun appendIncoming(encryptedBody: ByteArray) {
        val state = incoming ?: run { Log.w(TAG, "FILE_CHUNK with no active transfer"); return }
        try {
            val plain = encryptionEngine.decrypt(encryptedBody)
            state.out.write(plain)
            state.received += plain.size
            listener.onFileProgress(
                FileTransferProgress(state.meta.payloadId, state.received, state.meta.fileSizeBytes, TransferStatus.IN_PROGRESS, isIncoming = true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "appendIncoming failed: ${e.message}")
            abortIncoming()
        }
    }

    private fun finishIncoming() {
        val state = incoming ?: run { Log.w(TAG, "FILE_END with no active transfer"); return }
        incoming = null
        try {
            state.out.flush(); state.out.close()
            listener.onFileProgress(
                FileTransferProgress(state.meta.payloadId, state.received, state.meta.fileSizeBytes, TransferStatus.SUCCESS, isIncoming = true)
            )
            listener.onIncomingFile(IncomingFile(state.meta.payloadId, state.tempFile.absolutePath, state.meta))
        } catch (e: Exception) {
            Log.e(TAG, "finishIncoming failed: ${e.message}")
        }
    }

    /** Close and securely remove any partially received file (US-15.8). */
    private fun abortIncoming() {
        val state = incoming ?: return
        incoming = null
        try { state.out.close() } catch (_: Exception) { /* best effort */ }
        try {
            listener.onFileProgress(
                FileTransferProgress(state.meta.payloadId, state.received, state.meta.fileSizeBytes, TransferStatus.FAILURE, isIncoming = true)
            )
        } catch (_: Exception) { /* best effort */ }
        fileManager.secureDelete(state.tempFile.absolutePath)
    }

    private fun longToBytes(v: Long): ByteArray = ByteArray(8) { i -> (v shr (56 - i * 8)).toByte() }
}
