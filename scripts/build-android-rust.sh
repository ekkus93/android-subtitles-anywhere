#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point to an installed Android NDK}"

TARGET="aarch64-linux-android"
API=26
HOST_TAG="linux-x86_64"
TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST_TAG}"
LINKER="${TOOLCHAIN}/bin/aarch64-linux-android${API}-clang"

if [[ ! -x "${LINKER}" ]]; then
  echo "Android NDK linker not found: ${LINKER}" >&2
  exit 1
fi

rustup target add "${TARGET}"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="${LINKER}"
cargo build --manifest-path core-jni/Cargo.toml --target "${TARGET}" --release

SOURCE="core-jni/target/${TARGET}/release/libsilent_caption_jni.so"
DESTINATION="android/app/src/main/jniLibs/arm64-v8a/libsilent_caption_jni.so"
test -f "${SOURCE}"
mkdir -p "$(dirname "${DESTINATION}")"
cp "${SOURCE}" "${DESTINATION}"
