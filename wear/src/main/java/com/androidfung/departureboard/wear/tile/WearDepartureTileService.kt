package com.androidfung.departureboard.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.androidfung.departureboard.wear.haptic.WearNotificationPublisher
import com.androidfung.departureboard.wear.model.WearDepartureItem
import com.androidfung.departureboard.wear.model.WearStationBoard
import com.androidfung.departureboard.wear.model.WearTrackedJourneyState
import com.androidfung.departureboard.wear.sync.WearCompanionStateRepository
import com.androidfung.departureboard.wear.ui.WearDepartureBoardActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Native Wear OS Tile ("Next Departures & Live Sprint Tracker") allowing commuters to swipe
 * left/right from their watch face for a 0.5-second glance at:
 * - Their actively tracked train (`MM:SS` countdown, Platform badge, and Target Alighting Stop), OR
 * - The next 3 departures from their nearest concourse-geofenced or primary pinned station.
 *
 * Tapping anywhere on the Tile opens [WearDepartureBoardActivity] (directly into the Sprint HUD if tracking).
 */
class WearDepartureTileService : TileService() {

    companion object {
        private const val RESOURCES_VERSION = "1"
        const val CLICKABLE_ID_REFRESH_TILE = "refresh_wear_tile"
        private val tileExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

        fun requestTileUpdate(context: Context) {
            runCatching {
                getUpdater(context.applicationContext)
                    .requestUpdate(WearDepartureTileService::class.java)
            }
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> {
        val appCtx = applicationContext
        return Futures.submit(
            java.util.concurrent.Callable {
                WearCompanionStateRepository.ensureCachedStateLoaded(appCtx)
                if (requestParams.currentState.lastClickableId == CLICKABLE_ID_REFRESH_TILE) {
                    WearCompanionStateRepository.requestPhoneSync(appCtx)
                }
                val nowMs = System.currentTimeMillis()
                val tracked = WearCompanionStateRepository.trackedJourneyFlow.value
                    ?.takeUnless { it.isStale(nowMs) }
                val boards = WearCompanionStateRepository.stationBoardsFlow.value
                val activeBoard = boards.firstOrNull { it.isAtConcourse } ?: boards.firstOrNull()

                val rootElement = buildTileLayout(
                    context = appCtx,
                    tracked = tracked,
                    board = activeBoard,
                    nowEpochMs = nowMs
                )

                Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setFreshnessIntervalMillis(30_000L)
                    .setTileTimeline(
                        Timeline.Builder()
                            .addTimelineEntry(
                                TimelineEntry.Builder()
                                    .setLayout(
                                        Layout.Builder()
                                            .setRoot(rootElement)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            },
            tileExecutor
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<Resources> {
        return Futures.immediateFuture(
            Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildTileLayout(
        context: Context,
        tracked: WearTrackedJourneyState?,
        board: WearStationBoard?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): LayoutElement {
        val openActivityClickable = Clickable.Builder()
            .setId("open_wear_board")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(context.packageName)
                            .setClassName(WearDepartureBoardActivity::class.java.name)
                            .apply {
                                if (tracked != null) {
                                    addKeyToExtraMapping(
                                        WearNotificationPublisher.EXTRA_OPEN_SPRINT_HUD,
                                        ActionBuilders.booleanExtra(true)
                                    )
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .build()

        val refreshClickable = Clickable.Builder()
            .setId(CLICKABLE_ID_REFRESH_TILE)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        val refreshPill = Box.Builder()
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF1E2630.toInt()))
                            .setCorner(Corner.Builder().setRadius(dp(10f)).build())
                            .build()
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(7f))
                            .setEnd(dp(7f))
                            .setTop(dp(2f))
                            .setBottom(dp(2f))
                            .build()
                    )
                    .setClickable(refreshClickable)
                    .build()
            )
            .addContent(
                Text.Builder()
                    .setText("↻")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(0xFF81D4FA.toInt()))
                            .build()
                    )
                    .build()
            )
            .build()

        val columnBuilder = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (tracked != null) {
            val platText = if (tracked.isPlatformTbd) {
                context.getString(com.androidfung.departureboard.wear.R.string.wear_plat_tbd)
            } else {
                context.getString(com.androidfung.departureboard.wear.R.string.wear_plat_code, tracked.cleanPlatformCode)
            }
            columnBuilder.addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText(context.getString(com.androidfung.departureboard.wear.R.string.wear_tile_live_tracking, platText))
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFF00E676.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(refreshPill)
                    .build()
            )
            columnBuilder.addContent(Spacer.Builder().setHeight(dp(4f)).build())
            columnBuilder.addContent(
                Text.Builder()
                    .setText(tracked.effectiveMmSsCountdown(nowEpochMs))
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(24f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
            columnBuilder.addContent(
                Text.Builder()
                    .setText("${tracked.destinationName} (${tracked.lineName})")
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(12f))
                            .setColor(argb(0xFF81D4FA.toInt()))
                            .build()
                    )
                    .build()
            )
            val alightInfo = tracked.targetAlightStopName?.takeIf { it.isNotBlank() }?.let { stop ->
                val stopsStr = tracked.stopsAway?.let {
                    context.resources.getQuantityString(com.androidfung.departureboard.wear.R.plurals.wear_stops_suffix, it, it)
                }.orEmpty()
                context.getString(com.androidfung.departureboard.wear.R.string.wear_alight_target, stop, stopsStr)
            } ?: (tracked.currentLocation ?: context.getString(com.androidfung.departureboard.wear.R.string.wear_tile_tap_hud))
            columnBuilder.addContent(Spacer.Builder().setHeight(dp(4f)).build())
            columnBuilder.addContent(
                Text.Builder()
                    .setText(alightInfo)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(10f))
                            .setColor(argb(0xFFFFB74D.toInt()))
                            .build()
                    )
                    .build()
            )
        } else if (board != null) {
            val headerPrefix = if (board.isAtConcourse) "📍 " else "🚆 "
            columnBuilder.addContent(
                Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText("$headerPrefix${board.compactStationName} (${board.stationCrs})")
                            .setMaxLines(1)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFF4FC3F7.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(refreshPill)
                    .build()
            )
            columnBuilder.addContent(Spacer.Builder().setHeight(dp(4f)).build())

            board.departures.take(3).forEach { dep ->
                columnBuilder.addContent(buildDepartureTileRow(dep, nowEpochMs))
                columnBuilder.addContent(Spacer.Builder().setHeight(dp(3f)).build())
            }
        } else {
            columnBuilder.addContent(
                Text.Builder()
                    .setText("EkiBoard Wear")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(14f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(0xFFFFFFFF.toInt()))
                            .build()
                    )
                    .build()
            )
        }

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(Background.Builder().setColor(argb(0xFF000000.toInt())).build())
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(14f))
                            .setEnd(dp(14f))
                            .setTop(dp(16f))
                            .setBottom(dp(16f))
                            .build()
                    )
                    .setClickable(openActivityClickable)
                    .build()
            )
            .addContent(columnBuilder.build())
            .build()
    }

    private fun buildDepartureTileRow(
        dep: WearDepartureItem,
        nowEpochMs: Long = System.currentTimeMillis()
    ): LayoutElement {
        val platLabel = if (dep.isPlatformTbd) "TBD" else "P${dep.cleanPlatformCode}"
        val effectiveMins = dep.effectiveCountdownMinutes(nowEpochMs)
        val minsLabel = when {
            dep.isStale(nowEpochMs) -> "--"
            effectiveMins <= 0 -> "Due"
            else -> "${effectiveMins}m"
        }
        val platColor = if (dep.isPlatformTbd) 0xFFFFB74D.toInt() else 0xFF00E676.toInt()

        return Box.Builder()
            .setWidth(expand())
            .setModifiers(
                Modifiers.Builder()
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF14161A.toInt()))
                            .setCorner(Corner.Builder().setRadius(dp(8f)).build())
                            .build()
                    )
                    .setPadding(
                        Padding.Builder()
                            .setStart(dp(8f))
                            .setEnd(dp(8f))
                            .setTop(dp(4f))
                            .setBottom(dp(4f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                Row.Builder()
                    .setWidth(expand())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(
                        Text.Builder()
                            .setText(platLabel)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(platColor))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(dep.destination)
                            .setMaxLines(1)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(Spacer.Builder().setWidth(dp(6f)).build())
                    .addContent(
                        Text.Builder()
                            .setText(minsLabel)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFF4FC3F7.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
