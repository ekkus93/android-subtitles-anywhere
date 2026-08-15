# SC-101 through SC-109 UART transport evidence

This evidence covers software-verifiable ESP32 USB-UART transport work. Physical CP210x throughput, error-rate, Bluetooth coexistence, and Android USB acceptance remain hardware gates.

## Implemented

- Protocol-v1 C codec matching the Rust wire layout: `SCAP`, v1.0, 32-byte header, bounded 4096-byte payload, sequence/session/timestamp fields and CRC-32/ISO-HDLC.
- UART0 configured as 8N1 at the provisional 921600 baud rate. This rate remains subject to SC-084 hardware measurement.
- Fixed eight-frame TX queue with explicit drop/high-water counters. Producers never wait for UART transmission.
- Independent RX and TX FreeRTOS tasks. RX performs bounded frame accumulation/resynchronization and routes only validated complete control frames to the state machine.
- HELLO payload with boot ID, UART rate, protocol version, maximum payload and return-audio capability bits.
- START_SESSION, STOP_SESSION and HEARTBEAT lifecycle with stale-session rejection.
- Five-second heartbeat watchdog that invalidates an abandoned active session.
- Diagnostics payload exposing queued/sent/dropped frame counts, TX high-water and configured UART rate.
- Host tests compiled with strict C11 warnings-as-errors for codec integrity/truncation, session lifecycle, heartbeat expiry, stale-session rejection and TX saturation.

## Important implementation boundary

The A2DP callback does not write to UART. Bluetooth audio first enters a bounded producer queue; UART transmission is performed independently. This preserves the nonblocking Bluetooth callback invariant established by SC-066.

## Hardware-dependent acceptance still open

The software build cannot establish that 921600 baud is reliable through the actual CP210x board/Android USB stack, nor can it measure sustained Bluetooth-plus-UART CPU load and drop rate. Those results are intentionally not claimed here.

Gate SC-G6 should close only after the pinned ESP-IDF build and host checks are green and the later hardware transport test demonstrates sustained selected-audio streaming without Bluetooth callback starvation.
