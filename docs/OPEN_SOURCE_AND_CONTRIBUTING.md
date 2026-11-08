# 🤝 Open Source, Release & Contributing Guide

**Kutub (કુટુંબ)** is an open-source Android project released under the **MIT License** by **[Maulik Solanki](https://maulik-solanki.vercel.app)**.

---

## 1. 📄 Open-Source License & Attribution

This project is licensed under the **MIT License** ([`LICENSE`](../LICENSE)).

- **Author & Maintainer**: **Maulik Solanki**
- **Portfolio**: [https://maulik-solanki.vercel.app](https://maulik-solanki.vercel.app)
- **Email**: `mrmaulik1112@gmail.com`

You are free to use, study, modify, and distribute this software for personal or commercial purposes, provided that the original copyright notice and MIT permission notice (`Copyright (c) 2026 Maulik Solanki`) are included in all copies or substantial portions of the software.

---

## 2. 🛠️ Building & Testing Locally

### Prerequisites
- **Android Studio** (Ladybug or newer recommended)
- **JDK 17+**
- **Android SDK 36** (minimum device API 24 / Android 7.0+)

### Build Commands
```bash
# Build Debug APK
gradle :app:assembleDebug

# Run JVM & Robolectric Unit Tests
gradle :app:testDebugUnitTest --tests com.example.ExampleRobolectricTest
```

The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. 📦 How to Publish a Downloadable APK on GitHub Releases

So visitors from your portfolio ([https://maulik-solanki.vercel.app](https://maulik-solanki.vercel.app)) or GitHub can install the app directly on their Android phone without needing Android Studio:

1. Build or export the **APK** (`app-debug.apk` or signed release APK) and rename it to `Kutub-v1.0.0.apk`.
2. Open your GitHub repository in the browser and click **Releases** → **Create a new release** in the right sidebar.
3. Click **Choose a tag**, type **`v1.0.0`**, and select **Create new tag: v1.0.0 on publish**.
4. Set the Release Title to:
   `Kutub v1.0.0 — Offline Daily Life, Finance, Fuel & Meditation Super-App`
5. Drag and drop `Kutub-v1.0.0.apk` into the **Attach binaries by dropping them here** box.
6. Click **Publish release**.
7. Share your direct release link (`https://github.com/<your-username>/<repo-name>/releases/latest`) on your portfolio website!

---

## 4. 💡 How to Showcase This Project on Your Portfolio

When presenting **Kutub** on [https://maulik-solanki.vercel.app](https://maulik-solanki.vercel.app), your resume, or LinkedIn:
- **Highlight Product Ownership & UX Engineering**: Explain how you designed real-life workflows (such as the **Petrol Pump Fuel Draft & Later Odometer Edit workflow**, **Live 4-4-6 Breathing & Meditation Countdown**, **Single-Line Responsive UI across screen sizes**, and **5-Layer Anti-Spam Google Sheet Feedback Pipeline**).
- **Highlight AI-Assisted Software Engineering**: Modern engineering teams value developers who can architect, prompt, test, audit security, and ship complete production applications using AI-assisted workflows alongside Java/Kotlin fundamentals.

---

## 5. 🤝 How to Contribute

1. **Fork** the repository on GitHub.
2. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make your changes and verify that the build and unit tests pass:
   ```bash
   gradle :app:testDebugUnitTest --tests com.example.ExampleRobolectricTest
   ```
4. Commit your changes with a clear message and open a **Pull Request**.
