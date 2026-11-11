package com.androidfung.departureboard.data.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TransitLocaleFormatterTest {

    @Test
    fun testIsChinese() {
        assertTrue(TransitLocaleFormatter.isChinese(Locale.TRADITIONAL_CHINESE))
        assertTrue(TransitLocaleFormatter.isChinese(Locale.SIMPLIFIED_CHINESE))
        assertTrue(TransitLocaleFormatter.isChinese(Locale.forLanguageTag("zh-HK")))
        assertFalse(TransitLocaleFormatter.isChinese(Locale.UK))
        assertFalse(TransitLocaleFormatter.isChinese(Locale.US))
        assertFalse(TransitLocaleFormatter.isChinese(Locale.JAPANESE))
    }

    @Test
    fun testIsSimplifiedChinese() {
        assertTrue(TransitLocaleFormatter.isSimplifiedChinese(Locale.SIMPLIFIED_CHINESE))
        assertTrue(TransitLocaleFormatter.isSimplifiedChinese(Locale.forLanguageTag("zh-CN")))
        assertTrue(TransitLocaleFormatter.isSimplifiedChinese(Locale.forLanguageTag("zh-SG")))
        assertFalse(TransitLocaleFormatter.isSimplifiedChinese(Locale.TRADITIONAL_CHINESE))
        assertFalse(TransitLocaleFormatter.isSimplifiedChinese(Locale.forLanguageTag("zh-HK")))
        assertFalse(TransitLocaleFormatter.isSimplifiedChinese(Locale.forLanguageTag("zh-TW")))
    }

    @Test
    fun testFormatLocalized_traditionalChinese() {
        val result = TransitLocaleFormatter.formatLocalized(
            names = mapOf("en" to "Central", "zh-Hant" to "中環", "zh-Hans" to "中环"),
            locale = Locale.forLanguageTag("zh-HK")
        )
        assertEquals("中環", result)
    }

    @Test
    fun testFormatLocalized_simplifiedChinese() {
        val result = TransitLocaleFormatter.formatLocalized(
            names = mapOf("en" to "Central", "zh-Hant" to "中環", "zh-Hans" to "中环"),
            locale = Locale.forLanguageTag("zh-CN")
        )
        assertEquals("中环", result)
    }

    @Test
    fun testFormatLocalized_english() {
        val result = TransitLocaleFormatter.formatLocalized(
            names = mapOf("en" to "Central", "zh-Hant" to "中環", "zh-Hans" to "中环"),
            locale = Locale.UK
        )
        assertEquals("Central", result)
    }

    @Test
    fun testFormatLocalized_koreanAndOtherLocalesTakePrecedenceOverEnglishFallback() {
        val names = mapOf(
            "en" to "Shinjuku",
            "ja" to "新宿",
            "ko" to "신주쿠"
        )
        assertEquals("신주쿠", TransitLocaleFormatter.formatLocalized(names, Locale.KOREAN))
        assertEquals(
            "신주쿠 (Shinjuku)",
            TransitLocaleFormatter.formatLocalized(names, Locale.KOREAN, fallbackToBilingual = true)
        )
    }

    @Test
    fun testFormatBilingual() {
        val withParens = TransitLocaleFormatter.formatBilingual("Central", "中環", parentheses = true)
        assertEquals("Central (中環)", withParens)

        val withoutParens = TransitLocaleFormatter.formatBilingual("Central", "中環", parentheses = false)
        assertEquals("Central 中環", withoutParens)

        val enOnly = TransitLocaleFormatter.formatBilingual("Central", null)
        assertEquals("Central", enOnly)

        val zhOnly = TransitLocaleFormatter.formatBilingual(null, "中環")
        assertEquals("中環", zhOnly)
    }
}
