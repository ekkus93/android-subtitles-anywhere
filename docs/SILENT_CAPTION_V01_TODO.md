# Silent Caption v0.1 — Master TODO

> Execution rule: check an item only when its acceptance condition is demonstrated by the applicable automated or physical evidence. Physical-device gates remain open until physically exercised.

## SC-280 — Android service/session architecture

- [x] **SC-281** Implement foreground service for active caption sessions with required notification/channel behavior.
- [x] **SC-282** Define lifecycle state machine: unavailable, ready, starting, listening, reconnecting, stopping, error.
- [x] **SC-283** Coordinate USB transport, Rust core, Bluetooth-route readiness, and ASR backend.
- [x] **SC-284** Implement idempotent Start Listening / Stop Listening.
- [x] **SC-285** Handle app backgrounding, process recreation, screen off, rotation, and task removal explicitly.
- [x] **SC-286** Prevent stale captions/events from prior sessions after restart/reconnect.
- [x] **SC-287** Add lifecycle/state-machine tests.

**Gate SC-G15 (software): PASS.** CI/CD run #169 passed the Android build, JVM tests, lint, ktlint, detekt, and Rust JNI cross-build. Physical-device lifecycle/USB/Bluetooth acceptance remains separate and open until exercised on hardware.

---

## SC-300 — Android permissions, onboarding, and setup

- [ ] **SC-301** Implement first-run explanation of the dongle/audio loop and privacy model.
- [ ] **SC-302** Implement overlay permission flow only when Floating/Compact mode requires it.
- [ ] **SC-303** Implement notification/foreground-service permission handling for applicable Android versions.
- [ ] **SC-304** Implement USB permission/setup flow.
- [ ] **SC-305** Implement Bluetooth setup guidance and explicit route-readiness checks within Android platform limits.
- [ ] **SC-306** Build setup checklist with distinct USB, Bluetooth, model, and overlay states.
- [ ] **SC-307** Never claim media routing is correct solely because Bluetooth is connected.
- [ ] **SC-308** Add denial/retry/settings-return tests for every permission.

**Gate SC-G16:** a new user can reach a truthful Ready state without granting unrelated permissions.

---

## SC-320 — Model management

- [ ] **SC-321** Define signed/HTTPS model manifest containing backend/model IDs, versions, URLs, hashes, sizes, licenses, language metadata, and compatibility constraints.
- [ ] **SC-322** Implement download with temporary files, cancellation, progress, retry, and atomic promotion.
- [ ] **SC-323** Verify cryptographic hash before activation.
- [ ] **SC-324** Reject corrupt/incomplete/wrong-version models without deleting the last known-good model.
- [ ] **SC-325** Implement storage-space preflight and actionable insufficient-space errors.
- [ ] **SC-326** Implement model deletion and active-model protection.
