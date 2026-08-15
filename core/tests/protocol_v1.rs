use silent_caption_core::protocol::*;

fn frame(t: MessageType, payload: Vec<u8>) -> Frame {
    Frame {
        message_type: t,
        flags: 0,
        sequence: 7,
        session_id: 0x1122_3344_5566_7788,
        timestamp_ms: 1234,
        payload,
    }
}

#[test]
fn every_message_round_trips() {
    let cases = [
        frame(MessageType::Hello, vec![0; 20]),
        frame(MessageType::StartSession, vec![1; 8]),
        frame(MessageType::StopSession, vec![]),
        frame(MessageType::SetPowerState, vec![1]),
        frame(MessageType::Heartbeat, vec![]),
        frame(MessageType::AudioFormat, vec![0; 12]),
        frame(MessageType::AudioData, vec![1, 2]),
        frame(MessageType::Status, vec![0; 8]),
        frame(MessageType::Diagnostics, vec![0; 32]),
        frame(MessageType::Error, vec![1, 0, 1, 0]),
    ];
    for f in cases {
        let wire = encode(&f).unwrap();
        assert_eq!(decode(&wire).unwrap(), f);
    }
}

#[test]
fn crc_detects_corruption() {
    let mut w = encode(&frame(MessageType::AudioData, vec![1, 2, 3, 4])).unwrap();
    *w.last_mut().unwrap() ^= 1;
    assert_eq!(decode(&w), Err(ProtocolError::Integrity));
}

#[test]
fn oversized_declared_payload_is_rejected_before_payload() {
    let mut h = vec![0; HEADER_LEN];
    h[..4].copy_from_slice(&MAGIC);
    h[4] = 1;
    h[6] = MessageType::AudioData as u8;
    h[8..12].copy_from_slice(&4097u32.to_le_bytes());
    assert_eq!(decode(&h), Err(ProtocolError::OversizedPayload(4097)));
}

#[test]
fn unsupported_version_and_reserved_flags_are_rejected() {
    let mut w = encode(&frame(MessageType::Heartbeat, vec![])).unwrap();
    w[4] = 2;
    assert_eq!(decode(&w), Err(ProtocolError::UnsupportedVersion(2)));
    let mut f = frame(MessageType::Heartbeat, vec![]);
    f.flags = 0x80;
    assert_eq!(encode(&f), Err(ProtocolError::ReservedFlags(0x80)));
}

#[test]
fn truncated_frame_is_rejected() {
    let mut w = encode(&frame(MessageType::Hello, vec![0; 20])).unwrap();
    w.pop();
    assert!(matches!(
        decode(&w),
        Err(ProtocolError::InvalidLength { .. })
    ));
}

#[test]
fn stream_parser_resynchronizes_and_stays_bounded() {
    let good = encode(&frame(MessageType::Heartbeat, vec![])).unwrap();
    let mut p = StreamParser::default();
    let junk = vec![0xaa; MAX_FRAME * 2];
    assert!(p.push(&junk).is_empty());
    assert!(p.buffered_len() <= MAX_FRAME);
    let out = p.push(&good);
    assert_eq!(out.len(), 1);
    assert!(out[0].is_ok());
}

#[test]
fn sequence_tracker_reports_gap_duplicate_and_reset() {
    let mut s = SequenceTracker::default();
    assert_eq!(s.observe(10), SequenceEvent::First);
    assert_eq!(s.observe(11), SequenceEvent::InOrder);
    assert_eq!(s.observe(11), SequenceEvent::Duplicate);
    assert_eq!(s.observe(14), SequenceEvent::Gap(2));
    assert_eq!(s.gaps, 2);
    assert_eq!(s.duplicates, 1);
    assert_eq!(s.observe(3), SequenceEvent::Reset);
}

#[test]
fn boot_change_invalidates_session_and_sequence() {
    let mut p = PeerState::default();
    assert!(!p.observe_hello(1));
    p.start_session(42).unwrap();
    assert_eq!(p.sequence.observe(5), SequenceEvent::First);
    assert!(p.observe_hello(2));
    assert_eq!(p.active_session, None);
    assert_eq!(p.sequence.observe(0), SequenceEvent::First);
}

#[test]
fn stale_session_is_rejected() {
    let mut p = PeerState::default();
    p.start_session(7).unwrap();
    assert!(p.validate_session(7).is_ok());
    assert_eq!(p.validate_session(8), Err(ProtocolError::StaleSession));
}

#[test]
fn arbitrary_input_never_grows_parser_beyond_frame_bound() {
    let mut p = StreamParser::default();
    let mut x = 0x1234_5678u32;
    for _ in 0..10000 {
        x = x.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
        let b = (x >> 24) as u8;
        let _ = p.push(&[b]);
        assert!(p.buffered_len() <= MAX_FRAME);
    }
}
