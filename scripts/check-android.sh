#!/usr/bin/env bash
set -euo pipefail

: "${GRADLE_CMD:=gradle}"

"${GRADLE_CMD}" -p android --no-daemon \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug
