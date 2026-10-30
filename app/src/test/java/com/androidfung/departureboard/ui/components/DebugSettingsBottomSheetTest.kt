package com.androidfung.departureboard.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.UserPlan
import com.androidfung.departureboard.data.model.TransitRegion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebugSettingsBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class TestBillingDataSource : BillingDataSource {
        override val userPlanFlow = MutableStateFlow(UserPlan.FREE)
        override val isProFlow = MutableStateFlow(false)
        override val availableProducts = MutableStateFlow(emptyList<com.android.billingclient.api.ProductDetails>())
        override val billingError = MutableStateFlow<String?>(null)
        override fun queryAvailableProducts() {}
        override fun refreshPurchases(onResult: ((Boolean, String) -> Unit)?) {}
        override fun launchPurchaseFlow(
            activity: android.app.Activity,
            productDetails: com.android.billingclient.api.ProductDetails,
            basePlanId: String?
        ): Boolean = true
        override suspend fun tryConsumeAiQuery(): Boolean = true
        override suspend fun getRemainingAiQueries(): Int = 42
        override fun setDebugPro(enabled: Boolean) {}
        override fun setDebugPlan(plan: UserPlan) {}
    }

    @Test
    fun testDeveloperOptionsRendersHeadersAndSections() {
        composeTestRule.setContent {
            DebugSettingsBody(
                onDismissRequest = {},
                userPlan = UserPlan.FREE,
                onSelectPlan = {},
                selectedAiModel = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
                onSelectAiModel = {},
                selectedRegion = TransitRegion.GB,
                onSelectRegion = {},
                isHkRegionEnabled = true,
                onToggleHkRegionEnabled = {},
                remainingAiQueries = 3,
                onResetStations = {},
                onTriggerInAppReview = {},
                onResetInAppReviewData = {}
            )
        }

        composeTestRule.onNodeWithText("Debug Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("SUBSCRIPTION PLAN").assertIsDisplayed()
    }

    @Test
    fun testPlanSelectionInvokesCallback() {
        var selectedPlan: UserPlan? = null

        composeTestRule.setContent {
            DebugSettingsBody(
                onDismissRequest = {},
                userPlan = UserPlan.FREE,
                onSelectPlan = { selectedPlan = it },
                selectedAiModel = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
                onSelectAiModel = {},
                selectedRegion = TransitRegion.GB,
                onSelectRegion = {},
                isHkRegionEnabled = false,
                onToggleHkRegionEnabled = {},
                remainingAiQueries = 3,
                onResetStations = {},
                onTriggerInAppReview = {},
                onResetInAppReviewData = {}
            )
        }

        // Perform click on PRO Plan row
        composeTestRule.onNodeWithText("PRO Plan").performScrollTo().performClick()
        assertEquals(UserPlan.PRO, selectedPlan)
    }

    @Test
    fun testHkRegionSelectionCallback() {
        var selectedRegion: TransitRegion? = null

        composeTestRule.setContent {
            DebugSettingsBody(
                onDismissRequest = {},
                userPlan = UserPlan.FREE,
                onSelectPlan = {},
                selectedAiModel = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
                onSelectAiModel = {},
                selectedRegion = TransitRegion.GB,
                onSelectRegion = { selectedRegion = it },
                isHkRegionEnabled = true,
                onToggleHkRegionEnabled = {},
                remainingAiQueries = 3,
                onResetStations = {},
                onTriggerInAppReview = {},
                onResetInAppReviewData = {}
            )
        }

        composeTestRule.onNodeWithText("Hong Kong (HK)").performScrollTo().performClick()
        assertEquals(TransitRegion.HK, selectedRegion)
    }

    @Test
    fun testResetStationsTrigger() {
        var resetClicked = false

        composeTestRule.setContent {
            DebugSettingsBody(
                onDismissRequest = {},
                userPlan = UserPlan.FREE,
                onSelectPlan = {},
                selectedAiModel = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
                onSelectAiModel = {},
                selectedRegion = TransitRegion.GB,
                onSelectRegion = {},
                isHkRegionEnabled = false,
                onToggleHkRegionEnabled = {},
                remainingAiQueries = 3,
                onResetStations = { resetClicked = true },
                onTriggerInAppReview = {},
                onResetInAppReviewData = {}
            )
        }

        composeTestRule.onNodeWithText("Reset Saved Stations").performScrollTo().performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun testOfflineCacheAndDynamicModulesRendered() {
        composeTestRule.setContent {
            DebugSettingsBody(
                onDismissRequest = {},
                userPlan = UserPlan.FREE,
                onSelectPlan = {},
                selectedAiModel = com.androidfung.departureboard.ai.AiModelType.DEFAULT,
                onSelectAiModel = {},
                selectedRegion = TransitRegion.GB,
                onSelectRegion = {},
                isHkRegionEnabled = false,
                onToggleHkRegionEnabled = {},
                remainingAiQueries = 3,
                onResetStations = {},
                onTriggerInAppReview = {},
                onResetInAppReviewData = {}
            )
        }

        composeTestRule.onNodeWithText("Clear Offline HTTP Cache").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("DYNAMIC FEATURE MODULES").performScrollTo().assertIsDisplayed()
    }
}
