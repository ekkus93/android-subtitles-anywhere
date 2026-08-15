# SC-G3 Rust protocol and audio foundation evidence

Gate: **SC-G3**

## Implemented scope

The platform-neutral Rust core implements:

- bounded protocol-v1 parser and serializer;
- round-trip coverage for every protocol-v1 message type;
- malformed, truncated, oversized, unsupported-version, integrity, and reserved-field rejection;
- bounded arbitrary-input parser exercise;
- sequence gap, duplicate, reboot, stale-session, and discontinuity state;
- fixed-capacity PCM buffering with explicit drop-oldest/drop-newest policies and metrics;
- signed 16-bit little-endian PCM decoding;
- overflow-safe stereo-to-mono conversion;
- deterministic linear resampling to the canonical 16 kHz mono ASR representation;
- deterministic audio conversion fixtures;
- transport-neutral audio/session contracts; and
- audio/protocol diagnostic counters.

## Host-test evidence

The implementation is exercised by `core/tests/protocol_v1.rs` and
`core/tests/audio_foundation.rs`. The repository Rust quality gate is
`scripts/check-rust.sh`, which runs formatting, Clippy with warnings denied,
and the Rust test suite using the repository-pinned toolchain.

The final SC-G3 closure is conditioned on that quality gate remaining green on
`master`; no physical ESP32 or Android device is required for SC-G3.

## Boundedness

Protocol payloads have a fixed maximum and parser buffering is bounded. Audio
buffering uses a fixed sample capacity with an explicit overflow policy. The
transport-neutral interfaces do not depend on Android, iOS, USB, Bluetooth, or
ESP-IDF APIs.

## Gate conclusion

**SC-G3: PASS when `scripts/check-rust.sh` is green on the closure commit.**

At that point arbitrary validated protocol audio data can be transformed into a
bounded canonical PCM stream without platform dependencies. Hardware transport
and A2DP validation remain later gates.
