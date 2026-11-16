# Voice Translator — Offline Indonesia ↔ Mandarin

Aplikasi Android penerjemah suara-ke-suara (speech-to-speech) yang berjalan **sepenuhnya offline** di perangkat, dirancang untuk HP Android RAM 4 GB.

## Fitur

- Percakapan dua arah Indonesia ↔ Mandarin, berbasis giliran (push-to-talk).
- Input suara (ASR lokal) maupun input teks.
- Terjemahan teks, suara hasil terjemahan otomatis (TTS lokal), putar ulang, dan salin teks.
- Riwayat percakapan (Room) dan pengaturan kecepatan suara.
- Pengelola model: unduh sekali, verifikasi SHA-256, resume unduhan, hapus paket.
- Semua model berjalan di perangkat; tidak ada API key dan tidak ada data yang dikirim keluar.

## Arsitektur

```
Mikrofon → ASR (sherpa-onnx zipformer) → MT (2× OPUS-MT Marian int8 via Inggris) → TTS (Piper int8) → Speaker
```

OPUS-MT tidak punya pasangan langsung Indonesia–Mandarin, sehingga:

- ID → ZH: `opus-mt-id-en` → `opus-mt-en-zh`
- ZH → ID: `opus-mt-zh-en` → `opus-mt-en-id`

Aturan memori (target RAM 4 GB): ASR, penerjemah, dan TTS **tidak pernah dimuat bersamaan**. `EngineHost` melepas tahap sebelumnya sebelum memuat tahap berikutnya, dan `onTrimMemory`/`onLowMemory` melepas semua sesi model.

## Model

| Paket | Isi | Ukuran unduh |
|---|---|---|
| `asr-multilingual` | streaming zipformer (ar/en/id/ja/ru/th/vi/zh), encoder int8 | ~340 MB |
| `mt-id-en`, `mt-en-zh`, `mt-zh-en`, `mt-en-id` | OPUS-MT Marian int8 (encoder + decoder + decoder_with_past) | ~208 MB masing-masing |
| `tts-id` | VITS Piper id_ID news int8 + `espeak-ng-data` (sudah menyatu) | ~36 MB |
| `tts-zh` | VITS Piper zh_CN xiao_ya int8 + lexicon | ~20 MB |

Total unduhan sekitar **1,2 GB**, diunduh sekali saat pertama kali (bukan di dalam APK). Ini di atas target 700 MB pada BRD v1.1; lihat bagian Batasan.

## Build

Prasyarat: JDK 17+, Android SDK (platform 35, build-tools), Gradle 8.9 (wrapper tersedia).

Sekali sebelum build pertama, ambil pustaka native sherpa-onnx (versi **1.13.8**, build statik dengan ONNX Runtime):

```bash
bash tools/fetch_sherpa.sh
```

Versi ini dipakai karena sherpa-onnx 2.x menghapus frontend espeak untuk model Piper VITS, sehingga TTS Indonesia gagal bersuara.

```bash
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK release (ditandatangani debug key bila KEYSTORE_PATH kosong)
```

APK ada di `app/build/outputs/apk/`.

Release dengan keystore sendiri:

```bash
KEYSTORE_PATH=/path/release.jks KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... ./gradlew :app:assembleRelease
```

GitHub Actions: `.github/workflows/android.yml` membangun APK debug + release pada setiap push ke `main` dan mengunggahnya sebagai artifact.

## Menyiapkan model offline

1. Ekspor model terjemahan (sekali saja, butuh ~1 jam di CPU):

   ```bash
   python3 -m venv /tmp/mtenv && /tmp/mtenv/bin/pip install -r tools/requirements.txt
   /tmp/mtenv/bin/python tools/export_mt.py --out models-out/mt
   ```

2. Unduh model suara:

   ```bash
   bash tools/fetch_speech.sh models-out/speech
   ```

3. Kemas arsip dan perbarui katalog aplikasi:

   ```bash
   /tmp/mtenv/bin/python tools/package_models.py \
     --base-url https://github.com/<user>/<repo>/releases/download/models-v1
   ```

4. Unggah semua `release/*.zip` ke GitHub Release dengan tag yang sama, lalu rebuild APK agar `app/src/main/assets/model_catalog.json` terbaru ikut tertanam.

Alternatif: jalankan workflow **Models** secara manual dari tab Actions; workflow ini mengekspor, mengemas, mengunggah rilis model, dan memperbarui katalog secara otomatis.

URL model juga dapat diganti tanpa rebuild melalui **Pengaturan → Sumber unduhan model**.

## Pengujian

```bash
./gradlew :mt-engine:test
```

Tes memakai model di `models-out/mt` (atau `MT_MODELS_DIR`). Tes yang tersedia:

- `idToEnglishMatchesReference` — kecocokan terjemahan referensi.
- `indonesianToChinesePivot`, `chineseToIndonesian` — rute pivot dua arah.
- `benchmarkShortSentences` — waktu inferensi per kalimat.

Validasi ucapan (TTS → ASR, memastikan model suara bersuara dan dikenali kembali):

```bash
python3 -m venv /tmp/speechenv && /tmp/speechenv/bin/pip install sherpa-onnx==1.13.8
/tmp/speechenv/bin/python tools/speech_roundtrip.py
```

## Batasan

- Kualitas terjemahan mengikuti OPUS-MT; rute pivot lewat Inggris dapat menghilangkan nuansa.
- ASR multibahasa memakai satu model besar (~340 MB) karena tidak ada model Indonesia-saja di katalog sherpa-onnx.
- Total model ~1,2 GB (di atas target BRD 700 MB) demi kualitas yang memadai di RAM 4 GB.
- TTS Indonesia memerlukan `espeak-ng-data`, yang ikut di dalam paket `tts-id`; TTS Mandarin memakai `lexicon.txt`.
- Lisensi model mengikuti sumbernya: OPUS-MT (CC-BY-4.0), sherpa-onnx Piper (lihat model card masing-masing). Periksa sebelum distribusi komersial.
