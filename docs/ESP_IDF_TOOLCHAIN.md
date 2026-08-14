# ESP-IDF Toolchain

**TODO:** SC-003  
**Target:** ESP32 / ESP32-WROOM-32  
**Pinned ESP-IDF:** v5.5.5

`firmware/IDF_VERSION` is the machine-readable source of truth for the ESP-IDF release used by this repository. Do not build release firmware against an unpinned `master`, `release/*`, or another locally installed IDF and report it as equivalent.

## Why v5.5.5

Silent Caption requires the original ESP32's Bluetooth Classic stack and an A2DP sink. ESP-IDF 5.5 documents ESP32 dual-mode Bluetooth 4.2, Bluedroid as its Classic Bluetooth host, `CONFIG_BT_CLASSIC_ENABLED`, and `CONFIG_BT_A2DP_ENABLE`. Its A2DP API includes a sink data callback and an A2DP sink example. The v5.5 line therefore provides the required supported primitives while keeping the project on a current ESP-IDF 5.x bug-fix line.

The project initially uses the built-in SBC-capable A2DP path. Whether the firmware ultimately forwards encoded SBC frames or normalized PCM remains an SC-020/SC-030 implementation decision and must be measured rather than encoded into the toolchain choice.

## Installation

On Linux/macOS with the ESP-IDF host prerequisites installed:

```sh
./scripts/install-esp-idf.sh
```

By default this installs under `~/.espressif/frameworks/esp-idf-v5.5.5`. Override the parent directory with `ESP_IDF_ROOT` if needed.

Activate the environment:

```sh
. "$HOME/.espressif/frameworks/esp-idf-v5.5.5/export.sh"
```

Confirm the exact release:

```sh
idf.py --version
```

The reported version must identify ESP-IDF v5.5.5 before firmware acceptance evidence is recorded.

## Build

From a clean checkout after activation:

```sh
idf.py -C firmware set-target esp32
idf.py -C firmware build
```

`firmware/sdkconfig.defaults` establishes the target and required Bluedroid/Classic Bluetooth/A2DP feature baseline. Generated `firmware/sdkconfig` remains a build artifact and must not become the version-selection mechanism.

## Upgrade policy

ESP-IDF upgrades are intentional repository changes. An upgrade must update `firmware/IDF_VERSION`, validate the installer and clean firmware build, verify the required Bluetooth Classic/A2DP Kconfig/API surface, run firmware tests, and repeat hardware A2DP/USB acceptance gates affected by the Bluetooth stack change. Do not silently float to a newer bug-fix release in CI.
