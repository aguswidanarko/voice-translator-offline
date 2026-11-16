#!/usr/bin/env python3
"""Round-trip check for the offline speech stack: Piper TTS -> zipformer ASR.

Run with a venv that has sherpa-onnx==1.13.8 installed:
    python tools/speech_roundtrip.py
"""

from __future__ import annotations

import sys
import wave
from pathlib import Path

import sherpa_onnx

ROOT = Path(__file__).resolve().parent.parent
SPEECH = ROOT / "models-out" / "speech"
ASR_DIR = next(SPEECH.glob("sherpa-onnx-streaming-zipformer-*"))
TTS_DIRS = {
    "id": next(SPEECH.glob("vits-piper-id*")),
    "zh": next(SPEECH.glob("vits-piper-zh*")),
}

SAMPLES = {
    "id": [
        "Selamat pagi, apa kabar?",
        "Di mana stasiun kereta terdekat?",
        "Saya ingin memesan dua tiket ke Beijing.",
    ],
    "zh": [
        "早上好，你好吗？",
        "最近的火车站在哪里？",
        "我想订两张去北京的票。",
    ],
}


def asr_files() -> tuple[str, str, str]:
    encoder = next(ASR_DIR.glob("encoder*.int8.onnx"))
    decoder = next(p for p in ASR_DIR.glob("decoder*.onnx") if "with_past" not in p.name)
    joiner = next(ASR_DIR.glob("joiner*.onnx"))
    return str(encoder), str(decoder), str(joiner)


def build_asr() -> sherpa_onnx.OnlineRecognizer:
    encoder, decoder, joiner = asr_files()
    return sherpa_onnx.OnlineRecognizer.from_transducer(
        tokens=str(ASR_DIR / "tokens.txt"),
        encoder=encoder,
        decoder=decoder,
        joiner=joiner,
        num_threads=2,
        sample_rate=16000,
        feature_dim=80,
        decoding_method="greedy_search",
        provider="cpu",
    )


def build_tts(language: str) -> sherpa_onnx.OfflineTts:
    model_dir = TTS_DIRS[language]
    model = next(model_dir.glob("*.onnx"))
    data_dir = model_dir / "espeak-ng-data"
    lexicon = model_dir / "lexicon.txt"
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=str(model),
        tokens=str(model_dir / "tokens.txt"),
        lexicon=str(lexicon) if lexicon.is_file() else "",
        data_dir=str(data_dir) if data_dir.is_dir() else "",
    )
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=2, provider="cpu"),
        max_num_sentences=1,
    )
    return sherpa_onnx.OfflineTts(config)


def write_wav(path: Path, samples: list[float], sample_rate: int) -> None:
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(sample_rate)
        handle.writeframes(
            b"".join(int(max(-1.0, min(1.0, s)) * 32767).to_bytes(2, "little", signed=True) for s in samples)
        )


def read_wav(path: Path) -> tuple[list[float], int]:
    with wave.open(str(path), "rb") as handle:
        rate = handle.getframerate()
        frames = handle.readframes(handle.getnframes())
    values = [
        int.from_bytes(frames[i : i + 2], "little", signed=True) / 32768.0
        for i in range(0, len(frames), 2)
    ]
    return values, rate


def transcribe(recognizer, samples: list[float], rate: int, language: str) -> str:
    stream = recognizer.create_stream()
    try:
        stream.set_option("language", language)
    except Exception as error:  # older bindings
        print(f"  (set_option(language) dilewati: {error})")
    stream.accept_waveform(rate, samples)
    stream.input_finished()
    while recognizer.is_ready(stream):
        recognizer.decode_stream(stream)
    return recognizer.get_result(stream).strip()


def main() -> int:
    recognizer = build_asr()
    failures = 0
    for language, sentences in SAMPLES.items():
        tts = build_tts(language)
        print(f"\n== {language} ==")
        for sentence in sentences:
            audio = tts.generate(sentence, sid=0, speed=1.0)
            wav = Path("/tmp") / f"roundtrip-{language}.wav"
            write_wav(wav, audio.samples, audio.sample_rate)
            samples, rate = read_wav(wav)
            text = transcribe(recognizer, samples, rate, language)
            duration = len(samples) / rate
            print(f"  TTS ({duration:.2f}s): {sentence}")
            print(f"  ASR: {text}")
            if not text:
                failures += 1
                print("  !! transkripsi kosong")
    print(f"\nselesai, {failures} kegagalan")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
