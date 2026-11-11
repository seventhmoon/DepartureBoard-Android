package com.androidfung.departureboard.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a local receipt log of delayed (`>= 15m`) or cancelled journeys so commuters can
 * file Delay Repay / Service Delay Refund claims later when they have Wi-Fi, even after the
 * live departure has disappeared from the station board.
 */
object DelayRepayReceiptLog {

    private const val PREFS_NAME = "delay_repay_receipt_log"
    private const val KEY_RECEIPTS_JSON = "receipts_json"
    private const val MAX_RECEIPTS = 25

    private val DAY_KEY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private const val DATE_LABEL_PATTERN = "dd MMM HH:mm"

    data class DelayRepayReceipt(
        val id: String,
        val timestampMs: Long,
        val dateLabel: String,
        val stationName: String,
        val destinationName: String,
        val lineName: String,
        val trainCode: String?,
        val delayMinutes: Int,
        val isCancelled: Boolean,
        val badgeLabel: String,
        val compensationSummary: String,
        val claimUrl: String
    )

    private val _receiptsFlow = MutableStateFlow<List<DelayRepayReceipt>>(emptyList())
    val receiptsFlow: StateFlow<List<DelayRepayReceipt>> = _receiptsFlow.asStateFlow()

    fun loadReceipts(context: Context): List<DelayRepayReceipt> {
        return runCatching {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = sp.getString(KEY_RECEIPTS_JSON, null)
            val parsed = if (!raw.isNullOrBlank()) parseReceiptsJson(raw) else emptyList()
            _receiptsFlow.value = parsed
            parsed
        }.getOrElse { _receiptsFlow.value }
    }

    fun recordReceipt(
        context: Context,
        departureId: String,
        stationName: String,
        destinationName: String,
        lineName: String,
        trainCode: String?,
        delayMinutes: Int,
        isCancelled: Boolean,
        badgeLabel: String,
        compensationSummary: String
    ) {
        val current = loadReceipts(context)
        val nowMs = System.currentTimeMillis()
        val zonedNow = Instant.ofEpochMilli(nowMs).atZone(ZoneId.of("Europe/London"))
        val dayKey = DAY_KEY_FORMATTER.format(zonedNow)
        val receiptId = "${dayKey}_${departureId}_${lineName}_${destinationName}"
        val dateLabel = DateTimeFormatter.ofPattern(DATE_LABEL_PATTERN, Locale.getDefault()).format(zonedNow)
        val claimUrl = resolveOperatorClaimUrl(lineName)

        val newReceipt = DelayRepayReceipt(
            id = receiptId,
            timestampMs = nowMs,
            dateLabel = dateLabel,
            stationName = stationName,
            destinationName = destinationName,
            lineName = lineName,
            trainCode = trainCode,
            delayMinutes = delayMinutes,
            isCancelled = isCancelled,
            badgeLabel = badgeLabel,
            compensationSummary = compensationSummary,
            claimUrl = claimUrl
        )

        val updated = (listOf(newReceipt) + current.filterNot { it.id == receiptId }).take(MAX_RECEIPTS)
        _receiptsFlow.value = updated
        saveReceipts(context, updated)
    }

    fun deleteReceipt(context: Context, receiptId: String) {
        val updated = loadReceipts(context).filterNot { it.id == receiptId }
        _receiptsFlow.value = updated
        saveReceipts(context, updated)
    }

    fun clearAllReceipts(context: Context) {
        _receiptsFlow.value = emptyList()
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                remove(KEY_RECEIPTS_JSON)
            }
        }
    }

    fun copyReceiptToClipboard(context: Context, receipt: DelayRepayReceipt) {
        runCatching {
            val headcodePart = receipt.trainCode?.takeIf { it.isNotBlank() }?.let { " (#$it)" }.orEmpty()
            val statusPart = if (receipt.isCancelled) "Cancelled" else "+${receipt.delayMinutes}m delay"
            val text = "${receipt.dateLabel} • ${receipt.lineName}$headcodePart: ${receipt.stationName} → ${receipt.destinationName} ($statusPart) — ${receipt.compensationSummary}. Claim: ${receipt.claimUrl}"
            val cm = context.getSystemService<ClipboardManager>()
            cm?.setPrimaryClip(ClipData.newPlainText("Delay Repay Receipt", text))
        }
    }

    fun openClaimPortal(context: Context, receipt: DelayRepayReceipt) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, receipt.claimUrl.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun resolveOperatorClaimUrl(lineOrOperator: String): String {
        val key = lineOrOperator.trim().lowercase()
        return when {
            key.contains("northern rail") || key.contains("northernrailway") ||
                key.contains("northern trains") || key.contains("arriva rail north") || key == "nt" ->
                "https://www.northernrailway.co.uk/travel/delay-repay"
            key.contains("great northern") || key == "gn" ->
                "https://www.greatnorthernrail.com/help-and-support/journey-problems/delay-repay-compensation"
            key.contains("elizabeth") || key.contains("overground") || key.contains("dlr") ||
                key.contains("bakerloo") || key.contains("central") || key.contains("circle") ||
                key.contains("district") || key.contains("hammersmith") || key.contains("jubilee") ||
                key.contains("metropolitan") || key.contains("northern line") || key == "northern" ||
                key.contains("piccadilly") || key.contains("victoria") || key.contains("waterloo") || key.contains("tfl") ->
                "https://tfl.gov.uk/fares/refunds-and-replacements"
            key.contains("avanti") -> "https://www.avantiwestcoast.co.uk/help-and-support/delay-repay"
            key.contains("lner") -> "https://www.lner.co.uk/support/delay-repay/"
            key.contains("gwr") || key.contains("great western") -> "https://www.gwr.com/help-and-support/refunds-and-compensation/delay-repay"
            key.contains("thameslink") -> "https://www.thameslinkrailway.com/help-and-support/journey-problems/delay-repay-compensation"
            key.contains("southern") -> "https://www.southernrailway.com/help-and-support/journey-problems/delay-repay-compensation"
            key.contains("gatwick") -> "https://www.gatwickexpress.com/help-and-support/journey-problems/delay-repay-compensation"
            key.contains("southeastern") -> "https://www.southeasternrailway.co.uk/help-and-contact/refunds-and-compensation/delay-repay"
            key.contains("south western") || key.contains("swr") -> "https://www.southwesternrailway.com/contact-and-help/refunds-and-compensation/delay-repay"
            key.contains("greater anglia") || key.contains("stansted") -> "https://www.greateranglia.co.uk/about-us/our-performance/delay-repay"
            key.contains("c2c") -> "https://www.c2c-online.co.uk/help-feedback/delay-repay/"
            key.contains("chiltern") -> "https://www.chilternrailways.co.uk/delay-repay"
            key.contains("east midlands") || key.contains("emr") -> "https://www.eastmidlandsrailway.co.uk/help-manage/about-us/delay-repay"
            key.contains("northern") -> "https://www.northernrailway.co.uk/travel/delay-repay"
            key.contains("transpennine") || key.contains("tpe") -> "https://www.tpexpress.co.uk/help/delay-repay-compensation"
            key.contains("scotrail") -> "https://www.scotrail.co.uk/plan-your-journey/refunds-and-compensation/delay-repay"
            key.contains("crosscountry") -> "https://www.crosscountrytrains.co.uk/customer-service/contact-us/delay-repay"
            else -> "https://www.nationalrail.co.uk/help-and-assistance/compensation-and-refunds/"
        }
    }

    private fun saveReceipts(context: Context, receipts: List<DelayRepayReceipt>) {
        runCatching {
            val arr = JSONArray()
            receipts.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("timestampMs", r.timestampMs)
                        put("dateLabel", r.dateLabel)
                        put("stationName", r.stationName)
                        put("destinationName", r.destinationName)
                        put("lineName", r.lineName)
                        put("trainCode", r.trainCode.orEmpty())
                        put("delayMinutes", r.delayMinutes)
                        put("isCancelled", r.isCancelled)
                        put("badgeLabel", r.badgeLabel)
                        put("compensationSummary", r.compensationSummary)
                        put("claimUrl", r.claimUrl)
                    }
                )
            }
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(KEY_RECEIPTS_JSON, arr.toString())
            }
        }
    }

    private fun parseReceiptsJson(raw: String): List<DelayRepayReceipt> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        DelayRepayReceipt(
                            id = obj.optString("id"),
                            timestampMs = obj.optLong("timestampMs"),
                            dateLabel = obj.optString("dateLabel"),
                            stationName = obj.optString("stationName"),
                            destinationName = obj.optString("destinationName"),
                            lineName = obj.optString("lineName"),
                            trainCode = obj.optString("trainCode").takeIf { it.isNotBlank() },
                            delayMinutes = obj.optInt("delayMinutes"),
                            isCancelled = obj.optBoolean("isCancelled"),
                            badgeLabel = obj.optString("badgeLabel"),
                            compensationSummary = obj.optString("compensationSummary"),
                            claimUrl = obj.optString(
                                "claimUrl",
                                "https://www.nationalrail.co.uk/help-and-assistance/compensation-and-refunds/"
                            )
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
