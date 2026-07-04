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

## 3. Discovery — Classic Bluetooth for BOTH discovery and data (decided 2026-07-04)

**Why not BLE for discovery:** RFCOMM needs the peer's **classic BR/EDR MAC**, but a BLE
scan returns a **randomized/resolvable BLE address** by default (and `getAddress()` is
blocked since Android 6, so we can't advertise our own MAC either). A BLE-found device
therefore can't be reliably RFCOMM-connected. Classic inquiry returns the real MAC, so it
is the correct rendezvous for an RFCOMM data channel — and it is the most compatible path
across all brands + old Android (API 5+), which is the priority.

- **Discovery:** classic inquiry — `adapter.startDiscovery()` + a runtime `BroadcastReceiver`
  for `ACTION_FOUND` (device MAC + name + `EXTRA_RSSI` → `SignalStrength`, US-3.5) and
  `ACTION_DISCOVERY_FINISHED` (duty-cycle restart, US-3.7). Peers are identified by a name
  marker: the adapter name is set to **`BW|nickname|avatarId`**; only `BW|`-prefixed devices
  are shown. Prune peers unseen >10 s (US-3.3).
- **Discoverability (US-3.4):** to be *found by inquiry* a device must be discoverable, which
  on classic BT requires the system `ACTION_REQUEST_DISCOVERABLE` prompt (max 300 s), launched
  from an Activity. `startAdvertising` sets the name + starts the RFCOMM server; the **UI owns
  launching the discoverable intent** (add to Home's toggle). Being *connectable* (accepting
  RFCOMM) does not need the prompt — only being *findable* does.
- **Data channel:** Classic RFCOMM — `listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)`
  accept loop on the advertiser; `createRfcommSocketToServiceRecord(APP_UUID)` + `connect()`
  on the requester.
- **BLE (future, optional):** may later be added purely as a low-power presence beacon to
  soften the discoverability prompt — never as the RFCOMM rendezvous.

### 3a. App-level connect/accept handshake (RFCOMM has no request/accept primitive)
Once an RFCOMM socket connects, the link is up; accept/decline is an app handshake over it:
1. Requester connects → sends `CONNECT_REQUEST` (`nickname|avatarId|nonceHex`) → state `Requesting`.
2. Advertiser's accept loop reads `CONNECT_REQUEST` → state `IncomingRequest` → UI/notification.
3. Accept → `CONNECT_ACCEPT` → both proceed to KEX. Decline → `CONNECT_REJECT` → close.

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

## 7. Implementation status (2026-07-04)

Built and CI-compile-verified on branch `track-a/native-transport-foundation` (additive; Nearby still live):
- ✅ `Transport` interface + `BTEvent` additions
- ✅ `wire/Framer`, `wire/SasGenerator`, `wire/Control` (+ Framer/Sas unit tests, passing)
- ✅ `RfcommConnection` — framed link, chunked AES file transfer
- ✅ `BluetoothTransport` — classic inquiry discovery + RFCOMM + handshake + KEX/SAS + nonce tie-break
- ✅ `src/debug` on-device test harness ("BW Transport Test" launcher)

**Pending:** (1) **device testing on two phones** — emulators have no Bluetooth radio, so this cannot be
validated any other way; see the test guide in `AUDIT_AND_PLAN.md` §8. (2) **The production flip** — rebind
DI `Nearby → Transport`, migrate consumers off `Payload`, wire the SAS-confirmation UI, add the
discoverable intent to Home, delete `play-services-nearby`. The flip touches Track B/C files — coordinate.

Full per-commit status and file list: `AUDIT_AND_PLAN.md` §7.
