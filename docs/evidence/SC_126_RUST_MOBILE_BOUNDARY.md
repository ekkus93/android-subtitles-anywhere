# SC-126 Rust mobile protocol boundary evidence

## Software-verifiable result

The Rust core now owns validation and semantic interpretation of complete Protocol v1 candidate frames through `MobileProtocolBoundary`.

The boundary calls the existing Rust `protocol::decode`, so magic/version, message type, reserved flags, message-specific payload lengths, maximum payload and CRC integrity remain Rust-owned invariants. Session-scoped inbound messages are checked against `PeerState` before becoming mobile events.

Typed mobile events cover HELLO/boot identity, audio format, audio data, status, diagnostics and device error messages. Sequence gaps, duplicates and resets are explicit events rather than silent transport anomalies. A changed HELLO boot ID invalidates the previous session and sequence state. Explicit reset forgets all peer/session state.

Rust host tests cover boot change invalidation, valid audio typing, corrupt CRC rejection, stale-session rejection, sequence gaps/duplicates/resets and explicit reset.

On Android, `RustProtocolFrameSink` is the sole semantic bridge expected by `UsbSessionController`. Kotlin forwards complete candidate frame bytes to a `RustProtocolApi` and consumes accepted typed events or explicit rejection diagnostics. JVM tests use a fake Rust API, proving event forwarding and disconnect/reset behavior without loading a native library.

## Native binding boundary

This pass deliberately separates the stable cross-language API from a concrete JNI code generator/runtime. `RustProtocolApi` is the Android-side port; `MobileProtocolBoundary` is the Rust-side implementation target. A concrete JNI/UniFFI adapter must preserve these semantics and is required before physical Gate SC-G7 closure.

This separation avoids making JVM unit tests depend on an Android native `.so`, and it keeps protocol validation out of Kotlin.

## Gate status

SC-126's software architecture and host-test coverage are implemented. Gate SC-G7 remains open until the concrete native adapter is loaded on Android and real CP210x traffic reaches Rust validation through it.
