use silent_caption_core::protocol::{Frame, MessageType, encode};
use silent_caption_jni::{
    FfiError, accept_frame, create_boundary, destroy_boundary, reset_boundary, start_session,
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
fn session_and_reset_cross_native_boundary() {
    let handle = create_boundary();
    accept_frame(handle, &hello(7)).unwrap();
    start_session(handle, 42).unwrap();
    assert!(reset_boundary(handle));
    destroy_boundary(handle);
}

#[test]
fn zero_session_is_rejected_by_rust_core() {
    let handle = create_boundary();
    assert!(start_session(handle, 0).is_err());
    destroy_boundary(handle);
}
