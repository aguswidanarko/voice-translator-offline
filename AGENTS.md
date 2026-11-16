# Agent guide

## Commands

| Task | Command |
|---|---|
| Build APK | `./gradlew :app:assembleDebug` |
| Release APK | `./gradlew :app:assembleRelease` |
| MT engine tests | `./gradlew :mt-engine:test` |
| Export OPUS-MT models | `python tools/export_mt.py --out models-out/mt` (deps: `tools/requirements.txt`) |
| Fetch ASR/TTS models | `bash tools/fetch_speech.sh` |
| Fetch sherpa-onnx AAR bits | `bash tools/fetch_sherpa.sh` (pinned 1.13.8; required before first build) |
| Package models + catalog | `python tools/package_models.py --base-url <release-folder-url>` |

Local toolchain on this machine: Gradle at `.tools/gradle-8.9/bin/gradle` (wrapper also committed),
JDK 18 at `/Library/Java/JavaVirtualMachines/jdk-18.0.1.1.jdk/Contents/Home`,
Android SDK path in `local.properties`. Python scripts need a venv from `tools/requirements.txt`
(the export/validation venv is disposable and was removed to reclaim disk; recreate when needed).

## Layout

- `app/` — Android app: Compose UI (`ui/`), engines (`engine/`), audio (`audio/`),
  data layer (`data/model`, `data/db`, `data/settings`).
- `mt-engine/` — pure JVM ONNX Marian translator and SentencePiece tokenizer, unit tested.
- `tools/` — Python export/packaging scripts and speech model fetch script.
- `models-out/` — local model exports/downloads (gitignored).

## Invariants

- Memory: `EngineHost` loads only one stage (ASR / MT / TTS) at a time and releases the
  previous stage first. `VoiceTranslatorApp.onTrimMemory`/`onLowMemory` releases everything.
  Do not keep engines alive in parallel.
- sherpa-onnx is instantiated with a null `AssetManager` so the native `newFromFile` path is
  used; models live on internal storage, never in assets.
- sherpa-onnx is pinned to **1.13.8** (static-link AAR via `tools/fetch_sherpa.sh`). 2.x removed
  the espeak frontend for Piper VITS, which breaks Indonesian TTS. Do not bump without
  re-validating `tools/speech_roundtrip.py`.
- SentencePiece internal ids differ from OPUS-MT `vocab.json` ids. `source_vocab.tsv` stores
  `vocab.json` ids and `SentencePieceTokenizer` maps pieces to those ids. Never use raw SPM ids.
- Marian logits have 54796 entries while the SP vocab has 32000; decoding stops on EOS or
  `id >= 32000`.
- Models are never bundled in the APK. `app/src/main/assets/model_catalog.json` drives
  downloads (size + SHA-256) via `ModelDownloadWorker`.
- No inference over the network, no API keys in the APK.

## Model directory layouts

- MT: `model.json`, `encoder.int8.onnx`, `decoder.int8.onnx`, `decoder_with_past.int8.onnx`,
  `source_vocab.tsv`, `target_vocab.tsv`
- ASR: `encoder*.onnx`, `decoder*.onnx`, `joiner*.onnx`, `tokens.txt`
- TTS: any `*.onnx`, `tokens.txt`, plus `lexicon.txt` (Mandarin) or `espeak-ng-data/` (Indonesian).
  The Indonesian Piper archive bundles `espeak-ng-data` inside its own package; there is no shared
  espeak package.
