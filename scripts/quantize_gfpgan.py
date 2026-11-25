#!/usr/bin/env python3
"""
Phase 0: Quantize GFPGAN v1.4 (Apache 2.0) to hit OffPix's < 500 MB total app budget.

Uses `litert-torch` + `ai-edge-quantizer` (same proven pipeline used by litert-community):
  - profile=fp16  -> FLOAT_CASTING (~174 MB)
  - profile=w8a16 -> DYNAMIC_RANGE INT8 weights (~95-115 MB)

Usage:
    python scripts/quantize_gfpgan.py --weights GFPGANv1.4.pth --profile fp16
    python scripts/quantize_gfpgan.py --weights GFPGANv1.4.pth --profile w8a16
"""
from __future__ import annotations

import argparse
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "pack_models" / "src" / "main" / "assets" / "models"


def install_torchvision_shim() -> None:
    """basicsr <= 1.4.2 imports deprecated `torchvision.transforms.functional_tensor`."""
    import torchvision.transforms.functional as tv_f  # type: ignore
    sys.modules["torchvision.transforms.functional_tensor"] = tv_f


def export_gfpgan(weights_path: Path, profile: str) -> Path:
    install_torchvision_shim()

    import torch
    import litert_torch  # type: ignore
    from ai_edge_quantizer import quantizer, recipe_manager  # type: ignore
    from ai_edge_quantizer.recipe import AlgorithmName, qtyping  # type: ignore
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
    ckpt = torch.load(str(weights_path), map_location="cpu", weights_only=False)
    state = ckpt.get("params_ema", ckpt.get("params", ckpt))
    model.load_state_dict(state, strict=True)
    model.eval()

    class GfpganWrapper(torch.nn.Module):
        def __init__(self, core: torch.nn.Module):
            super().__init__()
            self.core = core

        def forward(self, x: torch.Tensor) -> torch.Tensor:
            out, _ = self.core(x, return_rgb=False, randomize_noise=False)
            return out

    wrapped = GfpganWrapper(model).eval()
    sample_input = (torch.randn(1, 3, 512, 512),)

    fp32_path = Path(tempfile.gettempdir()) / "gfpgan_v14_fp32.tflite"
    print(f"[2/4] Converting PyTorch -> FP32 LiteRT ({fp32_path})...")
    with torch.no_grad():
        litert_torch.convert(wrapped, sample_input).export(str(fp32_path))
    fp32_mb = fp32_path.stat().st_size / (1024 * 1024)
    print(f"      FP32 size: {fp32_mb:.1f} MB")

    print(f"[3/4] Quantizing via ai-edge-quantizer (profile={profile})...")
    rmgr = recipe_manager.RecipeManager()
    if profile == "fp16":
        rmgr.add_quantization_config(
            regex=".*",
            operation_name=qtyping.TFLOperationName.ALL_SUPPORTED,
            op_config=qtyping.OpQuantizationConfig(
                weight_tensor_config=qtyping.TensorQuantizationConfig(
                    num_bits=16, dtype=qtyping.TensorDataType.FLOAT
                ),
                compute_precision=qtyping.ComputePrecision.FLOAT,
            ),
            algorithm_key=AlgorithmName.FLOAT_CASTING,
        )
    elif profile == "w8a16":
        rmgr.add_dynamic_config(
            regex=".*",
            operation_name=qtyping.TFLOperationName.ALL_SUPPORTED,
            num_bits=8,
            granularity=qtyping.QuantGranularity.CHANNELWISE,
        )
    else:
        raise ValueError(f"Unsupported profile: {profile}")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    out_file = OUT_DIR / "gfpgan_v14_opt.tflite"
    if out_file.exists():
        out_file.unlink()

    qt = quantizer.Quantizer(float_model=str(fp32_path))
    qt.load_quantization_recipe(rmgr.get_quantization_recipe())
    qt.quantize().export_model(str(out_file))

    if fp32_path.exists():
        os.remove(fp32_path)

    size_mb = out_file.stat().st_size / (1024 * 1024)
    print(f"[4/4] Done! {fp32_mb:.1f} MB -> {size_mb:.1f} MB ({out_file})")
    return out_file


def main() -> int:
    parser = argparse.ArgumentParser(description="Quantize GFPGAN v1.4 for OffPix (<500 MB budget)")
    parser.add_argument("--weights", type=Path, required=True, help="Path to GFPGANv1.4.pth (Apache 2.0)")
    parser.add_argument("--profile", choices=["fp16", "w8a16"], default="fp16")
    args = parser.parse_args()

    if not args.weights.exists():
        print(f"ERROR: Weights not found at {args.weights}")
        return 1

    export_gfpgan(args.weights, args.profile)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
