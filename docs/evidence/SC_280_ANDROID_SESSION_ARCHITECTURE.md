# SC-280 Android service/session architecture — software evidence

This evidence closes the software-verifiable portion of SC-281 through SC-287 and records the boundary between host/CI evidence and physical-device acceptance.

## Architecture

`CaptionSessionStateMachine` is the single portable Android-side lifecycle authority. Its explicit phases are `Unavailable`, `Ready`, `Starting`, `Listening`, `Reconnecting`, `Stopping`, and `Error`. A monotonically increasing generation is assigned to each accepted start and is used to reject events from prior sessions.

`CaptionSessionCoordinator` requires USB, Rust core, Bluetooth-route readiness, and ASR readiness before it reports Ready or starts a session. Start and stop are idempotent. Dependencies start in Rust -> USB -> Bluetooth route -> ASR order. A partial start is rolled back in reverse order. Stop tears down ASR -> Bluetooth route -> USB -> Rust state.

Concrete Android-side dependency adapters are provided in `SessionDependencies.kt`:

- `RustCoreSessionDependency` owns Rust per-session start/stop while leaving the process-wide JNI boundary reusable.
- `UsbSessionDependency` requires truthful USB readiness, owns one `UsbSessionController`, and closes that controller deterministically.
- `PlatformSessionDependency` supplies the narrow readiness/start/stop adapter used for Bluetooth-route and ASR runtime composition; it cannot report a successful start while its readiness probe is false.
- `NativeRustProtocolApi.asSessionDependency()` connects the existing JNI API to the coordinator without moving JNI behavior into the state machine.

`CaptionSessionService` supplies the Android foreground-service boundary. It creates a low-importance notification channel, enters the foreground synchronously when started, uses `START_NOT_STICKY` so Android does not fabricate a caption session after process death, and explicitly stops when its task is removed. The manifest declares the foreground-service and microphone permissions required by the service type.

## Requirement mapping

- **SC-281 — software complete:** foreground service, notification channel, ongoing notification, manifest declaration, and required service permissions are implemented.
- **SC-282 — software complete:** all required lifecycle states are explicit and host-testable.
- **SC-283 — software complete:** the coordinator owns the four dependency roles and concrete Rust/JNI and USB adapters; Bluetooth-route and ASR composition use the explicit platform dependency boundary with truthful readiness gating.
- **SC-284 — software complete:** duplicate Start Listening / Stop Listening operations do not create duplicate coordinator sessions or duplicate dependency ownership.
- **SC-285 — software complete:** lifecycle policy is explicit: the foreground service is independent of Activity rotation/backgrounding, screen-off does not mutate portable session state, process recreation does not silently fabricate an active session because the service is `START_NOT_STICKY`, and task removal explicitly stops the service.
- **SC-286 — software complete:** generation filtering prevents prior-session caption/events from being accepted after restart. Reconnect within the same active session retains its generation.
- **SC-287 — software complete:** JVM tests cover lifecycle transitions, dependency readiness, idempotent start/stop, partial-start rollback, reconnect behavior, stale-generation filtering, Rust adapter lifecycle, USB ownership/teardown, and platform readiness rejection.

## Validation

The repository gate is `bash scripts/check-android.sh`, which runs Android assembly, JVM unit tests, Android lint, ktlint, and detekt. During the SC-280 closure loop, CI findings in the new code were corrected rather than suppressed: the session adapters were refactored to satisfy Detekt `ReturnCount`/`ComplexCondition`, and the dependency tests were formatted to satisfy ktlint. No lint baseline, Detekt suppression, or ktlint suppression was introduced for these findings.

A final green execution of `bash scripts/check-android.sh` is the CI confirmation for this closure. Until that run is observed, this document records the implementation/test closure but does not claim a green run that has not been seen.

## SC-G15 closure boundary

**SC-G15 software-verifiable acceptance: complete, pending final CI confirmation.** The architecture has explicit single-session ownership, deterministic dependency teardown/rollback, truthful readiness, reconnect state, and stale-generation rejection. The host-testable behaviors required to prevent duplicate sessions, retained dependency ownership, and false-ready/listening state are covered.

**Physical-device acceptance remains open.** A real Android-device pass is still required before claiming that SC-G15 is proven across OEM foreground-service restrictions, actual screen-off/background behavior, USB detach/reconnect, process/task lifecycle behavior, and real Bluetooth route changes. No physical-device checkbox or observation is inferred from JVM/CI evidence.
