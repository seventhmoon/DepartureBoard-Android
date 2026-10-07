package com.androidfung.departureboard.billing

/**
 * Defines subscription tiers and enforced quota limits for Prompt Departure.
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
    PRO;

    val maxStations: Int
        get() = if (this == PRO) SubscriptionTier.PRO_MAX_STATIONS else SubscriptionTier.FREE_MAX_STATIONS

    val maxWidgets: Int
        get() = if (this == PRO) SubscriptionTier.PRO_MAX_WIDGETS else SubscriptionTier.FREE_MAX_WIDGETS

    val maxDailyAiQueries: Int
        get() = if (this == PRO) SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES else SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES
}
