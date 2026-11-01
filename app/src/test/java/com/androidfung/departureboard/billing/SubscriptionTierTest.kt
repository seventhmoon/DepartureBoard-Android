package com.androidfung.departureboard.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionTierTest {

    @Test
    fun testFreeTierDailyAiQueriesLimit() {
        assertEquals(10, SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES)
        assertEquals(10, UserPlan.FREE.maxDailyAiQueries)
    }

    @Test
    fun testProTierDailyAiQueriesLimit() {
        assertEquals(100, SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES)
        assertEquals(100, UserPlan.PRO.maxDailyAiQueries)
    }
}
