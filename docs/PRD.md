# Product Requirements Document (PRD) — OffPix

> **Version:** 1.0 (v1 Free Demand-Validation Release)  
> **App Name / Package:** OffPix (`com.offpix.app`)  
> **Platform:** Android (`minSdk = 26`, `targetSdk = 35`)  
> **UI Reference:** [prototype/index.html](../prototype/index.html)

---

## 1. Product Vision & Core Principles

**OffPix** is an all-in-one, **100% offline** Android photo enhancement studio delivering flagship-grade **4x HD upscaling, motion unblur, face restoration, and tap-to-select object removal** entirely on-device.

### v1 Non-Negotiable Principles
- **100% Free:** Every feature unlocked out of the box. Zero paywalls, zero subscriptions, zero trial limits.
- **100% Offline Once Installed:** Works in airplane mode from first launch. Zero cloud uploads, zero "No internet connection" errors.
- **Zero Tracking / Zero Friction:** No sign-in/accounts, no ads, **no analytics or telemetry SDKs**, no watermarks.
- **Commercial-Safe Open Source:** Every bundled model uses a permissive commercial-safe license (BSD-3-Clause, MIT, Apache 2.0) so future monetization (e.g., optional one-time Pro Pack) is legally clean.
- **Sub-500 MB Total Footprint:** Target **~285–350 MB** installed size (base app + all 6 AI models).

---

## 2. Target Audience

1. **Everyday Android Users:** Rescuing blurry camera-shake shots, zoomed-in photos, old scans, and heavily compressed WhatsApp/Instagram forwards.
2. **Travelers & Casual Creators:** Removing photobombers, trash cans, and power lines in one tap without paying weekly subscriptions.
3. **Privacy-Conscious Users:** Parents, students, and professionals who refuse to upload family photos or personal documents to cloud servers.

---

## 3. Functional Requirements (All 6 Screens)

### 3.1 Screen 1 — Home / Photo Picker
- Hero CTA **"Pick a Photo from Device"** launching the system Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) with zero broad storage permissions.
- **"100% On-Device • Zero Cloud • Zero Ads"** status badge.
- Quick-launch cards for the 3 core tools (**4x HD Upscale**, **Unblur & Face Rescue**, **Magic Eraser**).
- **Recent Rescues** horizontal strip (local app-private thumbnails of recently saved edits, clearable by user).
- Top-bar button opening the **On-Device Engine & Licenses** sheet.

### 3.2 Screen 2 — Tab 1: 4x HD Upscale (Real-ESRGAN x4v3)
- Scale selector: **2x Fast HD** and **4x Ultra HD** (default).
- **Detail Sharpness / Blend** slider (blends enhanced output with original bicubic upscale for natural control).
- Live tile-by-tile progress HUD with **Cancel** support.
- **Interactive Before / After Swipe Slider** (touch drag across canvas) + Reset/Undo button.
- Automatic resolution guardrail based on device RAM (e.g., cap input longest edge at 1024 px on 4–6 GB devices so output stays ≤ 4096 px and never OOMs).

### 3.3 Screen 3 — Tab 2: Unblur & Face Rescue (NAFNet + YuNet + GFPGAN)
- Mode toggle:
  - **Quick Fix (NAFNet):** Whole-image camera-shake and motion deblurring.
  - **Portrait Restore (YuNet + GFPGAN):** Automatically detects faces, displays detected face box + 5-point alignment preview briefly, restores eyes/skin/hair detail, and blends smoothly back into the original photo.
- **Restoration Strength** slider (blends restored face/image with original to prevent over-smoothing).
- Live Before / After swipe slider.
- Graceful message if no face is detected in Portrait Restore mode (suggests switching to Quick Fix).

### 3.4 Screen 4 — Tab 3: Magic Eraser (SAM 2.1 + MI-GAN)
- Runs one-time SAM 2.1 image encoding when entering the Eraser tab for a photo (~180–580 ms, shown with subtle progress pill).
- Two selection tools:
  - **Tap Object (SAM 2.1):** Tapping anywhere highlights the object mask in ~10–30 ms. Multiple taps can refine/add selections.
  - **Brush Mask:** Freehand touch drawing on Jetpack `Canvas` with adjustable **Brush Size** slider (`12–54 px`).
- **Clear Mask** button and **Erase Selection (MI-GAN)** primary CTA.
- Supports **iterative erasing** (user can erase a photobomber, then tap a trash can and erase again on the updated image) + Undo/Reset.

### 3.5 Screen 5 — Save & Export Bottom Sheet
- Displays exact output dimensions (`W × H px`) and **"0 Watermarks"** badge.
- Format selector: **JPEG 95%** (default) or **PNG Lossless**.
- **Preserve EXIF Metadata** toggle (default ON — copies original date, camera model, and orientation-corrected metadata).
- Saves directly to `Pictures/PocketPhotoRescue/` via `MediaStore` with zero storage permission prompts on Android 10+.

### 3.6 Screen 6 — On-Device Engine & Open-Source Licenses Sheet
- Displays active LiteRT hardware backend (`NPU`, `GPU (ML Drift)`, or `CPU (XNNPACK)`), offline storage footprint, and **"0 KB Sent"** privacy guarantee.
- Lists all 6 bundled AI models with their exact open-source licenses (BSD-3-Clause, MIT, Apache 2.0) and attribution notices.

---

## 4. Non-Functional Requirements

| Category | Target Requirement |
| :--- | :--- |
| **Total Installed Size** | **< 500 MB** (target ~285–350 MB via quantized GFPGAN v1.4) |
| **Offline Availability** | 100% of features work in Airplane Mode immediately after Play Store install (`install-time` asset pack) |
| **Network Permissions** | Zero `INTERNET` permission required in v1 |
| **RAM Stability** | Zero OOM crashes on 4 GB RAM Android devices (lazy-load 1 model group per tab; explicit `CompiledModel.close()` on tab switch) |
| **UI Responsiveness** | Zero ANRs; all bitmap decoding, tiling, and LiteRT inference off the Main thread with coroutine cancellation |
| **Device Compatibility** | Android 8.0+ (`minSdk = 26`), universal hardware fallback (`NPU → GPU → CPU`) across Snapdragon, MediaTek, Exynos, and Google Tensor |
| **Play Store Data Safety** | **"No data collected, no data shared"** |

---

## 5. Explicit v1 Non-Goals (Out of Scope)
- User accounts, cloud sync, or server APIs
- Ads, paywalls, or in-app purchases
- Analytics, crash-reporting network SDKs, or telemetry
- Batch folder processing or video upscaling (candidate v2 features if demand validates)
