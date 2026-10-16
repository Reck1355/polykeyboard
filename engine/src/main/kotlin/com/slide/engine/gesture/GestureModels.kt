package com.slide.engine.gesture

import com.slide.engine.lexicon.Bigrams
import com.slide.engine.lexicon.Lexicon
import com.slide.engine.lexicon.Trigrams

/**
 * Which languages the packaged swipe model can actually decode, and what that means for a swipe.
 *
 * Both engines that can decode a gesture are English artefacts. The neural one is FUTO's
 * English-trained model: its vocabulary and the shape of its output are English, and a Spanish
 * swipe through it would not fail — it would confidently return its best English word for a
 * gesture nobody made. The deterministic one is English geometry over an English lexicon with
 * English-tuned constants, and it cannot produce the accented forms Spanish is written with
 * (`está`, `año`), which its lexicon does contain but its English word shape does not expect.
 *
 * So a language is decodable when it has a model, and this is the one place that is asked. A
 * language without one gets no gesture engine at all: gesture typing is left unavailable, and a
 * swipe commits nothing. That is a visible nothing rather than a fluent wrong word, and it is the
 * honest answer to "there is no Spanish model in this build".
 *
 * Adding a language here is a statement that a model was trained, not a wiring step. The model
 * files under `engine/src/main/assets/swipe` are per-language assets; this build ships the English
 * ones, and a Spanish model is a data deliverable rather than a code change.
 */
object GestureModels {

    /** The one language the packaged swipe model was trained on. */
    const val ENGLISH = "en"

    /** Every language with a swipe model in the shipped assets. */
    val LANGUAGES: Set<String> = setOf(ENGLISH)

    /** Whether a swipe can be decoded for [language] rather than guessed at. */
    fun supports(language: String): Boolean = language in LANGUAGES

    /**
     * The deterministic decoder for [language], or null when that language has no model.
     *
     * Null is the whole safety property: the caller publishes this as the gesture engine, and
     * every path that could emit a swiped word reads it through a null check. Building one anyway
     * "because the geometry is language-independent" is exactly the mistake this function exists
     * to prevent — it would type Spanish from an English word shape.
     */
    fun deterministicDecoderFor(
        language: String,
        lexicon: Lexicon,
        bigrams: Bigrams?,
        trigrams: Trigrams?,
    ): GestureDecodingEngine? = if (supports(language)) {
        GestureDecoder(lexicon, bigrams = bigrams, trigrams = trigrams)
    } else {
        null
    }
}
