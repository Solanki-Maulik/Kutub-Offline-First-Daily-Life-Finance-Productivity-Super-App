# 📖 Kutub (કુટુંબ) — Complete Features Documentation

**Kutub (કુટુંબ)** is a 100% free, offline-first Android super-app created by **[Maulik Solanki](https://maulik-solanki.vercel.app)** that unifies personal finance, Udhaar (debt/loan) management, daily productivity, and real-world Indian household utility tools into one cohesive Material 3 experience.

---

## 1. 🏠 Home Screen & Quick Navigation

- **Single-Line Dynamic Greeting**: Displays time-aware English and Gujarati greetings (*Good morning / સુપ્રભાત*, *Good afternoon / શુભ બપોર*, *Good evening / શુભ સાંજ*, *Good night / શુભ રાત્રિ*) that automatically scale to fit on one single line without overlapping action icons.
- **Interactive First-Launch Quick Guide**: A dismissible card on the Home screen and a top-bar `?` help button open a tabbed guide covering **Quick Guide**, **Support & Developer Info**, and **Report Bug / Feedback**.
- **Unified Ecosystem Access**: 1-tap cards for Money Hub, Udhaar, Tasks, Daily Schedule, Habits, Notes, Smart Entry AI, and the **Daily Life & Pro Toolkit**.

---

## 2. 🧰 Daily Life & Pro Toolkit (`DailyToolkitScreen`)

### 2.1 🧘 Live Meditation & Focus Countdown (`MEDITATION_FOCUS`)
- **Live Circular Countdown Ring**: High-contrast animated ring displaying remaining `MM:SS`.
- **4-4-6 Guided Breathing Pacer**:
  - **Breathe In (4 seconds)**
  - **Hold Gently (4 seconds)**
  - **Breathe Out (6 seconds)**
- **Flexible Session Controls**: Preset buttons for **5 min**, **10 min**, **15 min**, **25 min (Pomodoro)**, plus a custom minutes input (`1–180 min`), **Start / Pause**, **+5m**, and **Reset**.
- **Habit Auto-Completion**: Finishing a countdown automatically marks your daily Meditation habit as completed and increments your total mindful minutes.

### 2.2 ⛽ Vehicle Fuel & Mileage (`km/L`) Tracker (`VEHICLE_FUEL`)
Designed around the **real-life Indian petrol pump workflow**:
1. **At the Petrol Pump (Save Draft)**:
   - When you pay for petrol/diesel, you only know the **Fuel Paid (`₹`)** and **Fuel Rate (`₹/Litre`)** (and optionally your current **Start Odo (`km`)**).
   - Tap **Save Draft** to store the fill-up immediately with a `DRAFT` badge and automatically log the expense in your Money Ledger.
2. **Days or Weeks Later (Edit Odo & Compute Mileage)**:
   - When you visit the petrol pump again and check your new odometer reading, tap **Edit Odo** on the saved entry in your history list.
   - Enter or update **Start Odo (`km`)** and **Next / End Odo (`km`)** and tap **Update Fuel & Mileage**.
   - The app immediately calculates your exact **Mileage (`km/L`)**, **Distance Driven (`km`)**, and **Running Cost (`₹/km`)**.
3. **Consecutive Fill-Up Support**: Also automatically calculates mileage across consecutive odometer entries for the same vehicle.

### 2.3 🛡️ Safe-to-Spend Today & Subscription Radar (`SAFE_SPEND_SUBS`)
- **Safe Daily Spend (`₹/day`)**:
  $$\text{Safe Daily Spend} = \frac{\text{Reconciled Balance} - \text{Unpaid Bills} - \text{Active Monthly Subscriptions}}{\text{Remaining Days in Month}}$$
- **Subscription Renewal Radar**: Tracks monthly/yearly recurring services (WiFi, OTT, Mobile Recharge, Gym) and lets you log & pay renewals into the ledger with 1 tap.

### 2.4 🛒 Smart Grocery Price Memory List (`GROCERY_MEMORY`)
- **Historical Price Memory**: Stores the last price (`₹`) paid for each item so you can compare current market prices and estimate your total basket cost before checkout.
- **1-Tap Ledger Checkout**: Converts all checked items in your basket into a single `Groceries` expense in the Money Ledger.

### 2.5 🧮 EMI Prepayment Saver & 1-Tap WhatsApp Reminder (`EMI_WHATSAPP`)
- **EMI Prepayment Calculator**: Computes standard monthly EMI plus exact **Total Interest Saved (`₹`)** and **Months Saved** when paying an extra monthly amount toward principal.
- **Tri-Lingual Polite Payment Reminders**: Generates formatted Udhaar/payment reminders with your UPI ID in **ગુજરાતી (Gujarati)**, **हिन्दी (Hindi)**, and **English**, with direct 1-tap sharing to WhatsApp.

### 2.6 📋 60-Second Daily Wrap-Up & Passbook (`DAILY_WRAPUP`)
- **4-Pillar Daily Score (`0–100%`)**: Tracks whether you logged expenses, completed tasks, checked off habits, and meditated today.
- **Shareable Mini Passbook Statement**: Generates a clean text statement with Opening Balance, Total Credits (`+₹`), Total Debits (`-₹`), Closing Balance, and recent transactions.

---

## 3. 💰 Financial & Productivity Modules

### 3.1 Money Hub & Multi-Account Ledger
- Supports **Cash**, **Bank**, **UPI**, and **Wallet** accounts.
- Full CRUD (Create, Read, Update, Delete) for **Expenses**, **Income**, and **Transfers**.
- Visual category breakdown and account reconciliation.

### 3.2 Udhaar (Debts & Loans)
- Dedicated tracking for **You Got (`મળ્યા`)** and **You Gave (`આપ્યા`)**.
- Partial payment settlements, person-wise net balance, due dates, and WhatsApp reminder integration.

### 3.3 Tasks, Daily Schedule & Habits
- **Tasks**: High/Medium/Low priority badges, Material 3 Date & Clock pickers, edit modal, and real-time completion streaks.
- **Schedule / Timetable**: Time-blocked daily planner (`Start Time – End Time`) with recurrence options.
- **Habits**: Daily check-in cards, current vs. best streak tracking, and rule-based habits (e.g., *No-Spend Day*).

### 3.4 Notes & Knowledge Vault (`NoteEditorDialog`)
- Rich note and checklist editor with color accents and pin/archive support.
- **Single-Line Entity Attachments**: Attach any note to a **Transaction**, **Person (Udhaar)**, or **Task** using compact single-line selector buttons (`AutoSizeSingleLineText`) and bordered picker cards.
- **Action Item Extraction**: Convert unchecked checklist lines directly into Tasks with 1 tap.

### 3.5 Smart Entry & Offline-First Assistant (`AiAssistantScreen`)
- **Instant On-Device Parser (`LocalAiFallbackEngine`)**: Parses multi-item natural language prompts (e.g., `"chai 20 cash, petrol 300 bank, got 500 from Raj, task pay electricity bill, habit morning walk"`) in under `5 ms` completely offline.
- **Optional Cloud AI Enhancement**: When online and configured with Firebase AI Logic (`Firebase.ai`), supports conversational insights and receipt image extraction with automatic fallback to the local engine.
