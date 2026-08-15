# SC-012 USB-UART Identification

**Task:** SC-012  
**Evidence date:** 2026-08-14  
**Status:** PARTIAL — Linux enumeration established; Android-visible descriptors still require direct validation.

## ESP32-WROOM-32 prototype

The selected SC-011 ESP32-WROOM-32 prototype was connected to a Linux USB host and enumerated as:

```text
Bus 001 Device 010: ID 10c4:ea60 Silicon Labs CP210x UART Bridge
```

Observed identity:

- USB VID: `0x10c4` (Silicon Labs)
- USB PID: `0xea60`
- Host-visible product text: `Silicon Labs CP210x UART Bridge`
- Functional class for this project: onboard USB-to-UART bridge

This is direct enumeration evidence and is authoritative for the selected physical prototype. The Android USB transport should initially support VID/PID `10c4:ea60`, while still handling permission and unsupported-device states explicitly.

## ESP32-S3 comparison board

A separate ESP32-S3 board shown in the first two SC-011 photographs enumerated on the same Linux host as:

```text
Bus 001 Device 008: ID 303a:4001 ESP32 Macro Keyboard Project ESP32 Macro Keyboard
```

This ESP32-S3 device is **not** the v0.1 Bluetooth Classic/A2DP prototype and its `303a:4001` identity must not be used as the WROOM-32 USB filter. Its custom strings also indicate that this enumeration reflects firmware/configuration on that separate board rather than the WROOM-32 CP210x path.

## Remaining SC-012 evidence

Before SC-012 is closed, record the descriptors actually visible through Android's `UsbDevice`/USB host API for the WROOM-32 prototype, including at minimum VID/PID and any manufacturer/product/serial strings Android exposes. Linux enumeration alone does not prove Android-visible descriptor behavior.

## Gate impact

SC-G1 remains **OPEN**. SC-016 separately requires proof that the target Android phone enumerates this exact board over the intended USB data/OTG cable with no additional wiring/components.
