# BlueWhisper — Test Cases (QA)

> Derived **only** from `USER_STORIES.md`. Black-box, requirement-based test cases mapping each user story and its **Given/When/Then** acceptance criteria to verifiable tests.
> **Priority** matches the story's own priority: **Must** (ship-blocking) · **Should** (important) · **Could** (nice-to-have) · **Future** (later phase, out of Phase-1 scope).
> **Type:** Functional (F), Negative (N), UI/UX (U), Security (S), Performance (P), Accessibility (A), Compatibility (C).
> Most session/chat/file tests need **two devices** (Device A = Sender/Initiator, Device B = Receiver/Responder).

---

## 1. Install, Permissions & Prerequisites

### TC-1.1.1 — Rationale shown before system BT dialog (US-1.1) · Must · F
- **Pre:** Fresh install; Bluetooth permission never granted.
- **Steps:** Open app, first attempt to discover people.
- **Expected:** A plain-language reason ("to find nearby people") appears *before* the system permission dialog.

### TC-1.1.2 — Continue opens Android permission dialog (US-1.1) · Must · F
- **Steps:** On the rationale screen, tap Continue.
- **Expected:** The Android permission dialog appears.

### TC-1.1.3 — Granting BT starts discovery immediately (US-1.1) · Must · F
- **Steps:** Grant the Bluetooth permission.
- **Expected:** Dialog closes and discovery starts immediately.

### TC-1.1.4 — Denying BT shows blocked message + retry (US-1.1) · Must · N
- **Steps:** Deny the Bluetooth permission.
- **Expected:** Clear message that discovery is blocked, plus a button to try again.

### TC-1.2.1 — Correct nearby/location permission per Android version (US-1.2) · Must · C
- **Pre:** Android version that requires the permission.
- **Steps:** Start discovery.
- **Expected:** App requests the correct permission for that version.

### TC-1.2.2 — Rationale states discovery-only, not tracking (US-1.2) · Must · U
- **Expected:** The rationale explicitly states the permission is used only for nearby discovery, never for location tracking.

### TC-1.2.3 — Denying nearby permission disables discovery with retry (US-1.2) · Must · N
- **Steps:** Deny the permission.
- **Expected:** Discovery disabled with a clear explanation and a retry option.

### TC-1.3.1 — Notification permission requested when first needed (US-1.3) · Must · F
- **Pre:** Android version requiring runtime notification permission.
- **Steps:** Reach the point of first needing background alerts.
- **Expected:** App requests notification permission.

### TC-1.3.2 — Granted notifications enable background alerts (US-1.3) · Must · F
- **Steps:** Grant notification permission.
- **Expected:** Incoming-request alerts can be received while backgrounded.

### TC-1.3.3 — Denied notifications tells user + how to enable later (US-1.3) · Must · N
- **Steps:** Deny notification permission.
- **Expected:** App states background alerts are off and how to enable them later.

### TC-1.4.1 — Prompt with one-tap enable when BT off, on Home (US-1.4) · Must · F
- **Pre:** Bluetooth off.
- **Steps:** Open Home.
- **Expected:** Clear prompt with a one-tap way to enable Bluetooth.

### TC-1.4.2 — Discovery with BT off explains blocker, no silent fail (US-1.4) · Must · N
- **Steps:** With BT off, try to discover.
- **Expected:** App explains the blocker instead of failing silently.

### TC-1.4.3 — Enabling BT auto-starts discovery on return (US-1.4) · Must · F
- **Steps:** Enable Bluetooth, return to the app.
- **Expected:** Discovery starts automatically.

### TC-1.5.1 — "Don't ask again" → settings deep-link (US-1.5) · Should · N
- **Pre:** Permission set to "Don't ask again".
- **Steps:** Try the blocked feature.
- **Expected:** App shows which feature is unavailable and a button opening the app's system settings page.

### TC-1.5.2 — Re-enabling in settings works without restart (US-1.5) · Should · F
- **Steps:** Re-enable the permission in system settings, return to app.
- **Expected:** Feature works without restarting the app.

### TC-1.6.1 — All features work with Wi-Fi + mobile data off (US-1.6) · Must · S
- **Pre:** Wi-Fi and mobile data both off.
- **Steps:** Exercise onboarding, discovery, chat, file transfer, saving.
- **Expected:** Every feature works normally.

### TC-1.6.2 — Never shows a network error / requires internet (US-1.6) · Must · S
- **Steps:** Use the app throughout.
- **Expected:** No network/connectivity error is ever shown; internet is never required.

---

## 2. Onboarding & Profile Creation

### TC-2.1.1 — Empty nickname disables Continue (US-2.1) · Must · N
- **Steps:** Leave nickname empty.
- **Expected:** Continue button disabled.

### TC-2.1.2 — Input stops at 15 chars / blocks continue (US-2.1) · Must · N
- **Steps:** Type more than 15 characters.
- **Expected:** Input stops at 15 (or shows an error and blocks continuing).

### TC-2.1.3 — `|` character rejected with message (US-2.1) · Must · N
- **Steps:** Type `|`.
- **Expected:** Rejected with a message that `|` is not allowed.

### TC-2.1.4 — Valid nickname accepted, advances (US-2.1) · Must · F
- **Steps:** Enter valid 1–15 char nickname without `|`, tap Continue.
- **Expected:** Accepted; moves to next step.

### TC-2.2.1 — Fixed avatar library shown (US-2.2) · Must · U
- **Expected:** A fixed library of avatars is displayed on the avatar step.

### TC-2.2.2 — Cannot continue without avatar (US-2.2) · Must · N
- **Steps:** Leave no avatar selected, try to continue.
- **Expected:** Continue blocked.

### TC-2.2.3 — Selected avatar highlighted, enables continue (US-2.2) · Must · F
- **Steps:** Tap an avatar.
- **Expected:** Visibly highlighted as selected; can continue.

### TC-2.3.1 — Both English and Bangla offered (US-2.3) · Must · U
- **Expected:** Language step offers both English and Bangla.

### TC-2.3.2 — Choosing Bangla applies Bangla text from then on (US-2.3) · Must · F
- **Steps:** Choose Bangla, confirm.
- **Expected:** App text appears in Bangla from that point on.

### TC-2.4.1 — No account/credentials ever requested (US-2.4) · Must · S
- **Steps:** Complete onboarding.
- **Expected:** No email, phone, password, or login requested at any point.

### TC-2.4.2 — Profile remembered after close/reopen (US-2.4) · Must · F
- **Steps:** Complete onboarding, close and reopen.
- **Expected:** Nickname, avatar, and language remembered.

### TC-2.5.1 — Returning user lands on Home, no onboarding (US-2.5) · Must · F
- **Pre:** Onboarding completed before.
- **Steps:** Open app.
- **Expected:** Lands on Home; onboarding not shown again.

### TC-2.5.2 — Cleared data re-shows onboarding (US-2.5) · Must · F
- **Steps:** Clear app data, open app.
- **Expected:** Onboarding shown again.

### TC-2.6.1 — Splash shows then dismisses when ready (US-2.6) · Should · U
- **Steps:** Launch app.
- **Expected:** Splash shows while preparing, dismisses when first screen is ready.

### TC-2.6.2 — No UI freeze / blank white screen at startup (US-2.6) · Should · P
- **Steps:** Launch app.
- **Expected:** UI never freezes or shows a blank white screen.

---

## 3. Home & Discovery

### TC-3.1.1 — Scanning runs automatically on Home (US-3.1) · Must · F
- **Pre:** BT + permissions granted.
- **Steps:** Open Home.
- **Expected:** Scanning runs automatically.

### TC-3.1.2 — Nearby discoverable user shows nickname + avatar (US-3.1) · Must · U
- **Pre:** Another BlueWhisper user nearby and discoverable.
- **Expected:** They appear in the list with nickname and avatar.

### TC-3.2.1 — Explicit searching/empty state (US-3.2) · Should · U
- **Pre:** No devices found yet.
- **Expected:** List shows "searching for people nearby" / "no one nearby" state.

### TC-3.2.2 — Visible active-scanning indicator (US-3.2) · Should · U
- **Expected:** A visible indicator confirms scanning is active (not a blank screen).

### TC-3.3.1 — Device pruned ~10 s after it stops advertising (US-3.3) · Must · F
- **Steps:** Discover B, stop B advertising / move out of range, wait ~10 s.
- **Expected:** B removed from list.

### TC-3.3.2 — Reappearing device shown again (US-3.3) · Must · F
- **Steps:** Bring B back into range.
- **Expected:** B shown again.

### TC-3.4.1 — Discoverable ON → visible + receives requests (US-3.4) · Must · F
- **Steps:** B sets discoverability ON.
- **Expected:** Others can see B and send requests.

### TC-3.4.2 — Discoverable OFF → invisible + no requests (US-3.4) · Must · S
- **Steps:** B sets discoverability OFF.
- **Expected:** B disappears from others' lists and receives no requests.

### TC-3.4.3 — Toggle clearly shows current state (US-3.4) · Must · U
- **Expected:** Toggle clearly reflects ON/OFF state.

### TC-3.5.1 — Relative proximity/recency hint per device (US-3.5) · Could · U
- **Expected:** Each entry shows a relative proximity/recency hint (exact distance not required).

### TC-3.6.1 — List continuously adds/removes devices (US-3.6) · Must · F
- **Steps:** Stay on Home; bring devices in and out of range.
- **Expected:** Arriving devices added, departed devices removed, no app restart.

### TC-3.6.2 — Scan error auto-recovers or offers retry (US-3.6) · Must · N
- **Steps:** Induce a scan error.
- **Expected:** App recovers automatically or offers a retry.

### TC-3.7.1 — Duty-cycle scanning (active/pause) (US-3.7) · Should · P
- **Steps:** Leave Home open, observe scan behavior over time.
- **Expected:** Scanning alternates active and pause periods rather than running continuously.

### TC-3.8.1 — Entry points to Settings and Saved Files (US-3.8) · Must · F
- **Steps:** From Home, tap Settings, then Saved Files.
- **Expected:** Clear entry points present; each opens the corresponding screen.

---

## 4. Connection Request — Sending

### TC-4.1.1 — Tap sends request + "requesting…" state (US-4.1) · Must · F
- **Steps:** Tap a person in the list.
- **Expected:** Connection request sent; "requesting…" state shown.

### TC-4.2.1 — Cancel withdraws pending request, returns Home (US-4.2) · Should · F
- **Steps:** With request pending, tap Cancel.
- **Expected:** Request withdrawn; return to Home.

### TC-4.3.1 — Decline shows "declined" + returns Home (US-4.3) · Must · F
- **Steps:** Receiver declines the pending request.
- **Expected:** Sender sees clear "declined" message and returns to Home.

### TC-4.4.1 — Unanswered request times out at 30 s (US-4.4) · Must · F
- **Steps:** Send request; leave unanswered 30 s.
- **Expected:** Request auto-cancels; sender told it timed out.

### TC-4.5.1 — Target out of range → "no longer reachable" (US-4.5) · Should · N
- **Steps:** Send request; target goes out of range before answering.
- **Expected:** Clear "no longer reachable" error, not a silent hang.

---

## 5. Connection Request — Receiving

### TC-5.1.1 — Accept/Decline prompt with requester nickname + avatar (US-5.1) · Must · F
- **Steps:** Someone requests a connection.
- **Expected:** Prompt shows requester nickname + avatar and Accept/Decline buttons.

### TC-5.2.1 — Accept → key exchange → encrypted chat (US-5.2) · Must · F
- **Steps:** Tap Accept.
- **Expected:** App performs key exchange and opens encrypted chat.

### TC-5.3.1 — Decline → no session + sender told (US-5.3) · Must · F
- **Steps:** Tap Decline.
- **Expected:** No session created; sender is told the request was declined.

### TC-5.4.1 — High-priority notification on backgrounded incoming request (US-5.4) · Must · F
- **Pre:** App backgrounded.
- **Steps:** Request arrives.
- **Expected:** High-priority notification appears.

### TC-5.4.2 — Tapping notification opens Accept/Decline (US-5.4) · Must · F
- **Steps:** Tap the notification.
- **Expected:** Accept/Decline screen opens.

### TC-5.5.1 — Incoming request auto-expires at 30 s + sender informed (US-5.5) · Must · F
- **Steps:** Do not respond for 30 s.
- **Expected:** Prompt auto-dismisses; sender informed.

### TC-5.6.1 — No incoming request when discoverability OFF (US-5.6) · Must · S
- **Pre:** Discoverability OFF.
- **Steps:** Peer attempts to reach.
- **Expected:** No incoming request ever reaches the user.

---

## 6. Secure Session Setup (Encryption)

### TC-6.1.1 — Auto RSA-2048 wrapping AES-256 before any message (US-6.1) · Must · S
- **Steps:** Accept a connection; observe session open.
- **Expected:** RSA-2048 exchange wraps an AES-256 session key before any message can be sent.

### TC-6.1.2 — User never handles/enters keys (US-6.1) · Must · S
- **Expected:** User is never asked to handle or enter keys.

### TC-6.2.1 — Input/send disabled until key exchange complete (US-6.2) · Must · S
- **Steps:** Observe chat during key exchange window.
- **Expected:** Message input/send disabled until complete, then enabled.

### TC-6.2.2 — No message transmitted before session key exists (US-6.2) · Must · S
- **Expected:** In any state, no message is transmitted before the session key exists.

### TC-6.3.1 — Encrypted/secure indicator once secured (US-6.3) · Should · U
- **Expected:** Chat shows a clear encrypted/secure indicator when the session is secured.

### TC-6.4.1 — Key-exchange failure aborts + returns Home (US-6.4) · Must · N
- **Steps:** Induce key-exchange failure.
- **Expected:** Session aborts, error shown, return to Home.

### TC-6.4.2 — No plaintext chat opened on failure (US-6.4) · Must · S
- **Expected:** No plaintext chat is ever opened on failure.

---

## 7. Chat — Text Messaging

### TC-7.1.1 — Send encrypted, delivered, shown as sent (US-7.1) · Must · F
- **Steps:** Send a message on secure session.
- **Expected:** Encrypted, delivered, and shown in own chat as sent.

### TC-7.2.1 — Received messages decrypted, correct order (US-7.2) · Must · F
- **Steps:** Peer sends messages.
- **Expected:** Decrypted and shown in correct order.

### TC-7.3.1 — Typing indicator appears while peer types (US-7.3) · Should · F
- **Steps:** Peer types.
- **Expected:** Typing indicator appears.

### TC-7.3.2 — Typing indicator disappears shortly after stop (US-7.3) · Should · F
- **Steps:** Peer stops typing.
- **Expected:** Indicator disappears shortly after.

### TC-7.4.1 — Cannot type past 200 characters (US-7.4) · Must · N
- **Steps:** Reach 200 characters.
- **Expected:** Cannot type more.

### TC-7.4.2 — Character counter visible near limit (US-7.4) · Must · U
- **Steps:** Approach the limit.
- **Expected:** A character counter is visible.

### TC-7.5.1 — Progress bar shows messages used out of 20 (US-7.5) · Should · U
- **Expected:** Progress bar shows messages used / 20.

### TC-7.5.2 — Bar color escalates as limit nears (US-7.5) · Should · U
- **Steps:** Raise the count.
- **Expected:** Bar color escalates approaching the limit.

### TC-7.6.1 — Shared count increments on send OR receive (US-7.6) · Must · F
- **Steps:** Alternate sending and receiving.
- **Expected:** Shared count increases by one for either direction.

### TC-7.7.1 — Input locks at 20 with "session limit reached" (US-7.7) · Must · F
- **Steps:** Reach a count of 20.
- **Expected:** Input disabled; clear "session limit reached" message shown.

### TC-7.8.1 — Sent vs received distinct + send-ordered (US-7.8) · Should · U
- **Expected:** Sent and received messages visually distinct and in send order.

---

## 8. File Picker & Sending

### TC-8.1.1 — Distinct Camera/Gallery/Document/Audio/Video options (US-8.1) · Must · U
- **Steps:** Open the attach picker.
- **Expected:** Distinct options for each source shown.

### TC-8.2.1 — Camera opens, captured photo queued (US-8.2) · Should · F
- **Steps:** Choose Camera, capture a photo.
- **Expected:** Camera opens; captured photo queued for transfer.

### TC-8.3.1 — Each picker opens and returns a selectable file (US-8.3) · Must · F
- **Steps:** Choose Gallery/Document/Audio/Video in turn.
- **Expected:** Correct picker opens and returns a selectable file.

### TC-8.4.1 — File > 25 MB rejected with message, no transfer (US-8.4) · Must · N
- **Steps:** Select a file larger than 25 MB.
- **Expected:** Rejected with a clear "too large (max 25 MB)" message; no transfer starts.

### TC-8.5.1 — Unsupported type rejected with message (US-8.5) · Must · N
- **Steps:** Select a type not image/doc/audio/video.
- **Expected:** Rejected with a clear message.

### TC-8.6.1 — Second transfer blocked while one in progress (US-8.6) · Must · N
- **Steps:** With a transfer in progress, try to start another.
- **Expected:** Prevented; told to wait.

---

## 9. File Transfer

### TC-9.1.1 — File transfers only over encrypted BT link (US-9.1) · Must · S
- **Steps:** Send a valid file on a secure session.
- **Expected:** Transfers only over the encrypted Bluetooth link (never internet/server).

### TC-9.2.1 — Send progress overlay updates in real time (US-9.2) · Must · U
- **Steps:** Send a file.
- **Expected:** Progress overlay updates in real time to completion.

### TC-9.3.1 — Receive progress indicator updates to completion (US-9.3) · Must · U
- **Steps:** Receive a file.
- **Expected:** Progress indicator updates until fully received.

### TC-9.4.1 — Device kept awake during transfer (US-9.4) · Must · P
- **Steps:** Send a large file; let screen-sleep timer elapse.
- **Expected:** Device stays awake for transfer duration.

### TC-9.4.2 — Wake lock released on completion OR cancel (US-9.4) · Must · P
- **Steps:** Complete a transfer, and separately cancel one.
- **Expected:** Wake lock released promptly in both cases.

### TC-9.5.1 — Interrupted transfer shows clear failure message (US-9.5) · Should · N
- **Steps:** Interrupt a transfer.
- **Expected:** Clear failure message (no silent disappearance).

### TC-9.6.1 — Correct name/type/size/sender, never "Unknown" (US-9.6) · Must · F
- **Steps:** Receive a file.
- **Expected:** Name, type, size, and real sender nickname shown correctly; never "Unknown".

---

## 10. File Viewing, Vanish & Saving

### TC-10.1.1 — Received file opens in view-once viewer (US-10.1) · Must · F
- **Steps:** Open a received file.
- **Expected:** Opens in a dedicated view-once viewer.

### TC-10.2.1 — Visible vanish countdown timer (US-10.2) · Must · U
- **Expected:** A vanish countdown timer is visibly shown for the received file.

### TC-10.3.1 — Countdown continues after viewer closed, expires on time (US-10.3) · Must · S
- **Steps:** Start countdown, close viewer.
- **Expected:** Countdown continues and still expires on time.

### TC-10.4.1 — Save copies to Downloads/BlueWhisper/Received/ (US-10.4) · Must · F
- **Steps:** In viewer, tap Save.
- **Expected:** File copied to public storage under `Downloads/BlueWhisper/Received/`.

### TC-10.4.2 — Saved file visible to system file manager/gallery (US-10.4) · Must · F
- **Steps:** Open system file manager / gallery.
- **Expected:** Saved file visible.

### TC-10.5.1 — Unsaved file securely deleted on expiry OR decline (US-10.5) · Must · S
- **Steps:** Let countdown expire (and separately, decline) without saving.
- **Expected:** File securely deleted (overwritten, then removed).

### TC-10.6.1 — Decline unopened file → securely deleted, never saved (US-10.6) · Should · F
- **Steps:** Decline an unopened received file.
- **Expected:** Securely deleted without ever being saved.

### TC-10.7.1 — Correct sender nickname on received/saved file, never placeholder (US-10.7) · Must · F
- **Expected:** Any received or saved file shows the correct sender nickname, never a placeholder.

---

## 11. Session Wipe & Privacy (Core Promise)

### TC-11.1.1 — Disconnect clears messages/file states/temp + zeros keys (US-11.1) · Must · S
- **Steps:** Active session → disconnect.
- **Expected:** All messages, file states, temp files cleared; encryption keys zeroed.

### TC-11.2.1 — Turning BT off tears down + wipes session (US-11.2) · Must · S
- **Steps:** Active session → turn Bluetooth off.
- **Expected:** Session tears down; all session data wiped.

### TC-11.3.1 — App swipe from recents wipes as process ends (US-11.3) · Must · S
- **Steps:** Active session → swipe app from recents.
- **Expected:** Session data wiped as the process ends.

### TC-11.4.1 — Every wipe trigger deletes all temp files (US-11.4) · Must · S
- **Steps:** Trigger wipe via disconnect, BT off, and app swipe.
- **Expected:** All temporary files deleted on each path.

### TC-11.5.1 — Session keys overwritten with zeros on any wipe (US-11.5) · Must · S
- **Steps:** Trigger any wipe.
- **Expected:** Session keys overwritten with zeros in memory.

### TC-11.6.1 — ~2.5 s Disconnect screen then auto-Home (US-11.6) · Should · U
- **Steps:** End a session.
- **Expected:** ~2.5 s Disconnect screen shown, then auto-navigate to Home.

### TC-11.7.1 — End/leave action wipes + shows Disconnect screen (US-11.7) · Must · F
- **Steps:** In active session, tap end/leave.
- **Expected:** Session ends, data wiped, Disconnect screen shown.

### TC-11.8.1 — Peer disconnect/leave → clear message + wipe (US-11.8) · Must · F
- **Steps:** Peer disconnects or leaves range.
- **Expected:** Clear message shown; session ends with a wipe.

---

## 12. Background Service & Notifications

### TC-12.1.1 — Foreground service keeps scan/advertise backgrounded (US-12.1) · Must · F
- **Pre:** Discoverability on, app backgrounded.
- **Expected:** Foreground service keeps scanning/advertising alive.

### TC-12.2.1 — Persistent low-priority scanning notification (US-12.2) · Should · U
- **Steps:** Start background scanning.
- **Expected:** Persistent low-priority notification shown.

### TC-12.3.1 — High-priority notification on backgrounded incoming request (US-12.3) · Must · F
- **Pre:** App backgrounded.
- **Steps:** Request arrives.
- **Expected:** High-priority notification raised.

### TC-12.4.1 — Tap notification while running → Accept/Decline (US-12.4) · Must · F
- **Steps:** With app running, tap the request notification.
- **Expected:** Accept/Decline screen opens.

### TC-12.4.2 — Cold-start deep-link not lost (US-12.4) · Must · F
- **Pre:** App fully closed.
- **Steps:** Tap the request notification.
- **Expected:** App launches and still lands on Accept/Decline (tap isn't lost).

### TC-12.5.1 — Turning discoverability/scanning off stops bg activity (US-12.5) · Should · F
- **Steps:** With background activity running, turn discoverability/scanning off.
- **Expected:** Background service stops the corresponding activity.

---

## 13. Saved Files Management

### TC-13.1.1 — Saved files grouped by date with sender + metadata (US-13.1) · Must · U
- **Steps:** Open Saved Files with several items.
- **Expected:** Grouped by date; each shows sender and basic metadata.

### TC-13.2.1 — Open launches system viewer (US-13.2) · Must · F
- **Steps:** Saved file menu → Open.
- **Expected:** Opens in the appropriate system viewer.

### TC-13.3.1 — Share opens system share sheet with file (US-13.3) · Must · F
- **Steps:** Saved file menu → Share.
- **Expected:** System share sheet opens with the file.

### TC-13.4.1 — Delete removes file from list (US-13.4) · Should · F
- **Steps:** Delete a saved file.
- **Expected:** Disappears from the list.

### TC-13.5.1 — Total storage used displayed in Settings (US-13.5) · Should · U
- **Steps:** Open Settings.
- **Expected:** Total storage used by saved files displayed.

### TC-13.6.1 — Empty saved-files state (US-13.6) · Should · U
- **Pre:** No saved files.
- **Expected:** Explicit empty state shown.

---

## 14. Settings & Profile Management

### TC-14.1.1 — Edit nickname enforces 1–15 + no-`|`, persists (US-14.1) · Should · F/N
- **Steps:** Edit nickname in Settings; test empty, >15, and `|`.
- **Expected:** Same 1–15 char and no-`|` rules enforced; valid change persists.

### TC-14.2.1 — Change avatar saved + used going forward (US-14.2) · Should · F
- **Steps:** Pick a new avatar in Settings.
- **Expected:** Saved and used going forward.

### TC-14.3.1 — Switch language updates app text (US-14.3) · Must · F
- **Steps:** Switch language in Settings.
- **Expected:** App text updates to the chosen language.

### TC-14.4.1 — Settings states offline/no-account/wipe + storage usage (US-14.4) · Could · U
- **Steps:** Open Settings.
- **Expected:** Clearly states offline / no-account / wipe-on-disconnect behavior and shows storage usage.

---

## 15. Reliability, Errors & Edge Cases

### TC-15.1.1 — BT off wipes session; BT on resumes discovery (US-15.1) · Must · N
- **Steps:** Turn BT off during use, then back on.
- **Expected:** Any session wiped on off; discovery resumes cleanly on on.

### TC-15.2.1 — Reopen after kill → valid state, prior data wiped (US-15.2) · Must · S
- **Steps:** Kill the app, reopen.
- **Expected:** Opens to a valid state; any prior session data already wiped.

### TC-15.3.1 — Scanning already running on return after disconnect (US-15.3) · Must · F
- **Steps:** End a session, return to Home.
- **Expected:** Scanning already running again.

### TC-15.4.1 — Abrupt peer drop ends cleanly with message + wipe (US-15.4) · Must · N
- **Steps:** Peer disconnects abruptly during chat or transfer.
- **Expected:** Session ends cleanly with a message and a wipe; no dead screen.

### TC-15.5.1 — Failed connection → clear error + retry path (US-15.5) · Should · N
- **Steps:** Induce a connection failure.
- **Expected:** Clear error and retry path (not a silent hang).

### TC-15.6.1 — Simultaneous mutual requests → one clean 1:1 session (US-15.6) · Should · N
- **Steps:** Two users request each other at the same time.
- **Expected:** Exactly one 1:1 session established; no duplicate or stuck states.

### TC-15.7.1 — No third device can join; exactly one peer (US-15.7) · Must · S
- **Steps:** With A–B in session, device C attempts to join.
- **Expected:** C cannot join; app supports exactly one peer at a time.

### TC-15.8.1 — Interrupted transfer removes partials + reports failure (US-15.8) · Should · N
- **Steps:** Interrupt a transfer.
- **Expected:** Partial files removed; failure reported.

### TC-15.9.1 — Insufficient storage on Save → clear error, no partial write (US-15.9) · Should · N
- **Pre:** Device storage insufficient for the file.
- **Steps:** Tap Save.
- **Expected:** Failure reported clearly; file not partially written.

### TC-15.10.1 — Missing camera/storage access at pick → request/explain, no crash (US-15.10) · Should · N
- **Pre:** Needed camera/storage access missing.
- **Steps:** Choose a source in the picker.
- **Expected:** App requests it or explains how to enable it; does not crash.

---

## 16. Performance & Usability

### TC-16.1.1 — Cold start under ~2 s on mid-range device (US-16.1) · Should · P
- **Steps:** Cold-launch on a mid-range device; measure to first usable screen.
- **Expected:** First usable screen in under ~2 s.

### TC-16.1.2 — UI never freezes on main thread at startup (US-16.1) · Should · P
- **Expected:** UI never freezes on the main thread during startup.

### TC-16.2.1 — Smooth scrolling/animations, no visible stutter (US-16.2) · Should · P
- **Steps:** Scroll lists and run transitions during normal use.
- **Expected:** Stays smooth without visible stutter.

### TC-16.3.1 — Every string on every screen in chosen language (US-16.3) · Should · U
- **Steps:** Choose a language, walk every screen.
- **Expected:** Every user-facing string appears in the chosen language (no fallback).

### TC-16.4.1 — TalkBack announces meaningful labels (US-16.4) · Should · A
- **Steps:** Enable TalkBack, traverse buttons/toggles/list items.
- **Expected:** Meaningful labels announced.

### TC-16.4.2 — Core flows fully operable via TalkBack (US-16.4) · Should · A
- **Steps:** With TalkBack on, run onboarding, connect, chat, save.
- **Expected:** Core flows fully operable by voice/gestures.

---

## 17. Future Phases (Out of Scope for Phase 1 — regression guards only)

> These stories are **Future** priority and not implemented in Phase 1. No functional test cases are executed now; the tests below are **guards** to confirm Phase-1 guarantees are not weakened, per each story's AC.

### TC-17.1.1 — Wipe-on-disconnect remains the default (US-17.1) · Future · S
- **Expected:** Any future multi-message history must be explicit opt-in and must not weaken the Phase-1 wipe-on-disconnect default. *(Phase-1 check: no retention path exists by default.)*

### TC-17.2.1 — No group sessions in Phase 1 (US-17.2) · Future · S
- **Expected:** Group/multi-peer not available; if added later, must preserve E2E encryption and clear privacy semantics. *(Ties to TC-15.7.1.)*

### TC-17.3.1 — Android-only in Phase 1 (US-17.3) · Future · C
- **Expected:** No cross-platform (iOS) support in Phase 1; future support must keep offline/encryption/wipe guarantees.

### TC-17.4.1 — 25 MB cap + no resume in Phase 1 (US-17.4) · Future · N
- **Expected:** Files >25 MB rejected and transfers do not resume in Phase 1. *(Ties to TC-8.4.1, TC-15.8.1.)*

### TC-17.5.1 — No stored contacts/trust in Phase 1 (US-17.5) · Future · S
- **Expected:** No contacts/trusted-devices persistence; future trust must be local-only, revocable, consistent with no-account principle.

---

## Coverage Summary
- **User stories covered:** all of Sections 1–16 (US-1.1 → US-16.4), plus Section 17 (US-17.1–17.5) as regression guards.
- **Priorities reflect the doc:** Must / Should / Could / Future (not invented severities).
- **Test cases:** ~130, each split per Given/When/Then AC and citing its US.
- **Two-device tests:** Sections 4–11, 15 (Device A + Device B).
- **Core-promise (security) emphasis:** Sections 6, 9, 10, 11, plus US-1.6, US-3.4/5.6 (invisibility), US-15.7 (strictly 1:1).

*Derived exclusively from `USER_STORIES.md`. No source code was inspected.*
