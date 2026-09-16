# Candle Move Tracker (Native Android App)

**Package Name:** `com.candlemovetracker.app`  
**Language:** Kotlin  
**Minimum SDK:** 26 (Android 8.0)  
**Target SDK:** 34 (Android 14)  
**Build System:** Gradle (Kotlin DSL `kts`)

---

## 📌 Architecture & Design Overview

Candle Move Tracker is a dedicated **Visual Candle Movement Counter** for Android. It operates without OCR, without price label parsing, and without any external broker or web API connection.

### How it works:
1. **MediaProjection Screen Capture:**  
   Uses Android's `MediaProjectionManager` and `VirtualDisplay` to stream screen frames to an `ImageReader` in a background Foreground Service (`MediaProjectionService`).
2. **Visual Candle Detector (`VisualCandleDetector`):**  
   Crops the screen frame to the user's calibrated chart region. It identifies the rightmost active candle by detecting its color signature (bullish green vs. bearish red) and wicks. It dynamically estimates the vertical close position $Y_{\text{close}}$.
3. **Movement Counting Rule:**  
   - Every visible upward tick ($Y_{\text{close}} < Y_{\text{prev}}$ by $\ge 1\text{px}$) increments **UP by exactly 1**.
   - Every visible downward tick ($Y_{\text{close}} > Y_{\text{prev}}$ by $\ge 1\text{px}$) increments **DOWN by exactly 1**.
   - Consecutive UP movements count separately (e.g., UP -> UP -> UP -> DOWN -> UP = UP: 4, DOWN: 1).
   - Consecutive DOWN movements count separately.
   - Tiny pullbacks count.
   - Movement magnitude does not matter (1 movement = 1 count, never scaled).
   - Zero debounce to preserve real visual micro-ticks.
4. **1-Minute Cycle & History Storage:**  
   A background countdown timer tracks the 60-second lifecycle of each candle. At candle close, it records the final UP and DOWN counts, determines the dominant side, stores the candle into local persistent history (`CandleHistoryRepository`), and resets counters for the next candle.
5. **Floating Draggable Overlay (`FloatingOverlayService`):**  
   Uses Android `WindowManager` with `TYPE_APPLICATION_OVERLAY`. Allows traders to keep a compact, draggable HUD showing live UP, DOWN, and TIME counters right above their mobile browser or charting app.
6. **Calibration Mode (`CalibrationActivity` & `CalibrationOverlayView`):**  
   An interactive visual calibration tool that allows the user to drag 4 corner handles to frame the exact candlestick chart area. Saved in `PreferencesManager`.

---

## 🚀 How to Build & Run in Android Studio

1. **Open in Android Studio:**
   - Launch Android Studio (Giraffe, Hedgehog, Iguana, Jellyfish, or newer).
   - Select **Open...** and pick the `android/` directory (or the root directory).
   - Allow Gradle Sync to finish.

2. **Build Debug APK via Command Line:**
   ```bash
   cd android
   ./gradlew assembleDebug
   ```
   The resulting APK will be located at:
   `android/app/build/outputs/apk/debug/app-debug.apk`

3. **Install on Device or Emulator:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Required Permissions:**
   - **Display Over Other Apps:** The app prompts for `SYSTEM_ALERT_WINDOW` permission to display the floating HUD.
   - **Screen Capture:** On tapping "Start Analyzer", Android prompts for MediaProjection screen capture consent.
   - **Notifications:** Android 13+ requires notification permission for the foreground service.

---

## ⚠️ Accuracy & Limitations Disclaimer

- The app processes rendered screen frames only. It cannot detect price movements that the website/chart never renders in a captured frame.
- It does not read price numbers, does not use OCR, and does not connect to any exchange API.
- This is strictly a graphical movement counting utility. It is not financial advice, does not generate trading signals, and does not guarantee profit.
