# iOS Client

The iOS client is intentionally deferred beyond the Android-first v0.1 proof. This directory reserves the native Swift/SwiftUI platform boundary.

The iOS client must reuse the shared Rust ASR/caption core. iOS-specific transport, lifecycle, permissions, and presentation code remain native and must not leak into the portable core.
