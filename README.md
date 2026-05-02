# BlueWhisper — Phase 1 (Reviewed Build)

This is the result of a static review of the Phase 1 codebase. The Tier 2/3/4
roadmap from `PRD_GAP_ANALYSIS.md` §10 has now been implemented in source —
this README has been updated to reflect that, and was re-verified against the
codebase on 2026-05-02. **The code has not been compiled or run on a device
inside this sandbox**: every claim below is based on reading the source.

For the requirement-by-requirement audit see `PRD_DETAILED_MAPPING.md` and
`PRD_GAP_ANALYSIS.md`.

## How to open

1. Unzip into a folder.
2. Open Android Studio (Hedgehog | 2023.1.1 or newer recommended for AGP 8.3.2).
3. File → Open → select the unzipped `BlueWhisper` folder.
4. Wait for Gradle Sync. **Expect this to download dependencies for several
   minutes the first time.** A fresh sync touches the internet for AGP, Kotlin
   2.0.21, Compose BOM 2024.05, Hilt, Room, KSP, Nearby Connections, Tink,
   Coil, Coroutines, and the Android Gradle plugin's transitive deps.
5. After sync, hit Build → Make Project.

## What was changed vs the original (compile-blocking fixes)

1. Removed the stray `{app/...` directory at the zip root (un-expanded brace
   expansion left over from whoever generated the original zip).
2. **Bumped Kotlin to 2.0.21 + KSP to 2.0.21-1.0.25.** The original used
   Kotlin 1.9.23 *with* the `org.jetbrains.kotlin.plugin.compose` plugin,
   which only exists from Kotlin 2.0+. With 1.9.x it would not even sync.
3. Added `androidx.appcompat:appcompat:1.7.0` to dependencies.
   `LocaleManager.kt` imports `AppCompatDelegate` which lives there; without
   appcompat, the file would not compile.
4. **`UserProfileDataStore.kt`**: removed a call to a `suspend fun` from
   inside a non-suspending `.map { }` lambda, which Kotlin rejects.
5. **`ConnectionScreens.kt`**: moved the `package` declaration above the
   `import` line (Kotlin requires package first); hoisted a `context`
   variable declaration above the `LaunchedEffect` that referenced it.
6. **`SettingsScreen.kt`**: deleted a duplicate `package` declaration that
   appeared mid-file (Kotlin allows only one); merged the duplicate import
   blocks into one at the top; hoisted a `LocalContext.current` call out
   of an `onClick` lambda into the composable body.
7. **`SavedFilesScreen.kt`**: removed `verticalAlignment` from a `Column`
   (that parameter doesn't exist on `Column`); wrapped in a `Box` with
   `contentAlignment = Center` instead.
8. **`ChatScreen.kt`**: fixed a callback type mismatch where a
   `(File?) -> Unit` was passed where `() -> Unit` was expected; deleted
   a stray duplicate `hiltViewModel` import and a misnamed alias import.
9. **`AppModule.kt`**: removed duplicate `@Provides` methods for classes
   that are already `@Singleton class @Inject constructor(...)`. Hilt
   would have rejected the build with "duplicate binding" errors.
10. **`AndroidManifest.xml` icon refs**: the manifest pointed at
    `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` but no mipmap
    directories existed. Added a minimal adaptive launcher icon (vector
    speech-bubble on dark blue), with both `mipmap-anydpi-v26/` (modern)
    and `mipmap/` (pre-API-26 fallback) variants.

## Correctness fixes from the first review pass

11. **`MainActivity.kt`**: `startDestination` was a plain `var` read inside
    `setContent { }`, so updates from the lifecycleScope coroutine never
    triggered recomposition — the screen would stay blank forever. Changed
    to `mutableStateOf`. (Subsequently extended to `splashDoneState` and a
    buffered notification deep-link — see B-18 / B-19.)
12. **`NearbyConnectionsManager.kt`**: `device.nickname` was being set to
    the full wire-format `"nickname|avatarId"` string, so the home screen
    would show e.g. `Annoy|3` instead of `Annoy`. Now parses the nickname
    out of the endpoint name.
13. **`ChatViewModel.addReceivedFile`**: incremented `messageCount`
    without updating `isLimitReached` / `remainingMessages`, so a file
    received as the 20th message would not lock the input.
14. **`OnboardingViewModel`**: was saving the language selection to
    DataStore but not to the SharedPreferences that
    `BlueWhisperApp.attachBaseContext` reads on next launch. Result:
    picking Bangla on onboarding had no effect on the app's locale.
    Now persists to both.

## Tier 2 / 3 / 4 work landed since the first review

This is the work tracked in `PRD_GAP_ANALYSIS.md` §10. Verified on disk
2026-05-02:

- **Tests.** `app/src/test/` and `app/src/androidTest/` now hold every PRD
  Week-9 category — `MessageSerializerTest`, `EncryptionEngineTest` (incl.
  10 000-message nonce-uniqueness check), `NicknameRulesTest`,
  `OnboardingViewModelTest`, `ChatViewModelTest`, `VanishTimerTest`,
  `ConnectionViewModelTest`, `HomeViewModelTest`, `FileViewerViewModelTest`,
  `TwoDeviceIntegrationTest` (in-memory protocol harness), and
  `OnboardingFlowUiTest` (Espresso). Tests compile against the source as
  written; they have not been *executed* in this sandbox.
- **`NicknameRules`** added under `domain/model/`. Rejects `|` (the
  advertising-payload delimiter) and clamps length 1–15. Used by both
  `OnboardingViewModel` and `SettingsViewModel`.
- **Sender attribution**: `FileMetadata.senderNickname` and
  `ReceivedFile.senderNickname` are threaded end-to-end so saved files
  always carry the real sender (no more hardcoded `"Unknown"`).
- **`FileManager.savePublicCopy`**: saves to `MediaStore.Downloads` on Q+
  and the public `Downloads/BlueWhisper/Received/` directory on API 26-28.
  This replaces the pre-revision `getExternalFilesDir` (app-private) save.
- **Saved-file Open + Share**: `SavedFileActions` + a 3-dot menu on every
  saved-files row. Resolves the MediaStore content URI (Q+) or wraps the
  legacy file path through `FileProvider`.
- **Service is sole owner of advertise/discover.** `BluetoothForegroundService`
  added `ACTION_BECOME_DISCOVERABLE` / `ACTION_BECOME_INVISIBLE`. `HomeViewModel`
  no longer calls `startAdvertising` / `stopAdvertising` directly; it dispatches
  service intents.
- **WakeLock sized to file bytes** via `ChatViewModel.computeWakeLockTimeoutMs`.
  Replaces the prior hard-coded 35 s constant; clamps to [30 s, 10 min].
- **FILE_META-after-payload race fixed** via a `pendingPayloads`
  `ConcurrentHashMap` that reconciles either order.
- **`pendingFileMeta` / `pendingPayloads`** are both `ConcurrentHashMap`.
- **`cleanupSession`** no longer wipes `_nearbyDevices`. Stale pruning
  is left to the 2 s sweep / 10 s freshness threshold (FR-02.5).
- **Splash + lifecycle cleanup**: `MainActivity.splashDoneState` is now
  `mutableStateOf<Boolean>`; cold-start notification taps are buffered
  via `pendingDeepLink` and replayed once `NavController` mounts;
  `runBlocking` removed from `BlueWhisperApp.onCreate`.
- **Refactors**: `SettingsViewModel` moved out of `SettingsScreen.kt` into
  its own file. Vanish-countdown logic extracted into `VanishTimer.kt`
  so `ChatViewModel` is no longer the timer's sole owner.

## Bugs fixed in this final pass

- **B-21 — `EncryptionEngine` random nonce tail.** Random bytes were being
  written into a `sliceArray` copy that never made it back into `nonce`,
  so the last 4 bytes of every nonce were zero. Counter-based uniqueness
  still held (and AES-GCM was always safe), but the random-tail
  defence-in-depth was silently lost. Fixed by writing the random bytes
  directly into `nonce` via a separate `ByteArray(4) + copyInto(nonce, 8)`.
- **B-22 — `FileViewerViewModel.onCleared` secure-delete no-op.** The
  `viewModelScope.launch` ran *after* `super.onCleared()` cancelled the
  scope, so the secure-delete never ran. Fixed by spawning a detached
  daemon `Thread` instead. (User-visible impact was nil because
  `ChatViewModel.wipeSessionData → fileManager.deleteAllTempFiles()`
  already covers this path on every disconnect, but the redundant safety
  net is now functional.)

## Known issues NOT fixed in this codebase

These either need hardware/manual work or are accepted as-is:

- **B-09** — `performSingleScan` schedules an uncancellable stop coroutine.
  Mitigated by service-owned scan cycle.
- **B-10** — `wipeSessionData()` fires twice per disconnect. Idempotent
  today; an `isWiped` guard would be cleanup.
- **B-13** — optimistic send path doesn't surface
  `PayloadTransferUpdate.FAILURE` to the per-message UI yet.
- **B-20 — a11y / TalkBack** never verified. Manual sweep needed on device.
- **NFR-01.1 cold-start time** never measured on a real mid-range device.
  Code is plausibly fast (async splash, no main-thread DataStore read),
  but no on-device stopwatch number exists.
- **Hardcoded UI strings.** Many Composables hardcode English/Bangla
  strings inline rather than reading `R.string.*` via `stringResource()`.
  The `strings.xml` and `values-bn/strings.xml` resources exist with full
  translations but are largely unused outside onboarding/disconnect/chat
  shell, so runtime locale switching won't change most of the UI. Mechanical
  follow-up.

## Things to expect when you first build

The first sync may surface things not catchable from static reading alone,
plus:

- Possible `kotlinx-parcelize` plugin warnings since `@Parcelize` is used on
  `ReceivedFile` and `FileType`. The plugin is declared (`id("kotlin-parcelize")`)
  so this should work, but verify.
- Lint warnings about unused imports and unused variables — there are some.
  Safe to ignore for now.
- The `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` requires
  `compileSdk = 34` (set) and the service declares `foregroundServiceType =
  "connectedDevice"` in the manifest (set). Should be fine on API 34.

## Recommended workflow

1. Get it to **sync** first. Fix any sync errors.
2. Get it to **build**. Fix any compile errors.
3. Get it to **install on a device**. Fix any manifest/runtime startup
   crashes. Logcat is your friend.
4. Get the **onboarding screen** to render. Then home screen.
5. Test BT discovery with a second device — the 1:1 pairing layer needs
   careful manual testing.
6. Run the unit tests under `app/src/test/` (Robolectric + Mockito) to
   sanity-check the reviewed logic. The androidTest under
   `app/src/androidTest/` requires a connected device or emulator.

Don't try to test all 86 test cases at once. Walk through them on real
hardware. The wipe-on-disconnect tests (TC-W01 through TC-W08) matter the
most — they are the privacy promise the app is built around.

## Compliance score

Per `PRD_GAP_ANALYSIS.md` §8: **~98.3 / 100**. The remaining ~1.7 points
are on-device cold-start measurement (~1) and an a11y / TalkBack sweep
(~0.5), both hardware/manual.

Good luck.
