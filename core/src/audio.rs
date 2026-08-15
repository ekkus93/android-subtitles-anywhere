//! Bounded PCM buffering and canonical 16 kHz mono conversion.

use std::collections::VecDeque;

/// Canonical ASR sample rate.
pub const CANONICAL_SAMPLE_RATE_HZ: u32 = 16_000;

/// Policy applied when a bounded audio buffer cannot accept all new samples.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum OverflowPolicy {
    /// Preserve already-buffered audio and discard newest incoming samples.
    DropNewest,
    /// Preserve newest audio by evicting the oldest buffered samples.
    DropOldest,
}

/// Metrics for a bounded audio buffer.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct BufferMetrics {
    pub pushed_samples: u64,
    pub popped_samples: u64,
    pub dropped_samples: u64,
    pub underrun_samples: u64,
    pub high_water_samples: usize,
}

/// Fixed-capacity PCM ring buffer.
pub struct AudioRingBuffer {
    samples: VecDeque<i16>,
    capacity: usize,
    policy: OverflowPolicy,
    metrics: BufferMetrics,
}

impl AudioRingBuffer {
    /// Creates a buffer with a fixed sample capacity.
    ///
    /// # Panics
    ///
    /// Panics when `capacity` is zero.
    #[must_use]
    pub fn new(capacity: usize, policy: OverflowPolicy) -> Self {
        assert!(capacity > 0, "audio buffer capacity must be nonzero");
        Self {
            samples: VecDeque::with_capacity(capacity),
            capacity,
            policy,
            metrics: BufferMetrics::default(),
        }
    }

    /// Pushes samples, applying the configured overflow policy.
    pub fn push(&mut self, input: &[i16]) {
        self.metrics.pushed_samples = self
            .metrics
            .pushed_samples
            .saturating_add(u64::try_from(input.len()).unwrap_or(u64::MAX));
        for &sample in input {
            if self.samples.len() == self.capacity {
                self.metrics.dropped_samples = self.metrics.dropped_samples.saturating_add(1);
                match self.policy {
                    OverflowPolicy::DropNewest => continue,
                    OverflowPolicy::DropOldest => {
                        self.samples.pop_front();
                    }
                }
            }
            self.samples.push_back(sample);
        }
        self.metrics.high_water_samples = self.metrics.high_water_samples.max(self.samples.len());
    }

    /// Pops up to `output.len()` samples and returns the number written.
    pub fn pop_into(&mut self, output: &mut [i16]) -> usize {
        let requested = output.len();
        let mut written = 0;
        for slot in output {
            let Some(sample) = self.samples.pop_front() else {
                break;
            };
            *slot = sample;
            written += 1;
        }
        self.metrics.popped_samples = self
            .metrics
            .popped_samples
            .saturating_add(u64::try_from(written).unwrap_or(u64::MAX));
        self.metrics.underrun_samples = self.metrics.underrun_samples.saturating_add(
            u64::try_from(requested.saturating_sub(written)).unwrap_or(u64::MAX),
        );
        written
    }

    /// Returns the number of buffered samples.
    #[must_use]
    pub fn len(&self) -> usize {
        self.samples.len()
    }

    /// Returns whether no samples are buffered.
    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.samples.is_empty()
    }

    /// Returns current buffer metrics.
    #[must_use]
    pub const fn metrics(&self) -> BufferMetrics {
        self.metrics
    }
}

/// Converts little-endian signed 16-bit PCM bytes to samples.
///
/// # Errors
///
/// Returns `PcmError::IncompleteSample` for an odd byte count.
pub fn pcm_s16le(bytes: &[u8]) -> Result<Vec<i16>, PcmError> {
    if !bytes.len().is_multiple_of(2) {
        return Err(PcmError::IncompleteSample);
    }
    Ok(bytes
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect())
}

/// PCM conversion errors.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PcmError {
    IncompleteSample,
    IncompleteStereoFrame,
    InvalidSampleRate,
}

/// Downmixes interleaved signed 16-bit stereo PCM to mono.
///
/// Averaging is performed in i32 so adding two i16 samples cannot overflow.
///
/// # Errors
///
/// Returns `PcmError::IncompleteStereoFrame` for an odd sample count.
pub fn stereo_to_mono(samples: &[i16]) -> Result<Vec<i16>, PcmError> {
    if !samples.len().is_multiple_of(2) {
        return Err(PcmError::IncompleteStereoFrame);
    }
    Ok(samples
        .chunks_exact(2)
        .map(|pair| {
            let sum = i32::from(pair[0]) + i32::from(pair[1]);
            i16::try_from(sum / 2).expect("average of two i16 values remains within i16")
        })
        .collect())
}

/// Resamples mono PCM using deterministic linear interpolation.
///
/// This initial resampler prioritizes portability and deterministic behavior. Higher-quality
/// resampling can replace it behind the same interface after measured ASR evaluation.
///
/// # Errors
///
/// Returns `PcmError::InvalidSampleRate` when either rate is zero.
pub fn resample_mono_linear(
    input: &[i16],
    input_rate_hz: u32,
    output_rate_hz: u32,
) -> Result<Vec<i16>, PcmError> {
    if input_rate_hz == 0 || output_rate_hz == 0 {
        return Err(PcmError::InvalidSampleRate);
    }
    if input.is_empty() || input_rate_hz == output_rate_hz {
        return Ok(input.to_vec());
    }

    let output_len_u128 = (input.len() as u128 * u128::from(output_rate_hz))
        .div_ceil(u128::from(input_rate_hz));
    let output_len = usize::try_from(output_len_u128).unwrap_or(usize::MAX);
    let mut output = Vec::with_capacity(output_len.min(input.len().saturating_mul(8)));

    for out_index in 0..output_len {
        let position_num = out_index as u128 * u128::from(input_rate_hz);
        let left_u128 = position_num / u128::from(output_rate_hz);
        let fraction_num = position_num % u128::from(output_rate_hz);
        let left = usize::try_from(left_u128).unwrap_or(usize::MAX);
        if left >= input.len() {
            break;
        }
        let right = left.saturating_add(1).min(input.len() - 1);
        let denominator = i128::from(output_rate_hz);
        let fraction = i128::try_from(fraction_num).expect("u32-derived fraction fits i128");
        let a = i128::from(input[left]);
        let b = i128::from(input[right]);
        let interpolated = (a * (denominator - fraction) + b * fraction) / denominator;
        output.push(i16::try_from(interpolated).expect("interpolation remains within i16"));
    }
    Ok(output)
}

/// Converts signed 16-bit PCM into canonical 16 kHz mono samples.
///
/// # Errors
///
/// Returns a [`PcmError`] for invalid sample rate or incomplete stereo data.
pub fn canonicalize_s16(
    samples: &[i16],
    channels: u8,
    sample_rate_hz: u32,
) -> Result<Vec<i16>, PcmError> {
    let mono = match channels {
        1 => samples.to_vec(),
        2 => stereo_to_mono(samples)?,
        _ => return Err(PcmError::IncompleteStereoFrame),
    };
    resample_mono_linear(&mono, sample_rate_hz, CANONICAL_SAMPLE_RATE_HZ)
}
