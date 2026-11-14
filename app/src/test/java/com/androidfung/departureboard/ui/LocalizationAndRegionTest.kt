package com.androidfung.departureboard.ui

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.androidfung.departureboard.R
import com.androidfung.departureboard.data.model.TransitRegion
import com.androidfung.departureboard.data.model.gb.GbDefaultStations
import com.androidfung.departureboard.data.model.hk.HkDefaultStations
import com.androidfung.departureboard.test.TestFixtures
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        assertEquals("Northbound", context.getString(R.string.dir_northbound))
        assertEquals("Delayed", context.getString(R.string.status_delayed))
        assertEquals("Cancelled", context.getString(R.string.status_cancelled))
        assertEquals("Search station or line…", context.getString(R.string.dashboard_search_bar_placeholder))
        assertEquals("Network Status (5)", context.getString(R.string.dashboard_menu_network_status_count, 5))
        assertEquals("Debug Settings", context.getString(R.string.dashboard_menu_debug_settings))
        assertEquals("Switch Region", context.getString(R.string.switch_region_title))
    }

    @Test
    fun testTraditionalChineseStrings() {
        val context = getLocalizedContext(Locale.TRADITIONAL_CHINESE)

        assertEquals("英國", context.getString(R.string.region_uk))
        assertEquals("香港", context.getString(R.string.region_hk))
        assertEquals("即將抵達", context.getString(R.string.status_due))
        assertEquals("北行", context.getString(R.string.dir_northbound))
        assertEquals("延誤", context.getString(R.string.status_delayed))
        assertEquals("取消", context.getString(R.string.status_cancelled))
        assertEquals("搜尋車站或路線…", context.getString(R.string.dashboard_search_bar_placeholder))
        assertEquals("路網狀態 (5)", context.getString(R.string.dashboard_menu_network_status_count, 5))
        assertEquals("除錯設定", context.getString(R.string.dashboard_menu_debug_settings))
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
        assertTrue(GbDefaultStations.POPULAR_STATIONS.isNotEmpty())
        assertTrue(HkDefaultStations.POPULAR_STATIONS.isNotEmpty())

        assertTrue(GbDefaultStations.POPULAR_STATIONS.all { TransitRegion.fromStationId(it.id) == TransitRegion.GB })
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

    @Test
    fun testStationAndCallingPointMultilingualResolution() {
        val station = com.androidfung.departureboard.data.model.Station(
            id = "JP_METRO_M17",
            name = "Tokyo (東京)",
            localizedNames = mapOf(
                "en" to "Tokyo",
                "ja" to "東京",
                "ja-Hrkt" to "とうきょう"
            )
        )

        // Japanese locale
        assertEquals("東京", station.resolveDisplayName(showBilingual = false, locale = Locale.JAPANESE))
        // English locale
        assertEquals("Tokyo", station.resolveDisplayName(showBilingual = false, locale = Locale.ENGLISH))
        // Bilingual
        assertEquals("Tokyo (東京)", station.resolveDisplayName(showBilingual = true, locale = Locale.ENGLISH))

        val callingPoint = com.androidfung.departureboard.data.model.CallingPoint(
            stationName = "Central",
            localizedNames = mapOf(
                "en" to "Central",
                "zh-Hant" to "中環"
            )
        )

        assertEquals("中環", callingPoint.resolveDisplayName(showBilingual = false, locale = Locale.TRADITIONAL_CHINESE))
        assertEquals("Central", callingPoint.resolveDisplayName(showBilingual = false, locale = Locale.ENGLISH))
        assertEquals("Central (中環)", callingPoint.resolveDisplayName(showBilingual = true))

        val operator = com.androidfung.departureboard.data.model.TransitOperator(
            id = "MTR",
            name = "MTR",
            localizedNames = mapOf(
                "en" to "MTR",
                "zh-Hant" to "港鐵"
            )
        )
        assertEquals("港鐵", operator.resolveDisplayName(showBilingual = false, locale = Locale.TRADITIONAL_CHINESE))
        assertEquals("MTR (港鐵)", operator.resolveDisplayName(showBilingual = true))
    }
}
