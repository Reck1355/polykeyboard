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
    fun `anything unknown or unsupported falls back to english`() {
        for (tag in listOf(null, "", "   ", "fr_FR", "de", "zh_CN", "ja", "und", "-", "espa", "e")) {
            assertEquals("$tag", KeyboardLanguages.FALLBACK, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `only english and spanish are supported`() {
        assertEquals(setOf("en", "es"), KeyboardLanguages.SUPPORTED)
        assertTrue(KeyboardLanguages.isSupported("en"))
        assertTrue(KeyboardLanguages.isSupported("es"))
        assertFalse(KeyboardLanguages.isSupported("fr"))
        assertFalse(KeyboardLanguages.isSupported(""))
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
