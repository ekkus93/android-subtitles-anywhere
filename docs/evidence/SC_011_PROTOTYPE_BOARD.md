# SC-011 Prototype Board Identification

**Task:** SC-011  
**Evidence date:** 2026-08-14  
**Status:** PASS

## Selected prototype board

The v0.1 Bluetooth/audio-return prototype uses the ESP32 board shown in the user-supplied photographs identified during SC-011 as the **third and fourth photographs**. The first and second photographs show a separate ESP32-S3-N16R8 board and are explicitly **not** the Bluetooth Classic/A2DP prototype covered by SC-011.

Observed characteristics of the selected board:

- Espressif module shield marking: `ESP32-WROOM-32`.
- Carrier-board bottom silkscreen: `ESP32S`.
- Generic 30-pin development-board form factor (15 header positions per side).
- Micro-USB connector.
- Onboard USB-to-UART bridge present.
- `EN` and `BOOT` pushbuttons present.
- Four PCB mounting holes.
- No manufacturer name, commercial board model, or PCB revision identifier is visible in the supplied front/back photographs.

## Identification policy

The board must therefore be described in project documentation as:

> **Generic `ESP32S` 30-pin ESP32-WROOM-32 development board; manufacturer and PCB revision unknown.**

The project must not silently relabel this hardware as a DOIT ESP32 DevKit V1, NodeMCU-32S, Espressif DevKitC, or another branded/reference board merely because the layout is similar. Any schematic-based assumption must be verified against this physical board before it is used as hardware evidence.

## Scope

This evidence closes only SC-011. The following remain separate characterization tasks:

- SC-012: identify the exact onboard USB-UART bridge and enumerate its VID/PID/descriptors.
- SC-013: establish EN/GPIO0/DTR/RTS auto-reset wiring from direct validation or a positively matched schematic.
- SC-014: characterize regulator, LEDs, USB bridge, and other always-on loads.

## Gate impact

SC-G1 remains **OPEN**. Exact USB enumeration and the remaining prototype/phone characterization tasks have not yet been completed.
