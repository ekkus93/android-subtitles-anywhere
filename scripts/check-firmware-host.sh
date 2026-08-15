#!/usr/bin/env bash
set -euo pipefail

cc="${CC:-cc}"
mkdir -p .tmp/firmware-host

"${cc}" -std=c11 -Wall -Wextra -Werror -Wpedantic \
  -Ifirmware/main \
  firmware/main/audio_queue.c tests/host/test_audio_queue.c \
  -o .tmp/firmware-host/test_audio_queue
.tmp/firmware-host/test_audio_queue

"${cc}" -std=c11 -Wall -Wextra -Werror -Wpedantic \
  -Ifirmware/main \
  firmware/main/return_audio.c tests/host/test_return_audio.c \
  -o .tmp/firmware-host/test_return_audio
.tmp/firmware-host/test_return_audio
