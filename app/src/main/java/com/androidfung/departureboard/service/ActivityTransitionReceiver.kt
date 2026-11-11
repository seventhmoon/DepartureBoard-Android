package com.androidfung.departureboard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androidfung.departureboard.util.ConcourseActivityGate
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult

/**
 * Receives low-power Play Services Activity Recognition & Transition updates and forwards them
 * to [ConcourseActivityGate] and [ConcourseMotionTracker] so concourse polling adapts immediately
 * when the user starts walking or sprinting toward a station.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            for (event in result.transitionEvents) {
                if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    ConcourseActivityGate.recordHardwareActivity(
                        activityType = event.activityType,
                        confidence = 90
                    )
                    ConcourseMotionTracker.notifyActivityDetected(event.activityType)
                }
            }
        } else if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val mostProbable = result.mostProbableActivity
            ConcourseActivityGate.recordHardwareActivity(
                activityType = mostProbable.type,
                confidence = mostProbable.confidence
            )
            ConcourseMotionTracker.notifyActivityDetected(mostProbable.type)
        }
    }
}
