---
name: sc-evidence
description: Draft or update the docs/evidence/ file for a given SC-XXX task per docs/TRACEABILITY.md, so a TODO checkbox in docs/SILENT_CAPTION_V01_TODO.md is never marked complete without the required evidence. Use when the user says "record evidence for SC-XXX", "close out SC-XXX", "write the evidence doc", or asks whether an SC task is really done.
---

Given an `SC-XXX` (or gate `SC-Gn`) ID, help the user produce or extend its evidence document under `docs/evidence/`, following the convention in `docs/TRACEABILITY.md`. Read that file plus the task's entry in `docs/SILENT_CAPTION_V01_TODO.md` first — the acceptance condition there defines what evidence is actually required.

Steps:

1. **Find or name the evidence doc.** Check `docs/evidence/` for an existing file covering this ID (naming pattern: descriptive, e.g. `SC_280_ANDROID_SESSION_ARCHITECTURE.md`, `SC_G0_BASELINE.md`). If none exists, propose a filename and confirm it with the user before creating it.

2. **Classify the evidence needed** against `docs/TRACEABILITY.md`'s evidence classes — a task can span more than one:
   - *Host software*: automated test/lint/build command + passing result.
   - *Android software*: unit/instrumentation/build result; physical-device evidence if behavior depends on Android hardware or OS services.
   - *Firmware software*: ESP-IDF build/static checks + hardware evidence for radio/UART/timing/sleep or other hardware-dependent behavior.
   - *Protocol*: normative doc + deterministic test vectors + implementation tests where applicable.
   - *Hardware*: exact board/device/OS/firmware identity, reproducible procedure, observed result, measurements/logs.
   - *Performance*: device/model/config, corpus/workload, metric definition, raw or summarized measurements, pass/fail threshold.

3. **Gather the actual evidence** — don't fabricate it. Run the relevant `/verify` checks and capture real output; ask the user for anything that requires physical hardware or manual observation you can't produce yourself (device model, OS version, firmware build, measured values). If something required is missing or unproven, say so explicitly in the doc rather than omitting it or implying it passed.

4. **Write/update the doc** with: the relevant `SC-*` IDs near the top, an architecture/change summary, a per-requirement mapping (which sub-task is satisfied by what evidence), and an explicit "remaining/unproven work" section for anything not yet covered — do not hide gaps behind a success-looking summary (see the "Failure integrity" rule in `docs/TRACEABILITY.md`).

5. **Only then** suggest checking the corresponding box in `docs/SILENT_CAPTION_V01_TODO.md`, and only for the sub-items the gathered evidence actually covers — leave the rest unchecked. If the ID is a gate (`SC-Gn`), its evidence must demonstrate every prerequisite checkbox, not just assert the gate passed.

6. If a commit accompanies this work, follow the repo's commit convention (Conventional Commits, e.g. `docs: record SC-XXX evidence`) and reference the `SC-XXX` ID in the subject.
