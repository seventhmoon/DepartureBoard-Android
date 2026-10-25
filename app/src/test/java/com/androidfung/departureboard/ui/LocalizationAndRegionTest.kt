package com.androidfung.departureboard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.DefaultStations
import com.androidfung.departureboard.data.model.HkDefaultStations
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.test.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalizationAndRegionTest {

    private fun getLocalizedContext(locale: Locale): Context {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    @Test
    fun testEnglishStrings() {
        val context = getLocalizedContext(Locale.ENGLISH)

        assertEquals("United Kingdom", context.getString(R.string.region_uk))
        assertEquals("Hong Kong", context.getString(R.string.region_hk))
        assertEquals("Due", context.getString(R.string.status_due))
        assertEquals("On time", context.getString(R.string.status_on_time))
        assertEquals("Delayed", context.getString(R.string.status_delayed))
        assertEquals("Cancelled", context.getString(R.string.status_cancelled))
        assertEquals("Search station, line, or stop…", context.getString(R.string.search_hint))
        assertEquals("Plat 3", context.getString(R.string.platform_label, "3"))
        assertEquals("Developer Options", context.getString(R.string.debug_settings_title))
        assertEquals("Switch Region", context.getString(R.string.switch_region_title))
    }

    @Test
    fun testTraditionalChineseStrings() {
        val context = getLocalizedContext(Locale.TRADITIONAL_CHINESE)

        assertEquals("英國", context.getString(R.string.region_uk))
        assertEquals("香港", context.getString(R.string.region_hk))
        assertEquals("即將抵達", context.getString(R.string.status_due))
        assertEquals("準時", context.getString(R.string.status_on_time))
        assertEquals("延誤", context.getString(R.string.status_delayed))
        assertEquals("取消", context.getString(R.string.status_cancelled))
        assertEquals("搜尋車站、路線或巴士站…", context.getString(R.string.search_hint))
        assertEquals("3 號月台", context.getString(R.string.platform_label, "3"))
        assertEquals("開發者選項", context.getString(R.string.debug_settings_title))
        assertEquals("切換地區", context.getString(R.string.switch_region_title))
    }

    @Test
    fun testTransitRegionPropertiesAndDefaults() {
        // Verify GB properties
        val gb = TransitRegion.GB
        assertEquals("GB", gb.id)
        assertEquals("GB", gb.code)
        assertEquals(ZoneId.of("Europe/London"), gb.timeZone)
        assertEquals(R.string.region_uk, gb.displayNameRes)

        // Verify HK properties
        val hk = TransitRegion.HK
        assertEquals("HK", hk.id)
        assertEquals("HK", hk.code)
        assertEquals(ZoneId.of("Asia/Hong_Kong"), hk.timeZone)
        assertEquals(R.string.region_hk, hk.displayNameRes)

        // Verify default stations for regions
        assertTrue(DefaultStations.POPULAR_STATIONS.isNotEmpty())
        assertTrue(HkDefaultStations.POPULAR_STATIONS.isNotEmpty())

        assertTrue(DefaultStations.POPULAR_STATIONS.all { TransitRegion.fromStationId(it.id) == TransitRegion.GB })
        assertTrue(HkDefaultStations.POPULAR_STATIONS.all { TransitRegion.fromStationId(it.id) == TransitRegion.HK })
    }

    @Test
    fun testDepartureFormattingAcrossTimeZones() {
        val testIso = "2026-04-12T10:30:00Z"

        // GB London time (BST is UTC+1, or GMT UTC+0; on April 12 it is BST UTC+1 -> 11:30)
        val gbDeparture = TestFixtures.createTestDeparture(
            id = "GB_DEP_1",
            stationId = "940GZZLUOXC",
            expectedArrivalIso = testIso
        )
        val gbClockTime = gbDeparture.formattedActualClockTime
        assertNotNull(gbClockTime)
        assertEquals("11:30", gbClockTime)

        // HK Hong Kong time (UTC+8 -> 18:30)
        val hkDeparture = TestFixtures.createTestDeparture(
            id = "HK_DEP_1",
            stationId = "HK_MTR_TWL_CEN",
            expectedArrivalIso = testIso
        )
        val hkClockTime = hkDeparture.formattedActualClockTime
        assertNotNull(hkClockTime)
        assertEquals("18:30", hkClockTime)
    }
}
