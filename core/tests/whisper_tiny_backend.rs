use std::collections::VecDeque;

use silent_caption_core::asr::{AsrBackend, AsrError, AudioChunk, LanguagePolicy};
use silent_caption_core::whisper::{
    BACKEND_ID, OVERLAP_SAMPLES, STEP_SAMPLES, WINDOW_SAMPLES, WhisperEngine, WhisperResult,
    WhisperTinyBackend,
};

#[derive(Default)]
struct FakeEngine {
    loaded: bool,
    cancelled: bool,
    policies: Vec<LanguagePolicy>,
    calls: Vec<(usize, bool)>,
    results: VecDeque<WhisperResult>,
}

impl WhisperEngine for FakeEngine {
    fn load(&mut self) -> Result<(), AsrError> {
        self.loaded = true;
        Ok(())
    }

    fn unload(&mut self) {
        self.loaded = false;
    }

    fn set_language(&mut self, policy: &LanguagePolicy) -> Result<(), AsrError> {
        if let LanguagePolicy::Explicit(language) = policy
            && language == "xx-invalid"
        {
            return Err(AsrError::Backend("unsupported language".to_owned()));
        }
        self.policies.push(policy.clone());
        Ok(())
    }

    fn transcribe(&mut self, samples: &[i16], is_final: bool) -> Result<WhisperResult, AsrError> {
        self.calls.push((samples.len(), is_final));
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
fn constants_define_bounded_overlapping_windows() {
    assert_eq!(WINDOW_SAMPLES, 80_000);
    assert_eq!(STEP_SAMPLES, 16_000);
    assert_eq!(OVERLAP_SAMPLES, 64_000);
}

#[test]
fn windowed_input_emits_partial_then_final_without_duplicate_partial() {
    let engine = FakeEngine {
        results: VecDeque::from([
            result("hello world", "en"),
            result("hello world", "en"),
            result("hello world again", "en"),
        ]),
        ..FakeEngine::default()
    };
    let mut backend = WhisperTinyBackend::new(engine);
    backend.load().unwrap();
    backend.start_session(7).unwrap();

    let first = backend
        .push_audio(AudioChunk {
            session_id: 7,
            source_start_ms: 0,
            samples: &vec![0; WINDOW_SAMPLES],
        })
        .unwrap();
    assert_eq!(first.len(), 1);
    assert!(!first[0].is_final);
    assert_eq!(first[0].backend_id, BACKEND_ID);

    let second = backend
        .push_audio(AudioChunk {
            session_id: 7,
            source_start_ms: 5_000,
            samples: &vec![0; STEP_SAMPLES],
        })
        .unwrap();
    assert!(second.is_empty());

    let final_events = backend.finish(7).unwrap();
    assert_eq!(final_events.len(), 1);
    assert!(final_events[0].is_final);
    assert_eq!(final_events[0].text, "hello world again");
}

#[test]
fn language_auto_and_explicit_policies_reach_runtime() {
    let mut backend = WhisperTinyBackend::new(FakeEngine::default());
    backend.load().unwrap();
    backend.set_language(LanguagePolicy::Auto).unwrap();
    backend
        .set_language(LanguagePolicy::Explicit("es".to_owned()))
        .unwrap();
    assert_eq!(
        backend.set_language(LanguagePolicy::Explicit("xx-invalid".to_owned())),
        Err(AsrError::Backend("unsupported language".to_owned()))
    );
    let engine = backend.into_engine();
    assert_eq!(engine.policies.len(), 2);
}

#[test]
fn stale_session_and_cancellation_are_explicit() {
    let mut backend = WhisperTinyBackend::new(FakeEngine::default());
    backend.load().unwrap();
    backend.start_session(3).unwrap();
    assert_eq!(
        backend.push_audio(AudioChunk {
            session_id: 4,
            source_start_ms: 0,
            samples: &[1],
        }),
        Err(AsrError::InvalidSession)
    );
    backend.cancel();
    assert!(backend.into_engine().cancelled);
}

#[test]
fn finish_flushes_short_audio_as_final_window() {
    let engine = FakeEngine {
        results: VecDeque::from([result("hola", "es")]),
        ..FakeEngine::default()
    };
    let mut backend = WhisperTinyBackend::new(engine);
    backend.load().unwrap();
    backend.start_session(9).unwrap();
    assert!(
        backend
            .push_audio(AudioChunk {
                session_id: 9,
                source_start_ms: 250,
                samples: &[0; 16_000],
            })
            .unwrap()
            .is_empty()
    );
    let events = backend.finish(9).unwrap();
    assert_eq!(events.len(), 1);
    assert_eq!(events[0].text, "hola");
    assert!(events[0].is_final);
    let engine = backend.into_engine();
    assert_eq!(engine.calls, [(16_000, true)]);
}
