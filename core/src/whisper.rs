//! Windowed Whisper Tiny multilingual adapter behind the common ASR contract.

use crate::asr::{AsrBackend, AsrError, AudioChunk, LanguagePolicy};
use crate::captions::CaptionEvent;

pub const BACKEND_ID: &str = "whisper-tiny-multilingual";
pub const WHISPER_CPP_VERSION: &str = "1.9.1";
pub const MODEL_ID: &str = "ggml-tiny.bin";
pub const MODEL_SIZE_BYTES: u64 = 77_691_713;
pub const MODEL_SHA256: &str = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21";
pub const WINDOW_SAMPLES: usize = 80_000;
pub const STEP_SAMPLES: usize = 16_000;
pub const OVERLAP_SAMPLES: usize = WINDOW_SAMPLES - STEP_SAMPLES;

#[derive(Clone, Debug, PartialEq)]
pub struct WhisperResult {
    pub text: String,
    pub language: Option<String>,
    pub confidence: Option<f32>,
}

pub trait WhisperEngine {
    /// Loads the pinned model/runtime resources.
    ///
    /// # Errors
    /// Returns a stable ASR error if initialization fails.
    fn load(&mut self) -> Result<(), AsrError>;
    fn unload(&mut self);
    /// Applies automatic detection or an explicit language code.
    ///
    /// # Errors
    /// Returns a stable ASR error if the language is unsupported.
    fn set_language(&mut self, policy: &LanguagePolicy) -> Result<(), AsrError>;
    /// Transcribes one bounded canonical PCM window.
    ///
    /// # Errors
    /// Returns a stable ASR error if inference fails.
    fn transcribe(&mut self, samples: &[i16], is_final: bool) -> Result<WhisperResult, AsrError>;
    fn cancel(&mut self);
}

pub struct WhisperTinyBackend<E> {
    engine: E,
    loaded: bool,
    session_id: Option<u64>,
    sequence: u64,
    buffer: Vec<i16>,
    consumed_samples: u64,
    source_origin_ms: u64,
    last_text: String,
}

impl<E> WhisperTinyBackend<E> {
    #[must_use]
    pub fn new(engine: E) -> Self {
        Self {
            engine,
            loaded: false,
            session_id: None,
            sequence: 0,
            buffer: Vec::new(),
            consumed_samples: 0,
            source_origin_ms: 0,
            last_text: String::new(),
        }
    }

    #[must_use]
    pub fn into_engine(self) -> E {
        self.engine
    }
}

impl<E: WhisperEngine> WhisperTinyBackend<E> {
    fn caption(&mut self, result: &WhisperResult, is_final: bool) -> Option<CaptionEvent> {
        let text = result.text.trim();
        if text.is_empty() || (!is_final && text == self.last_text) {
            return None;
        }
        self.sequence = self.sequence.saturating_add(1);
        text.clone_into(&mut self.last_text);
        let start_ms = self
            .source_origin_ms
            .saturating_add(self.consumed_samples.saturating_mul(1_000) / 16_000);
        let buffered = u64::try_from(self.buffer.len()).unwrap_or(u64::MAX);
        let end_ms = start_ms.saturating_add(buffered.saturating_mul(1_000) / 16_000);
        Some(CaptionEvent {
            session_id: self.session_id?,
            sequence: self.sequence,
            text: text.to_owned(),
            is_final,
            source_start_ms: start_ms,
            source_end_ms: end_ms,
            confidence: result.confidence,
            backend_id: BACKEND_ID.to_owned(),
        })
    }

    fn decode_ready_windows(&mut self) -> Result<Vec<CaptionEvent>, AsrError> {
        let mut events = Vec::new();
        while self.buffer.len() >= WINDOW_SAMPLES {
            let result = self
                .engine
                .transcribe(&self.buffer[..WINDOW_SAMPLES], false)?;
            if let Some(event) = self.caption(&result, false) {
                events.push(event);
            }
            self.buffer.drain(..STEP_SAMPLES);
            self.consumed_samples = self
                .consumed_samples
                .saturating_add(u64::try_from(STEP_SAMPLES).unwrap_or(u64::MAX));
        }
        Ok(events)
    }
}

impl<E: WhisperEngine> AsrBackend for WhisperTinyBackend<E> {
    fn id(&self) -> &str {
        BACKEND_ID
    }

    fn load(&mut self) -> Result<(), AsrError> {
        if self.loaded {
            return Err(AsrError::AlreadyLoaded);
        }
        self.engine.load()?;
        self.loaded = true;
        Ok(())
    }

    fn unload(&mut self) {
        self.engine.unload();
        self.loaded = false;
        self.session_id = None;
        self.buffer.clear();
        self.last_text.clear();
    }

    fn set_language(&mut self, policy: LanguagePolicy) -> Result<(), AsrError> {
        if !self.loaded {
            return Err(AsrError::NotLoaded);
        }
        self.engine.set_language(&policy)
    }

    fn start_session(&mut self, session_id: u64) -> Result<(), AsrError> {
        if !self.loaded {
            return Err(AsrError::NotLoaded);
        }
        if session_id == 0 {
            return Err(AsrError::InvalidSession);
        }
        self.session_id = Some(session_id);
        self.sequence = 0;
        self.buffer.clear();
        self.consumed_samples = 0;
        self.source_origin_ms = 0;
        self.last_text.clear();
        Ok(())
    }

    fn push_audio(&mut self, chunk: AudioChunk<'_>) -> Result<Vec<CaptionEvent>, AsrError> {
        if self.session_id != Some(chunk.session_id) {
            return Err(AsrError::InvalidSession);
        }
        if chunk.samples.is_empty() {
            return Ok(Vec::new());
        }
        if self.buffer.is_empty() && self.consumed_samples == 0 {
            self.source_origin_ms = chunk.source_start_ms;
        }
        self.buffer.extend_from_slice(chunk.samples);
        self.decode_ready_windows()
    }

    fn finish(&mut self, session_id: u64) -> Result<Vec<CaptionEvent>, AsrError> {
        if self.session_id != Some(session_id) {
            return Err(AsrError::InvalidSession);
        }
        let mut events = self.decode_ready_windows()?;
        if !self.buffer.is_empty() {
            let samples = self.buffer.clone();
            let result = self.engine.transcribe(&samples, true)?;
            if let Some(event) = self.caption(&result, true) {
                events.push(event);
            }
        }
        self.session_id = None;
        self.buffer.clear();
        self.last_text.clear();
        Ok(events)
    }

    fn cancel(&mut self) {
        self.engine.cancel();
        self.session_id = None;
        self.buffer.clear();
        self.last_text.clear();
    }
}
