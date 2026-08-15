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

`bash scripts/check-rust.sh` and the complete repository CI passed at commit `ec4a60a58036786e1eccec40ed968ec9d9c58404` in GitHub Actions run `31899531632` on 2026-08-15.

**SC-G9: PASS.** No physical hardware evidence is required for this gate.
