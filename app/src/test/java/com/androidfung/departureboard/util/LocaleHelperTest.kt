package com.androidfung.departureboard.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Locale

class LocaleHelperTest {

    @Test
    fun testGetCurrentLanguageTag_fallbackToDefault() {
        val tag = LocaleHelper.getCurrentLanguageTag(fallbackToDefault = true)
        assertNotNull(tag)
    }

    @Test
    fun testSetAndExtractLocale() {
        val locale = Locale.JAPAN
        assertEquals("ja-JP", locale.toLanguageTag())
        val enLocale = Locale.forLanguageTag("en-US")
        assertEquals("en-US", enLocale.toLanguageTag())
    }
}
