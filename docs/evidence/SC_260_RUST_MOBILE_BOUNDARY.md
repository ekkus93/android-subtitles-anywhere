# SC-260 Rust mobile boundary — software evidence

## Audit result

SC-126 had already established a concrete JNI `cdylib`, opaque handle store, protocol-v1 validation, Android loader, and typed protocol event encoding. SC-260 extends that foundation instead of introducing a second FFI stack.

## SC-261 — mechanism, ownership, threading

The selected mechanism is JNI through the `silent-caption-jni` Rust `cdylib`. Android owns an opaque positive 64-bit handle. Rust owns all mutable portable state behind that handle. A process-global `Mutex<BoundaryStore>` serializes FFI entry, preventing simultaneous mutation of one boundary without exposing Rust pointers to Kotlin. Handles are monotonically allocated and destroyed explicitly; use-after-destroy is rejected.

The FFI is synchronous and non-reentrant. Long-running inference must not execute while holding the global handle-store mutex; later engine integration should enqueue/copy only bounded input at this boundary and perform inference outside the store lock.

## SC-262 — lifecycle-safe creation/destruction

`create_boundary`, `reset_boundary`, and `destroy_boundary` own the lifecycle. Reset replaces all protocol/session/caption/backend state. Destroy removes the owned boundary, and repeated destroy/use-after-destroy is rejected. Android `NativeRustProtocolApi` implements `Closeable` and zeros its handle before native destruction.

## SC-263 — session and backend/model selection

The boundary now has explicit start and stop semantics. A second start while active returns stable `session_active`. Stop is idempotent for a live handle and clears protocol and caption session state. Backend selection is represented by stable portable choices: Zipformer, Whisper Tiny multilingual, and Whisper Base multilingual. Backend changes are rejected while a session is active.

This is the control-plane contract; actual model installation/activation remains SC-320.

## SC-264 — bounded input

Protocol input remains a borrowed Rust byte slice after JNI converts the Java byte array. Protocol-v1 validation enforces the existing maximum frame/payload bounds before typed events are emitted. The boundary does not retain raw frame input. Caption events are borrowed on entry. No unbounded history is retained by the mobile boundary.

JNI necessarily performs one bounded Java-byte-array to Rust `Vec<u8>` conversion with the current `jni` API. This is an explicit bounded-copy tradeoff, not an unbounded queue.

## SC-265 — event stream

The existing FFI event envelope exposes hello, audio-format, audio-data, status, diagnostics, protocol error, and sequence anomaly events. SC-260 additionally routes portable SC-240 caption replacement/finalization and discontinuity semantics through the Rust boundary for host-verifiable integration. Android presentation wiring for caption events belongs to the service/session/UI blocks.

## SC-266 — stable errors

`FfiError` has stable numeric codes and stable snake-case names independent of Rust `Debug` formatting:

- 1 `invalid_handle`
- 2 `integrity`
- 3 `stale_session`
- 4 `protocol_error`
- 5 `session_active`

Android decodes the same protocol error codes and now recognizes `session_active`.

## SC-267 — stress tests

`core-jni/tests/ffi_boundary.rs` covers:

- create/use/destroy/use-after-destroy;
- start/duplicate-start/stop/restart/reset;
- backend selection before and during sessions;
- partial caption replacement, discontinuity clearing, and final commit through the FFI-owned state;
- stable error code/name mapping;
- zero-session rejection;
- 1,000 create/select/start/caption/stop/destroy cycles;
- monotonic handles and state isolation across cycles.

## Software closure and SC-G14

SC-261 through SC-267 are software-implemented at the portable/JNI contract level, subject to `scripts/check-rust.sh` and Android checks passing. The boundary is documented, ownership is explicit, repeated lifecycle behavior is host-tested, and cancellation is represented by synchronous stop/reset state invalidation.

SC-G14 should be considered software-closed only after those repository checks are green. Physical Android soak/leak validation may add evidence later but is not required to prove the host-verifiable ownership and state-machine contract.
