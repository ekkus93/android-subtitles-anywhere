# SC-011 Prototype Board Identification

**Task:** SC-011  
**Evidence date:** 2026-08-14  
**Status:** PASS

## Selected prototype board

The v0.1 Bluetooth/audio-return prototype uses the ESP32 board shown in the user-supplied photographs identified during SC-011 as the **third and fourth photographs**. The first and second photographs show a separate ESP32-S3-N16R8 board and are explicitly **not** the Bluetooth Classic/A2DP prototype covered by SC-011.

The selected WROOM-32 boards were purchased as Amazon ASIN `B0BQJ8BTVB`, listed as an **Aideepen 6-Pack 30PIN ESP-WROOM-32 Development Board / ESP32S**, 30-pin Micro-USB variant. This purchase identity supplements, but does not override, direct physical and USB-enumeration evidence.

Observed characteristics of the selected board:

- Espressif module shield marking: `ESP32-WROOM-32`.
- Carrier-board bottom silkscreen: `ESP32S`.
- Aideepen retail listing / Amazon ASIN: `B0BQJ8BTVB`.
- 30-pin development-board form factor (15 header positions per side).
- Micro-USB connector.
- Onboard USB-to-UART bridge present; direct enumeration under SC-012 identifies the prototype as Silicon Labs CP210x, VID/PID `10c4:ea60`.
- `EN` and `BOOT` pushbuttons present.
- Four PCB mounting holes.
- No PCB revision identifier is visible in the supplied front/back photographs.

The separate ESP32-S3 boards were purchased as Amazon ASIN `B0DG8L5NG5`, listed under brand **AYWHP** as an ESP32-S3 development board with N16R8-class module and dual USB Type-C ports. The photographed module is marked `ESP32-S3-N16R8`. These boards are contextual hardware only and are not the Bluetooth Classic/A2DP endpoint for v0.1.

## Identification policy

The WROOM-32 prototype should be described as:

> **Aideepen-listed `ESP32S` 30-pin ESP32-WROOM-32 development board, Amazon ASIN B0BQJ8BTVB; PCB revision unknown.**

The project must not silently relabel this hardware as a DOIT ESP32 DevKit V1, NodeMCU-32S, Espressif DevKitC, or another reference board merely because the layout is similar. Any schematic-based assumption must be verified against this physical board before it is used as hardware evidence.

## Scope

This evidence closes only SC-011. The following remain separate characterization tasks:

- SC-012: identify the exact onboard USB-UART bridge and enumerate its VID/PID/descriptors.
- SC-013: establish EN/GPIO0/DTR/RTS auto-reset wiring from direct validation or a positively matched schematic.
- SC-014: characterize regulator, LEDs, USB bridge, and other always-on loads.

## Gate impact

SC-G1 remains **OPEN**. Exact Android USB enumeration and the remaining prototype/phone characterization tasks have not yet been completed.
