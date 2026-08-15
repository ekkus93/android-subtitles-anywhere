use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jbyteArray, jlong};
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
        self.boundaries
            .insert(handle, MobileProtocolBoundary::default());
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
    pub kind: u8,
    pub session_id: u64,
    pub timestamp_ms: u32,
    pub numeric_value: u64,
    pub payload: Vec<u8>,
}

impl From<MobileEvent> for FfiEvent {
    fn from(event: MobileEvent) -> Self {
        match event {
            MobileEvent::Hello { boot_id, rebooted } => Self {
                kind: 1,
                session_id: 0,
                timestamp_ms: 0,
                numeric_value: boot_id,
                payload: vec![u8::from(rebooted)],
            },
            MobileEvent::AudioFormat {
                session_id,
                payload,
            } => Self::payload(2, session_id, 0, payload),
            MobileEvent::AudioData {
                session_id,
                timestamp_ms,
                payload,
            } => Self::payload(3, session_id, timestamp_ms, payload),
            MobileEvent::Status {
                session_id,
                payload,
            } => Self::payload(4, session_id, 0, payload),
            MobileEvent::Diagnostics {
                session_id,
                payload,
            } => Self::payload(5, session_id, 0, payload),
            MobileEvent::Error {
                session_id,
                payload,
            } => Self::payload(6, session_id, 0, payload),
            MobileEvent::SequenceGap { missing } => Self::numeric(7, missing.into()),
            MobileEvent::SequenceDuplicate => Self::numeric(8, 0),
            MobileEvent::SequenceReset => Self::numeric(9, 0),
        }
    }
}

impl FfiEvent {
    fn payload(kind: u8, session_id: u64, timestamp_ms: u32, payload: Vec<u8>) -> Self {
        Self {
            kind,
            session_id,
            timestamp_ms,
            numeric_value: 0,
            payload,
        }
    }

    fn numeric(kind: u8, numeric_value: u64) -> Self {
        Self {
            kind,
            session_id: 0,
            timestamp_ms: 0,
            numeric_value,
            payload: Vec::new(),
        }
    }
}

pub fn create_boundary() -> u64 {
    BOUNDARIES.lock().expect("boundary store poisoned").create()
}

pub fn destroy_boundary(handle: u64) -> bool {
    BOUNDARIES
        .lock()
        .expect("boundary store poisoned")
        .destroy(handle)
}

pub fn reset_boundary(handle: u64) -> bool {
    BOUNDARIES
        .lock()
        .expect("boundary store poisoned")
        .reset(handle)
}

pub fn start_session(handle: u64, session_id: u64) -> Result<(), FfiError> {
    BOUNDARIES
        .lock()
        .expect("boundary store poisoned")
        .start_session(handle, session_id)
}

pub fn accept_frame(handle: u64, frame: &[u8]) -> Result<Vec<FfiEvent>, FfiError> {
    BOUNDARIES
        .lock()
        .expect("boundary store poisoned")
        .accept_frame(handle, frame)
}

fn encode_result(result: Result<Vec<FfiEvent>, FfiError>) -> Vec<u8> {
    match result {
        Ok(events) => {
            let mut output = vec![0, u8::try_from(events.len()).unwrap_or(u8::MAX)];
            for event in events.into_iter().take(usize::from(u8::MAX)) {
                output.push(event.kind);
                output.extend_from_slice(&event.session_id.to_le_bytes());
                output.extend_from_slice(&event.timestamp_ms.to_le_bytes());
                output.extend_from_slice(&event.numeric_value.to_le_bytes());
                let length = u32::try_from(event.payload.len()).unwrap_or(u32::MAX);
                output.extend_from_slice(&length.to_le_bytes());
                output.extend_from_slice(&event.payload);
            }
            output
        }
        Err(error) => {
            let code = match error {
                FfiError::InvalidHandle => 1,
                FfiError::Protocol(ProtocolError::Integrity) => 2,
                FfiError::Protocol(ProtocolError::StaleSession) => 3,
                FfiError::Protocol(_) => 4,
            };
            vec![code, 0]
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_ekkus93_silentcaption_usb_NativeRustProtocolApi_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    jlong::try_from(create_boundary()).unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_ekkus93_silentcaption_usb_NativeRustProtocolApi_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let Ok(handle) = u64::try_from(handle) else {
        return 0;
    };
    u8::from(handle > 0 && destroy_boundary(handle))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_ekkus93_silentcaption_usb_NativeRustProtocolApi_nativeReset(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let Ok(handle) = u64::try_from(handle) else {
        return 0;
    };
    u8::from(handle > 0 && reset_boundary(handle))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_ekkus93_silentcaption_usb_NativeRustProtocolApi_nativeStartSession(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    session_id: jlong,
) -> jboolean {
    let (Ok(handle), Ok(session_id)) = (u64::try_from(handle), u64::try_from(session_id)) else {
        return 0;
    };
    u8::from(handle > 0 && session_id > 0 && start_session(handle, session_id).is_ok())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_ekkus93_silentcaption_usb_NativeRustProtocolApi_nativeAcceptFrame(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    frame: JByteArray,
) -> jbyteArray {
    let result = match u64::try_from(handle) {
        Ok(handle) if handle > 0 => env
            .convert_byte_array(&frame)
            .map_err(|_| FfiError::InvalidHandle)
            .and_then(|bytes| accept_frame(handle, &bytes)),
        _ => Err(FfiError::InvalidHandle),
    };
    match env.byte_array_from_slice(&encode_result(result)) {
        Ok(array) => array.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
