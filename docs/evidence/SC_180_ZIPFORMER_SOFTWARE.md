# SC-180 Zipformer software baseline

## SC-181 artifact selection

Selected runtime: sherpa-onnx `v1.13.5`.

Selected Android runtime artifact: `sherpa-onnx-1.13.5.aar`.

- Published by the upstream k2-fsa/sherpa-onnx project.
- SHA-256: `6419cd8bc983e0c4fab06067f0fe0313fdc0f7103818ac1e7a08d50787b7a82b` (GitHub release digest).
- sherpa-onnx source license: Apache-2.0.

Selected streaming model: `sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2`.

- Upstream release size: 127,887,156 bytes.
- SHA-256: `9c559283e8498d3fe95913c79ca1cb454bb26281ac2b102b41306c7d752765d9`.
- Language: English only.
- Training/source model: `desh2608/icefall-asr-librispeech-pruned-transducer-stateless7-streaming-small`.
- Source-model license: Apache-2.0.
- Upstream model documentation identifies it as the small approximately 20M-parameter streaming Zipformer model and reports 320 ms streaming WER of 3.94 (greedy) / 3.88 (modified beam) on LibriSpeech test-clean and 9.79 / 9.53 on test-other.

The archive hash is independently published for a byte-identical 127,887,156-byte mirror. Before production redistribution, the model archive downloaded from the official sherpa-onnx release must be hash-verified and its license/notice files retained with the installed model.

## SC-182 native boundary

`core/src/zipformer.rs` implements `ZipformerBackend<E>` behind the common `AsrBackend` interface. `ZipformerEngine` is the narrow runtime boundary for the platform sherpa-onnx binding. Android-specific AAR/JNI types therefore do not enter the portable core.

The production Android engine implementation remains a platform integration step; the portable boundary and lifecycle semantics are software-verifiable on the host.

## SC-183 streaming

The backend accepts canonical 16 kHz mono PCM incrementally, forwards each chunk to the engine, decodes after each accepted chunk, and emits normalized partial/final `CaptionEvent` values. Duplicate partial snapshots are suppressed.

## SC-184 endpointing/reset

An engine result marked as an endpoint is emitted as final, then the recognizer stream is reset while the outer ASR session remains active. Explicit cancellation reaches the engine and invalidates the active session.

## SC-185 deterministic tests

`core/tests/zipformer_backend.rs` verifies:

- incremental partial and final caption emission;
- normalized backend/session/sequence metadata;
- duplicate partial suppression;
- endpoint finalization and stream reset;
- stale-session rejection;
- explicit English-only language policy;
- cancellation propagation.

These tests use a deterministic fake `ZipformerEngine`, so they require no model download, Android runtime, USB transport, or physical hardware.

## SC-186 benchmark status

Software-verifiable benchmark inputs are defined by the SC-G9 `LatencyMetrics` contract and the deterministic ASR harness. Actual Android latency, CPU, memory, thermal, battery, and sustained real-time-factor measurements remain **OPEN** until the selected runtime/model are exercised on the supported physical Android device(s).

## Gate status

SC-G10 remains **OPEN**. The software architecture and deterministic backend behavior can be validated in CI, but usable real-time captions on the target Android phone require the SC-186 device benchmark and real sherpa-onnx integration evidence.
