//! Transport-neutral session/audio input contracts.

use crate::audio::PcmError;

/// Description of PCM delivered by a transport after protocol validation.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PcmFormat {
    pub sample_rate_hz: u32,
    pub channels: u8,
}

/// One validated PCM input block.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct AudioInput<'a> {
    pub session_id: u64,
    pub sequence: u32,
    pub timestamp_ms: u32,
    pub format: PcmFormat,
    pub samples: &'a [i16],
    pub discontinuity: bool,
}

/// Errors exposed by the transport-neutral audio sink contract.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AudioInputError {
    StaleSession,
    InvalidFormat(PcmError),
    NotStarted,
}

/// Consumer contract for validated audio independent of USB, Bluetooth, Android, or iOS APIs.
pub trait AudioInputSink {
    /// Starts a new logical stream session.
    ///
    /// # Errors
    ///
    /// Returns an error when the session cannot be started.
    fn start_session(&mut self, session_id: u64) -> Result<(), AudioInputError>;

    /// Consumes one validated PCM block.
    ///
    /// # Errors
    ///
    /// Returns an error for stale sessions, invalid formats, or invalid lifecycle state.
    fn push_audio(&mut self, input: AudioInput<'_>) -> Result<(), AudioInputError>;

    /// Stops the named session. Repeated stops must be safe.
    fn stop_session(&mut self, session_id: u64);
}
