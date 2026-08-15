//! Bounded codec and stream parser for Silent Caption wire protocol v1.

use std::collections::VecDeque;

pub const MAGIC: [u8; 4] = *b"SCAP";
pub const HEADER_LEN: usize = 32;
pub const MAX_PAYLOAD: usize = 4096;
pub const MAX_FRAME: usize = HEADER_LEN + MAX_PAYLOAD;
pub const PROTOCOL_MAJOR: u8 = 1;
pub const PROTOCOL_MINOR: u8 = 0;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
#[repr(u8)]
pub enum MessageType {
    Hello = 0x01,
    StartSession = 0x02,
    StopSession = 0x03,
    SetPowerState = 0x04,
    Heartbeat = 0x05,
    AudioFormat = 0x10,
    AudioData = 0x11,
    Status = 0x20,
    Diagnostics = 0x21,
    Error = 0x7f,
}

impl TryFrom<u8> for MessageType {
    type Error = ProtocolError;

    fn try_from(v: u8) -> Result<Self, ProtocolError> {
        Ok(match v {
            1 => Self::Hello,
            2 => Self::StartSession,
            3 => Self::StopSession,
            4 => Self::SetPowerState,
            5 => Self::Heartbeat,
            0x10 => Self::AudioFormat,
            0x11 => Self::AudioData,
            0x20 => Self::Status,
            0x21 => Self::Diagnostics,
            0x7f => Self::Error,
            _ => return Err(ProtocolError::UnsupportedMessage(v)),
        })
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Frame {
    pub message_type: MessageType,
    pub flags: u8,
    pub sequence: u32,
    pub session_id: u64,
    pub timestamp_ms: u32,
    pub payload: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub enum ProtocolError {
    UnsupportedVersion(u8),
    UnsupportedMessage(u8),
    ReservedFlags(u8),
    OversizedPayload(u32),
    InvalidLength { message: MessageType, actual: usize },
    Integrity,
    InvalidField,
    StaleSession,
}

fn valid_length(t: MessageType, n: usize) -> bool {
    match t {
        MessageType::Hello => n == 20,
        MessageType::StartSession | MessageType::Status => n == 8,
        MessageType::StopSession | MessageType::Heartbeat => n == 0,
        MessageType::SetPowerState => n == 1,
        MessageType::AudioFormat => n == 12,
        MessageType::AudioData => n > 0,
        MessageType::Diagnostics => n == 32,
        MessageType::Error => (4..=164).contains(&n),
    }
}

/// Computes the CRC-32/ISO-HDLC checksum over a sequence of byte slices.
#[must_use]
pub fn crc32(parts: &[&[u8]]) -> u32 {
    let mut crc = 0xffff_ffffu32;
    for part in parts {
        for &b in *part {
            crc ^= u32::from(b);
            for _ in 0..8 {
                crc = if crc & 1 != 0 {
                    (crc >> 1) ^ 0xedb8_8320
                } else {
                    crc >> 1
                };
            }
        }
    }
    !crc
}

/// Encodes one validated protocol frame.
///
/// # Errors
///
/// Returns [`ProtocolError`] when flags, payload size, or message-specific payload length are invalid.
pub fn encode(frame: &Frame) -> Result<Vec<u8>, ProtocolError> {
    if frame.flags & !0x03 != 0 {
        return Err(ProtocolError::ReservedFlags(frame.flags));
    }
    if frame.payload.len() > MAX_PAYLOAD {
        let reported_len = u32::try_from(frame.payload.len()).unwrap_or(u32::MAX);
        return Err(ProtocolError::OversizedPayload(reported_len));
    }
    if !valid_length(frame.message_type, frame.payload.len()) {
        return Err(ProtocolError::InvalidLength {
            message: frame.message_type,
            actual: frame.payload.len(),
        });
    }
    let payload_len = u32::try_from(frame.payload.len()).map_err(|_| ProtocolError::InvalidField)?;
    let mut out = Vec::with_capacity(HEADER_LEN + frame.payload.len());
    out.extend_from_slice(&MAGIC);
    out.extend_from_slice(&[
        PROTOCOL_MAJOR,
        PROTOCOL_MINOR,
        frame.message_type as u8,
        frame.flags,
    ]);
    out.extend_from_slice(&payload_len.to_le_bytes());
    out.extend_from_slice(&frame.sequence.to_le_bytes());
    out.extend_from_slice(&frame.session_id.to_le_bytes());
    out.extend_from_slice(&frame.timestamp_ms.to_le_bytes());
    let c = crc32(&[&out, &frame.payload]);
    out.extend_from_slice(&c.to_le_bytes());
    out.extend_from_slice(&frame.payload);
    Ok(out)
}

/// Decodes and validates exactly one complete protocol frame.
///
/// # Errors
///
/// Returns [`ProtocolError`] for malformed, unsupported, oversized, truncated, or corrupt frames.
///
/// # Panics
///
/// This function does not panic for externally supplied input. Fixed-size field conversions occur only
/// after the minimum header length has been validated.
pub fn decode(bytes: &[u8]) -> Result<Frame, ProtocolError> {
    if bytes.len() < HEADER_LEN || bytes[..4] != MAGIC {
        return Err(ProtocolError::InvalidField);
    }
    if bytes[4] != PROTOCOL_MAJOR {
        return Err(ProtocolError::UnsupportedVersion(bytes[4]));
    }
    let t = MessageType::try_from(bytes[6])?;
    let flags = bytes[7];
    if flags & !0x03 != 0 {
        return Err(ProtocolError::ReservedFlags(flags));
    }
    let n = u32::from_le_bytes(bytes[8..12].try_into().expect("slice length"));
    if n as usize > MAX_PAYLOAD {
        return Err(ProtocolError::OversizedPayload(n));
    }
    let total = HEADER_LEN + n as usize;
    if bytes.len() != total {
        return Err(ProtocolError::InvalidLength {
            message: t,
            actual: bytes.len().saturating_sub(HEADER_LEN),
        });
    }
    if !valid_length(t, n as usize) {
        return Err(ProtocolError::InvalidLength {
            message: t,
            actual: n as usize,
        });
    }
    let expected = u32::from_le_bytes(bytes[28..32].try_into().expect("slice length"));
    if crc32(&[&bytes[..28], &bytes[32..]]) != expected {
        return Err(ProtocolError::Integrity);
    }
    Ok(Frame {
        message_type: t,
        flags,
        sequence: u32::from_le_bytes(bytes[12..16].try_into().expect("slice length")),
        session_id: u64::from_le_bytes(bytes[16..24].try_into().expect("slice length")),
        timestamp_ms: u32::from_le_bytes(bytes[24..28].try_into().expect("slice length")),
        payload: bytes[32..].to_vec(),
    })
}

#[derive(Default)]
pub struct StreamParser {
    buf: VecDeque<u8>,
}

impl StreamParser {
    /// Returns the number of bytes currently retained by the bounded parser.
    #[must_use]
    pub fn buffered_len(&self) -> usize {
        self.buf.len()
    }

    /// Adds stream bytes and returns every complete frame or parse error discovered.
    ///
    /// # Panics
    ///
    /// This method does not panic for externally supplied input. Header field conversion occurs only
    /// after a complete fixed-size header has been buffered.
    pub fn push(&mut self, data: &[u8]) -> Vec<Result<Frame, ProtocolError>> {
        for &b in data {
            if self.buf.len() < MAX_FRAME {
                self.buf.push_back(b);
            } else {
                self.buf.pop_front();
                self.buf.push_back(b);
            }
        }
        let mut out = Vec::new();
        loop {
            while self.buf.len() >= 4 && !self.buf.iter().take(4).copied().eq(MAGIC) {
                self.buf.pop_front();
            }
            if self.buf.len() < HEADER_LEN {
                break;
            }
            let h: Vec<u8> = self.buf.iter().take(HEADER_LEN).copied().collect();
            let n = u32::from_le_bytes(h[8..12].try_into().expect("slice length"));
            if n as usize > MAX_PAYLOAD {
                out.push(Err(ProtocolError::OversizedPayload(n)));
                self.buf.pop_front();
                continue;
            }
            let total = HEADER_LEN + n as usize;
            if self.buf.len() < total {
                break;
            }
            let raw: Vec<u8> = self.buf.drain(..total).collect();
            out.push(decode(&raw));
        }
        out
    }
}

#[derive(Default, Debug, Eq, PartialEq)]
pub struct SequenceTracker {
    last: Option<u32>,
    pub gaps: u32,
    pub duplicates: u32,
}

#[derive(Debug, Eq, PartialEq)]
pub enum SequenceEvent {
    First,
    InOrder,
    Gap(u32),
    Duplicate,
    Reset,
}

impl SequenceTracker {
    pub fn observe(&mut self, n: u32) -> SequenceEvent {
        let Some(last) = self.last else {
            self.last = Some(n);
            return SequenceEvent::First;
        };
        if n == last {
            self.duplicates = self.duplicates.saturating_add(1);
            return SequenceEvent::Duplicate;
        }
        let expected = last.wrapping_add(1);
        self.last = Some(n);
        if n == expected {
            SequenceEvent::InOrder
        } else {
            let forward = n.wrapping_sub(expected);
            if forward < 0x8000_0000 {
                self.gaps = self.gaps.saturating_add(forward);
                SequenceEvent::Gap(forward)
            } else {
                SequenceEvent::Reset
            }
        }
    }

    pub fn reset(&mut self) {
        self.last = None;
    }
}

#[derive(Default)]
pub struct PeerState {
    pub boot_id: Option<u64>,
    pub active_session: Option<u64>,
    pub sequence: SequenceTracker,
}

impl PeerState {
    pub fn observe_hello(&mut self, boot_id: u64) -> bool {
        let changed = self.boot_id.is_some_and(|old| old != boot_id);
        if changed {
            self.active_session = None;
            self.sequence.reset();
        }
        self.boot_id = Some(boot_id);
        changed
    }

    /// Activates a nonzero session identifier.
    ///
    /// # Errors
    ///
    /// Returns [`ProtocolError::InvalidField`] when `id` is zero.
    pub fn start_session(&mut self, id: u64) -> Result<(), ProtocolError> {
        if id == 0 {
            return Err(ProtocolError::InvalidField);
        }
        self.active_session = Some(id);
        Ok(())
    }

    /// Validates that a message belongs to the active session.
    ///
    /// # Errors
    ///
    /// Returns [`ProtocolError::StaleSession`] when `id` is not the active session.
    pub fn validate_session(&self, id: u64) -> Result<(), ProtocolError> {
        if self.active_session == Some(id) {
            Ok(())
        } else {
            Err(ProtocolError::StaleSession)
        }
    }
}
