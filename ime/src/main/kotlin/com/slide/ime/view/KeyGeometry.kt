package com.slide.ime.view

import com.slide.core.layout.Key
import com.slide.core.layout.KeyboardLayout
import kotlin.math.abs
import kotlin.math.min

/** A key with its resolved on-screen bounds. Bounds are the full touch cell, gaps included. */
class PlacedKey(
    val key: Key,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Index of the row this key belongs to, for row-aware hit testing. */
    val row: Int,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    /** Squared distance from (x, y) to the nearest point of this key's cell; 0 when inside. */
    fun squaredDistanceTo(x: Float, y: Float): Float {
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return dx * dx + dy * dy
    }
}

object KeyGeometry {

    /**
     * Places every key of [layout] into [width] x [contentHeight], starting at [topOffset].
     *
     * Cells tile the surface without gaps so that no touch lands "between" keys — the visual gap is
     * applied at draw time by insetting, not by shrinking the touch target.
     */
    fun place(
        layout: KeyboardLayout,
        width: Float,
        contentHeight: Float,
        topOffset: Float = 0f,
    ): List<PlacedKey> {
        val placed = ArrayList<PlacedKey>(layout.rows.sumOf { it.keys.size })
        val totalHeightWeight = layout.rows.sumOf { it.heightWeight.toDouble() }.toFloat()
        if (totalHeightWeight <= 0f || width <= 0f || contentHeight <= 0f) return placed

        val unitWidth = width / layout.widthUnits
        var y = topOffset

        layout.rows.forEachIndexed { rowIndex, row ->
            val rowHeight = contentHeight * (row.heightWeight / totalHeightWeight)
            var x = row.leadingGap * unitWidth

            row.keys.forEach { key ->
                val keyWidth = key.widthWeight * unitWidth
                placed += PlacedKey(
                    key = key,
                    left = x,
                    top = y,
                    right = x + keyWidth,
                    bottom = y + rowHeight,
                    row = rowIndex,
                )
                x += keyWidth
            }
            y += rowHeight
        }
        return placed
    }

    /**
     * Finds the key under (x, y), falling back to the nearest key when the touch lands in the
     * leading/trailing gap of a row.
     *
     * The fallback is deliberately row-local and bounded. The view can contain intentional blank
     * space above or below the placed keys (most notably the user-configurable bottom padding),
     * and treating that as the nearest key makes a tap on empty chrome type a character. Likewise,
     * a pointer dragged well past a row must be able to cancel instead of snapping back to an edge
     * key no matter how far away it is.
     */
    fun hitTest(keys: List<PlacedKey>, x: Float, y: Float): PlacedKey? {
        keys.firstOrNull { it.contains(x, y) }?.let { return it }

        var nearest: PlacedKey? = null
        var nearestDistance = Float.POSITIVE_INFINITY
        for (candidate in keys) {
            if (y < candidate.top || y >= candidate.bottom) continue
            val distance = candidate.squaredDistanceTo(x, y)
            if (distance < nearestDistance) {
                nearest = candidate
                nearestDistance = distance
            }
        }
        val resolved = nearest ?: return null
        // y is already inside this row, so only horizontal distance remains. Half a key accepts
        // the largest built-in half-unit indent while still bounding the outer edge.
        val maximumFallback = resolved.width * MAX_FALLBACK_KEY_FRACTION
        return resolved.takeIf { nearestDistance <= maximumFallback * maximumFallback }
    }

    /** Keys whose cell the point falls within, restricted to gesture-eligible letter keys. */
    fun gestureKeyAt(keys: List<PlacedKey>, x: Float, y: Float): Key? =
        keys.firstOrNull { it.contains(x, y) && it.key.gestureEligible }?.key

    /**
     * The hex keycap drawn for [placed], and the shape a touch resolves against.
     *
     * One shape serves both jobs on purpose. A cap that is drawn from one set of numbers and hit
     * tested from another is the failure this geometry exists to prevent: the finger lands where
     * the eye says the key is, and the keyboard disagrees by a few pixels that no test can see.
     *
     * The cell stays the layout's unit — positions are [placed]'s and nothing here moves them —
     * so the cap is derived from the cell rather than replacing it.
     */
    fun hexShapeFor(placed: PlacedKey): HexShape {
        val halfHeight = placed.height * 0.5f * HexKeyMetrics.HEIGHT_FRACTION
        return HexShape(
            centerX = placed.centerX,
            centerY = placed.centerY,
            halfWidth = placed.width * 0.5f * HexKeyMetrics.WIDTH_FRACTION,
            halfHeight = halfHeight,
            shoulderHalfHeight = halfHeight * HexKeyMetrics.SHOULDER_HEIGHT_FRACTION,
        )
    }

    /**
     * Finds the key whose hex cap holds (x, y), falling back to the nearest cap within half a key.
     *
     * The caps are the targets now, not the tiling cells, and that is a real change of behaviour:
     * the notch between two caps of a row sits under the point of a cap from the row above, so a
     * finger aimed there gets the key whose drawn point it is touching rather than the key whose
     * rectangle happens to contain it. [hitTest] still answers the cell question for the callers
     * that want it.
     *
     * Two rules keep the caps from swallowing the rest of the view:
     *
     *  - The touch must land within the rows themselves. Every cap overhangs the row above and
     *    below by a sixth of a row height, which is what makes them interlock, and the top strip
     *    and the user's bottom padding live inside that overhang. That padding is deliberate
     *    breathing room, so a tap there types nothing — the same promise the cell hit test makes.
     *  - The fallback is bounded, as [hitTest]'s is. Half a key reaches the deliberate row indent
     *    and no further, so a finger dragged clear of the keyboard cancels instead of snapping
     *    back to an edge key.
     *
     * Where two caps do overlap the point — which takes a layout whose rows are not staggered by
     * the honeycomb's half key, as QWERTY's second and third letter rows are — the nearest centre
     * owns it and an exact tie keeps the earlier key. Caps never overlap sideways within a row:
     * neighbours share an edge and nothing more.
     */
    fun hitTestHex(keys: List<PlacedKey>, x: Float, y: Float): PlacedKey? {
        if (keys.isEmpty()) return null
        if (y < keys.minOf { it.top } || y >= keys.maxOf { it.bottom }) return null

        var containing: PlacedKey? = null
        var containingCentreDistance = Float.POSITIVE_INFINITY
        var nearest: PlacedKey? = null
        var nearestDistance = Float.POSITIVE_INFINITY

        for (candidate in keys) {
            val shape = hexShapeFor(candidate)
            val distance = shape.squaredDistanceTo(x, y)
            if (distance <= 0f) {
                val dx = x - shape.centerX
                val dy = y - shape.centerY
                val centreDistance = dx * dx + dy * dy
                if (centreDistance < containingCentreDistance) {
                    containing = candidate
                    containingCentreDistance = centreDistance
                }
                continue
            }
            if (distance < nearestDistance) {
                nearest = candidate
                nearestDistance = distance
            }
        }

        containing?.let { return it }
        val resolved = nearest ?: return null
        val maximumFallback = resolved.width * HexKeyMetrics.MAX_FALLBACK_KEY_FRACTION
        return resolved.takeIf { nearestDistance <= maximumFallback * maximumFallback }
    }

    /** Half a key covers the deliberate half-unit row indents without accepting distant taps. */
    private const val MAX_FALLBACK_KEY_FRACTION = 0.5f
}

/** One corner of a hex keycap, in the keyboard view's pixels. */
data class HexVertex(val x: Float, val y: Float)

/**
 * The hexagon drawn for one key, and the shape a touch resolves against.
 *
 * Pointy ends top and bottom, straight edges left and right. That orientation is the one whose
 * tiling staggers by half a key width, which is exactly how the QWERTY letter rows are laid out —
 * so caps of neighbouring rows interlock instead of merely sitting above one another.
 *
 * Bounds are absolute: the cap is anchored at the cell's centre, so a key's cap always covers its
 * key and never migrates with a neighbour's size.
 */
class HexShape(
    /** Centre of the cap, which is the centre of the key's touch cell. */
    val centerX: Float,
    val centerY: Float,
    /** Half the cap's width: the distance from its centre to its left and right edges. */
    val halfWidth: Float,
    /** Half the cap's height: the distance from its centre to its pointy ends. */
    val halfHeight: Float,
    /** Half the height of the straight left/right edge; the remainder is the two points. */
    val shoulderHalfHeight: Float,
) {
    val left: Float get() = centerX - halfWidth
    val right: Float get() = centerX + halfWidth
    val top: Float get() = centerY - halfHeight
    val bottom: Float get() = centerY + halfHeight
    val shoulderTop: Float get() = centerY - shoulderHalfHeight
    val shoulderBottom: Float get() = centerY + shoulderHalfHeight

    /** The six corners, clockwise from the top point — the order a drawn path wants them in. */
    fun vertices(): List<HexVertex> = listOf(
        HexVertex(centerX, top),
        HexVertex(right, shoulderTop),
        HexVertex(right, shoulderBottom),
        HexVertex(centerX, bottom),
        HexVertex(left, shoulderBottom),
        HexVertex(left, shoulderTop),
    )

    /**
     * True when (x, y) is inside the cap or on its outline.
     *
     * Between the shoulder and a point the cap narrows linearly, so the test is the width the
     * outline still has at that height — exact, because the sides are straight, rather than a
     * sampled approximation that would need a fudge factor at the corners.
     */
    fun contains(x: Float, y: Float): Boolean {
        val dx = abs(x - centerX)
        if (dx > halfWidth) return false
        val dy = abs(y - centerY)
        if (dy > halfHeight) return false
        if (dy <= shoulderHalfHeight) return true
        val taper = halfHeight - shoulderHalfHeight
        if (taper <= 0f) return false
        return dx <= halfWidth * (halfHeight - dy) / taper
    }

    /**
     * Squared distance from (x, y) to the cap's outline; 0 when inside.
     *
     * Measured to the six edges rather than to the centre, because that is what the fallback
     * needs: it prices a touch in the gap between two caps by how far it is from the cap it is
     * reaching for, and on a wide key like space the centre is half a bar away from every edge.
     */
    fun squaredDistanceTo(x: Float, y: Float): Float {
        if (contains(x, y)) return 0f
        var best = squaredDistanceToSegment(x, y, centerX, top, right, shoulderTop)
        best = min(best, squaredDistanceToSegment(x, y, right, shoulderTop, right, shoulderBottom))
        best = min(best, squaredDistanceToSegment(x, y, right, shoulderBottom, centerX, bottom))
        best = min(best, squaredDistanceToSegment(x, y, centerX, bottom, left, shoulderBottom))
        best = min(best, squaredDistanceToSegment(x, y, left, shoulderBottom, left, shoulderTop))
        best = min(best, squaredDistanceToSegment(x, y, left, shoulderTop, centerX, top))
        return best
    }
}

/**
 * Proportions of the hex keycap, as fractions of one placed key's touch cell.
 *
 * Named and gathered here so the cap can be reshaped in one place, and so the tests can assert
 * the shape instead of restating the arithmetic.
 */
object HexKeyMetrics {

    /** Cap width as a fraction of the cell width; 1.0 spans the cell exactly. */
    const val WIDTH_FRACTION = 1.0f

    /**
     * The regular hexagon's cap height as a fraction of the row pitch (a cell's height) — 4/3.
     *
     * 4/3 is the honeycomb's own proportion for rows staggered by half a key width: at that height
     * a cap's point reaches the shoulder line of the caps above, so the caps of the two rows meet
     * edge to edge and no surface between them is left over. That overhang into the row above and
     * below is what the interlock is made of, so shrinking this to 1.0 makes the caps neighbours
     * rather than a honeycomb.
     *
     * A pitch is always three quarters of a cap ([HexBoard.PITCH_CAP_FRACTION]), so this fraction
     * is the same in every orientation. What an orientation changes is the cell's own aspect — how
     * tall the pitch is against the cell's width — and the ratio it is sized from, below.
     */
    const val HEIGHT_FRACTION = 4f / 3f

    /**
     * Portrait's hexagon ratio — cap height as a fraction of the CELL WIDTH — 1.5, measured against
     * the cell's width rather than its pitch because width is the axis portrait cannot move.
     *
     * Which axis is spent decides which ratio an orientation gets. Portrait is width-bound: QWERTY
     * needs ten columns, so on a 1080 px screen the cell width is fixed at 108 px and can never
     * grow — the height budget is the only free axis. The regular hexagon (ratio 1.1547) spends
     * none of it, giving 108 x 124.7 caps and a 405 px block, 17% of a 2400 px screen; 1.5 spends
     * it tall, giving 108 x 162 caps and a 526.5 px block, 22%. Landscape is the opposite — the
     * display height cap spends the height — so it keeps the regular ratio (2/sqrt(3) = 1.1547) and
     * buys width with the side bands instead. The letter grid is identical either way; only the
     * cell's aspect moves. Read the two fractions as one relation: hexagon ratio =
     * [HEIGHT_FRACTION] x pitch/cellWidth.
     */
    const val PORTRAIT_HEIGHT_FRACTION = 1.5f

    /**
     * The straight left/right edge, as a fraction of the cap's height.
     *
     * One half is the regular hexagon: the straight edge covers the middle half of the cap's
     * height and each point a quarter — the proportion at which the caps above and below a row
     * meet along a shared line rather than a shared point.
     */
    const val SHOULDER_HEIGHT_FRACTION = 0.5f

    /**
     * How far outside a cap a touch may still resolve, as a fraction of one key's width.
     *
     * Mirrors the cell hit test's bound: half a key reaches the deliberate half-unit row indent
     * while still refusing a finger dragged clear of the surface.
     */
    const val MAX_FALLBACK_KEY_FRACTION = 0.5f
}

/** Squared distance from (x, y) to the segment (ax, ay)-(bx, by). */
private fun squaredDistanceToSegment(
    x: Float,
    y: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float {
    val vx = bx - ax
    val vy = by - ay
    val lengthSquared = vx * vx + vy * vy
    val along = if (lengthSquared <= 0f) 0f else ((x - ax) * vx + (y - ay) * vy) / lengthSquared
    val clamped = along.coerceIn(0f, 1f)
    val dx = x - (ax + vx * clamped)
    val dy = y - (ay + vy * clamped)
    return dx * dx + dy * dy
}
