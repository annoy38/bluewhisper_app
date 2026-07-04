package com.bluewhisper.bluetooth.wire

/**
 * Opcodes carried in the 1-byte prefix of a [FrameType.CONTROL] frame body:
 * `[opcode: 1 byte][payload…]`.
 *
 * CONTROL frames are never AES-encrypted — they carry the handshake and the key
 * exchange itself. Confidentiality of the session key comes from RSA-wrapping it
 * ([KEX]); authenticity comes from the SAS numeric compare, not from encryption.
 */
object Control {
    /** Requester → advertiser. Payload: UTF-8 "nickname|avatarId|nonceHex". */
    const val CONNECT_REQUEST: Byte = 0x10

    /** Advertiser → requester: request accepted; proceed to key exchange. No payload. */
    const val CONNECT_ACCEPT: Byte = 0x11

    /** Advertiser → requester: request declined. No payload. */
    const val CONNECT_REJECT: Byte = 0x12

    /** Either direction: a key-exchange packet. Payload: packed [MessagePacket] (KEY_EXCHANGE or ACK). */
    const val KEX: Byte = 0x13

    /** Either direction: the user confirmed the SAS numeric code matches. No payload. */
    const val SAS_ACCEPT: Byte = 0x14

    /** Either direction: the user rejected the SAS code (possible MITM). No payload. */
    const val SAS_REJECT: Byte = 0x15

    /** Either direction: intentional disconnect signal. No payload. */
    const val DISCONNECT: Byte = 0x16
}
