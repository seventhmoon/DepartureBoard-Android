package com.androidfung.departureboard.review

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.androidfung.departureboard.BuildConfig
import com.androidfung.departureboard.data.datastore.dataStore
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

/**
 * Manages Google Play In-App Review requests according to Google Play guidelines:
 * - Does not interrupt active or time-critical user flows (e.g. while rushing or checking live trains).
 * - Triggers only for engaged users who have used the app across multiple days and sessions.
 * - Adheres to quota throttling (at least 30-day cooldown between prompts).
 * - Gracefully falls back to opening the Play Store web/app page when manually requested by the user.
 */
class InAppReviewManager(
    private val context: Context,
    private val reviewManager: ReviewManager = if (USE_FAKE_REVIEW_IN_DEBUG && BuildConfig.DEBUG) {
        FakeReviewManager(context)
    } else {
        ReviewManagerFactory.create(context)
    }
) {

    companion object {
        private const val TAG = "InAppReviewManager"
        private const val STORE_URL = "https://play.google.com/store/apps/details?id=com.androidfung.departureboard"

        // Debug flag to test review flows without needing a published Play Store production track
        var USE_FAKE_REVIEW_IN_DEBUG = true

        // Smart trigger thresholds for engaged users
        const val MIN_DAYS_SINCE_FIRST_LAUNCH = 3L
        const val MIN_APP_OPENS = 5
        val COOLDOWN_DAYS_BETWEEN_REVIEWS = TimeUnit.DAYS.toMillis(30)

        private val KEY_FIRST_LAUNCH_TIME = longPreferencesKey("review_first_launch_time")
        private val KEY_APP_OPEN_COUNT = intPreferencesKey("review_app_open_count")
        private val KEY_LAST_REVIEW_TIME = longPreferencesKey("review_last_prompt_time")
        private val KEY_REVIEW_COMPLETED = booleanPreferencesKey("review_flow_completed")

        @Volatile
        @Suppress("StaticFieldLeak")
        private var instance: InAppReviewManager? = null

        fun getInstance(context: Context): InAppReviewManager {
            return instance ?: synchronized(this) {
                instance ?: InAppReviewManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Record an app session launch to track engagement.
     */
    suspend fun recordAppLaunch() {
        try {
            context.dataStore.edit { prefs ->
                val now = System.currentTimeMillis()
                if (prefs[KEY_FIRST_LAUNCH_TIME] == null) {
                    prefs[KEY_FIRST_LAUNCH_TIME] = now
                }
                val count = prefs[KEY_APP_OPEN_COUNT] ?: 0
                prefs[KEY_APP_OPEN_COUNT] = count + 1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recording app launch for reviews", e)
        }
    }

    /**
     * Checks whether the user satisfies the "engaged / active user" conditions to be prompted:
     * - Installed/active for at least 3 days.
     * - Opened the app at least 5 times.
     * - Has saved stations (demonstrating genuine usage).
     * - At least 30 days have elapsed since the last prompt.
     * - Has not already completed the review flow.
     */
    suspend fun shouldTriggerSmartReview(savedStationsCount: Int): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            val completed = prefs[KEY_REVIEW_COMPLETED] ?: false
            if (completed) return false

            val firstLaunchTime = prefs[KEY_FIRST_LAUNCH_TIME] ?: return false
            val openCount = prefs[KEY_APP_OPEN_COUNT] ?: 0
            val lastReviewTime = prefs[KEY_LAST_REVIEW_TIME] ?: 0L

            val now = System.currentTimeMillis()
            val daysSinceFirstLaunch = TimeUnit.MILLISECONDS.toDays(now - firstLaunchTime)
            val timeSinceLastPrompt = now - lastReviewTime

            // Must have installed at least MIN_DAYS_SINCE_FIRST_LAUNCH days ago
            if (daysSinceFirstLaunch < MIN_DAYS_SINCE_FIRST_LAUNCH) return false

            // Must have at least MIN_APP_OPENS opens
            if (openCount < MIN_APP_OPENS) return false

            // Must have at least 1 saved/favorite station
            if (savedStationsCount < 1) return false

            // Must satisfy cooldown
            if (timeSinceLastPrompt < COOLDOWN_DAYS_BETWEEN_REVIEWS) return false

            true
        } catch (e: Exception) {
            Log.w(TAG, "Error evaluating shouldTriggerSmartReview", e)
            false
        }
    }

    /**
     * Attempts to start the Google Play in-app review flow.
     * If [force] is false, it first checks [shouldTriggerSmartReview].
     * If [fallbackToPlayStore] is true and in-app review fails, it opens the Play Store URL.
     */
    suspend fun launchReviewFlow(
        activity: Activity,
        force: Boolean = false,
        savedStationsCount: Int = 0,
        fallbackToPlayStore: Boolean = false
    ) {
        if (!force && !shouldTriggerSmartReview(savedStationsCount)) {
            Log.d(TAG, "Smart review conditions not met. Skipping review flow.")
            return
        }

        try {
            val reviewInfo: ReviewInfo = reviewManager.requestReviewFlow().await()
            val flowTask = reviewManager.launchReviewFlow(activity, reviewInfo)
            flowTask.await()

            // The API does not disclose whether the user submitted a review or dismissed it.
            // In accordance with guidelines, treat completion as a successful prompt attempt.
            recordReviewPrompted(completed = !force)
            Log.d(TAG, "In-app review flow completed.")
        } catch (e: Exception) {
            Log.w(TAG, "In-app review flow encountered error", e)
            if (fallbackToPlayStore) {
                openPlayStore(activity)
            }
        }
    }

    /**
     * Directly opens the Play Store app or browser listing.
     */
    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, STORE_URL.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch Play Store URL", e)
        }
    }

    private suspend fun recordReviewPrompted(completed: Boolean) {
        try {
            context.dataStore.edit { prefs ->
                prefs[KEY_LAST_REVIEW_TIME] = System.currentTimeMillis()
                if (completed) {
                    prefs[KEY_REVIEW_COMPLETED] = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist review prompted state", e)
        }
    }

    /**
     * Reset review state for debug/testing purposes.
     */
    suspend fun resetReviewDataForTesting() {
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(KEY_FIRST_LAUNCH_TIME)
                prefs.remove(KEY_APP_OPEN_COUNT)
                prefs.remove(KEY_LAST_REVIEW_TIME)
                prefs.remove(KEY_REVIEW_COMPLETED)
            }
            Log.d(TAG, "Review state reset for testing.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset review data", e)
        }
    }
}
