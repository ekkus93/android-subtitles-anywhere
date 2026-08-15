//! Shared, platform-neutral Silent Caption core.
//!
//! Android and future iOS clients consume this crate through an FFI boundary.
//! Platform-specific USB, UI, and lifecycle APIs do not belong here.

pub mod asr;
pub mod audio;
pub mod captions;
pub mod diagnostics;
pub mod mobile;
pub mod models {}
pub mod protocol;
pub mod session {}
pub mod transport;
pub mod vad {}
pub mod whisper;
pub mod zipformer;

/// Core semantic version exposed to platform adapters and diagnostics.
pub const CORE_VERSION: &str = env!("CARGO_PKG_VERSION");

#[cfg(test)]
mod tests {
    use super::CORE_VERSION;

    #[test]
    fn core_version_matches_package_version() {
        assert_eq!(CORE_VERSION, env!("CARGO_PKG_VERSION"));
    }
}
