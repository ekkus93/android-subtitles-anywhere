//! Whisper Base multilingual adapter reusing the common Whisper windowing pipeline.

use crate::asr::{AsrBackend, AsrError, AudioChunk, LanguagePolicy};
use crate::captions::CaptionEvent;
use crate::whisper::{WhisperBackend, WhisperEngine};

pub const BACKEND_ID: &str = "whisper-base-multilingual";
pub const WHISPER_CPP_VERSION: &str = "1.9.1";
pub const MODEL_ID: &str = "ggml-base.bin";
pub const MODEL_SIZE_BYTES: u64 = 147_964_211;
pub const MODEL_SHA1: &str = "465707469ff3a37a2b9b8d8f89f2f99de7299dac";
pub const WINDOW_SAMPLES: usize = crate::whisper::WINDOW_SAMPLES;
pub const STEP_SAMPLES: usize = crate::whisper::STEP_SAMPLES;
pub const OVERLAP_SAMPLES: usize = crate::whisper::OVERLAP_SAMPLES;

pub struct WhisperBaseBackend<E>(WhisperBackend<E>);

impl<E> WhisperBaseBackend<E> {
    #[must_use]
    pub fn new(engine: E) -> Self {
        Self(WhisperBackend::new(engine, BACKEND_ID))
    }

    #[must_use]
    pub fn into_engine(self) -> E {
        self.0.into_engine()
    }
}

impl<E: WhisperEngine> AsrBackend for WhisperBaseBackend<E> {
    fn id(&self) -> &str {
        self.0.id()
    }

    fn load(&mut self) -> Result<(), AsrError> {
        self.0.load()
    }

    fn unload(&mut self) {
        self.0.unload();
    }

    fn set_language(&mut self, policy: LanguagePolicy) -> Result<(), AsrError> {
        self.0.set_language(policy)
    }

    fn start_session(&mut self, session_id: u64) -> Result<(), AsrError> {
        self.0.start_session(session_id)
    }

    fn push_audio(&mut self, chunk: AudioChunk<'_>) -> Result<Vec<CaptionEvent>, AsrError> {
        self.0.push_audio(chunk)
    }

    fn finish(&mut self, session_id: u64) -> Result<Vec<CaptionEvent>, AsrError> {
        self.0.finish(session_id)
    }

    fn cancel(&mut self) {
        self.0.cancel();
    }
}
