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
MainActivity.kt             enableEdgeToEdge + AppTheme { HomeScreen() }  (no navigation library)
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
core/
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

**Permissions.** Status is `GRANTED / NOT_REQUESTED / DENIED / PERMANENTLY_DENIED`.
Permanent denial = previously requested (DataStore flag) + not granted + the activity says
no rationale should be shown. The screen reports rationale flags on every `ON_RESUME` and
after every permission result, so the UI updates after the user changes settings.

**State / events / effects.** `HomeUiState` in `_homeUiState`/`homeUiState`; user actions via
`onEvent(HomeEvent)`; one-shot work (launch a permission request, open settings, snackbar)
via `HomeEffect` on a `Channel`.

**How to add a new screen.** No navigation library exists (single screen). Add Navigation 3
with a sealed `Route` in `core/navigation/` first, then follow the `presentation/home/` slice.

## 4. Tech Stack & Dependencies

| Library | Version | Why |
|---|---|---|
| AGP (built-in Kotlin) | 9.4.1 | Template |
| Kotlin | 2.2.10 | Template |
| Compose BOM | 2026.02.01 | Template (Material 3) |
| Koin (BOM) — android, androidx-compose | 4.2.2 | **New.** DI (guideline default) |
| DataStore Preferences | 1.2.1 | **New.** Switches, sensitivity, permission-requested flags |
| lifecycle-runtime-compose / viewmodel-compose | 2.11.0 | **New.** `collectAsStateWithLifecycle`, ViewModel |
| kotlinx-coroutines-test | 1.11.0 | **New.** Unit tests |

No networking, no image loading, no Material Icons Extended (icons are vector drawables).

## 5. Feature Progress

### Feature: Charging alerts  (Design: Home → Charging card)
- [ ] Domain models + use cases
- [ ] Repository interface + implementation
- [ ] Data sources (ChargingMonitor) + notifications
- [ ] DI wiring
- [ ] Components (one per file, previewed)
- [ ] Screen, all states
- [ ] ViewModel + UiState/Event/Effect
- [ ] Edge cases (notifications blocked, channel blocked, service killed)
- [ ] Tests
- [ ] Builds clean, committed

### Feature: Motion alarm  (Design: Home → Motion card, hero, alarm overlay)
- [ ] MotionDetector + tests
- [ ] MotionSensorSource, wake lock, arming countdown
- [ ] AlarmPlayer (siren, vibration, alarm volume, audio focus) + Stop action
- [ ] UI + sensitivity
- [ ] Edge cases (no accelerometer, screen off, re-arm after stop)
- [ ] Builds clean, committed

### Feature: Clap detection  (Design: Home → Clap card)
- [ ] ClapDetector + tests
- [ ] MicrophoneSource, microphone service type, permission flow
- [ ] UI + sensitivity
- [ ] Edge cases (permission permanently denied, no mic, mic busy, restarted from background)
- [ ] Builds clean, committed

## 6. Screen ↔ Design Map

| Screen | Orientation | Design ref | Implementation path | Status |
|---|---|---|---|---|
| Home | Any | `design/tokens.md` (no frames) | `presentation/home/HomeScreen.kt` | Not started |

## 7. Data Model & API

No network API. Local data (DataStore `settings`): `charging_enabled`, `motion_enabled`,
`clap_enabled`, `motion_sensitivity`, `clap_sensitivity`, `requested_<permission>`,
`battery_tip_dismissed`.

## 8. Milestone Progress

Execution mode: **Hybrid**.

| # | Milestone | Status |
|---|---|---|
| 1 | Foundation | Done |
| 2 | Data & domain layer | In progress |
| 3 | Protection engine — **gate** | Not started |
| 4 | Home screen — **gate** (permissions) | Not started |
| 5 | Integration | Not started |
| 6 | QA | Not started |

## 9. Decisions & Assumptions

Approved at intake (user: "go with your defaults and test on connected device"):

- Hybrid execution mode; dark + light themes following the system; Manrope bundled.
- Low / Medium / High sensitivity for motion and clap. No PIN to stop. No restart on boot.
- No navigation library for a single screen (deviation from the Navigation 3 default, §5.7,
  justified by §5.1 "don't scaffold what the project has no use for").
- Bundled synthesised siren (fallback: system default alarm tone); alarm stream raised to max
  while ringing and restored on stop.
- Testing on the connected Xiaomi M2101K7AG (Redmi Note 10, API 31, MIUI).
- Colour tokens named from the **first** four hex digits.
- Material Icons Extended not added (huge, frozen at 1.7.x, outside the BOM): needed icons
  are Material Symbols Rounded vector drawables.
- Foreground service type `specialUse` for charging + motion (no specific type fits sensor
  monitoring); `microphone` added only while clap detection runs. Google Play would require a
  `specialUse` justification.
- Motion detection holds a partial wake lock while armed so the accelerometer keeps
  reporting with the screen off.
- A 5-second arming countdown follows enabling motion detection and stopping an alarm.
- Charging alerts require notification permission (they are notifications). Motion and clap
  do not, but ask once so the alarm's Stop action can appear on the lock screen.

## 10. Known Gaps / TODO

- API 24–30 and 32–37 not run on a device or emulator yet (only API 31 hardware connected).

## 11. How to Build & Run

```
./gradlew assembleDebug installDebug     # build + install on the connected device
./gradlew testDebugUnitTest              # unit tests
./gradlew lintDebug                      # lint
```

No keys or secrets are needed.
