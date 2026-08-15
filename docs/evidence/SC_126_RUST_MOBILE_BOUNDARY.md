# SC-126 Rust mobile protocol boundary evidence

## Software-verifiable result

The Rust core owns validation and semantic interpretation of complete Protocol v1 candidate frames through `MobileProtocolBoundary`.

The boundary calls the existing Rust `protocol::decode`, so magic/version, message type, reserved flags, message-specific payload lengths, maximum payload and CRC integrity remain Rust-owned invariants. Session-scoped inbound messages are checked against `PeerState` before becoming mobile events.

Typed mobile events cover HELLO/boot identity, audio format, audio data, status, diagnostics and device error messages. Sequence gaps, duplicates and resets are explicit events rather than silent transport anomalies. A changed HELLO boot ID invalidates the previous session and sequence state. Explicit reset forgets all peer/session state.

## Concrete JNI binding

`core-jni` is an isolated `cdylib` crate. It owns opaque native handles for `MobileProtocolBoundary` instances and exposes JNI entry points for create, destroy, reset, start-session and accept-frame operations. Destroyed/unknown handles are rejected instead of dereferenced.

The JNI result is a bounded binary envelope containing a status byte and typed event records. Kotlin's `NativeRustProtocolApi` decodes that envelope into the existing `RustProtocolResult`/`RustProtocolEvent` API. Native errors distinguish invalid handles, integrity failures, stale sessions and other protocol failures. Kotlin rejects truncated, unknown or trailing native data rather than accepting a partial result.

The JNI crate is separate from `silent-caption-core` so the platform-neutral core can retain its `unsafe_code = "forbid"` policy. JNI-specific unsafe ABI requirements remain confined to the adapter crate.

Rust host tests cover opaque-handle lifecycle/use-after-destroy, session/reset operations and invalid session creation in addition to the core mobile-boundary tests.

## Android build integration

`jni = 0.21.1` is pinned in the JNI crate. CI installs pinned Android NDK `27.2.12479018`, adds the `aarch64-linux-android` Rust target, cross-builds `libsilent_caption_jni.so` for API 26/arm64-v8a, copies it into the generated `jniLibs` staging directory and then builds/tests/lints the Android application. Generated `.so`/`jniLibs` products are ignored and repository policy rejects committed `.so` files.

The ordinary JVM tests continue to use the `RustProtocolApi` seam and therefore do not require loading an Android native library on the host JVM.

## Gate status

SC-126 is software-complete once Rust and Android CI pass this concrete JNI/cross-build implementation. Gate SC-G7 remains open for physical acceptance: a real Android device must load the packaged arm64 library and real CP210x traffic must traverse USB -> JNI -> Rust validation, including detach/reconnect behavior.
