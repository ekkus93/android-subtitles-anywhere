# Android Client

This directory owns the Kotlin/Jetpack Compose Android application and Android-specific integration: USB host transport, permissions, foreground-service lifecycle, overlay presentation, and platform UI.

Speech recognition, caption stabilization, portable session logic, and transport-neutral protocol parsing belong in the shared Rust core.

## SC-004 pinned baseline

- Android Gradle Plugin: `9.3.0`
- Gradle: `9.5.0` (wrapper is generated/pinned as part of the build bootstrap)
- JDK: `17`
- Kotlin: AGP 9.3 built-in Kotlin, with Compose compiler plugin `2.2.10`
- `compileSdk`: `37`
- `targetSdk`: `37`
- `minSdk`: `26` (Android 8.0)
- Compose BOM: `2026.07.00`
- Material 3: `1.4.0`

`minSdk 26` is a deliberate project support floor rather than a Compose requirement. It gives the first hardware-focused release a bounded Android compatibility matrix while retaining support back to Android 8.0. Lowering it later requires explicit USB/Bluetooth/service/FFI compatibility testing.

`compileSdk` and `targetSdk` are pinned to API 37 so the application is developed against the current Android 17 API surface. SDK changes are intentional repository changes, not implicit local-environment upgrades.

## Build

From this directory, after the Gradle wrapper is present:

```sh
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
```

SC-005 will make these commands CI gates. Physical-device behavior is not implied by a successful host build.
