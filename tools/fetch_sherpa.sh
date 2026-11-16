#!/usr/bin/env bash
# Fetches the sherpa-onnx static AAR and unpacks what the app needs:
#   app/libs/sherpa-onnx-classes.jar
#   app/src/main/jniLibs/<abi>/libsherpa-onnx-jni.so
#
# Static-link variant: onnxruntime is embedded, so the app uses onnxruntime-android
# only for the OPUS-MT models.
set -euo pipefail

VERSION="${SHERPA_VERSION:-1.13.8}"
URL="${SHERPA_AAR_URL:-https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-static-link-onnxruntime-${VERSION}.aar}"
ABIS="${ABIS:-arm64-v8a x86_64}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "mengunduh $URL"
curl -fL --retry 3 -o "$WORK/sherpa.aar" "$URL"
unzip -q "$WORK/sherpa.aar" -d "$WORK/x"

mkdir -p "$ROOT/app/libs"
cp "$WORK/x/classes.jar" "$ROOT/app/libs/sherpa-onnx-classes.jar"

for abi in $ABIS; do
  if [ -d "$WORK/x/jni/$abi" ]; then
    mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
    cp "$WORK/x/jni/$abi/"*.so "$ROOT/app/src/main/jniLibs/$abi/"
    echo "  $abi: $(ls "$ROOT/app/src/main/jniLibs/$abi" | tr '\n' ' ')"
  else
    echo "  peringatan: $abi tidak ada di AAR" >&2
  fi
done
