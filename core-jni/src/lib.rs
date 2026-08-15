use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use silent_caption_core::mobile::{MobileEvent, MobileProtocolBoundary};
use silent_caption_core::protocol::ProtocolError;

static BOUNDARIES: LazyLock<Mutex<BoundaryStore>> =
    LazyLock::new(|| Mutex::new(BoundaryStore::default()));

#[derive(Default)]
struct BoundaryStore {
    next_handle: u64,
    boundaries: HashMap<u64, MobileProtocolBoundary>,
}

impl BoundaryStore {
    fn create(&mut self) -> u64 {
        self.next_handle = self.next_handle.saturating_add(1).max(1);
        let handle = self.next_handle;
        self.boundaries.insert(handle, MobileProtocolBoundary::default());
        handle
    }

    fn destroy(&mut self, handle: u64) -> bool {
        self.boundaries.remove(&handle).is_some()
    }

    fn reset(&mut self, handle: u64) -> bool {
        let Some(boundary) = self.boundaries.get_mut(&handle) else {
            return false;
        };
        boundary.reset();
        true
    }

    fn start_session(&mut self, handle: u64, session_id: u64) -> Result<(), FfiError> {
        self.boundaries
            .get_mut(&handle)
            .ok_or(FfiError::InvalidHandle)?
            .start_session(session_id)
            .map_err(FfiError::Protocol)
    }

    fn accept_frame(&mut self, handle: u64, frame: &[u8]) -> Result<Vec<FfiEvent>, FfiError> {
        let events = self
            .boundaries
            .get_mut(&handle)
            .ok_or(FfiError::InvalidHandle)?
            .accept_frame(frame)
            .map_err(FfiError::Protocol)?;
        Ok(events.into_iter().map(FfiEvent::from).collect())
    }
}

#[derive(Debug, Eq, PartialEq)]
pub enum FfiError {
    InvalidHandle,
    Protocol(ProtocolError),
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FfiEvent {
    pub kind: &'static str,
    pub session_id: u64,
    pub timestamp_ms: u32,
    pub numeric_value: u64,
    pub payload: Vec<u8>,
}

impl From<MobileEvent> for FfiEvent {
    fn from(event: MobileEvent) -> Self {
        match event {
            MobileEvent::Hello { boot_id, rebooted } => Self {
                kind: "hello",
                session_id: 0,
                timestamp_ms: 0,
                numeric_value: boot_id,
                payload: vec![u8::from(rebooted)],
            },
            MobileEvent::AudioFormat { session_id, payload } => {
                Self::payload("audio_format", session_id, 0, payload)
            }
            MobileEvent::AudioData {
                session_id,
                timestamp_ms,
                payload,
            } => Self::payload("audio_data", session_id, timestamp_ms, payload),
            MobileEvent::Status { session_id, payload } => {
                Self::payload("status", session_id, 0, payload)
            }
            MobileEvent::Diagnostics { session_id, payload } => {
                Self::payload("diagnostics", session_id, 0, payload)
            }
            MobileEvent::Error { session_id, payload } => {
                Self::payload("error", session_id, 0, payload)
            }
            MobileEvent::SequenceGap { missing } => Self::numeric("sequence_gap", missing.into()),
            MobileEvent::SequenceDuplicate => Self::numeric("sequence_duplicate", 0),
            MobileEvent::SequenceReset => Self::numeric("sequence_reset", 0),
        }
    }
}

impl FfiEvent {
    fn payload(kind: &'static str, session_id: u64, timestamp_ms: u32, payload: Vec<u8>) -> Self {
        Self { kind, session_id, timestamp_ms, numeric_value: 0, payload }
    }

    fn numeric(kind: &'static str, numeric_value: u64) -> Self {
        Self { kind, session_id: 0, timestamp_ms: 0, numeric_value, payload: Vec::new() }
    }
}

pub fn create_boundary() -> u64 {
    BOUNDARIES.lock().expect("boundary store poisoned").create()
}

pub fn destroy_boundary(handle: u64) -> bool {
    BOUNDARIES.lock().expect("boundary store poisoned").destroy(handle)
}

pub fn reset_boundary(handle: u64) -> bool {
    BOUNDARIES.lock().expect("boundary store poisoned").reset(handle)
}

pub fn start_session(handle: u64, session_id: u64) -> Result<(), FfiError> {
    BOUNDARIES.lock().expect("boundary store poisoned").start_session(handle, session_id)
}

pub fn accept_frame(handle: u64, frame: &[u8]) -> Result<Vec<FfiEvent>, FfiError> {
    BOUNDARIES.lock().expect("boundary store poisoned").accept_frame(handle, frame)
}
