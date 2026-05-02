package com.bluewhisper.bluetooth

import com.bluewhisper.domain.model.MessagePacket
import com.bluewhisper.domain.model.PacketType
import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tier 4 #18 (PRD W9 two-device integration): runs the full key-exchange +
 * encrypted message round-trip in a single process by pairing two device-side
 * stacks through an in-memory transport.
 *
 * A real two-device test on hardware is still required for the Nearby
 * Connections layer — this verifies the protocol contract that lives ABOVE
 * Nearby (KeyExchangeManager + EncryptionEngine + MessageSerializer).
 */
@RunWith(RobolectricTestRunner::class)
class TwoDeviceIntegrationTest {

    private class Device(label: String) {
        val tag = label
        val engine = EncryptionEngine()
        val serializer = MessageSerializer(Gson())
        val kex = KeyExchangeManager(engine, serializer)

        // outgoing wire — peer reads from here
        val outbox: ArrayDeque<MessagePacket> = ArrayDeque()
        fun send(packet: MessagePacket) { outbox.addLast(packet) }
    }

    @Test fun `key exchange establishes matching session keys on both devices`() {
        val a = Device("A")
        val b = Device("B")

        // Both sides initiate (mirror the real flow).
        a.kex.initiate("A", "B") { a.send(it) }
        b.kex.initiate("B", "A") { b.send(it) }

        // A sends its public key to B; B receives.
        val aPub = a.outbox.removeFirst()
        val bDoneFromA = b.kex.handleIncoming(aPub) { b.send(it) }
        assertEquals(false, bDoneFromA)  // B is responder, not done yet

        // B sends its public key to A; A receives → A is initiator (lex "A" < "B")
        val bPub = b.outbox.removeFirst()
        val aDoneFromB = a.kex.handleIncoming(bPub) { a.send(it) }
        assertTrue("Initiator A must be done after exchanging keys", aDoneFromB)

        // A also sent the wrapped AES key to B as ACK — drain any pending.
        // Find the ACK packet in A's outbox and feed to B.
        val ack = a.outbox.removeFirst()
        assertEquals(PacketType.ACK, ack.tp)
        val bDoneFromAck = b.kex.handleKeyAck(ack)
        assertTrue("Responder B must be done after KEY_ACK", bDoneFromAck)

        // Both engines must now hold a session key.
        assertTrue(a.engine.hasSessionKey())
        assertTrue(b.engine.hasSessionKey())
    }

    @Test fun `text message round-trips encrypted between paired engines`() {
        val a = Device("A")
        val b = Device("B")
        // Run the same handshake as above
        a.kex.initiate("A", "B") { a.send(it) }
        b.kex.initiate("B", "A") { b.send(it) }
        b.kex.handleIncoming(a.outbox.removeFirst()) { b.send(it) }
        a.kex.handleIncoming(b.outbox.removeFirst()) { a.send(it) }
        b.kex.handleKeyAck(a.outbox.removeFirst())

        // Build a TEXT packet, pack + encrypt on A, decrypt + unpack on B.
        val packet = MessagePacket(i = 0, c = "hello B", t = 1L, tp = PacketType.TEXT)
        val packed = a.serializer.packMessage(packet)
        val onWire = a.engine.encrypt(packed)

        val received = b.engine.decrypt(onWire)
        val unpacked = b.serializer.unpackMessage(received)
        assertEquals(packet, unpacked)
    }

    @Test fun `ciphertext on the wire contains zero plaintext bytes`() {
        // NFR-03.1: BT capture must show ciphertext only.
        val a = Device("A"); val b = Device("B")
        a.kex.initiate("A", "B") { a.send(it) }
        b.kex.initiate("B", "A") { b.send(it) }
        b.kex.handleIncoming(a.outbox.removeFirst()) { b.send(it) }
        a.kex.handleIncoming(b.outbox.removeFirst()) { a.send(it) }
        b.kex.handleKeyAck(a.outbox.removeFirst())

        val plain = "this MUST NOT appear in the ciphertext".toByteArray(Charsets.UTF_8)
        val cipher = a.engine.encrypt(plain)
        // Search ciphertext for the plaintext substring.
        val cipherStr = cipher.toString(Charsets.ISO_8859_1)
        val plainStr = plain.toString(Charsets.ISO_8859_1)
        assertEquals(-1, cipherStr.indexOf(plainStr))

        // And verify decryption still returns the exact bytes.
        assertArrayEquals(plain, b.engine.decrypt(cipher))
    }
}
