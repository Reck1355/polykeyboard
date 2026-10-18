package com.slide.ime.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.slide.core.theme.KeyboardTheme
import com.slide.core.theme.Themes
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The row of word candidates above the keys.
 *
 * The centre cell is the leading candidate and the side cells are alternatives. Keeping that
 * placement stable turns a wrong decode from a retyped word into one predictable tap.
 *
 * It draws itself rather than nesting TextViews. Three cells is a fixed, tiny layout, and doing it
 * in one onDraw keeps it consistent with [KeyboardView] and avoids a measure pass on every swipe.
 */
class SuggestionStripView(context: Context) : View(context) {

    interface Listener {
        /** Opens the clipboard panel. Offered only while the strip has no candidates. */
        fun onClipboardRequested() = Unit

        /** Opens the text-editing panel. Offered only while the strip has no candidates. */
        fun onTextEditRequested() = Unit

        /** [index] is the position in the list passed to [setSuggestions], 0 being the best. */
        fun onSuggestionPicked(index: Int, word: String)

        /**
         * A candidate was held down: the gesture for teaching the keyboard a word, or for taking
         * one back. Default no-op so existing embedders are unaffected.
         */
        fun onSuggestionHeld(index: Int, word: String) = Unit

        /** The microphone button was tapped. */
        fun onVoiceRequested()

        /** The emoji button above the board was tapped. */
        fun onEmojiRequested() = Unit

        /** The language button above the board was tapped. */
        fun onLanguageRequested() = Unit

        /** The symbols button above the board was tapped: the `?123` page, off the board. */
        fun onSymbolsRequested() = Unit
    }

    var listener: Listener? = null

    /** False for password/secure and non-language editors. */
    var voiceEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            pressedIndex = -1
            micPressed = false
            clipboardPressed = false
            editPressed = false
            emojiPressed = false
            languagePressed = false
            symbolsPressed = false
            refreshAccessibilityDescription()
            invalidate()
        }

    var keyboardTheme: KeyboardTheme = Themes.Light
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Whether the board on screen is a symbols page rather than the letters.
     *
     * The square at the strip's right end is one button, not two: `?123` while the letters are up,
     * `ABC` while a symbols page is, and tapping it goes to whichever page it is not showing. He
     * asked for exactly that - *in symbols the symbols key needs to turn back into letters so i can
     * switch easily* - and it is what the key does on every other keyboard he has used, so the way
     * back is the way in.
     */
    var lettersPageOffered: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            symbolsPressed = false
            refreshAccessibilityDescription()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val words = ArrayList<StripSuggestion>(HOLD_AT_MOST)

    /**
     * Which candidate the leftmost slot shows.
     *
     * A word table offers far more than three readings of what has been typed, and the words past
     * the third are not noise: a person looking for 家 among the readings of `jia` needs the ones
     * after the top three as much as the top three. The slots are a window onto the list and this is
     * where the window opens; with no more candidates than fit, it stays at nought and the placement
     * helper decides which slot the best word sits in.
     */
    private var scrollPx = 0f
    private var dragStartX = 0f
    private var dragStartPx = 0f
    private var dragging = false

    /**
     * Which candidate the leftmost slot shows, read off [scrollPx].
     *
     * The drag travels in pixels so that it follows the thumb. It used to step a whole cell at a
     * time, which on a 1080 px screen meant a swipe shorter than 184 px moved nothing at all: the
     * gesture was there and simply did not answer, so the strip read as unscrollable. The slot is
     * now derived, and the window rests on one after the finger lifts.
     */
    private val scrollOffset: Int
        get() = scrollSlot(scrollPx)

    private fun cellWidth(): Float = suggestionWidth() / MAX_VISIBLE

    private fun maxScrollPx(): Float = SuggestionStripLayout.maxScrollPx(
        width = width.toFloat(),
        height = height.toFloat(),
        voiceEnabled = voiceEnabled,
        slotCount = MAX_VISIBLE,
        count = words.size,
        trailingSquares = trailingSquares(),
    )

    private fun scrollSlot(px: Float): Int {
        val last = (words.size - MAX_VISIBLE).coerceAtLeast(0)
        val cell = cellWidth().coerceAtLeast(1f)
        return (px / cell).roundToInt().coerceIn(0, last)
    }

    /** Rounds a finished drag onto a slot, so the strip never rests between two candidates. */
    private fun settleScroll() {
        if (!dragging) return
        dragging = false
        scrollPx = scrollOffset * cellWidth()
        invalidate()
    }

    /** See [ellipsized]. */
    private var ellipsizedWidth = 0f
    private val ellipsizedNormal = HashMap<String, String>()
    private val ellipsizedBold = HashMap<String, String>()

    /** Reused across draws; `Paint.fontMetrics` allocates a fresh object per read. */
    private val reusableMetrics = Paint.FontMetrics()

    private var pressedIndex = -1
    private var micPressed = false
    private var clipboardPressed = false
    private var editPressed = false

    /**
     * The two buttons the boards gave up.
     *
     * The emoji key and the globe used to sit in the bottom key row, where the globe took the
     * leftmost cell and the emoji key sat beside the period. Both have left the board for here, and
     * unlike the clipboard and editing shortcuts they are not offered only while the strip is
     * empty: they are the keyboard's only route to emoji and to another language, so they are on
     * the strip in every state.
     */
    private var emojiPressed = false
    private var languagePressed = false
    private var symbolsPressed = false
    private var emptyMessage = "Type or swipe for suggestions"

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    /** Set once a hold has fired, so releasing does not also pick the candidate. */
    private var longPressFired = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(16f)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1f)
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    private val accessibilityHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (y !in 0f..height.toFloat()) return INVALID_ID
            if (isOverClipboard(x)) return A11Y_CLIPBOARD
            if (isOverEdit(x)) return A11Y_EDIT
            if (voiceEnabled && isOverMic(x)) return A11Y_MIC
            if (isOverEmoji(x)) return A11Y_EMOJI
            if (isOverLanguage(x)) return A11Y_LANGUAGE
            if (isOverSymbols(x)) return A11Y_SYMBOLS
            val index = indexAt(x)
            return if (index >= 0) A11Y_WORD_BASE + index else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            if (toolbarShown) {
                virtualViewIds += A11Y_CLIPBOARD
                virtualViewIds += A11Y_EDIT
            }
            words.indices.forEach { virtualViewIds += A11Y_WORD_BASE + it }
            if (voiceEnabled) virtualViewIds += A11Y_MIC
            virtualViewIds += A11Y_EMOJI
            virtualViewIds += A11Y_LANGUAGE
            virtualViewIds += A11Y_SYMBOLS
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            node.className = "android.widget.Button"
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            if (virtualViewId == A11Y_CLIPBOARD) {
                node.contentDescription = "Clipboard"
                node.setBoundsInParent(
                    Rect(squareWidth().toInt(), 0, (squareWidth() * 2).toInt(), height),
                )
                return
            }
            if (virtualViewId == A11Y_EDIT) {
                node.contentDescription = "Edit text"
                node.setBoundsInParent(
                    Rect((squareWidth() * 2).toInt(), 0, (squareWidth() * 3).toInt(), height),
                )
                return
            }
            if (virtualViewId == A11Y_MIC) {
                node.contentDescription = "Voice typing"
                node.setBoundsInParent(Rect(micLeft().toInt(), 0, (micLeft() + squareWidth()).toInt(), height))
                return
            }
            if (virtualViewId == A11Y_EMOJI) {
                node.contentDescription = "Emoji"
                node.setBoundsInParent(
                    Rect(
                        squareLeft(1).toInt(),
                        0,
                        squareRight(1).toInt(),
                        height,
                    ),
                )
                return
            }
            if (virtualViewId == A11Y_LANGUAGE) {
                node.contentDescription = "Next language"
                node.setBoundsInParent(
                    Rect(
                        squareLeft(2).toInt(),
                        0,
                        squareRight(2).toInt(),
                        height,
                    ),
                )
                return
            }
            if (virtualViewId == A11Y_SYMBOLS) {
                node.contentDescription =
                    if (lettersPageOffered) "Letters" else "Symbols and numbers"
                node.setBoundsInParent(
                    Rect(
                        squareLeft(3).toInt(),
                        0,
                        squareRight(3).toInt(),
                        height,
                    ),
                )
                return
            }

            val index = virtualViewId - A11Y_WORD_BASE
            val candidate = words.getOrNull(index)
            val word = candidate?.word.orEmpty()
            node.contentDescription = buildList {
                add(word)
                if (index == 0) add("best suggestion")
                // Spoken as well as drawn: the underline is the only mark that separates a typo
                // fix from a word completion, and a mark only some users can see is not a label.
                if (candidate?.isCorrection == true) add("correction")
            }.joinToString(", ")
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
            val cellWidth = suggestionWidth() / MAX_VISIBLE
            // A candidate scrolled off the edge has no place on screen to describe, so it is left out
            // of the node tree entirely rather than announced at a position it does not occupy.
            val visualSlot = slotOfIndex(index) ?: return
            val contentLeft = suggestionLeft()
            node.setBoundsInParent(
                Rect(
                    (contentLeft + visualSlot * cellWidth).toInt(),
                    0,
                    (contentLeft + (visualSlot + 1) * cellWidth).toInt(),
                    height,
                ),
            )
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?,
        ): Boolean {
            if (virtualViewId == A11Y_CLIPBOARD) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK || !toolbarShown) return false
                listener?.onClipboardRequested()
                return true
            }
            if (virtualViewId == A11Y_EDIT) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK || !toolbarShown) return false
                listener?.onTextEditRequested()
                return true
            }
            if (virtualViewId == A11Y_MIC) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK || !voiceEnabled) return false
                listener?.onVoiceRequested()
                return true
            }
            if (virtualViewId == A11Y_EMOJI) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
                listener?.onEmojiRequested()
                return true
            }
            if (virtualViewId == A11Y_LANGUAGE) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
                listener?.onLanguageRequested()
                return true
            }
            if (virtualViewId == A11Y_SYMBOLS) {
                if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
                listener?.onSymbolsRequested()
                return true
            }
            val index = virtualViewId - A11Y_WORD_BASE
            val word = words.getOrNull(index)?.word ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    listener?.onSuggestionPicked(index, word)
                    true
                }
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                    listener?.onSuggestionHeld(index, word)
                    true
                }
                else -> false
            }
        }
    }

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        refreshAccessibilityDescription()
    }

    /**
     * Shows up to three candidates, best first, or clears the strip when given none.
     *
     * Three rather than the decoder's five: past that the cells are too narrow to read at a glance
     * or to hit reliably, and the fourth and fifth candidates are almost never the intended word.
     */
    /**
     * Shows candidates that each know whether the keyboard is offering them as a fix.
     *
     * One list of self-describing candidates rather than a list of words plus a parallel list of
     * flags: the placement moves a candidate between cells, and two lists are one index away from
     * underlining the wrong word. Callers with no opinion on labels pass [StripSuggestions.wordsOnly].
     */
    fun setSuggestions(candidates: List<StripSuggestion>) {
        // Held well past what fits, because the ones past the third are reachable by scrolling and
        // were previously thrown away before the strip ever saw them. Only MAX_VISIBLE are drawn at
        // once; the rest wait their turn off the edge.
        val requested = candidates.take(HOLD_AT_MOST)
        if (words.size == requested.size && words.indices.all { words[it] == requested[it] }) return
        // A pending hold captured a slot, not a word, and re-reads the list when it fires. Left
        // armed across a swap — the decoder finishing mid-hold — it would teach or forget whatever
        // word has since landed in that slot.
        cancelPendingLongPress()
        words.clear()
        ellipsizedNormal.clear()
        ellipsizedBold.clear()
        requested.forEach(words::add)
        // A new list is a new question, so the window opens at the start of it. Keeping an old scroll
        // would drop the best reading off the left edge exactly when the user is watching it change.
        scrollPx = 0f
        dragging = false
        pressedIndex = -1
        clipboardPressed = false
        editPressed = false
        micPressed = false
        refreshAccessibilityDescription()
        accessibilityHelper.invalidateRoot()
        invalidate()
    }

    fun setEmptyMessage(message: String) {
        if (emptyMessage == message) return
        emptyMessage = message
        refreshAccessibilityDescription()
        invalidate()
    }

    fun clear() = setSuggestions(emptyList())

    val isEmpty: Boolean get() = words.isEmpty()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The strip keeps its height even when empty. Letting it collapse would resize the whole
        // input view on every swipe, shoving the keys up and down under the user's thumb.
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(dp(HEIGHT_DP).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(keyboardTheme.background)
        drawMicButton(canvas)
        drawEmojiButton(canvas)
        drawLanguageButton(canvas)
        drawSymbolsButton(canvas)
        if (words.isEmpty()) {
            drawToolbarButton(
                canvas,
                squareWidth() * (leftGroupStart() + 0.5f),
                pressed = clipboardPressed,
            ) { x, y ->
                drawClipboardGlyph(canvas, x, y)
            }
            drawToolbarButton(
                canvas,
                squareWidth() * (leftGroupStart() + 1.5f),
                pressed = editPressed,
            ) { x, y ->
                drawEditGlyph(canvas, x, y)
            }
            if (emptyMessage.isNotEmpty()) {
                // The hint shares the row with the toolbar icons, so it centres over what is
                // left of the strip and disappears rather than colliding on a narrow screen.
                val messageLeft = squareWidth() * (leftGroupStart() + 2f)
                val messageRight = squareLeft(trailingSquares())
                val messageWidth = messageRight - messageLeft
                if (messageWidth >= dp(72f)) {
                    textPaint.color = keyboardTheme.hintText
                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.textSize = minOf(sp(12f), messageWidth * 0.055f)
                    textPaint.getFontMetrics(reusableMetrics)
                    canvas.drawText(
                        emptyMessage,
                        messageLeft + messageWidth / 2f,
                        height / 2f - (reusableMetrics.ascent + reusableMetrics.descent) / 2f,
                        textPaint,
                    )
                }
            }
            return
        }

        textPaint.textSize = sp(16f)
        val cellWidth = suggestionWidth() / MAX_VISIBLE

        // More candidates than fit: a hairline under the words, as long as the share of the list on
        // screen. It says the strip continues and where the user is in it, which is the difference
        // between a scroll and a gesture nobody knows is there.
        if (words.size > MAX_VISIBLE) {
            val trackLeft = suggestionLeft()
            val trackWidth = cellWidth * MAX_VISIBLE
            val shown = (scrollPx + trackWidth) / (cellWidth * words.size)
            markPaint.color = keyboardTheme.suggestionHighlightText
            markPaint.strokeWidth = dp(2f)
            val lineY = height - dp(1.5f)
            canvas.drawLine(trackLeft, lineY, trackLeft + trackWidth * shown, lineY, markPaint)
        }
        // Vertically centre on the text's own middle rather than its baseline.
        textPaint.getFontMetrics(reusableMetrics)
        val baseline = height / 2f - (reusableMetrics.ascent + reusableMetrics.descent) / 2f

        // Keep the best candidate in the centre. That stable target is easier to scan and matches
        // the placement people have learned from mature mobile keyboards.
        // The window is a slot onto the list, so a drag in progress shows two halves of two words at
        // once: everything the candidates draw is clipped to it rather than allowed to run under a
        // button. One slot past each edge is drawn too, so a word entering from the side arrives as
        // it moves instead of appearing only once it has landed.
        val windowLeft = suggestionLeft()
        val windowWidth = suggestionWidth()
        canvas.save()
        canvas.clipRect(windowLeft, 0f, windowLeft + windowWidth, height.toFloat())
        val origin = if (words.size <= MAX_VISIBLE) {
            0
        } else {
            SuggestionStripLayout.firstVisibleIndex(scrollPx, cellWidth)
        }
        val remainder = if (words.size <= MAX_VISIBLE) {
            0f
        } else {
            SuggestionStripLayout.remainderPx(scrollPx, cellWidth)
        }
        for (slot in -1 until MAX_VISIBLE + 1) {
            val index = if (words.size <= MAX_VISIBLE) {
                SuggestionPlacement.candidateAtSlot(words.size, slot) ?: continue
            } else {
                (origin + slot).takeIf { it in words.indices } ?: continue
            }
            val left = windowLeft + slot * cellWidth - remainder

            if (index == pressedIndex) {
                pressedPaint.color = keyboardTheme.keyPressedOverlay
                val inset = dp(4f)
                canvas.drawRoundRect(
                    left + inset,
                    inset,
                    left + cellWidth - inset,
                    height - inset,
                    dp(12f),
                    dp(12f),
                    pressedPaint,
                )
            }

            // The top choice is the one that gets committed, so it is marked as such: the user
            // should be able to tell at a glance whether the keyboard already agrees with them.
            // Accent colour on top of weight, because bold alone disappears at 16sp in peripheral
            // vision while a colour change reads without focusing on the strip.
            textPaint.color =
                if (index == 0) keyboardTheme.suggestionHighlightText else keyboardTheme.suggestionText
            textPaint.typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            val label = ellipsized(words[index].word, cellWidth - dp(12f), bold = index == 0)
            canvas.drawText(label, left + cellWidth / 2f, baseline, textPaint)

            // A typo fix and a word completion look identical otherwise: same cell, same weight,
            // same colour. The underline says the keyboard thinks what you typed was wrong, which
            // is the one thing you need to know before it silently changes the word at the space.
            // Drawn in the same accent as the top-choice colour, because the two marks answer the
            // same question and should not look like two different systems.
            if (words[index].isCorrection) {
                markPaint.color = keyboardTheme.suggestionHighlightText
                markPaint.strokeWidth = dp(2f)
                val half = minOf(textPaint.measureText(label), cellWidth - dp(12f)) / 2f
                val underlineY = baseline + dp(3f)
                canvas.drawLine(
                    left + cellWidth / 2f - half,
                    underlineY,
                    left + cellWidth / 2f + half,
                    underlineY,
                    markPaint,
                )
            }
        }
        val inset = dp(10f)
        dividerPaint.color = keyboardTheme.divider
        for (slot in 1 until MAX_VISIBLE) {
            val x = windowLeft + slot * cellWidth - scrollPx
            canvas.drawLine(x, inset, x, height - inset, dividerPaint)
        }
        canvas.restore()
    }

    /** Shared chrome for the empty-strip toolbar buttons: circle, pressed overlay, then glyph. */
    private inline fun drawToolbarButton(
        canvas: Canvas,
        centerX: Float,
        pressed: Boolean,
        glyph: (centerX: Float, centerY: Float) -> Unit,
    ) {
        val centerY = height / 2f
        pressedPaint.color = keyboardTheme.specialKeyBackground
        canvas.drawCircle(centerX, centerY, height * 0.36f, pressedPaint)
        if (pressed) {
            pressedPaint.color = keyboardTheme.keyPressedOverlay
            canvas.drawCircle(centerX, centerY, height * 0.4f, pressedPaint)
        }
        iconPaint.color = keyboardTheme.suggestionText
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = dp(2f)
        glyph(centerX, centerY)
    }

    /** A clipboard: rounded body with the clip tab on top. */
    private fun drawClipboardGlyph(canvas: Canvas, centerX: Float, centerY: Float) {
        val halfWidth = dp(6f)
        val halfHeight = dp(7.5f)
        canvas.drawRoundRect(
            centerX - halfWidth,
            centerY - halfHeight + dp(1.5f),
            centerX + halfWidth,
            centerY + halfHeight,
            dp(2f),
            dp(2f),
            iconPaint,
        )
        canvas.drawRoundRect(
            centerX - dp(3f),
            centerY - halfHeight - dp(1f),
            centerX + dp(3f),
            centerY - halfHeight + dp(3f),
            dp(1.5f),
            dp(1.5f),
            iconPaint,
        )
    }

    /** A text cursor I-beam, the least ambiguous "edit text" mark at strip size. */
    private fun drawEditGlyph(canvas: Canvas, centerX: Float, centerY: Float) {
        val halfHeight = dp(8f)
        val serif = dp(3.5f)
        canvas.drawLine(centerX, centerY - halfHeight, centerX, centerY + halfHeight, iconPaint)
        canvas.drawLine(
            centerX - serif, centerY - halfHeight, centerX + serif, centerY - halfHeight, iconPaint,
        )
        canvas.drawLine(
            centerX - serif, centerY + halfHeight, centerX + serif, centerY + halfHeight, iconPaint,
        )
    }

    /**
     * The emoji button, where the board's own emoji key used to reach.
     *
     * It sits one square in from the microphone, or at the edge itself while voice is off, so the
     * right end of the strip reads the same way it did when these were keys: language further in,
     * emoji nearer the thumb.
     */
    private fun drawEmojiButton(canvas: Canvas) {
        drawToolbarButton(canvas, centerX = squareLeft(1) + height / 2f, pressed = emojiPressed) { x, y ->
            EmojiGlyph.draw(canvas, iconPaint, x, y, height * 0.25f)
        }
    }

    /** The language switch, on the strip because the board no longer carries a globe. */
    private fun drawLanguageButton(canvas: Canvas) {
        drawToolbarButton(canvas, centerX = squareLeft(2) + height / 2f, pressed = languagePressed) { x, y ->
            GlobeGlyph.draw(canvas, iconPaint, x, y, height * 0.25f)
        }
    }

    /**
     * The `?123` key, one square further in again.
     *
     * The honeycomb's bottom row gave its cell up so the row could be seven cells and sit a hex in
     * from each edge; the symbols page still has to be reachable, and a mode key belongs beside the
     * other mode keys rather than on the board.
     */
    private fun drawSymbolsButton(canvas: Canvas) {
        val label = if (lettersPageOffered) "ABC" else "?123"
        drawToolbarButton(canvas, centerX = squareLeft(3) + height / 2f, pressed = symbolsPressed) { x, y ->
            textPaint.color = keyboardTheme.suggestionText
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = height * 0.30f
            textPaint.getFontMetrics(reusableMetrics)
            canvas.drawText(
                label,
                x,
                y - (reusableMetrics.ascent + reusableMetrics.descent) / 2f,
                textPaint,
            )
        }
    }

    /**
     * The microphone lives here rather than in the bottom key row, as in Gboard.
     *
     * The alternative is taking width from the space bar, which is the most-hit key on the
     * keyboard and the one that can least afford to shrink. Here it is always reachable and never
     * competes with a letter.
     */
    private fun drawMicButton(canvas: Canvas) {
        if (!voiceEnabled) return
        val centerX = SuggestionStripLayout.micLeft(
            width.toFloat(),
            height.toFloat(),
            voiceEnabled,
            trailingSquares(),
        ) + height / 2f
        val centerY = height / 2f

        // A stable circular control separates voice input from suggestions without the hard
        // vertical divider that made the glyph look bolted onto the edge of the strip.
        pressedPaint.color = keyboardTheme.specialKeyBackground
        canvas.drawCircle(centerX, centerY, height * 0.36f, pressedPaint)
        if (micPressed) {
            pressedPaint.color = keyboardTheme.keyPressedOverlay
            canvas.drawCircle(centerX, centerY, height * 0.40f, pressedPaint)
        }

        textPaint.color = keyboardTheme.suggestionText
        MicGlyph.draw(canvas, textPaint, centerX, centerY, height * 0.25f)
    }

    /** Left edge of the microphone's square: the first one inside the trailing buttons. */
    private fun micLeft(): Float = SuggestionStripLayout.micLeft(
        width.toFloat(),
        height.toFloat(),
        voiceEnabled,
        trailingSquares(),
    )

    /**
     * Squares reserved after the candidates: the emoji, language and symbols buttons. The
     * microphone is not among them any more - it sits at the other end, in the square the settings
     * button held - so the candidate window gains that whole square of width.
     *
     * The candidate window and every hit test read this one number, so a change to what the strip
     * carries cannot leave the words drawing over a button or a button sitting under a word.
     */
    private fun trailingSquares(): Int = SuggestionStripLayout.trailingSquares()

    /** Left edge of the square [fromEdge] steps in from the right edge, counting the edge as 1. */
    private fun squareLeft(fromEdge: Int): Float =
        SuggestionStripLayout.squareLeft(width.toFloat(), height.toFloat(), fromEdge)

    private fun squareRight(fromEdge: Int): Float =
        SuggestionStripLayout.squareRight(width.toFloat(), height.toFloat(), fromEdge)

    private fun squareWidth(): Float = height.toFloat()

    private fun suggestionLeft(): Float =
        SuggestionStripLayout.suggestionLeft(height.toFloat())

    private fun suggestionWidth(): Float =
        SuggestionStripLayout.suggestionWidth(
            width.toFloat(),
            height.toFloat(),
            voiceEnabled,
            trailingSquares(),
        )

    /** The touch that owns the current press, so a resting second finger cannot steal or fire it. */
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                dragStartX = event.x
                dragStartPx = scrollPx
                dragging = false
                clipboardPressed = isOverClipboard(event.x)
                editPressed = !clipboardPressed && isOverEdit(event.x)
                micPressed = !clipboardPressed && !editPressed && isOverMic(event.x)
                emojiPressed =
                    !clipboardPressed && !editPressed && !micPressed && isOverEmoji(event.x)
                languagePressed =
                    !clipboardPressed && !editPressed && !micPressed && !emojiPressed &&
                        isOverLanguage(event.x)
                symbolsPressed =
                    !clipboardPressed && !editPressed && !micPressed && !emojiPressed &&
                        !languagePressed && isOverSymbols(event.x)
                val overButton =
                    clipboardPressed || editPressed || micPressed ||
                        emojiPressed || languagePressed || symbolsPressed
                pressedIndex = if (overButton) -1 else indexAt(event.x)
                longPressFired = false
                if (pressedIndex >= 0) scheduleLongPress(pressedIndex)
                invalidate()
                // A press that starts on the empty part of the window is a drag waiting to happen,
                // and it has to be claimed here or no move event ever arrives. It used to be claimed
                // only over a word, so pressing the space beside one scrolled nothing at all.
                val dragWindow = !overButton && SuggestionStripLayout.withinWindow(
                    x = event.x,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    voiceEnabled = voiceEnabled,
                    trailingSquares = trailingSquares(),
                )
                if (dragWindow) parent?.requestDisallowInterceptTouchEvent(true)
                return overButton || pressedIndex >= 0 || dragWindow
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // A strip has no second target: whatever was pressed ends here, so neither the
                // original finger nor the new one can fire through the other's lift later.
                releasePress()
            }

            MotionEvent.ACTION_MOVE -> {
                // Sliding off cancels the press, matching how the keys behave. Coordinates come
                // from the pressing finger, not index 0, which may belong to a different touch.
                val (x, y) = activeCoordinates(event) ?: return true
                // A horizontal drag scrolls the candidates instead of pressing one. The press is
                // dropped the moment the drag is claimed, so a scroll can never commit the word the
                // thumb happened to start on: the strip is the one place where a slip would type a
                // word the user never chose.
                if (
                    words.size > MAX_VISIBLE &&
                    !clipboardPressed &&
                    !editPressed &&
                    !micPressed &&
                    !emojiPressed &&
                    !languagePressed &&
                    !symbolsPressed
                ) {
                    if (!dragging && kotlin.math.abs(x - dragStartX) > dp(8f)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        releasePress()
                    }
                    if (dragging) {
                        // One pixel of thumb, one pixel of words. The strip used to move a whole cell
                        // per step, so a flick shorter than a cell produced no movement at all and
                        // the gesture read as dead.
                        scrollPx = SuggestionStripLayout.draggedPx(
                            startPx = dragStartPx,
                            fingerDownX = dragStartX,
                            fingerX = x,
                            maxPx = maxScrollPx(),
                        )
                        invalidate()
                        return true
                    }
                }
                val inBounds = y in 0f..height.toFloat()
                val stillOnClipboard = clipboardPressed && isOverClipboard(x) && inBounds
                val stillOnEdit = editPressed && isOverEdit(x) && inBounds
                val stillOnMic = micPressed && isOverMic(x) && inBounds
                val stillOnEmoji = emojiPressed && isOverEmoji(x) && inBounds
                val stillOnLanguage = languagePressed && isOverLanguage(x) && inBounds
                val stillOnSymbols = symbolsPressed && isOverSymbols(x) && inBounds
                val stillOnWord = pressedIndex >= 0 && indexAt(x) == pressedIndex && inBounds
                if (
                    clipboardPressed != stillOnClipboard ||
                    editPressed != stillOnEdit ||
                    micPressed != stillOnMic ||
                    emojiPressed != stillOnEmoji ||
                    languagePressed != stillOnLanguage ||
                    symbolsPressed != stillOnSymbols ||
                    (pressedIndex >= 0 && !stillOnWord)
                ) {
                    clipboardPressed = stillOnClipboard
                    editPressed = stillOnEdit
                    micPressed = stillOnMic
                    emojiPressed = stillOnEmoji
                    languagePressed = stillOnLanguage
                    symbolsPressed = stillOnSymbols
                    if (!stillOnWord) {
                        pressedIndex = -1
                        cancelPendingLongPress()
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Only the pressing finger's lift finishes its tap. Another finger lifting must
                // leave the armed hold and pressed flags exactly as they are.
                if (!StripPointerRouting.liftIsAuthoritative(
                        activePointerId,
                        event.getPointerId(event.actionIndex),
                    )
                ) {
                    return true
                }
                if (event.findPointerIndex(activePointerId) < 0) {
                    releasePress()
                    return true
                }
                finishTap()
            }

            MotionEvent.ACTION_UP -> {
                finishTap()
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                settleScroll()
                releasePress()
            }
        }
        return true
    }

    private fun activeCoordinates(event: MotionEvent): Pair<Float, Float>? {
        val index = event.findPointerIndex(activePointerId)
        return if (index >= 0) event.getX(index) to event.getY(index) else null
    }

    /** Resolves the current press: fires whichever control the pressing finger still holds. */
    private fun finishTap() {
        parent?.requestDisallowInterceptTouchEvent(false)
        settleScroll()
        val index = pressedIndex
        val clipboard = clipboardPressed
        val edit = editPressed
        val mic = micPressed
        val emoji = emojiPressed
        val language = languagePressed
        val symbols = symbolsPressed
        val held = longPressFired
        releasePress()

        if (clipboard) {
            announceForAccessibility("Clipboard")
            listener?.onClipboardRequested()
        } else if (edit) {
            announceForAccessibility("Edit text")
            listener?.onTextEditRequested()
        } else if (mic) {
            announceForAccessibility("Voice typing")
            listener?.onVoiceRequested()
        } else if (emoji) {
            announceForAccessibility("Emoji")
            listener?.onEmojiRequested()
        } else if (language) {
            announceForAccessibility("Next language")
            listener?.onLanguageRequested()
        } else if (symbols) {
            announceForAccessibility("Symbols and numbers")
            listener?.onSymbolsRequested()
        } else if (index in words.indices && !held) {
            announceForAccessibility("Suggestion ${words[index].word}")
            listener?.onSuggestionPicked(index, words[index].word)
        }
    }

    /** Clears every pressed flag and the armed hold without firing anything. */
    private fun releasePress() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pressedIndex = -1
        clipboardPressed = false
        editPressed = false
        micPressed = false
        emojiPressed = false
        languagePressed = false
        symbolsPressed = false
        cancelPendingLongPress()
        invalidate()
    }

    private fun scheduleLongPress(index: Int) {
        cancelPendingLongPress()
        val runnable = Runnable {
            if (index !in words.indices) return@Runnable
            longPressFired = true
            pressedIndex = -1
            invalidate()
            listener?.onSuggestionHeld(index, words[index].word)
        }
        longPressRunnable = runnable
        handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let(handler::removeCallbacks)
        longPressRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingLongPress()
    }

    private fun isOverMic(x: Float): Boolean {
        if (!voiceEnabled) return false
        val left = SuggestionStripLayout.micLeft(
            width.toFloat(),
            height.toFloat(),
            voiceEnabled,
            trailingSquares(),
        )
        return x >= left && x < left + squareWidth()
    }

    /** The emoji square: one in from the edge, or at the edge itself when the microphone is away. */
    private fun isOverEmoji(x: Float): Boolean =
        x >= squareLeft(1) && x < squareRight(1)

    /** The language square, one further in again. */
    private fun isOverLanguage(x: Float): Boolean =
        x >= squareLeft(2) && x < squareRight(2)

    /** The symbols square, the innermost of the four on the right end. */
    private fun isOverSymbols(x: Float): Boolean =
        x >= squareLeft(3) && x < squareRight(3)

    /**
     * Whether the clipboard and text-editing shortcuts are on the strip.
     *
     * They borrow the space candidates use, exactly as Gboard's toolbar does: present while
     * there is nothing to suggest, gone the moment candidates need the room.
     */
    private val toolbarShown: Boolean get() = words.isEmpty()

    /**
     * Which square the strip's left-hand group starts on.
     *
     * The microphone holds square nought while it is on, so the clipboard and text-editing icons
     * begin one square in. They began there whether or not it was on, which left square nought
     * empty the moment the microphone was switched off - a hole at the end of the strip with the
     * two icons sitting in from the edge. Whichever controls are on the left are packed against the
     * edge now, and the candidates start after them.
     */
    private fun leftGroupStart(): Float = SuggestionStripLayout.leftGroupStart()

    private fun isOverClipboard(x: Float): Boolean =
        toolbarShown &&
            x >= squareWidth() * leftGroupStart() &&
            x < squareWidth() * (leftGroupStart() + 1f)

    private fun isOverEdit(x: Float): Boolean =
        toolbarShown &&
            x >= squareWidth() * (leftGroupStart() + 1f) &&
            x < squareWidth() * (leftGroupStart() + 2f)

    /** The candidate drawn in [slot], or null when the window runs off the end of the list. */
    private fun candidateAtSlot(slot: Int): Int? =
        if (words.size <= MAX_VISIBLE) {
            SuggestionPlacement.candidateAtSlot(words.size, slot)
        } else {
            SuggestionStripLayout.indexForSlot(slot, scrollPx, cellWidth(), words.size)
        }

    private fun slotOfIndex(index: Int): Int? =
        if (words.size <= MAX_VISIBLE) {
            SuggestionPlacement.slotForCandidate(words.size, index)
                .takeIf { it in 0 until MAX_VISIBLE }
        } else {
            val origin = SuggestionStripLayout.firstVisibleIndex(scrollPx, cellWidth())
            (index - origin).takeIf { it in 0 until MAX_VISIBLE }
        }

    private fun indexAt(x: Float): Int {
        if (words.isEmpty()) return -1
        val index = SuggestionStripLayout.indexAt(
            x = x,
            width = width.toFloat(),
            height = height.toFloat(),
            voiceEnabled = voiceEnabled,
            slotCount = MAX_VISIBLE,
            trailingSquares = trailingSquares(),
            scrollPx = scrollPx,
        ) ?: return -1
        return index.takeIf { it in words.indices } ?: -1
    }

    /** Trims a word that will not fit its cell, so it degrades to "extraordi…" rather than clipping. */
    private fun ellipsize(word: String, available: Float): String {
        if (textPaint.measureText(word) <= available) return word
        var end = word.length
        while (end > 1 && textPaint.measureText(word.substring(0, end) + "…") > available) end--
        return word.substring(0, end) + "…"
    }

    /**
     * [ellipsize], memoized per word and paint weight.
     *
     * The measure-and-trim loop runs string concatenations and text measurement inside [onDraw],
     * which repaints on every press highlight; the same three words do not need re-trimming each
     * frame. Cleared when the words change and when the cell width does.
     */
    private fun ellipsized(word: String, available: Float, bold: Boolean): String {
        if (available != ellipsizedWidth) {
            ellipsizedNormal.clear()
            ellipsizedBold.clear()
            ellipsizedWidth = available
        }
        val cache = if (bold) ellipsizedBold else ellipsizedNormal
        return cache.getOrPut(word) { ellipsize(word, available) }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.view.View"
        info.isFocusable = true
        info.contentDescription = accessibilityDescription()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    private fun refreshAccessibilityDescription() {
        contentDescription = accessibilityDescription()
        accessibilityHelper.invalidateRoot()
    }

    private fun accessibilityDescription(): String = buildList {
        if (words.isNotEmpty()) add("Suggestions: ${words.joinToString(", ") { it.word }}")
        else if (emptyMessage.isNotEmpty()) add(emptyMessage)
        if (words.isEmpty()) add("Clipboard and edit text buttons at the left")
        if (voiceEnabled) add("Voice typing button, inside the buttons at the right")
    }.joinToString(". ")

    private companion object {
        const val MAX_VISIBLE = 3

        /**
         * How many candidates the strip holds. Enough to scroll a long way through a syllable's
         * readings without keeping the whole table: `jia` alone has sixty-five.
         */
        const val HOLD_AT_MOST = 40
        const val HEIGHT_DP = 48f
        const val A11Y_WORD_BASE = 0
        const val A11Y_MIC = 100
        const val A11Y_CLIPBOARD = 102
        const val A11Y_EDIT = 103
        const val A11Y_EMOJI = 104
        const val A11Y_LANGUAGE = 105
        const val A11Y_SYMBOLS = 106
    }
}

/**
 * A candidate as the strip needs it: the word, and whether the keyboard is offering it as a fix.
 *
 * The two travel in one object rather than as parallel lists on purpose. The centre-first
 * placement moves a candidate between cells, and a separate array of flags is one index away from
 * underlining the wrong word the moment the two get out of step.
 */
data class StripSuggestion(val word: String, val isCorrection: Boolean = false)

/** Pure label lookup, kept out of the View so "is this cell a fix" stays a testable question. */
internal object SuggestionMarks {

    /**
     * True when the candidate [candidateAtSlot] puts in [slot] is a typo fix.
     *
     * The mark belongs to the candidate, not to the cell, so it is resolved through the same
     * placement mapping the drawing and hit-testing use.
     */
    fun isCorrectionAtSlot(candidates: List<StripSuggestion>, slot: Int): Boolean {
        val index = SuggestionPlacement.candidateAtSlot(candidates.size, slot) ?: return false
        return candidates[index].isCorrection
    }
}

/** Pure toolbar geometry, separated from Android drawing so edge hit targets stay regression-tested. */
internal object SuggestionStripLayout {
    /**
     * The squares to the left of the candidates: the microphone's, while voice typing is on. The
     * settings button held this square until it moved into the app's own settings screens, and the
     * width it held went to the suggestions rather than to a gap.
     */
    fun suggestionLeft(height: Float): Float = 0f

    /**
     * The trailing buttons' share of the strip: the emoji, language and symbols squares, and
     * nothing else. The candidate window reads it, and so does every hit test.
     */
    fun trailingSquares(): Int = ALWAYS_TRAILING

    /**
     * Those three squares are on the strip in every state, so they are what the arithmetic assumes
     * when a caller says nothing. A caller that carries a fourth button passes the count itself;
     * defaulting to zero instead ran the candidate window under the buttons, which is exactly the
     * mistake the hit-target test exists to catch.
     */
    private const val ALWAYS_TRAILING = 3

    /**
     * Left edge of the microphone's square: the first one inside the trailing buttons.
     *
     * The candidates are anchored at the strip's left edge, so with the button on this is also the
     * candidate window's own right edge. One function answers both the drawing and the hit test,
     * rather than two offsets that can drift apart. With voice typing off there is no seat, and the
     * whole width belongs to the words.
     */
    fun micLeft(
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        trailingSquares: Int = ALWAYS_TRAILING,
    ): Float = if (!voiceEnabled) {
        width
    } else {
        suggestionWidth(width, height, voiceEnabled, trailingSquares)
    }

    /** Left edge of the square [fromEdge] steps in from the right edge, counting the edge as 1. */
    fun squareLeft(width: Float, height: Float, fromEdge: Int): Float =
        width - height.coerceAtLeast(0f) * fromEdge

    /** Right edge of the same square. */
    fun squareRight(width: Float, height: Float, fromEdge: Int): Float =
        width - height.coerceAtLeast(0f) * (fromEdge - 1)

    /**
     * Every square reserved for a button must be counted exactly once, on the side it is drawn, or
     * the candidates are charged for a gap that is not there. The microphone was once counted twice
     * - as a trailing square in both this arithmetic and the `trailingSquares` the strip passed -
     * so the strip reserved six squares where it drew five, three candidates shared 374 px, a word
     * drawn into ninety-five px of that is `...`, and every suggestion on his phone was an ellipsis.
     */
    fun suggestionWidth(
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        trailingSquares: Int = ALWAYS_TRAILING,
    ): Float = (
        width -
            height.coerceAtLeast(0f) * ((if (voiceEnabled) 1 else 0) + trailingSquares)
        ).coerceAtLeast(0f)

    fun slotAt(
        x: Float,
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        slotCount: Int,
        trailingSquares: Int = ALWAYS_TRAILING,
    ): Int? {
        if (slotCount <= 0) return null
        val left = suggestionLeft(height)
        val contentWidth = suggestionWidth(width, height, voiceEnabled, trailingSquares)
        if (contentWidth <= 0f || x < left || x >= left + contentWidth) return null
        return ((x - left) / (contentWidth / slotCount)).toInt().coerceIn(0, slotCount - 1)
    }

    /**
     * Where the left-hand group begins: square nought with the microphone away, one square in with
     * it on. The controls on that side are packed against this edge, so switching the microphone off
     * closes the gap instead of leaving a hole with the icons set in from the edge.
     */
    fun leftGroupStart(): Float = 0f

    /**
     * How far the candidates have travelled after a drag.
     *
     * One pixel of finger, one pixel of words: [startPx] is where the list sat when the finger went
     * down, and the movement is subtracted because dragging left brings later candidates in. The old
     * arithmetic stepped whole cells here, so a flick shorter than one cell moved nothing and the
     * strip read as unscrollable.
     */
    fun draggedPx(startPx: Float, fingerDownX: Float, fingerX: Float, maxPx: Float): Float =
        (startPx - (fingerX - fingerDownX)).coerceIn(0f, maxPx.coerceAtLeast(0f))

    /**
     * The first candidate whose left edge has passed the window's left edge.
     *
     * Drawing used to pair this with the full [scrollPx] as if they were the same unit: a slot
     * index times a cell, minus a pixel offset. After a one-cell settle that placed the next word
     * one cell *left* of the window, so a swipe either snapped back to the same three words or
     * skipped one. Floor of pixels-over-cell is the origin; the leftover pixels are the remainder
     * [remainderPx] draws with.
     */
    fun firstVisibleIndex(scrollPx: Float, cell: Float): Int {
        if (cell <= 0f) return 0
        return floor(scrollPx / cell).toInt().coerceAtLeast(0)
    }

    /** How far into the current cell [scrollPx] sits, in pixels. Always in `[0, cell)`. */
    fun remainderPx(scrollPx: Float, cell: Float): Float {
        if (cell <= 0f) return 0f
        return scrollPx - firstVisibleIndex(scrollPx, cell) * cell
    }

    /** The candidate drawn in [slot] once the list has travelled [scrollPx]. */
    fun indexForSlot(slot: Int, scrollPx: Float, cell: Float, count: Int): Int? {
        val index = firstVisibleIndex(scrollPx, cell) + slot
        return index.takeIf { it in 0 until count }
    }

    /**
     * Left edge of [slot] relative to the window, after [scrollPx]. Zero when a settle has lined
     * that slot up with the window's left edge.
     */
    fun slotLeft(slot: Int, scrollPx: Float, cell: Float): Float =
        slot * cell - remainderPx(scrollPx, cell)

    /** Whether [x] is in the candidate window rather than in one of the controls' squares. */
    fun withinWindow(
        x: Float,
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        trailingSquares: Int = ALWAYS_TRAILING,
    ): Boolean {
        val left = suggestionLeft(height)
        val contentWidth = suggestionWidth(width, height, voiceEnabled, trailingSquares)
        return contentWidth > 0f && x >= left && x < left + contentWidth
    }

    /**
     * The candidate under [x] once the list is scrolled by [scrollPx], or null outside the window.
     *
     * The result may be past the end of the list: this knows the window's arithmetic, not how many
     * candidates there are, and bounding it is the caller's job. It is a position in the whole list
     * rather than a slot, because a drag can leave the window resting between two candidates.
     */
    fun indexAt(
        x: Float,
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        slotCount: Int,
        trailingSquares: Int = ALWAYS_TRAILING,
        scrollPx: Float = 0f,
    ): Int? {
        if (slotCount <= 0) return null
        val left = suggestionLeft(height)
        val contentWidth = suggestionWidth(width, height, voiceEnabled, trailingSquares)
        if (contentWidth <= 0f || x < left || x >= left + contentWidth) return null
        val cell = contentWidth / slotCount
        if (cell <= 0f) return null
        return floor((x - left + scrollPx) / cell).toInt()
    }

    /** How far the list travels before its last candidate reaches the far edge of the window. */
    fun maxScrollPx(
        width: Float,
        height: Float,
        voiceEnabled: Boolean,
        slotCount: Int,
        count: Int,
        trailingSquares: Int = ALWAYS_TRAILING,
    ): Float {
        if (slotCount <= 0) return 0f
        val cell = suggestionWidth(width, height, voiceEnabled, trailingSquares) / slotCount
        return (count - slotCount).coerceAtLeast(0) * cell
    }
}

/** Pure candidate-to-cell mapping, kept separate so the centre-first interaction is testable. */
internal object SuggestionPlacement {
    private val THREE = intArrayOf(1, 0, 2)
    private val TWO = intArrayOf(1, 0, NONE)
    private val ONE = intArrayOf(NONE, 0, NONE)

    fun candidateAtSlot(candidateCount: Int, slot: Int): Int? {
        if (slot !in 0..2) return null
        val candidate = when (candidateCount.coerceIn(0, 3)) {
            1 -> ONE[slot]
            2 -> TWO[slot]
            3 -> THREE[slot]
            else -> NONE
        }
        return candidate.takeIf { it != NONE }
    }

    fun slotForCandidate(candidateCount: Int, candidateIndex: Int): Int {
        for (slot in 0..2) {
            if (candidateAtSlot(candidateCount, slot) == candidateIndex) return slot
        }
        return 1
    }

    private const val NONE = -1
}

/**
 * Which finger owns the strip's single press.
 *
 * The strip has one press slot but any number of fingers. Kept outside the View so the
 * ownership rule — the part that was wrong when a resting second thumb could steal or fire
 * another finger's tap — stays regression-tested.
 */
internal object StripPointerRouting {

    /**
     * True when [liftedPointerId] is the pressing finger's own lift, the only one allowed to
     * resolve anything. A press that has already ended (invalid active id) owns no lift either,
     * so a trailing finger's UP stays a harmless no-op.
     */
    fun liftIsAuthoritative(activePointerId: Int, liftedPointerId: Int): Boolean =
        activePointerId != MotionEvent.INVALID_POINTER_ID && activePointerId == liftedPointerId
}
