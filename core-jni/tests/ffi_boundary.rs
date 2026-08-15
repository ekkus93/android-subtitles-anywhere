use silent_caption_core::captions::CaptionEvent;
use silent_caption_core::protocol::{Frame, MessageType, encode};
use silent_caption_core::vad::{CaptionUpdate, DiscontinuityReason};
use silent_caption_jni::{
    BackendSelection, FfiError, accept_caption, accept_frame, create_boundary, destroy_boundary,
    discontinuity, reset_boundary, select_backend, start_session, stop_session,
};

fn hello(boot_id: u64) -> Vec<u8> {
    let mut payload = vec![0; 20];
    payload[..8].copy_from_slice(&boot_id.to_le_bytes());
    encode(&Frame {
        message_type: MessageType::Hello,
        flags: 0,
        sequence: 0,
        session_id: 0,
        timestamp_ms: 0,
        payload,
    })
    .unwrap()
}

fn caption(session_id: u64, sequence: u64, text: &str, is_final: bool) -> CaptionEvent {
    CaptionEvent {
        session_id,
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
fn handle_lifecycle_rejects_use_after_destroy() {
    let handle = create_boundary();
    assert!(handle > 0);
    assert!(accept_frame(handle, &hello(1)).is_ok());
    assert!(destroy_boundary(handle));
    assert_eq!(
        accept_frame(handle, &hello(1)),
        Err(FfiError::InvalidHandle)
    );
    assert!(!destroy_boundary(handle));
}

#[test]
fn session_start_stop_and_reset_cross_native_boundary() {
    let handle = create_boundary();
    accept_frame(handle, &hello(7)).unwrap();
    start_session(handle, 42).unwrap();
    assert_eq!(start_session(handle, 43), Err(FfiError::SessionActive));
    stop_session(handle).unwrap();
    start_session(handle, 43).unwrap();
    assert!(reset_boundary(handle));
    destroy_boundary(handle);
}

#[test]
fn backend_selection_is_blocked_during_active_session() {
    let handle = create_boundary();
    select_backend(handle, BackendSelection::WhisperTiny).unwrap();
    start_session(handle, 9).unwrap();
    assert_eq!(
        select_backend(handle, BackendSelection::WhisperBase),
        Err(FfiError::SessionActive)
    );
    stop_session(handle).unwrap();
    select_backend(handle, BackendSelection::WhisperBase).unwrap();
    destroy_boundary(handle);
}

#[test]
fn caption_and_discontinuity_semantics_cross_ffi_boundary() {
    let handle = create_boundary();
    start_session(handle, 7).unwrap();
    assert_eq!(
        accept_caption(handle, &caption(7, 1, "hello", false)).unwrap(),
        Some(CaptionUpdate::ReplacePartial {
            text: "hello".to_owned()
        })
    );
    assert_eq!(
        discontinuity(handle, DiscontinuityReason::UsbGap).unwrap(),
        [
            CaptionUpdate::ClearPartial,
            CaptionUpdate::Discontinuity(DiscontinuityReason::UsbGap)
        ]
    );
    assert_eq!(
        accept_caption(handle, &caption(7, 2, "hello world", true)).unwrap(),
        Some(CaptionUpdate::CommitFinal {
            text: "hello world".to_owned()
        })
    );
    stop_session(handle).unwrap();
    assert_eq!(
        accept_caption(handle, &caption(7, 3, "stale", false)).unwrap(),
        None
    );
    destroy_boundary(handle);
}

#[test]
fn stable_error_codes_and_names_do_not_depend_on_rust_debug_text() {
    assert_eq!(FfiError::InvalidHandle.code(), 1);
    assert_eq!(FfiError::InvalidHandle.name(), "invalid_handle");
    assert_eq!(FfiError::SessionActive.code(), 5);
    assert_eq!(FfiError::SessionActive.name(), "session_active");
}

#[test]
fn zero_session_is_rejected_by_rust_core() {
    let handle = create_boundary();
    assert!(start_session(handle, 0).is_err());
    destroy_boundary(handle);
}

#[test]
fn repeated_lifecycle_cycles_do_not_reuse_live_handles_or_leak_state() {
    let mut previous = 0;
    for cycle in 1..=1_000_u64 {
        let handle = create_boundary();
        assert!(handle > previous);
        previous = handle;
        select_backend(handle, BackendSelection::Zipformer).unwrap();
        start_session(handle, cycle).unwrap();
        let update = accept_caption(handle, &caption(cycle, 1, "partial", false)).unwrap();
        assert!(matches!(update, Some(CaptionUpdate::ReplacePartial { .. })));
        stop_session(handle).unwrap();
        assert!(destroy_boundary(handle));
        assert_eq!(stop_session(handle), Err(FfiError::InvalidHandle));
    }
}
