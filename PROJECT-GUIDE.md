# Anti-Theft Alarm — Project Guide

## 1. Overview

A single-screen Android app that guards the phone in the background:

- **Charging alerts** — notifies "Charger Connected" / "Charger Disconnected".
- **Motion alarm** — when the phone is moved or picked up, plays a loud looping siren with a
  Stop button (in-app and on the notification).
- **Clap detection** (bonus) — listens through the microphone and triggers the same alarm.

Each feature has an on/off switch; motion and clap also have Low / Medium / High sensitivity.
Supports API 24–37, phones, tablets, foldables, both orientations, dark and light themes.

Source of requirements: the "Android Assessment Task – Anti-Theft Alarm App" brief, plus the
intake decisions recorded in §9.

## 2. Design Source

None was supplied. The visual design was proposed during intake and approved; the local
snapshot is `design/tokens.md`. Font licence: `design/licenses/Manrope-OFL.txt`.

## 3. Architecture & Conventions

Rules: `CLAUDE-CODE-GUIDELINES.md`. Single `:app` module, package `com.anti.theft`.

```
App.kt                      Application: starts Koin, creates notification channels
MainActivity.kt             enableEdgeToEdge + AppTheme { AppNavHost() } — hosts the nav graph only
di/                         appModule (dispatchers, DataStore, repositories, service helpers)
                            homeModule (use cases, HomeViewModel)
domain/
    models/                 settings, permission, capability, protection-status models
    repo/                   repository interfaces
    usecase/                ToggleFeature, ResolvePermissionStatus, SyncProtection
    detector/               MotionDetector, ClapDetector — pure Kotlin signal processing
data/
    local/                  DataStore keys, in-memory ProtectionStateHolder
    repo/                   repository implementations
    service/                ProtectionService (foreground service) and its collaborators:
                            ChargingMonitor, MotionSensorSource, MicrophoneSource,
                            AlarmPlayer, ProtectionNotifier, ServiceTypes
presentation/home/          HomeScreen, HomeViewModel, HomeUiState, HomeEvent, HomeEffect,
                            components/
presentation/alarm/         AlarmScreen, AlarmViewModel, AlarmUiState, AlarmEvent, components/
core/
    navigation/             Route (sealed NavKey), AppNavHost (Navigation 3)
    theme/                  Color, Type, Shape, Dimens, Theme
    ui/                     app-wide components (GradientBackground, PrimaryButton, …)
    utils/                  extension functions (settings intents, permission helpers)
    dispatchers/            AppDispatchers (injected)
```

**How protection runs.** The UI never talks to the service directly. `SyncProtectionUseCase`
works out which features can actually run (switch on + permission granted + hardware
present) and asks `ProtectionRepository` to start, update or stop `ProtectionService`.
The service observes `SettingsRepository` itself, runs one coroutine per enabled feature, and
publishes live state (running, motion arming/armed, clap listening, ringing) to
`ProtectionStateHolder`, which the ViewModel observes through `ProtectionRepository`.

**Service crash/ANR safety (do not regress).**
- Every start command calls `startForeground()` synchronously before anything else; if the
  microphone type is refused it retries without it. A service started with
  `startForegroundService()` therefore always reaches the foreground in time.
- Nothing calls `stopService()` (it crashes API 26–30 when it beats `startForeground()`). To
  stop, `ProtectionRepositoryImpl` sends another start command; the service enters the
  foreground, re-evaluates (every start forces a fresh evaluation) and stops itself with
  `stopSelfResult(lastStartId)`, which keeps it alive if a newer start is queued.
- Each guard runs inside `isolated { }`; a scope `CoroutineExceptionHandler` shuts down cleanly
  instead of crashing. Sensor maths and audio capture run off the main thread.
- Stress-tested: rapid on/off toggling on API 31 (device) and API 36 (emulator), no crash/ANR.

**Permissions.** Status is `GRANTED / NOT_REQUESTED / DENIED / PERMANENTLY_DENIED`.
Permanent denial = previously requested (DataStore flag) + not granted + the activity says
no rationale should be shown. The screen reports rationale flags on every `ON_RESUME` and
after every permission result, so the UI updates after the user changes settings.

**State / events / effects.** `HomeUiState` in `_homeUiState`/`homeUiState`; user actions via
`onEvent(HomeEvent)`; one-shot work (launch a permission request, open settings, snackbar)
via `HomeEffect` on a `Channel`.

**Navigation.** Navigation 3: `Route.Home` and `Route.Alarm` in `core/navigation/Route.kt`;
`AppNavHost` owns the back stack, pushes `Route.Alarm` when the service starts ringing (or the
alarm notification is tapped) and pops it when the alarm stops. Screens expose callbacks only.

**System bars.** `MainActivity` calls `enableEdgeToEdge()` and nothing else. Each screen owns
its background and insets (`safeDrawing` padding applied once, on the screen's content) and
declares its status-bar icon appearance through `SystemBarAppearance` (dark icons on the light
theme, light icons on the dark theme and on the red alarm screen).

**How to add a new screen.** Add a `Route` object, a `presentation/<feature>/` slice following
`home/`, register its ViewModel in a Koin module, and add an `entry<Route.X>` in `AppNavHost`.

## 4. Tech Stack & Dependencies

| Library | Version | Why |
|---|---|---|
| AGP (built-in Kotlin) | 9.4.1 | Template |
| Kotlin | 2.2.10 | Template |
| Compose BOM | 2026.02.01 | Template (Material 3) |
| Koin (BOM) — android, androidx-compose | 4.2.2 | **New.** DI (guideline default); `singleOf`/`factoryOf`/`viewModelOf` DSL |
| DataStore Preferences | 1.2.1 | **New.** Switches, sensitivity, permission-requested flags |
| lifecycle-runtime-compose / viewmodel-compose | 2.11.0 | **New.** `collectAsStateWithLifecycle`, ViewModel |
| kotlinx-coroutines-test | 1.11.0 | **New.** Unit tests |
| Navigation 3 (runtime, ui) | 1.2.0 | **New.** Navigation (user requirement) |
| lifecycle-viewmodel-navigation3 | 2.11.0 | **New.** Per-entry ViewModel scoping |
| kotlinx-serialization-core + plugin | 1.10.0 / Kotlin 2.2.10 | **New.** `@Serializable` NavKeys for `rememberNavBackStack` |

No networking, no image loading, no Material Icons Extended (icons are vector drawables).

## 5. Feature Progress

### Feature: Charging alerts  (Design: Home → Charging card)
- [x] Domain models + use cases
- [x] Repository interface + implementation
- [x] Data sources (ChargingMonitor) + notifications
- [x] DI wiring
- [x] Components (one per file, previewed)
- [x] Screen, all states
- [x] ViewModel + UiState/Event/Effect
- [x] Edge cases (notifications blocked, channel blocked, service killed)
- [x] Tests
- [x] Builds clean, committed

### Feature: Motion alarm  (Design: Home → Motion card, hero, alarm overlay)
- [x] MotionDetector + tests
- [x] MotionSensorSource, wake lock, arming countdown
- [ ] AlarmPlayer (siren, vibration, alarm volume, audio focus) + Stop action — built; **awaiting device gate**
- [x] UI + sensitivity
- [ ] Edge cases (no accelerometer, screen off, re-arm after stop) — **awaiting device gate**
- [x] Builds clean, committed

### Feature: Clap detection  (Design: Home → Clap card)
- [x] ClapDetector + tests
- [x] MicrophoneSource, microphone service type, permission flow
- [x] UI + sensitivity
- [x] Permission permanently denied (verified on device) · [ ] mic busy / background restart — **awaiting device gate**
- [x] Builds clean, committed

## 6. Screen ↔ Design Map

| Screen | Orientation | Design ref | Implementation path | Status |
|---|---|---|---|---|
| Home | Any | `design/tokens.md` (no frames) | `presentation/home/HomeScreen.kt` | Done; verified light, dark, landscape on API 31 |
| Alarm | Any | `design/tokens.md` (no frames) | `presentation/alarm/AlarmScreen.kt` | Built; not yet seen on device (needs a real trigger) |

## 7. Data Model & API

No network API. Local data (DataStore `settings`): `charging_enabled`, `motion_enabled`,
`clap_enabled`, `motion_sensitivity`, `clap_sensitivity`, `requested_<permission>`,
`battery_tip_dismissed`.

## 8. Milestone Progress

Execution mode: **Hybrid**.

| # | Milestone | Status |
|---|---|---|
| 1 | Foundation | Done |
| 2 | Data & domain layer | Done |
| 3 | Protection engine — **gate** | Done; motion + clap alarms confirmed by the user on device |
| 4 | Home + Alarm screens, Navigation 3 — **gate** (permissions) | Done; permission gate verified on API 31 |
| 5 | Integration | Done |

| 6 | QA | Done (see matrix below) |

### Verification matrix (2026-10-04)

Devices: Xiaomi M2101K7AG / MIUI, API 31 (physical) · Pixel 9a emulator, API 36.

| Check | Result |
|---|---|
| Assembles · unit tests (37) · lint (0 errors) | Verified |
| App launches · Home ↔ Alarm navigation | Verified (Alarm screen confirmed by user) |
| Charging alerts (connect / disconnect) | Verified API 31 + 36 |
| Motion alarm · clap alarm | Verified by user, API 31 |
| Screen-off motion · notification Stop action | Not explicitly confirmed |
| Notification permission (API 33+ runtime flow) | Verified API 36 |
| Mic permission: deny → deny again → permanent → Settings → auto-enable | Verified API 31 |
| FGS types: specialUse 0x40000000, + microphone 0x40000080 | Verified API 36 |
| Rapid toggle stress (no crash / ANR / orphan service) | Verified API 31 + 36 |
| Crash → reopen restores protection | Verified API 31 (MIUI blocks automatic restart) |
| Light / dark theme · status-bar icon contrast · landscape two-pane · 1.3× font | Verified API 31 |
| Gesture navigation insets | Verified API 36 |
| Process death during permission dialog | Unit-tested only |
| API 24–30, 32–35, 37 | Not run (no SDK command-line tools to fetch those images) |
| Tablet / foldable width | Two-pane layout verified via landscape phone only |

## 9. Decisions & Assumptions

Approved at intake (user: "go with your defaults and test on connected device"):

- Hybrid execution mode; dark + light themes following the system; Manrope bundled.
- Low / Medium / High sensitivity for motion and clap. No PIN to stop. No restart on boot.
- ~~No navigation library~~ — **reversed by the user on 2026-10-04**: use Navigation 3, no
  condition-based screen switching. The alarm is its own `Route.Alarm` destination.
- Bundled synthesised siren (fallback: system default alarm tone); alarm stream raised to max
  while ringing and restored on stop.
- Testing on the connected Xiaomi M2101K7AG (Redmi Note 10, API 31, MIUI).
- Colour tokens named from the **first** four hex digits.
- Material Icons Extended not added (huge, frozen at 1.7.x, outside the BOM): needed icons
  are Material Symbols Rounded vector drawables.
- Foreground service type `specialUse` for charging + motion (no specific type fits sensor
  monitoring); `microphone` added only while clap detection runs. Google Play would require a
  `specialUse` justification.
- Clap detection (revised after user report of false alarms on loud speech and a phone dropped
  on a mattress): a candidate must be loud vs. the adaptive background **and** have a sharp
  attack (≤ 6 ms) **and** ≥ 22/28/35 % of its energy above 2 kHz (High/Medium/Low) **and**
  decay to 30 % within 120 ms. Audio source is UNPROCESSED where supported, else
  VOICE_RECOGNITION (no AGC). Unit tests cover claps, speech, a shout, a plosive, a thud and
  music; an ablation showed the spectral check is what rejects speech and thuds.
- Motion detection holds a partial wake lock while armed so the accelerometer keeps
  reporting with the screen off.
- A 5-second arming countdown follows enabling motion detection and stopping an alarm.
- Motion wake lock: 2 h timeout renewed hourly while armed; a separate 10 s wake lock covers
  alarm start-up until MediaPlayer's own wake lock takes over.
- Pending permission/guard kept in `SavedStateHandle` so a result after process death still
  completes the user's action.
- The service clears any stale alarm notification in `onCreate` (left by a process that died
  mid-alarm).
- Charging alerts require notification permission (they are notifications). Motion and clap
  do not, but ask once so the alarm's Stop action can appear on the lock screen.

## 10. Known Gaps / TODO

- Confirmed by the user: motion and clap alarms. Not explicitly confirmed yet: screen-off
  motion detection and the notification Stop action.
- MIUI does not restart a crashed sticky service while Autostart is off (observed with
  `am crash`); protection resumes when the app is next opened. The in-app tip points users to
  Autostart / battery settings. Sticky restart on stock Android not observed (no device).
- Process death during the permission dialog is covered by a unit test (SavedStateHandle), not
  reproduced on device (a process with a foreground service can't be killed via `am kill`).
- Lint: 4 "newer version available" warnings (Compose BOM, Kotlin, serialization) left on
  purpose — guidelines §5.19 forbid opportunistic upgrades.

- API 24–30 and 32–37 not run on a device or emulator yet (only API 31 hardware connected).

## 11. How to Build & Run

```
./gradlew assembleDebug installDebug     # build + install on the connected device
./gradlew testDebugUnitTest              # unit tests
./gradlew lintDebug                      # lint
```

No keys or secrets are needed.
