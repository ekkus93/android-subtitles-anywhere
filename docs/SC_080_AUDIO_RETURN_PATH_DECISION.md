# SC-080 Audio Return-Path Decision

Status: software analysis complete; hardware measurements pending.

## Context

Silent Caption must return audio received by the ESP32-WROOM-32 A2DP sink to the Android phone over the prototype's USB-UART bridge. The candidate representations are encoded SBC frames and decoded signed 16-bit PCM.

The project is pinned to ESP-IDF v5.5.5.

## SC-081 — Stable encoded SBC access

ESP-IDF v5.5 exposes a public A2DP sink API for undecoded audio: `esp_a2d_sink_register_audio_data_callback`. The callback receives an `esp_a2d_audio_buff_t` containing encoded frames, frame count, timestamp, data length, and data pointer. The buffer is owned by the application after callback delivery and must be released with `esp_a2d_audio_buff_free`.

This means encoded SBC access does **not** require private Bluedroid hooks on the pinned ESP-IDF release. It does require the external-codec A2DP configuration (`CONFIG_BT_A2DP_USE_EXTERNAL_CODEC=y`).

Conclusion: SC-081 is software-verifiably viable.

## SC-082 — Encoded forwarding prototype

The encoded candidate is represented by a transport-neutral `sc_return_audio_block_t` descriptor and bandwidth model in `return_audio.h/.c`. It can carry opaque SBC payloads with a source timestamp and frame count. The production A2DP callback is deliberately not switched to external-codec mode yet because doing so disables the currently useful decoded-PCM callback and would make the comparison hardware-dependent before SC-G4.

The public encoded callback is therefore a viable production candidate, not the default implementation at this stage.

## SC-083 — Decoded PCM forwarding prototype

The existing A2DP sink uses `esp_a2d_sink_register_data_callback`, whose public contract supplies SBC-decoded PCM. The bounded callback queue is the PCM forwarding prototype. PCM can therefore be returned without adding an SBC decoder to Android/Rust.

## SC-084 — UART throughput model

Hardware throughput/error/CPU measurements remain pending. Software calculations use standard asynchronous UART 8N1 framing: 10 wire bits per payload byte.

At 921600 baud, the theoretical payload ceiling is 92,160 bytes/s before protocol framing and scheduling overhead.

Representative raw PCM requirements:

| Representation | Payload bytes/s | Fits 921600 8N1 theoretically? |
| --- | ---: | --- |
| 48 kHz, stereo, s16 | 192,000 | No |
| 44.1 kHz, stereo, s16 | 176,400 | No |
| 48 kHz, mono, s16 | 96,000 | No |
| 44.1 kHz, mono, s16 | 88,200 | Barely, with inadequate margin |
| 16 kHz, mono, s16 | 32,000 | Yes, substantial margin |

Encoded SBC is normally far below raw PCM bandwidth, but its exact negotiated bitrate must be measured from real A2DP sessions rather than assumed.

## SC-085 — Where PCM conversion belongs

For a PCM return path, forwarding native 44.1/48 kHz stereo PCM at 921600 baud is impossible. Even 44.1 kHz mono leaves little framing/scheduling margin. Therefore a PCM default would require ESP32-side conversion to canonical 16 kHz mono before UART, or a materially faster verified UART rate.

The Rust core already owns deterministic canonical PCM conversion. Duplicating resampling on ESP32 increases firmware CPU cost and creates two conversion implementations. Conversely, sending native PCM to the phone violates the initial 921600-baud budget.

This tension favors encoded SBC if hardware validates the external-codec callback and Android-side decoding path.

## SC-086 — Candidate comparison

| Criterion | Encoded SBC | Decoded PCM |
| --- | --- | --- |
| UART bandwidth | Strong | Poor unless ESP32 downmix/resample |
| ESP32 CPU | Avoids decode; favorable | A2DP decode plus possible conversion |
| Android/Rust complexity | Requires SBC decoder | Simple PCM ingestion |
| Fidelity to received A2DP stream | Preserves encoded frames | Already decoded/transformed |
| Public ESP-IDF 5.5 API | Yes, external-codec callback | Yes, decoded-data callback |
| Portability across older ESP-IDF | Weaker | Stronger |
| 921600-baud headroom | Expected strong; measure | Native stream generally insufficient |
| Hardware evidence | Pending | Pending |

## SC-087 — Decision state

**Provisional V0.1 preference: encoded SBC return.**

Rationale: ESP-IDF 5.5.5 provides a public encoded-frame callback, avoiding the private-hook risk that originally motivated the experiment, while SBC substantially reduces the UART bandwidth problem and avoids decoding/resampling work on the ESP32.

This is intentionally **not yet the final Gate SC-G5 decision**. SC-G5 explicitly requires measurements. The default firmware remains on decoded PCM until physical tests establish:

1. encoded callback behavior with the Android source;
2. actual negotiated SBC bitrate/frame cadence;
3. sustainable UART throughput/error rate at 921600 baud and any higher candidate rate;
4. ESP32 CPU/queue behavior under simultaneous A2DP and UART load; and
5. reliable SBC decoding on the Rust/mobile side.

After those measurements, SC-087 can either promote encoded SBC and disable decoded PCM in the default firmware, or reject encoded SBC and select a measured PCM conversion strategy.

## Software-only closure

SC-081, SC-082, SC-083, SC-085, and the analytical portion of SC-086 are software-verifiable. SC-084, final SC-086 comparison, SC-087 default-path removal, and Gate SC-G5 remain hardware-dependent.
