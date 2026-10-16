package com.slide.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The active language is decided here and nowhere else, so this is where "which language" is
 * pinned. Every case below is a tag Android can actually report for one of the declared subtypes
 * in `ime/src/main/res/xml/method.xml`, plus the malformed and unknown input the resolver has to
 * survive — a keyboard that answers "French" with a Spanish board and an English dictionary is
 * worse than one that answers it with English.
 */
class KeyboardLanguagesTest {

    @Test
    fun `spanish subtypes resolve to spanish`() {
        for (tag in listOf("es_ES", "es-ES", "es", "es_419", "es-US", "ES_es")) {
            assertEquals(tag, KeyboardLanguages.SPANISH, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `english subtypes resolve to english`() {
        for (tag in listOf("en_US", "en-US", "en", "en_GB", "EN_us")) {
            assertEquals(tag, KeyboardLanguages.FALLBACK, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `chinese subtypes resolve to chinese`() {
        for (tag in listOf("zh_CN", "zh-CN", "zh", "zh_Hans", "zh_CN_#Hans", "ZH_cn")) {
            assertEquals(tag, KeyboardLanguages.CHINESE, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `anything unknown or unsupported falls back to english`() {
        for (tag in listOf(null, "", "   ", "fr_FR", "de", "ja", "und", "-", "espa", "e")) {
            assertEquals("$tag", KeyboardLanguages.FALLBACK, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `english, spanish and chinese are supported`() {
        assertEquals(setOf("en", "es", "zh"), KeyboardLanguages.SUPPORTED)
        assertTrue(KeyboardLanguages.isSupported("en"))
        assertTrue(KeyboardLanguages.isSupported("es"))
        assertTrue(KeyboardLanguages.isSupported("zh"))
        assertFalse(KeyboardLanguages.isSupported("fr"))
        assertFalse(KeyboardLanguages.isSupported(""))
    }

    @Test
    fun `the globe key can reach every language, in order, and come back`() {
        val walked = generateSequence(KeyboardLanguages.SWITCHABLE.first()) {
            KeyboardLanguages.next(it.language)
        }.take(KeyboardLanguages.SWITCHABLE.size).map { it.language }.toList()
        assertEquals(KeyboardLanguages.SWITCHABLE.map { it.language }, walked)
        assertEquals(
            KeyboardLanguages.SWITCHABLE.first(),
            KeyboardLanguages.next(KeyboardLanguages.SWITCHABLE.last().language),
        )
    }

    @Test
    fun `every resolved language has boards to draw`() {
        // The resolver is the only source of a language and the board table is the only consumer of
        // one, so they have to agree for every answer the resolver can give.
        for (language in KeyboardLanguages.SUPPORTED) {
            val boards = Layouts.boardsFor(language)
            assertEquals(language, boards.hex.languageTag)
            assertEquals(language, boards.qwerty.languageTag)
            assertEquals(language, boards.email.languageTag)
            assertEquals(language, boards.uri.languageTag)
        }
    }

    @Test
    fun `an unsupported language gets the fallback boards rather than none`() {
        assertSame(Layouts.HexQwertyEn, Layouts.boardsFor("fr").hex)
        assertSame(Layouts.QwertyEn, Layouts.boardsFor("").qwerty)
    }
}
