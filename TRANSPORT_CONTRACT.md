# BlueWhisper — Transport Contract (Track A, Day-1 Gate)

> **Status:** Interface merged. This is the stable seam Tracks B and C code against.
> **Interface:** `app/src/main/java/com/bluewhisper/bluetooth/Transport.kt`
> **Implements later (Track A):** native BLE + Classic-Bluetooth RFCOMM; the current
> `NearbyConnectionsManager` is the throwaway implementation until then.

---

## 1. Why an interface

Every ViewModel currently injects the concrete `NearbyConnectionsManager` and
`ChatViewModel` even imports Google Nearby's `Payload` / `PayloadTransferUpdate`.
`Transport` removes all Play-Services types from the call sites so the transport
can be swapped for the BLE+RFCOMM stack (per `AUDIT_AND_PLAN.md` §2) without
touching UI code.

**Rule for all tracks:** depend on `Transport`, never on `NearbyConnectionsManager`
or any `com.google.android.gms.*` type. If you need a transport capability that
isn't on the interface, ask the Architect to add it — don't reach through.

---

## 2. Wire framing (RFCOMM is a byte stream — framing is mandatory)

Unlike Nearby's discrete payloads, RFCOMM is a continuous stream. Every frame:

```
┌────────┬──────────────┬───────────────────────┐
│ type   │ length (u32) │ body (length bytes)    │
│ 1 byte │ big-endian   │                        │
└────────┴──────────────┴───────────────────────┘
```

| type | meaning | body |
|---|---|---|
| `0x01` CONTROL | key exchange / ACK / SAS / disconnect | serialized `MessagePacket` (KEY_EXCHANGE/ACK/DISCONNECT); encrypted once the key exists |
| `0x02` MESSAGE | chat text / typing / file-meta notice | DEFLATE(JSON `MessagePacket`) → **AES-256-GCM** |
| `0x03` FILE_META | file header before chunks | AES-GCM(JSON `FileMetadata` incl. transferId, name, size, type, senderNickname) |
| `0x04` FILE_CHUNK | one encrypted slice | AES-GCM(≤64 KB plaintext); receiver decrypts + appends to temp, emits progress |
| `0x05` FILE_END | end of a file | transferId → receiver emits `IncomingFile` |

- **Length-prefix framing removes any need for a delimiter char.** The reserved
  `|` (US-2.1) matters ONLY for the discovery advertising name `"nickname|avatarId"`,
  so Track C must keep rejecting `|` in nicknames.
- Message path reuses the existing `MessageSerializer.packMessage` (DEFLATE) +
  `EncryptionEngine` (AES-256-GCM). **Files now use the same AES key** (fixes US-9.1)
  — they are no longer handed to the transport as plaintext.
- `transferId` is app-assigned (`Long`) — replaces Nearby's `payload.id`. It stays a
  `Long` so `Models.FileMetadata.payloadId` and `ChatViewModel` need minimal edits.

## 3. Discovery

- **Preferred (BLE):** advertise a fixed service UUID; put `nickname|avatarId` in the
  scan-response (fits in 31 bytes for a 1–15 char nickname). RSSI → `SignalStrength`
  (US-3.5); prune peers unseen >10 s (US-3.3); duty-cycle scan (US-3.7).
- **Fallback (Classic):** used when `adapter.isMultipleAdvertisementSupported() == false`
  (budget/old devices). Discoverability via `ACTION_REQUEST_DISCOVERABLE`; discovery via
  `startDiscovery()` + `ACTION_FOUND` (RSSI from `EXTRA_RSSI`); device name carries
  `nickname|avatarId`.
- Data channel is **Classic RFCOMM in both modes** (`BluetoothServerSocket.accept()` on the
  advertiser; `BluetoothSocket.connect()` on the requester, fixed SPP UUID).

## 4. Key-exchange confirmation (US-6.1) — the new step

1. Connection established → RSA-2048 public keys exchanged → AES-256 session key wrapped
   (unchanged crypto).
2. Both peers derive the **same short numeric code** (SAS) from the exchanged key material.
3. Transport emits **`BTEvent.KeyConfirmationRequired(code)`** on both sides.
4. UI shows the code; user taps confirm/reject → **`Transport.confirmKeyMatch(accepted)`**.
5. **Both** accept → **`BTEvent.SessionKeyReady`** (the existing chat-enable gate — unchanged).
   Either rejects → **`BTEvent.KeyConfirmationRejected`** → abort + Home.
6. Timeout/exception anywhere → **`BTEvent.KeyExchangeFailed(msg)`** → abort + Home; never
   open a plaintext path (US-6.4).

> Consumers already wait for `SessionKeyReady` to open chat, so inserting confirmation
> before it is low-churn: just add UI for the two new events + the `confirmKeyMatch` call.

## 5. Simultaneous-connect tie-break (US-15.6)

Replace the hardcoded "A"/"B" initiator election. On connect, both peers exchange a random
64-bit session nonce (or compare stable MACs); the **higher value is the initiator** (generates
the AES key), the other is the responder. Deterministic under mutual requests → exactly one
session, one key.

---

## 6. Migration checklist per track (what changed vs. the old manager)

**Track A (owns the swap):**
- Make the transport a `@Singleton class … : Transport`; bind it in `AppModule`
  (`@Binds fun bindTransport(impl): Transport`). Rewire every `@Inject` site from
  `NearbyConnectionsManager` to `Transport`.
- Implement framing (§2), BLE/Classic discovery (§3), confirmation (§4), tie-break (§5),
  and app-layer file encryption. Delete `play-services-nearby`.

**Track B & C (code against `Transport` today):**
- Inject `Transport`, not `NearbyConnectionsManager`.
- **File API changed** (seals the Nearby leak in `ChatViewModel`):
  - Send: build an `OutgoingFile(uri, name, size, type, senderNickname)` and call
    `transport.sendFile(it)` → returns `transferId: Long?`. Do **not** construct `Payload`.
  - Receive: collect `transport.incomingFiles: SharedFlow<IncomingFile>` — each carries a
    decrypted `tempPath` + `FileMetadata`. Drop `copyPayloadToTemp` / `asFile()` /
    `pendingPayloads`.
  - Progress: collect `transport.transferProgress: SharedFlow<FileTransferProgress>`
    (fields `transferId`, `bytesTransferred`, `totalBytes`, `status`, `isIncoming`) instead
    of `PayloadTransferUpdate`. Use the transport's `TransferStatus`
    (`IN_PROGRESS/SUCCESS/FAILURE`).
- `startAdvertising(nickname, avatarId)` now takes both args — drop `formatAdvertisingName`.
- New events to handle in the connection/chat flow: `KeyConfirmationRequired`,
  `KeyConfirmationRejected`, `KeyExchangeFailed`.

---

## 7. Not yet done (rest of Track A)
The interface + framing/confirmation contract are defined. Still to build: the two
`Transport` implementations, the DI rebind, and removal of Nearby. Those proceed after
B and C start against this contract.
