package com.slide.ime.view

import com.slide.core.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hex keycap geometry: the drawn cap and the touch target are one shape, anchored on the key
 * positions the layout already produced.
 *
 * The numbers are chosen so the arithmetic is checkable by eye: a 1100 px wide QWERTY layer is
 * 10 units, so a unit is 110 px, and 400 px of content over four rows is a 100 px row.
 */
class HexKeyGeometryTest {

    private fun place(
        width: Float = 1100f,
        contentHeight: Float = 400f,
        topOffset: Float = 0f,
    ): List<PlacedKey> = KeyGeometry.place(Layouts.QwertyEn, width, contentHeight, topOffset)

    private fun placed(keys: List<PlacedKey>, row: Int, label: String): PlacedKey =
        keys.first { it.row == row && it.key.label == label }

    private fun capOf(keys: List<PlacedKey>, row: Int, label: String): HexShape =
        KeyGeometry.hexShapeFor(placed(keys, row, label))

    private fun capsContaining(keys: List<PlacedKey>, x: Float, y: Float): Int =
        keys.count { KeyGeometry.hexShapeFor(it).contains(x, y) }

    private fun assertVertexEquals(expected: HexVertex, actual: HexVertex, message: String) {
        assertEquals(message, expected.x, actual.x, 0.001f)
        assertEquals(message, expected.y, actual.y, 0.001f)
    }

    @Test
    fun `every cap is centred on its key and spans its width`() {
        place().forEach { key ->
            val cap = KeyGeometry.hexShapeFor(key)
            assertEquals(key.centerX, cap.centerX, 0.0001f)
            assertEquals(key.centerY, cap.centerY, 0.0001f)
            assertEquals(key.left, cap.left, 0.0001f)
            assertEquals(key.right, cap.right, 0.0001f)
            assertEquals(key.height * 2f / 3f, cap.halfHeight, 0.0001f)
        }
    }

    @Test
    fun `cap reaches a sixth of a row past its cell above and below`() {
        val keys = place()
        val q = placed(keys, row = 0, label = "q")
        val cap = KeyGeometry.hexShapeFor(q)

        assertEquals(q.height * 2f / 3f, cap.halfHeight, 0.0001f)
        assertEquals(q.height / 3f, cap.shoulderHalfHeight, 0.0001f)
        assertEquals(q.height / 6f, q.top - cap.top, 0.0001f)
        assertEquals(q.height / 6f, cap.bottom - q.bottom, 0.0001f)
    }

    @Test
    fun `letter caps keep the layout's letter positions`() {
        val keys = place()

        val q = capOf(keys, row = 0, label = "q")
        assertEquals(55f, q.centerX, 0.0001f)
        assertEquals(50f, q.centerY, 0.0001f)
        val a = capOf(keys, row = 1, label = "a")
        assertEquals(110f, a.centerX, 0.0001f)
        assertEquals(150f, a.centerY, 0.0001f)
        val z = capOf(keys, row = 2, label = "z")
        assertEquals(220f, z.centerX, 0.0001f)
        assertEquals(250f, z.centerY, 0.0001f)

        // Every key owns its own centre, so the cap can only have moved if its cell moved.
        keys.forEach { key ->
            assertSame(key, KeyGeometry.hitTestHex(keys, key.centerX, key.centerY))
        }
    }

    @Test
    fun `caps within a row meet along their shared edge and never overlap`() {
        place().groupBy { it.row }.forEach { (row, rowKeys) ->
            val ordered = rowKeys.sortedBy { it.left }
            ordered.zipWithNext().forEach { (left, right) ->
                val leftCap = KeyGeometry.hexShapeFor(left)
                val rightCap = KeyGeometry.hexShapeFor(right)
                val edge = "row $row: ${left.key.label} -> ${right.key.label}"
                assertEquals(edge, leftCap.right, rightCap.left, 0.0001f)
                assertEquals(edge, leftCap.shoulderTop, rightCap.shoulderTop, 0.0001f)
                assertEquals(edge, leftCap.shoulderBottom, rightCap.shoulderBottom, 0.0001f)
            }
        }
    }

    @Test
    fun `caps of rows staggered by half a key share their edges`() {
        val keys = place()
        val q = capOf(keys, row = 0, label = "q")
        val w = capOf(keys, row = 0, label = "w")
        val a = capOf(keys, row = 1, label = "a")

        // a's upper-left edge is q's lower-right edge, and a's upper-right is w's lower-left.
        assertVertexEquals(q.vertices()[2], a.vertices()[0], "q lower-right meets a upper-left")
        assertVertexEquals(q.vertices()[3], a.vertices()[5], "q lower-right point meets a shoulder")
        assertVertexEquals(w.vertices()[3], a.vertices()[1], "w lower-left point meets a shoulder")
        assertVertexEquals(w.vertices()[4], a.vertices()[0], "w lower-left meets a upper-right")
    }

    @Test
    fun `shared edge between staggered caps separates them`() {
        val keys = place()

        // The edge shared by q (row 0) and a (row 1) runs through its midpoint: both caps hold it.
        assertEquals(2, capsContaining(keys, 82.5f, 100f))
        // A pixel either side of it belongs to exactly one cap each.
        assertEquals(1, capsContaining(keys, 82.5f, 99f))
        assertEquals(1, capsContaining(keys, 82.5f, 101f))
        assertSame(placed(keys, 0, "q"), KeyGeometry.hitTestHex(keys, 82.5f, 99f))
        assertSame(placed(keys, 1, "a"), KeyGeometry.hitTestHex(keys, 82.5f, 101f))
    }

    @Test
    fun `notch between two caps of a row belongs to the cap whose point reaches into it`() {
        val keys = place()

        // Just below the top of row 1, in the notch between a and s, sits the point of w's cap.
        assertSame(placed(keys, 0, "w"), KeyGeometry.hitTestHex(keys, 165f, 110f))
        // The cell grid answers that same touch with a row-1 key, which is the change of target.
        assertEquals(1, KeyGeometry.hitTest(keys, 165f, 110f)?.row)
    }

    @Test
    fun `overlapping caps resolve to the nearest centre and a tie keeps the earlier key`() {
        val keys = place()

        // Rows 1 and 2 are aligned instead of staggered, so s's point and z's point overlap.
        assertSame(placed(keys, 1, "s"), KeyGeometry.hitTestHex(keys, 220f, 200f))
        assertSame(placed(keys, 2, "z"), KeyGeometry.hitTestHex(keys, 220f, 210f))
    }

    @Test
    fun `blank space above and below the key surface does not select a key`() {
        val keys = place(width = 1000f, contentHeight = 400f, topOffset = 20f)
        val bottom = keys.maxOf { it.bottom }

        // The caps overhang the surface by a sixth of a row; the padding they reach into stays inert.
        assertNull(KeyGeometry.hitTestHex(keys, 500f, 19.999f))
        assertNull(KeyGeometry.hitTestHex(keys, 500f, bottom + 0.001f))
    }

    @Test
    fun `far horizontal touch does not snap to an edge key`() {
        val keys = place(width = 1000f, contentHeight = 400f)
        val last = keys.filter { it.row == 1 }.maxByOrNull { it.left }!!

        assertNull(KeyGeometry.hitTestHex(keys, last.right + last.width, last.centerY))
    }

    @Test
    fun `row indent stays a forgiving touch target`() {
        val keys = place(width = 1000f, contentHeight = 400f)
        val firstKey = keys.filter { it.row == 1 }.minByOrNull { it.left }!!

        assertTrue(firstKey.left > 0f)
        assertSame(firstKey, KeyGeometry.hitTestHex(keys, firstKey.left / 2f, firstKey.centerY))
    }

    @Test
    fun `cap outlines its six corners clockwise from the top point`() {
        val cap = capOf(place(), row = 1, label = "a")

        val expected = listOf(
            HexVertex(110f, 83.333f),
            HexVertex(165f, 116.667f),
            HexVertex(165f, 183.333f),
            HexVertex(110f, 216.667f),
            HexVertex(55f, 183.333f),
            HexVertex(55f, 116.667f),
        )
        assertEquals(expected.size, cap.vertices().size)
        expected.forEachIndexed { index, vertex ->
            assertVertexEquals(vertex, cap.vertices()[index], "vertex $index")
        }
    }

    @Test
    fun `cap holds its inside and its outline, and nothing else`() {
        val cap = capOf(place(), row = 1, label = "a")

        assertTrue(cap.contains(110f, 150f))
        assertTrue(cap.contains(165f, 150f))
        // The outline belongs to the cap: a touch exactly on it is still that key's.
        assertTrue(cap.contains(cap.centerX, cap.bottom))
        assertTrue(cap.contains(cap.right, cap.centerY))
        assertTrue(cap.contains(cap.left, cap.centerY))
        assertFalse(cap.contains(cap.right + 0.5f, cap.centerY))
        assertFalse(cap.contains(cap.centerX, cap.bottom + 0.5f))
        // A corner of the cell is outside the cap, which is the whole point of the shape.
        assertFalse(cap.contains(55f, 200f))
    }

    @Test
    fun `cap distance is measured to its outline`() {
        val cap = capOf(place(), row = 1, label = "a")

        assertEquals(0f, cap.squaredDistanceTo(110f, 150f), 0.0001f)
        assertEquals(0f, cap.squaredDistanceTo(165f, 150f), 0.0001f)
        assertEquals(9f, cap.squaredDistanceTo(52f, 150f), 0.01f)
        assertEquals(9f, cap.squaredDistanceTo(cap.centerX, cap.bottom + 3f), 0.01f)
        assertEquals(2500f, cap.squaredDistanceTo(215f, 150f), 0.01f)
    }

    @Test
    fun `wide keys keep the cap's proportions`() {
        val keys = place()
        val space = placed(keys, row = 3, label = " ")

        val cap = KeyGeometry.hexShapeFor(space)
        assertEquals(space.width, cap.halfWidth * 2f, 0.0001f)
        assertEquals(space.height * 2f / 3f, cap.halfHeight, 0.0001f)
        assertEquals(space.height / 3f, cap.shoulderHalfHeight, 0.0001f)
        assertSame(space, KeyGeometry.hitTestHex(keys, space.centerX, space.centerY))
    }

    @Test
    fun `an empty layer resolves nothing`() {
        assertNull(KeyGeometry.hitTestHex(emptyList(), 100f, 100f))
    }
}
