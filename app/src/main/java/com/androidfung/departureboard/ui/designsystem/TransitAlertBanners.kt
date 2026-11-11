package com.androidfung.departureboard.ui.designsystem

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.LineBadgeInfo
import com.androidfung.departureboard.data.model.StationServiceHoursInfo
import com.androidfung.departureboard.ui.theme.LocalIsDarkTheme
import com.androidfung.departureboard.util.PlatformBacklogAlert

private val HTTPS_URL_REGEX = Regex("""(https://[^\s]+)""")

/**
 * Standardized Transit Alert Banner Container that enforces identical margins, padding,
 * icon alignment, rounded corners, and typography across all disruption, backlog, and service banners.
 */
@Composable
fun TransitAlertCard(
    iconText: String,
    title: String,
    accentColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bodyContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = if (bodyContent == null) Alignment.CenterVertically else Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .padding(top = if (bodyContent == null) 0.dp else 1.dp),
                contentAlignment = if (bodyContent == null) Alignment.Center else Alignment.TopCenter
            ) {
                Text(
                    text = iconText,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = if (bodyContent == null) Arrangement.Center else Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        ),
                        color = accentColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (trailingContent != null) {
                        trailingContent()
                    }
                }
                if (bodyContent != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    bodyContent()
                }
            }
        }
    }
}

/**
 * Multiple disruptions rollup banner with expand/collapse chevron.
 * Shared across Dashboard cards and Station Detail screen.
 */
@Composable
fun MultipleDisruptionsBanner(
    disruptedBadges: List<LineBadgeInfo>,
    modifier: Modifier = Modifier,
    onAskAiAlternative: ((lineName: String) -> Unit)? = null
) {
    var isDisruptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current
    val hasSevere = disruptedBadges.any { it.statusSeverity <= 6 }

    val summaryAccentColor = if (hasSevere) {
        if (isDark) Color(0xFFFFB4A9) else Color(0xFFBA1A1A)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFC05600)
    }
    val summaryBgColor = if (hasSevere) {
        if (isDark) Color(0xFF930006).copy(alpha = 0.20f) else Color(0xFFFFDAD6).copy(alpha = 0.45f)
    } else {
        if (isDark) Color(0xFFFFA000).copy(alpha = 0.12f) else Color(0xFFFFF3CD).copy(alpha = 0.65f)
    }
    val summaryBorderColor = summaryAccentColor.copy(alpha = if (isDark) 0.45f else 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        TransitAlertCard(
            iconText = if (hasSevere) "✕" else "⚠",
            title = stringResource(R.string.station_detail_disruptions_count, disruptedBadges.size) +
                " (${disruptedBadges.joinToString(", ") { it.displayName }})",
            accentColor = summaryAccentColor,
            backgroundColor = summaryBgColor,
            borderColor = summaryBorderColor,
            onClick = { isDisruptionsExpanded = !isDisruptionsExpanded },
            trailingContent = {
                Icon(
                    imageVector = if (isDisruptionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isDisruptionsExpanded) {
                        stringResource(R.string.line_status_collapse_desc)
                    } else {
                        stringResource(R.string.line_status_expand_desc)
                    },
                    tint = summaryAccentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        AnimatedVisibility(
            visible = isDisruptionsExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                disruptedBadges.forEach { disruptedBadge ->
                    LineDisruptionBanner(
                        badge = disruptedBadge,
                        onAskAiAlternative = onAskAiAlternative
                    )
                }
            }
        }
    }
}

/**
 * Line Disruption Alert Banner with warning icon and reason description.
 * Intelligently converts raw HTTPS disruption URLs into clean, clickable travel alert links.
 * Adapts high-contrast semantic colors for severe disruptions (error red) vs delays/minor issues (high-contrast amber).
 */
@Composable
fun LineDisruptionBanner(
    badge: LineBadgeInfo,
    modifier: Modifier = Modifier,
    onAskAiAlternative: ((lineName: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val isSevere = badge.statusSeverity <= 6

    val accentColor = if (isSevere) {
        if (isDark) Color(0xFFFFB4A9) else Color(0xFFBA1A1A)
    } else {
        if (isDark) Color(0xFFFFB74D) else Color(0xFFC05600)
    }
    val backgroundColor = if (isSevere) {
        if (isDark) Color(0xFF930006).copy(alpha = 0.20f) else Color(0xFFFFDAD6).copy(alpha = 0.45f)
    } else {
        if (isDark) Color(0xFFFFA000).copy(alpha = 0.12f) else Color(0xFFFFF3CD).copy(alpha = 0.65f)
    }
    val borderColor = accentColor.copy(alpha = if (isDark) 0.45f else 0.35f)
    val delayFallback = stringResource(R.string.travel_alert_delays)

    TransitAlertCard(
        iconText = if (isSevere) "✕" else "⚠",
        title = "${badge.displayName}: ${badge.statusDescription ?: delayFallback}",
        accentColor = accentColor,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        modifier = modifier,
        bodyContent = {
            val reason = badge.disruptionReason?.trim()
            if (!reason.isNullOrBlank()) {
                val urlMatch = HTTPS_URL_REGEX.find(reason)
                val url = urlMatch?.value

                if (url != null && reason.startsWith("https://")) {
                    // Entire reason is a URL: show a clean clickable action link
                    Text(
                        text = stringResource(R.string.travel_alert_view_details),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        color = accentColor,
                        modifier = Modifier
                            .clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (_: ActivityNotFoundException) {}
                            }
                            .padding(vertical = 1.dp)
                    )
                } else if (url != null) {
                    // Mixed text and URL: display clean text with a trailing link button
                    val textWithoutUrl = reason.replace(url, "").trim()
                    if (textWithoutUrl.isNotBlank()) {
                        Text(
                            text = textWithoutUrl,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(R.string.travel_alert_more_details),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        color = accentColor,
                        modifier = Modifier
                            .clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (_: ActivityNotFoundException) {}
                            }
                            .padding(top = 2.dp)
                    )
                } else {
                    // Plain text disruption description
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onAskAiAlternative != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .clickable { onAskAiAlternative(badge.displayName) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.travel_alert_ask_ai_alternatives),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = accentColor
                    )
                }
            }
        }
    )
}

/**
 * Banner alerting commuters when a rapid transit platform experiences severe delay / headway backlog.
 * Uses the unified TransitAlertCard template with high-contrast amber styling and matching alignment.
 */
@Composable
fun PlatformBacklogBanner(
    alert: PlatformBacklogAlert,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val accentColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFC05600)
    val backgroundColor = if (isDark) Color(0xFFFFA000).copy(alpha = 0.12f) else Color(0xFFFFF3CD).copy(alpha = 0.65f)
    val borderColor = accentColor.copy(alpha = if (isDark) 0.45f else 0.35f)

    TransitAlertCard(
        iconText = "⏱️",
        title = stringResource(R.string.platform_backlog_banner_title, alert.lineName),
        accentColor = accentColor,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        modifier = modifier,
        bodyContent = {
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * Banner alerting commuters when the final train of the night ("Last Train Home") is approaching
 * or when 24-hour Night Tube / Night Bus service is active.
 * Uses the unified TransitAlertCard template with matching padding and alignment.
 */
@Composable
fun LastTrainServiceBanner(
    serviceHoursInfo: StationServiceHoursInfo,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val isNightService = serviceHoursInfo.isNightServiceActive
    val accentColor = if (isNightService) {
        if (isDark) Color(0xFFB388FF) else Color(0xFF5E35B1)
    } else {
        if (isDark) Color(0xFFFFB300) else Color(0xFF8C5000)
    }
    val bgColor = if (isNightService) {
        if (isDark) Color(0xFF311B92).copy(alpha = 0.25f) else Color(0xFFEDE7F6)
    } else {
        if (isDark) Color(0xFFFFB300).copy(alpha = 0.14f) else Color(0xFFFFF3CD).copy(alpha = 0.65f)
    }
    val borderColor = accentColor.copy(alpha = if (isDark) 0.45f else 0.35f)

    val titleText = if (isNightService) {
        stringResource(R.string.night_service_banner_title)
    } else {
        stringResource(
            R.string.last_train_banner_title,
            serviceHoursInfo.lastTrainTime ?: "--:--"
        )
    }

    TransitAlertCard(
        iconText = if (isNightService) "🌙" else "🏁",
        title = titleText,
        accentColor = accentColor,
        backgroundColor = bgColor,
        borderColor = borderColor,
        modifier = modifier,
        bodyContent = {
            val subtitleParts = buildList {
                if (isNightService) {
                    add(stringResource(R.string.night_service_banner_subtitle))
                } else {
                    serviceHoursInfo.minutesUntilLastTrain?.let { mins ->
                        add(stringResource(R.string.last_train_banner_countdown, mins))
                    }
                    serviceHoursInfo.firstTrainTime?.let { first ->
                        add(stringResource(R.string.last_train_banner_first_train, first))
                    }
                }
            }
            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/**
 * High-visibility Step-Free Access / Lift Outage Guard banner powered by live StopPoint disruption feeds.
 */
@Composable
fun StepFreeOutageBanner(
    alerts: List<com.androidfung.departureboard.data.model.TransitAlert>,
    modifier: Modifier = Modifier
) {
    if (alerts.isEmpty()) return
    val isDark = LocalIsDarkTheme.current
    val accentColor = if (isDark) Color(0xFFFFB4A9) else Color(0xFFBA1A1A)
    val bgColor = if (isDark) Color(0xFF930006).copy(alpha = 0.22f) else Color(0xFFFFDAD6).copy(alpha = 0.55f)
    val borderColor = accentColor.copy(alpha = if (isDark) 0.50f else 0.40f)
    val primaryAlert = alerts.first()

    TransitAlertCard(
        iconText = "♿",
        title = stringResource(R.string.step_free_lift_outage_title),
        accentColor = accentColor,
        backgroundColor = bgColor,
        borderColor = borderColor,
        modifier = modifier,
        bodyContent = {
            Text(
                text = primaryAlert.descriptionText?.takeIf { it.isNotBlank() } ?: primaryAlert.headerText,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
