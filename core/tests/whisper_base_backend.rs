use std::collections::VecDeque;

use silent_caption_core::asr::{
    AsrBackend, AsrError, AudioChunk, LanguagePolicy, LatencyMetrics, PcmFixture,
    character_error_rate, normalize_transcript, word_error_rate,
};
use silent_caption_core::whisper::{WhisperEngine, WhisperResult};
use silent_caption_core::whisper_base::{
    BACKEND_ID, OVERLAP_SAMPLES, STEP_SAMPLES, WINDOW_SAMPLES, WhisperBaseBackend,
};

#[derive(Default)]
struct FakeEngine {
    cancelled: bool,
    policies: Vec<LanguagePolicy>,
    results: VecDeque<WhisperResult>,
}

impl WhisperEngine for FakeEngine {
    fn load(&mut self) -> Result<(), AsrError> {
        Ok(())
    }

    fn unload(&mut self) {}

    fn set_language(&mut self, policy: &LanguagePolicy) -> Result<(), AsrError> {
        self.policies.push(policy.clone());
        Ok(())
    }

    fn transcribe(&mut self, _samples: &[i16], _is_final: bool) -> Result<WhisperResult, AsrError> {
        Ok(self.results.pop_front().unwrap_or(WhisperResult {
            text: String::new(),
            language: None,
            confidence: None,
        }))
    }

    fn cancel(&mut self) {
        self.cancelled = true;
    }
}

fn result(text: &str, language: &str) -> WhisperResult {
    WhisperResult {
        text: text.to_owned(),
        language: Some(language.to_owned()),
        confidence: None,
    }
}

#[test]
fn base_reuses_tiny_window_policy() {
    assert_eq!(WINDOW_SAMPLES, 80_000);
    assert_eq!(STEP_SAMPLES, 16_000);
    assert_eq!(OVERLAP_SAMPLES, 64_000);
}

#[test]
fn base_uses_common_asr_contract_and_stabilization() {
    let engine = FakeEngine {
        results: VecDeque::from([
            result("hello base", "en"),
            result("hello base", "en"),
            result("hello base final", "en"),
        ]),
        ..FakeEngine::default()
    };
    let mut backend = WhisperBaseBackend::new(engine);
    backend.load().unwrap();
    backend.set_language(LanguagePolicy::Auto).unwrap();
    backend.start_session(7).unwrap();

    let first_samples = vec![0; WINDOW_SAMPLES];
    let first = backend
        .push_audio(AudioChunk {
            session_id: 7,
            source_start_ms: 0,
            samples: &first_samples,
        })
        .unwrap();
    assert_eq!(first.len(), 1);
    assert_eq!(first[0].backend_id, BACKEND_ID);
    assert!(!first[0].is_final);

    let step_samples = vec![0; STEP_SAMPLES];
    let duplicate = backend
        .push_audio(AudioChunk {
            session_id: 7,
            source_start_ms: 5_000,
            samples: &step_samples,
        })
        .unwrap();
    assert!(duplicate.is_empty());

    let final_events = backend.finish(7).unwrap();
    assert_eq!(final_events.len(), 1);
    assert_eq!(final_events[0].text, "hello base final");
    assert!(final_events[0].is_final);
}

#[test]
fn base_sc224_uses_common_fixture_scoring_and_latency_metrics() {
    let fixture = PcmFixture::generated("base-en", "The quick brown fox", 1_000);
    let engine = FakeEngine {
        results: VecDeque::from([result("the quick brown fox", "en")]),
        ..FakeEngine::default()
    };
    let mut backend = WhisperBaseBackend::new(engine);
    backend.load().unwrap();
    backend.start_session(12).unwrap();
    let events = fixture.inject(&mut backend, 12, 8_000).unwrap();
    let final_caption = events.iter().rev().find(|event| event.is_final).unwrap();
    assert_eq!(
        normalize_transcript(&final_caption.text),
        fixture.transcript.to_lowercase()
    );
    assert!(word_error_rate(&fixture.transcript, &final_caption.text) < f64::EPSILON);
    assert!(character_error_rate(&fixture.transcript, &final_caption.text) < f64::EPSILON);

    let metrics = LatencyMetrics {
        audio_duration_ms: 5_000,
        processing_ms: 4_000,
        first_partial_ms: Some(1_000),
        finalization_delay_ms: Some(500),
        end_to_caption_ms: Some(500),
    };
    assert!((metrics.real_time_factor().unwrap() - 0.8).abs() < f64::EPSILON);
}

#[test]
fn base_language_selection_and_cancel_are_explicit() {
    let mut backend = WhisperBaseBackend::new(FakeEngine::default());
    backend.load().unwrap();
    backend
        .set_language(LanguagePolicy::Explicit("es".to_owned()))
        .unwrap();
    backend.start_session(3).unwrap();
    backend.cancel();
    let engine = backend.into_engine();
    assert_eq!(
        engine.policies,
        [LanguagePolicy::Explicit("es".to_owned())]
    );
    assert!(engine.cancelled);
}
