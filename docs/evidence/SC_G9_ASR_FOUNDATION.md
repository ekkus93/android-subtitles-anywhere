# SC-G9 ASR foundation evidence

SC-161 through SC-167 establish a platform-neutral, hardware-independent ASR test contract.

## Implemented

- `AsrBackend` defines load/unload, language policy, session start, chunk input, partial/final results, errors, finish, and cancellation.
- `CaptionEvent` normalizes session/sequence identity, text, partial/final state, source timestamps, optional confidence, and backend ID.
- `PcmFixture` provides deterministic 16 kHz mono PCM injection without ESP32, USB, Android, or media routing.
- Repository fixtures in this block are algorithmically generated and therefore introduce no third-party media licensing dependency.
- Transcript normalization plus deterministic WER/CER functions provide backend-comparison scoring.
- `LatencyMetrics` records first-partial, finalization, end-to-caption, processing/audio duration, and real-time factor inputs.
- Host tests exercise cancellation, stale-session rejection, unload/reload model-switch-style lifecycle, deterministic fixture injection, scoring, and caption normalization.

## Verification

Run:

```text
bash scripts/check-rust.sh
```

SC-G9 may be closed when the pinned Rust formatting, Clippy, unit/integration, and documentation checks pass for this implementation. No physical hardware evidence is required for this gate.
