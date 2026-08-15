//! Platform-neutral caption event contract.

/// Normalized caption emitted by every ASR backend.
#[derive(Clone, Debug, PartialEq)]
pub struct CaptionEvent {
    pub session_id: u64,
    pub sequence: u64,
    pub text: String,
    pub is_final: bool,
    pub source_start_ms: u64,
    pub source_end_ms: u64,
    pub confidence: Option<f32>,
    pub backend_id: String,
}

impl CaptionEvent {
    /// Returns whether the event has a valid source-time interval.
    #[must_use]
    pub const fn has_valid_time_range(&self) -> bool {
        self.source_start_ms <= self.source_end_ms
    }
}
