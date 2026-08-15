use silent_caption_core::asr::{
    AsrBackend, AsrError, AudioChunk, LanguagePolicy, LatencyMetrics, PcmFixture,
    character_error_rate, normalize_transcript, word_error_rate,
};
use silent_caption_core::captions::CaptionEvent;

#[derive(Default)]
struct FakeBackend {
    loaded: bool,
    session: Option<u64>,
    sequence: u64,
    cancelled: bool,
    received_samples: usize,
}

impl AsrBackend for FakeBackend {
    fn id(&self) -> &str {
        "fake"
    }

    fn load(&mut self) -> Result<(), AsrError> {
        if self.loaded {
            return Err(AsrError::AlreadyLoaded);
        }
        self.loaded = true;
        Ok(())
    }

    fn unload(&mut self) {
        *self = Self::default();
    }

    fn set_language(&mut self, _policy: LanguagePolicy) -> Result<(), AsrError> {
        self.loaded.then_some(()).ok_or(AsrError::NotLoaded)
    }

    fn start_session(&mut self, session_id: u64) -> Result<(), AsrError> {
        if !self.loaded {
            return Err(AsrError::NotLoaded);
        }
        if session_id == 0 {
            return Err(AsrError::InvalidSession);
        }
        self.session = Some(session_id);
        self.cancelled = false;
        Ok(())
    }

    fn push_audio(&mut self, chunk: AudioChunk<'_>) -> Result<Vec<CaptionEvent>, AsrError> {
        if self.cancelled {
            return Err(AsrError::Cancelled);
        }
        if self.session != Some(chunk.session_id) {
            return Err(AsrError::InvalidSession);
        }
        self.received_samples = self.received_samples.saturating_add(chunk.samples.len());
        self.sequence = self.sequence.saturating_add(1);
        Ok(vec![CaptionEvent {
            session_id: chunk.session_id,
            sequence: self.sequence,
            text: "synthetic partial".to_owned(),
            is_final: false,
            source_start_ms: chunk.source_start_ms,
            source_end_ms: chunk.source_start_ms.saturating_add(10),
            confidence: None,
            backend_id: self.id().to_owned(),
        }])
    }

    fn finish(&mut self, session_id: u64) -> Result<Vec<CaptionEvent>, AsrError> {
        if self.session != Some(session_id) {
            return Err(AsrError::InvalidSession);
        }
        self.sequence = self.sequence.saturating_add(1);
        Ok(vec![CaptionEvent {
            session_id,
            sequence: self.sequence,
            text: "synthetic final".to_owned(),
            is_final: true,
            source_start_ms: 0,
            source_end_ms: 1_000,
            confidence: Some(1.0),
            backend_id: self.id().to_owned(),
        }])
    }

    fn cancel(&mut self) {
        self.cancelled = true;
        self.session = None;
    }
}

#[test]
fn normalized_caption_contract_carries_required_fields() {
    let event = CaptionEvent {
        session_id: 9,
        sequence: 3,
        text: "hello".to_owned(),
        is_final: true,
        source_start_ms: 100,
        source_end_ms: 300,
        confidence: None,
        backend_id: "fake".to_owned(),
    };
    assert!(event.has_valid_time_range());
    assert_eq!(event.session_id, 9);
    assert_eq!(event.backend_id, "fake");
}

#[test]
fn generated_fixture_injects_without_hardware() {
    let fixture = PcmFixture::generated("generated-tone-v1", "synthetic final", 1_000);
    let mut backend = FakeBackend::default();
    backend.load().unwrap();
    backend.set_language(LanguagePolicy::Auto).unwrap();
    backend.start_session(7).unwrap();
    let events = fixture.inject(&mut backend, 7, 1_600).unwrap();
    assert_eq!(backend.received_samples, 16_000);
    assert_eq!(events.last().unwrap().text, "synthetic final");
    assert!(events.last().unwrap().is_final);
}

#[test]
fn normalization_and_error_rates_are_deterministic() {
    assert_eq!(normalize_transcript("Hello,  WORLD!"), "hello world");
    assert_eq!(word_error_rate("one two three", "one four three"), 1.0 / 3.0);
    assert_eq!(character_error_rate("abc", "adc"), 1.0 / 3.0);
}

#[test]
fn latency_metrics_report_real_time_factor() {
    let metrics = LatencyMetrics {
        audio_duration_ms: 2_000,
        processing_ms: 500,
        first_partial_ms: Some(100),
        finalization_delay_ms: Some(200),
        end_to_caption_ms: Some(250),
    };
    assert_eq!(metrics.real_time_factor(), Some(0.25));
}

#[test]
fn cancellation_and_session_reset_reject_stale_audio() {
    let mut backend = FakeBackend::default();
    backend.load().unwrap();
    backend.start_session(1).unwrap();
    backend.cancel();
    assert_eq!(
        backend.push_audio(AudioChunk {
            session_id: 1,
            source_start_ms: 0,
            samples: &[1, 2],
        }),
        Err(AsrError::Cancelled)
    );
    backend.start_session(2).unwrap();
    assert_eq!(
        backend.push_audio(AudioChunk {
            session_id: 1,
            source_start_ms: 0,
            samples: &[1, 2],
        }),
        Err(AsrError::InvalidSession)
    );
}

#[test]
fn unload_and_model_switch_style_lifecycle_is_explicit() {
    let mut first = FakeBackend::default();
    first.load().unwrap();
    first.start_session(1).unwrap();
    first.unload();
    assert_eq!(first.set_language(LanguagePolicy::Auto), Err(AsrError::NotLoaded));

    let mut replacement = FakeBackend::default();
    replacement.load().unwrap();
    replacement
        .set_language(LanguagePolicy::Explicit("en".to_owned()))
        .unwrap();
    replacement.start_session(2).unwrap();
    assert_eq!(replacement.session, Some(2));
}
