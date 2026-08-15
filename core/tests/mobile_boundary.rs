use silent_caption_core::mobile::{MobileEvent, MobileProtocolBoundary};
use silent_caption_core::protocol::{Frame, MessageType, ProtocolError, encode};

fn frame(message_type: MessageType, sequence: u32, session_id: u64, payload: Vec<u8>) -> Vec<u8> {
    encode(&Frame {
        message_type,
        flags: 0,
        sequence,
        session_id,
        timestamp_ms: 123,
        payload,
    })
    .unwrap()
}

fn hello(boot_id: u64) -> Vec<u8> {
    let mut payload = vec![0; 20];
    payload[..8].copy_from_slice(&boot_id.to_le_bytes());
    frame(MessageType::Hello, 0, 0, payload)
}

#[test]
fn hello_reports_boot_change_and_invalidates_session() {
    let mut boundary = MobileProtocolBoundary::default();
    assert_eq!(
        boundary.accept_frame(&hello(1)).unwrap(),
        [MobileEvent::Hello {
            boot_id: 1,
            rebooted: false
        }]
    );
    boundary.start_session(42).unwrap();
    assert_eq!(
        boundary.accept_frame(&hello(2)).unwrap(),
        [MobileEvent::Hello {
            boot_id: 2,
            rebooted: true
        }]
    );
    let audio = frame(MessageType::AudioData, 1, 42, vec![1]);
    assert_eq!(
        boundary.accept_frame(&audio),
        Err(ProtocolError::StaleSession)
    );
}

#[test]
fn audio_is_typed_only_after_rust_validation() {
    let mut boundary = MobileProtocolBoundary::default();
    boundary.accept_frame(&hello(9)).unwrap();
    boundary.start_session(7).unwrap();
    let audio = frame(MessageType::AudioData, 10, 7, vec![1, 2, 3]);
    assert_eq!(
        boundary.accept_frame(&audio).unwrap(),
        [MobileEvent::AudioData {
            session_id: 7,
            timestamp_ms: 123,
            payload: vec![1, 2, 3],
        }]
    );
}

#[test]
fn corrupt_crc_and_stale_session_are_rejected() {
    let mut boundary = MobileProtocolBoundary::default();
    boundary.accept_frame(&hello(3)).unwrap();
    boundary.start_session(8).unwrap();
    let stale = frame(MessageType::AudioData, 1, 9, vec![1]);
    assert_eq!(
        boundary.accept_frame(&stale),
        Err(ProtocolError::StaleSession)
    );

    let mut corrupt = frame(MessageType::AudioData, 1, 8, vec![1]);
    *corrupt.last_mut().unwrap() ^= 1;
    assert_eq!(
        boundary.accept_frame(&corrupt),
        Err(ProtocolError::Integrity)
    );
}

#[test]
fn gaps_duplicates_and_resets_are_explicit_events() {
    let mut boundary = MobileProtocolBoundary::default();
    boundary.accept_frame(&hello(4)).unwrap();
    boundary.start_session(5).unwrap();
    boundary
        .accept_frame(&frame(MessageType::AudioData, 10, 5, vec![1]))
        .unwrap();
    let duplicate = boundary
        .accept_frame(&frame(MessageType::AudioData, 10, 5, vec![2]))
        .unwrap();
    assert_eq!(duplicate[0], MobileEvent::SequenceDuplicate);
    let gap = boundary
        .accept_frame(&frame(MessageType::AudioData, 13, 5, vec![3]))
        .unwrap();
    assert_eq!(gap[0], MobileEvent::SequenceGap { missing: 2 });
    let reset = boundary
        .accept_frame(&frame(MessageType::AudioData, 2, 5, vec![4]))
        .unwrap();
    assert_eq!(reset[0], MobileEvent::SequenceReset);
}

#[test]
fn reset_forgets_peer_and_session() {
    let mut boundary = MobileProtocolBoundary::default();
    boundary.accept_frame(&hello(4)).unwrap();
    boundary.start_session(5).unwrap();
    boundary.reset();
    let audio = frame(MessageType::AudioData, 1, 5, vec![1]);
    assert_eq!(
        boundary.accept_frame(&audio),
        Err(ProtocolError::StaleSession)
    );
}
