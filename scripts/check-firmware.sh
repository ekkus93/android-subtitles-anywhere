#!/usr/bin/env bash
set -euo pipefail

bash scripts/check-firmware-host.sh

if ! command -v idf.py >/dev/null 2>&1; then
  echo "idf.py is not available; activate the pinned ESP-IDF environment first." >&2
  exit 1
fi

expected="$(tr -d '[:space:]' < firmware/IDF_VERSION)"
actual="$(idf.py --version | sed -n 's/^ESP-IDF \(v[^-[:space:]]*\).*/\1/p')"

if [[ "${actual}" != "${expected}" ]]; then
  echo "ESP-IDF version mismatch: expected ${expected}, got ${actual:-unknown}" >&2
  exit 1
fi

idf.py -C firmware set-target esp32
idf.py -C firmware build
