use std::collections::VecDeque;

use silent_caption_core::asr::{
    AsrBackend, AsrError, LanguagePolicy, LatencyMetrics, PcmFixture, character_error_rate,
    normalize_transcript, word_error_rate,
};
use silent_caption_core::whisper::{WhisperEngine, WhisperResult, WhisperTinyBackend};

#[derive(Default)]
struct HarnessEngine {
    results: VecDeque<WhisperResult>,
    cancelled: bool,
}

impl WhisperEngine for HarnessEngine {
    fn load(&mut self) -> Result<(), AsrError> {
        Ok(())
    }

    fn unload(&mut self) {}

    fn set_language(&mut self, _policy: &LanguagePolicy) -> Result<(), AsrError> {
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
fn sc206_fixture_injection_scores_final_transcript_deterministically() {
    let fixture = PcmFixture::generated("tiny-en", "Hello world again", 6_000);
    let engine = HarnessEngine {
        results: VecDeque::from([
            result("hello world", "en"),
            result("hello world again", "en"),
            result("hello world again", "en"),
        ]),
        ..HarnessEngine::default()
    };
    let mut backend = WhisperTinyBackend::new(engine);
    backend.load().unwrap();
    backend.set_language(LanguagePolicy::Auto).unwrap();
    backend.start_session(41).unwrap();

    let events = fixture.inject(&mut backend, 41, 16_000).unwrap();
    let final_caption = events.iter().rev().find(|event| event.is_final).unwrap();
    assert_eq!(normalize_transcript(&final_caption.text), fixture.transcript.to_lowercase());
    assert!(word_error_rate(&fixture.transcript, &final_caption.text) < f64::EPSILON);
    assert!(character_error_rate(&fixture.transcript, &final_caption.text) < f64::EPSILON);
}

#[test]
fn sc206_multilingual_fixture_uses_common_normalization_and_scoring() {
    let reference = "¡Hola, mundo!";
    let hypothesis = "hola mundo";
    assert_eq!(normalize_transcript(reference), hypothesis);
    assert!(word_error_rate(reference, hypothesis) < f64::EPSILON);
    assert!(character_error_rate(reference, hypothesis) < f64::EPSILON);
}

#[test]
fn sc206_latency_metrics_report_real_time_factor_without_wall_clock_flakes() {
    let metrics = LatencyMetrics {
        audio_duration_ms: 5_000,
        processing_ms: 2_500,
        first_partial_ms: Some(800),
        finalization_delay_ms: Some(300),
        end_to_caption_ms: Some(300),
    };
    let rtf = metrics.real_time_factor().unwrap();
    assert!((rtf - 0.5).abs() < f64::EPSILON);
    assert_eq!(metrics.first_partial_ms, Some(800));
    assert_eq!(metrics.finalization_delay_ms, Some(300));
}

#[test]
fn sc206_repeated_lifecycle_and_cancel_are_bounded() {
    for session_id in 1..=32 {
        let mut backend = WhisperTinyBackend::new(HarnessEngine::default());
        backend.load().unwrap();
        backend.start_session(session_id).unwrap();
        backend.cancel();
        assert!(backend.into_engine().cancelled);
    }
}
