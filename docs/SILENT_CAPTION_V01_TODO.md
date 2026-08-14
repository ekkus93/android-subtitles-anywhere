# Silent Caption v0.1 Implementation TODO

**Companion specification:** `docs/SILENT_CAPTION_V01_SPEC.md`  
**Rule:** Complete tasks in dependency order unless a task is explicitly marked parallel-safe. Hardware assumptions must be converted into recorded evidence before dependent work is closed.

## Completion conventions

- [ ] Every completed task has tests or recorded evidence appropriate to the task.
- [ ] Hardware-dependent tasks identify exact board/phone/OS/firmware versions used.
- [ ] No checkbox is closed solely because code exists; its acceptance condition must pass.
- [ ] Do not hide unsupported/degraded states behind fallbacks that make the UI report success.
- [ ] Keep Android/iOS code out of the transport-neutral ASR/caption core.

---

## SC-000 — Repository and engineering baseline

- [ ] **SC-001** Create repository structure for `firmware/`, Rust core, `android/`, future `ios/`, `protocol/`, `docs/`, and tests/fixtures as appropriate.
- [ ] **SC-002** Select and pin supported Rust toolchain; add formatting, linting, unit-test, and dependency-lock policy.
- [ ] **SC-003** Select and pin ESP-IDF version supporting the target ESP32-WROOM-32 Bluetooth Classic/A2DP implementation.
- [ ] **SC-004** Create Android Kotlin/Compose project with documented minimum/target SDK levels.
- [ ] **SC-005** Establish CI for host-buildable Rust, Android build/unit tests, firmware host/static checks where practical, formatting, and linting.
- [ ] **SC-006** Add repository contribution/build instructions with exact local commands.
- [ ] **SC-007** Add fixture policy: no copyrighted media fixtures without redistribution rights; use generated/permissively licensed speech fixtures.
- [ ] **SC-008** Add a completion/traceability convention linking TODO IDs to tests/evidence.

**Gate SC-G0:** clean checkout can execute all non-hardware baseline checks reproducibly.

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

## SC-060 — Minimal ESP32 A2DP sink

- [ ] **SC-061** Create ESP-IDF firmware project for the documented WROOM-32 target.
- [ ] **SC-062** Implement Bluetooth Classic initialization and A2DP sink role.
- [ ] **SC-063** Give the prototype a stable, recognizable Bluetooth device name.
- [ ] **SC-064** Pair Android phone and route ordinary media audio to the ESP32.
- [ ] **SC-065** Verify no DAC/amplifier/speaker path is required and the prototype itself emits no media audio.
- [ ] **SC-066** Instrument A2DP connection, stream start/stop, codec/audio format, callback load, and buffer pressure.
- [ ] **SC-067** Test disconnect/reconnect and phone media pause/resume.

**Gate SC-G4:** phone reliably treats the ESP32 as its Bluetooth media output and firmware receives the stream.

---

## SC-070 — Determine ESP32 return-audio representation

- [ ] **SC-071** Investigate whether ESP-IDF exposes usable incoming SBC/encoded frames at a stable supported boundary.
- [ ] **SC-072** Measure encoded-frame bitrate, framing complexity, and CPU/memory cost if available.
- [ ] **SC-073** Prototype decoded PCM callback path.
- [ ] **SC-074** Prototype downmix/resample to 16 kHz/16-bit/mono on ESP32 if required.
- [ ] **SC-075** Measure ESP32 CPU, heap, dropped frames, and stability for candidate paths.
- [ ] **SC-076** Select encoded forwarding or ASR PCM forwarding based on simplicity/reliability/serial bandwidth evidence.
- [ ] **SC-077** Record the decision and update protocol AUDIO_FORMAT constraints without breaking protocol versioning.

**Gate SC-G5:** one return representation is selected from measurements and can run continuously without A2DP starvation.

---

## SC-080 — ESP32 USB-UART data/control channel

- [ ] **SC-081** Implement protocol-v1 framing over ESP32 UART.
- [ ] **SC-082** Implement HELLO/capabilities and status/error messages.
- [ ] **SC-083** Implement audio-frame transmission with sequence/timing metadata.
- [ ] **SC-084** Implement bounded queues/backpressure; never allow serial congestion to cause unbounded growth.
- [ ] **SC-085** Benchmark candidate baud rates with the actual onboard USB-UART bridge and Android host.
- [ ] **SC-086** Select a reliable production-prototype baud rate with sufficient margin for the selected audio representation.
- [ ] **SC-087** Run sustained UART corruption/loss test and record error rates.
- [ ] **SC-088** Implement control commands and idempotent session handling.

**Gate SC-G6:** firmware can continuously return framed audio and accept control commands over the existing onboard USB-UART connection.

---

## SC-100 — Android USB transport proof

- [ ] **SC-101** Implement explicit USB device discovery/allow-list logic using documented VID/PID/capabilities.
- [ ] **SC-102** Implement Android USB permission flow.
- [ ] **SC-103** Integrate/implement support for the actual USB-UART bridge.
- [ ] **SC-104** Configure and validate selected UART parameters.
- [ ] **SC-105** Implement attach, detach, reconnect, permission-denied, unsupported-device, and I/O-error states.
- [ ] **SC-106** Feed received bytes into the Rust protocol parser through the intended FFI boundary or a temporary proof harness.
- [ ] **SC-107** Capture returned audio to a diagnostic file/fixture without ASR.
- [ ] **SC-108** Reconstruct/play/analyze the diagnostic capture and prove speech is intelligible and corresponds to the source media.
- [ ] **SC-109** Verify the complete phone -> A2DP -> ESP32 -> USB-UART -> same-phone loop with only board + USB cable.
- [ ] **SC-110** Run at least a 30-minute transport soak with sequence/error metrics.

**CRITICAL GATE SC-G7:** do not treat the architecture as proven until SC-109 and SC-110 pass. Polished ASR/UI work may be developed in parallel with fixtures but must not conceal failure of this gate.

---

## SC-120 — ESP32 power management

- [ ] **SC-121** Implement explicit ACTIVE state.
- [ ] **SC-122** Implement STANDBY state optimized for short pauses/fast resume.
- [ ] **SC-123** Implement DEEP_SLEEP entry after explicit command and/or validated inactivity policy.
- [ ] **SC-124** Ensure session/audio buffers are safely terminated before sleep.
- [ ] **SC-125** Implement firmware failsafe so app crash/disconnect cannot leave ACTIVE indefinitely.
- [ ] **SC-126** Test wake/resume from STANDBY through normal serial control.
- [ ] **SC-127** Test whether Android can use the board's USB-UART DTR/RTS/auto-reset path to reboot/wake from DEEP_SLEEP.
- [ ] **SC-128** If deep-sleep USB wake is not possible without added hardware, document the limitation; do not add prototype wiring unless separately approved.
- [ ] **SC-129** Measure whole-board USB current in ACTIVE.
- [ ] **SC-130** Measure whole-board USB current in STANDBY.
- [ ] **SC-131** Measure whole-board USB current in DEEP_SLEEP.
- [ ] **SC-132** Record wake latency and Bluetooth/A2DP reconnection latency.
- [ ] **SC-133** Add configurable standby/deep-sleep timeout with safe defaults.

**Gate SC-G8:** power states are measurable, deterministic, and do not corrupt subsequent sessions.

---

## SC-140 — Rust ASR abstraction

- [ ] **SC-141** Define `AsrEngine`-style backend-neutral lifecycle/API.
- [ ] **SC-142** Define normalized partial, final, speech-boundary, timing, model-state, and error events.
- [ ] **SC-143** Define cancellation/reset semantics for model switching and stream discontinuity.
- [ ] **SC-144** Ensure input buffering is bounded when an ASR backend falls behind real time.
- [ ] **SC-145** Expose real-time factor, inference latency, queue depth, and dropped-audio metrics.
- [ ] **SC-146** Add fake/deterministic ASR backend for core and UI tests.

**Gate SC-G9:** caption/session layers can be tested without depending on a specific ASR runtime.

---

## SC-150 — Streaming Zipformer backend

- [ ] **SC-151** Select exact sherpa-onnx-compatible streaming Zipformer model package and document languages/license/source/version.
- [ ] **SC-152** Integrate the selected runtime in a way compatible with Android and the Rust boundary.
- [ ] **SC-153** Implement streaming audio ingestion and partial/final result mapping.
- [ ] **SC-154** Implement endpoint/reset behavior.
- [ ] **SC-155** Add deterministic fixture acceptance tests.
- [ ] **SC-156** Measure latency/RTF/RAM on target Android hardware.
- [ ] **SC-157** Verify a sustained session remains real-time without runaway memory.

---

## SC-160 — Whisper Tiny multilingual backend

- [ ] **SC-161** Select/pin Whisper runtime and exact Tiny multilingual model format/version/license/source.
- [ ] **SC-162** Implement bounded sliding-window/chunked inference.
- [ ] **SC-163** Implement overlap/deduplication and hypothesis stabilization.
- [ ] **SC-164** Map output to normalized partial/final events.
- [ ] **SC-165** Add deterministic fixture acceptance tests.
- [ ] **SC-166** Measure latency/RTF/RAM on target Android hardware.
- [ ] **SC-167** Verify sustained thermal/real-time behavior.

---

## SC-170 — Whisper Base multilingual backend

- [ ] **SC-171** Pin exact Base multilingual model format/version/license/source.
- [ ] **SC-172** Reuse the bounded Whisper streaming adapter without backend-specific UI assumptions.
- [ ] **SC-173** Add deterministic fixture acceptance tests.
- [ ] **SC-174** Measure latency/RTF/RAM on target Android hardware.
- [ ] **SC-175** Detect/report when the device cannot sustain real-time Base inference instead of silently accumulating backlog.
- [ ] **SC-176** Verify sustained thermal behavior.

**Gate SC-G10:** all three ASR choices conform to the same event contract and fail explicitly when unsupported or too slow.

---

## SC-180 — ASR comparative benchmark and defaults

- [ ] **SC-181** Create representative clean speech benchmark corpus with redistribution-safe fixtures.
- [ ] **SC-182** Measure accuracy for Zipformer, Whisper Tiny, and Whisper Base.
- [ ] **SC-183** Measure time-to-first-useful-partial.
- [ ] **SC-184** Measure finalization latency.
- [ ] **SC-185** Measure RTF and peak/steady RAM.
- [ ] **SC-186** Measure sustained battery/thermal behavior on target phone.
- [ ] **SC-187** Publish results in repository docs.
- [ ] **SC-188** Select default model based on measured user experience; expected candidate is streaming Zipformer but evidence controls the decision.
- [ ] **SC-189** Define user-facing Fastest/Lightweight/Higher-accuracy labels from measured behavior rather than unsupported claims.

---

## SC-200 — Model manager

- [ ] **SC-201** Define signed/trusted model manifest format including engine, package version, language coverage, size, hashes, compatibility, and license metadata.
- [ ] **SC-202** Implement model download with progress and cancellation.
- [ ] **SC-203** Verify cryptographic hash before installation.
- [ ] **SC-204** Make installation atomic and recover safely from interruption/out-of-space.
- [ ] **SC-205** Implement installed-model enumeration and selection.
- [ ] **SC-206** Implement model deletion without deleting the active model mid-session.
- [ ] **SC-207** Detect corrupt/incompatible assets and fail closed.
- [ ] **SC-208** Keep model family separate from exact package/version in persisted settings.
- [ ] **SC-209** Add tests for interrupted/corrupt/wrong-version downloads.

---

## SC-220 — Caption engine

- [ ] **SC-221** Define caption data model with stable IDs, partial/final state, text, timing, and revision semantics.
- [ ] **SC-222** Implement partial-hypothesis replacement without uncontrolled text jumping.
- [ ] **SC-223** Implement final caption segmentation suitable for one/two-line overlay presentation.
- [ ] **SC-224** Handle punctuation/whitespace consistently across ASR backends.
- [ ] **SC-225** Handle stream discontinuities without merging unrelated utterances.
- [ ] **SC-226** Maintain bounded in-memory recent-caption history when persistence is disabled.
- [ ] **SC-227** Add deterministic tests for revisions, finalization, rotation/session continuity, and reset.

---

## SC-240 — Rust/mobile FFI

- [ ] **SC-241** Validate UniFFI or select/document another maintainable Rust-to-Kotlin/Swift boundary.
- [ ] **SC-242** Expose session configuration and lifecycle.
- [ ] **SC-243** Expose model metadata/selection/state.
- [ ] **SC-244** Expose normalized caption events.
- [ ] **SC-245** Expose diagnostics snapshots/events without leaking raw transcript/audio by default.
- [ ] **SC-246** Define thread/callback ownership and cancellation semantics.
- [ ] **SC-247** Add lifecycle/stress tests for repeated create/start/stop/destroy/model-switch cycles.
- [ ] **SC-248** Ensure the public core API contains no Android-specific USB or overlay types.

---

## SC-260 — Android session service

- [ ] **SC-261** Implement foreground-service/session architecture appropriate to current Android requirements.
- [ ] **SC-262** Integrate USB transport, Rust session core, and firmware control.
- [ ] **SC-263** Implement explicit states: disconnected, connecting, ready, waking, audio-wait, ASR-loading, listening, standby, reconnecting, degraded, fatal error.
- [ ] **SC-264** Implement Start Listening orchestration.
- [ ] **SC-265** Implement Stop -> STANDBY orchestration and timeout-driven sleep behavior.
- [ ] **SC-266** Handle USB detach/reconnect during a session.
- [ ] **SC-267** Handle firmware reboot/protocol renegotiation during a session.
- [ ] **SC-268** Handle process/activity recreation without falsely showing a dead session as active.
- [ ] **SC-269** Add notification actions/state appropriate to background captioning.

---

## SC-280 — Android design system and navigation

- [ ] **SC-281** Implement dark-first color tokens from the spec.
- [ ] **SC-282** Validate text/control contrast and dynamic text accessibility.
- [ ] **SC-283** Implement typography, spacing, surfaces, state/error components, and audio activity indicator.
- [ ] **SC-284** Implement top-level Live / History / Settings navigation.
- [ ] **SC-285** Support portrait and landscape at the application level without forced orientation.
- [ ] **SC-286** Add screenshot/golden tests for critical states where practical.

---

## SC-300 — First-run setup UX

- [ ] **SC-301** Welcome/explanation screen centered on silent listening rather than video only.
- [ ] **SC-302** Dongle detection/connect step.
- [ ] **SC-303** USB permission and transport-verification step.
- [ ] **SC-304** Bluetooth media-output pairing/selection guidance.
- [ ] **SC-305** End-to-end test-audio step with visible returned-audio activity.
- [ ] **SC-306** Short ASR verification step.
- [ ] **SC-307** Request overlay permission only when needed and explain why.
- [ ] **SC-308** Provide specific remediation for each failed setup stage.
- [ ] **SC-309** Persist setup completion only after required gates actually pass or user explicitly exits setup.

---

## SC-320 — Live screen

- [ ] **SC-321** Show Caption Dongle readiness without exposing unnecessary protocol jargon.
- [ ] **SC-322** Show whether return audio is actually being received.
- [ ] **SC-323** Show selected ASR model/language.
- [ ] **SC-324** Implement prominent Start Listening / Stop control.
- [ ] **SC-325** Implement subtle audio-energy/activity visualization.
- [ ] **SC-326** Expose current display mode and quick selection.
- [ ] **SC-327** Show concise actionable connection/model/ASR errors.
- [ ] **SC-328** Never display Ready/Listening if the required underlying pipeline is not operational.

---

## SC-340 — Floating caption mode

- [ ] **SC-341** Implement overlay permission handling.
- [ ] **SC-342** Implement highly legible dark caption surface with approximately two-line default.
- [ ] **SC-343** Implement draggable position.
- [ ] **SC-344** Implement bounded resize/width behavior.
- [ ] **SC-345** Implement temporary tap controls for size, pause/resume, and close.
- [ ] **SC-346** Auto-hide controls without hiding captions.
- [ ] **SC-347** Render partial/final transitions without excessive visual instability.
- [ ] **SC-348** Persist independent portrait and landscape position/size.
- [ ] **SC-349** Clamp/recover overlay bounds after rotation, resolution, font-scale, or display changes.
- [ ] **SC-350** Test over representative video, podcast, browser, and other app surfaces.

---

## SC-360 — Compact caption mode

- [ ] **SC-361** Implement minimal one/two-line caption bar.
- [ ] **SC-362** Reuse the same caption event/state model as Floating mode.
- [ ] **SC-363** Implement drag/position and orientation persistence.
- [ ] **SC-364** Ensure Compact mode remains operable with large accessibility font scales.

---

## SC-380 — Reader mode

- [ ] **SC-381** Implement dedicated live scrolling transcript view.
- [ ] **SC-382** Distinguish current/partial text from finalized earlier text without sacrificing readability.
- [ ] **SC-383** Auto-scroll while user remains at live position.
- [ ] **SC-384** Stop forced auto-scroll when user scrolls backward.
- [ ] **SC-385** Show clear Jump to Live action.
- [ ] **SC-386** Implement comfortable portrait layout.
- [ ] **SC-387** Implement intentional landscape layout with bounded readable line width.
- [ ] **SC-388** Preserve live session across orientation changes.
- [ ] **SC-389** Verify podcast/audio-only workflow without any dependency on video metadata.

---

## SC-400 — Transcript history and privacy

- [ ] **SC-401** Keep transcript persistence OFF by default.
- [ ] **SC-402** Implement explicit opt-in setting and explanatory copy.
- [ ] **SC-403** Implement local session persistence only when enabled.
- [ ] **SC-404** Implement History session list.
- [ ] **SC-405** Implement transcript reader with timing metadata where available.
- [ ] **SC-406** Implement copy/share/export only through explicit user action.
- [ ] **SC-407** Implement delete-one and delete-all.
- [ ] **SC-408** Verify disabling persistence stops future writes.
- [ ] **SC-409** Verify normal diagnostic logs contain no transcript/audio payload.
- [ ] **SC-410** Define/document local storage and backup behavior for retained transcripts.

---

## SC-420 — Settings

- [ ] **SC-421** Captions: appearance, text size, max lines, default position/display mode.
- [ ] **SC-422** Speech: ASR model, model manager, language/auto behavior supported by each backend.
- [ ] **SC-423** Device: dongle state, transport, power behavior/timeouts.
- [ ] **SC-424** Privacy: save transcripts, retention controls, delete history.
- [ ] **SC-425** Advanced: audio diagnostics, ASR diagnostics, developer diagnostics.
- [ ] **SC-426** About: app version, firmware/protocol versions, licenses.
- [ ] **SC-427** Persist settings with schema/version migration tests.
- [ ] **SC-428** Ensure unsupported backend settings are not presented as universally available.

---

## SC-440 — Diagnostics and supportability

- [ ] **SC-441** Display USB bridge/VID/PID and transport state.
- [ ] **SC-442** Display firmware/protocol version and ESP32 power state.
- [ ] **SC-443** Display A2DP/stream state when known.
- [ ] **SC-444** Display audio format, packet gaps, overruns/underruns, buffer occupancy, and audio level.
- [ ] **SC-445** Display ASR engine/model/version/load state.
- [ ] **SC-446** Display RTF, queue depth, partial latency, and finalization latency.
- [ ] **SC-447** Add bounded/redacted diagnostic logging.
- [ ] **SC-448** Add explicit diagnostic audio recording only behind user action with prominent privacy indication.
- [ ] **SC-449** Add exportable support bundle that excludes transcript/raw audio by default.

---

## SC-460 — Reliability and recovery

- [ ] **SC-461** Test USB unplug/replug while idle.
- [ ] **SC-462** Test USB unplug/replug while listening.
- [ ] **SC-463** Test Bluetooth disconnect/reconnect.
- [ ] **SC-464** Test firmware reset mid-stream.
- [ ] **SC-465** Test malformed/corrupt protocol frames.
- [ ] **SC-466** Test sustained packet loss/backpressure.
- [ ] **SC-467** Test missing/corrupt model.
- [ ] **SC-468** Test ASR backend crash/error and restart.
- [ ] **SC-469** Test ASR slower than real time; ensure bounded backlog and visible degraded/error state.
- [ ] **SC-470** Test rapid model switching/start/stop cycles.
- [ ] **SC-471** Test Android activity/process recreation.
- [ ] **SC-472** Test phone rotation repeatedly during active captioning.
- [ ] **SC-473** Test font-scale/display-size changes.
- [ ] **SC-474** Run at least a two-hour end-to-end soak and record memory, transport errors, ASR RTF, thermals, and power observations.

---

## SC-480 — Security and privacy hardening

- [ ] **SC-481** Threat-model USB device impersonation/malformed firmware input.
- [ ] **SC-482** Enforce bounded protocol allocations and fuzz parser continuously in CI where practical.
- [ ] **SC-483** Verify model downloads against trusted metadata and cryptographic hashes.
- [ ] **SC-484** Ensure temporary model/download files cannot be mistaken for installed valid assets.
- [ ] **SC-485** Audit logs/analytics/crash reports for transcript/audio leakage.
- [ ] **SC-486** Ensure no network permission/use is required for normal inference after models are installed except explicitly documented features.
- [ ] **SC-487** Document third-party licenses for firmware, ASR runtimes, and model assets.
- [ ] **SC-488** Add dependency/security scanning appropriate to Rust, Android, and firmware ecosystems.

---

## SC-500 — Media compatibility matrix

- [ ] **SC-501** Define representative Android media categories: browser video, local media, podcast, audiobook, social video, streaming radio, and representative protected/DRM media.
- [ ] **SC-502** Test A2DP routing and return capture for each category.
- [ ] **SC-503** Record unsupported/protected behavior accurately; do not claim DRM circumvention.
- [ ] **SC-504** Verify source application does not need to expose subtitles/transcripts.
- [ ] **SC-505** Verify no video metadata is required for audio-only use.
- [ ] **SC-506** Publish compatibility findings with phone/OS/app versions used.

---

## SC-520 — Accessibility and UX validation

- [ ] **SC-521** Validate contrast against applicable WCAG guidance.
- [ ] **SC-522** Validate Android accessibility semantics for controls/settings.
- [ ] **SC-523** Test large font/display scaling in all three caption modes.
- [ ] **SC-524** Verify controls remain reachable in portrait and landscape.
- [ ] **SC-525** Test caption readability over bright, dark, and visually busy content.
- [ ] **SC-526** Test caption stability with rapid partial revisions.
- [ ] **SC-527** Conduct task-oriented usability pass: first setup, start podcast, start video, switch ASR, rotate, stop, resume, delete history.
- [ ] **SC-528** Ensure common post-setup workflow is approximately: connect/leave dongle -> open app -> Start Listening -> return to desired content.

---

## SC-540 — Android v0.1 release hardening

- [ ] **SC-541** Define supported Android API/device/architecture matrix from actual test results.
- [ ] **SC-542** Verify release build/minification/native library packaging.
- [ ] **SC-543** Verify model storage and free-space handling.
- [ ] **SC-544** Verify foreground-service/notification behavior against target Android version requirements.
- [ ] **SC-545** Verify overlay permission UX and recovery after permission revocation.
- [ ] **SC-546** Verify USB permission behavior after reboot/reconnect.
- [ ] **SC-547** Run full unit/integration/instrumented/hardware acceptance suite.
- [ ] **SC-548** Produce reproducible firmware build and record firmware hash/version.
- [ ] **SC-549** Produce reproducible-enough release build instructions and artifact checksums.
- [ ] **SC-550** Update user-facing setup/troubleshooting documentation from actual hardware behavior.

**Gate SC-G11:** Android v0.1 passes all mandatory acceptance criteria in the companion specification.

---

## SC-560 — iOS feasibility gate

This phase begins only after the transport-neutral Rust boundary exists; it may be researched earlier but must not force Android-specific compromises into the core.

- [ ] **SC-561** Document current iOS accessory/USB/BLE constraints relevant to the final hardware.
- [ ] **SC-562** Select a legally/platform-supported candidate return transport for iOS.
- [ ] **SC-563** Prototype iPhone -> Bluetooth A2DP -> hardware -> return transport -> same iPhone.
- [ ] **SC-564** Measure sustainable return bandwidth and latency.
- [ ] **SC-565** Verify simultaneous A2DP sink and selected return transport behavior on candidate hardware.
- [ ] **SC-566** Validate Rust core/FFI build for iOS.
- [ ] **SC-567** Feed a real returned stream into one ASR backend on iPhone.
- [ ] **SC-568** Record MFi/accessory/custom-hardware implications, if any, before committing to production hardware.
- [ ] **SC-569** Decide whether WROOM-32 remains viable for cross-platform production hardware or a different SoC/bridge architecture is required.

**Gate SC-G12:** do not begin a polished iOS client until the complete return-audio loop is proven on iPhone.

---

## SC-580 — iOS client (post-feasibility)

- [ ] **SC-581** Create Swift/SwiftUI application shell.
- [ ] **SC-582** Integrate Rust core through the shared FFI contract.
- [ ] **SC-583** Implement selected iOS transport adapter.
- [ ] **SC-584** Implement Live/Reader experience appropriate to iOS platform capabilities.
- [ ] **SC-585** Map Floating/Compact concepts only to mechanisms permitted by iOS; document unavoidable platform differences.
- [ ] **SC-586** Implement model management for all supported ASR backends.
- [ ] **SC-587** Implement privacy/history/settings parity where platform capabilities allow.
- [ ] **SC-588** Add iOS-specific lifecycle, interruption, rotation, accessibility, thermal, and soak tests.

---

## SC-600 — Final v0.1 completion audit

- [ ] **SC-601** Re-read `SILENT_CAPTION_V01_SPEC.md` and map every normative requirement to code/test/evidence or an explicitly deferred post-v0.1 item.
- [ ] **SC-602** Verify every closed TODO checkbox has supporting evidence.
- [ ] **SC-603** Verify all open validation items in the spec have recorded outcomes or remain explicitly blocked/deferred.
- [ ] **SC-604** Run formatting/lint/build/unit/integration/security checks from clean checkout.
- [ ] **SC-605** Run critical hardware acceptance from a cleanly flashed ESP32 and cleanly installed Android app.
- [ ] **SC-606** Run final two-hour end-to-end soak.
- [ ] **SC-607** Verify all three ASR models remain selectable and correctly identified.
- [ ] **SC-608** Verify portrait and landscape for Floating, Compact, and Reader modes.
- [ ] **SC-609** Verify transcript retention is OFF on a fresh install.
- [ ] **SC-610** Verify whole-board Active/Standby/Deep-Sleep measurements are documented.
- [ ] **SC-611** Verify no undocumented additional prototype hardware/wiring became required.
- [ ] **SC-612** Verify README/setup docs match the actual tested workflow.
- [ ] **SC-613** Record final known limitations and compatibility matrix.
- [ ] **SC-614** Tag/record the firmware, Rust core, Android app, protocol, and model-package versions used for v0.1 acceptance.

**Final gate SC-G13:** v0.1 is complete only when mandatory acceptance criteria pass and remaining limitations are explicit rather than hidden behind unchecked assumptions.