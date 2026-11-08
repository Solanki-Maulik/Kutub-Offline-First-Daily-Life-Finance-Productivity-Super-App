# Kutub (કુટુંબ) — Offline-First Daily Life, Finance & Productivity Super-App

**Kutub (કુટુંબ)** is a fast, privacy-first, **100% offline-capable** Android application built with **Kotlin** and **Jetpack Compose (Material Design 3)**. It brings together personal finance, udhaar (debt/loan) tracking, tasks, daily timetables, habit streaks, notes, and a complete **Daily Life & Pro Toolkit**—including a **Vehicle Fuel & Mileage (`km/L`) Tracker** and a **Live Meditation Countdown & Breathing Pacer**—in a single clean app with zero subscriptions or paywalls.

---

## 🔒 Public Repository Security & Offline-First Architecture

- **Zero API Keys in Repository**: This repository contains **no `GEMINI_API_KEY`** or hardcoded credentials in source code, Gradle files, or `.env.example`. It is completely safe to publish as a **Public GitHub Repository**.
- **100% Offline-First Operation**:
  - All financial transactions, accounts, budgets, bills, udhaar records, tasks, schedules, habits, notes, fuel logs, and meditation sessions are stored locally on your device using **Android Room (SQLite)** and **SharedPreferences**.
  - **Instant Offline Smart Data Entry**: The Smart Entry & Assistant module includes a built-in **on-device local NLP & Regex parser** (`< 5 ms` response time) that parses natural-language commands into **Expenses, Income, Transfers, Udhaar (Got/Gave), Tasks, Schedules, Habits, and Notes** even with **no internet connection**.
  - When online and configured with Firebase AI Logic (`Firebase.ai` + App Check), cloud-assisted insights and receipt scanning are optionally supported with automatic instant fallback to the on-device engine.

---

## ✨ Highlighted Daily Life & Pro Toolkit Features (100% Free)

### 1. 🧘 Live Meditation & Focus Countdown Timer (`MEDITATION_FOCUS`)
- **Live Circular Countdown Ring**: Real-time `MM:SS` countdown ring with smooth progress animation.
- **4-4-6 Guided Breathing Pacer**: Cycles automatically through **Breathe In (4s)** → **Hold Gently (4s)** → **Breathe Out (6s)** while your session is active to help reduce stress and improve focus.
- **Preset & Custom Durations**: Choose **5 min**, **10 min**, **15 min**, **25 min (Pomodoro Focus)**, or enter any custom duration (`1–180 min`), with live **Start / Pause**, **+5m**, and **Reset** controls.
- **Automatic Habit Streak Sync**: Completing a meditation session automatically checks off your **Meditation Habit** for today and logs your peaceful minutes.

### 2. ⛽ Vehicle Fuel & Mileage (`km/L`) Tracker (`VEHICLE_FUEL`)
- **Real Mileage (`km/L`) & Running Cost (`₹/km`)**: Log your vehicle name (Bike, Scooter, Car), current odometer reading (`km`), fuel paid (`₹`), and fuel rate (`₹/L`).
- **Auto-Computed Litres & Efficiency**: Automatically computes fuel volume (`L`), compares against the previous odometer reading for that vehicle to calculate exact **`km/L` mileage** and **`₹/km` running cost**, and tracks lifetime fuel spend.
- **Next Service Odometer Alert**: Shows remaining kilometres until your next scheduled service and warns when maintenance is due.
- **1-Tap Ledger Sync**: Automatically logs each fuel fill-up as a **Transport** expense in your Money Ledger.

### 3. 🛡️ Safe-to-Spend Today & Subscription Renewal Radar (`SAFE_SPEND_SUBS`)
- **Safe Daily Spend (`₹/day`)**: Automatically subtracts unpaid upcoming bills and active subscriptions from your total reconciled balance and divides by the remaining days in the current month.
- **Subscription Renewal Radar**: Tracks monthly and yearly subscriptions with renewal countdowns and **1-Tap Pay & Log** into the Money Ledger.

### 4. 🛒 Smart Grocery Price Memory List (`GROCERY_MEMORY`)
- **Last Price Memory**: Remembers the last price (`₹`) you paid for each grocery item so you can spot price increases and estimate your basket total before leaving home.
- **1-Tap Checkout to Ledger**: Logs your checked grocery cart directly as a single **Groceries** expense in your Money Ledger.

### 5. 🧮 EMI Prepayment Saver & 1-Tap WhatsApp Reminder (`EMI_WHATSAPP`)
- **Loan Prepayment Calculator**: Calculates your monthly EMI, total interest saved (`₹`), and loan tenure months saved when making an extra monthly prepayment.
- **Polite Payment Reminders (Gujarati / Hindi / English)**: Generates polite Udhaar/payment reminder messages in **ગુજરાતી (Gujarati)**, **हिन्दी (Hindi)**, and **English** with **1-Tap WhatsApp Share** or clipboard copy.

### 6. 📋 60-Second Daily Wrap-Up & Passbook Statement (`DAILY_WRAPUP`)
- **Daily Completion Score (`0–100%`)**: Evaluates your daily progress across four pillars—Expenses Logged, Tasks Completed, Habits Checked Off, and Mindful Meditation Minutes.
- **Bank-Passbook Statement Export**: Generates a clean, text-formatted mini passbook statement (Opening/Closing Balance, Total Cr/Dr, Net Flow, and recent transactions) ready to share via WhatsApp or save to notes.

---

## 💰 Core Ecosystem Modules

| Module | Key Capabilities |
| :--- | :--- |
| **Money Hub & Ledger** | Track Expenses, Income, and Account-to-Account Transfers across Cash, Bank, UPI, and Wallet accounts with full edit/delete support and category breakdowns. |
| **Udhaar (Debts & Loans)** | Track money **You Got** (`મળ્યા`) and **You Gave** (`આપ્યા`), partial settlements, due dates, and polite WhatsApp reminders. |
| **Tasks & Priorities** | Manage daily tasks with High/Medium/Low priorities, interactive Material 3 Date & Clock pickers, edit support, and active task completion streaks. |
| **Daily Schedule & Timetable** | Plan time-blocked events (`Start Time – End Time`) with Daily/Weekly recurrence and quick time pickers. |
| **Habits & Streak Tracker** | Build daily habits with 1-tap check-in, current/best streak calculation, and automatic Meditation timer integration. |
| **Notes & Knowledge Vault** | Capture quick notes, checklists, and ideas with instant search and action-item extraction. |
| **Backup & CSV Export** | Export transactions to CSV or create/restore full local JSON backups on your device. |

---

## 📱 Device Compatibility & Minimum Requirements

- **Minimum Android Version**: **Android 7.0 (Nougat / API Level 24)**
- **Target / Compiled SDK**: **Android 16 (API Level 36)**
- **Supported Screen Sizes**: Responsive Material 3 layouts with `AutoSizeSingleLineText` and adaptive cards that fit compact phones, large phones, foldables, and tablets without text clipping or button overlap.

---

## 🔐 App Permissions

Kutub follows strict **least-privilege** privacy standards:
- `INTERNET` & `ACCESS_NETWORK_STATE` *(Install-time normal permissions)*: Used only when optional cloud features are invoked. Core app features work completely offline.
- `USE_BIOMETRIC` *(Install-time normal permission)*: Used only if you enable the optional Biometric App Lock in Settings.
- `POST_NOTIFICATIONS` *(Optional runtime permission on Android 13+)*: Requested only when scheduling local reminders.
- **Zero Storage Permissions Required**: Uses Android's zero-permission **Photo Picker (`PickVisualMedia`)** and **Storage Access Framework (`CreateDocument` / `OpenDocument`)** for receipt images and JSON/CSV backups.

---

## 🛠️ Tech Stack & Project Structure

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose + Material Design 3 (Dynamic & Custom Light/Dark Themes, Adaptive Typography)
- **Architecture**: MVVM (`FinanceViewModel`, `StateFlow`, `collectAsStateWithLifecycle`)
- **Local Persistence**: Room SQLite Database (`AppDatabase`, `FinanceDao`) + SharedPreferences JSON persistence (`DailyToolkitUtils`)
- **Testing**: JUnit 4 + Robolectric JVM unit tests (`ExampleRobolectricTest`)

```text
app/src/main/java/com/example/
├── data/           # Room entities (Transaction, Account, DebtRecord, TaskItem, ScheduleEvent, Habit, Note, etc.) & DAO
├── ui/
│   ├── components/ # Reusable responsive components (AutoSizeSingleLineText, QuickAddSheet, etc.)
│   ├── screens/    # HomeScreen, DailyToolkitScreen, EcosystemHubScreens, AddTransactionScreen, AiAssistantScreen, SettingsScreen, etc.
│   └── theme/      # Material 3 Color, Typography & Theme definitions
├── utils/          # DailyToolkitUtils, GeminiAiService (Offline NLP + AI), AccountUtils, HabitUtils, TaskUtils, BackupUtils
└── viewmodel/      # FinanceViewModel (Unified state & repository layer)
```

---

## 🚀 Getting Started (Clone & Build)

1. **Clone the repository**:
   ```bash
   git clone <your-github-repo-url>
   cd kutub
   ```

2. **Open in Android Studio**:
   - Open the project folder in **Android Studio Ladybug (or newer)**.
   - No `.env` file or `GEMINI_API_KEY` is required—Gradle automatically uses `.env.example` and builds out-of-the-box.

3. **Build Debug APK via CLI**:
   ```bash
   gradle :app:assembleDebug
   ```

4. **Run Unit Tests**:
   ```bash
   gradle :app:testDebugUnitTest --tests com.example.ExampleRobolectricTest
   ```

---

## 📄 License

This project is open-source and available for personal and educational use.
