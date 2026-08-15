# Silent Caption Wire Protocol v1

**TODO:** SC-021 through SC-030  
**Status:** normative v1 definition

## 1. Scope and byte order

Protocol v1 is transport-neutral. It carries control, status, diagnostics, and audio between the mobile host and dongle. USB/UART packetization is not part of this document.

All multi-byte integers are unsigned little-endian unless explicitly stated. Unknown enum values are invalid unless this document explicitly permits them.

## 2. Frame

Every frame has a fixed 32-byte header followed by `payload_length` bytes.

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 4 | magic = ASCII `SCAP` (`53 43 41 50`) |
| 4 | 1 | protocol major = `1` |
| 5 | 1 | protocol minor = `0` |
| 6 | 1 | message type |
| 7 | 1 | flags |
| 8 | 4 | payload length |
| 12 | 4 | sequence number |
| 16 | 8 | session ID |
| 24 | 4 | timestamp, milliseconds |
| 28 | 4 | CRC-32/ISO-HDLC over bytes 0..27 followed by payload |

Maximum payload is **4096 bytes**; maximum complete frame is **4128 bytes**. Receivers MUST reject a larger declared payload before allocating storage for it.

Sequence numbers are independent per transmitting endpoint, start at zero after boot, and increment modulo 2^32 for every transmitted frame. A duplicate sequence number is ignored after diagnostics are updated. A forward gap is reported as a discontinuity. A backward/non-wrap sequence is treated as peer reset unless a new HELLO has already established it.

Timestamp is the sender's monotonic milliseconds modulo 2^32. It is for ordering/latency diagnostics, not wall-clock time. AUDIO_DATA timestamps refer to the first audio sample represented by the payload when that timing is available.

CRC uses CRC-32/ISO-HDLC (`poly=0x04C11DB7`, reflected input/output, init/xorout `0xFFFFFFFF`; common reflected implementation polynomial `0xEDB88320`). The four CRC bytes in the header are treated as absent: calculate over header bytes 0..27 and then payload.

## 3. Flags

| Bit | Name | Meaning |
| ---: | --- | --- |
| 0 | ACK_REQUIRED | receiver must answer with STATUS or ERROR referencing the operation where applicable |
| 1 | DISCONTINUITY | stream continuity was lost before this frame |
| 2..7 | reserved | transmitter MUST send zero; v1 receiver MUST reject nonzero reserved bits |

## 4. Message types

| Value | Message |
| ---: | --- |
| `0x01` | HELLO |
| `0x02` | START_SESSION |
| `0x03` | STOP_SESSION |
| `0x04` | SET_POWER_STATE |
| `0x05` | HEARTBEAT |
| `0x10` | AUDIO_FORMAT |
| `0x11` | AUDIO_DATA |
| `0x20` | STATUS |
| `0x21` | DIAGNOSTICS |
| `0x7F` | ERROR |

Unsupported message types MUST produce `ERROR(UNSUPPORTED_MESSAGE)` when a response is possible and MUST NOT alter session state.

## 5. HELLO and capabilities

HELLO payload is 20 bytes:

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 8 | boot ID: fresh nonzero random value per boot where entropy is available |
| 8 | 4 | capability bits |
| 12 | 2 | maximum RX payload |
| 14 | 2 | maximum TX payload |
| 16 | 1 | minimum supported major |
| 17 | 1 | maximum supported major |
| 18 | 1 | firmware major |
| 19 | 1 | firmware minor |

Capability bits: bit 0 PCM audio, bit 1 SBC audio, bit 2 standby, bit 3 deep sleep, bit 4 diagnostics. Unknown capability bits are ignored.

Both endpoints send HELLO after transport establishment. A peer that cannot support protocol major 1 terminates the logical session and reports an unsupported-version condition to its platform.

## 6. Session control

START_SESSION payload is 8 bytes containing a new, host-generated, nonzero session ID. The frame header session ID MUST contain the same value. Reusing a previous session ID after stop/reconnect is forbidden.

STOP_SESSION payload is empty; header session ID identifies the session. It is idempotent. Frames carrying a stale nonzero session ID MUST NOT be accepted as current-session audio/control.

Session ID zero means no active session and is valid for HELLO, heartbeat, diagnostics, and pre-session status/error traffic.

## 7. Power

SET_POWER_STATE payload is one byte:

- `0` ACTIVE
- `1` STANDBY
- `2` DEEP_SLEEP

The receiver reports the resulting/requested state using STATUS before entering a state that prevents further acknowledgement. Unsupported states return ERROR and do not silently substitute another state.

## 8. Audio format

AUDIO_FORMAT payload is 12 bytes:

| Offset | Size | Field |
| ---: | ---: | --- |
| 0 | 1 | representation: `1` PCM, `2` SBC |
| 1 | 1 | PCM sample encoding: `0` N/A, `1` signed 16-bit LE |
| 2 | 1 | channels (`1` or `2`) |
| 3 | 1 | reserved = 0 |
| 4 | 4 | sample rate Hz |
| 8 | 2 | samples per channel represented by a normal AUDIO_DATA payload, 0 if variable |
| 10 | 2 | codec-specific flags, zero unless defined by a later compatible extension |

AUDIO_FORMAT MUST precede AUDIO_DATA after session start and whenever the format changes. Format change is a stream discontinuity.

AUDIO_DATA payload is raw bytes in the active AUDIO_FORMAT representation. It MUST be nonempty and no larger than 4096 bytes. PCM payload size MUST be an integral number of complete interleaved sample frames. SBC payload boundaries are preserved when the selected implementation can expose them; exact SBC framing policy is finalized by SC-081..087 without changing the v1 envelope.

## 9. Status, diagnostics, errors, heartbeat

HEARTBEAT has no payload. Either endpoint may send it when otherwise idle. Heartbeat does not prove media/audio readiness.

STATUS payload is 8 bytes:

- byte 0: lifecycle state: 0 unavailable, 1 ready, 2 active, 3 standby, 4 sleeping, 5 error
- byte 1: A2DP state: 0 unavailable/disconnected, 1 connected, 2 streaming
- byte 2: power state: 0 active, 1 standby, 2 deep sleep
- byte 3: reserved = 0
- bytes 4..7: status flags (bit 0 format valid, bit 1 audio flowing, remaining bits reserved)

DIAGNOSTICS payload is 32 bytes containing eight u32 counters in order: RX frames, TX frames, sequence gaps, duplicates, CRC failures, malformed frames, audio drops, queue high-water bytes. Counters saturate at `0xFFFFFFFF` rather than wrapping.

ERROR payload begins with a u16 code and u8 offending message type, followed by u8 detail length and that many UTF-8 diagnostic bytes. Detail length is at most 160 and is diagnostic only; software behavior depends on the numeric code.

Error codes: 1 malformed frame, 2 unsupported version, 3 unsupported message, 4 invalid state, 5 stale session, 6 unsupported capability/format, 7 busy/resource limit, 8 integrity failure, 9 internal failure.

## 10. Parser requirements

A stream parser MUST:

1. search for exact `SCAP` magic when unsynchronized;
2. consume no unbounded memory while searching;
3. reject unsupported major versions, reserved flags, lengths >4096, invalid fixed payload lengths, and invalid enum/reserved fields;
4. validate CRC before exposing a frame to stateful logic;
5. recover after malformed/corrupt input by bounded resynchronization to a later magic sequence;
6. report CRC/malformed/gap/duplicate counters without treating diagnostic counters as proof of success;
7. never allocate based solely on an unvalidated wire length;
8. reject stale-session AUDIO_DATA/control before it reaches audio/ASR state;
9. clear active format/session stream state on peer boot-ID change;
10. treat duplicate frames as duplicates rather than replaying their effects.

## 11. Reboot and reconnect

HELLO boot ID distinguishes a restarted peer from transport churn. A changed boot ID invalidates sequence-history assumptions, active audio format, and any dongle-side session state. The host creates a new session ID before restarting audio. A reconnect with the same boot ID may retain diagnostics but MUST still require explicit session/format state before accepting audio if continuity cannot be proven.

## 12. Test vectors

Canonical machine-readable vectors live in `protocol/test_vectors_v1.json`. Numeric fields in that file are accompanied by complete hexadecimal wire frames so Rust, firmware host tests, Android fixtures, and later device tests can consume identical bytes.
