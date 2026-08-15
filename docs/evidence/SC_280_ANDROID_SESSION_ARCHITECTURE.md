# SC-280 Android service/session architecture — software evidence

This evidence covers the software-verifiable portion of SC-281 through SC-287.

## Architecture

`CaptionSessionStateMachine` is the single portable Android-side lifecycle authority. Its explicit phases are `Unavailable`, `Ready`, `Starting`, `Listening`, `Reconnecting`, `Stopping`, and `Error`. A monotonically increasing generation is assigned to each accepted start and is used to reject events from prior sessions.

`CaptionSessionCoordinator` requires USB, Rust core, Bluetooth-route readiness, and ASR readiness before it reports Ready or starts a session. Start and stop are idempotent. A partial start is rolled back in reverse order. Stop tears down ASR, Bluetooth route, USB, then Rust state.

`CaptionSessionService` supplies the Android foreground-service boundary. It creates a low-importance notification channel, enters the foreground synchronously when started, uses `START_NOT_STICKY` so Android does not fabricate a caption session after process death, and explicitly stops when its task is removed.

## Requirement mapping

- SC-281: foreground service, notification channel, ongoing notification, and manifest declaration are implemented.
- SC-282: all required lifecycle states are explicit and unit-testable.
- SC-283: dependency coordination contract covers USB, Rust core, Bluetooth-route readiness, and ASR.
- SC-284: duplicate start/stop calls are no-ops at the state/coordinator layer.
- SC-285: foreground service is independent of Activity rotation/backgrounding; process recreation cannot silently resume a session because the service is `START_NOT_STICKY`; task removal explicitly stops the service. Screen-off behavior does not alter the state machine.
- SC-286: generation filtering prevents prior-session caption/events from being accepted after restart. Reconnect within the same session retains its generation.
- SC-287: host unit tests cover state transitions, idempotency, dependency readiness, partial-start rollback, reconnect, and stale-generation filtering.

## Remaining integration work

The coordinator deliberately depends on small `SessionDependency` interfaces. Concrete adapters from the existing USB controller and Rust JNI boundary, plus Bluetooth route and concrete ASR runtime adapters, should be wired when those Android runtime layers are composed. This avoids embedding hardware or JNI calls in the state machine and keeps lifecycle behavior host-testable.

Physical-device acceptance remains necessary for Android foreground-service behavior across OEM background restrictions, screen-off, USB detach/reconnect, and actual Bluetooth route changes. Those observations are not claimed by this software evidence.
