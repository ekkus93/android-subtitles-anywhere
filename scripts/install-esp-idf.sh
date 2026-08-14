#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
idf_version="$(tr -d '[:space:]' < "${repo_root}/firmware/IDF_VERSION")"
idf_root="${ESP_IDF_ROOT:-${HOME}/.espressif/frameworks}"
idf_path="${idf_root}/esp-idf-${idf_version}"

if [[ ! "${idf_version}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid firmware/IDF_VERSION: ${idf_version}" >&2
  exit 1
fi

mkdir -p "${idf_root}"

if [[ -d "${idf_path}/.git" ]]; then
  actual="$(git -C "${idf_path}" describe --tags --exact-match HEAD 2>/dev/null || true)"
  if [[ "${actual}" != "${idf_version}" ]]; then
    echo "Existing ${idf_path} is not pinned at ${idf_version}." >&2
    exit 1
  fi
else
  git clone --branch "${idf_version}" --depth 1 --recursive \
    https://github.com/espressif/esp-idf.git "${idf_path}"
fi

"${idf_path}/install.sh" esp32

cat <<EOF
ESP-IDF ${idf_version} installed at:
  ${idf_path}

Activate it with:
  . "${idf_path}/export.sh"
EOF
