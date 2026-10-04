# Prompt Departure 🚇

A modern, high-performance London transit departure board app built for Android with **Jetpack Compose**, **Material 3 Expressive**, and **Glance AppWidgets**. Powered by the **Transport for London (TfL) Unified API**.

---

## ✨ Features

- **🔴 Live Departure Countdowns & Amber Dot-Matrix Display:**
  - Real-time arrival predictions inspired by classic London Underground platform dot-matrix LED displays with animated rolling digits.
  - Accompanied by exact London scheduled clock times (e.g. `18:02`, `18:14`) and live train track progression indicators (*Approaching*, *At Platform*, *In Transit*).
- **🚇 Complete Network Coverage & Official TfL Roundels:**
  - Seamless support for all London transit modes: **London Underground**, **Elizabeth line**, **London Overground** (including all 6 2024 named lines: Liberty, Lioness, Mildmay, Suffragette, Weaver, Windrush), **DLR**, **London Buses**, and **Trams**.
  - **Vector TfL Line Roundels:** Departure rows lead with crisp, uniform TfL roundels tinted in official line brand colors, with adaptive dark-mode contrast borders for dark lines (Northern, Piccadilly).
  - **Transport Mode Header Indicators:** Station cards feature circular transit mode badges to immediately distinguish Tube stations, Bus stops, and Rail interchanges.
  - **Smart Direction & Terminus Filtering:** Tap any line badge to filter departures; through stations cycle through cardinal directions (`→ EB`, `← WB`, etc.), while terminus stations toggle directly without redundant direction cycling.
  - Outbound destination resolving across **all 40 London terminus stations** (Wimbledon, Edgware, Mill Hill East, Brixton, Stanmore, Ealing Broadway, etc.).
- **🤖 Intelligent Hybrid Transit AI Assistant:**
  - Natural language commuter assistant powered by **On-Device Gemini Nano** (via Android ML Kit Prompt API) with seamless cloud fallback via **Firebase AI Logic (Gemini 1.5 Flash)**.
  - Resolves natural language queries, colloquial names, and station typos (e.g., *"when is the next mill hill east train from Bank"* or *"Heathrow T5 from Farringdon"*).
  - Automatically queries live TfL line statuses, disruptions, and delays with one-tap Suggested Questions tailored to the user's nearest station.
  - Built-in software keyboard (`imePadding`) and scroll support for comfortable typing.
- **⚡ Interactive Home Screen Widgets (Glance):**
  - Live London departure boards directly on your Android home screen.
  - **Per-Widget Station & Line Filtering:** Dedicate widgets to a specific station and route (e.g. show *only* the Northern line at King's Cross or bus route *221* at your local bus stop).
  - **Direct Deep-Linking:** Tapping any widget opens the app straight into that station's detailed live departures sheet.
  - One-tap interactive refresh button directly on the widget surface.
  - Automatic background synchronization managed via **WorkManager**.
- **📍 Location-Aware Near-Me Sorting:**
  - Automatically identifies and flags your nearest saved station with walking distance in meters using Google Play Services Location (`FusedLocationProviderClient`).
- **🔀 Smooth Drag & Drop Customization:**
  - Reorder saved stations effortlessly with custom animated drag-and-drop grid gestures.
- **🎨 Edge-to-Edge & Adaptive Layouts:**
  - Full Android 15/16 edge-to-edge support with dynamic system bar insets.
  - Adaptive grid hierarchy designed for phones, foldables, and tablets.
  - Pixel-perfect circular adaptive launcher icons adhering strictly to Google's 66dp safe zone specs.

---

## 🛠️ Tech Stack & Architecture

- **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Expressive design tokens.
- **AI / Generative Model:** Hybrid on-device **Gemini Nano** ([ML Kit GenAI Prompt API](https://developers.google.com/ml-kit)) + Cloud **Gemini 1.5 Flash** ([Firebase AI Logic](https://firebase.google.com/docs/ai-logic)).
- **Widgets:** [Glance AppWidget](https://developer.android.com/jetpack/compose/glance) backed by DataStore Preferences.
- **Architecture:** Unidirectional Data Flow (MVI/MVVM), Kotlin Coroutines & `StateFlow`.
- **Networking:** [Retrofit 2](https://square.github.io/retrofit/) + [Moshi](https://github.com/square/moshi) with Kotlin code-generation (KSP).
- **Persistence:** [Room Database](https://developer.android.com/training/data-storage/room) for instantaneous offline caching & [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preferences DataStore).
- **Background Work:** [AndroidX WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for periodic background widget updates.
- **Location:** Google Play Services Location (`FusedLocationProviderClient`).

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or higher
- Android SDK 35+ (target SDK 37)

### 1. Clone the repository
```bash
git clone https://github.com/your-username/departure-board.git
cd departure-board
```

### 2. Configure your TfL API Key (Secure Setup)
This project uses `local.properties` to ensure your personal API keys are **never accidentally committed to GitHub**.

1. Obtain a free API key from the [TfL Open Data Portal](https://api-portal.tfl.gov.uk/).
2. Open `local.properties` (or copy from `local.properties.example`):
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   TFL_API_KEY=your_tfl_api_key_here
   ```
3. Gradle will automatically inject this key into `BuildConfig.TFL_API_KEY` at compile time and pass it as an authenticated query parameter on all TfL API requests.

> **Note:** If you don't provide an API key, the app will still function using TfL's open anonymous quota, but you may experience rate limiting (`HTTP 429`) during frequent updates.

### 3. Build & Run
Run the unit test suite:
```bash
./gradlew testDebugUnitTest
```

Build the debug APK:
```bash
./gradlew assembleDebug
```

Or deploy directly to your device or emulator via Android Studio.

---

## 📱 Home Screen Widget Setup

1. Long-press on your device's home screen and choose **Widgets**.
2. Scroll to **Prompt Departure** and drag the departure card onto your home screen.
3. In the configuration dialog:
   - Select any London Underground, Rail, or Bus station.
   - Choose whether to display **All Lines & Routes** or filter to a **Specific Route** (e.g., Victoria Line or Bus 221).
4. Tap the widget at any time to open full live arrivals and line disruption statuses in the app!

---

## 📄 License

```text
Copyright 2026 Prompt Departure Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
