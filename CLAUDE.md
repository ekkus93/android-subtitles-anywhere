# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This is a monorepo, not a single Android project. Components:

- `android/` — Kotlin/Jetpack Compose Android client (single Gradle module `:app`, package `com.ekkus93.silentcaption`). USB serial transport + JNI bridge to the Rust core + foreground caption session service.
- `core/` — platform-neutral Rust protocol/audio/ASR/caption/session core. `unsafe_code` is forbidden here (`[lints] unsafe_code = "forbid"` in `core/Cargo.toml`) — keep it that way.
- `core-jni/` — Rust JNI boundary crate wrapping `core` for Android; this is the intended (and only) place `unsafe` belongs.
- `firmware/` — ESP-IDF C firmware for the ESP32-WROOM-32 A2DP-sink prototype.
- `protocol/` — versioned dongle↔mobile wire protocol spec (`PROTOCOL_V1.md`) + shared JSON test vectors.
- `ios/` — reserved/empty placeholder for a future Swift/SwiftUI client.
- `tests/host/` — C host unit tests for firmware logic; `tests/fixtures/` — redistribution-safe audio fixtures (do not commit copyrighted media).
- `docs/` — `SILENT_CAPTION_V01_SPEC.md` (product spec) and `SILENT_CAPTION_V01_TODO.md` (execution plan, gated `SC-XXX` phases) are authoritative. `docs/TRACEABILITY.md` defines the evidence convention (see below). `docs/evidence/` holds per-task evidence docs.
- `scripts/` — the `check-*.sh` scripts below are the source of truth for what CI runs; prefer them over ad hoc commands.

## Build / test / lint commands

These mirror `.github/workflows/ci.yml` exactly — run the relevant one before considering a change done.

**Rust core** (`scripts/check-rust.sh`), toolchain pinned in `rust-toolchain.toml`:
```sh
cargo fmt --manifest-path core/Cargo.toml --all -- --check
cargo clippy --manifest-path core/Cargo.toml --all-targets --all-features -- -D warnings
cargo test --manifest-path core/Cargo.toml --all-features --locked

cargo fmt --manifest-path core-jni/Cargo.toml --all -- --check
cargo clippy --manifest-path core-jni/Cargo.toml --all-targets --all-features -- -D warnings
cargo test --manifest-path core-jni/Cargo.toml --all-features
```
Both crate manifests deny `clippy::all` and `clippy::pedantic` — treat clippy warnings as errors when writing Rust.

**Android** (`scripts/check-android.sh`):
```sh
gradle -p android --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:detekt
```
Use bare `gradle`, not `./gradlew` — the wrapper script itself (`gradlew`/`gradlew.bat`) is **not committed** to this repo yet (only `android/gradle/wrapper/gradle-wrapper.properties` is tracked, pinning 9.5.0); README notes the wrapper is pending `SC-004`. CI installs Gradle 9.5.0 via `setup-gradle` and calls it directly. To run a single test class: add `--tests "com.ekkus93.silentcaption.session.SomeTest"` to the `testDebugUnitTest` invocation. There is no `androidTest/` source set yet (instrumented-test deps are present but unused).

Kotlin style is enforced by ktlint (`ktlint_official` style, `android/.editorconfig` exempts `@Composable` functions from the lowercase-name rule) and detekt (default ruleset). Run `gradle -p android :app:ktlintFormat` to auto-fix formatting. Pre-existing findings that predate this tooling are grandfathered in `android/app/detekt-baseline.xml` — new code must not add to it; don't regenerate the baseline to silence a new finding without asking.

**Firmware** (`scripts/check-firmware.sh` runs the host tests below, then requires an activated ESP-IDF environment matching the version pinned in `firmware/IDF_VERSION`):
```sh
idf.py -C firmware set-target esp32
idf.py -C firmware build
```
Host-only C tests (no ESP-IDF needed) via `scripts/check-firmware-host.sh` — compiles `tests/host/*.c` against `firmware/main/*.c` with `-std=c11 -Wall -Wextra -Werror -Wpedantic` and runs the resulting binaries directly.

**Building the Rust→Android `.so`** (`scripts/build-android-rust.sh`): requires `ANDROID_NDK_HOME` set (CI pins NDK 27.2.12479018), targets `aarch64-linux-android` API 26, copies the result to `android/app/src/main/jniLibs/arm64-v8a/libsilent_caption_jni.so`.

## SC-XXX traceability (read `docs/TRACEABILITY.md` for full detail)

- Work is tracked as numbered `SC-XXX` tasks in `docs/SILENT_CAPTION_V01_TODO.md`, grouped into phases each ending in a gate (`SC-G0`, `SC-G1`, …). Complete tasks in dependency order unless a task is explicitly parallel-safe.
- **A TODO checkbox may be marked complete only when the repo contains or references evidence for it — code existing is not completion.** Evidence requirements differ by class (host software, Android software, firmware software, protocol, hardware, performance); hardware/benchmark evidence needs exact device/firmware identity and a reproducible procedure.
- Evidence docs live under `docs/evidence/` (e.g. `SC_280_ANDROID_SESSION_ARCHITECTURE.md`), named descriptively, identifying the relevant `SC-*` IDs near the top.
- Reference the relevant `SC-XXX`/`SC-Gn` ID in tests, scripts, evidence docs, and meaningful implementation comments where it aids traceability — not on every line.
- **Never hide unsupported, untested, disconnected, corrupt, or degraded states behind a success-looking fallback.** Keep those states explicit in code, evidence, and UI.
- Keep Android/iOS-specific APIs out of the transport-neutral Rust ASR/caption core (`core/`).
- Do not commit copyrighted media into `tests/fixtures/` — see `tests/fixtures/README.md`.
- The `repository-policy` CI job rejects committed build artifacts (`target/`, `build/`, `.apk`, `.aab`, `.elf`, `.bin`, `.so`) and trailing whitespace — don't stage generated output.

## Commit conventions

Conventional Commits, observed consistently in history: `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, optionally scoped (e.g. `fix(rust):`, `test(jni):`). Most commits touching a tracked task reference its `SC-XXX` ID directly in the subject line, e.g. `feat: add SC-281 foreground session service`.
