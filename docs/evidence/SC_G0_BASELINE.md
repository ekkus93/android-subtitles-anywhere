# SC-G0 Baseline Verification Evidence

**Tasks:** SC-001 through SC-008, Gate SC-G0  
**Baseline commit validated by CI:** `21b72e655a7120b5cb2f4d1ef67ccbcb354c6418`  
**GitHub Actions run:** `31853015044`  
**Run URL:** `https://github.com/ekkus93/android-subtitles-anywhere/actions/runs/31853015044`  
**Runner:** GitHub-hosted Ubuntu 24.04  
**Result:** PASS

## Evidence

A clean GitHub Actions checkout of `master` executed the repository baseline on 2026-08-15 UTC.

| Area | Evidence | Result |
| --- | --- | --- |
| Repository policy | Build-product rejection, pinned-toolchain validation, whitespace check | PASS |
| Rust | Pinned Rust 1.88.0; `scripts/check-rust.sh` formatting, Clippy with warnings denied, locked unit tests | PASS |
| Android | JDK 17; pinned Gradle 9.5.0; `scripts/check-android.sh` debug build, JVM unit tests, Android Lint | PASS |
| ESP-IDF | Repository installer selected pinned ESP-IDF v5.5.5; `scripts/check-firmware.sh` verified target/version and built firmware for ESP32 | PASS |
| Release policy | Tagged-release job was skipped on the ordinary `master` push; normal CI retained no release artifacts | PASS |

The successful workflow completed all four required non-release jobs: `Repository policy`, `Rust`, `Android`, and `ESP-IDF firmware`.

## Baseline audit

- **SC-001:** repository contains the documented Android, Rust core, firmware, future iOS, protocol, docs, and fixture boundaries.
- **SC-002:** `rust-toolchain.toml` pins Rust 1.88.0; formatting/Clippy/tests are enforced; `core/Cargo.lock` is committed as the dependency lock policy artifact.
- **SC-003:** `firmware/IDF_VERSION` pins ESP-IDF v5.5.5 and the clean CI firmware build passes.
- **SC-004:** Android Kotlin/Compose skeleton and SDK policy are committed and the clean CI Android build/test/lint job passes.
- **SC-005:** `.github/workflows/ci.yml` executes repository policy, Rust, Android, and ESP-IDF baseline jobs.
- **SC-006:** root README and platform/toolchain documentation provide exact baseline commands and bootstrap instructions.
- **SC-007:** `tests/fixtures/README.md` defines redistribution/provenance policy.
- **SC-008:** `docs/TRACEABILITY.md` defines completion and evidence rules.

## Gate decision

**SC-G0: PASS.** A clean checkout can execute all currently applicable non-hardware baseline checks reproducibly. Hardware-dependent behavior remains explicitly outside this gate.
