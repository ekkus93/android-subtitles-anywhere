# Silent Caption Completion Traceability

This file defines the evidence convention used to close `SC-*` work in `docs/SILENT_CAPTION_V01_TODO.md`.

## Rule

A TODO checkbox may be marked complete only when its acceptance condition is satisfied and the repository contains or references appropriate evidence. Code existence alone is not completion.

## Evidence classes

| Class | Required evidence |
| --- | --- |
| Host software | Automated test/lint/build command and passing result. |
| Android software | Relevant unit/instrumentation/build result; physical-device evidence where behavior depends on Android hardware or OS services. |
| Firmware software | ESP-IDF build/static checks plus hardware evidence for radio, UART, timing, sleep, or other hardware-dependent behavior. |
| Protocol | Normative document plus deterministic test vectors and implementation tests when applicable. |
| Hardware | Exact board/device/OS/firmware identity, procedure, observed result, and measurements/logs needed to reproduce the claim. |
| Performance | Device/model/configuration, corpus/workload, metric definition, raw or summarized measurements, and pass/fail threshold. |

## Linking convention

Tests, scripts, evidence documents, and meaningful implementation comments should include the relevant stable TODO ID when doing so improves traceability, for example `SC-042` or `SC-G7`. Avoid adding IDs to every source line.

Hardware and benchmark evidence should be stored under `docs/evidence/` using a descriptive filename and should identify all relevant `SC-*` tasks near the top of the document.

When a gate is closed, its evidence must demonstrate every prerequisite checkbox rather than relying on an assertion that the gate passed.

## Failure integrity

Unsupported, untested, permission-denied, disconnected, corrupt, or performance-degraded states must remain explicit. A fallback may be implemented only when the specification permits it and the UI/diagnostics identify the resulting state accurately.
