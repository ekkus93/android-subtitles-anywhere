# Silent Caption

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

Install/activate the repository-pinned ESP-IDF version once SC-003 records it, then:

```sh
idf.py -C firmware set-target esp32
idf.py -C firmware build
```

### Android

The exact Gradle commands will be added when SC-004 creates and pins the Android project.

## Engineering rules

- Complete `docs/SILENT_CAPTION_V01_TODO.md` in dependency order unless explicitly parallel-safe.
- Do not close hardware-dependent tasks without recorded device/firmware evidence.
- Do not hide unsupported or degraded states behind success-looking fallbacks.
- Keep Android/iOS APIs outside the transport-neutral Rust ASR/caption core.
- Do not commit copyrighted media fixtures without redistribution rights; see `tests/fixtures/README.md`.
