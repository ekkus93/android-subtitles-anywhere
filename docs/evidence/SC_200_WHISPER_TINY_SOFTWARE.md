# SC-200 Whisper Tiny multilingual software evidence

## Scope

This evidence covers the software-verifiable portions of SC-201 through SC-206. SC-207 and Gate SC-G11 remain open until measurements are taken on the target Android phone.

## SC-201 artifact selection

- Runtime: `whisper.cpp` v1.9.1.
- Runtime license: MIT.
- Model: multilingual Tiny GGML artifact `ggml-tiny.bin`.
- Model size: 77,691,713 bytes.
- Model SHA-256: `be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21`.
- Portable backend ID: `whisper-tiny-multilingual`.

The portable core pins these values in `core/src/whisper.rs` so model/runtime identity is inspectable and testable rather than implicit.

## SC-202 runtime boundary

`WhisperTinyBackend<E>` implements the repository-wide `AsrBackend` contract. Native inference is isolated behind `WhisperEngine`, whose operations cover model loading/unloading, language selection, bounded-window transcription, and cancellation.

The selected native runtime exposes a C API and officially supports Android. Its upstream Android example builds the native runtime with Android CMake/NDK integration. The application-side native implementation must preserve the `WhisperEngine` contract and must not leak Android/JNI types into the portable core.

A physical model/inference run is not claimed by this evidence. Concrete packaged-native validation belongs with Android/device integration and SC-207 measurements.

## SC-203 window policy

The Tiny backend uses canonical 16 kHz mono PCM and a bounded rolling window:

- window: 80,000 samples / 5 seconds;
- step: 16,000 samples / 1 second;
- overlap: 64,000 samples / 4 seconds.

After each non-final inference the backend drains exactly one step, preserving bounded overlap/context. Short residual audio is flushed as a final window at session finish.

## SC-204 stabilization

Partial text is normalized at the runtime boundary by trimming whitespace. Repeated identical partial snapshots are suppressed. Final results remain explicit `CaptionEvent { is_final: true }` events rather than being silently treated as another partial.

## SC-205 language policy

The common `LanguagePolicy` reaches the runtime unchanged:

- `Auto` requests runtime language detection;
- `Explicit(code)` requests the specified runtime-supported language;
- unsupported explicit languages return an explicit `AsrError` rather than silently falling back.

## SC-206 deterministic tests

Host tests cover:

- bounded window/step/overlap constants;
- partial-to-final behavior and duplicate suppression;
- automatic and explicit language propagation;
- stale-session rejection and cancellation;
- short-audio final flush;
- deterministic `PcmFixture` injection through the common ASR harness;
- WER/CER scoring using repository-standard transcript normalization;
- deterministic real-time-factor/latency metric calculation without wall-clock flakes;
- repeated create/load/start/cancel lifecycle cycles.

Relevant tests are `core/tests/whisper_tiny_backend.rs` and `core/tests/whisper_tiny_sc206.rs`.

## Remaining hardware/device evidence

SC-207 remains open. On the target Android phone, record at minimum:

- real-time factor over representative English and multilingual fixtures;
- first-partial and finalization latency;
- process/native memory while loaded and during sustained inference;
- CPU utilization;
- thermal behavior under sustained captioning;
- battery/power behavior over a documented interval.

Gate SC-G11 must remain open until those measurements exist and the backend is demonstrated selectable with the packaged native runtime on the target device.
