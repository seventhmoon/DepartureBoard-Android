package com.androidfung.departureboard.wear.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.androidfung.departureboard.wear.model.WearDepartureItem

/**
 * Wrist Haptic Platform Announcer for Wear OS.
 *
 * When a commuter is waiting in a major station concourse (such as Euston, Waterloo, or Paddington)
 * watching a train with `Plat TBD`, this announcer detects the exact transition to a confirmed platform
 * (`TBD -> Plat X`) or a last-minute platform alteration (`Plat A -> Plat B`) and fires a distinct
 * wrist vibration pattern.
 */
object WearConcourseHapticAnnouncer {

    enum class WristAlertType {
        PLATFORM_ANNOUNCED,
        PLATFORM_CHANGED,
        ALIGHT_NEXT_STOP
    }

    data class WristHapticEvent(
        val type: WristAlertType,
        val departureId: String,
        val headline: String,
        val detail: String
    )

    /**
     * Compares the previous state of a watched departure with the newly polled departure
     * and returns a [WristHapticEvent] if the platform was just announced or altered.
     */
    fun evaluatePlatformTransition(
        previous: WearDepartureItem?,
        current: WearDepartureItem,
        isWatched: Boolean
    ): WristHapticEvent? {
        if (!isWatched || previous == null) return null

        val wasTbd = previous.isPlatformTbd
        val isNowConfirmed = !current.isPlatformTbd

        if (wasTbd && isNowConfirmed) {
            return WristHapticEvent(
                type = WristAlertType.PLATFORM_ANNOUNCED,
                departureId = current.id,
                headline = "Plat ${current.platformCode} Announced!",
                detail = "${current.destination} (${current.scheduledTime}) is boarding at Platform ${current.platformCode}"
            )
        }

        if (!wasTbd && isNowConfirmed && !previous.platformCode.equals(current.platformCode, ignoreCase = true)) {
            return WristHapticEvent(
                type = WristAlertType.PLATFORM_CHANGED,
                departureId = current.id,
                headline = "Platform Changed → Plat ${current.platformCode}",
                detail = "${current.destination} moved from Plat ${previous.platformCode} to Plat ${current.platformCode}"
            )
        }

        return null
    }

    /**
     * Triggers a sharp wrist haptic pulse on the watch so the user feels the platform announcement
     * while walking across the concourse.
     */
    fun triggerWristPulse(context: Context, type: WristAlertType = WristAlertType.PLATFORM_ANNOUNCED) {
        try {
            val pattern = when (type) {
                WristAlertType.PLATFORM_ANNOUNCED -> longArrayOf(0L, 200L, 80L, 280L)
                WristAlertType.PLATFORM_CHANGED -> longArrayOf(0L, 160L, 70L, 160L, 70L, 260L)
                WristAlertType.ALIGHT_NEXT_STOP -> longArrayOf(0L, 180L, 100L, 240L)
            }
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService<VibratorManager>()
                vm?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService<Vibrator>()
                vibrator?.vibrate(effect)
            }
        } catch (_: Exception) {
        }
    }
}
