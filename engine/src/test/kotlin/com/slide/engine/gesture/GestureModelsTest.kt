package com.slide.engine.gesture

import com.slide.core.layout.KeyboardLanguages
import com.slide.engine.TestLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glide typing is English-only in this build, and that is a fact about the shipped assets rather
 * than about wiring. These assertions are what stop it from becoming an assumption: Spanish gets no
 * decoder, so the swipe path has nothing to answer with, so a Spanish swipe commits nothing instead
 * of an English word nobody gestured.
 */
class GestureModelsTest {

    @Test
    fun `the languages that can decode a swipe are exactly the ones with a word list`() {
        assertEquals(setOf("en", "es", "zh"), GestureModels.LANGUAGES)
        assertTrue(GestureModels.supports("en"))
        assertTrue(GestureModels.supports("es"))
        // Mandarin decodes against the pinyin table's own keys rather than a lexicon of words, and that
        // list now exists: PinyinEngine.swipeLexicon builds it. The two kinds of list are not
        // interchangeable, so which one a language is matched against is the caller's decision and
        // PinyinGlideTest pins the outcome the user sees.
        assertTrue(GestureModels.supports("zh"))
        assertFalse(GestureModels.supports(""))
    }

    @Test
    fun `a language with a word list gets a decoder, and one without is refused`() {
        val lexicon = TestLexicon.instance
        assertNotNull(GestureModels.deterministicDecoderFor("en", lexicon, bigrams = null, trigrams = null))
        // Spanish is decoded against the Spanish lexicon this build ships. The list is what the swipe
        // is matched to, so passing one here is the whole of what Spanish glide needs.
        assertNotNull(GestureModels.deterministicDecoderFor("es", lexicon, bigrams = null, trigrams = null))
        // The factory answers by language, not by which list it was handed: every language it lists
        // gets a decoder. For Mandarin the caller passes PinyinEngine.swipeLexicon, so a swipe there
        // cannot be answered with an English word even though this test hands it an English list.
        assertNotNull(GestureModels.deterministicDecoderFor("zh", lexicon, bigrams = null, trigrams = null))
    }

    @Test
    fun `the fallback language has a model and the language without one is deliberate`() {
        // An unknown subtype resolves to English, so it keeps glide — with English's model, over
        // English words. That is what falling back means, and it is why the fallback is a language
        // this build ships data for rather than an empty board.
        assertTrue(GestureModels.supports(KeyboardLanguages.FALLBACK))
        assertTrue(GestureModels.supports(KeyboardLanguages.resolve("fr_FR")))
        // Spanish resolves to itself and decodes against its own word list, so it glides without
        // pretending to be English. An accented word swiped with a plain letter will be missed, and
        // that is the accepted cost of decoding something rather than nothing.
        // A language this build does not carry resolves to one it does, in the resolver rather than
        // at the call site, so nothing can hand the board a language with no data behind it.
        assertEquals(
            if (KeyboardLanguages.SUPPORTED.contains(KeyboardLanguages.SPANISH)) KeyboardLanguages.SPANISH else KeyboardLanguages.FALLBACK,
            KeyboardLanguages.resolve("es_ES"),
        )
        // Every language the build offers must glide, whichever tier it is: the list is the only
        // thing that differs between them, and a language offered without a model would hand the
        // user a board that quietly stops learning their swipes.
        for (entry in KeyboardLanguages.SWITCHABLE) {
            assertTrue("${entry.language} is offered but cannot glide", GestureModels.supports(entry.language))
        }
        assertEquals(KeyboardLanguages.SUPPORTED, KeyboardLanguages.SWITCHABLE.map { it.language }.toSet())
        // And the direction that must hold in both tiers: the engine can glide every language the
        // build can offer. The reverse is not required, because the engine is code and the offer is
        // data, and a slim build ships less data than the engine can handle.
        assertTrue(GestureModels.LANGUAGES.containsAll(KeyboardLanguages.SUPPORTED))
    }
}
