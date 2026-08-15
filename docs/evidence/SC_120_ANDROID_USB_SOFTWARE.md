# SC-120 Android USB transport — software evidence

This pass establishes the software-verifiable Android USB transport implementation. It does not claim physical CP210x/Android acceptance.

## Implemented software-verifiable scope

- Characterized prototype identity constants for Silicon Labs CP210x VID/PID `10c4:ea60`.
- `UsbConnectionCoordinator` deterministically prefers the characterized bridge. A single unknown attached device may be surfaced for explicit user confirmation; multiple unknown devices fail as unsupported rather than guessing.
- `AndroidUsbPlatform` binds discovery, permission checking/request and device opening to Android `UsbManager`.
- Permission-required, denial, retry/open-failure, unsupported/no-device, ready and detached states are explicit.
- CP210x serial support uses pinned `usb-serial-for-android` 3.9.0 and configures 921600 baud, 8 data bits, no parity and one stop bit.
- DTR and RTS are deliberately deasserted after opening. Normal transport setup never intentionally pulses reset/bootloader modem-control lines.
- Blocking byte transport remains isolated behind `UsbSessionController`'s worker thread so USB I/O does not run on the Compose/UI thread.
- Reads use bounded 4096-byte chunks with finite timeout and cancellation/close semantics.
- Bounded 4128-byte protocol accumulation (`32 + 4096`) handles arbitrary partial reads, SCAP resynchronization and oversized-length rejection.
- `ProtocolFrameSink` remains the boundary intended for the Rust mobile/JNI parser integration in SC-126.
- JVM tests cover fragmented frames, malformed/noise resynchronization, bounded oversized input, clean detach, unexpected disconnect, characterized-device preference, ambiguous-device rejection, permission denial, open failure and detach selection clearing.

## Deliberately hardware/cross-language work still open

Physical testing is still required to establish that the actual Android device grants permission, the CP210x library opens this exact bridge reliably at 921600 baud, and deasserted DTR/RTS does not reset or bootload the prototype ESP32-WROOM-32.

SC-126 remains open: complete candidate frames currently terminate at `ProtocolFrameSink`; the actual JNI/UniFFI call into the Rust protocol parser is the next cross-language integration block.

## Gate conclusion

Gate SC-G7 remains open until real attach/detach/permission/CP210x behavior and Rust frame validation are demonstrated. The software implementation is structured so failures cannot silently become a ready state.
