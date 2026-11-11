package com.androidfung.departureboard.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.androidfung.departureboard.wear.haptic.WearNotificationPublisher
import com.androidfung.departureboard.wear.model.WearSnapshotFormatter
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository
import com.androidfung.departureboard.wear.ui.WearDepartureBoardActivity

/**
 * Native Wear OS Watch Face Complication Data Source (`SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`).
 * Displays the user's active tracked train or next concourse departure right on any watch face,
 * and launches [WearDepartureBoardActivity] when tapped.
 */
class WearNextTrainComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        fun requestComplicationUpdate(context: Context) {
            runCatching {
                val appCtx = context.applicationContext
                ComplicationDataSourceUpdateRequester.create(
                    appCtx,
                    ComponentName(appCtx, WearNextTrainComplicationService::class.java)
                ).requestUpdateAll()
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return createComplicationData(
            type = type,
            shortTitle = "Plat 11",
            shortText = "3m",
            longText = "WAT • Plat 11 • 3m",
            progressPercent = 75,
            tapAction = null
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WearCompanionStateRepository.ensureCachedStateLoaded(applicationContext)
        val tracked = WearCompanionStateRepository.trackedJourneyFlow.value
        val boards = WearCompanionStateRepository.stationBoardsFlow.value
        val activeBoard = boards.firstOrNull { it.isAtConcourse } ?: boards.firstOrNull()

        val openIntent = Intent(applicationContext, WearDepartureBoardActivity::class.java).apply {
            if (tracked != null) {
                putExtra(WearNotificationPublisher.EXTRA_OPEN_SPRINT_HUD, true)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            applicationContext,
            401,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (tracked != null) {
            val platTitle = if (tracked.isPlatformTbd) {
                getString(com.androidfung.departureboard.wear.R.string.wear_plat_tbd)
            } else {
                getString(com.androidfung.departureboard.wear.R.string.wear_plat_code, tracked.cleanPlatformCode)
            }
            val mins = (tracked.remainingSeconds / 60).coerceAtLeast(0)
            val shortCountdown = if (tracked.remainingSeconds <= 30) {
                getString(com.androidfung.departureboard.wear.R.string.wear_status_due)
            } else {
                getString(com.androidfung.departureboard.wear.R.string.wear_minutes_short, mins)
            }
            createComplicationData(
                type = request.complicationType,
                shortTitle = platTitle,
                shortText = shortCountdown,
                longText = "$platTitle • ${tracked.destinationName} (${tracked.formattedMmSsCountdown})",
                progressPercent = tracked.progressPercent,
                tapAction = tapPendingIntent
            )
        } else if (activeBoard != null) {
            val snapshot = WearSnapshotFormatter.buildComplicationSnapshot(activeBoard)
            val firstDep = activeBoard.departures.firstOrNull()
            val shortTitle = firstDep?.let {
                if (it.isPlatformTbd) "${activeBoard.stationCrs} TBD"
                else getString(com.androidfung.departureboard.wear.R.string.wear_plat_code, it.cleanPlatformCode)
            } ?: activeBoard.stationCrs
            val shortVal = firstDep?.let {
                if (it.countdownMinutes <= 0) getString(com.androidfung.departureboard.wear.R.string.wear_status_due)
                else getString(com.androidfung.departureboard.wear.R.string.wear_minutes_short, it.countdownMinutes)
            } ?: "--"
            createComplicationData(
                type = request.complicationType,
                shortTitle = shortTitle,
                shortText = shortVal,
                longText = snapshot.shortText,
                progressPercent = snapshot.rangedProgressPercent,
                tapAction = tapPendingIntent
            )
        } else {
            createComplicationData(
                type = request.complicationType,
                shortTitle = "EkiBoard",
                shortText = "--",
                longText = getString(com.androidfung.departureboard.wear.R.string.wear_complication_tap_open),
                progressPercent = 0,
                tapAction = tapPendingIntent
            )
        }
    }

    private fun createComplicationData(
        type: ComplicationType,
        shortTitle: String,
        shortText: String,
        longText: String,
        progressPercent: Int,
        tapAction: PendingIntent?
    ): ComplicationData? {
        val desc = PlainComplicationText.Builder(longText).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortText).build(),
                contentDescription = desc
            )
                .setTitle(PlainComplicationText.Builder(shortTitle).build())
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(longText).build(),
                contentDescription = desc
            )
                .setTitle(PlainComplicationText.Builder(shortTitle).build())
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = progressPercent.coerceIn(0, 100).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = desc
            )
                .setTitle(PlainComplicationText.Builder(shortTitle).build())
                .setText(PlainComplicationText.Builder(shortText).build())
                .setTapAction(tapAction)
                .build()

            else -> null
        }
    }
}
