# OffPix — Privacy Policy

**Effective Date:** 2026-11-24  
**App Package:** `com.offpix.app`

## 1. Zero Data Collection (100% On-Device)

**OffPix** is designed from the ground up to operate **100% offline on your Android device**.

- **No Photos Uploaded:** Every photo you pick, upscale, unblur, restore, or erase is processed strictly locally on your device's NPU, GPU, or CPU. Your photos never leave your phone.
- **No Internet Permission:** OffPix intentionally does **not** request the Android `INTERNET` permission. The app cannot transmit data over the network.
- **No Accounts or Sign-In:** You do not need to create an account or provide an email address, phone number, or personal details.
- **No Analytics, Ads, or Trackers:** OffPix contains zero advertising SDKs, zero analytics libraries, zero crash-reporting network beacons, and zero third-party tracking.

## 2. Device Storage & Permissions

- **Photo Selection:** OffPix uses the system **Android Photo Picker**, granting access only to the specific photo(s) you explicitly select—without requesting broad gallery read permissions.
- **Saving Edited Photos:** When you tap **Save**, OffPix writes the edited image locally to your device's `Pictures/OffPix` folder via Android `MediaStore`. On Android 8–9 (API 26–28), the app requests legacy storage write permission solely to save your exported photo.
- **Local Recent History:** OffPix stores references to your recently saved photos locally in app-private `SharedPreferences` on your device. Clearing app storage or uninstalling OffPix removes this list permanently.

## 3. Children's Privacy

Because OffPix collects zero personal data and has no network connectivity, it does not knowingly or unknowingly collect information from children under 13 (or any other age).

## 4. Contact

If you have questions about this Privacy Policy, please open an issue on the official repository:  
https://github.com/omkarspace/offpix
