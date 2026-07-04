# BlueWhisper — Architecture Audit & Parallel Implementation Plan

> **Author:** Architect terminal · **Date:** 2026-07-04
> **Basis:** `USER_STORIES.md` (client requirements) verified against the implemented code.
> **Method:** Four parallel code audits (transport/crypto · chat/files · wipe/privacy/lifecycle · onboarding/settings/i18n/a11y), each reading the real source and mapping every user story to a status + evidence.

---

## 1. Executive verdict

The app is **substantially built** — onboarding, discovery UI, chat, file picker/transfer, foreground service, and notifications exist and much works. But **several headline promises — "end-to-end encrypted," "leave no trace," "offline Bluetooth" — are not delivered as specified.** These are the reasons the product exists, so they are P0.

**Scorecard (~80 stories):** ~45 solid · ~25 partial/deviating · ~10 missing or broken. The broken ones cluster on privacy, security, and the wipe guarantee.

---

## 2. Transport decision (the foundation)

**Current:** Google Nearby Connections (`Strategy.P2P_STAR`, `play-services-nearby:19.1.0`).

**Problem:** It **requires Google Play Services** and **opportunistically uses the Wi-Fi radio** (Wi-Fi Direct/hotspot) even with Wi-Fi toggled off — so "offline / Bluetooth-only / no third party" (US-1.6) is not literally true. It also **hides the radio**, so RSSI proximity (US-3.5), 10 s advertising-packet pruning (US-3.3), and duty-cycled scanning (US-3.7) cannot be implemented as written (the current code fakes them with recency proxies that misbehave).

**Decision (optimized for all phone brands + old Android):** replace Nearby with a **capability-detected hybrid Bluetooth transport behind a `Transport` interface.**

| Layer | Technology | Compatibility | Why |
|---|---|---|---|
| **Data / session channel** | **Classic Bluetooth RFCOMM** (`BluetoothServerSocket` / `BluetoothSocket`, SPP UUID) | **API 5+, 100% of BT devices, every brand** | Universal floor; reliable; high-throughput; 25 MB files fine |
| **Discovery (preferred)** | **BLE advertise + scan** (`BluetoothLeAdvertiser` / `BluetoothLeScanner`) | API 26+ **where chipset supports peripheral mode** | Matches stories (RSSI, duty cycle, background); low power |
| **Discovery (fallback)** | **Classic BT inquiry + SDP** (`startDiscovery`, `ACTION_FOUND`) | **All devices** | Covers budget/old devices lacking BLE advertising; RSSI via `EXTRA_RSSI` |

- Runtime capability check: `adapter.isMultipleAdvertisementSupported()` selects BLE vs classic discovery.
- **No Play Services, no Wi-Fi radio, no internet** → US-1.6 becomes literally true.
- Encryption stays **app-layer AES-256-GCM + RSA-2048** (already implemented) and is applied to **both text and files** over whichever socket.
- Classic-BT discoverability maps to `ACTION_REQUEST_DISCOVERABLE` (max 300 s, needs consent) — documented UX cost on the fallback path only.
- `minSdk 26` is retained (covers ~99% of devices); it can be lowered to 21/23 later for wider reach since GMS is no longer required.

**Consequence:** the transport rewrite is Track A (Architect-owned). All other fixes live *above* the `Transport` interface and proceed in parallel.

---

## 3. Findings by severity

Status legend: **IMPL** ok · **PARTIAL** incomplete · **DEVIATES** works but not as specified · **MISSING/BROKEN**.

### P0 — Core promises not met (privacy, security, wipe)

| US | Issue | Evidence | Fix |
|---|---|---|---|
| US-10.5 | **`secureDelete` doesn't overwrite** — `FileOutputStream(file)` truncates to 0 before length is read → 0 bytes written, then delete. Plain delete. | `FileManager.kt:118-133` | Capture `length` before opening; overwrite via `RandomAccessFile`/channel without truncation; then delete. |
| US-9.1 | **Files are NOT app-encrypted** — sent as raw `Payload.fromFile`; rely only on transport crypto. | `NearbyConnectionsManager.kt:235-243` | Encrypt file bytes with the AES session key (streamed chunks) before send; decrypt on receive. |
| US-6.1 | **Key exchange is unauthenticated** (MITM-able) — no connection auth token used. | `KeyExchangeManager.kt`; transport connect | **[CONFIRMED]** Add short numeric-compare confirmation: both peers derive & display the same code from the exchanged keys; each taps to confirm before chat opens. One extra step in the connection flow. |
| US-11.3 / 11.4 / 15.2 | **Swipe-away leaves temp files** — cleanup runs in `viewModelScope.launch{}` in `onCleared()` after the scope is cancelled → no-op. No startup sweep. | `ChatViewModel.kt:418-433` | Move cleanup to `Service.onTaskRemoved` + a daemon thread (pattern already in `FileViewerViewModel.kt:185-191`); add startup temp sweep. |
| US-11.2 / 15.1 | **BT-off wipe may never fire** — `BluetoothStateReceiver` is manifest-registered for an implicit broadcast blocked for backgrounded apps (API 26+). | `AndroidManifest.xml:117-123`; `BluetoothStateReceiver.kt:34-46` | Register the receiver at runtime from the foreground service. |
| US-11.5 | **AES key copies not zeroed** — only the engine's internal array is `fill(0)`-ed; copies in `SecretKeySpec` linger. | `EncryptionEngine.kt:47-48,64-65,84,117,135-142` | Zero all copies; minimize copies; document RSA JCA keys are unzeroable. |
| ZERO-TRACE | **Room `saved_files` metadata persists** across sessions/uninstall (sender, timestamps). No wipe path clears it. | `Database.kt:8-75` | **[CONFIRMED intended exception]** Only *explicitly-saved* files persist. Ensure no wipe path deletes them (Track B); state the exception plainly in Settings (Track C, US-14.4). |

### P1 — Broken user-facing flows

| US | Issue | Evidence | Fix |
|---|---|---|---|
| US-1.3 | **Notification permission never requested at runtime** → alerts silently suppressed on API 33+ (also breaks US-5.4 / 12.3). | declared `Manifest:41` only | Request `POST_NOTIFICATIONS` at runtime; handle denial messaging. |
| US-1.4 / 1.5 | **"Open Settings" and "Turn On Bluetooth" buttons do nothing** (both `navigateUp`). No BT-off detection; no auto-resume on BT-on. | `BluetoothErrorScreen.kt:86,96-103`; `BluetoothStateReceiver.kt:42-45` | Wire `ACTION_REQUEST_ENABLE`, `ACTION_APPLICATION_DETAILS_SETTINGS`; detect BT-off and route; resume scan on STATE_ON. |
| US-6.4 | **Key-exchange failure has no recovery** — chat hangs on "Securing…" forever. | `KeyExchangeManager.kt:62-64,114-117,135-138` | Add KEX timeout → abort + error + return Home; never open plaintext path. |
| US-15.6 | **Simultaneous mutual-request tie-break broken** — hardcoded "A"/"B"; both sides can self-elect initiator → key mismatch. | `KeyExchangeManager.kt:87-89`; `NearbyConnectionsManager.kt:406-407` | Deterministic tie-break by comparing stable device identifiers. |
| US-11.6 / 11.7 | **Manual "End Chat" never shows Disconnect screen** — `disconnect()` sets `isConnected=false` before the event that triggers nav. | `ChatViewModel.kt:400,110`; `ChatScreen.kt:76-83` | Emit `ChatEvent.Disconnected` directly from `disconnect()`. |
| US-8.4 / 15.10 | **Error/permission messages swallowed** — `onError = { clearToast() }` discards the string ("too large", unsupported, permission-denied never shown). | `ChatScreen.kt:105` | Pass the message through to a snackbar/toast. |
| US-8.5 | **Unsupported file types accepted** — `validate()` only checks size, always returns OK. | `FileManager.kt:83-92` | Enforce allowed MIME allow-list; return `UnsupportedType`. |
| US-14.2 | **Avatar can't be changed in Settings** — tap flips a boolean; no picker rendered; `updateAvatar()` unreachable. | `SettingsScreen.kt:34,93`; `SettingsViewModel.kt:61-63` | Render avatar-picker dialog wired to `updateAvatar()`. |
| US-16.3 / 14.3 | **Bangla parity not rendered** — `strings.xml` has full 73/73 EN↔BN parity, but Onboarding/Settings/SavedFiles **hardcode English** and never read resources. | screens use no `stringResource`; `values-bn/strings.xml` complete | Route every user-facing string through `strings.xml`; delete inline EN/BN ternaries. |

### P2 — Polish / partial

| US | Issue | Evidence |
|---|---|---|
| US-7.6 | Shared 20-counter **desyncs on files** (sent files not counted, received are). | `ChatViewModel.kt:285-358` |
| US-9.4 | **Receiver holds no WakeLock** during its transfer. | `ChatViewModel.kt:319,156,162` |
| US-10.6 | **No per-file "decline"** action. | `ChatScreen.kt:462-471` |
| US-7.4 | **No visible character counter.** | `ChatScreen.kt:581` |
| US-3.7 / 3.3 | **Duty-cycle bug** → near-continuous scanning + false-pruning of present devices. | `NearbyConnectionsManager.kt:25-26,62-73,126-153` |
| US-3.5 | Proximity drifts to "Far away" for long-present devices. | `NearbyConnectionsManager.kt:83-90` |
| US-2.1 / 2.2 | Nickname `|`/over-length silently sanitized (no message); avatar always pre-selected. | `OnboardingViewModel.kt:19,34-42` |
| US-1.2 | Missing `NEARBY_WIFI_DEVICES` (moot after transport swap; note for interim). | `Manifest` |
| US-16.4 | Accessibility: back buttons + most controls lack `contentDescription`/semantics. | `SettingsScreen.kt:64`; `SavedFilesScreen.kt:114` |
| US-3.8 | Saved Files not reachable directly from Home (only via Settings). | `Navigation.kt:69,165` |
| US-4.2 / 4.3 / 4.4 | Cancel-outgoing uses wrong API; no explicit "declined"/"timed out" text. | `ConnectionViewModel.kt:95-101`; `ConnectionScreens.kt` |

---

## 4. What's solid — do NOT touch

Text encryption on the wire (AES-256-GCM, real app-layer E2E for messages), RSA-2048/AES-256 key setup, encrypted indicator (US-6.3), splash + onboarding routing (US-2.5/2.6), DataStore profile with no account (US-2.4), saved-files list/open/share/delete + storage total (US-13.x), foreground service ownership of scanning (US-12.1/12.2/12.3), 2.5 s Disconnect screen (US-11.6 duration), peer-initiated disconnect wipe (US-11.8/15.4). String resource files are complete and correctly translated — they just need to be *consumed*.

---

## 5. Parallel implementation plan — three terminals

**Ownership split minimizes file collisions.** `ChatViewModel.kt` is the one hotspot — ownership is partitioned by concern (see notes).

### Track A — Architect (transport foundation + protocol + security core)
Owns the risky core; defines the interface everyone else codes against.
1. **Day-1 deliverable (unblocks B & C):** define the `Transport` interface (discovery, connect, send/receive frames, disconnect events) + the wire framing contract.
2. Implement `BleClassicTransport` (BLE discovery + RFCOMM channel) and `ClassicOnlyTransport` fallback; runtime capability detection.
3. Real RSSI proximity, 10 s pruning, true duty-cycle (fixes US-3.3/3.5/3.7 for real).
4. **P0:** file-payload AES encryption (US-9.1); authenticated key exchange (US-6.1); deterministic simultaneous-connect tie-break (US-15.6); KEX-failure timeout/abort (US-6.4).
5. Remove `play-services-nearby`; update manifest permissions.
- **Files:** `bluetooth/*` (replace `NearbyConnectionsManager`), `KeyExchangeManager`, `EncryptionEngine`, `MessageSerializer`, transport hooks in the service. Consumes ChatViewModel only via the interface.

### Track B — Privacy, wipe & lifecycle
The zero-trace guarantee end to end.
1. **P0:** fix `secureDelete` overwrite (US-10.5); AES key-copy zeroing (US-11.5).
2. **P0:** `Service.onTaskRemoved` + daemon-thread wipe (US-11.3/11.4); startup temp sweep (US-15.2); runtime-registered `BluetoothStateReceiver` (US-11.2/15.1).
3. **P1:** notification-permission runtime request (US-1.3); BT enable + app-settings deep-links + BT-off detection/resume (US-1.4/1.5/15.1); manual-end `ChatEvent.Disconnected` emission (US-11.6/11.7).
- **Files:** `FileManager`, `BluetoothForegroundService`, `MainActivity`, `BlueWhisperApp`, `BluetoothStateReceiver`, `BluetoothErrorScreen`, **ChatViewModel (wipe/disconnect methods only)**.

### Track C — Chat/file UX, i18n & accessibility
Everything the user sees and reads.
1. **P1:** surface swallowed error/permission messages (US-8.4/15.10); MIME allow-list (US-8.5); avatar-picker in Settings (US-14.2); full Bangla parity — route all screens through `strings.xml` (US-16.3/14.3).
2. **P2:** char counter (US-7.4); file counter-desync (US-7.6); receiver WakeLock (US-9.4); per-file decline (US-10.6); nickname/avatar validation messages (US-2.1/2.2); a11y labels (US-16.4); Home→SavedFiles nav (US-3.8); "declined"/"timed out" text (US-4.3/4.4).
- **Files:** `presentation/screens/{chat,fileviewer,settings,savedfiles,onboarding,home}/*`, `res/values*/strings.xml`, **ChatViewModel (send/counter/UX methods only)**.

### Sequencing & checkpoints
- **Gate 1 (Track A, day 1):** `Transport` interface + framing contract merged → B and C proceed unblocked.
- **Gate 2:** each track completes its **P0** items → Architect review (`/code-review` + on-device `verify`) before merge.
- **Gate 3:** P1 → integration test on two physical devices across ≥2 brands (incl. one budget/old device to exercise the classic-BT fallback).
- **Gate 4:** P2 + full regression against the acceptance criteria in `USER_STORIES.md`.
- **ChatViewModel coordination:** B touches wipe/disconnect; C touches send/counter/UX; A calls it only via the interface. Land B's and C's ChatViewModel edits on separate small PRs to avoid conflicts.

---

## 6. Confirmed decisions (client, 2026-07-04)
- **minSdk stays 26.** No lowering; ~99% device coverage is acceptable.
- **Authenticated key exchange:** ADD a short numeric-compare confirmation (both peers see the same code; one tap to confirm) to close the MITM gap. This becomes part of Track A's US-6.1 work and adds a confirmation step to the connection flow (both Sender and Receiver) before the chat opens.
- **`saved_files` is the intended durable exception to zero-trace.** Only explicitly-saved files persist. Settings MUST state this plainly (US-14.4): "Messages and unsaved files vanish on disconnect. Files you tap Save are kept in your Downloads." Track C owns the Settings copy; Track B ensures no wipe path touches saved files.
