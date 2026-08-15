//! Streaming Zipformer adapter behind the platform-neutral ASR contract.
//!
//! The actual sherpa-onnx runtime is intentionally supplied through the narrow
//! `ZipformerEngine` boundary. Android may implement that boundary with the
//! pinned sherpa-onnx AAR without coupling the portable core to JNI/Android APIs.

use crate::asr::{AsrBackend, AsrError, AudioChunk, LanguagePolicy};
use crate::captions::CaptionEvent;

pub const BACKEND_ID: &str = "sherpa-onnx-zipformer-en-20m";
pub const SHERPA_ONNX_VERSION: &str = "1.13.5";
pub const MODEL_ID: &str = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17";
pub const MODEL_ARCHIVE_SIZE: u64 = 127_887_156;
pub const MODEL_ARCHIVE_SHA256: &str =
    "9c559283e8498d3fe95913c79ca1cb454bb26281ac2b102b41306c7d752765d9";
pub const ANDROID_AAR_SHA256: &str =
    "6419cd8bc983e0c4fab06067f0fe0313fdc0f7103818ac1e7a08d50787b7a82b";

/// Result snapshot returned by the sherpa-onnx streaming engine.
#[derive(Clone, Debug, PartialEq)]
pub struct ZipformerResult {
    pub text: String,
    pub is_endpoint: bool,
    pub confidence: Option<f32>,
}

/// Narrow runtime boundary implemented by the platform sherpa-onnx binding.
pub trait ZipformerEngine {
    /// Loads model/runtime resources.
    ///
    /// # Errors
    /// Returns a stable ASR error if initialization fails.
    fn load(&mut self) -> Result<(), AsrError>;
    /// Releases all runtime resources.
    fn unload(&mut self);
    /// Starts or resets the recognizer stream.
    ///
    /// # Errors
    /// Returns a stable ASR error if a stream cannot be created.
    fn start_stream(&mut self) -> Result<(), AsrError>;
    /// Accepts canonical 16 kHz mono PCM.
    ///
    /// # Errors
    /// Returns a stable ASR error if audio cannot be accepted.
    fn accept_samples(&mut self, samples: &[i16]) -> Result<(), AsrError>;
    /// Decodes available frames and returns the current transcript snapshot.
    ///
    /// # Errors
    /// Returns a stable ASR error if recognition fails.
    fn decode(&mut self) -> Result<Option<ZipformerResult>, AsrError>;
    /// Signals end-of-input and returns the final transcript snapshot.
    ///
    /// # Errors
    /// Returns a stable ASR error if finalization fails.
    fn finish(&mut self) -> Result<Option<ZipformerResult>, AsrError>;
    /// Cancels and resets the active stream.
    fn cancel(&mut self);
}

/// Streaming Zipformer implementation of the common ASR contract.
pub struct ZipformerBackend<E> {
    engine: E,
    loaded: bool,
    session_id: Option<u64>,
    sequence: u64,
    source_start_ms: u64,
    source_end_ms: u64,
    last_text: String,
}

impl<E> ZipformerBackend<E> {
    #[must_use]
    pub fn new(engine: E) -> Self {
        Self {
            engine,
            loaded: false,
            session_id: None,
            sequence: 0,
            source_start_ms: 0,
            source_end_ms: 0,
            last_text: String::new(),
        }
    }

    #[must_use]
    pub fn into_engine(self) -> E {
        self.engine
    }
}

impl<E: ZipformerEngine> ZipformerBackend<E> {
    fn event(&mut self, result: &ZipformerResult, is_final: bool) -> Option<CaptionEvent> {
        let text = result.text.trim();
        if text.is_empty() || (!is_final && text == self.last_text) {
            return None;
        }
        self.sequence = self.sequence.saturating_add(1);
        text.clone_into(&mut self.last_text);
        Some(CaptionEvent {
            session_id: self.session_id?,
            sequence: self.sequence,
            text: text.to_owned(),
            is_final,
            source_start_ms: self.source_start_ms,
            source_end_ms: self.source_end_ms,
            confidence: result.confidence,
            backend_id: BACKEND_ID.to_owned(),
        })
    }
}

impl<E: ZipformerEngine> AsrBackend for ZipformerBackend<E> {
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
        self.last_text.clear();
    }

    fn set_language(&mut self, policy: LanguagePolicy) -> Result<(), AsrError> {
        if !self.loaded {
            return Err(AsrError::NotLoaded);
        }
        match policy {
            LanguagePolicy::Auto => Ok(()),
            LanguagePolicy::Explicit(language) if language == "en" => Ok(()),
            LanguagePolicy::Explicit(_) => Err(AsrError::Backend(
                "selected Zipformer model supports English only".to_owned(),
            )),
        }
    }

    fn start_session(&mut self, session_id: u64) -> Result<(), AsrError> {
        if !self.loaded {
            return Err(AsrError::NotLoaded);
        }
        if session_id == 0 {
            return Err(AsrError::InvalidSession);
        }
        self.engine.start_stream()?;
        self.session_id = Some(session_id);
        self.sequence = 0;
        self.source_start_ms = 0;
        self.source_end_ms = 0;
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
        if self.source_end_ms == 0 {
            self.source_start_ms = chunk.source_start_ms;
        }
        let duration_ms = u64::try_from(chunk.samples.len())
            .unwrap_or(u64::MAX)
            .saturating_mul(1_000)
            / 16_000;
        self.source_end_ms = chunk.source_start_ms.saturating_add(duration_ms);
        self.engine.accept_samples(chunk.samples)?;
        let mut events = Vec::new();
        if let Some(result) = self.engine.decode()? {
            let endpoint = result.is_endpoint;
            if let Some(event) = self.event(&result, endpoint) {
                events.push(event);
            }
            if endpoint {
                self.engine.start_stream()?;
                self.last_text.clear();
                self.source_start_ms = self.source_end_ms;
            }
        }
        Ok(events)
    }

    fn finish(&mut self, session_id: u64) -> Result<Vec<CaptionEvent>, AsrError> {
        if self.session_id != Some(session_id) {
            return Err(AsrError::InvalidSession);
        }
        let events = self
            .engine
            .finish()?
            .as_ref()
            .and_then(|result| self.event(result, true))
            .into_iter()
            .collect();
        self.session_id = None;
        self.last_text.clear();
        Ok(events)
    }

    fn cancel(&mut self) {
        self.engine.cancel();
        self.session_id = None;
        self.last_text.clear();
    }
}
