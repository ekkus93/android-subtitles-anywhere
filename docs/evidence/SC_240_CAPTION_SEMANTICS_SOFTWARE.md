# SC-240 VAD, caption stabilization, and session semantics — software evidence

## Scope

This block defines portable, deterministic semantics for SC-241 through SC-247. It does not claim physical-device ASR quality or transport reliability evidence.

## SC-241 — VAD and endpointing strategy

Backends may retain native endpointing when available, but the portable contract requires equivalent speech/finalization behavior. `EnergyVad` provides a deterministic energy-based reference implementation for canonical 16 kHz mono PCM and host tests. This avoids coupling portable session semantics to a particular inference runtime.

## SC-242 — speech state and bounded context

`EnergyVad` exposes explicit `Silence`, `Speech`, and `PostRoll` states. `VadConfig` bounds pre-roll and post-roll in samples. Pre-roll retains only the latest configured samples; post-roll monotonically drains to silence. No unbounded audio history is retained.

## SC-243 — partial replacement

`CaptionStabilizer` emits `ReplacePartial` rather than append-only partial text. Identical repeated partials are suppressed.

## SC-244 — final commit

Final backend events become `CommitFinal`. Final sequence numbers are monotonic within a session and duplicate/older finals are ignored. Committing a final clears the pending partial.

## SC-245 — punctuation, casing, confidence

The portable display policy only normalizes whitespace. It preserves backend punctuation and casing verbatim and does not synthesize confidence. This prevents the presentation layer from inventing linguistic information unsupported by the selected ASR backend.

## SC-246 — discontinuities

The portable contract names USB gaps, Bluetooth gaps, media-route changes, and pause/resume as explicit discontinuity reasons. A discontinuity clears an outstanding partial before emitting the discontinuity marker, preventing pre-gap text from being silently combined with post-gap recognition.

## SC-247 — deterministic tests

`core/tests/caption_semantics.rs` covers:

- silence -> speech -> post-roll -> silence;
- bounded pre-roll;
- rapid partial replacement;
- duplicate suppression;
- final commit and duplicate-final rejection;
- punctuation/casing/confidence preservation;
- USB discontinuity behavior;
- pause/resume behavior;
- stale-session rejection;
- 1,000 rapid updates as a bounded-state regression.

## Gate SC-G13

The software-verifiable portable semantics are implemented. Gate SC-G13 should close only after repository checks pass and later integration tests demonstrate that real USB/Bluetooth/media-route interruptions are translated into these discontinuity semantics by the Android session coordinator.
