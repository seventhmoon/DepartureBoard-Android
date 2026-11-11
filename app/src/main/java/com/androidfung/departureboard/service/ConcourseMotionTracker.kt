package com.androidfung.departureboard.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Registers hardware-batched Google Play Services [ActivityRecognition] transitions when permitted,
 * feeding [com.androidfung.departureboard.util.ConcourseActivityGate] without continuous GPS drain.
 */
object ConcourseMotionTracker {

    private const val REQUEST_CODE_ACTIVITY_TRANSITION = 4107

    @Volatile
    private var isRegistered = false

    private val _motionUpdatesFlow = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val motionUpdatesFlow: SharedFlow<Int> = _motionUpdatesFlow.asSharedFlow()

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun ensureRegistered(context: Context) {
        if (isRegistered || !hasActivityRecognitionPermission(context)) return
        runCatching {
            val appCtx = context.applicationContext
            val transitions = buildList {
                val trackedTypes = listOf(
                    DetectedActivity.STILL,
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    DetectedActivity.ON_BICYCLE,
                    DetectedActivity.IN_VEHICLE
                )
                for (type in trackedTypes) {
                    add(
                        ActivityTransition.Builder()
                            .setActivityType(type)
                            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                            .build()
                    )
                }
            }
            val request = ActivityTransitionRequest(transitions)
            val intent = Intent(appCtx, ActivityTransitionReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(
                appCtx,
                REQUEST_CODE_ACTIVITY_TRANSITION,
                intent,
                flags
            )
            ActivityRecognition.getClient(appCtx)
                .requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener { isRegistered = true }
        }
    }

    internal fun notifyActivityDetected(activityType: Int) {
        _motionUpdatesFlow.tryEmit(activityType)
    }
}
