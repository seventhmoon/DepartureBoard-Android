package com.androidfung.departureboard.util

import android.app.Application
import android.content.Intent
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class CustomTabsLauncherTest {

    @Test
    fun buildCustomTabsIntent_activityContext_configuresPartialSheetAndTheme() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val toolbarColor = Color.parseColor("#112233")

        val customTabsIntent = CustomTabsLauncher.buildCustomTabsIntent(
            context = activity,
            toolbarColor = toolbarColor,
            isDarkTheme = true,
            presentation = CustomTabsLauncher.TabPresentation.PARTIAL_SHEET
        )
        val rawIntent = customTabsIntent.intent

        assertTrue(
            "Expected positive initial activity height for partial Custom Tab",
            CustomTabsIntent.getInitialActivityHeightPx(rawIntent) > 0
        )
        assertEquals(
            CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE,
            rawIntent.getIntExtra(CustomTabsIntent.EXTRA_ACTIVITY_HEIGHT_RESIZE_BEHAVIOR, -1)
        )
        assertEquals(16, CustomTabsIntent.getToolbarCornerRadiusDp(rawIntent))
        assertEquals(
            CustomTabsIntent.SHOW_PAGE_TITLE,
            rawIntent.getIntExtra(CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, -1)
        )
        assertEquals(
            CustomTabsIntent.COLOR_SCHEME_DARK,
            rawIntent.getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1)
        )
        assertEquals(
            toolbarColor,
            CustomTabsIntent.getColorSchemeParams(rawIntent, CustomTabsIntent.COLOR_SCHEME_DARK).toolbarColor
        )
    }

    @Test
    fun buildCustomTabsIntent_nonActivityContext_omitsPartialSheetAndAddsNewTaskFlag() {
        val appContext = ApplicationProvider.getApplicationContext<Application>()

        val customTabsIntent = CustomTabsLauncher.buildCustomTabsIntent(
            context = appContext,
            presentation = CustomTabsLauncher.TabPresentation.PARTIAL_SHEET
        )
        val rawIntent = customTabsIntent.intent

        assertEquals(0, CustomTabsIntent.getInitialActivityHeightPx(rawIntent))
        assertTrue((rawIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun openWebUrl_rejectsBlankAndNonHttpSchemes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertFalse(CustomTabsLauncher.openWebUrl(activity, ""))
        assertFalse(CustomTabsLauncher.openWebUrl(activity, "   "))
        assertFalse(CustomTabsLauncher.openWebUrl(activity, "javascript:alert(1)"))
        assertFalse(CustomTabsLauncher.openWebUrl(activity, "file:///etc/hosts"))
        assertFalse(CustomTabsLauncher.openWebUrl(activity, "geo:51.5072,-0.1276"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun openWebUrl_fallsBackToActionViewWhenNoCustomTabsProviderInstalled() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val url = "https://tfl.gov.uk/plan-a-journey/"

        val launched = CustomTabsLauncher.openWebUrl(activity, url)

        assertTrue(launched)
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(url, started.dataString)
    }
}
