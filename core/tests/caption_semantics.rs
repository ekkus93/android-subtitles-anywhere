use silent_caption_core::captions::CaptionEvent;
use silent_caption_core::vad::{
    CaptionStabilizer, CaptionUpdate, DiscontinuityReason, EnergyVad, SpeechState, VadConfig,
    normalize_display_text,
};

fn caption(sequence: u64, text: &str, is_final: bool) -> CaptionEvent {
    CaptionEvent {
        session_id: 7,
        sequence,
        text: text.to_owned(),
        is_final,
        source_start_ms: 0,
        source_end_ms: 100,
        confidence: None,
        backend_id: "fixture".to_owned(),
    }
}

#[test]
fn vad_tracks_silence_speech_and_bounded_post_roll() {
    let mut vad = EnergyVad::new(VadConfig {
        speech_threshold: 100,
        pre_roll_samples: 4,
        post_roll_samples: 4,
    });
    assert!(!vad.observe(&[0, 0, 0, 0]));
    assert_eq!(vad.state(), SpeechState::Silence);
    assert!(vad.observe(&[0, 101]));
    assert_eq!(vad.state(), SpeechState::Speech);
    assert!(vad.observe(&[0, 0]));
    assert_eq!(vad.state(), SpeechState::PostRoll);
    assert!(vad.observe(&[0, 0]));
    assert!(!vad.observe(&[0]));
    assert_eq!(vad.state(), SpeechState::Silence);
}

#[test]
fn pre_roll_is_strictly_bounded_to_latest_audio() {
    let mut vad = EnergyVad::new(VadConfig {
        speech_threshold: 100,
        pre_roll_samples: 3,
        post_roll_samples: 0,
    });
    assert!(!vad.observe(&[1, 2]));
    assert!(!vad.observe(&[3, 4]));
    assert_eq!(vad.pre_roll(), [2, 3, 4]);
}

#[test]
fn partials_replace_and_finals_commit_once() {
    let mut stabilizer = CaptionStabilizer::default();
    stabilizer.start_session(7);
    assert_eq!(
        stabilizer.accept(&caption(1, "hello", false)),
        Some(CaptionUpdate::ReplacePartial {
            text: "hello".to_owned()
        })
    );
    assert_eq!(stabilizer.accept(&caption(2, "hello", false)), None);
    assert_eq!(
        stabilizer.accept(&caption(3, "hello world", false)),
        Some(CaptionUpdate::ReplacePartial {
            text: "hello world".to_owned()
        })
    );
    assert_eq!(
        stabilizer.accept(&caption(4, "hello world.", true)),
        Some(CaptionUpdate::CommitFinal {
            text: "hello world.".to_owned()
        })
    );
    assert_eq!(stabilizer.accept(&caption(4, "hello world.", true)), None);
}

#[test]
fn punctuation_casing_and_confidence_are_not_fabricated() {
    assert_eq!(normalize_display_text("  Hello,   WORLD!  "), "Hello, WORLD!");
    let event = caption(1, "Mixed CASE?", true);
    assert_eq!(event.confidence, None);
    let mut stabilizer = CaptionStabilizer::default();
    stabilizer.start_session(7);
    assert_eq!(
        stabilizer.accept(&event),
        Some(CaptionUpdate::CommitFinal {
            text: "Mixed CASE?".to_owned()
        })
    );
}

#[test]
fn discontinuity_clears_partial_before_marker() {
    let mut stabilizer = CaptionStabilizer::default();
    stabilizer.start_session(7);
    let _ = stabilizer.accept(&caption(1, "unfinished", false));
    assert_eq!(
        stabilizer.discontinuity(DiscontinuityReason::UsbGap),
        [
            CaptionUpdate::ClearPartial,
            CaptionUpdate::Discontinuity(DiscontinuityReason::UsbGap)
        ]
    );
}

#[test]
fn stale_session_events_are_ignored_after_restart() {
    let mut stabilizer = CaptionStabilizer::default();
    stabilizer.start_session(8);
    assert_eq!(stabilizer.accept(&caption(1, "stale", false)), None);
}

#[test]
fn long_and_rapid_updates_remain_replacement_based() {
    let mut stabilizer = CaptionStabilizer::default();
    stabilizer.start_session(7);
    for sequence in 1..=1_000 {
        let event = caption(sequence, &format!("utterance {sequence}"), false);
        assert!(matches!(
            stabilizer.accept(&event),
            Some(CaptionUpdate::ReplacePartial { .. })
        ));
    }
    assert_eq!(
        stabilizer.discontinuity(DiscontinuityReason::PauseResume),
        [
            CaptionUpdate::ClearPartial,
            CaptionUpdate::Discontinuity(DiscontinuityReason::PauseResume)
        ]
    );
}
