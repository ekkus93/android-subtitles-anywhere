# Silent Caption v0.1 Specification

**Repository:** `ekkus93/android-subtitles-anywhere`  
**Status:** Initial implementation specification  
**Target:** Android-first, cross-platform architecture with future iOS client  
**Prototype hardware:** ESP32-WROOM-32 development board with onboard USB-UART

## 1. Product summary

Silent Caption is a private, real-time speech-to-text layer for audio played by a mobile device. It is intended for situations where a user wants to consume video, podcasts, audiobooks, streaming radio, browser media, voice content, or other spoken media without making the audio audible to people nearby and without requiring headphones.

The prototype routes the phone's media audio to an ESP32-WROOM-32 acting as a silent Bluetooth Classic A2DP sink. The ESP32 does not drive a speaker. It returns the received digital audio to the same phone through its onboard USB-UART connection. A shared Rust core converts the returned audio into a normalized audio stream, performs ASR, stabilizes partial/final hypotheses, and exposes caption events to the native UI.

The product must not assume that video exists. The primary abstraction is **silent listening**, not video subtitle extraction.

## 2. Goals

1. Caption arbitrary compatible media audio without using the phone microphone.
2. Keep media inaudible to bystanders by routing playback to a silent A2DP sink.
3. Support Android as the v0.1 shipping platform while keeping the core portable to iOS.
4. Keep speech recognition local by default.
5. Provide three user-selectable ASR choices:
   - streaming Zipformer via sherpa-onnx;
   - Whisper Tiny multilingual;
   - Whisper Base multilingual.
6. Provide useful captions in portrait and landscape.
7. Support both captions over another application and a dedicated full-screen transcript reader.
8. Allow the ESP32 to remain physically attached while minimizing idle battery drain.
9. Make privacy-preserving behavior the default: no transcript retention unless enabled by the user.
10. Build a protocol and core that can later support transports other than USB-UART.

## 3. Non-goals for v0.1

- Custom production PCB.
- Cloud ASR as a required path.
- Circumventing DRM or protected-output policy.
- Guaranteed compatibility with every media application or phone vendor.
- Speaker or headphone audio output from the ESP32.
- Microphone-based transcription as the primary path.
- Automatic translation in the initial acceptance gate.
- Full iOS product completion before the Android architecture is proven.

## 4. System architecture

```text
Mobile media application
        |
        | Bluetooth Classic A2DP
        v
ESP32-WROOM-32 DevKit
  A2DP sink; no speaker output
        |
        | USB-UART return stream + control
        v
Native mobile transport adapter
        |
        v
Shared Rust core
  framing -> decode/PCM -> resample/downmix -> VAD -> ASR
        |
        v
Caption engine
  partial/final stabilization + timestamps + history
        |
        +------------------+
        |                  |
        v                  v
Android Kotlin/Compose   Future Swift/SwiftUI
        |
        +-> Floating / Compact / Reader UI
```

### 4.1 Architectural boundaries

- The ESP32 firmware knows nothing about ASR or caption UI.
- The Rust ASR/caption layer does not depend on Android or iOS UI APIs.
- Platform transport adapters feed a versioned wire protocol into the Rust core.
- Android and iOS consume normalized caption/session events and do not implement recognition algorithms.
- Transport, ASR backend, and caption presentation are independently replaceable.

## 5. Prototype hardware

### 5.1 Required hardware

The initial Android proof of concept requires only:

- an ESP32-WROOM-32 development board with onboard USB-UART;
- an Android phone supporting USB host/OTG and Bluetooth Classic A2DP;
- a compatible USB data/OTG cable.

No microphone, DAC, amplifier, speaker, external codec, or additional prototype wiring is required for the baseline experiment.

### 5.2 Hardware discovery items

The implementation must record the exact development-board model, USB-UART chipset, VID/PID, auto-reset wiring, and sustainable UART rates. Common bridge families include CP210x, CH34x/CH910x, and FTDI, but the software must not assume one until the board is identified.

### 5.3 Whole-board power

Power claims must be based on current measured at the USB input, not ESP32 silicon deep-sleep figures alone. The USB-UART bridge, regulator, LEDs, and other board components may remain powered during ESP32 deep sleep.

## 6. ESP32 firmware

### 6.1 Bluetooth role

The ESP32 shall operate as a Bluetooth Classic A2DP sink named clearly enough for the user to select it as the phone's media output. It shall not emit analog audio.

### 6.2 Audio return strategy

Two return strategies must be investigated behind the same protocol abstraction:

1. **Encoded-frame forwarding:** forward usable A2DP/SBC payloads when the ESP-IDF stack exposes them cleanly and the resulting serial bandwidth is reliable.
2. **ASR PCM forwarding:** decode on ESP32, downmix/resample to an ASR-oriented format such as 16 kHz, 16-bit, mono PCM, then forward it.

The prototype shall choose the simpler reliable path based on measurement rather than architectural preference. 16 kHz/16-bit/mono PCM is approximately 256 kbit/s of payload, so 115200 baud is insufficient; higher UART rates such as 921600 baud are candidates and must be validated.

### 6.3 Firmware power states

The firmware shall expose an explicit state machine:

- **ACTIVE:** A2DP/audio-return pipeline active.
- **STANDBY:** short idle period with fast resume where practical.
- **DEEP_SLEEP:** Bluetooth and normal processing stopped to minimize ESP32 consumption.

The phone may request state changes. Firmware shall also have inactivity/failure-safe timeouts so a crashed or disconnected application does not leave the device unnecessarily active.

### 6.4 USB-UART sleep control

While awake, serial commands may request standby, active operation, diagnostics, or deep sleep. True deep-sleep wake cannot assume ordinary UART receive remains available. The prototype shall validate whether the board's USB-UART DTR/RTS/auto-reset wiring can reliably reset/wake the ESP32 from the Android USB host. If not, the limitation shall be documented and a production wake circuit considered later; additional prototype wiring is not a v0.1 prerequisite.

## 7. Wire protocol

The USB return/control protocol shall be versioned, framed, bounded, and corruption-detecting.

Minimum logical message classes:

- HELLO / protocol version / firmware capabilities;
- START_SESSION;
- STOP_SESSION;
- ENTER_STANDBY;
- ENTER_DEEP_SLEEP;
- AUDIO_DATA;
- AUDIO_FORMAT;
- STATUS;
- DIAGNOSTICS;
- ERROR;
- HEARTBEAT where needed.

Each audio frame shall provide enough sequencing/timing information to detect gaps, duplicates, resets, and overruns. Parsers must reject oversized, malformed, unsupported-version, and invalid-length messages without unbounded allocation.

Protocol design must allow future BLE or accessory transports without changing the ASR/caption APIs.

## 8. Shared Rust core

Suggested logical crates/modules:

```text
core/
  protocol/
  transport/
  audio/
  vad/
  asr/
  captions/
  models/
  session/
  diagnostics/
  ffi/
```

Responsibilities include:

- wire-protocol parser and serializer;
- packet sequencing and stream discontinuity handling;
- jitter/ring buffering;
- audio decoding if encoded frames are returned;
- sample-format conversion;
- resampling and mono downmix;
- VAD/utterance segmentation where appropriate;
- ASR backend lifecycle;
- normalized partial/final/error events;
- caption stabilization and segmentation;
- timestamps;
- model metadata and integrity validation;
- session state;
- transcript data model;
- diagnostics and performance counters.

FFI should use a maintainable Rust-to-Kotlin/Swift strategy such as UniFFI where compatible with the selected native ASR runtimes. Platform-specific functionality remains behind native adapters.

## 9. ASR architecture

### 9.1 User-selectable models

The application shall support:

1. **Streaming Zipformer** via a selected sherpa-onnx-compatible package. This is the expected low-latency/default candidate.
2. **Whisper Tiny multilingual** as a lightweight multilingual Whisper option.
3. **Whisper Base multilingual** as a higher-accuracy, higher-resource Whisper option.

Zipformer family/engine identity must be separated from the exact downloadable model package and version because language coverage and checkpoints may change.

### 9.2 Backend contract

All engines shall normalize output into events equivalent to:

- partial hypothesis;
- finalized hypothesis;
- speech start/end where available;
- timestamp/timing metadata where available;
- backend error;
- backend/model state.

Whisper is not natively streaming; its implementation shall use bounded chunking/overlap and caption stabilization rather than pretending it has the same inference semantics as Zipformer.

### 9.3 Model management

Do not bundle all models into the application package. The model manager shall support:

- discover available packages from trusted metadata;
- download on demand;
- show size before download;
- cryptographic integrity verification;
- atomic installation;
- interruption recovery;
- version metadata;
- select installed model;
- delete unused model;
- fail closed on corrupt/incompatible assets.

### 9.4 Benchmarking

Before selecting the default package permanently, benchmark all three choices using the same clean speech fixtures and real devices. Measure:

- word error rate or equivalent accuracy metric;
- time to first useful partial;
- finalization latency;
- real-time factor;
- RAM;
- sustained CPU/GPU/NPU use as applicable;
- battery impact;
- thermal behavior over long sessions.

## 10. Android application

### 10.1 Technology

- Kotlin.
- Jetpack Compose for application UI.
- Android USB Host API for the prototype USB-UART connection.
- Foreground service where required for long-running caption sessions.
- `TYPE_APPLICATION_OVERLAY`/appropriate Android overlay mechanism after explicit user permission for floating captions.
- Rust core exposed through the chosen FFI boundary.

### 10.2 USB adapter

The Android layer shall identify the supported USB-UART device, request permission explicitly, configure a validated serial rate, expose connection/state events, and never silently attach to an arbitrary USB device. Disconnects and reconnects must be recoverable.

### 10.3 Session lifecycle

A normal session is:

1. User connects/leaves connected the Caption Dongle.
2. App establishes USB transport and wakes/initializes firmware as needed.
3. Phone routes media audio to the ESP32 A2DP sink.
4. App verifies return audio is present.
5. User starts listening/captioning.
6. Rust core emits live caption events.
7. User stops; app requests standby.
8. After configured inactivity, firmware may enter deep sleep.

The UI shall distinguish device-connected, A2DP-ready, audio-receiving, ASR-loading, ASR-running, degraded, reconnecting, and error states rather than reducing all failures to a generic spinner.

## 11. Future iOS architecture

iOS portability is a design constraint, not a promise that Android USB-host code will transfer directly. iOS USB/accessory communication is more constrained. Therefore the Rust core must not depend on USB.

Potential future return transports include an approved accessory path, BLE, or another transport supported by the final hardware. The iOS client should use Swift/SwiftUI with the same normalized Rust session/caption APIs where practical.

A dedicated iOS feasibility gate must prove the complete phone -> A2DP -> hardware -> return transport -> same phone loop before substantial iOS UI investment.

## 12. UX principles

1. **Silent listening, not video-only captions.** Podcasts and audio-only content are first-class.
2. **One-action normal use.** After setup, the user should usually open the app and press Start Listening.
3. **Disappear when working.** The overlay/reader is the product; configuration chrome should not dominate.
4. **Readable before branded.** Caption contrast and stability take priority over decorative styling.
5. **Explain state, not implementation.** Show Ready/Receiving/Listening rather than UART/SBC terminology outside diagnostics.
6. **Privacy by default.** Transcript persistence is off unless enabled.

## 13. Visual design

Dark-first palette:

| Role | Hex |
|---|---|
| Background | `#0B0F14` |
| Surface | `#131A22` |
| Elevated surface | `#1B2530` |
| Primary aqua | `#39D6C5` |
| Primary pressed | `#5CE4D5` |
| Primary text | `#F2F6F8` |
| Secondary text | `#9BAAB5` |
| Divider | `#293640` |
| Success | `#58D68D` |
| Warning | `#F4BF4F` |
| Error | `#FF6B6B` |

Caption text should normally be soft white on a highly opaque dark background. Accent color is for controls/state, not continuous caption prose. All combinations must meet applicable accessibility contrast requirements.

## 14. Screens and navigation

Top-level destinations:

- **Live**
- **History**
- **Settings**

### 14.1 First-run/setup flow

1. Welcome/product explanation.
2. Connect Caption Dongle.
3. USB permission/transport verification.
4. Pair/select Caption Dongle as Bluetooth media output.
5. Verify Bluetooth/A2DP state as far as platform APIs permit.
6. Play test audio.
7. Verify return audio with visible level/activity.
8. Run a short ASR test.
9. Grant overlay permission when Floating mode is requested.
10. Finish only after the end-to-end path is verified or present a specific remediation step.

### 14.2 Live screen

Must prominently show:

- dongle readiness;
- audio-return readiness;
- selected ASR model/language;
- Start Listening / Stop control;
- current caption/session state;
- selected display mode;
- concise actionable errors.

### 14.3 Display modes

**Floating:** draggable/resizable captions over another application.  
**Compact:** minimal one/two-line caption bar.  
**Reader:** dedicated full-screen scrolling live transcript for podcasts, audiobooks, or users who do not need another app visible.

### 14.4 Orientation

Portrait and landscape are first-class layouts. Floating/Compact modes shall persist independent portrait and landscape position/size settings. Rotation must not leave the overlay off-screen or covering an unreasonable portion of the display.

Reader mode shall use a comfortable bounded text width and preserve reading position appropriately. If the user scrolls backward, expose a clear **Jump to Live** action rather than forcing auto-scroll.

### 14.5 Overlay interaction

Default overlay should be low-chrome and approximately two lines where space permits. A tap reveals temporary controls for drag/resize, text size, pause/resume, and close. Controls auto-hide. Partial text may be visually differentiated, but updates must prioritize stability and avoid distracting word-by-word jumping.

### 14.6 History

Transcript retention is disabled by default. When enabled, History supports session listing, reading, search if implemented, copy/share/export where platform policy permits, and deletion. The user must be able to delete all retained transcript data.

### 14.7 Settings

Groups:

**Captions**
- appearance;
- text size;
- maximum lines;
- default position;
- display mode.

**Speech**
- ASR model;
- model management;
- language/auto-detection where supported.

**Device**
- Caption Dongle;
- connection state;
- power behavior;
- preferred transport when multiple transports exist.

**Privacy**
- save transcripts, default off;
- retention controls;
- delete history.

**Advanced**
- audio diagnostics;
- ASR diagnostics;
- developer diagnostics/log export where appropriate.

**About**
- version;
- open-source licenses;
- firmware/protocol versions.

## 15. Diagnostics

Diagnostics should expose enough information to debug the complete chain without requiring a debugger:

- USB device/bridge identity;
- transport state and negotiated/configured rate;
- firmware/protocol version;
- ESP32 power state;
- A2DP state where known;
- audio format;
- packet sequence gaps/loss;
- buffer occupancy/overruns/underruns;
- audio level;
- selected ASR backend/model/version;
- model load state;
- real-time factor;
- partial/final latency;
- recoverable/fatal error counters.

Logs must not contain transcript/audio content by default.

## 16. Privacy and security

- Local ASR is the default architecture.
- No audio or transcript is uploaded without a separately specified, explicit feature and consent.
- Transcript persistence defaults off.
- No raw audio persistence during normal operation unless the user explicitly enables a diagnostic recording mode.
- Diagnostic logs redact transcript/audio payloads by default.
- Downloaded model artifacts require integrity verification.
- Protocol parsing is bounded and defensive.
- USB device identity/capabilities are validated before use.
- External content, model metadata, firmware messages, and media-derived text are untrusted input.

## 17. Reliability and failure behavior

The application must fail visibly and specifically for:

- dongle absent;
- USB permission denied;
- unsupported USB-UART bridge;
- serial configuration failure;
- firmware/protocol version mismatch;
- A2DP not connected/selected;
- no return audio;
- malformed/corrupt packets;
- sustained packet loss or overrun;
- model absent/corrupt;
- ASR initialization failure;
- ASR falling behind real time;
- overlay permission denied;
- orientation/display changes;
- USB disconnect/reconnect;
- firmware reboot during session.

Recovery should preserve user intent where safe, but must not silently claim captioning is active when audio or ASR is unavailable.

## 18. Performance targets

Initial targets are engineering goals to validate, not guaranteed product claims:

- sustained real-time processing for all models advertised as usable on the current device;
- useful streaming caption latency preferably below approximately 1 second for the default streaming backend;
- bounded buffers with no unbounded memory growth during multi-hour sessions;
- graceful degradation/error when ASR cannot maintain real time;
- stable two-hour podcast/video test without transport corruption or runaway memory;
- minimal whole-dongle standby/deep-sleep current, measured at USB input.

Exact thresholds shall be tightened after prototype measurements.

## 19. Testing strategy

### 19.1 Host/Rust tests

- protocol encode/decode and malformed-input/property/fuzz coverage;
- packet sequencing/discontinuity handling;
- audio conversion/resampling fixtures;
- bounded buffer behavior;
- ASR backend contract tests;
- caption stabilization tests;
- transcript persistence/privacy tests;
- model manifest/hash validation;
- session state-machine tests.

### 19.2 Firmware tests

- state transitions;
- command parser bounds;
- A2DP lifecycle;
- audio forwarding;
- UART saturation/backpressure;
- reconnect/reboot behavior;
- standby/deep-sleep entry;
- wake/reset behavior on the actual board.

### 19.3 Android tests

- USB permission and attach/detach;
- supported/unsupported device handling;
- foreground lifecycle;
- overlay permission and rendering;
- portrait/landscape persistence;
- Reader/Compact/Floating behavior;
- model selection/download/delete;
- process recreation;
- accessibility semantics;
- transcript retention default-off behavior.

### 19.4 Hardware-in-loop acceptance

The critical first gate is:

```text
Android media -> Bluetooth A2DP -> ESP32 -> USB-UART -> Android
```

Capture returned audio and prove that intelligible source audio can be reconstructed/processed while the ESP32 emits no audible output. ASR and polished UI are not prerequisites for this gate.

## 20. v0.1 acceptance criteria

v0.1 is acceptable when all of the following are demonstrated on documented test hardware:

1. Android routes ordinary compatible media audio to the ESP32 A2DP sink with no speaker attached/output.
2. The ESP32 returns a stable digital audio stream to the same Android phone over onboard USB-UART using only the development board and data/OTG cable.
3. The Rust core processes the returned stream without unbounded buffering.
4. All three selectable ASR backends can be installed/selected and produce captions on supported hardware.
5. Floating, Compact, and Reader modes function.
6. Portrait and landscape layouts are usable and independently persisted where required.
7. Transcript retention is off by default and deletion works when enabled.
8. Disconnect, protocol, missing-model, and ASR-behind-real-time failures are visible and recoverable where specified.
9. ESP32 Active/Standby/Deep-Sleep behavior is implemented and whole-board current is measured/documented.
10. A sustained session passes the defined soak test without runaway memory, silent audio loss, or application crash.
11. The Rust/platform boundary remains transport- and ASR-backend-neutral enough to support the planned iOS feasibility work.

## 21. Open validation items

These are deliberate prototype questions, not unspecified architecture:

- exact ESP32 development board and USB-UART bridge/wiring;
- encoded SBC forwarding versus ESP32-side PCM conversion;
- maximum reliable UART rate with the chosen Android device/bridge;
- DTR/RTS/reset behavior for deep-sleep wake;
- total USB current in Active/Standby/Deep-Sleep;
- exact Zipformer model package/language coverage;
- final Whisper runtime/integration choice;
- device-specific ASR latency, accuracy, thermal, and battery results;
- behavior across representative media/DRM applications;
- final iOS hardware return transport.

Each open item has a corresponding gated task in `SILENT_CAPTION_V01_TODO.md` and must be resolved by evidence rather than assumption.