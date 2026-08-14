# Silent Caption

[![CI/CD](https://github.com/ekkus93/android-subtitles-anywhere/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/ekkus93/android-subtitles-anywhere/actions/workflows/ci.yml)

Silent Caption is an Android-first, local speech-to-text layer for media audio. The prototype routes phone media to an ESP32-WROOM-32 A2DP sink, returns digital audio over the board's onboard USB-UART interface, and captions it through a portable Rust core.

The authoritative v0.1 architecture and execution plan are:

- `docs/SILENT_CAPTION_V01_SPEC.md`
- `docs/SILENT_CAPTION_V01_TODO.md`

## Repository layout

- `android/` — Kotlin/Jetpack Compose Android client and platform integration.
- `core/` — platform-neutral Rust protocol/audio/ASR/caption/session core.
- `firmware/` — ESP-IDF firmware for the ESP32-WROOM-32 prototype.
- `ios/` — reserved Swift/SwiftUI client boundary for future iOS work.
- `protocol/` — versioned dongle/mobile wire protocol and test vectors.
- `tests/fixtures/` — redistribution-safe deterministic fixtures.
- `docs/` — product specification, TODO, evidence, and engineering documentation.

## Baseline development commands

### Rust

The repository pins its Rust toolchain in `rust-toolchain.toml`.

```sh
cargo fmt --manifest-path core/Cargo.toml --all -- --check
cargo clippy --manifest-path core/Cargo.toml --all-targets --all-features -- -D warnings
cargo test --manifest-path core/Cargo.toml --all-features
```

### ESP-IDF firmware

ESP-IDF is pinned in `firmware/IDF_VERSION`; setup details and the upgrade policy are in `docs/ESP_IDF_TOOLCHAIN.md`.

```sh
./scripts/install-esp-idf.sh
. "$HOME/.espressif/frameworks/esp-idf-v5.5.5/export.sh"
idf.py -C firmware set-target esp32
idf.py -C firmware build
```

### Android

The exact Gradle commands will be added when SC-004 creates and pins the Android project.

## CI/CD and release assets

GitHub Actions runs baseline validation for pushes to `master`, pull requests targeting `master`, and manual workflow dispatches. Normal CI runs do not upload workflow artifacts.

Release assets are created only for version tags matching the `v*` trigger and validated as semantic-version-style tags such as `v0.1.0`. A tagged run publishes a GitHub Release containing a source archive and `SHA256SUMS`. Android APK/AAB and ESP32 firmware release assets will be added to this tag-only release job when those build targets are established.

## Engineering rules

- Complete `docs/SILENT_CAPTION_V01_TODO.md` in dependency order unless explicitly parallel-safe.
- Do not close hardware-dependent tasks without recorded device/firmware evidence.
- Do not hide unsupported or degraded states behind success-looking fallbacks.
- Keep Android/iOS APIs outside the transport-neutral Rust ASR/caption core.
- Do not commit copyrighted media fixtures without redistribution rights; see `tests/fixtures/README.md`.
