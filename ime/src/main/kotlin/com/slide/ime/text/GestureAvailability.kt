package com.slide.ime.text

import com.slide.engine.gesture.GestureDecodingEngine

/**
 * Whether a swipe can be answered at all right now.
 *
 * The decoder is a term in the conjunction rather than a check behind it, and that ordering is the
 * point: a language with no trained swipe model is given no decoder (`GestureModels`), so gesture
 * typing is unavailable, so the view never starts a gesture sequence, so a swipe on that language
 * commits nothing. There is no path where a language without a model reaches a candidate list.
 *
 * Pulled out of the service because it is the whole of the answer to "what does the swipe path do
 * for Spanish" and a private method over live view state could only be described, not asserted.
 */
object GestureAvailability {

    fun available(
        gestureTypingEnabled: Boolean,
        suggestionsAllowed: Boolean,
        decoder: GestureDecodingEngine?,
        alphaLayer: Boolean,
        panelShown: Boolean,
    ): Boolean =
        gestureTypingEnabled && suggestionsAllowed && decoder != null && alphaLayer && !panelShown
}
