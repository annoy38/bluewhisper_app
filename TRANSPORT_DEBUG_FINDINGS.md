# BlueWhisper — Transport Debug Findings & Current Bugs

> **Scope:** On-device debugging of the Track A native `BluetoothTransport`
> (classic inquiry discovery + RFCOMM) via the debug-only **BW Transport Test**
> harness. Branch: `track-a/native-transport-foundation`.
> **Last updated:** 2026-07-10.
> **Status legend:** 🔴 open/blocking · 🟡 hypothesis (needs on-device confirm) ·
> 🟢 fixed this session · ⚪ known/architectural.

---

## 1. Test setup

- **Phone A:** Redmi Note 7S (MIUI), Android 10 (API 29).
- **Phone B:** Realme 5i (ColorOS), Android 10 (API 29).
- Both installed the debug **BW Transport Test** APK (CI artifact).
- Emulators cannot be used — AVDs have no Bluetooth radio.
- No adb/USB connection available yet (phones are not USB-C; testing via
  on-screen log + photos). This slows every iteration to a CI round-trip.

---

## 0. 🟢 RESOLUTION (2026-07-11) — two root causes fixed

The blocking discovery bug (§2) had **two independent root causes**, both now fixed in
source. Pending final on-device confirmation with the corrected test procedure below.

**Root cause A — radio-mode conflict (hits the Android-10 pair).** `BluetoothTransport`
never enforced that advertising (being discoverable + running the RFCOMM accept loop) and
discovery (classic inquiry) are **mutually exclusive**. The harness *Advertise* button makes
the phone discoverable for 300s *and* starts the server; tapping *Discover* afterward left the
adapter discoverable while inquiring. Most classic-BT stacks silently refuse to inquire while
discoverable → `startDiscovery()` returns `true` but never scans, so no `ACTION_DISCOVERY_FINISHED`.
*Fix:* `startDiscovery()` now calls `stopAdvertising()` first, `startAdvertising()` calls
`stopDiscovery()` first, and `runInquiryDutyCycle()` waits for `cancelDiscovery()` to settle
before a fresh `startDiscovery()`. An `ACTION_DISCOVERY_STARTED` log line now gives a definitive
"the radio really started scanning" signal, and a loud warning fires if the phone is still stuck
in a discoverable window (which software cannot cancel — see below).

**Root cause B — `neverForLocation` (hits any Android-12+ phone).** The manifest declared
`BLUETOOTH_SCAN` with `usesPermissionFlags="neverForLocation"` and capped `ACCESS_FINE_LOCATION`
at `maxSdkVersion=30`. On API 31+ that means classic inquiry is accepted but returns **zero
results** (the OS treats inquiry as location-derived). *Fix:* removed `neverForLocation`, extended
`ACCESS_FINE_LOCATION` to all API levels, and the debug harness now requests it on API 31+ too.

**The one thing software cannot fix:** an active `ACTION_REQUEST_DISCOVERABLE` window (up to
300s) keeps a phone discoverable and blocks its own inquiry, and there is no API to end it early.
So the discovering phone must not be in that window — see the corrected procedure.

**Corrected manual test (do this to confirm):**
1. Keep the two roles on **separate** phones. Phone A = advertiser, Phone B = discoverer.
2. On **Phone B (discoverer): do NOT tap Advertise.** If you did earlier, toggle Bluetooth
   OFF then ON on Phone B to clear any leftover discoverable window.
3. Phone A: tap **Advertise**, accept the "make discoverable" system prompt.
4. Phone B: tap **Discover**. Watch its log for `inquiry STARTED` → `found:` → A appearing in
   the Nearby list. `inquiry STARTED` appearing is the proof the radio is now really scanning.

---

## 2. 🔴 BLOCKING BUG — discovery inquiry never actually runs

**Symptom:** two phones never see each other. In the harness the discoverer logs:

```
inquiry cycle: startDiscovery() -> true, isDiscovering=false
```

repeated every cycle, with **zero `ACTION_FOUND`** and **zero
`ACTION_DISCOVERY_FINISHED`** across 8+ cycles (2+ minutes).

**Why this is the smoking gun:** `ACTION_DISCOVERY_FINISHED` fires ~12 s after
*every* real inquiry, even when no device is found. Its total absence proves the
adapter accepts the discovery request (`startDiscovery()` returns `true`) but
**never actually performs the inquiry scan** (`isDiscovering` stays `false`).

**What has been RULED OUT (all verified green on-device):**

| Ruled-out cause | Evidence |
|---|---|
| Bluetooth off | `btEnabled=true` |
| Scan permission missing | `scanPerm=true` |
| Connect permission missing | `connPerm=true` |
| Location *permission* missing | `fineLocationPerm=true` |
| Location *services* toggle off | `locationServicesOn=true` |
| Peer not discoverable | Phone B's **system Bluetooth saw + paired** `BW|Tester|1` |
| Receiver not registered | `discovery receiver registered` logs fine |
| Receiver export flag | Protected system broadcasts reach `NOT_EXPORTED` receivers |
| Adapter rename race | Name applies in ~500 ms (see §4) |

**🟡 Leading hypothesis:** the discoverer phone is still in an
**advertising / discoverable** state from an earlier "Advertise" tap — its
adapter name is still `BW|Tester|1`, i.e. `stopAdvertising()` never ran. On
classic Bluetooth **a device that is currently *discoverable* generally cannot
run an *inquiry* at the same time** (conflicting radio operations), so the
adapter silently refuses to scan while remaining discoverable.

**Next step to confirm (no rebuild):** on the discoverer, tap **Stop**, wait
~2 s, then tap **Discover** only (keep the *other* phone advertising). If
`isDiscovering=true`, `inquiry FINISHED`, and `found:` lines then appear, the
discoverable-vs-inquiry conflict is confirmed.

**If that fails:** capture `adb logcat` on the discoverer — the Bluetooth stack
logs the concrete reason it won't start inquiry. This is the fastest resolution
and needs a USB cable (both phones are micro-USB).

**Likely code fix (once confirmed):** the transport must guarantee it is **not
advertising/discoverable while discovering** and vice-versa (mutually exclusive
radio modes), instead of allowing both to be active. The harness currently lets
the user tap both.

---

## 3. ⚪ Architectural concerns exposed by this bug

These are not yet "fixed" — they are design risks the debugging surfaced.

- **⚪ Discoverable-mode dependency is fragile.** Classic inquiry discovery
  requires the advertiser to be in discoverable mode, which on classic BT needs
  the `ACTION_REQUEST_DISCOVERABLE` system prompt (max 300 s, user consent) and
  is handled inconsistently across OEMs. MIUI keeps Bluetooth *always*
  discoverable; stock/ColorOS require the prompt. This is the main reliability
  risk of the classic-only discovery approach.
- **⚪ Peer identification by renaming the adapter is hacky.** Embedding
  `BW|nickname|avatarId` in the device Bluetooth name changes the user's global
  BT name and is async. A more robust scheme is SDP service-UUID matching (find
  peers by the app's RFCOMM UUID rather than by name) — deferred.
- **⚪ Inquiry + RFCOMM-server + discoverable coexistence** needs an explicit
  state machine so the three radio activities never conflict (this is the
  suspected trigger of the §2 bug).

---

## 4. 🟢 Bugs fixed during this session

- **🟢 `setName` propagation race.** After `adapter.setName("BW|…")` the adapter
  still reported the old name; peers filtered it out. Now the transport watches
  until the name actually applies (confirmed ~500 ms on the Redmi) and reads the
  fresh inquiry name (`EXTRA_NAME`) instead of the stale cached `device.name`.
- **🟢 `hasScanPermission()` wrong on Android < 12.** It returned `true`
  unconditionally on API 26–30 without checking `ACCESS_FINE_LOCATION`, which
  classic discovery requires on those versions. Now it checks fine-location on
  API < 31.
- **🟢 Receiver export flag (regression avoided).** A mid-session change to
  `RECEIVER_EXPORTED` was reverted to `RECEIVER_NOT_EXPORTED`: `ACTION_FOUND` /
  `ACTION_DISCOVERY_FINISHED` / `ACTION_SCAN_MODE_CHANGED` are protected system
  broadcasts already delivered to `NOT_EXPORTED` receivers, and exporting would
  let a malicious app inject spoofed peers (flagged by security review).

---

## 5. 🟢 Diagnostics added (debug harness only)

On-screen debug stream (`BluetoothTransport.debugLog`) surfaced in the harness,
so device testing needs **no adb cable**:

- discovery-start dump: sdk, BT enabled, scan/connect/fine-location perms,
  Location Services toggle, adapter name;
- per inquiry cycle: `startDiscovery()` return value **and** `isDiscovering`;
- every device seen (`found:` mac / inquiry name / cached name) + keep/ignore
  reason; `bcast:` for every broadcast reaching the receiver;
- advertise: requested vs applied name, `scanMode` (DISCOVERABLE vs
  connectable-only), and a scan-mode-change monitor.

---

## 6. ⚪ Pre-existing known bugs (from `AUDIT_AND_PLAN.md`, not this session)

Still outstanding, tracked separately; listed here for a single view.

**P0 (privacy/security core):**
- `secureDelete` does not overwrite (truncates to 0 before writing) — `FileManager`.
- Files sent as plaintext over the transport (app-layer AES not applied) — being
  addressed by the new `RfcommConnection` file path.
- Key exchange unauthenticated (MITM) — SAS numeric-compare added in Track A.
- Swipe-away leaves temp files (cleanup runs after scope cancelled).
- BT-off wipe may never fire (manifest-registered receiver blocked on API 26+).
- AES key copies not zeroed.

**P1 (broken user flows):**
- Notification permission never requested at runtime (API 33+).
- "Open Settings" / "Turn On Bluetooth" buttons do nothing.
- Key-exchange failure hangs on "Securing…" forever (no timeout).
- Simultaneous mutual-request tie-break broken (hardcoded A/B).
- Manual "End Chat" never shows the Disconnect screen.
- Error/permission messages swallowed.
- Unsupported file types accepted (validate only checks size).
- Avatar can't be changed in Settings.
- Bangla parity not rendered (screens hardcode English).

---

## 7. Open questions / next actions

1. **Confirm §2** with the Stop → Discover test (no rebuild), or via `adb logcat`.
2. If confirmed, implement the **mutually-exclusive radio-mode** state machine
   in `BluetoothTransport` (never discover while discoverable, and vice-versa).
3. Re-test the full path on two devices: discover → connect → KEX → SAS →
   message → file.
4. Only then proceed to the **production flip** (Nearby → Transport) per
   `TRANSPORT_CONTRACT.md` / `AUDIT_AND_PLAN.md` §7.
