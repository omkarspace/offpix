# Implementation Roadmap & Verification Plan

> **Goal:** Ship a rock-solid, < 500 MB, 100% offline Android v1 of **OffPix** (`com.offpix.app`) matching the approved [prototype/index.html](../prototype/index.html).

---

## Phase 0 — Model Acquisition, Tensor Inspection & GFPGAN Quantization
- [ ] Write `scripts/fetch_models.py` to download the 5 ready-made `litert-community` `.tflite` files (Real-ESRGAN, NAFNet, YuNet, SAM 2.1 Enc/Dec, MI-GAN) into `pack_models/src/main/assets/models/`.
- [ ] Inspect SAM 2.1 `litert-community/sam2-1-tiny-click-to-segment` prompt encoder & FPN projection weights and document exact tensor ops.
- [ ] Write `scripts/inspect_models.py` to verify exact tensor names, shapes, and dtypes for every `.tflite` file.
- [ ] Export & benchmark GFPGAN v1.4 Clean FP16 (~174 MB) vs. W8A16 (~110 MB) and verify face quality + full-GPU residency.
- **Exit Gate:** All 6 `.tflite` models verified locally; combined size **≤ 350 MB**.

## Phase 1 — Android Project Scaffold & Material 3 UI Shell
- [ ] Initialize Kotlin + Jetpack Compose (Material 3) Gradle project (`minSdk = 26`, `targetSdk = 35`) with `:app` and `:pack_models` (`install-time` PAD module).
- [ ] Confirm **zero `INTERNET` permission** in `AndroidManifest.xml` and zero analytics/ad SDKs in `build.gradle.kts`.
- [ ] Build all 6 screens matching [prototype/index.html](../prototype/index.html):
  - Home / Photo Picker (`PickVisualMedia` + Recent Rescues)
  - 3-Tab Editor with interactive `BeforeAfterSlider` composable & live `ProcessingHud`
  - Save & Export Sheet (`MediaStore` + `ExifInterface` preservation)
  - On-Device Engine & Open-Source Licenses Sheet
- **Exit Gate:** App compiles cleanly, installs on device/emulator, picks real photos, preserves EXIF on save, and passes UI review against the prototype.

## Phase 2 — LiteRT Engine & Tab 1: 4x HD Upscale (Real-ESRGAN)
- [ ] Implement `LiteRtRuntime` (`NPU → GPU (ML Drift) → CPU (XNNPACK)` fallback chain + single-thread dispatcher).
- [ ] Implement `TilingEngine` (`128×128` input, `16 px` overlap, raised-cosine feathering) and RAM-aware resolution cap.
- [ ] Wire `RealEsrganRunner` (NHWC input → NCHW output) to Tab 1 with `2x` / `4x HD` toggle, sharpness blend slider, and cancellation.
- **Exit Gate:** 4x upscaling runs end-to-end on device GPU with zero visible tile seams and smooth Before/After comparison.

## Phase 3 — Tab 2: Unblur & Face Rescue (NAFNet + YuNet + GFPGAN)
- [ ] Wire `NafnetRunner` (`256×256` tiled deblur with `32 px` overlap) to **Quick Fix** mode.
- [ ] Implement `YuNetDetector` (`640×640` BGR `[0, 255]`, multi-stride decode + NMS) and `FaceAligner` (Umeyama 5-point FFHQ warp + soft elliptical inverse paste-back).
- [ ] Wire quantized `GfpganRunner` (`512×512` NCHW `[-1, 1]`) to **Portrait Restore** mode with strict `close()` cleanup on tab switch.
- **Exit Gate:** Blurry portrait shows YuNet 5-point detection, restores crisp facial details without border seams, and stays within memory limits on a 4 GB RAM device.

## Phase 4 — Tab 3: Magic Eraser (SAM 2.1 + MI-GAN)
- [ ] Wire `Sam2Runner` (one-time `1024×1024` encoder + cached FPN + per-tap decoder).
- [ ] Build interactive Jetpack `Canvas` overlay supporting both **Tap-to-Select** and **Brush Mask** (with adjustable brush size + Clear + iterative erase).
- [ ] Wire `MiganInpainter` (2× context crop, mask dilation, `[mask-0.5, rgb*mask]` NCHW packing, feathered paste-back).
- **Exit Gate:** Tapping an object highlights it in < 50 ms; tapping Erase cleanly removes the object while preserving full original sharpness outside the mask.

## Phase 5 — Release Hardening, AAB Verification & Play Store Prep
- [ ] Verify Airplane Mode test across all 3 tabs from fresh install.
- [ ] Build signed `.aab` and verify `install-time` asset pack delivery with `bundletool build-apks --local-testing`.
- [ ] Complete Play Store Data Safety declaration (**No data collected, no data shared**) and Open-Source Licenses attribution check.

---

## Device Verification Matrix

| Device Tier | Example Hardware | Key Test Focus |
| :--- | :--- | :--- |
| **Budget (4 GB RAM)** | Redmi / Samsung A-series (MediaTek Mali / Snapdragon 6) | Zero OOM on GFPGAN + 4x Upscale; OpenGL ES / XNNPACK fallback |
| **Mid-Range (8 GB RAM)** | Google Pixel 7a / 8a (Tensor Mali) | ML Drift GPU speed, SAM 2.1 tap latency, thermal stability |
| **Flagship (12+ GB RAM)** | Galaxy S25/S26 / Pixel 9/10 (Snapdragon 8 / Tensor) | NPU/GPU acceleration speed & high-res 4x output quality |
