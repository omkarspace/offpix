#!/usr/bin/env python3
"""
Phase 0: Download all commercial-safe LiteRT (.tflite) models for Pocket Photo Rescue
into pack_models/src/main/assets/models/.

Usage:
    python scripts/fetch_models.py                  # Download all 5 ready-made models (~155.4 MB)
    python scripts/fetch_models.py --include-gfpgan # Also download pre-built GFPGAN FP16 (431 MB)
"""
from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = ROOT / "pack_models" / "src" / "main" / "assets" / "models"

HF_REPOS = [
    {
        "name": "Real-ESRGAN General x4v3 (4x Upscaler)",
        "repo": "litert-community/real-esrgan-x4v3-litert",
        "license": "BSD-3-Clause",
        "preferred_ext": ".tflite",
        "target_name": "realesrgan_x4v3_fp16.tflite",
        "optional": False,
    },
    {
        "name": "NAFNet-GoPro-width32 (Motion Unblur)",
        "repo": "litert-community/NAFNet-GoPro-width32-LiteRT",
        "license": "MIT",
        "preferred_ext": ".tflite",
        "target_name": "nafnet_fp16.tflite",
        "optional": False,
    },
    {
        "name": "YuNet Face Detector (5-Point Landmarks)",
        "repo": "litert-community/YuNet-Face-LiteRT",
        "license": "MIT",
        "preferred_ext": ".tflite",
        "target_name": "yunet_fp16.tflite",
        "optional": False,
    },
    {
        "name": "SAM 2.1 Hiera-Tiny Image Encoder",
        "repo": "litert-community/SAM2.1-Hiera-Tiny-Image-Encoder",
        "license": "Apache-2.0",
        "preferred_ext": ".tflite",
        "target_name": "sam2_tiny_image_encoder_v2_fp16.tflite",
        "optional": False,
    },
    {
        "name": "SAM 2.1 Hiera-Tiny Mask Decoder",
        "repo": "litert-community/SAM2.1-Hiera-Tiny-Mask-Decoder",
        "license": "Apache-2.0",
        "preferred_ext": ".tflite",
        "target_name": "sam2_tiny_mask_decoder_v2_fp16.tflite",
        "optional": False,
    },
    {
        "name": "MI-GAN 512 Places2 (Object Inpainting)",
        "repo": "litert-community/MI-GAN-512-Places2-LiteRT",
        "license": "MIT",
        "preferred_ext": ".tflite",
        "target_name": "migan_512_fp16.tflite",
        "optional": False,
    },
    {
        "name": "GFPGAN v1.4 Face Restore (Pre-built 431 MB — quantize to <=175 MB for release)",
        "repo": "litert-community/GFPGAN-v1.4-LiteRT",
        "license": "Apache-2.0",
        "preferred_ext": ".tflite",
        "match_substring": "gfpgan",
        "target_name": "gfpgan_v14_fp16.tflite",
        "optional": True,
    },
]


def list_repo_files(repo_id: str) -> list[str]:
    url = f"https://huggingface.co/api/models/{repo_id}"
    req = urllib.request.Request(url, headers={"User-Agent": "PocketPhotoRescue-Fetcher/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return [s["rfilename"] for s in data.get("siblings", [])]


def pick_tflite_file(files: list[str], match_substring: str | None = None) -> str | None:
    tflites = [f for f in files if f.lower().endswith(".tflite")]
    if match_substring:
        filtered = [f for f in tflites if match_substring.lower() in f.lower()]
        if filtered:
            tflites = filtered
    # Prefer fp16 variants if multiple exist
    fp16 = [f for f in tflites if "fp16" in f.lower() or "f16" in f.lower()]
    if fp16:
        return fp16[0]
    return tflites[0] if tflites else None


def download_file(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "PocketPhotoRescue-Fetcher/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as out:
        total = int(resp.headers.get("Content-Length", "0"))
        downloaded = 0
        while True:
            chunk = resp.read(1024 * 256)
            if not chunk:
                break
            out.write(chunk)
            downloaded += len(chunk)
            if total > 0:
                pct = downloaded * 100.0 / total
                sys.stdout.write(f"\r    {downloaded / (1024 * 1024):6.1f} / {total / (1024 * 1024):6.1f} MB ({pct:5.1f}%)")
                sys.stdout.flush()
    print()
    tmp.replace(dest)


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch Pocket Photo Rescue LiteRT models")
    parser.add_argument("--include-gfpgan", action="store_true", help="Also download 431 MB pre-built GFPGAN FP16")
    parser.add_argument("--force", action="store_true", help="Re-download existing files")
    args = parser.parse_args()

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    total_bytes = 0

    for entry in HF_REPOS:
        if entry["optional"] and not args.include_gfpgan:
            print(f"[SKIP] {entry['name']} (pass --include-gfpgan to download)")
            continue

        dest = MODELS_DIR / entry["target_name"]
        if dest.exists() and not args.force:
            mb = dest.stat().st_size / (1024 * 1024)
            total_bytes += dest.stat().st_size
            print(f"[OK]   {entry['target_name']} already present ({mb:.1f} MB)")
            continue

        print(f"[INFO] Querying {entry['repo']} ({entry['license']})...")
        files = list_repo_files(entry["repo"])
        chosen = pick_tflite_file(files, entry.get("match_substring"))
        if not chosen:
            print(f"[WARN] No .tflite file found in {entry['repo']}. Files: {files}")
            continue

        url = f"https://huggingface.co/{entry['repo']}/resolve/main/{chosen}"
        print(f"[GET]  {chosen} -> {dest.relative_to(ROOT)}")
        download_file(url, dest)
        total_bytes += dest.stat().st_size

    print(f"\nTotal downloaded models size: {total_bytes / (1024 * 1024):.1f} MB")
    print(f"Models directory: {MODELS_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
