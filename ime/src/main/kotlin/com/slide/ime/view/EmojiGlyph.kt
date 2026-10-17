package com.slide.ime.view

import android.graphics.Canvas
import android.graphics.Paint

/**
 * The emoji panel's button: a face, drawn rather than typed as a character.
 *
 * Drawn for the same reason the microphone is: a font's smiley arrives at whatever size and weight
 * the font chose, and the toolbar needs one icon that sits at the same optical weight as the glyphs
 * beside it on every device. Leaves [paint] as it found it, since callers reuse one paint.
 */
object EmojiGlyph {

    fun draw(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, radius: Float) {
        val style = paint.style
        val strokeWidth = paint.strokeWidth

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.16f
        canvas.drawCircle(centerX, centerY, radius * 0.78f, paint)

        // Two eyes and a smile, sized off the radius so the whole face is one instruction: whatever
        // the toolbar's height, the features keep their proportion to it.
        paint.style = Paint.Style.FILL
        val eyeOffset = radius * 0.30f
        val eyeRadius = radius * 0.11f
        canvas.drawCircle(centerX - eyeOffset, centerY - radius * 0.18f, eyeRadius, paint)
        canvas.drawCircle(centerX + eyeOffset, centerY - radius * 0.18f, eyeRadius, paint)

        paint.style = Paint.Style.STROKE
        canvas.drawArc(
            centerX - radius * 0.42f,
            centerY - radius * 0.22f,
            centerX + radius * 0.42f,
            centerY + radius * 0.60f,
            20f,
            140f,
            false,
            paint,
        )

        paint.style = style
        paint.strokeWidth = strokeWidth
    }
}
