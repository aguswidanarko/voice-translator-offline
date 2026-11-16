#!/usr/bin/env python3
"""Export OPUS-MT (Marian) models to int8 ONNX + compact tokenizer data.

Produces, per model dir:
  encoder.int8.onnx, decoder.int8.onnx, decoder_with_past.int8.onnx
  source_vocab.tsv   piece \t id \t score      (unigram segmentation)
  target_vocab.tsv   id \t piece               (detokenization)
  model.json         config + file hashes

Run:  python tools/export_mt.py --out models-out/mt
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys

MODELS = [
    "Helsinki-NLP/opus-mt-id-en",
    "Helsinki-NLP/opus-mt-en-id",
    "Helsinki-NLP/opus-mt-en-zh",
    "Helsinki-NLP/opus-mt-zh-en",
]

QUANTIZE = """
import sys, onnx, os
from onnxruntime.quantization import quantize_dynamic, QuantType
src, dst = sys.argv[1], sys.argv[2]
quantize_dynamic(src, dst, weight_type=QuantType.QInt8)
"""


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_vocab(export_dir: str) -> dict[str, int]:
    with open(os.path.join(export_dir, "vocab.json"), encoding="utf-8") as f:
        return json.load(f)


def extract_tokenizer(export_dir: str, vocab: dict[str, int], out_dir: str) -> None:
    from sentencepiece import sentencepiece_model_pb2 as mp

    for name, which in (("source_vocab.tsv", "source.spm"), ("target_vocab.tsv", "target.spm")):
        proto = mp.ModelProto()
        proto.ParseFromString(open(os.path.join(export_dir, which), "rb").read())
        with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
            for p in proto.pieces:
                piece, score = p.piece, p.score
                if which == "source.spm":
                    # id must come from vocab.json: SPM ids differ from the model's embedding ids
                    if piece in vocab:
                        f.write(f"{piece}\t{vocab[piece]}\t{score:.6f}\n")
                else:
                    f.write(f"{vocab.get(piece, -1)}\t{piece}\n")

    with open(os.path.join(out_dir, "target_vocab.tsv"), encoding="utf-8") as f:
        rows = [line.rstrip("\n").split("\t") for line in f if line.strip()]
    rows = [r for r in rows if r[0] != "-1"]
    rows.sort(key=lambda r: int(r[0]))
    with open(os.path.join(out_dir, "target_vocab.tsv"), "w", encoding="utf-8") as f:
        for i, piece in rows:
            f.write(f"{i}\t{piece}\n")


def export_one(repo: str, out_root: str) -> dict:
    name = repo.split("/")[-1]
    out_dir = os.path.join(out_root, name)
    os.makedirs(out_dir, exist_ok=True)
    raw = os.path.join(out_dir, "_raw")
    if not os.path.isdir(raw):
        r = subprocess.run([shutil.which("optimum-cli") or "optimum-cli", "export", "onnx",
                            "--model", repo, "--task", "text2text-generation-with-past",
                            "--opset", "17", "--no-post-process", raw])
        if r.returncode != 0:
            raise SystemExit(f"export failed: {repo}")

    cfg = json.load(open(os.path.join(raw, "config.json"), encoding="utf-8"))
    vocab = load_vocab(raw)

    files = {}
    for src_name, dst_name in (
        ("encoder_model.onnx", "encoder.int8.onnx"),
        ("decoder_model.onnx", "decoder.int8.onnx"),
        ("decoder_with_past_model.onnx", "decoder_with_past.int8.onnx"),
    ):
        dst = os.path.join(out_dir, dst_name)
        subprocess.run([sys.executable, "-c", QUANTIZE, os.path.join(raw, src_name), dst], check=True)
        files[dst_name] = {"size": os.path.getsize(dst), "sha256": sha256(dst)}

    extract_tokenizer(raw, vocab, out_dir)

    heads = cfg["decoder_attention_heads"]
    meta = {
        "id": name,
        "source": name.split("-")[2],
        "target": name.split("-")[3],
        "bosTokenId": cfg["decoder_start_token_id"],
        "eosTokenId": cfg["eos_token_id"],
        "padTokenId": cfg["pad_token_id"],
        "decoderLayers": cfg["decoder_layers"],
        "attentionHeads": heads,
        "dModel": cfg["d_model"],
        "headDim": cfg["d_model"] // heads,
        "maxTargetLength": 128,
        "files": files,
        "sourceVocab": {"name": "source_vocab.tsv", "size": os.path.getsize(os.path.join(out_dir, "source_vocab.tsv"))},
        "targetVocab": {"name": "target_vocab.tsv", "size": os.path.getsize(os.path.join(out_dir, "target_vocab.tsv"))},
    }
    with open(os.path.join(out_dir, "model.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    shutil.rmtree(raw, ignore_errors=True)
    return meta


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="models-out/mt")
    ap.add_argument("--models", nargs="*", default=MODELS)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    entries = []
    for repo in args.models:
        print(f"== {repo}", flush=True)
        entries.append(export_one(repo, args.out))

    with open(os.path.join(args.out, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"formatVersion": 1, "models": entries}, f, indent=2)

    total = 0
    for e in entries:
        size = sum(v["size"] for v in e["files"].values())
        total += size
        print(f"{e['id']}: {size / 1e6:.1f} MB")
    print(f"TOTAL: {total / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
