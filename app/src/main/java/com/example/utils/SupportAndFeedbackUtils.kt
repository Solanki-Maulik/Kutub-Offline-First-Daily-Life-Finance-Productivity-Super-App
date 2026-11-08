package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class FeedbackReportItem(
    val id: String = UUID.randomUUID().toString(),
    val userName: String,          // Optional Name
    val contactOrDeviceInfo: String, // 2nd info field: Email / Phone / Handle or Device Info
    val category: String,          // BUG_REPORT, FEATURE_REQUEST, UI_FIT_ISSUE, GENERAL_FEEDBACK
    val message: String,           // Detailed issue or feedback
    val deviceModelAndSdk: String, // Auto-captured Android version & device model for bug debugging
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING_SYNC" // SENT_TO_SHEET, QUEUED_OFFLINE
)

sealed class FeedbackValidationOutcome {
    data class Valid(val sanitizedItem: FeedbackReportItem, val contentHash: String) : FeedbackValidationOutcome()
    data class Blocked(val reason: String, val remainingCooldownSeconds: Int = 0) : FeedbackValidationOutcome()
}

data class FeedbackSubmitResult(
    val success: Boolean,
    val sentToGoogleSheet: Boolean,
    val queuedOffline: Boolean,
    val userMessage: String
)

object SupportAndFeedbackUtils {
    const val DEVELOPER_NAME = "Maulik Solanki"
    const val PORTFOLIO_URL = "https://maulik-solanki.vercel.app"
    const val SUPPORT_EMAIL = "mrmaulik1112@gmail.com"
    const val LICENSE_NAME = "MIT Open-Source License"

    private const val PREFS_NAME = "kutub_support_feedback_prefs"
    private const val KEY_LAST_SUBMIT_TS = "last_submit_timestamp_ms"
    private const val KEY_DAILY_DAY_KEY = "daily_submit_day_key"
    private const val KEY_DAILY_COUNT = "daily_submit_count"
    private const val KEY_LAST_CONTENT_HASH = "last_submit_content_hash"
    private const val KEY_FEEDBACK_HISTORY_JSON = "feedback_history_json"
    private const val KEY_CUSTOM_SHEET_WEBHOOK_URL = "custom_google_sheet_webhook_url"
    const val DEFAULT_GOOGLE_SHEET_WEBHOOK_URL =
        "https://script.google.com/macros/s/AKfycbxWOTu1azoHJ7v67a96TLaJ_YuMj2BDg4bzA9k3He5697Cv_ROpT_eli5BIabUrTDSS/exec"

    // Anti-spam security thresholds
    const val COOLDOWN_MILLIS = 120_000L // 2 minutes between submissions
    const val MAX_SUBMISSIONS_PER_DAY = 5
    const val MIN_MESSAGE_LENGTH = 10
    const val MAX_MESSAGE_LENGTH = 1000
    private const val APP_CLIENT_SIGNATURE = "KUTUB_ANDROID_V1_SAFE_REPORT"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Prevents Google Sheet Formula Injection (=, +, -, @, tab, CR) and strips HTML/script tags.
     */
    fun sanitizeForSheetCell(raw: String, maxLength: Int = 250): String {
        val stripped = raw
            .replace(Regex("<[^>]*>"), "") // Remove HTML/script tags
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .take(maxLength)

        if (stripped.isEmpty()) return ""
        val firstChar = stripped.first()
        return if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t') {
            "'$stripped"
        } else {
            stripped
        }
    }

    /**
     * Computes SHA-256 hash of normalized category + message to block duplicate spam submissions.
     */
    fun computeContentHash(category: String, message: String): String {
        val normalized = "${category.trim().uppercase(Locale.ROOT)}|${message.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")}"
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Checks whether the message is repetitive gibberish spam (e.g. "aaaaaaaaaa" or "1111111111").
     */
    fun isRepetitiveGibberish(text: String): Boolean {
        val clean = text.filter { !it.isWhitespace() }.lowercase(Locale.ROOT)
        if (clean.length < MIN_MESSAGE_LENGTH) return true
        val distinctChars = clean.toSet().size
        return distinctChars <= 2
    }

    /**
     * Validates input fields, anti-bot honeypot, 2-minute rate limit cooldown, 5/day cap, and duplicate hash guard.
     */
    fun validateAndCheckRateLimit(
        sp: SharedPreferences,
        rawName: String,
        rawContactOrExtraInfo: String,
        category: String,
        rawMessage: String,
        honeypot: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): FeedbackValidationOutcome {
        // 1. Honeypot check (bots fill hidden fields)
        if (honeypot.isNotBlank()) {
            return FeedbackValidationOutcome.Blocked("Automated spam submission blocked.")
        }

        // 2. Cooldown check (120 seconds between reports)
        val lastSubmitTs = sp.getLong(KEY_LAST_SUBMIT_TS, 0L)
        val elapsed = nowMillis - lastSubmitTs
        if (lastSubmitTs > 0L && elapsed in 0 until COOLDOWN_MILLIS) {
            val remainingSec = ((COOLDOWN_MILLIS - elapsed) / 1000L).toInt().coerceAtLeast(1)
            return FeedbackValidationOutcome.Blocked(
                reason = "Rate limit active to prevent spam. Please wait ${remainingSec}s before sending another report.",
                remainingCooldownSeconds = remainingSec
            )
        }

        // 3. Daily cap check (max 5 reports per day per device)
        val todayKey = HabitUtils.getTodayIso()
        val storedDay = sp.getString(KEY_DAILY_DAY_KEY, "") ?: ""
        val dailyCount = if (storedDay == todayKey) sp.getInt(KEY_DAILY_COUNT, 0) else 0
        if (dailyCount >= MAX_SUBMISSIONS_PER_DAY) {
            return FeedbackValidationOutcome.Blocked(
                reason = "Daily feedback limit ($MAX_SUBMISSIONS_PER_DAY reports/day) reached for anti-spam security. Please email $SUPPORT_EMAIL directly if urgent."
            )
        }

        // 4. Content length & quality validation
        val trimmedMessage = rawMessage.trim()
        if (trimmedMessage.length < MIN_MESSAGE_LENGTH) {
            return FeedbackValidationOutcome.Blocked(
                reason = "Please enter at least $MIN_MESSAGE_LENGTH characters describing the bug or feedback."
            )
        }
        if (trimmedMessage.length > MAX_MESSAGE_LENGTH) {
            return FeedbackValidationOutcome.Blocked(
                reason = "Message is too long (max $MAX_MESSAGE_LENGTH characters)."
            )
        }
        if (isRepetitiveGibberish(trimmedMessage)) {
            return FeedbackValidationOutcome.Blocked(
                reason = "Please enter a meaningful description (repetitive characters are blocked by spam filter)."
            )
        }

        // 5. Duplicate submission check
        val contentHash = computeContentHash(category, trimmedMessage)
        val lastHash = sp.getString(KEY_LAST_CONTENT_HASH, "") ?: ""
        if (contentHash == lastHash) {
            return FeedbackValidationOutcome.Blocked(
                reason = "This exact feedback was already submitted. Thank you!"
            )
        }

        val safeName = sanitizeForSheetCell(rawName.ifBlank { "Anonymous User" }, maxLength = 60)
        val safeContact = sanitizeForSheetCell(rawContactOrExtraInfo.ifBlank { "Not provided" }, maxLength = 100)
        val safeCategory = sanitizeForSheetCell(category.ifBlank { "BUG_REPORT" }, maxLength = 40)
        val safeMessage = sanitizeForSheetCell(trimmedMessage, maxLength = MAX_MESSAGE_LENGTH)
        val deviceSummary = sanitizeForSheetCell(
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}",
            maxLength = 120
        )

        val item = FeedbackReportItem(
            userName = safeName,
            contactOrDeviceInfo = safeContact,
            category = safeCategory,
            message = safeMessage,
            deviceModelAndSdk = deviceSummary,
            timestamp = nowMillis
        )
        return FeedbackValidationOutcome.Valid(item, contentHash)
    }

    fun recordSuccessfulSubmission(sp: SharedPreferences, contentHash: String, nowMillis: Long = System.currentTimeMillis()) {
        val todayKey = HabitUtils.getTodayIso()
        val storedDay = sp.getString(KEY_DAILY_DAY_KEY, "") ?: ""
        val newCount = if (storedDay == todayKey) sp.getInt(KEY_DAILY_COUNT, 0) + 1 else 1
        sp.edit()
            .putLong(KEY_LAST_SUBMIT_TS, nowMillis)
            .putString(KEY_DAILY_DAY_KEY, todayKey)
            .putInt(KEY_DAILY_COUNT, newCount)
            .putString(KEY_LAST_CONTENT_HASH, contentHash)
            .apply()
    }

    fun getConfiguredWebhookUrl(sp: SharedPreferences): String {
        val custom = (sp.getString(KEY_CUSTOM_SHEET_WEBHOOK_URL, "") ?: "").trim()
        return if (custom.startsWith("https://script.google.com/macros/s/")) {
            custom
        } else {
            DEFAULT_GOOGLE_SHEET_WEBHOOK_URL
        }
    }

    fun setConfiguredWebhookUrl(sp: SharedPreferences, url: String) {
        sp.edit().putString(KEY_CUSTOM_SHEET_WEBHOOK_URL, url.trim()).apply()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun loadFeedbackHistory(sp: SharedPreferences): List<FeedbackReportItem> {
        val raw = sp.getString(KEY_FEEDBACK_HISTORY_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<FeedbackReportItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FeedbackReportItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        userName = o.optString("userName", "Anonymous User"),
                        contactOrDeviceInfo = o.optString("contactOrDeviceInfo", "Not provided"),
                        category = o.optString("category", "BUG_REPORT"),
                        message = o.optString("message", ""),
                        deviceModelAndSdk = o.optString("deviceModelAndSdk", ""),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        status = o.optString("status", "QUEUED_OFFLINE")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFeedbackToHistory(sp: SharedPreferences, item: FeedbackReportItem) {
        val existing = loadFeedbackHistory(sp).filter { it.id != item.id }
        val updated = (listOf(item) + existing).take(30)
        val arr = JSONArray()
        updated.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("userName", entry.userName)
                    put("contactOrDeviceInfo", entry.contactOrDeviceInfo)
                    put("category", entry.category)
                    put("message", entry.message)
                    put("deviceModelAndSdk", entry.deviceModelAndSdk)
                    put("timestamp", entry.timestamp)
                    put("status", entry.status)
                }
            )
        }
        sp.edit().putString(KEY_FEEDBACK_HISTORY_JSON, arr.toString()).apply()
    }

    /**
     * Submits the validated feedback to the Google Sheet Apps Script Web App URL when online,
     * or queues it locally when offline / unconfigured.
     */
    suspend fun submitFeedbackReport(
        context: Context,
        sp: SharedPreferences,
        item: FeedbackReportItem,
        contentHash: String
    ): FeedbackSubmitResult = withContext(Dispatchers.IO) {
        val webhookUrl = getConfiguredWebhookUrl(sp)
        val online = isNetworkAvailable(context)

        var sentToSheet = false
        if (online && webhookUrl.startsWith("https://script.google.com/macros/s/")) {
            try {
                val payload = JSONObject().apply {
                    put("appSignature", APP_CLIENT_SIGNATURE)
                    put("id", item.id)
                    put("timestampIso", FormatUtils.formatDateTime(item.timestamp))
                    put("name", item.userName)
                    put("contactOrExtraInfo", item.contactOrDeviceInfo)
                    put("category", item.category)
                    put("message", item.message)
                    put("deviceInfo", item.deviceModelAndSdk)
                    put("contentHash", contentHash)
                }
                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    sentToSheet = response.isSuccessful || response.code in 200..399
                }
            } catch (_: Exception) {
                sentToSheet = false
            }
        }

        val finalStatus = if (sentToSheet) "SENT_TO_SHEET" else "QUEUED_OFFLINE"
        val savedItem = item.copy(status = finalStatus)
        saveFeedbackToHistory(sp, savedItem)
        recordSuccessfulSubmission(sp, contentHash, item.timestamp)

        val userMsg = when {
            sentToSheet -> "Thank you! Your report was synced directly to Maulik Solanki's Google Sheet."
            !online -> "Saved offline! Your report is queued locally and ready to send when internet connects."
            else -> "Report verified & saved! Tap 'Send via Email' or connect a Google Sheet Web App URL."
        }

        FeedbackSubmitResult(
            success = true,
            sentToGoogleSheet = sentToSheet,
            queuedOffline = !sentToSheet,
            userMessage = userMsg
        )
    }

    /**
     * Ready-to-paste Google Apps Script (Extensions -> Apps Script in Google Sheets)
     * with server-side signature check and formula-injection protection.
     */
    val GOOGLE_APPS_SCRIPT_TEMPLATE = """
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    if (data.appSignature !== "KUTUB_ANDROID_V1_SAFE_REPORT") {
      return ContentService.createTextOutput("Unauthorized").setMimeType(ContentService.MimeType.TEXT);
    }
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["Timestamp", "Category", "Name (Optional)", "Contact / Extra Info", "Issue / Feedback", "Device & Android", "Hash"]);
    }
    sheet.appendRow([
      data.timestampIso || new Date().toISOString(),
      data.category || "FEEDBACK",
      data.name || "Anonymous",
      data.contactOrExtraInfo || "-",
      data.message || "",
      data.deviceInfo || "",
      data.contentHash || ""
    ]);
    return ContentService.createTextOutput(JSON.stringify({status: "ok"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
    """.trimIndent()
}
