package com.androidfung.departureboard.billing

/**
 * Defines subscription tiers and enforced quota limits for EkiBoard.
 * Both Free and Pro tiers have strict caps to safeguard against excessive
 * battery drain and protect external transit API rate limits.
 */
object SubscriptionTier {

    // Free Tier Quota Limits (Option A: Balanced / Growth)
    const val FREE_MAX_STATIONS = 3
    const val FREE_MAX_WIDGETS = 2
    const val FREE_MAX_DAILY_AI_QUERIES = 3

    // Pro Tier Quota Limits (Option A: Capped to protect API rate limits & battery)
    const val PRO_MAX_STATIONS = 15
    const val PRO_MAX_WIDGETS = 5
    const val PRO_MAX_DAILY_AI_QUERIES = 100

    // Max Tier Quota Limits (Debug / Internal testing only - virtually unlimited)
    const val MAX_MAX_STATIONS = 50
    const val MAX_MAX_WIDGETS = 20
    const val MAX_MAX_DAILY_AI_QUERIES = 9999

    // Google Play Billing Product & Base Plan IDs
    const val PRODUCT_SUBSCRIPTION_ID = "pro_annual"
    const val BASE_PLAN_ANNUAL = "annual-auto-renewing"
    const val BASE_PLAN_MONTHLY = "monthly"

    // Backward-compatibility aliases
    const val PRODUCT_SUBS_ANNUAL = PRODUCT_SUBSCRIPTION_ID
    const val PRODUCT_SUBS_MONTHLY = PRODUCT_SUBSCRIPTION_ID
}

enum class UserPlan {
    FREE,
    PRO,
    MAX;

    val isPaidOrAbove: Boolean
        get() = this == PRO || this == MAX

    val maxStations: Int
        get() = when (this) {
            MAX -> SubscriptionTier.MAX_MAX_STATIONS
            PRO -> SubscriptionTier.PRO_MAX_STATIONS
            FREE -> SubscriptionTier.FREE_MAX_STATIONS
        }

    val maxWidgets: Int
        get() = when (this) {
            MAX -> SubscriptionTier.MAX_MAX_WIDGETS
            PRO -> SubscriptionTier.PRO_MAX_WIDGETS
            FREE -> SubscriptionTier.FREE_MAX_WIDGETS
        }

    val maxDailyAiQueries: Int
        get() = when (this) {
            MAX -> SubscriptionTier.MAX_MAX_DAILY_AI_QUERIES
            PRO -> SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES
            FREE -> SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES
        }
}
