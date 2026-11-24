# AI Model Pipeline & Quantization Specification

> **Total Target Model Footprint:** ~285–330 MB (all 6 models, 100% commercial-safe, 100% LiteRT GPU/NPU/CPU)

---

## 1. Master Model Inventory & Tensor I/O Contracts

| # | Feature | Model / Source Repo | Target File & Size | License | Input Tensor | Output Tensor |
| :-: | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **4x Upscale** | [`litert-community/real-esrgan-x4v3-litert`](https://huggingface.co/litert-community/real-esrgan-x4v3-litert) | `realesrgan_x4v3_fp16.tflite` (**3.5 MB**) | BSD-3-Clause | `[1, 128, 128, 3]` **NHWC**, RGB `[0, 1]` | `[1, 3, 512, 512]` **NCHW**, RGB clamped `[0, 1]` |
| 2 | **Motion Unblur** | [`litert-community/NAFNet-GoPro-width32-LiteRT`](https://huggingface.co/litert-community/NAFNet-GoPro-width32-LiteRT) | `nafnet_fp16.tflite` (**38.3 MB**) | MIT | `[1, 3, 256, 256]` **NCHW**, RGB `[0, 1]` | `[1, 3, 256, 256]` **NCHW**, RGB `[0, 1]` |
| 3 | **Face Detector** | [`litert-community/YuNet-Face-LiteRT`](https://huggingface.co/litert-community/YuNet-Face-LiteRT) | `yunet_fp16.tflite` (**0.3 MB**) | MIT | `[1, 3, 640, 640]` (or NHWC per file), letterboxed **BGR** `[0, 255]` | Strides 8/16/32: scores, boxes, **5 landmarks** |
| 4 | **Face Restore** | [`TencentARC/GFPGAN`](https://github.com/TencentARC/GFPGAN) v1.4 (re-exported clean FP16 / W8A16) | `gfpgan_v14_opt.tflite` (**~110–175 MB**) | Apache 2.0 | `[1, 3, 512, 512]` **NCHW**, RGB `[-1, 1]`, FFHQ-aligned | `[1, 3, 512, 512]` **NCHW**, RGB `[-1, 1]` |
| 5 | **Tap-to-Select** | [`SAM2.1-Hiera-Tiny-Image-Encoder`](https://huggingface.co/litert-community/SAM2.1-Hiera-Tiny-Image-Encoder) + [`Mask-Decoder`](https://huggingface.co/litert-community/SAM2.1-Hiera-Tiny-Mask-Decoder) | `sam2_tiny_image_encoder_v2_fp16.tflite` (**80 MB**) + `sam2_tiny_mask_decoder_v2_fp16.tflite` (**17 MB**) | Apache 2.0 | Enc: `[1, 3, 1024, 1024]` NCHW ImageNet RGB<br>Dec: `image_embeddings [1,256,64,64]`, `sparse_prompt [1,2,256]`, `feat_s1 [1,64,128,128]`, `feat_s0 [1,32,256,256]` | Enc: 3 FPN maps<br>Dec: `pred_masks [1,3,256,256]`, `iou_scores [1,3]` |
| 6 | **Object Erase** | [`litert-community/MI-GAN-512-Places2-LiteRT`](https://huggingface.co/litert-community/MI-GAN-512-Places2-LiteRT) | `migan_512_fp16.tflite` (**16.3 MB**) | MIT (code + `LICENSE-WEIGHTS`) | `[1, 4, 512, 512]` **NCHW**: `concat(mask - 0.5, rgb * mask)` | `[1, 3, 512, 512]` **NCHW**, RGB `[-1, 1]` |

> **Critical Implementation Note:** Before writing Kotlin buffer packers, run a 10-line inspection script (`scripts/inspect_models.py`) against each downloaded `.tflite` file to log exact input/output tensor names, indices, shapes, and data types so zero assumptions exist.

---

## 2. Pipeline Mathematical & Image Processing Specifications

### 2.1 Real-ESRGAN 4x Overlapped Tiling
- **Tile Input:** `128 × 128` px, **Overlap:** `16 px`, **Stride:** `112 px`.
- **Tile Output:** `512 × 512` px, **Output Overlap:** `64 px`, **Output Stride:** `448 px`.
- **Edge Handling:** Mirror/clamp-pad right & bottom border tiles to `128 × 128`, then crop output back to exact `4W × 4H`.
- **Feathering:** Accumulate each output tile using a separable 2D raised-cosine (or linear) weight ramp over the 64 px overlap band so tile seams are invisible on smooth gradients.
- **2x Fast Mode:** Run 4x on a 0.5× downscaled input (or downsample 4x output by 0.5× with bilinear filtering) depending on user sharpness preference.

### 2.2 NAFNet-GoPro Motion Deblur Tiling
- **Tile Input/Output:** `256 × 256` px, **Overlap:** `32 px`, **Stride:** `224 px`.
- **Normalization:** `float = pixel / 255.0f` (`[0, 1]`), NCHW layout (`R` plane, `G` plane, `B` plane).
- **Blending:** Raised-cosine feathering across the 32 px overlap band. Emit `onProgress(doneTiles / totalTiles)` after each tile for live UI feedback.

### 2.3 YuNet + GFPGAN Portrait Restore Pipeline
1. **YuNet Face Detection:**
   - Letterbox input photo to `640 × 640`, preserve scale & padding offsets, convert RGB → **BGR `[0, 255]`**.
   - Decode anchor strides `{8, 16, 32}`, apply score threshold `>= 0.60` and NMS `IoU = 0.30`.
   - Map detected bounding boxes and 5 landmarks back to original image coordinates:
     `[right_eye, left_eye, nose_tip, right_mouth, left_mouth]`.
2. **Umeyama 5-Point FFHQ Alignment (`512 × 512`):**
   - Standard FFHQ 512 reference coordinates:
     - Right eye: `(192.98138, 239.94708)`
     - Left eye: `(318.90277, 240.19360)`
     - Nose tip: `(256.63416, 314.01935)`
     - Right mouth: `(201.26117, 371.41043)`
     - Left mouth: `(313.08905, 371.15118)`
   - Solve 2D similarity transform (`scale, rotation, tx, ty`) via Umeyama closed-form least squares → warp face crop to `512 × 512` using `android.graphics.Matrix` + bilinear filter.
3. **GFPGAN Inference & Paste-Back:**
   - Normalize RGB to `[-1, 1]`: `(pixel / 127.5f) - 1.0f`, pack as NCHW `[1, 3, 512, 512]`.
   - Run GFPGAN → denormalize `(val + 1.0f) * 127.5f`, clamp `[0, 255]`.
   - Compute inverse similarity matrix → warp restored face back onto full photo using a **feathered elliptical/eroded alpha mask** (32 px soft edge) so jawlines and hair blend seamlessly.
   - Apply user's **Restoration Strength** slider via `alpha = strength`.

### 2.4 SAM 2.1 + MI-GAN Magic Eraser Pipeline
1. **SAM 2.1 One-Time Encode:**
   - Resize/pad photo to `1024 × 1024`, normalize with ImageNet mean `[0.485, 0.456, 0.406]` and std `[0.229, 0.224, 0.225]`, pack NCHW.
   - Run `sam2_tiny_image_encoder_v2_fp16.tflite` once per photo and retain the 3 FPN outputs in memory until the user switches photos or leaves the Eraser tab.
2. **SAM 2.1 Per-Tap Decode:**
   - Convert tap `(x, y)` in view space → `1024 × 1024` coordinate space.
   - Encode click coordinates into `sparse_prompt [1, 2, 256]` and project FPN features (`feat_s0`, `feat_s1`) using the exact weights/logic verified from `litert-community/sam2-1-tiny-click-to-segment`.
   - Run `sam2_tiny_mask_decoder_v2_fp16.tflite`, pick mask index with highest `iou_scores`, threshold `logits > 0.0f`, and merge into the user's editable mask bitmap (which also accepts freehand brush strokes).
3. **MI-GAN Crop-and-Composite Inpainting:**
   - Find tight bounding box of the mask, expand by **2× context padding** (minimum `512 × 512` when image permits, clamped to image bounds), resize crop & mask to `512 × 512`.
   - Dilate binary mask by `6 px` to cover object shadows/fringes.
   - Mask convention: **`1.0 = keep (known)`**, **`0.0 = erase (hole)`**.
   - Pack `[1, 4, 512, 512]` NCHW:
     - Ch 0: `mask - 0.5f`
     - Ch 1..3: `((rgb / 127.5f) - 1.0f) * mask`
   - Run MI-GAN → denormalize output `[-1, 1]` → `[0, 255]` → feather-composite `orig * softMask + inpainted * (1 - softMask)` back into the full-res photo so untouched regions lose zero resolution.

---

## 3. GFPGAN v1.4 Size Optimization Plan (431 MB → ~110–175 MB)

The pre-built `litert-community/GFPGAN-v1.4-LiteRT` file is **431 MB** and peaks at **1,309 MB RAM**, pushing the total app over 500 MB and risking OOM on 4 GB phones.

### Quantization Steps (Phase 0)
1. Download official Apache 2.0 `GFPGANv1.4.pth` (~348 MB FP32).
2. Convert via `litert-torch` (`ai-edge-torch`) testing two precision profiles:
   - **Profile A (Clean FP16):** Expected size **~174 MB** — virtually identical quality to FP32.
   - **Profile B (W8A16 Weight-Only INT8):** Expected size **~95–115 MB** — cuts disk and RAM by ~70%.
3. **Visual QA Gate:** Compare FP32 vs. Clean FP16 vs. W8A16 side-by-side on 10 diverse blurry/low-res faces (eyes, teeth, skin texture, hair). Ship W8A16 if visually indistinguishable; otherwise ship Clean FP16 (~174 MB, still keeping total app at **~345 MB**, well under 500 MB).

---

## 4. Open-Source License Compliance Checklist

Every model's license text MUST be bundled in `app/src/main/assets/licenses/` and displayed in the **Engine & Licenses** bottom sheet:
- **Real-ESRGAN (`realesr-general-x4v3`)**: BSD-3-Clause
- **NAFNet (`NAFNet-GoPro-width32`)**: MIT
- **YuNet (`opencv_zoo`)**: MIT
- **GFPGAN v1.4 (`TencentARC/GFPGAN`)**: Apache 2.0
- **SAM 2.1 Hiera-Tiny (`Meta / litert-community`)**: Apache 2.0
- **MI-GAN 512 Places2 (`Picsart-AI-Research/MI-GAN`)**: MIT (covers both code & `LICENSE-WEIGHTS`)
