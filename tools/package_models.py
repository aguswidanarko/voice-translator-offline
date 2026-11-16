#!/usr/bin/env python3
"""Zip the exported models, compute checksums, and refresh the app's model catalog.

Usage:
    python3 tools/package_models.py --base-url https://github.com/ORG/REPO/releases/download/models-v1

Each catalog entry is written to release/<archive>. The catalog embedded in
app/src/main/assets/model_catalog.json is updated with real sizes and sha256 values.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MT_DIR = ROOT / "models-out" / "mt"
SPEECH_DIR = ROOT / "models-out" / "speech"
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "model_catalog.json"
RELEASE = ROOT / "release"


def resolve_source(package_id: str) -> Path:
    if package_id.startswith("mt-"):
        path = MT_DIR / f"opus-mt-{package_id[3:]}"
        if not path.is_dir():
            raise SystemExit(f"model sumber tidak ditemukan: {path}")
        return path

    if package_id == "asr-multilingual":
        matches = sorted(SPEECH_DIR.glob("sherpa-onnx-streaming-zipformer-*"))
        if not matches:
            raise SystemExit(f"model ASR tidak ditemukan di {SPEECH_DIR}")
        return matches[0]

    if package_id == "tts-id":
        matches = sorted(SPEECH_DIR.glob("vits-piper-id*"))
        if not matches:
            raise SystemExit(f"model TTS Indonesia tidak ditemukan di {SPEECH_DIR}")
        return matches[0]

    if package_id == "tts-zh":
        matches = sorted(SPEECH_DIR.glob("vits-piper-zh*"))
        if not matches:
            raise SystemExit(f"model TTS Mandarin tidak ditemukan di {SPEECH_DIR}")
        return matches[0]

    raise SystemExit(f"paket tidak dikenal: {package_id}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def zip_tree(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".tmp")
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        for path in sorted(source.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(source).as_posix())
    tmp.replace(target)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=None, help="URL folder tempat arsip model diunggah")
    parser.add_argument("--only", nargs="*", help="hanya paket tertentu")
    parser.add_argument("--skip-existing", action="store_true")
    args = parser.parse_args()

    catalog = json.loads(CATALOG.read_text())
    if args.base_url is not None:
        catalog["baseUrl"] = args.base_url.rstrip("/")

    for package in catalog["packages"]:
        if args.only and package["id"] not in args.only:
            continue
        archive = RELEASE / package["archive"]
        if args.skip_existing and archive.is_file():
            print(f"lewati (sudah ada): {archive.name}")
        else:
            source = resolve_source(package["id"])
            print(f"mengemas {package['id']} dari {source} ...")
            zip_tree(source, archive)
        package["sizeBytes"] = archive.stat().st_size
        package["sha256"] = sha256(archive)
        print(f"  -> {archive.name} {package['sizeBytes'] / 1e6:.1f} MB")

    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
    total = sum(p["sizeBytes"] for p in catalog["packages"])
    print(f"katalog diperbarui: {CATALOG} (total {total / 1e6:.0f} MB)")
    if not catalog["baseUrl"]:
        print("peringatan: baseUrl kosong; isi lewat --base-url atau halaman Pengaturan aplikasi.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
