//! Hardware-independent ASR contracts, deterministic injection, and scoring.

use crate::audio::CANONICAL_SAMPLE_RATE_HZ;
use crate::captions::CaptionEvent;

/// Language-selection policy shared by all ASR backends.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum LanguagePolicy {
    Auto,
    Explicit(String),
}

/// Audio chunk supplied to an ASR backend.
#[derive(Clone, Copy, Debug)]
pub struct AudioChunk<'a> {
    pub session_id: u64,
    pub source_start_ms: u64,
    pub samples: &'a [i16],
}

/// Stable ASR failure categories.
#[derive(Clone, Debug, Eq, PartialEq)]
pub enum AsrError {
    NotLoaded,
    AlreadyLoaded,
    InvalidSession,
    InvalidAudio,
    Cancelled,
    Backend(String),
}

/// Backend-neutral ASR lifecycle and streaming interface.
pub trait AsrBackend {
    /// Stable backend identifier used in diagnostics and caption events.
    fn id(&self) -> &str;

    /// Loads backend resources.
    ///
    /// # Errors
    /// Returns an error when resources cannot be loaded or are already loaded.
    fn load(&mut self) -> Result<(), AsrError>;

    /// Releases backend resources and resets active state.
    fn unload(&mut self);

    /// Sets automatic or explicit language selection.
    ///
    /// # Errors
    /// Returns an error when the backend cannot apply the requested policy.
    fn set_language(&mut self, policy: LanguagePolicy) -> Result<(), AsrError>;

    /// Starts a new recognition session.
    ///
    /// # Errors
    /// Returns an error when the backend is unavailable or the session is invalid.
    fn start_session(&mut self, session_id: u64) -> Result<(), AsrError>;

    /// Supplies canonical 16 kHz mono signed-16 PCM.
    ///
    /// # Errors
    /// Returns an error for stale sessions, invalid audio, cancellation, or backend failure.
    fn push_audio(&mut self, chunk: AudioChunk<'_>) -> Result<Vec<CaptionEvent>, AsrError>;

    /// Flushes pending speech and returns any final captions.
    ///
    /// # Errors
    /// Returns an error for stale sessions or backend failure.
    fn finish(&mut self, session_id: u64) -> Result<Vec<CaptionEvent>, AsrError>;

    /// Cancels active recognition and discards pending results.
    fn cancel(&mut self);
}

/// Deterministic canonical PCM source for backend tests without USB/ESP32 hardware.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PcmFixture {
    pub id: String,
    pub transcript: String,
    pub samples: Vec<i16>,
}

impl PcmFixture {
    /// Creates a deterministic synthetic PCM fixture.
    #[must_use]
    pub fn generated(id: &str, transcript: &str, duration_ms: u32) -> Self {
        let sample_count_u64 = u64::from(CANONICAL_SAMPLE_RATE_HZ)
            .saturating_mul(u64::from(duration_ms))
            / 1_000;
        let sample_count = usize::try_from(sample_count_u64).unwrap_or(usize::MAX);
        let samples = (0..sample_count)
            .map(|index| {
                let phase = i32::try_from(index % 160).unwrap_or(0) - 80;
                i16::try_from(phase.saturating_mul(128)).unwrap_or(0)
            })
            .collect();
        Self {
            id: id.to_owned(),
            transcript: transcript.to_owned(),
            samples,
        }
    }

    /// Feeds the fixture in deterministic chunks and returns all emitted captions.
    ///
    /// # Errors
    /// Returns backend lifecycle or recognition failures.
    pub fn inject<B: AsrBackend>(
        &self,
        backend: &mut B,
        session_id: u64,
        chunk_samples: usize,
    ) -> Result<Vec<CaptionEvent>, AsrError> {
        if chunk_samples == 0 {
            return Err(AsrError::InvalidAudio);
        }
        let mut events = Vec::new();
        for (index, samples) in self.samples.chunks(chunk_samples).enumerate() {
            let offset = index.saturating_mul(chunk_samples);
            let offset_u64 = u64::try_from(offset).unwrap_or(u64::MAX);
            let source_start_ms = offset_u64.saturating_mul(1_000)
                / u64::from(CANONICAL_SAMPLE_RATE_HZ);
            events.extend(backend.push_audio(AudioChunk {
                session_id,
                source_start_ms,
                samples,
            })?);
        }
        events.extend(backend.finish(session_id)?);
        Ok(events)
    }
}

/// Normalizes transcript text for deterministic WER/CER scoring.
#[must_use]
pub fn normalize_transcript(text: &str) -> String {
    text.chars()
        .flat_map(char::to_lowercase)
        .map(|character| {
            if character.is_alphanumeric() || character.is_whitespace() {
                character
            } else {
                ' '
            }
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn edit_distance<T: Eq>(reference: &[T], hypothesis: &[T]) -> usize {
    let mut previous: Vec<usize> = (0..=hypothesis.len()).collect();
    let mut current = vec![0; hypothesis.len().saturating_add(1)];
    for (row, reference_item) in reference.iter().enumerate() {
        current[0] = row.saturating_add(1);
        for (column, hypothesis_item) in hypothesis.iter().enumerate() {
            let substitution = previous[column]
                .saturating_add(usize::from(reference_item != hypothesis_item));
            let deletion = previous[column.saturating_add(1)].saturating_add(1);
            let insertion = current[column].saturating_add(1);
            current[column.saturating_add(1)] = substitution.min(deletion).min(insertion);
        }
        std::mem::swap(&mut previous, &mut current);
    }
    previous[hypothesis.len()]
}

/// Word error rate after repository-standard normalization.
#[must_use]
pub fn word_error_rate(reference: &str, hypothesis: &str) -> f64 {
    let reference = normalize_transcript(reference);
    let hypothesis = normalize_transcript(hypothesis);
    let reference_words: Vec<&str> = reference.split_whitespace().collect();
    let hypothesis_words: Vec<&str> = hypothesis.split_whitespace().collect();
    if reference_words.is_empty() {
        return f64::from(!hypothesis_words.is_empty());
    }
    edit_distance(&reference_words, &hypothesis_words) as f64 / reference_words.len() as f64
}

/// Character error rate after repository-standard normalization.
#[must_use]
pub fn character_error_rate(reference: &str, hypothesis: &str) -> f64 {
    let reference: Vec<char> = normalize_transcript(reference).chars().collect();
    let hypothesis: Vec<char> = normalize_transcript(hypothesis).chars().collect();
    if reference.is_empty() {
        return f64::from(!hypothesis.is_empty());
    }
    edit_distance(&reference, &hypothesis) as f64 / reference.len() as f64
}

/// Timing observations used by deterministic and device ASR benchmarks.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct LatencyMetrics {
    pub audio_duration_ms: u64,
    pub processing_ms: u64,
    pub first_partial_ms: Option<u64>,
    pub finalization_delay_ms: Option<u64>,
    pub end_to_caption_ms: Option<u64>,
}

impl LatencyMetrics {
    /// Real-time factor (`processing time / audio duration`).
    #[must_use]
    pub fn real_time_factor(self) -> Option<f64> {
        (self.audio_duration_ms != 0)
            .then(|| self.processing_ms as f64 / self.audio_duration_ms as f64)
    }
}
