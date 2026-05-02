package com.bluewhisper.domain.model

import android.os.Parcelable
import java.util.UUID
import kotlinx.parcelize.Parcelize

// ── User Profile ─────────────────────────────────────────────────
data class UserProfile(
    val deviceId: String = UUID.randomUUID().toString(),
    val nickname: String = "",
    val avatarId: Int = 1,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val isDiscoverable: Boolean = true,
    val createdAtEpoch: Long = System.currentTimeMillis()
)

enum class AppLanguage(val code: String) {
    ENGLISH("EN"),
    BANGLA("BN")
}

// ── Nearby Device ─────────────────────────────────────────────────
data class NearbyDevice(
    val endpointId: String,
    val nickname: String,
    val avatarId: Int,
    val signalStrength: SignalStrength = SignalStrength.MEDIUM,
    val lastSeenEpoch: Long = System.currentTimeMillis(),
    val connectionStatus: DeviceStatus = DeviceStatus.AVAILABLE
)

enum class SignalStrength { STRONG, MEDIUM, WEAK }

enum class DeviceStatus { AVAILABLE, CONNECTING, BUSY }

// ── Session ───────────────────────────────────────────────────────
data class ActiveSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val remoteEndpointId: String,
    val remoteNickname: String,
    val remoteAvatarId: Int,
    val connectedAtEpoch: Long = System.currentTimeMillis()
)

// ── Message ───────────────────────────────────────────────────────
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val content: String,
    val timestampEpoch: Long = System.currentTimeMillis(),
    val direction: MessageDirection,
    val type: MessageType = MessageType.TEXT,
    val fileReference: String? = null  // ReceivedFile id for FILE_NOTIFICATION
)

enum class MessageDirection { SENT, RECEIVED }

enum class MessageType { TEXT, FILE_NOTIFICATION, TYPING }

// ── Message Packet (wire format) ─────────────────────────────────
data class MessagePacket(
    val i: Int,             // index 0–19
    val c: String,          // content (max 200 chars)
    val t: Long,            // timestamp epoch ms
    val tp: PacketType      // packet type
)

enum class PacketType {
    TEXT,           // 0 - regular chat message
    TYPING,         // 1 - typing indicator
    FILE_META,      // 2 - file metadata before transfer
    KEY_EXCHANGE,   // 3 - RSA key exchange on connect
    ACK,            // 4 - message acknowledgment
    DISCONNECT      // 5 - intentional disconnect signal
}

// ── File Transfer ─────────────────────────────────────────────────
data class FileMetadata(
    val payloadId: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val fileType: FileType,
    // Sender's display nickname (FR-10 attribution).
    // Default kept for backwards compatibility with packets from older builds.
    val senderNickname: String = ""
)

@Parcelize
enum class FileType : Parcelable {
    IMAGE, AUDIO, DOCUMENT, VIDEO;

    companion object {
        fun fromExtension(ext: String): FileType = when (ext.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> IMAGE
            "mp3", "aac", "ogg", "m4a" -> AUDIO
            "pdf", "txt", "doc", "docx" -> DOCUMENT
            "mp4", "3gp", "mkv", "avi" -> VIDEO
            else -> DOCUMENT
        }
    }
}

// ── Received File ─────────────────────────────────────────────────
@Parcelize
data class ReceivedFile(
    val id: String = UUID.randomUUID().toString(),
    val payloadId: Long,
    val fileName: String,
    val fileSizeBytes: Long,
    val fileType: FileType,
    val tempPath: String,
    val senderNickname: String = "",
    val receivedAtEpoch: Long = System.currentTimeMillis(),
    val state: FileState = FileState.TRANSFERRING,
    val viewedAtEpoch: Long? = null,
    val vanishAtEpoch: Long? = null,
    val remainingSeconds: Int = 10
) : Parcelable

@Parcelize
enum class FileState : Parcelable {
    TRANSFERRING,
    RECEIVED_UNVIEWED,
    VIEWING,
    SAVED,
    VANISHED
}

// ── Connection State ──────────────────────────────────────────────
sealed class ConnectionState {
    object Idle : ConnectionState()
    data class Requesting(val targetEndpointId: String) : ConnectionState()
    data class IncomingRequest(
        val endpointId: String,
        val requesterNickname: String,
        val requesterAvatarId: Int
    ) : ConnectionState()
    data class Connected(val session: ActiveSession) : ConnectionState()
    object Disconnecting : ConnectionState()
}

// ── App State ─────────────────────────────────────────────────────
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Exception? = null) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}
