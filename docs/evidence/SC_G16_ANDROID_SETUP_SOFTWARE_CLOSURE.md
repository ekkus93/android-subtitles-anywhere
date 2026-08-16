# SC-G16 Android setup software closure

## Scope

This record closes the software-verifiable portion of SC-301 through SC-308 and Gate SC-G16. It does not claim that hardware-dependent gates are complete, and it does not claim that a speech model is installed before SC-320 model management exists.

## Task audit

- **SC-301 — PASS (software):** the first-run setup UI explains the Android -> Bluetooth dongle -> USB return-audio loop and states that raw audio/captions remain local by default.
- **SC-302 — PASS (software):** display-over-other-apps permission is optional in Reader mode and becomes required only when Floating/Compact mode is requested.
- **SC-303 — PASS (software):** notification permission is required only on Android versions where the runtime notification permission applies; denial is represented as ActionRequired rather than false readiness.
- **SC-304 — PASS (software):** the setup probe detects an attached USB device, checks UsbManager permission, and exposes an explicit permission request action.
- **SC-305 — PASS (software):** setup provides Bluetooth-settings guidance and checks Android output devices for an A2DP media route.
- **SC-306 — PASS (software):** SetupEvaluator produces distinct USB, Bluetooth media-route, speech-model, notification, and floating-caption checklist items.
- **SC-307 — PASS (software):** Bluetooth readiness is based on the A2DP media-output route; the UI explicitly states that a Bluetooth connection alone is insufficient.
- **SC-308 — PASS (host-testable policy):** JVM tests cover overlay denial/recovery, notification applicability/denial, USB permission denial/retry, Bluetooth route failure, model readiness, and the rule that unrelated overlay permission cannot block Reader-mode readiness.

## Gate assessment

**SC-G16 software gate: PASS.** `SetupEvaluator` permits Ready only when every currently required setup dimension is ready, while leaving overlay permission optional when Floating/Compact mode is not requested. This establishes the gate's central invariant: unrelated permissions cannot be prerequisites for Reader-mode readiness.

The current application intentionally supplies `modelReady = false`. Therefore the shipping UI cannot yet reach overall Ready until SC-320 implements verified model management. This is an intentional dependency, not a false-ready fallback.

## Verification

Primary implementation:

- `android/app/src/main/java/com/ekkus93/silentcaption/MainActivity.kt`
- `android/app/src/main/java/com/ekkus93/silentcaption/setup/AndroidSetupProbe.kt`
- `android/app/src/main/java/com/ekkus93/silentcaption/setup/SetupState.kt`

Host tests:

- `android/app/src/test/java/com/ekkus93/silentcaption/setup/SetupStateTest.kt`

Repository Android quality gate:

```sh
bash scripts/check-android.sh
```

At closure time the preceding CI iterations had already demonstrated successful Kotlin compilation, Detekt, APK assembly, and unit tests; subsequent failures were isolated ktlint formatting findings and were corrected. Final CI status is intentionally not asserted by this evidence record until the external run reports it.

## Deferred/non-claims

- No hardware Bluetooth/A2DP or USB enumeration evidence is manufactured here; those remain governed by their earlier hardware gates.
- No SC-320 model download/install/hash verification is claimed.
- No real-device settings-return/instrumentation evidence is claimed beyond the deterministic setup-policy/JVM coverage above.
