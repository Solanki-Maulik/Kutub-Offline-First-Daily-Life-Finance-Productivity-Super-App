# 🔒 Security, Privacy & Anti-Spam Architecture

**Kutub (કુટુંબ)** is engineered with an **Offline-First, Zero-Key Public Repository** security model so users retain 100% ownership of their personal data and developers can safely publish and fork the repository on GitHub.

---

## 1. Zero API Keys & Public Repository Safety

- **No Secrets in Source Control**:
  - `.env.example` contains **no `GEMINI_API_KEY`** or third-party secret keys.
  - `.gitignore` strictly excludes `.env`, `local.properties`, `*.jks`, and `*.keystore` files from ever being committed.
- **Safe to Clone & Fork**: Anyone can clone this repository and run `gradle :app:assembleDebug` without needing to configure secret keys.

---

## 2. 100% On-Device Data Privacy

- **Local Room SQLite Database**:
  - All financial transactions, account balances, Udhaar records, tasks, schedules, habits, and notes are stored locally inside the app's private sandboxed SQLite database (`AppDatabase`).
- **Local Toolkit Storage**:
  - Vehicle fuel logs, draft odometer entries, meditation stats, grocery price memory, and subscription items are stored locally in private `SharedPreferences` JSON storage.
- **User-Controlled Backup & Restore**:
  - Backups (JSON) and exports (CSV) use Android's system **Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`)**, writing only to the exact file chosen by the user.

---

## 3. Least-Privilege Android Permissions

Kutub complies with Google Play Developer Program policies and requests only minimal permissions:

| Permission | Type | Why It Is Used |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Normal (Install-time) | Used only when submitting optional bug reports or using optional cloud AI features. |
| `android.permission.ACCESS_NETWORK_STATE` | Normal (Install-time) | Checks whether the device is online before attempting network calls, falling back to offline storage immediately if offline. |
| `android.permission.USE_BIOMETRIC` | Normal (Install-time) | Allows optional fingerprint / face unlock when Biometric App Lock is enabled in Settings. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (Android 13+) | Requested only if the user enables local task/bill reminders. |

- **Zero Broad Storage Permissions**: Kutub **never** requests `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, or `READ_MEDIA_IMAGES`. Receipt selection uses the zero-permission Android Photo Picker (`PickVisualMedia`).

---

## 4. 5-Layer Anti-Spam & Security for Google Sheet Bug Reporting

Kutub includes an in-app **Report Bug / Feedback** dialog (`SupportAndFeedbackUtils.kt`) connected to a Google Apps Script Web App (`doPost`) that writes user feedback to a Google Sheet while protecting against spam and injection attacks:

1. **Client-Side Cooldown & Daily Quota**:
   - Enforces a **2-minute cooldown** (`120,000 ms`) between submissions and a **maximum of 5 submissions per 24 hours** per device.
2. **SHA-256 Duplicate Content Detection**:
   - Computes a SHA-256 hash of normalized feedback text and blocks duplicate re-submissions.
3. **Gibberish & Bot Heuristic Filter**:
   - Rejects payloads under 8 characters, over 1,200 characters, or repetitive single-character spam (`isRepetitiveGibberish`).
4. **Spreadsheet Formula-Injection Sanitization (`sanitizeForSheetCell`)**:
   - Any input starting with `=`, `+`, `-`, `@`, `\t`, or `\r` is automatically prefixed with a single quote (`'`) and stripped of HTML tags so malicious formulas (such as `=IMPORTXML(...)` or `=IMAGE(...)`) can never execute inside Google Sheets.
5. **Server-Side Apps Script Verification (`kutub_feedback_v1`)**:
   - Validates the `appToken` signature (`kutub_feedback_v1`) and enforces server-side `CacheService` rate limiting inside Google Apps Script.
   - If the device is offline, feedback is safely queued on-device and can also be sent via the user's email client (`mailto:mrmaulik1112@gmail.com`).

---

## 5. Reporting a Security Vulnerability

If you discover a security issue, please report it via:
- **In-App Bug Report Dialog**: Tap the `?` icon on the Home Screen → **Report Bug** tab.
- **Direct Email**: `mrmaulik1112@gmail.com`
- **Developer Portfolio**: [https://maulik-solanki.vercel.app](https://maulik-solanki.vercel.app)
