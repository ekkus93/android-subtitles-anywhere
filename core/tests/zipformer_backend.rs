use std::collections::VecDeque;

use silent_caption_core::asr::{AsrBackend, AsrError, AudioChunk, LanguagePolicy};
use silent_caption_core::zipformer::{
    BACKEND_ID, ZipformerBackend, ZipformerEngine, ZipformerResult,
};

#[derive(Default)]
struct FakeEngine {
    loaded: bool,
    starts: usize,
    accepted: usize,
    cancelled: bool,
    results: VecDeque<ZipformerResult>,
    final_result: Option<ZipformerResult>,
}

impl ZipformerEngine for FakeEngine {
    fn load(&mut self) -> Result<(), AsrError> {
        self.loaded = true;
        Ok(())
    }

    fn unload(&mut self) {
        self.loaded = false;
    }

    fn start_stream(&mut self) -> Result<(), AsrError> {
        self.starts = self.starts.saturating_add(1);
        Ok(())
    }

    fn accept_samples(&mut self, samples: &[i16]) -> Result<(), AsrError> {
        self.accepted = self.accepted.saturating_add(samples.len());
        Ok(())
    }

    fn decode(&mut self) -> Result<Option<ZipformerResult>, AsrError> {
        Ok(self.results.pop_front())
    }

    fn finish(&mut self) -> Result<Option<ZipformerResult>, AsrError> {
        Ok(self.final_result.take())
    }

    fn cancel(&mut self) {
        self.cancelled = true;
    }
}

fn result(text: &str, is_endpoint: bool) -> ZipformerResult {
    ZipformerResult {
        text: text.to_owned(),
        is_endpoint,
        confidence: None,
    }
}

#[test]
fn streaming_partial_and_final_use_common_caption_contract() {
    let engine = FakeEngine {
        results: VecDeque::from([result("hello", false)]),
        final_result: Some(result("hello world", true)),
        ..FakeEngine::default()
    };
    let mut backend = ZipformerBackend::new(engine);
    backend.load().unwrap();
    backend.set_language(LanguagePolicy::Auto).unwrap();
    backend.start_session(7).unwrap();

    let partial = backend
        .push_audio(AudioChunk {
            session_id: 7,
            source_start_ms: 100,
            samples: &[0; 1_600],
        })
        .unwrap();
    assert_eq!(partial.len(), 1);
    assert_eq!(partial[0].text, "hello");
    assert!(!partial[0].is_final);
    assert_eq!(partial[0].backend_id, BACKEND_ID);

    let final_events = backend.finish(7).unwrap();
    assert_eq!(final_events.len(), 1);
    assert_eq!(final_events[0].text, "hello world");
    assert!(final_events[0].is_final);
    assert!(final_events[0].sequence > partial[0].sequence);
}

#[test]
fn duplicate_partial_is_suppressed() {
    let engine = FakeEngine {
        results: VecDeque::from([result("same", false), result("same", false)]),
        ..FakeEngine::default()
    };
    let mut backend = ZipformerBackend::new(engine);
    backend.load().unwrap();
    backend.start_session(1).unwrap();
    let chunk = AudioChunk {
        session_id: 1,
        source_start_ms: 0,
        samples: &[0; 800],
    };
    assert_eq!(backend.push_audio(chunk).unwrap().len(), 1);
    assert!(backend.push_audio(chunk).unwrap().is_empty());
}

#[test]
fn endpoint_emits_final_and_resets_stream() {
    let engine = FakeEngine {
        results: VecDeque::from([result("utterance", true)]),
        ..FakeEngine::default()
    };
    let mut backend = ZipformerBackend::new(engine);
    backend.load().unwrap();
    backend.start_session(2).unwrap();
    let events = backend
        .push_audio(AudioChunk {
            session_id: 2,
            source_start_ms: 0,
            samples: &[0; 1_600],
        })
        .unwrap();
    assert!(events[0].is_final);
    assert_eq!(backend.into_engine().starts, 2);
}

#[test]
fn stale_session_and_unsupported_language_are_explicit() {
    let mut backend = ZipformerBackend::new(FakeEngine::default());
    backend.load().unwrap();
    assert!(
        backend
            .set_language(LanguagePolicy::Explicit("en".to_owned()))
            .is_ok()
    );
    assert!(
        backend
            .set_language(LanguagePolicy::Explicit("fr".to_owned()))
            .is_err()
    );
    backend.start_session(3).unwrap();
    assert_eq!(
        backend.push_audio(AudioChunk {
            session_id: 4,
            source_start_ms: 0,
            samples: &[1],
        }),
        Err(AsrError::InvalidSession)
    );
}

#[test]
fn cancellation_reaches_runtime_boundary() {
    let mut backend = ZipformerBackend::new(FakeEngine::default());
    backend.load().unwrap();
    backend.start_session(5).unwrap();
    backend.cancel();
    assert!(backend.into_engine().cancelled);
}
