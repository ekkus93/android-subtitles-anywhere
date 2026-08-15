//! Platform-neutral mobile boundary for validated protocol-v1 events.

use crate::protocol::{decode, Frame, MessageType, PeerState, ProtocolError, SequenceEvent};

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum MobileEvent {
    Hello { boot_id: u64, rebooted: bool },
    AudioFormat { session_id: u64, payload: Vec<u8> },
    AudioData { session_id: u64, timestamp_ms: u32, payload: Vec<u8> },
    Status { session_id: u64, payload: Vec<u8> },
    Diagnostics { session_id: u64, payload: Vec<u8> },
    Error { session_id: u64, payload: Vec<u8> },
    SequenceGap { missing: u32 },
    SequenceDuplicate,
    SequenceReset,
}

#[derive(Default)]
pub struct MobileProtocolBoundary {
    peer: PeerState,
}

impl MobileProtocolBoundary {
    /// Validates one complete candidate frame and returns typed mobile events.
    ///
    /// # Errors
    ///
    /// Returns [`ProtocolError`] for malformed/corrupt frames, invalid HELLO payloads,
    /// or session-scoped messages that do not belong to the active session.
    pub fn accept_frame(&mut self, bytes: &[u8]) -> Result<Vec<MobileEvent>, ProtocolError> {
        let frame = decode(bytes)?;
        if frame.message_type == MessageType::Hello {
            return self.accept_hello(&frame);
        }

        if is_session_scoped(frame.message_type) {
            self.peer.validate_session(frame.session_id)?;
        }

        let mut events = Vec::with_capacity(2);
        if is_sequenced(frame.message_type) {
            match self.peer.sequence.observe(frame.sequence) {
                SequenceEvent::First | SequenceEvent::InOrder => {}
                SequenceEvent::Gap(missing) => events.push(MobileEvent::SequenceGap { missing }),
                SequenceEvent::Duplicate => events.push(MobileEvent::SequenceDuplicate),
                SequenceEvent::Reset => events.push(MobileEvent::SequenceReset),
            }
        }
        if let Some(event) = map_frame(frame) {
            events.push(event);
        }
        Ok(events)
    }

    /// Activates the session selected by the platform control plane.
    ///
    /// # Errors
    ///
    /// Returns [`ProtocolError::InvalidField`] if `session_id` is zero.
    pub fn start_session(&mut self, session_id: u64) -> Result<(), ProtocolError> {
        self.peer.start_session(session_id)
    }

    pub fn reset(&mut self) {
        self.peer = PeerState::default();
    }

    fn accept_hello(&mut self, frame: &Frame) -> Result<Vec<MobileEvent>, ProtocolError> {
        let boot_bytes: [u8; 8] = frame.payload[..8]
            .try_into()
            .map_err(|_| ProtocolError::InvalidField)?;
        let boot_id = u64::from_le_bytes(boot_bytes);
        if boot_id == 0 {
            return Err(ProtocolError::InvalidField);
        }
        let rebooted = self.peer.observe_hello(boot_id);
        Ok(vec![MobileEvent::Hello { boot_id, rebooted }])
    }
}

fn is_session_scoped(message: MessageType) -> bool {
    matches!(
        message,
        MessageType::AudioFormat
            | MessageType::AudioData
            | MessageType::Status
            | MessageType::Diagnostics
            | MessageType::Error
    )
}

fn is_sequenced(message: MessageType) -> bool {
    is_session_scoped(message)
}

fn map_frame(frame: Frame) -> Option<MobileEvent> {
    match frame.message_type {
        MessageType::AudioFormat => Some(MobileEvent::AudioFormat {
            session_id: frame.session_id,
            payload: frame.payload,
        }),
        MessageType::AudioData => Some(MobileEvent::AudioData {
            session_id: frame.session_id,
            timestamp_ms: frame.timestamp_ms,
            payload: frame.payload,
        }),
        MessageType::Status => Some(MobileEvent::Status {
            session_id: frame.session_id,
            payload: frame.payload,
        }),
        MessageType::Diagnostics => Some(MobileEvent::Diagnostics {
            session_id: frame.session_id,
            payload: frame.payload,
        }),
        MessageType::Error => Some(MobileEvent::Error {
            session_id: frame.session_id,
            payload: frame.payload,
        }),
        MessageType::Hello
        | MessageType::StartSession
        | MessageType::StopSession
        | MessageType::SetPowerState
        | MessageType::Heartbeat => None,
    }
}
