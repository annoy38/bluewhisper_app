package com.bluewhisper.bluetooth

import android.net.Uri
import com.bluewhisper.domain.model.ActiveSession
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.domain.model.FileMetadata
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.NearbyDevice
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport abstraction for BlueWhisper's peer-to-peer link.
 *
 * ARCHITECT CONTRACT (Track A, 2026-07-04). This is the stable seam every other
 * track codes against. It is deliberately free of any Google Nearby / Play
 * Services types so the implementation can be swapped for the native
 * BLE + Classic-Bluetooth-RFCOMM stack (see TRANSPORT_CONTRACT.md) without
 * touching ViewModels.
 *
 * Implementations MUST:
 *  - be a process-wide singleton (Hilt @Singleton), injected as `Transport`;
 *  - apply app-layer AES-256-GCM to BOTH messages AND files (US-9.1) — callers
 *    never see plaintext on the wire and never handle [Payload]-style types;
 *  - run the authenticated key-exchange confirmation (US-6.1) — emit
 *    [BTEvent.KeyConfirmationRequired] with a short numeric code, wait for
 *    [confirmKeyMatch] on BOTH peers, and only then emit [BTEvent.SessionKeyReady];
 *  - fail safe (US-6.4) — on key-exchange timeout/error emit
 *    [BTEvent.KeyExchangeFailed] and tear the session down; never open a
 *    plaintext path;
 *  - enforce strictly one peer (US-15.7) and a deterministic tie-break for
 *    simultaneous mutual requests (US-15.6).
 */
interface Transport {

    // ── Observable state (collected by ViewModels) ───────────────────────────

    /** Live discovery list; pruned ~10 s after a peer stops advertising (US-3.3). */
    val nearbyDevices: StateFlow<List<NearbyDevice>>

    /** Connection lifecycle state machine (Idle → Requesting/IncomingRequest → Connected). */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Decrypted, non-key-exchange application packets (TEXT / TYPING / FILE_META /
     * DISCONNECT). KEY_EXCHANGE and ACK packets are handled internally and never
     * surface here.
     */
    val incomingPackets: SharedFlow<MessagePacket>

    /** Fully-received, decrypted files written to a temp path, matched to their metadata. */
    val incomingFiles: SharedFlow<IncomingFile>

    /** Real-time send/receive progress, keyed by [FileTransferProgress.transferId]. */
    val transferProgress: SharedFlow<FileTransferProgress>

    /** One-shot transport events (see [BTEvent]). */
    val events: SharedFlow<BTEvent>

    // ── Discovery & advertising ──────────────────────────────────────────────

    /** Become discoverable as [nickname]/[avatarId] (US-3.4). Nickname must not contain '|'. */
    fun startAdvertising(nickname: String, avatarId: Int)

    fun stopAdvertising()

    /** Start scanning for peers on an efficient duty cycle (US-3.7). */
    fun startDiscovery()

    fun stopDiscovery()

    // ── Connection lifecycle ─────────────────────────────────────────────────

    /** Send a connection request to a discovered peer (US-4.1). */
    fun requestConnection(peerId: PeerId, myNickname: String, myAvatarId: Int)

    /** Accept an incoming request → begins key exchange (US-5.2). */
    fun acceptConnection(peerId: PeerId)

    /** Decline an incoming request, or cancel an outgoing one (US-4.2 / US-5.3). */
    fun rejectConnection(peerId: PeerId)

    /** Deliberately end the active session (US-11.7). Sends a disconnect signal, then cleans up. */
    fun disconnect()

    /** Stop everything (discovery + advertising + any connection) and clean up. Wipe paths use this. */
    fun stopAll()

    // ── Key-exchange confirmation (US-6.1) ───────────────────────────────────

    /**
     * User's verdict on the numeric code shown after [BTEvent.KeyConfirmationRequired].
     * `true` from BOTH peers → [BTEvent.SessionKeyReady]; either `false` →
     * [BTEvent.KeyConfirmationRejected] and the session aborts.
     */
    fun confirmKeyMatch(accepted: Boolean)

    // ── Data ─────────────────────────────────────────────────────────────────

    /** Encrypt and send a chat/control packet. Returns false if there is no live session. */
    fun sendMessage(packet: MessagePacket): Boolean

    /**
     * Encrypt and stream a file over the session (US-9.1). The implementation reads
     * [OutgoingFile.uri], chunks + encrypts it, and reports progress via
     * [transferProgress]. Returns the assigned transferId, or null if no session /
     * a transfer is already in progress (US-8.6).
     */
    fun sendFile(file: OutgoingFile): Long?

    // ── Query ────────────────────────────────────────────────────────────────

    fun connectedPeerId(): PeerId?
}

/**
 * Opaque peer handle. Concretely a Bluetooth MAC (classic) or a resolved BLE
 * address, but callers must treat it as an opaque token.
 */
typealias PeerId = String

/** A file queued for sending. The transport owns reading, chunking, and encrypting it. */
data class OutgoingFile(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val fileType: FileType,
    val senderNickname: String,
)

/** A fully-received, decrypted file the transport has written to [tempPath]. */
data class IncomingFile(
    val transferId: Long,
    /** Decrypted temp file path (in app cache). Ownership transfers to the caller. */
    val tempPath: String,
    val metadata: FileMetadata,
)

/** Real-time transfer progress for one file. */
data class FileTransferProgress(
    val transferId: Long,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: TransferStatus,
    val isIncoming: Boolean,
)

enum class TransferStatus { IN_PROGRESS, SUCCESS, FAILURE }
