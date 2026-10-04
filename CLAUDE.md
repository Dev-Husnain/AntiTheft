# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Rules live in CLAUDE-CODE-GUIDELINES.md

`CLAUDE-CODE-GUIDELINES.md` at the repo root is the permanent rules document for this project (architecture, naming, Compose structure, DI, navigation, workflow, git, definition of done). **Read it in full before doing any work.** This file intentionally does not copy those rules: it holds only build commands and the current project state. Do not edit the guidelines file during a build.

Key points from it that change how you start:
- New work begins with the intake procedure (§3, §13): do not create, edit or delete project files until a milestone plan has been agreed with the user. The **Project Inputs** block at the top of the guidelines is still unfilled placeholders; ask for it before anything else.
- Once a plan is approved, the agent creates and maintains `PROJECT-GUIDE.md` (living record of architecture, progress, decisions) and `design/tokens.md` as the first changes of the Foundation milestone. When resuming, read `PROJECT-GUIDE.md` and continue from its milestone progress instead of repeating intake.
- Existing project conventions win on *form*; the guidelines win on safety, secrets, permissions, verification and honesty (§4).

## Current state

Fresh Android Studio template: a single `:app` module (package `com.anti.theft`) with one `MainActivity` showing a Compose `Greeting`, plus the template theme in `ui/theme/`. None of the target Clean Architecture tree (§5.1: `di/`, `data/`, `domain/`, `presentation/`, `core/`), Koin, or Navigation 3 exists yet. This is the "empty or template project" case in §3.2. The directory is not yet a git repository (§8 covers initializing it).

## Build setup

- AGP 9.4.1 with **built-in Kotlin** (there is no `kotlin-android` plugin; only `kotlin.compose` is applied). Kotlin 2.2.10, Compose BOM 2026.02.01.
- `compileSdk`/`targetSdk` 37 (set via the AGP 9 `compileSdk { version = release(37) }` DSL), `minSdk` 24, Java 11 source/target.
- Release builds use the AGP 9 `optimization { enable = false }` block (R8 currently off).
- All dependencies and plugins go through `gradle/libs.versions.toml`; no inline versions. Don't upgrade AGP, Gradle, Kotlin, the BOM or SDK levels unless a requirement forces it (§5.19).

## Commands

Run from the repo root (`./gradlew` in Bash, `.\gradlew.bat` in PowerShell):

```
./gradlew assembleDebug                      # build debug APK
./gradlew installDebug                       # install on connected device/emulator
./gradlew testDebugUnitTest                  # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.anti.theft.ExampleUnitTest"            # one class
./gradlew testDebugUnitTest --tests "com.anti.theft.ExampleUnitTest.addition_isCorrect"  # one method
./gradlew connectedDebugAndroidTest          # instrumented/Compose UI tests (needs device)
./gradlew lintDebug                          # Android lint
./gradlew :app:processDebugMainManifest      # produce merged manifest to audit permissions
```

The guidelines require running the build (and tests if present) before every commit, and reporting honestly when the environment couldn't build or run on a device.
