#!/usr/bin/env python3
"""
Phase 0: Quantize GFPGAN v1.4 (Apache 2.0) to hit OffPix's < 500 MB total app budget.

Why:
  The pre-built litert-community/GFPGAN-v1.4-LiteRT file is 431 MB (and ~1.3 GB peak RAM),
  which pushes the full 6-model bundle to ~606 MB and risks OOM on 4 GB budget Android phones.
  Original GFPGANv1.4.pth is 348 MB FP32 -> Clean FP16 is ~174 MB, and W8A16 (INT8 weights)
  is ~95-115 MB, bringing OffPix's total installed footprint down to ~285-350 MB.

Requirements (run in a clean Python 3.10/3.11 venv):
    pip install torch torchvision gfpgan ai-edge-torch ai-edge-litert pillow numpy

Usage:
    python scripts/quantize_gfpgan.py --weights GFPGANv1.4.pth --profile fp16
    python scripts/quantize_gfpgan.py --weights GFPGANv1.4.pth --profile w8a16
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "pack_models" / "src" / "main" / "assets" / "models"


def export_gfpgan(weights_path: Path, profile: str) -> Path:
    import torch
    import ai_edge_torch  # type: ignore
    from gfpgan.archs.gfpganv1_clean_arch import GFPGANv1Clean  # type: ignore

    print(f"[1/4] Loading GFPGAN v1.4 Clean architecture from {weights_path}...")
    model = GFPGANv1Clean(
        out_size=512,
        num_style_feat=512,
        channel_multiplier=2,
        decoder_load_path=None,
        fix_decoder=False,
        num_mlp=8,
        input_is_latent=True,
        different_w=True,
        narrow=1,
        sft_half=True,
    )
    ckpt = torch.load(str(weights_path), map_location="cpu")
    state = ckpt.get("params_ema", ckpt.get("params", ckpt))
    model.load_state_dict(state, strict=True)
    model.eval()

    class GfpganWrapper(torch.nn.Module):
        def __init__(self, core: torch.nn.Module):
            super().__init__()
            self.core = core

        def forward(self, x: torch.Tensor) -> torch.Tensor:
            out, _ = self.core(x, return_rgb=False)
            return out

    wrapped = GfpganWrapper(model).eval()
    sample_input = (torch.randn(1, 3, 512, 512),)

    print(f"[2/4] Converting to LiteRT ({profile})...")
    if profile == "fp16":
        tfl_flags = {"_experimental_lower_tensor_list_ops": True}
        edge_model = ai_edge_torch.convert(
            wrapped,
            sample_input,
            _ai_edge_converter_flags={
                "optimizations": ["DEFAULT"],
                "target_spec.supported_types": ["float16"],
                **tfl_flags,
            },
        )
    elif profile == "w8a16":
        from ai_edge_torch.quantize import pt2e_quantizer, quant_config  # type: ignore
        q = pt2e_quantizer.PT2EQuantizer().set_global(
            pt2e_quantizer.get_symmetric_quantization_config(is_dynamic=True)
        )
        edge_model = ai_edge_torch.convert(
            wrapped,
            sample_input,
            quant_config=quant_config.QuantConfig(pt2e_quantizer=q),
        )
    else:
        raise ValueError(f"Unsupported profile: {profile}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_file = OUT_DIR / "gfpgan_v14_opt.tflite"
    print(f"[3/4] Exporting -> {out_file}...")
    edge_model.export(str(out_file))

    size_mb = out_file.stat().st_size / (1024 * 1024)
    print(f"[4/4] Done! Output size: {size_mb:.1f} MB (target <= 175 MB)")
    if size_mb > 200:
        print("[WARN] Model exceeded 200 MB; check StyleGAN2 weight folding or try --profile w8a16.")
    return out_file


def main() -> int:
    parser = argparse.ArgumentParser(description="Quantize GFPGAN v1.4 for OffPix (<500 MB budget)")
    parser.add_argument("--weights", type=Path, required=True, help="Path to GFPGANv1.4.pth (Apache 2.0)")
    parser.add_argument("--profile", choices=["fp16", "w8a16"], default="fp16")
    args = parser.parse_args()

    if not args.weights.exists():
        print(f"ERROR: Weights not found at {args.weights}")
        print("Download official Apache 2.0 GFPGANv1.4.pth from https://github.com/TencentARC/GFPGAN/releases")
        return 1

    export_gfpgan(args.weights, args.profile)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
