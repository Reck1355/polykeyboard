package com.slide.core.layout

import com.slide.core.BuildConfig

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
            // A slim build carries no data for this language, so the answer must be the language it
            // does carry. Asserting the clamp here is the half that catches a build offering a board
            // whose dictionary is not in the APK.
            assertEquals(
                tag,
                if (BuildConfig.ENGLISH_ONLY) KeyboardLanguages.FALLBACK else KeyboardLanguages.SPANISH,
                KeyboardLanguages.resolve(tag),
            )
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
            // A slim build carries no data for this language, so the answer must be the language it
            // does carry. Asserting the clamp here is the half that catches a build offering a board
            // whose dictionary is not in the APK.
            assertEquals(
                tag,
                if (BuildConfig.ENGLISH_ONLY) KeyboardLanguages.FALLBACK else KeyboardLanguages.CHINESE,
                KeyboardLanguages.resolve(tag),
            )
        }
    }

    @Test
    fun `anything unknown or unsupported falls back to english`() {
        for (tag in listOf(null, "", "   ", "fr_FR", "de", "ja", "und", "-", "espa", "e")) {
            assertEquals("$tag", KeyboardLanguages.FALLBACK, KeyboardLanguages.resolve(tag))
        }
    }

    @Test
    fun `what is supported is what this build carries`() {
        // Both tiers are gated: a full build ships all three, and a slim build must answer for one.
        // The half that matters is the slim half, because there the Spanish and Chinese data is
        // absent, and a language offered with no lexicon behind it types into an empty dictionary.
        val expected = if (BuildConfig.ENGLISH_ONLY) setOf("en") else setOf("en", "es", "zh")
        assertEquals(expected, KeyboardLanguages.SUPPORTED)
        assertTrue(KeyboardLanguages.isSupported("en"))
        assertFalse(KeyboardLanguages.isSupported("fr"))
        assertFalse(KeyboardLanguages.isSupported(""))
        assertEquals(KeyboardLanguages.SUPPORTED, KeyboardLanguages.SWITCHABLE.map { it.language }.toSet())
        for (language in listOf("es", "zh")) {
            assertEquals(
                "a language this build does not carry must resolve to one it does",
                if (BuildConfig.ENGLISH_ONLY) KeyboardLanguages.FALLBACK else language,
                KeyboardLanguages.resolve("$language-CN"),
            )
        }
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
    fun `an empty stored set means every shipped language is on`() {
        assertEquals(KeyboardLanguages.SWITCHABLE, KeyboardLanguages.enabledLanguages(emptySet()))
        for (entry in KeyboardLanguages.SWITCHABLE) {
            assertTrue(KeyboardLanguages.languageIsOn(emptySet(), entry.language))
        }
    }

    @Test
    fun `the globe walks only the languages that are on`() {
        if (KeyboardLanguages.SWITCHABLE.size < 2) return
        val first = KeyboardLanguages.SWITCHABLE[0].language
        val second = KeyboardLanguages.SWITCHABLE[1].language
        assertEquals(first, KeyboardLanguages.next(first, setOf(first)).language)
        assertEquals(second, KeyboardLanguages.next(first, setOf(first, second)).language)
        assertEquals(first, KeyboardLanguages.next(second, setOf(first, second)).language)
    }

    @Test
    fun `the last remaining language cannot be turned off`() {
        val last = KeyboardLanguages.SWITCHABLE.first().language
        assertEquals(setOf(last), KeyboardLanguages.withLanguageOn(setOf(last), last, on = false))
    }

    @Test
    fun `turning one off from a fresh install leaves the rest on`() {
        if (KeyboardLanguages.SWITCHABLE.size < 2) return
        val drop = KeyboardLanguages.SWITCHABLE.last().language
        val left = KeyboardLanguages.withLanguageOn(emptySet(), drop, on = false)
        assertEquals(KeyboardLanguages.SWITCHABLE.size - 1, left.size)
        assertFalse(drop in left)
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
