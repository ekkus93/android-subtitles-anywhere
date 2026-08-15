# SC-120 Android USB transport — software evidence

This pass establishes the hardware-independent Android USB transport seams and tests. It does not claim physical CP210x/Android acceptance.

## Implemented software-verifiable scope

- Characterized prototype identity constants for Silicon Labs CP210x VID/PID `10c4:ea60` plus an explicit device identity abstraction suitable for user-confirmed fallback.
- Explicit transport states for detached, permission-required, ready, unsupported/no-device, permission denial, open failure, disconnect and I/O failure.
- Blocking byte-transport abstraction isolated behind a worker thread so USB I/O never needs to run on the Compose/UI thread.
- Bounded 4128-byte protocol accumulation (`32 + 4096`) with arbitrary partial-read handling, SCAP resynchronization and oversized-length rejection.
- Session controller with cancellation/close semantics, bounded 4096-byte read chunks, finite read/write timeouts and false-ready prevention after disconnect/error.
- `ProtocolFrameSink` boundary intended to be implemented by the Rust mobile/JNI bridge. Complete frames are delivered to this boundary; parser/session state is reset on disconnect.
- JVM fake-transport tests for fragmented frames, malformed/noise resynchronization, bounded oversized input, clean detach and disconnect state transitions.

## Deliberately hardware/platform-dependent work still open

SC-121 through SC-124 are not fully closable until the Android platform adapter is exercised against a real device. The next implementation layer must bind these seams to `UsbManager`, the Android permission broadcast flow and a CP210x serial driver, while ensuring DTR/RTS behavior does not reset or enter the ESP32 bootloader.

SC-126's architectural boundary is established, but the actual JNI/UniFFI Rust invocation belongs with the Rust mobile boundary work and must be validated on Android before Gate SC-G7 closes.

## Gate conclusion

Gate SC-G7 remains open. The software seams prevent hardware concerns from contaminating protocol/session logic and provide deterministic JVM coverage, but real attach/detach/permission/CP210x behavior is required for acceptance.
