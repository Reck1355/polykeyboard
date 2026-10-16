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
    fun `only English has a trained swipe model in this build`() {
        assertEquals(setOf("en"), GestureModels.LANGUAGES)
        assertTrue(GestureModels.supports("en"))
        assertFalse(GestureModels.supports("es"))
        assertFalse(GestureModels.supports(""))
    }

    @Test
    fun `a language with no model gets no decoder at all`() {
        val lexicon = TestLexicon.instance
        assertNotNull(GestureModels.deterministicDecoderFor("en", lexicon, bigrams = null, trigrams = null))
        assertNull(GestureModels.deterministicDecoderFor("es", lexicon, bigrams = null, trigrams = null))
    }

    @Test
    fun `the fallback language has a model and the language without one is deliberate`() {
        // An unknown subtype resolves to English, so it keeps glide — with English's model, over
        // English words. That is what falling back means, and it is why the fallback is a language
        // this build ships data for rather than an empty board.
        assertTrue(GestureModels.supports(KeyboardLanguages.FALLBACK))
        assertTrue(GestureModels.supports(KeyboardLanguages.resolve("fr_FR")))
        // Spanish resolves to itself and has no model: no glide. That is the whole of what this
        // build claims about swiping in Spanish.
        assertEquals(KeyboardLanguages.SPANISH, KeyboardLanguages.resolve("es_ES"))
        assertFalse(GestureModels.supports(KeyboardLanguages.SPANISH))
        // And every language with a model is a language the resolver can return at all.
        assertTrue(KeyboardLanguages.SUPPORTED.containsAll(GestureModels.LANGUAGES))
    }
}
