---
name: verify
description: Run the same checks CI runs for a given component of this monorepo (Rust core, Android, firmware, or firmware host tests). Use when the user asks to "run the checks", "verify my changes", "make sure this passes CI", or before considering a change in core/, core-jni/, android/, or firmware/ done.
---

Run the check script(s) matching what `.github/workflows/ci.yml` runs, for whichever component(s) the current changes touch. Use `git status`/`git diff --stat` to figure out which components are dirty if it isn't obvious from the conversation; if changes span multiple components, run all the relevant scripts.

Component → script mapping:

- **Rust core / JNI crate** (`core/`, `core-jni/`): `bash scripts/check-rust.sh` — runs `cargo fmt --check`, `cargo clippy -D warnings` (both crates deny `clippy::pedantic`), and `cargo test`.
- **Android** (`android/`): `bash scripts/check-android.sh` — runs `gradle -p android --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:detekt`. Respects `GRADLE_CMD` if the caller needs a non-default gradle binary. Do not substitute `./gradlew` — the wrapper script isn't committed yet (see CLAUDE.md). If `:app:ktlintCheck` fails on formatting-only violations, `gradle -p android :app:ktlintFormat` auto-fixes them safely; if `:app:detekt` fails, do not regenerate `android/app/detekt-baseline.xml` to silence it — fix the finding or ask the user before touching the baseline.
- **Firmware, host-only** (`firmware/`, `tests/host/`, no ESP-IDF required): `bash scripts/check-firmware-host.sh` — compiles and runs the C unit tests in `tests/host/` against `firmware/main/*.c`.
- **Firmware, full build** (requires an activated ESP-IDF environment matching `firmware/IDF_VERSION`): `bash scripts/check-firmware.sh` — runs the host tests first, then `idf.py -C firmware set-target esp32 && idf.py -C firmware build`. If `idf.py` isn't on PATH, report that ESP-IDF needs to be activated (`scripts/install-esp-idf.sh`, then source its `export.sh`) rather than silently skipping — don't claim firmware passed when only the host tests ran.

After running, report pass/fail per script plainly, and paste the actual failing output (not a paraphrase) for anything that failed. Don't mark related `docs/SILENT_CAPTION_V01_TODO.md` checkboxes or evidence docs as complete based on a passing run alone — that still needs the evidence described in `docs/TRACEABILITY.md` (see the `/sc-evidence` skill).
