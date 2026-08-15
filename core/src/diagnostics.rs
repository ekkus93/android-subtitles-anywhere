//! Transport-neutral audio and protocol diagnostics.

/// Saturating counters for one core session.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct Diagnostics {
    pub packets_received: u64,
    pub sequence_gaps: u64,
    pub duplicate_packets: u64,
    pub buffer_overruns: u64,
    pub buffer_underrun_samples: u64,
    pub audio_samples_received: u64,
    pub audio_samples_emitted: u64,
    pub peak_abs_sample: u16,
    pub last_source_timestamp_ms: Option<u32>,
}

impl Diagnostics {
    /// Records input PCM and its source timestamp.
    pub fn observe_audio(&mut self, samples: &[i16], timestamp_ms: u32) {
        self.audio_samples_received = self
            .audio_samples_received
            .saturating_add(u64::try_from(samples.len()).unwrap_or(u64::MAX));
        self.last_source_timestamp_ms = Some(timestamp_ms);
        for &sample in samples {
            self.peak_abs_sample = self.peak_abs_sample.max(sample.unsigned_abs());
        }
    }

    /// Records canonical samples emitted toward ASR.
    pub fn observe_emitted(&mut self, sample_count: usize) {
        self.audio_samples_emitted = self
            .audio_samples_emitted
            .saturating_add(u64::try_from(sample_count).unwrap_or(u64::MAX));
    }
}
