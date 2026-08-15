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

SC-223 is software-complete. Tiny and Base now delegate to one `WhisperBackend<E>` implementation in `core/src/whisper.rs`; model-specific wrappers contain identity/artifact metadata rather than copies of the streaming state machine.

The shared implementation owns:

- canonical 16 kHz mono PCM handling;
- 80,000-sample / 5-second windows;
- 16,000-sample / 1-second steps;
- 64,000-sample / 4-second overlap/context;
- duplicate-partial suppression;
- final buffered-audio flush;
- session validation and sequencing;
- source-time accounting;
- language-policy forwarding;
- cancellation and lifecycle behavior.

Base aliases its window constants to the shared Whisper constants so policy drift between Tiny and Base is mechanically prevented. Both wrappers retain distinct stable backend IDs.

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

The existing Tiny tests exercise the same shared implementation through the Tiny wrapper, providing regression coverage that both model identities preserve the common streaming semantics. These are host tests and do not claim real whisper.cpp inference performance.

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

The Base backend has a distinct stable backend ID (`whisper-base-multilingual`) and never aliases or silently substitutes Tiny. The shared backend accepts an explicit backend identity and does not contain fallback/model-selection behavior. Therefore inference or capability failure cannot silently change the selected model inside the portable Whisper pipeline.

Device capability/performance policy must surface an explicit warning or failure when Base cannot be sustained. The actual Android warning UI belongs to the later Android model/session-management blocks and remains to be integrated with SC-225 measurements.

## Software closure

Subject to the repository Rust checks passing, SC-221, SC-222, SC-223, SC-224, and the portable-core portion of SC-226 are software-verifiable and complete. SC-225 remains open, and the Android warning presentation portion of SC-226 remains downstream work. Gate SC-G12 remains open until physical-device evidence exists.
