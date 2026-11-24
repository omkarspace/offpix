#!/usr/bin/env python3
"""
Phase 0: Inspect exact input/output tensor names, shapes, and data types for all
downloaded .tflite models in pack_models/src/main/assets/models/.

Requires either `ai-edge-litert` or `tensorflow` / `tflite-runtime`:
    pip install ai-edge-litert
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = ROOT / "pack_models" / "src" / "main" / "assets" / "models"


def load_interpreter(model_path: Path):
    try:
        from ai_edge_litert.interpreter import Interpreter  # type: ignore
        return Interpreter(model_path=str(model_path))
    except ImportError:
        pass
    try:
        from tflite_runtime.interpreter import Interpreter  # type: ignore
        return Interpreter(model_path=str(model_path))
    except ImportError:
        pass
    try:
        import tensorflow as tf  # type: ignore
        return tf.lite.Interpreter(model_path=str(model_path))
    except ImportError:
        print("ERROR: Install `ai-edge-litert` (recommended) or `tflite-runtime` to inspect .tflite tensors.")
        sys.exit(1)


def main() -> int:
    if not MODELS_DIR.exists():
        print(f"Models directory does not exist yet: {MODELS_DIR}")
        print("Run `python scripts/fetch_models.py` first.")
        return 1

    models = sorted(MODELS_DIR.glob("*.tflite"))
    if not models:
        print(f"No .tflite files found in {MODELS_DIR}.")
        return 1

    total_mb = 0.0
    for path in models:
        mb = path.stat().st_size / (1024 * 1024)
        total_mb += mb
        print(f"\n=== {path.name} ({mb:.2f} MB) ===")
        interp = load_interpreter(path)
        print("  Inputs:")
        for idx, t in enumerate(interp.get_input_details()):
            print(f"    [{idx}] name={t['name']!r} shape={list(t['shape'])} dtype={t['dtype'].__name__}")
        print("  Outputs:")
        for idx, t in enumerate(interp.get_output_details()):
            print(f"    [{idx}] name={t['name']!r} shape={list(t['shape'])} dtype={t['dtype'].__name__}")

    print(f"\nTotal size across {len(models)} models: {total_mb:.2f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
