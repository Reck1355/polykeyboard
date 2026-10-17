package com.slide.ime.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The language switch's button: a globe, drawn rather than typed as a character.
 *
 * Same reason as [EmojiGlyph]: the emoji globe renders as whatever the font decides, and one of the
 * two globes on the keyboard arrived colourless while the other did not. A drawn globe is the same
 * mark on every device. Leaves [paint] as it found it, since callers reuse one paint.
 */
object GlobeGlyph {

    fun draw(canvas: Canvas, paint: Paint, centerX: Float, centerY: Float, radius: Float) {
        val style = paint.style
        val strokeWidth = paint.strokeWidth

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.16f
        val r = radius * 0.78f
        canvas.drawCircle(centerX, centerY, r, paint)

        // Equator and one meridian. Outlines only: a filled pass at this size reads as a blob
        // rather than as a globe, which is the whole reason this is drawn instead of typed.
        canvas.drawLine(centerX - r, centerY, centerX + r, centerY, paint)
        canvas.drawOval(
            RectF(centerX - r * 0.46f, centerY - r, centerX + r * 0.46f, centerY + r),
            paint,
        )

        paint.style = style
        paint.strokeWidth = strokeWidth
    }
}
