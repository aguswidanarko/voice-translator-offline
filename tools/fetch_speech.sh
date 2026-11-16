#!/usr/bin/env bash
# Downloads the speech models (ASR + TTS). The Indonesian Piper archive already
# bundles espeak-ng-data, so no separate espeak package is required.
#
#   tools/fetch_speech.sh [dest]        # default dest: models-out/speech
set -euo pipefail

DEST="${1:-models-out/speech}"
mkdir -p "$DEST"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download"

fetch() {
  local url="$1" name="$2"
  curl -fL --retry 3 -o "$WORK/$name" "$url"
  tar xjf "$WORK/$name" -C "$DEST"
  rm -f "$WORK/$name"
}

fetch "$BASE/asr-models/sherpa-onnx-streaming-zipformer-ar_en_id_ja_ru_th_vi_zh-2025-02-10.tar.bz2" asr.tar.bz2
fetch "$BASE/tts-models/vits-piper-id_ID-news_tts-medium-int8.tar.bz2" tts-id.tar.bz2
fetch "$BASE/tts-models/vits-piper-zh_CN-xiao_ya-medium-int8.tar.bz2" tts-zh.tar.bz2

find "$DEST" -maxdepth 1 -type d | sort
