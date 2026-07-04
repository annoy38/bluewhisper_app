# BlueWhisper — User Stories (Client Requirements)

> **What this document is:** My requirements for BlueWhisper, written as user stories from the client's point of view.
> **What BlueWhisper is:** An **offline, Bluetooth-based, 1:1 end-to-end encrypted messenger** for Android. No internet, no server, no account. Two nearby phones connect directly, exchange encrypted text and files, and leave **no trace** once they disconnect.
>
> **How to read each story**
> - **ID & Title** — a stable reference.
> - **Story** — `As a <persona>, I want <capability>, so that <benefit>`.
> - **Priority** — `Must` (ship-blocking) · `Should` (important) · `Could` (nice-to-have) · `Future` (later phase).
> - **Acceptance Criteria** — written as **Given / When / Then** so each is unambiguous and testable. If any Then fails, the story is not done.

---

## Personas (the people in these stories)

| Persona | Who they are |
|---|---|
| **New User** | Opening the app for the very first time. |
| **Returning User** | Already onboarded; opens straight to Home. |
| **Sender** | The person who starts a connection or sends content. |
| **Receiver** | The person who receives a request or content. |
| **Session Peer** | Either party inside an active 1:1 session (most chat/file stories apply to both). |
| **Bangla-First User** | Wants the whole app in Bangla. |
| **Battery-Conscious User** | Wants minimal power drain while idle or scanning. |
| **Accessibility User** | Relies on TalkBack / screen reader / large text. |
| **Privacy-Focused User** | The core persona — wants zero data residue and full control. |

---

# 1. Install, Permissions & Prerequisites

### US-1.1 — Request Bluetooth permission with a reason
**Story:** As a New User, I want the app to ask for Bluetooth permission and explain why, so that I understand what it's for before I grant it.
**Priority:** Must
**Acceptance Criteria**
- **Given** I have never granted Bluetooth permission, **when** I first try to discover people, **then** the app shows a plain-language reason ("to find nearby people") before the system permission dialog.
- **Given** the rationale is shown, **when** I tap Continue, **then** the Android permission dialog appears.
- **Given** I grant the permission, **when** the dialog closes, **then** discovery starts immediately.
- **Given** I deny the permission, **when** the dialog closes, **then** I see a clear message explaining discovery is blocked and a button to try again.

### US-1.2 — Request nearby-devices / location permission
**Story:** As a New User, I want to be asked for the nearby-devices (or location) permission my Android version needs, so that scanning can work.
**Priority:** Must
**Acceptance Criteria**
- **Given** my Android version requires it, **when** discovery starts, **then** the app requests the correct permission for that version.
- **Given** the request is shown, **then** the rationale states the permission is used only for nearby discovery and never for location tracking.
- **Given** I deny it, **then** discovery is disabled with a clear explanation and a retry option.

### US-1.3 — Request notification permission
**Story:** As a User, I want to be asked to allow notifications, so that I can be alerted to incoming connection requests when the app is in the background.
**Priority:** Must
**Acceptance Criteria**
- **Given** my Android version requires notification permission, **when** I first need background alerts, **then** the app requests it.
- **Given** I grant it, **then** I can receive incoming-request alerts while the app is backgrounded.
- **Given** I deny it, **then** the app tells me background alerts are off and how to enable them later.

### US-1.4 — Prompt to turn on Bluetooth
**Story:** As a User, I want the app to prompt me to enable Bluetooth when it's off, so that I don't have to dig through system settings.
**Priority:** Must
**Acceptance Criteria**
- **Given** Bluetooth is off, **when** I open Home, **then** the app shows a clear prompt with a one-tap way to enable Bluetooth.
- **Given** Bluetooth is off, **when** I try to discover, **then** the app explains the blocker instead of failing silently.
- **Given** I enable Bluetooth, **when** I return to the app, **then** discovery starts automatically.

### US-1.5 — Recover from a permanently denied permission
**Story:** As a User who denied a permission, I want clear guidance on what won't work and how to re-enable it, so that I'm never stuck on a broken screen.
**Priority:** Should
**Acceptance Criteria**
- **Given** I selected "Don't ask again," **when** I try the blocked feature, **then** the app shows which feature is unavailable and a button that opens the app's system settings page.
- **Given** I re-enable the permission in settings and return, **then** the feature works without restarting the app.

### US-1.6 — Work fully offline
**Story:** As a Privacy-Focused User, I want the app to work with Wi-Fi and mobile data off, so that I'm certain nothing leaves my device over the network.
**Priority:** Must
**Acceptance Criteria**
- **Given** Wi-Fi and mobile data are both off, **when** I use any feature (onboarding, discovery, chat, file transfer, saving), **then** every feature works normally.
- **Given** the app is running, **then** it never shows a network/connectivity error and never requires internet.

---

# 2. Onboarding & Profile Creation

### US-2.1 — Enter a valid nickname
**Story:** As a New User, I want to enter a nickname of 1–15 characters, so that others recognize me by a name I chose.
**Priority:** Must
**Acceptance Criteria**
- **Given** the nickname field is empty, **then** the Continue button is disabled.
- **Given** I type more than 15 characters, **then** input stops at 15 (or shows an error and blocks continuing).
- **Given** I type the `|` character, **then** it is rejected with the message that `|` is not allowed.
- **Given** a valid 1–15 character nickname with no `|`, **when** I tap Continue, **then** the app accepts it and moves to the next step.

### US-2.2 — Select an avatar
**Story:** As a New User, I want to pick an avatar from a fixed set, so that I have a visual identity in others' device lists.
**Priority:** Must
**Acceptance Criteria**
- **Given** the avatar step, **then** a fixed library of avatars is shown.
- **Given** no avatar is selected, **then** I cannot continue.
- **Given** I tap an avatar, **then** it is visibly highlighted as selected and I can continue.

### US-2.3 — Choose language during onboarding
**Story:** As a Bangla-First User, I want to choose English or Bangla during onboarding, so that I can use the app in my language from the start.
**Priority:** Must
**Acceptance Criteria**
- **Given** the language step, **then** both English and Bangla are offered.
- **Given** I choose Bangla, **when** I confirm, **then** the app's text appears in Bangla from that point on.

### US-2.4 — Save profile locally with no account
**Story:** As a Privacy-Focused User, I want my profile stored on-device with no signup, so that my identity never leaves my phone.
**Priority:** Must
**Acceptance Criteria**
- **Given** onboarding, **then** the app never asks for email, phone number, password, or any login.
- **Given** I complete onboarding, **when** I close and reopen the app, **then** my nickname, avatar, and language are remembered.

### US-2.5 — Skip onboarding on return
**Story:** As a Returning User, I want to bypass onboarding after first setup, so that I go straight to Home.
**Priority:** Must
**Acceptance Criteria**
- **Given** I have completed onboarding before, **when** I open the app, **then** I land on Home without seeing onboarding again.
- **Given** app data is cleared, **when** I open the app, **then** onboarding is shown again.

### US-2.6 — See a splash on launch
**Story:** As a User, I want a brief splash on launch, so that the app feels intentional and doesn't flash a blank screen.
**Priority:** Should
**Acceptance Criteria**
- **Given** I launch the app, **then** a splash shows while it prepares and dismisses when the first screen is ready.
- **Given** the app is starting, **then** the UI never freezes or shows a blank white screen.

---

# 3. Home & Discovery

### US-3.1 — See nearby BlueWhisper users
**Story:** As a Sender, I want the app to scan for nearby users while I'm on Home, so that I can find someone to connect with.
**Priority:** Must
**Acceptance Criteria**
- **Given** I am on Home with Bluetooth and permissions granted, **then** scanning runs automatically.
- **Given** another BlueWhisper user is nearby and discoverable, **then** they appear in my list with their nickname and avatar.

### US-3.2 — See a searching / empty state
**Story:** As a User, I want a clear "searching / no one nearby" state, so that I know the app is working even when the list is empty.
**Priority:** Should
**Acceptance Criteria**
- **Given** no devices are found yet, **then** the list shows an explicit "searching for people nearby" or "no one nearby" state.
- **Given** scanning is active, **then** a visible indicator confirms it (not a blank screen).

### US-3.3 — Remove devices that left range
**Story:** As a User, I want devices that left range to disappear, so that I never try to reach someone who is gone.
**Priority:** Must
**Acceptance Criteria**
- **Given** a device stops advertising, **when** ~10 seconds pass with no new signal, **then** it is removed from my list.
- **Given** a device reappears, **then** it is shown again.

### US-3.4 — Toggle my discoverability
**Story:** As a Privacy-Focused User, I want a discoverable on/off toggle, so that I control when others can see and contact me.
**Priority:** Must
**Acceptance Criteria**
- **Given** discoverability is ON, **then** other users can see me and send me requests.
- **Given** I switch it OFF, **then** I disappear from others' lists and receive no requests.
- **Given** either state, **then** the toggle clearly shows the current state.

### US-3.5 — See a proximity / signal hint
**Story:** As a User in a crowded space, I want a proximity indicator per device, so that I can tell who is closest.
**Priority:** Could
**Acceptance Criteria**
- **Given** the device list, **then** each entry shows a relative proximity/recency hint.
- *Client note: exact distance isn't required; a relative "closer / farther / just seen" hint is enough.*

### US-3.6 — Keep discovery updating
**Story:** As a User, I want discovery to keep updating automatically, so that newly-arrived people appear without restarting the app.
**Priority:** Must
**Acceptance Criteria**
- **Given** I stay on Home, **then** the list continuously adds arriving devices and removes departed ones.
- **Given** a scan error occurs, **then** the app recovers automatically or offers a retry.

### US-3.7 — Conserve battery while scanning
**Story:** As a Battery-Conscious User, I want scanning to run efficiently, so that leaving Home open doesn't drain my battery.
**Priority:** Should
**Acceptance Criteria**
- **Given** scanning is active, **then** it alternates active and pause periods rather than running continuously.

### US-3.8 — Navigate to Settings and Saved Files
**Story:** As a User, I want to reach Settings and Saved Files from Home, so that I can manage my profile and kept files.
**Priority:** Must
**Acceptance Criteria**
- **Given** I am on Home, **then** there are clear entry points to Settings and Saved Files.
- **Given** I tap either, **then** the corresponding screen opens.

---

# 4. Connection Request — Sending

### US-4.1 — Send a connection request with one tap
**Story:** As a Sender, I want to tap a nearby person to request a connection, so that starting a session is effortless.
**Priority:** Must
**Acceptance Criteria**
- **Given** a person is in my list, **when** I tap them, **then** a connection request is sent and I see a "requesting…" state.

### US-4.2 — Cancel my pending request
**Story:** As a Sender, I want to cancel a request I sent, so that I'm not stuck waiting if I change my mind.
**Priority:** Should
**Acceptance Criteria**
- **Given** my request is pending, **when** I tap Cancel, **then** the request is withdrawn and I return to Home.

### US-4.3 — Be told when my request is declined
**Story:** As a Sender, I want clear feedback if the other person declines, so that I know the outcome and can move on.
**Priority:** Must
**Acceptance Criteria**
- **Given** my request is pending, **when** the receiver declines, **then** I see a clear "declined" message and return to Home.

### US-4.4 — Have my request time out at 30s
**Story:** As a Sender, I want an unanswered request to time out after 30 seconds, so that I'm not left waiting indefinitely.
**Priority:** Must
**Acceptance Criteria**
- **Given** my request is pending and unanswered, **when** 30 seconds pass, **then** the request auto-cancels and I'm told it timed out.

### US-4.5 — Be told if the target left range
**Story:** As a Sender, I want to know if the person disappeared before connecting, so that I understand why it failed.
**Priority:** Should
**Acceptance Criteria**
- **Given** my request is pending, **when** the target goes out of range, **then** I get a clear "no longer reachable" error instead of a silent hang.

---

# 5. Connection Request — Receiving

### US-5.1 — See an Accept / Decline prompt
**Story:** As a Receiver, I want an Accept/Decline prompt identifying the requester, so that I control who I talk to.
**Priority:** Must
**Acceptance Criteria**
- **Given** someone requests a connection, **then** I see a prompt with their nickname and avatar plus Accept and Decline buttons.

### US-5.2 — Accept and enter the session
**Story:** As a Receiver, I want to accept a request, so that the secure session opens.
**Priority:** Must
**Acceptance Criteria**
- **Given** the Accept/Decline prompt, **when** I tap Accept, **then** the app performs key exchange and opens the encrypted chat.

### US-5.3 — Decline a request
**Story:** As a Receiver, I want to decline a request, so that unwanted contact is rejected.
**Priority:** Must
**Acceptance Criteria**
- **Given** the prompt, **when** I tap Decline, **then** no session is created and the sender is told I declined.

### US-5.4 — Be notified in the background
**Story:** As a Receiver, I want a notification when a request arrives while the app is backgrounded, so that I don't miss it.
**Priority:** Must
**Acceptance Criteria**
- **Given** the app is backgrounded, **when** a request arrives, **then** a high-priority notification appears.
- **Given** the notification, **when** I tap it, **then** the Accept/Decline screen opens.

### US-5.5 — Have incoming requests time out
**Story:** As a Receiver, I want an unanswered incoming request to auto-expire after 30 seconds, so that stale prompts don't linger.
**Priority:** Must
**Acceptance Criteria**
- **Given** an incoming request prompt, **when** 30 seconds pass without me responding, **then** it auto-dismisses and the sender is informed.

### US-5.6 — Receive nothing when not discoverable
**Story:** As a Privacy-Focused User with discoverability off, I want no connection requests, so that I'm truly invisible.
**Priority:** Must
**Acceptance Criteria**
- **Given** my discoverability is OFF, **then** no incoming request ever reaches me.

---

# 6. Secure Session Setup (Encryption)

### US-6.1 — Have encryption set up automatically
**Story:** As a Session Peer, I want keys exchanged automatically before the first message, so that security is handled for me.
**Priority:** Must
**Acceptance Criteria**
- **Given** a connection is accepted, **when** the session opens, **then** an RSA-2048 exchange wraps an AES-256 session key before any message can be sent.
- **Given** the process, **then** I am never asked to handle or enter keys.

### US-6.2 — Be blocked from sending until secure
**Story:** As a Privacy-Focused User, I want sending disabled until encryption is ready, so that plaintext can never leak.
**Priority:** Must
**Acceptance Criteria**
- **Given** key exchange is not yet complete, **then** the message input/send is disabled.
- **Given** key exchange completes, **then** input is enabled.
- **Given** any state, **then** no message is ever transmitted before the session key exists.

### US-6.3 — Know the connection is encrypted
**Story:** As a Session Peer, I want a visible "encrypted" indicator, so that I trust the chat is private.
**Priority:** Should
**Acceptance Criteria**
- **Given** the session is secured, **then** the chat shows a clear encrypted/secure indicator.

### US-6.4 — Fail safely if key exchange fails
**Story:** As a Session Peer, I want a clear error and clean exit if key exchange fails, so that I never end up in an insecure or stuck session.
**Priority:** Must
**Acceptance Criteria**
- **Given** key exchange fails, **then** the session aborts, I see an error, and I return to Home.
- **Given** a failure, **then** no plaintext chat is ever opened.

---

# 7. Chat — Text Messaging

### US-7.1 — Send an encrypted text message
**Story:** As a Session Peer, I want to send encrypted text, so that we can converse privately.
**Priority:** Must
**Acceptance Criteria**
- **Given** the secure session is ready, **when** I send a message, **then** it is encrypted, delivered, and shown in my chat as sent.

### US-7.2 — Receive a text message
**Story:** As a Session Peer, I want to receive my peer's messages, so that I can read what they send.
**Priority:** Must
**Acceptance Criteria**
- **Given** my peer sends a message, **then** it is decrypted and shown in the correct order in my chat.

### US-7.3 — See a typing indicator
**Story:** As a Session Peer, I want to see when my peer is typing, so that the chat feels live.
**Priority:** Should
**Acceptance Criteria**
- **Given** my peer is typing, **then** a typing indicator appears.
- **Given** my peer stops typing, **then** the indicator disappears shortly after.

### US-7.4 — Be limited to 200 characters per message
**Story:** As a Session Peer, I want a 200-character cap per message, so that messages stay short.
**Priority:** Must
**Acceptance Criteria**
- **Given** the input, **when** I reach 200 characters, **then** I cannot type more.
- **Given** I approach the limit, **then** a character counter is visible.

### US-7.5 — See my progress toward the message limit
**Story:** As a Session Peer, I want a visual progress bar of messages used, so that I can pace the conversation.
**Priority:** Should
**Acceptance Criteria**
- **Given** an active session, **then** a progress bar shows messages used out of 20.
- **Given** the count rises, **then** the bar's color escalates as the limit nears.

### US-7.6 — Have both directions count toward 20
**Story:** As a Session Peer, I want both sent and received messages counted toward the 20-message cap, so that the limit reflects the whole conversation.
**Priority:** Must
**Acceptance Criteria**
- **Given** a session, **when** either I send or my peer sends, **then** the shared count increases by one.

### US-7.7 — Be stopped at the 20-message cap
**Story:** As a Privacy-Focused User, I want the session to lock at 20 messages, so that sessions stay brief and ephemeral.
**Priority:** Must
**Acceptance Criteria**
- **Given** the count reaches 20, **then** the input is disabled and a clear "session limit reached" message is shown.

### US-7.8 — Distinguish my messages from my peer's
**Story:** As a Session Peer, I want my messages and my peer's clearly distinguished and time-ordered, so that the conversation is easy to follow.
**Priority:** Should
**Acceptance Criteria**
- **Given** messages exist, **then** sent and received messages are visually distinct and displayed in send order.

---

# 8. File Picker & Sending

### US-8.1 — Choose what kind of file to send
**Story:** As a Sender, I want clear source options (Camera, Gallery, Document, Audio, Video), so that I can attach exactly what I want in one tap.
**Priority:** Must
**Acceptance Criteria**
- **Given** I open the attach picker, **then** distinct options for Camera, Gallery, Document, Audio, and Video are shown.

### US-8.2 — Capture a photo to send
**Story:** As a Sender, I want to take a photo from the camera, so that I can share something in the moment.
**Priority:** Should
**Acceptance Criteria**
- **Given** I choose Camera, **then** the camera opens; **when** I capture a photo, **then** it is queued for transfer.

### US-8.3 — Pick an existing file
**Story:** As a Sender, I want to pick an existing image, document, audio, or video, so that I can send files already on my phone.
**Priority:** Must
**Acceptance Criteria**
- **Given** I choose Gallery/Document/Audio/Video, **then** the correct picker opens and returns a selectable file.

### US-8.4 — Be stopped from sending oversized files
**Story:** As a Sender, I want files over 25 MB rejected before transfer, so that I don't start a transfer that can't complete.
**Priority:** Must
**Acceptance Criteria**
- **Given** I select a file larger than 25 MB, **then** it is rejected with a clear "too large (max 25 MB)" message and no transfer starts.

### US-8.5 — Be stopped from sending unsupported types
**Story:** As a Sender, I want unsupported file types rejected, so that only valid content is sent.
**Priority:** Must
**Acceptance Criteria**
- **Given** I select a file whose type is not allowed (not image/doc/audio/video), **then** it is rejected with a clear message.

### US-8.6 — Send only one file at a time
**Story:** As a Sender, I want one transfer at a time, so that transfers stay reliable.
**Priority:** Must
**Acceptance Criteria**
- **Given** a transfer is in progress, **when** I try to start another, **then** the app prevents it and tells me to wait.

---

# 9. File Transfer

### US-9.1 — Send a file directly over Bluetooth
**Story:** As a Sender, I want files sent peer-to-peer, so that they never touch the internet or a server.
**Priority:** Must
**Acceptance Criteria**
- **Given** a valid file and a secure session, **when** I send it, **then** it transfers only over the encrypted Bluetooth link.

### US-9.2 — Watch send progress
**Story:** As a Sender, I want a live progress overlay while sending, so that I know it's working and how far along it is.
**Priority:** Must
**Acceptance Criteria**
- **Given** a transfer is underway, **then** a progress overlay updates in real time until completion.

### US-9.3 — Watch receive progress
**Story:** As a Receiver, I want a live progress indicator while receiving, so that I know a file is arriving.
**Priority:** Must
**Acceptance Criteria**
- **Given** a file is incoming, **then** a progress indicator updates until the file is fully received.

### US-9.4 — Not lose a transfer to screen sleep
**Story:** As a Sender of a large file, I want the device kept awake during transfer, so that it completes even if the screen would sleep.
**Priority:** Must
**Acceptance Criteria**
- **Given** a transfer starts, **then** the device is kept awake for its duration.
- **Given** the transfer completes or is cancelled, **then** the wake lock is released promptly.

### US-9.5 — Know if a transfer fails
**Story:** As a Session Peer, I want clear feedback if a transfer fails or is interrupted, so that I can retry or move on.
**Priority:** Should
**Acceptance Criteria**
- **Given** a transfer is interrupted, **then** I see a clear failure message (not a silent disappearance).

### US-9.6 — See correct file metadata
**Story:** As a Receiver, I want the file's name, type, size, and sender shown correctly, so that I know what I'm receiving and from whom.
**Priority:** Must
**Acceptance Criteria**
- **Given** a file is received, **then** its name, type, size, and the real sender nickname are shown correctly (never "Unknown").

---

# 10. File Viewing, Vanish & Saving

### US-10.1 — Open a received file view-once
**Story:** As a Receiver, I want received files to open in a view-once viewer, so that shared content isn't casually re-opened.
**Priority:** Must
**Acceptance Criteria**
- **Given** I received a file, **when** I open it, **then** it opens in a dedicated view-once viewer.

### US-10.2 — See a vanish countdown
**Story:** As a Receiver, I want a visible countdown for the file, so that I know how long I have before it's destroyed.
**Priority:** Must
**Acceptance Criteria**
- **Given** a received file, **then** a vanish countdown timer is visibly shown.

### US-10.3 — Trust the countdown even if I close the viewer
**Story:** As a Sender, I want the countdown to keep running if the receiver closes the viewer, so that files can't be preserved by backing out.
**Priority:** Must
**Acceptance Criteria**
- **Given** a countdown is running, **when** the receiver closes the viewer, **then** the countdown continues and still expires on time.

### US-10.4 — Save a file I want to keep
**Story:** As a Receiver, I want a Save action that copies the file to public phone storage, so that I keep only what I consciously choose.
**Priority:** Must
**Acceptance Criteria**
- **Given** the viewer, **when** I tap Save, **then** the file is copied to public storage under `Downloads/BlueWhisper/Received/`.
- **Given** I saved it, **then** it is visible to the system file manager/gallery.

### US-10.5 — Have unsaved files securely destroyed
**Story:** As a Privacy-Focused User, I want files securely deleted on expiry or decline, so that nothing lingers that I didn't keep.
**Priority:** Must
**Acceptance Criteria**
- **Given** a file I did not save, **when** the countdown expires **or** I decline it, **then** the file is securely deleted (overwritten, then removed).

### US-10.6 — Decline a file before viewing
**Story:** As a Receiver, I want to decline a received file without opening it, so that I can reject content I don't want.
**Priority:** Should
**Acceptance Criteria**
- **Given** an unopened received file, **when** I decline, **then** it is securely deleted without ever being saved.

### US-10.7 — See who sent a file
**Story:** As a Receiver, I want each received/saved file attributed to the real sender, so that I know its origin.
**Priority:** Must
**Acceptance Criteria**
- **Given** any received or saved file, **then** the correct sender nickname is shown, never a placeholder.

---

# 11. Session Wipe & Privacy (Core Promise)

### US-11.1 — Leave no trace on disconnect
**Story:** As a Privacy-Focused User, I want all session data wiped the moment we disconnect, so that nothing outlives the meeting.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **when** it disconnects, **then** all messages, file states, and temp files are cleared and encryption keys are zeroed.

### US-11.2 — Wipe when Bluetooth is turned off
**Story:** As a Privacy-Focused User, I want a wipe triggered if Bluetooth is turned off, so that ending via BT still protects my data.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **when** I turn Bluetooth off, **then** the session tears down and all session data is wiped.

### US-11.3 — Wipe when I swipe the app away
**Story:** As a Privacy-Focused User, I want a wipe on app swipe/process kill, so that closing the app leaves no residue.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **when** I swipe the app from recents, **then** session data is wiped as the process ends.

### US-11.4 — Delete all temp files on any wipe
**Story:** As a Privacy-Focused User, I want all temporary files deleted on every wipe, so that no cached content remains.
**Priority:** Must
**Acceptance Criteria**
- **Given** any wipe trigger (disconnect / BT off / app swipe), **then** all temporary files are deleted.

### US-11.5 — Zero encryption keys from memory
**Story:** As a Privacy-Focused User, I want encryption keys erased from RAM on wipe, so that they can't be recovered.
**Priority:** Must
**Acceptance Criteria**
- **Given** any wipe, **then** the session keys are overwritten with zeros in memory.

### US-11.6 — See a disconnect confirmation screen
**Story:** As a User, I want a brief Disconnect screen before returning Home, so that I have clear confirmation the session ended.
**Priority:** Should
**Acceptance Criteria**
- **Given** a session ends, **then** a ~2.5-second Disconnect screen is shown, **then** the app auto-navigates to Home.

### US-11.7 — Leave a session deliberately
**Story:** As a Session Peer, I want to end the session myself at any time, so that I'm always in control of when it ends.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **when** I tap the end/leave action, **then** the session ends, data is wiped, and the Disconnect screen shows.

### US-11.8 — Be told when the peer disconnects
**Story:** As a Session Peer, I want clear notice if my peer leaves or goes out of range, so that I understand why the session ended.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **when** my peer disconnects or leaves range, **then** I see a clear message and the session ends with a wipe.

---

# 12. Background Service & Notifications

### US-12.1 — Stay reachable in the background
**Story:** As a Receiver, I want a foreground service to keep Bluetooth working when the app is backgrounded, so that I can still get requests without keeping the app on-screen.
**Priority:** Must
**Acceptance Criteria**
- **Given** discoverability is on and the app is backgrounded, **then** a foreground service keeps scanning/advertising alive.

### US-12.2 — See a persistent scanning notification
**Story:** As a User, I want a low-priority persistent notification while scanning, so that I know the app is active in the background.
**Priority:** Should
**Acceptance Criteria**
- **Given** background scanning is active, **then** a persistent low-priority notification is shown.

### US-12.3 — Get alerted to incoming requests
**Story:** As a Receiver, I want a high-priority alert on an incoming request, so that I notice it immediately.
**Priority:** Must
**Acceptance Criteria**
- **Given** the app is backgrounded, **when** a request arrives, **then** a high-priority notification is raised.

### US-12.4 — Deep-link from the notification (even cold start)
**Story:** As a Receiver, I want tapping the request notification to open the Accept/Decline screen directly, so that I respond in one step.
**Priority:** Must
**Acceptance Criteria**
- **Given** a request notification, **when** I tap it while the app is running, **then** the Accept/Decline screen opens.
- **Given** the app was fully closed, **when** I tap the notification, **then** the app launches and still lands on Accept/Decline (the tap isn't lost).

### US-12.5 — Stop background activity when I choose
**Story:** As a Battery-Conscious User, I want to stop background scanning/advertising, so that I control power use when I don't need discovery.
**Priority:** Should
**Acceptance Criteria**
- **Given** background activity is running, **when** I turn discoverability/scanning off, **then** the background service stops the corresponding activity.

---

# 13. Saved Files Management

### US-13.1 — See saved files grouped by date
**Story:** As a User, I want saved files listed and grouped by date, so that I can find a kept file quickly.
**Priority:** Must
**Acceptance Criteria**
- **Given** I have saved files, **then** they are grouped by date and each shows sender and basic metadata.

### US-13.2 — Open a saved file
**Story:** As a User, I want to open a saved file in a system viewer, so that I can use it outside the app.
**Priority:** Must
**Acceptance Criteria**
- **Given** a saved file's menu, **when** I tap Open, **then** the file opens in the appropriate system viewer.

### US-13.3 — Share a saved file
**Story:** As a User, I want to share a saved file to other apps, so that I can forward what I chose to keep.
**Priority:** Must
**Acceptance Criteria**
- **Given** a saved file's menu, **when** I tap Share, **then** the system share sheet opens with the file.

### US-13.4 — Delete a saved file
**Story:** As a User, I want to remove a saved file from the list, so that I can tidy up.
**Priority:** Should
**Acceptance Criteria**
- **Given** a saved file, **when** I delete it, **then** it disappears from the list.

### US-13.5 — See total storage used
**Story:** As a User, I want to see the total space my saved files use, so that I can manage device storage.
**Priority:** Should
**Acceptance Criteria**
- **Given** the Settings screen, **then** the total storage used by saved files is displayed.

### US-13.6 — See an empty saved-files state
**Story:** As a User, I want a clear empty state when I have no saved files, so that the screen isn't confusing when blank.
**Priority:** Should
**Acceptance Criteria**
- **Given** I have no saved files, **then** an explicit empty state is shown.

---

# 14. Settings & Profile Management

### US-14.1 — Edit my nickname later
**Story:** As a User, I want to change my nickname in Settings, so that I can update my identity over time.
**Priority:** Should
**Acceptance Criteria**
- **Given** Settings, **when** I edit my nickname, **then** the same 1–15 char and no-`|` rules are enforced and the change persists.

### US-14.2 — Change my avatar later
**Story:** As a User, I want to change my avatar in Settings, so that I can refresh my look.
**Priority:** Should
**Acceptance Criteria**
- **Given** Settings, **when** I pick a new avatar, **then** it is saved and used going forward.

### US-14.3 — Switch language anytime
**Story:** As a Bangla-First User, I want to switch between English and Bangla in Settings, so that I can change without reinstalling.
**Priority:** Must
**Acceptance Criteria**
- **Given** Settings, **when** I switch language, **then** the app's text updates to the chosen language.

### US-14.4 — Review the app's privacy behavior
**Story:** As a Privacy-Focused User, I want Settings to describe the app's privacy behavior, so that I understand its guarantees.
**Priority:** Could
**Acceptance Criteria**
- **Given** Settings, **then** it clearly states the offline/no-account/wipe-on-disconnect behavior and shows storage usage.

---

# 15. Reliability, Errors & Edge Cases

### US-15.1 — Survive a Bluetooth toggle
**Story:** As a User, I want the app to recover if Bluetooth is toggled off and on, so that a normal interruption doesn't break it.
**Priority:** Must
**Acceptance Criteria**
- **Given** BT is turned off during use, **then** any session is wiped; **when** BT is turned back on, **then** discovery resumes cleanly.

### US-15.2 — Survive a process kill
**Story:** As a User, I want the app to handle being killed by the system, so that it reopens cleanly and my data was wiped.
**Priority:** Must
**Acceptance Criteria**
- **Given** the app is killed, **when** I reopen it, **then** it opens to a valid state with any prior session data already wiped.

### US-15.3 — Resume scanning after a disconnect
**Story:** As a User, I want discovery to restart automatically after a session ends, so that I can immediately connect with someone else.
**Priority:** Must
**Acceptance Criteria**
- **Given** a session just ended, **when** I return to Home, **then** scanning is already running again.

### US-15.4 — Handle the peer leaving mid-chat
**Story:** As a Session Peer, I want a graceful end if my peer suddenly leaves during chat or transfer, so that I'm not stuck on a dead screen.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active chat or transfer, **when** my peer disconnects abruptly, **then** the session ends cleanly with a message and a wipe.

### US-15.5 — Handle a failed connection
**Story:** As a Sender, I want clear feedback if a connection can't be established, so that I can retry.
**Priority:** Should
**Acceptance Criteria**
- **Given** a connection attempt fails, **then** I see a clear error and a retry path (not a silent hang).

### US-15.6 — Handle simultaneous mutual requests
**Story:** As a User, I want simultaneous mutual connection attempts resolved into one clean session, so that we don't end up broken or doubled.
**Priority:** Should
**Acceptance Criteria**
- **Given** two users request each other at the same time, **then** exactly one 1:1 session is established with no duplicate or stuck states.

### US-15.7 — Be limited to strictly one peer
**Story:** As a Privacy-Focused User, I want only 1:1 sessions, so that no third device can join.
**Priority:** Must
**Acceptance Criteria**
- **Given** an active session, **then** no additional device can join it; the app supports exactly one peer at a time.

### US-15.8 — Clean up after an interrupted transfer
**Story:** As a Session Peer, I want a clean state if a file transfer is interrupted, so that partial/temp files don't linger.
**Priority:** Should
**Acceptance Criteria**
- **Given** a transfer is interrupted, **then** partial files are removed and the failure is reported.

### US-15.9 — Handle running out of storage on save
**Story:** As a Receiver, I want a clear error if there isn't enough space to save a file, so that I understand why the save failed.
**Priority:** Should
**Acceptance Criteria**
- **Given** insufficient storage, **when** I tap Save, **then** the app reports the failure clearly and the file is not partially written.

### US-15.10 — Handle denied camera/storage at pick time
**Story:** As a Sender, I want a clear prompt if camera or storage access is missing when I attach a file, so that I can grant it and continue.
**Priority:** Should
**Acceptance Criteria**
- **Given** the needed access is missing, **when** I choose a source, **then** the app requests it or explains how to enable it, without crashing.

---

# 16. Performance & Usability

### US-16.1 — Open the app quickly
**Story:** As a User, I want the app to cold-start fast (under ~2 s on a mid-range phone), so that it's ready when I need it.
**Priority:** Should
**Acceptance Criteria**
- **Given** a mid-range device, **when** I cold-launch, **then** the first usable screen appears in under ~2 seconds.
- **Given** startup, **then** the UI never freezes on the main thread.

### US-16.2 — Enjoy smooth, responsive UI
**Story:** As a User, I want smooth scrolling and transitions, so that the app feels polished.
**Priority:** Should
**Acceptance Criteria**
- **Given** normal use, **then** scrolling and animations stay smooth without visible stutter.

### US-16.3 — Use the whole app in my language
**Story:** As a Bangla-First User, I want full Bangla/English parity across every screen, so that nothing falls back to the other language.
**Priority:** Should
**Acceptance Criteria**
- **Given** I chose a language, **then** every user-facing string on every screen appears in that language.

### US-16.4 — Use the app with a screen reader
**Story:** As an Accessibility User, I want all interactive elements labeled for TalkBack, so that I can use BlueWhisper without sight.
**Priority:** Should
**Acceptance Criteria**
- **Given** TalkBack is on, **then** buttons, toggles, and list items announce meaningful labels.
- **Given** TalkBack is on, **then** the core flows (onboarding, connect, chat, save) are fully operable by voice/gestures.

---

# 17. Future Phases (Out of Scope for Phase 1)

### US-17.1 — Multi-message history (opt-in)
**Story:** As a User, I want an optional way to keep a conversation beyond one session, so that I can continue a thread later.
**Priority:** Future
**Acceptance Criteria**
- **Given** this is a future phase, **then** it must not weaken the Phase-1 default of wipe-on-disconnect; any retention is explicit and opt-in.

### US-17.2 — Group (multi-peer) sessions
**Story:** As a User, I want to talk with more than one nearby person, so that small groups can share.
**Priority:** Future
**Acceptance Criteria**
- **Given** a future phase, **then** group support must preserve end-to-end encryption and clear privacy semantics.

### US-17.3 — Cross-platform (iOS) support
**Story:** As a User, I want to connect with iPhone users, so that platform doesn't limit who I can reach.
**Priority:** Future
**Acceptance Criteria**
- **Given** a future phase, **then** the same offline/encryption/wipe guarantees apply across platforms.

### US-17.4 — Larger files & resumable transfers
**Story:** As a Sender, I want to send files above 25 MB with resume-on-interrupt, so that big files transfer reliably.
**Priority:** Future
**Acceptance Criteria**
- **Given** a future phase, **then** larger transfers remain encrypted and can resume after interruption.

### US-17.5 — Contacts / trusted devices
**Story:** As a Returning User, I want to mark trusted people, so that reconnecting with them is faster.
**Priority:** Future
**Acceptance Criteria**
- **Given** a future phase, **then** any stored trust must be local-only and revocable, consistent with the no-account principle.

---

*End of user stories.*
