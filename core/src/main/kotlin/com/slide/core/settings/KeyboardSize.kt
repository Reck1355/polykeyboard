package com.slide.core.settings

/**
 * The keyboard-size ladder — the five stops the "Keyboard size" control offers.
 *
 * One stop is a tenth of the base size ([STEP_GAIN]), so the five multipliers are 0.8 / 0.9 / 1.0 /
 * 1.1 / 1.2 and [DEFAULT_STEP] 0 is the board exactly as it was approved — byte for byte, not
 * merely close.
 *
 * The stops live here, beside the setting that stores them, because two readers have to agree on
 * them: this store keeps a stored step inside the range, and `HexBoard.sizeMultiplier` turns a step
 * into the multiplier the board's sizing is derived at. What the step *does* to the board is the
 * board's business and is deliberately not restated here.
 */
object KeyboardSize {

    /** The smallest stop: eight tenths of the approved board. */
    const val MIN_STEP = -2

    /** The approved board. Nothing about it changes at this stop. */
    const val DEFAULT_STEP = 0

    /** The largest stop: twelve tenths of the approved board. */
    const val MAX_STEP = 2

    /** How much of the base size one stop is — a tenth, so the five stops are 80%..120%. */
    const val STEP_GAIN = 0.1f

    /** The five stops, in order — the control's whole range. */
    val STEPS: IntRange get() = MIN_STEP..MAX_STEP

    /** [step] held to the ladder: a corrupt or hand-edited value is clamped, never accepted. */
    fun clampStep(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP)
}
