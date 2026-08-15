# SC-061 through SC-066 A2DP software evidence

## Scope

This evidence covers the software-verifiable portion of the ESP32 Bluetooth/A2DP proof. Physical pairing and continuous media evidence remain SC-067 through SC-069 and Gate SC-G4.

## SC-061 — Classic Bluetooth initialization

`firmware/main/a2dp_sink.c` initializes NVS, releases unused BLE controller memory, initializes/enables the ESP32 controller in Classic Bluetooth mode, and initializes/enables Bluedroid. The pinned ESP-IDF firmware build validates the API surface.

## SC-062 — deterministic sink identity

The sink name is the compile-time constant `Silent Caption`. The firmware sets connectable/general-discoverable scan mode. Actual Android pairing behavior is intentionally deferred to SC-067 hardware acceptance.

## SC-063 — connection/audio state

The A2DP callback records connection and audio-started state and records the negotiated SBC sample rate and channel count using ESP-IDF 5.5.5's typed `sbc_info` representation.

## SC-064 — silent audio callback

The A2DP data callback accepts received digital audio and places it into the bounded producer queue. There is no DAC, I2S, codec, or speaker playback path in the firmware component. Physical inaudibility remains SC-068.

## SC-065 — instrumentation

The producer records callback count, callback bytes, latest and maximum callback interval, dropped blocks/bytes, and queue high-water occupancy. Negotiated sample rate/channels are retained by the A2DP status structure.

## SC-066 — bounded nonblocking producer

`audio_queue.c` is a fixed-capacity ring of 12 blocks, each bounded to 1024 bytes. Push is finite local memory copy/accounting only: it has no waits, locks, UART operations, allocation, or retry loop. A full queue drops the incoming block and increments explicit counters.

The queue implementation is deliberately independent of FreeRTOS so it can be compiled and exercised on the host. `tests/host/test_audio_queue.c` covers FIFO behavior, timing/high-water metrics, full-queue drop behavior, and malformed/oversized input. `scripts/check-firmware-host.sh` builds it as C11 with `-Wall -Wextra -Werror -Wpedantic`; `scripts/check-firmware.sh` runs that host test before the pinned ESP-IDF build.

## Closure rule

SC-061 through SC-066 may be marked complete when the repository firmware check is green on the closure commit. SC-067 through SC-069 and SC-G4 remain open until physical ESP32-WROOM-32 + Android testing is performed.
