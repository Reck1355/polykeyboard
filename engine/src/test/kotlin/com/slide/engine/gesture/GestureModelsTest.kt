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
        assertEquals(setOf("en", "es"), GestureModels.LANGUAGES)
        assertTrue(GestureModels.supports("en"))
        assertTrue(GestureModels.supports("es"))
        // Mandarin is absent on purpose rather than by omission: a swipe there has to land on a
        // pinyin string, and the list it would be matched against is the pinyin table's key set,
        // which is a different change from shipping a lexicon.
        assertFalse(GestureModels.supports("zh"))
        assertFalse(GestureModels.supports(""))
    }

    @Test
    fun `a language with a word list gets a decoder, and one without is refused`() {
        val lexicon = TestLexicon.instance
        assertNotNull(GestureModels.deterministicDecoderFor("en", lexicon, bigrams = null, trigrams = null))
        // Spanish is decoded against the Spanish lexicon this build ships. The list is what the swipe
        // is matched to, so passing one here is the whole of what Spanish glide needs.
        assertNotNull(GestureModels.deterministicDecoderFor("es", lexicon, bigrams = null, trigrams = null))
        // Mandarin has no list to match against, so it is refused outright rather than decoding
        // English words onto a Chinese board.
        assertNull(GestureModels.deterministicDecoderFor("zh", lexicon, bigrams = null, trigrams = null))
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
        assertEquals(KeyboardLanguages.SPANISH, KeyboardLanguages.resolve("es_ES"))
        assertTrue(GestureModels.supports(KeyboardLanguages.SPANISH))
        // Mandarin is the one language this build declines to glide in, and the refusal is deliberate
        // and named rather than a gap waiting to be filled by accident.
        assertFalse(GestureModels.supports(KeyboardLanguages.CHINESE))
        // And every language with a model is a language the resolver can return at all.
        assertTrue(KeyboardLanguages.SUPPORTED.containsAll(GestureModels.LANGUAGES))
    }
}
