package com.slide.ime.text

import com.slide.core.layout.KeyboardLanguages
import com.slide.engine.gesture.GestureCandidate
import com.slide.engine.gesture.GestureDecodingEngine
import com.slide.engine.gesture.GestureKeyMap
import com.slide.engine.gesture.GestureModels
import com.slide.engine.gesture.GesturePoint
import com.slide.engine.lexicon.Lexicon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a swipe does on a language with no trained model.
 *
 * The answer is a chain of three facts, and each link is asserted rather than assumed: the Spanish
 * language is given no decoder ([GestureModels]), a missing decoder makes gesture typing
 * unavailable however the settings are set (this object), and the view only starts a gesture
 * sequence while gesture typing is available. The swipe therefore commits nothing on Spanish —
 * which is the honest outcome, and the reason a stale English decoder is never left in place to
 * answer for it.
 */
class GestureAvailabilityTest {

    private object StubDecoder : GestureDecodingEngine {
        override fun decode(
            points: List<GesturePoint>,
            keys: GestureKeyMap,
            blockOffensive: Boolean,
            previousWord: String?,
            previousPreviousWord: String?,
        ): List<GestureCandidate> = emptyList()
    }

    @Test
    fun `no decoder means no gesture typing, whatever the settings say`() {
        for (enabled in listOf(true, false)) {
            for (suggestions in listOf(true, false)) {
                for (alpha in listOf(true, false)) {
                    for (panel in listOf(true, false)) {
                        assertFalse(
                            "enabled=$enabled suggestions=$suggestions alpha=$alpha panel=$panel",
                            GestureAvailability.available(
                                gestureTypingEnabled = enabled,
                                suggestionsAllowed = suggestions,
                                decoder = null,
                                alphaLayer = alpha,
                                panelShown = panel,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a decoder on the letters layer with everything else in order is gesture typing`() {
        assertTrue(
            GestureAvailability.available(
                gestureTypingEnabled = true,
                suggestionsAllowed = true,
                decoder = StubDecoder,
                alphaLayer = true,
                panelShown = false,
            ),
        )
    }

    @Test
    fun `each other condition still switches gesture typing off on its own`() {
        val available: (Boolean, Boolean, Boolean, Boolean) -> Boolean = { enabled, suggestions, alpha, panel ->
            GestureAvailability.available(
                gestureTypingEnabled = enabled,
                suggestionsAllowed = suggestions,
                decoder = StubDecoder,
                alphaLayer = alpha,
                panelShown = panel,
            )
        }
        assertFalse(available(false, true, true, false))
        assertFalse(available(true, false, true, false))
        assertFalse(available(true, true, false, false))
        assertFalse(available(true, true, true, true))
    }

    @Test
    fun `the Spanish language reaches the swipe path with no decoder and so with no gesture typing`() {
        val lexicon = Lexicon(CharArray(0), intArrayOf(0), ByteArray(0), ByteArray(0))
        val spanish = KeyboardLanguages.resolve("es_ES")
        val decoder = GestureModels.deterministicDecoderFor(spanish, lexicon, null, null)
        assertNull(decoder)
        assertFalse(
            GestureAvailability.available(
                gestureTypingEnabled = true,
                suggestionsAllowed = true,
                decoder = decoder,
                alphaLayer = true,
                panelShown = false,
            ),
        )
    }
}
