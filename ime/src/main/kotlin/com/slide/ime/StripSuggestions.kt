package com.slide.ime

import com.slide.engine.suggest.WordSuggestion
import com.slide.ime.view.StripSuggestion

/**
 * The one place the suggester's graded candidates become what the strip draws.
 *
 * The engine grades every candidate. `Typed` is what the user typed, `Completion` finishes the word
 * they are part way through, and `Correction` is a near miss under the touch model, which is the
 * engine saying the word in front of the cursor looks wrong. Only that last grade is a claim about
 * a typo, and the strip is the only place the claim is ever shown, so the grade has to survive the
 * trip. Mapping it once, here, keeps the three strip callers from each deciding for themselves.
 */
internal object StripSuggestions {

    /**
     * Candidates that carry no typo claim, for callers that only have words in hand, such as a
     * gesture's alternative readings. Those are not corrections and must not be underlined as if
     * they were, so they say so explicitly rather than defaulting into the flag by accident.
     */
    fun wordsOnly(words: List<String>): List<StripSuggestion> =
        words.map { StripSuggestion(it) }

    /**
     * True when the keyboard has a fix on offer for the word being typed.
     *
     * The strip's mark and the underline in the text are both decided here, so the two cannot end up
     * disagreeing about the same word.
     */
    fun hasCorrection(words: List<WordSuggestion>): Boolean = from(words).any { it.isCorrection }

    fun from(words: List<WordSuggestion>): List<StripSuggestion> = words.map { candidate ->
        StripSuggestion(
            word = candidate.word,
            isCorrection = candidate.kind == WordSuggestion.Kind.Correction,
        )
    }
}
