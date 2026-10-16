package com.slide.ime

import com.slide.engine.suggest.WordSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripSuggestionsTest {

    private fun typed(word: String) = WordSuggestion(word, 1f, WordSuggestion.Kind.Typed)
    private fun completion(word: String) = WordSuggestion(word, 2f, WordSuggestion.Kind.Completion)
    private fun correction(word: String) = WordSuggestion(word, 3f, WordSuggestion.Kind.Correction)

    @Test
    fun `a typo fix is labelled as a fix`() {
        val strip = StripSuggestions.from(
            listOf(correction("the"), completion("then"), typed("teh")),
        )

        assertTrue("the correction lost its label", strip[0].isCorrection)
        assertFalse(strip[1].isCorrection)
        assertFalse(strip[2].isCorrection)
    }

    @Test
    fun `a word completion is never labelled as a typo`() {
        // Until this flag reaches the strip, a completion and a correction are the same cell with
        // the same weight. Labelling a completion as a fix would underline half-typed words and
        // claim the user mistyped something they were simply still writing.
        val strip = StripSuggestions.from(listOf(completion("hello"), completion("help")))

        assertTrue(strip.none { it.isCorrection })
    }

    @Test
    fun `words and their labels stay in step`() {
        val words = listOf(typed("teh"), correction("the"), completion("then"))

        val strip = StripSuggestions.from(words)

        assertEquals(words.map { it.word }, strip.map { it.word })
        assertEquals(words.size, strip.size)
    }

    @Test
    fun `the text underline and the strip mark are decided by one verdict`() {
        // The strip's mark and the underline under the word in the editor both read this, so a word
        // cannot be underlined in one place and left plain in the other.
        assertTrue(StripSuggestions.hasCorrection(listOf(typed("teh"), correction("the"))))
        assertFalse(StripSuggestions.hasCorrection(listOf(typed("teh"), completion("then"))))
        assertFalse(StripSuggestions.hasCorrection(emptyList()))
    }
}
