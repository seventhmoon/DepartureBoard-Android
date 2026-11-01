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
    const val FREE_MAX_DAILY_AI_QUERIES = 10

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
    @Deprecated(
        message = "Use PRODUCT_SUBSCRIPTION_ID instead",
        replaceWith = ReplaceWith("PRODUCT_SUBSCRIPTION_ID"),
    )
    const val PRODUCT_SUBS_ANNUAL = PRODUCT_SUBSCRIPTION_ID

    @Deprecated(
        message = "Use PRODUCT_SUBSCRIPTION_ID instead",
        replaceWith = ReplaceWith("PRODUCT_SUBSCRIPTION_ID"),
    )
    const val PRODUCT_SUBS_MONTHLY = PRODUCT_SUBSCRIPTION_ID
}

enum class UserPlan(
    val maxStations: Int,
    val maxWidgets: Int,
    val maxDailyAiQueries: Int,
) {
    FREE(
        maxStations = SubscriptionTier.FREE_MAX_STATIONS,
        maxWidgets = SubscriptionTier.FREE_MAX_WIDGETS,
        maxDailyAiQueries = SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES,
    ),
    PRO(
        maxStations = SubscriptionTier.PRO_MAX_STATIONS,
        maxWidgets = SubscriptionTier.PRO_MAX_WIDGETS,
        maxDailyAiQueries = SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES,
    ),
    MAX(
        maxStations = SubscriptionTier.MAX_MAX_STATIONS,
        maxWidgets = SubscriptionTier.MAX_MAX_WIDGETS,
        maxDailyAiQueries = SubscriptionTier.MAX_MAX_DAILY_AI_QUERIES,
    );

    val isPaidOrAbove: Boolean
        get() = ((this == PRO) || (this == MAX))
}

