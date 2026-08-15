# Silent Caption v0.1 Implementation TODO

**Companion specification:** `docs/SILENT_CAPTION_V01_SPEC.md`  
**Rule:** Complete tasks in dependency order unless a task is explicitly marked parallel-safe. Hardware assumptions must be converted into recorded evidence before dependent work is closed.

## Completion conventions

- [x] Every completed task has tests or recorded evidence appropriate to the task.
- [ ] Hardware-dependent tasks identify exact board/phone/OS/firmware versions used.
- [x] No checkbox is closed solely because code exists; its acceptance condition must pass.
- [x] Do not hide unsupported/degraded states behind fallbacks that make the UI report success.
- [x] Keep Android/iOS code out of the transport-neutral ASR/caption core.

---

## SC-000 — Repository and engineering baseline

- [x] **SC-001** Create repository structure for `firmware/`, Rust core, `android/`, future `ios/`, `protocol/`, `docs/`, and tests/fixtures as appropriate.
- [x] **SC-002** Select and pin supported Rust toolchain; add formatting, linting, unit-test, and dependency-lock policy.
- [x] **SC-003** Select and pin ESP-IDF version supporting the target ESP32-WROOM-32 Bluetooth Classic/A2DP implementation.
- [x] **SC-004** Create Android Kotlin/Compose project with documented minimum/target SDK levels.
- [x] **SC-005** Establish CI for host-buildable Rust, Android build/unit tests, firmware host/static checks where practical, formatting, and linting.
- [x] **SC-006** Add repository contribution/build instructions with exact local commands.
- [x] **SC-007** Add fixture policy: no copyrighted media fixtures without redistribution rights; use generated/permissively licensed speech fixtures.
- [x] **SC-008** Add a completion/traceability convention linking TODO IDs to tests/evidence.

**Gate SC-G0: PASS.** Clean checkout executes all non-hardware baseline checks reproducibly. Evidence: `docs/evidence/SC_G0_BASELINE.md`.

---

## SC-010 — Identify and characterize prototype hardware

- [ ] **SC-011** Record exact ESP32-WROOM-32 development-board model/revision.
- [ ] **SC-012** Identify onboard USB-UART bridge, USB VID/PID, and Android-visible descriptors.
- [ ] **SC-013** Record EN/GPIO0/DTR/RTS auto-reset wiring from schematic or direct validation.
- [ ] **SC-014** Record regulator/power LED/other always-on board loads relevant to sleep measurements.
- [ ] **SC-015** Record Android test phone model, Android version, USB host/OTG support, and Bluetooth capabilities.
- [ ] **SC-016** Prove the phone enumerates the board over the intended USB data/OTG cable with no additional wiring/components.
- [ ] **SC-017** Establish a repeatable method for measuring whole-board USB current.

**Gate SC-G1:** exact prototype hardware path is documented and USB enumeration works with board + cable only.

---

## SC-020 — Wire protocol v1

- [ ] **SC-021** Define protocol magic, version negotiation, frame header, payload length, sequence number, timestamp semantics, and integrity/check field.
- [ ] **SC-022** Define bounded maximum frame/payload sizes.
- [ ] **SC-023** Define HELLO/capabilities message.
- [ ] **SC-024** Define START_SESSION and STOP_SESSION.
- [ ] **SC-025** Define ACTIVE/STANDBY/DEEP_SLEEP control/status semantics.
- [ ] **SC-026** Define AUDIO_FORMAT and AUDIO_DATA messages supporting the candidate encoded and PCM paths.
- [ ] **SC-027** Define STATUS, DIAGNOSTICS, ERROR, and heartbeat semantics.
- [ ] **SC-028** Define reboot/session-ID behavior so stale frames cannot be mistaken for the current stream.
- [ ] **SC-029** Define parser behavior for malformed lengths, unsupported versions/types, corruption, sequence gaps, duplicates, and resets.
- [ ] **SC-030** Publish protocol test vectors usable by Rust and firmware implementations.

**Gate SC-G2:** protocol v1 document/test vectors are sufficient for independent Rust and firmware implementations.

---

## SC-040 — Rust protocol and audio foundation

- [ ] **SC-041** Implement bounded protocol parser/serializer.
- [ ] **SC-042** Add round-trip tests for every protocol-v1 message.
- [ ] **SC-043** Add malformed/truncated/oversized/unknown-version tests.
- [ ] **SC-044** Add fuzz/property testing for the parser with bounded allocation assertions.
- [ ] **SC-045** Implement sequence-gap, duplicate, reboot, and discontinuity detection.
- [ ] **SC-046** Implement bounded ring/jitter buffer with explicit overflow policy and metrics.
- [ ] **SC-047** Implement PCM sample-format conversion.
- [ ] **SC-048** Implement stereo-to-mono downmix.
- [ ] **SC-049** Implement resampling to the ASR canonical format, initially 16 kHz mono.
- [ ] **SC-050** Add deterministic audio fixture tests for conversion/resampling.
- [ ] **SC-051** Define transport-neutral input/session interfaces.
- [ ] **SC-052** Define diagnostics counters for packets, gaps, overruns, underruns, audio level, and timing.

**Gate SC-G3:** arbitrary validated protocol audio frames can be converted into a bounded canonical PCM stream without platform dependencies.

---

## SC-060 — ESP32 Bluetooth/A2DP proof

- [ ] **SC-061** Initialize NVS and Bluetooth controller/host for Classic Bluetooth.
- [ ] **SC-062** Advertise a deterministic A2DP sink name and document pairing behavior.
- [ ] **SC-063** Implement A2DP connection/audio-state event handling.
- [ ] **SC-064** Receive A2DP audio callbacks without routing audio to DAC/I2S/speaker hardware.
- [ ] **SC-065** Instrument callback bytes/second, callback interval/jitter, negotiated sample rate/channels, and queue occupancy.
- [ ] **SC-066** Implement bounded producer queue so Bluetooth callbacks never block on UART.
- [ ] **SC-067** Prove Android can pair/select the ESP32 as a media-output device.
- [ ] **SC-068** Prove media playback is inaudible because the dongle has no playback path.
- [ ] **SC-069** Capture evidence that continuous media produces continuous digital audio data.

**Gate SC-G4:** phone media reliably reaches the ESP32 as digital A2DP audio without audible playback.

---

## SC-080 — Candidate audio return-path experiment

- [ ] **SC-081** Determine whether stable encoded SBC frame access is available at a suitable ESP-IDF integration point without brittle private hooks.
- [ ] **SC-082** Prototype encoded-frame forwarding if SC-081 is viable.
- [ ] **SC-083** Prototype decoded PCM forwarding path.
- [ ] **SC-084** Benchmark UART rates starting at 921600 baud; measure sustainable throughput, framing overhead, errors, and CPU load.
- [ ] **SC-085** For PCM path, evaluate on-ESP32 downmix/resampling versus phone-side conversion.
- [ ] **SC-086** Compare encoded and PCM paths on implementation complexity, bandwidth, latency, robustness, and portability.
- [ ] **SC-087** Record decision and remove/disable the rejected path from the default implementation.

**Gate SC-G5:** one return-audio representation is selected using measurements rather than assumption.

---

## SC-100 — ESP32 USB-UART transport and control

- [ ] **SC-101** Implement UART framing transport using protocol v1.
- [ ] **SC-102** Configure chosen UART rate and document clock/error assumptions.
- [ ] **SC-103** Implement TX buffering/backpressure/drop counters.
- [ ] **SC-104** Implement RX control parser independently from audio TX.
- [ ] **SC-105** Implement HELLO/capability exchange.
- [ ] **SC-106** Implement START_SESSION/STOP_SESSION.
- [ ] **SC-107** Implement heartbeat/watchdog behavior.
- [ ] **SC-108** Add firmware protocol unit/host tests where practical.
- [ ] **SC-109** Add firmware diagnostics dump/version information.

**Gate SC-G6:** ESP32 can exchange bounded protocol/control traffic and stream selected audio representation over USB-UART without blocking Bluetooth callbacks.

---

## SC-120 — Android USB transport

- [ ] **SC-121** Implement USB device discovery/filtering by VID/PID plus user-confirmed fallback.
- [ ] **SC-122** Implement Android USB permission flow and denial/retry behavior.
- [ ] **SC-123** Implement USB-UART driver support for the characterized prototype bridge.
- [ ] **SC-124** Configure UART and control lines without accidentally entering bootloader/reset loops.
- [ ] **SC-125** Implement asynchronous reads/writes with bounded buffers and cancellation.
- [ ] **SC-126** Feed received bytes into the Rust protocol parser.
- [ ] **SC-127** Implement attach/detach/reconnect lifecycle.
- [ ] **SC-128** Surface unsupported bridge/permission/device states explicitly.
- [ ] **SC-129** Add fake-transport JVM/instrumentation tests.

**Gate SC-G7:** Android receives validated dongle frames over USB and handles attach/detach/permission failures without crashes or false-ready state.

---

## SC-140 — End-to-end audio-loop proof

- [ ] **SC-141** Start a session from Android and receive ESP32 HELLO/status.
- [ ] **SC-142** Play known media on Android routed to the ESP32 A2DP sink.
- [ ] **SC-143** Receive returned audio frames over USB in the same phone.
- [ ] **SC-144** Decode/convert returned data into canonical PCM in Rust.
- [ ] **SC-145** Verify returned audio against known source using duration, level, timing, and intelligibility checks.
- [ ] **SC-146** Run at least 30 minutes continuously and record gaps, drops, resets, and buffer high-water marks.
- [ ] **SC-147** Test pause/resume, seek, media-app switch, USB detach/reattach, and Bluetooth reconnect.

**Gate SC-G8 (critical feasibility gate):** Android media -> Bluetooth A2DP -> ESP32 -> USB-UART -> same Android phone produces stable, intelligible digital audio. Do not treat the architecture as proven before this gate passes.

---

## SC-160 — ASR abstraction and deterministic test harness

- [ ] **SC-161** Define normalized ASR backend interface: load/unload, language policy, streaming/chunk input, partial/final result, timestamps, errors, cancellation.
- [ ] **SC-162** Define normalized `CaptionEvent` including session ID, sequence, text, partial/final flag, source-time range, confidence if available, and backend ID.
- [ ] **SC-163** Build deterministic file/PCM injection path that bypasses ESP32/USB for ASR tests.
- [ ] **SC-164** Add permissively licensed/generated speech corpus with expected transcripts and provenance.
- [ ] **SC-165** Define normalization/scoring policy and baseline WER/CER tooling.
- [ ] **SC-166** Define latency metrics: first partial, finalization delay, real-time factor, end-to-caption latency.
- [ ] **SC-167** Add cancellation/model-switch/session-reset tests.

**Gate SC-G9:** any ASR backend can be tested deterministically without hardware and emits the same normalized event contract.

---

## SC-180 — Streaming Zipformer backend

- [ ] **SC-181** Select exact sherpa-onnx/Zipformer model artifact, version, license, languages, hashes, and expected installed size.
- [ ] **SC-182** Integrate runtime through the Rust core or a narrowly documented native boundary.
- [ ] **SC-183** Implement true streaming input and partial/final results.
- [ ] **SC-184** Implement endpointing/reset behavior.
- [ ] **SC-185** Add deterministic accuracy/latency tests.
- [ ] **SC-186** Benchmark supported Android devices for latency, CPU, memory, thermal behavior, and battery impact.

**Gate SC-G10:** Zipformer provides usable real-time captions on the target Android phone with measured performance.

---

## SC-200 — Whisper Tiny multilingual backend

- [ ] **SC-201** Select exact model/runtime artifact, version, license, hash, and size.
- [ ] **SC-202** Integrate the runtime behind the common ASR interface.
- [ ] **SC-203** Implement chunk/window policy with overlap/context handling.
- [ ] **SC-204** Implement partial/final stabilization so repeated windows do not visibly duplicate text.
- [ ] **SC-205** Implement language auto-detect and explicit-language policy supported by the runtime.
- [ ] **SC-206** Add deterministic accuracy/latency tests.
- [ ] **SC-207** Benchmark Android CPU/memory/thermal/battery behavior.

**Gate SC-G11:** Whisper Tiny multilingual is selectable and produces stable captions with measured device performance.

---

## SC-220 — Whisper Base multilingual backend

- [ ] **SC-221** Select exact model/runtime artifact, version, license, hash, and size.
- [ ] **SC-222** Integrate behind the same ASR interface without backend-specific UI coupling.
- [ ] **SC-223** Reuse/generalize Whisper chunk/stabilization infrastructure where appropriate.
- [ ] **SC-224** Add deterministic accuracy/latency tests.
- [ ] **SC-225** Benchmark Android CPU/memory/thermal/battery behavior and identify devices where Base is not real-time.
- [ ] **SC-226** Surface performance warnings rather than silently switching models.

**Gate SC-G12:** Whisper Base multilingual is selectable, measured, and fails/degrades explicitly when the device cannot sustain it.

---

## SC-240 — VAD, caption stabilization, and session semantics

- [ ] **SC-241** Select and document VAD/endpointing strategy per backend.
- [ ] **SC-242** Implement speech/no-speech state and bounded pre-roll/post-roll where required.
- [ ] **SC-243** Implement partial-caption replacement rather than append-only duplication.
- [ ] **SC-244** Implement final-caption commit semantics.
- [ ] **SC-245** Implement punctuation/casing policy without fabricating unsupported confidence.
- [ ] **SC-246** Implement session discontinuity handling after USB/Bluetooth gaps or media-route changes.
- [ ] **SC-247** Add rapid speech, silence, long utterance, pause/resume, and discontinuity tests.

**Gate SC-G13:** caption stream remains readable and semantically consistent across normal speech boundaries and transport interruptions.

---

## SC-260 — Rust mobile boundary

- [ ] **SC-261** Select/document Rust mobile FFI mechanism and ownership/threading model.
- [ ] **SC-262** Expose lifecycle-safe core creation/destruction.
- [ ] **SC-263** Expose session start/stop and backend/model selection.
- [ ] **SC-264** Expose audio/protocol input without unbounded copies.
- [ ] **SC-265** Expose caption/status/diagnostic event stream.
- [ ] **SC-266** Map Rust errors into stable platform-neutral error codes/messages.
- [ ] **SC-267** Add FFI stress tests for repeated create/start/stop/destroy cycles.

**Gate SC-G14:** Android can drive the portable Rust core through a documented, leak-free, cancellation-safe boundary.

---

## SC-280 — Android service/session architecture

- [ ] **SC-281** Implement foreground service for active caption sessions with required notification/channel behavior.
- [ ] **SC-282** Define lifecycle state machine: unavailable, ready, starting, listening, reconnecting, stopping, error.
- [ ] **SC-283** Coordinate USB transport, Rust core, Bluetooth-route readiness, and ASR backend.
- [ ] **SC-284** Implement idempotent Start Listening / Stop Listening.
- [ ] **SC-285** Handle app backgrounding, process recreation, screen off, rotation, and task removal explicitly.
- [ ] **SC-286** Prevent stale captions/events from prior sessions after restart/reconnect.
- [ ] **SC-287** Add lifecycle/state-machine tests.

**Gate SC-G15:** a caption session survives expected Android lifecycle changes without duplicate sessions, leaks, or false state.

---

## SC-300 — Android permissions, onboarding, and setup

- [ ] **SC-301** Implement first-run explanation of the dongle/audio loop and privacy model.
- [ ] **SC-302** Implement overlay permission flow only when Floating/Compact mode requires it.
- [ ] **SC-303** Implement notification/foreground-service permission handling for applicable Android versions.
- [ ] **SC-304** Implement USB permission/setup flow.
- [ ] **SC-305** Implement Bluetooth setup guidance and explicit route-readiness checks within Android platform limits.
- [ ] **SC-306** Build setup checklist with distinct USB, Bluetooth, model, and overlay states.
- [ ] **SC-307** Never claim media routing is correct solely because Bluetooth is connected.
- [ ] **SC-308** Add denial/retry/settings-return tests for every permission.

**Gate SC-G16:** a new user can reach a truthful Ready state without granting unrelated permissions.

---

## SC-320 — Model management

- [ ] **SC-321** Define signed/HTTPS model manifest containing backend/model IDs, versions, URLs, hashes, sizes, licenses, language metadata, and compatibility constraints.
- [ ] **SC-322** Implement download with temporary files, cancellation, progress, retry, and atomic promotion.
- [ ] **SC-323** Verify cryptographic hash before activation.
- [ ] **SC-324** Reject corrupt/incomplete/wrong-version models without deleting the last known-good model.
- [ ] **SC-325** Implement storage-space preflight and actionable insufficient-space errors.
- [ ] **SC-326** Implement model deletion and active-model protection.
- [ ] **SC-327** Display installed/download size, license/source, languages, and performance guidance.
- [ ] **SC-328** Add interrupted-download, corrupt-hash, low-storage, upgrade, rollback, and delete tests.

**Gate SC-G17:** all three ASR choices can be installed/verified/removed safely without corrupt model activation.

---

## SC-340 — Live home screen

- [ ] **SC-341** Implement graphite/aqua design tokens with accessible contrast and dynamic-type behavior.
- [ ] **SC-342** Implement dongle state card: USB, Bluetooth/A2DP, firmware/protocol, and truthful readiness.
- [ ] **SC-343** Implement Start Listening / Stop Listening primary action.
- [ ] **SC-344** Implement selected ASR/language summary.
- [ ] **SC-345** Implement display-mode selector: Floating, Reader, Compact.
- [ ] **SC-346** Implement visible Listening/Reconnecting/Error states.
- [ ] **SC-347** Add Compose previews and screenshot tests for major states.

**Gate SC-G18:** home screen communicates whether captioning can actually start and why not when blocked.

---

## SC-360 — Floating and Compact captions

- [ ] **SC-361** Implement overlay service/window with explicit permission gate.
- [ ] **SC-362** Implement draggable Floating caption card.
- [ ] **SC-363** Implement Compact one/two-line bar.
- [ ] **SC-364** Implement adjustable font size, opacity, width, background, and screen margins.
- [ ] **SC-365** Persist independent portrait and landscape position/size preferences.
- [ ] **SC-366** Clamp/recover overlay geometry after rotation, resolution/inset changes, or invalid stored coordinates.
- [ ] **SC-367** Implement partial replacement/final transition without disruptive reflow.
- [ ] **SC-368** Test over video, browser, podcast/audio-only app, home screen, and screen rotation.
- [ ] **SC-369** Test accessibility, large fonts, RTL, and long unbroken text.

**Gate SC-G19:** readable captions remain controllable and correctly positioned over arbitrary foreground apps in portrait and landscape.

---

## SC-380 — Reader mode and transcript interaction

- [ ] **SC-381** Implement full-screen Reader mode independent of media metadata/video presence.
- [ ] **SC-382** Implement current-caption emphasis and committed transcript history.
- [ ] **SC-383** Implement auto-scroll that disengages when user scrolls backward.
- [ ] **SC-384** Implement Jump to Live.
- [ ] **SC-385** Implement bounded in-memory transcript retention.
- [ ] **SC-386** Implement optional persisted transcript history only after explicit user opt-in.
- [ ] **SC-387** Add clear/delete transcript controls.
- [ ] **SC-388** Test long sessions, rotation, large fonts, RTL, and app background/restore.

**Gate SC-G20:** Reader mode supports podcast/audio-only use as a first-class experience without requiring media metadata.

---

## SC-400 — Settings and diagnostics UX

- [ ] **SC-401** Implement Settings sections for ASR model, language, captions, power, privacy/history, and advanced diagnostics.
- [ ] **SC-402** Implement live diagnostics view: USB bridge/device, firmware/protocol, A2DP state, audio format/rate, packet/gap/drop counts, ASR backend, latency, buffer occupancy.
- [ ] **SC-403** Add copy/export diagnostics with privacy-aware redaction and no transcript by default.
- [ ] **SC-404** Implement reset caption layout and reset settings.
- [ ] **SC-405** Implement explicit model/runtime error explanations and remediation.
- [ ] **SC-406** Ensure diagnostics remain useful when ASR is disabled so transport can be debugged independently.

**Gate SC-G21:** transport, audio, ASR, and UI failures can be distinguished without guesswork.

---

## SC-420 — ESP32 power management

- [ ] **SC-421** Define measured ACTIVE/STANDBY/DEEP_SLEEP state transitions and timers.
- [ ] **SC-422** Implement explicit host power-state commands with acknowledgements.
- [ ] **SC-423** Implement safe Bluetooth teardown before deep sleep.
- [ ] **SC-424** Implement inactivity failsafe if host disappears/crashes.
- [ ] **SC-425** Validate whether USB-UART DTR/RTS/EN wiring can reliably reset/wake the exact prototype board from deep sleep without extra wires.
- [ ] **SC-426** Prevent control-line manipulation from accidentally selecting ROM bootloader mode.
- [ ] **SC-427** Measure whole-board USB current in ACTIVE, STANDBY, and DEEP_SLEEP, including bridge/regulator/LED loads.
- [ ] **SC-428** Measure wake-to-USB-ready, wake-to-Bluetooth-ready, and wake-to-A2DP-ready latency.
- [ ] **SC-429** Implement Android Start Listening wake sequence and failure timeout/retry.
- [ ] **SC-430** Implement configurable standby/deep-sleep timeout with safe defaults.

**Gate SC-G22:** connected prototype can enter a materially lower-power idle state and return to usable captioning without unplugging or adding wires; actual whole-board current is documented.

---

## SC-440 — Privacy, security, and robustness

- [ ] **SC-441** Keep raw audio and transcripts in memory by default; no implicit cloud upload.
- [ ] **SC-442** Audit Android permissions and remove unrelated permissions.
- [ ] **SC-443** Validate all USB/protocol lengths/types before allocation/use.
- [ ] **SC-444** Bound all audio/protocol/transcript queues and histories.
- [ ] **SC-445** Sanitize/redact logs; raw transcript/audio logging disabled in production.
- [ ] **SC-446** Add malformed USB stream stress/fuzz tests through the Android/Rust boundary.
- [ ] **SC-447** Add disconnect/reconnect, repeated start/stop, low-storage, model-corruption, and process-restart soak tests.
- [ ] **SC-448** Document threat/privacy model, including malicious USB device considerations.

**Gate SC-G23:** malformed input, device churn, and normal privacy defaults do not expose unbounded resource use, silent cloud behavior, or transcript leakage.

---

## SC-460 — Performance and acceptance

- [ ] **SC-461** Define target Android phone(s) and performance acceptance thresholds before final benchmarking.
- [ ] **SC-462** Measure end-to-caption latency distribution for all three models.
- [ ] **SC-463** Measure WER/CER on the fixed test corpus.
- [ ] **SC-464** Measure CPU, peak RSS, thermal throttling, and battery drain for sustained sessions.
- [ ] **SC-465** Measure USB/UART/A2DP drop/gap rates during at least 60-minute sessions.
- [ ] **SC-466** Test portrait/landscape rotation repeatedly during live captioning.
- [ ] **SC-467** Test podcast/audio-only, browser video, video app, social-media clip, audiobook/voice-message style content where legally available.
- [ ] **SC-468** Test Bluetooth/USB interruption and recovery during active ASR.
- [ ] **SC-469** Verify media remains inaudible throughout intended silent-listening operation.
- [ ] **SC-470** Record known unsupported devices/bridges/media-routing behaviors rather than masking them.

**Gate SC-G24:** Android v0.1 meets documented accuracy, latency, stability, privacy, and silent-listening acceptance thresholds on the target device set.

---

## SC-480 — Android release hardening

- [ ] **SC-481** Configure release build, signing procedure, versioning, minification/native-symbol handling, and reproducible release checklist.
- [ ] **SC-482** Verify release APK/AAB contains only intended ABIs/assets/models.
- [ ] **SC-483** Verify clean install, upgrade, uninstall/reinstall, model migration, and settings migration.
- [ ] **SC-484** Add OSS notices/licenses for Rust, Android, ASR runtimes/models, and firmware dependencies.
- [ ] **SC-485** Final accessibility pass: TalkBack, large text, contrast, touch targets, orientation.
- [ ] **SC-486** Final privacy disclosure and permission rationale review.
- [ ] **SC-487** Produce user setup/troubleshooting guide covering dongle connection, Bluetooth media routing, models, overlay, power, and diagnostics.

**Gate SC-G25:** Android v0.1 release candidate is reproducibly buildable, installable, documented, and legally attributable.

---

## SC-500 — iOS enablement and feasibility

- [ ] **SC-501** Keep Rust APIs free of Android-only assumptions throughout v0.1.
- [ ] **SC-502** Build Rust core for required iOS architectures and define Swift FFI packaging.
- [ ] **SC-503** Research/document iOS accessory/USB serial constraints for the intended non-MFi prototype/product path; do not assume Android USB-host behavior transfers to iOS.
- [ ] **SC-504** Research/document iOS Bluetooth/media-routing behavior for the same-phone A2DP-out/accessory-return concept.
- [ ] **SC-505** Decide iOS return transport/hardware requirements from evidence.
- [ ] **SC-506** Prototype Swift/SwiftUI Reader mode using deterministic injected audio/core events independent of accessory feasibility.
- [ ] **SC-507** If platform rules permit, prototype real accessory transport; otherwise document required hardware/program changes.

**Gate SC-G26:** iOS feasibility is evidence-based, with the Rust core demonstrably reusable and platform-specific transport constraints explicitly resolved or declared blockers.

---

## SC-520 — Final documentation and completion audit

- [ ] **SC-521** Update architecture diagrams to match shipped implementation.
- [ ] **SC-522** Update protocol specification/test vectors to shipped version.
- [ ] **SC-523** Ensure every completed TODO has test/evidence traceability.
- [ ] **SC-524** Ensure no stale/dead alternate audio-return implementation remains enabled accidentally.
- [ ] **SC-525** Ensure no TODO/spec claim says iOS works unless its actual platform gate passed.
- [ ] **SC-526** Record final supported hardware/Android versions/models and known limitations.
- [ ] **SC-527** Run clean release build and full acceptance matrix.
- [ ] **SC-528** Archive final benchmark/current-consumption/soak-test evidence.
- [ ] **SC-529** Perform final code, privacy, dependency/license, and documentation audit.

**Gate SC-G27 — v0.1 complete:** all required Android gates pass; hardware feasibility is proven; all three ASR choices are usable as documented; privacy/power/diagnostics behavior is verified; release documentation is complete. iOS is either separately proven or explicitly documented as future/blocked work.
