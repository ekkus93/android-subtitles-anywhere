# SC-220 Whisper Base multilingual — software evidence

## Scope

This evidence covers the software-verifiable portions of SC-221 through SC-226. Physical Android performance measurements remain deferred to SC-225 and Gate SC-G12.

## SC-221 — pinned artifact

- Runtime: whisper.cpp v1.9.1.
- Runtime license: MIT.
- Model: multilingual Whisper Base, `ggml-base.bin`.
- Published whisper.cpp model-table size: 142 MiB (remote artifact approximately 148 MB).
- Published model-table checksum: SHA-1 `465707469ff3a37a2b9b8d8f89f2f99de7299dac`.
- The model is deliberately the multilingual Base artifact, not `base.en`.

A cryptographic SHA-256 must be recorded by the model-management/download pipeline before activation; the upstream model table currently identifies this artifact with the published SHA-1 above.

## SC-222 — common interface

`WhisperBaseBackend<E>` implements the existing platform-neutral `AsrBackend` contract and consumes the same `WhisperEngine` runtime abstraction as Whisper Tiny. There is no Android UI dependency and no Base-specific UI coupling in the core.

## SC-223 — generalized Whisper behavior

Base intentionally follows the same bounded rolling-window policy as Tiny:

- canonical input: 16 kHz mono PCM;
- window: 80,000 samples / 5 seconds;
- step: 16,000 samples / 1 second;
- overlap/context: 64,000 samples / 4 seconds;
- duplicate partial text is suppressed;
- final flush commits the remaining buffered audio.

The runtime engine abstraction and result type are shared with Tiny so the eventual concrete whisper.cpp Android runtime does not need separate model-specific bindings.

## SC-224 — deterministic tests

`core/tests/whisper_base_backend.rs` covers:

- common window/overlap policy;
- `AsrBackend` lifecycle;
- duplicate-partial stabilization and final commit;
- common deterministic PCM fixture injection;
- WER/CER normalization/scoring;
- deterministic latency/RTF metrics;
- explicit language selection;
- cancellation.

These are host tests and do not claim real whisper.cpp inference performance.

## SC-225 — hardware measurement boundary

Still open. Measure on supported physical Android devices:

- real-time factor and end-to-caption latency;
- CPU utilization;
- peak/resident memory;
- thermal throttling during sustained sessions;
- battery impact;
- explicit identification of devices that cannot sustain Base in real time.

Gate SC-G12 cannot close until this evidence exists.

## SC-226 — explicit performance warning contract

The Base backend has a distinct stable backend ID (`whisper-base-multilingual`) and never aliases or silently substitutes Tiny. Device capability/performance policy must surface an explicit warning or failure when Base cannot be sustained. Silent model switching is prohibited.

The actual Android warning UI belongs to the later Android model/session-management blocks and remains to be integrated with SC-225 measurements.
