#!/usr/bin/env python3
"""
Phase 0: Quantize the GPU-clean `litert-community/GFPGAN-v1.4-LiteRT/gfpgan_fp16.tflite`
directly via `ai-edge-quantizer` (INT8 dynamic-range weights) so it stays 100% GPU-clean
while shrinking from 431 MB down to ~110-150 MB (keeping OffPix comfortably < 500 MB).

Usage:
    python scripts/quantize_gfpgan.py --input gfpgan_fp16.tflite
"""
from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "pack_models" / "src" / "main" / "assets" / "models"


def quantize_tflite_int8(input_tflite: Path, out_file: Path) -> Path:
    from ai_edge_quantizer import quantizer, recipe_manager  # type: ignore
    from ai_edge_quantizer.recipe import qtyping  # type: ignore

    in_mb = input_tflite.stat().st_size / (1024 * 1024)
    print(f"[1/3] Loading GPU-clean GFPGAN LiteRT model ({in_mb:.1f} MB): {input_tflite}")

    rmgr = recipe_manager.RecipeManager()
    rmgr.add_dynamic_config(
        regex=".*",
        operation_name=qtyping.TFLOperationName.ALL_SUPPORTED,
        num_bits=8,
        granularity=qtyping.QuantGranularity.CHANNELWISE,
    )

    out_file.parent.mkdir(parents=True, exist_ok=True)
    if out_file.exists():
        out_file.unlink()

    print("[2/3] Applying INT8 channelwise weight quantization via ai-edge-quantizer...")
    qt = quantizer.Quantizer(float_model=str(input_tflite))
    qt.load_quantization_recipe(rmgr.get_quantization_recipe())
    qt.quantize().export_model(str(out_file))

    out_mb = out_file.stat().st_size / (1024 * 1024)
    print(f"[3/3] Done! {in_mb:.1f} MB -> {out_mb:.1f} MB ({out_file})")
    return out_file


def main() -> int:
    parser = argparse.ArgumentParser(description="Quantize GPU-clean GFPGAN v1.4 .tflite to INT8 weights")
    parser.add_argument("--input", type=Path, required=True, help="Path to gfpgan_fp16.tflite")
    parser.add_argument(
        "--output",
        type=Path,
        default=OUT_DIR / "gfpgan_v14_opt.tflite",
        help="Output quantized .tflite path",
    )
    args = parser.parse_args()

    if not args.input.exists():
        print(f"ERROR: Input model not found at {args.input}")
        return 1

    quantize_tflite_int8(args.input, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
