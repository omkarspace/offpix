# OffPix

**100% Offline, On-Device AI Photo Rescue Studio for Android** — flagship 4x HD upscaling, motion unblur, face restoration, and tap-to-select magic object removal with **zero cloud uploads, zero login, zero ads, zero analytics, and zero `INTERNET` permission**.

---

## Core Features (3-Tab Studio)

1. **4x HD Upscale** — Real-ESRGAN General x4v3 (`3.5 MB`, BSD-3-Clause) with overlapped `128×128` raised-cosine tiling and live Before/After swipe comparison.
2. **Unblur & Face Rescue** —
   - *Quick Fix:* NAFNet-GoPro-width32 (`38.3 MB`, MIT) for camera-shake & motion blur.
   - *Portrait Restore:* YuNet (`0.3 MB`, MIT) 5-point FFHQ alignment + GFPGAN v1.4 (Apache 2.0).
3. **Magic Eraser** — SAM 2.1 Hiera-Tiny (`80 MB + 17 MB`, Apache 2.0) tap-to-select + Brush mask + MI-GAN 512 Places2 (`16.3 MB`, MIT) crop-and-composite inpainting.

## Architecture Highlights

- **Single Unified Runtime:** Standalone Google **LiteRT (`CompiledModel` API)** with automatic hardware priority: `NPU → GPU (ML Drift) → CPU (XNNPACK)` across Snapdragon, MediaTek, Exynos, and Google Tensor devices.
- **Zero-Network Privacy:** `android.permission.INTERNET` is explicitly removed in `AndroidManifest.xml`; models ship in an `install-time` Play Asset Delivery pack (`:pack_models`).
- **4 GB RAM Safe:** Single-thread GPU confinement, reusable `TensorBuffer`s, strict `CompiledModel.close()` on tab switch, and RAM-aware resolution caps.

## Project Structure

- `prototype/index.html` — Interactive zero-dependency HTML prototype of all 6 Material 3 screens.
- `docs/` — [PRD](docs/PRD.md), [Architecture](docs/ARCHITECTURE.md), [Model Pipeline Spec](docs/MODEL_PIPELINE_SPEC.md), [Roadmap](docs/IMPLEMENTATION_ROADMAP.md), and [Research Report](docs/research-pocket-photo-rescue.md).
- `app/` — Kotlin + Jetpack Compose (Material 3) Android app (`com.offpix.app`).
- `pack_models/` — Play Asset Delivery (`install-time`) module for `.tflite` models.
- `scripts/` — Model download (`fetch_models.py`) and tensor inspection (`inspect_models.py`).

## Quick Start

1. Open the repository in **Android Studio** (JDK 17 + Android SDK 35).
2. Download the open-source LiteRT models into `pack_models/src/main/assets/models/`:
   ```bash
   python scripts/fetch_models.py
   python scripts/inspect_models.py
   ```
3. Build and run `:app` on a physical Android device (Android 8.0+ / API 26+).
