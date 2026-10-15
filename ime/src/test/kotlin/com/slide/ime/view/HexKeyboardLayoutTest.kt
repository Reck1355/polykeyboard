package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.Layouts
import com.slide.core.theme.HexPalette
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approved hex board, and the honeycomb invariant it has to keep.
 *
 * The invariant is the one that was got wrong once: in a pointy-top honeycomb every row must sit
 * exactly half a key from the row above it, and the first attempt aligned the a-row and the z-row
 * on top of each other so two rows stacked instead of interlocking. So the stagger is asserted
 * here as a number — the step between each adjacent pair of rows, measured off the placed keys —
 * rather than trusted to the eye, because the picture that would have caught it is the picture
 * that lied about it.
 *
 * Numbers are chosen so the arithmetic is checkable by hand: a 1080 px phone is ten 108 px cells,
 * so half a key is 54 px.
 */
class HexKeyboardLayoutTest {

    private val boardWidth = 1080f
    private val cellWidth = HexBoard.cellWidth(boardWidth)

    /** The board as the view places it in a 1080 px wide frame tall enough to hold it. */
    private fun placement(
        viewportWidth: Float = boardWidth,
        availableHeight: Float = HexBoard.boardHeight(boardWidth),
    ): HexBoard.HexPlacement = HexBoard.place(Layouts.HexQwertyEn, viewportWidth, availableHeight)

    private fun placed(): List<PlacedKey> = placement().keys

    private fun rowKeys(keys: List<PlacedKey>, row: Int): List<PlacedKey> =
        keys.filter { it.row == row }.sortedBy { it.left }

    private fun placed(keys: List<PlacedKey>, row: Int, label: String): PlacedKey =
        keys.first { it.row == row && it.key.label == label }

    private fun capOf(keys: List<PlacedKey>, row: Int, label: String): HexShape =
        KeyGeometry.hexShapeFor(placed(keys, row, label))

    /** Compares two lists of cell measurements with the tolerance float arithmetic needs. */
    private fun assertCells(expected: List<Float>, actual: List<Float>, message: String = "cells") {
        assertEquals("$message: count", expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) ->
            assertEquals(message, want, got, 0.0001f)
        }
    }

    private fun assertCorners(expected: HexVertex, actual: HexVertex, message: String) {
        assertEquals(message, expected.x, actual.x, 0.001f)
        assertEquals(message, expected.y, actual.y, 0.001f)
    }

    @Test
    fun `the board is four rows of ten, nine, nine, seven keys`() {
        val keys = placed()
        assertEquals(4, keys.maxOf { it.row } + 1)
        assertEquals(listOf(10, 9, 9, 7), (0..3).map { rowKeys(keys, it).size })
    }

    @Test
    fun `every row starts half a key from the row above it`() {
        val keys = placed()

        val offsets = HexBoard.rowOffsetsInCells(keys, cellWidth)
        assertEquals(4, offsets.size)
        assertEquals(0f, offsets[0], 0.0001f)
        assertEquals(0.5f, offsets[1], 0.0001f)
        assertEquals(0f, offsets[2], 0.0001f)
        assertEquals(0.5f, offsets[3], 0.0001f)

        // The honeycomb itself: every adjacent pair steps by exactly half a cell, and none of them
        // steps by zero — the failure was the z-row landing on the a-row.
        val steps = HexBoard.adjacentStaggerStepsInCells(keys, cellWidth)
        assertCells(listOf(0.5f, 0.5f, 0.5f), steps, "adjacent stagger steps")
        steps.forEach { assertEquals(0.5f, it, 0.0001f) }

        // Half a key at 108 px cells, which is the 54 px the board was approved on.
        assertEquals(108f, cellWidth, 0.0001f)
        assertEquals(54f, HexBoard.HONEYCOMB_STEP_IN_CELLS * cellWidth, 0.0001f)
    }

    @Test
    fun `staggered neighbours meet along their shared edges - the caps interlock`() {
        val keys = placed()
        val q = capOf(keys, row = 0, label = "q")
        val w = capOf(keys, row = 0, label = "w")
        val a = capOf(keys, row = 1, label = "a")

        // a's upper-left point is q's lower-right corner, and its upper-right is w's lower-left —
        // the line the two rows share, which a row aligned on the grid could not produce.
        assertCorners(q.vertices()[2], a.vertices()[0], "q lower-right meets a upper-left")
        assertCorners(q.vertices()[3], a.vertices()[5], "q's point meets a's shoulder")
        assertCorners(w.vertices()[3], a.vertices()[1], "w's point meets a's shoulder")
        assertCorners(w.vertices()[4], a.vertices()[0], "w lower-left meets a upper-right")
    }

    @Test
    fun `a touch in the notch between two keys resolves to the point above it`() {
        val keys = placed()
        val a = placed(keys, 1, "a")
        val w = placed(keys, 0, "w")

        // Halfway between a and s, just inside the second row: the point of w hangs there.
        val notchX = (placed(keys, 1, "s").centerX + a.centerX) / 2f
        val notchY = a.top + cellWidth * 0.05f
        assertSame(w, KeyGeometry.hitTestHex(keys, notchX, notchY))
        // The old cell grid answers that same touch with a row-1 key, which is the change.
        assertEquals(1, KeyGeometry.hitTest(keys, notchX, notchY)?.row)
    }

    @Test
    fun `the bottom row is globe, 123, comma, space, period, emoji, enter`() {
        val bottom = rowKeys(placed(), 3)

        assertEquals(
            listOf(
                KeyType.LANGUAGE,
                KeyType.SYMBOLS,
                KeyType.CHARACTER,
                KeyType.SPACE,
                KeyType.CHARACTER,
                KeyType.EMOJI,
                KeyType.ENTER,
            ),
            bottom.map { it.key.type },
        )
        assertEquals(",", bottom[2].key.label)
        assertEquals(".", bottom[4].key.label)
        // One cell margins: the row is inset half a key at each end, like the a-row above it.
        assertEquals(0.5f * cellWidth, bottom.first().left, 0.01f)
        assertEquals(0.5f * cellWidth, boardWidth - bottom.last().right, 0.01f)
    }

    @Test
    fun `the space bar is one key over three cells`() {
        val keys = placed()
        val bottom = rowKeys(keys, 3)
        val space = bottom.first { it.key.type == KeyType.SPACE }

        assertEquals(3f, space.key.widthWeight, 0.0001f)
        assertEquals(3f * cellWidth, space.width, 0.01f)
        // One key, not three: there is a single cell spanning the bar, so there is nothing between
        // its cells for a seam to be drawn on.
        assertEquals(1, bottom.count { it.key.type == KeyType.SPACE })
    }

    @Test
    fun `a thumb anywhere on the space bar types a space`() {
        val keys = placed()
        val space = placed(keys, 3, " ")
        val cell = space.width / 3f

        // Each of the three cells the bar spans, and its centre, all resolve to it.
        for (slot in 0 until 3) {
            val cellCentreX = space.left + (slot + 0.5f) * cell
            assertSame(space, KeyGeometry.hitTestHex(keys, cellCentreX, space.centerY))
        }
        assertSame(space, KeyGeometry.hitTestHex(keys, space.centerX, space.centerY))
        // Including the very top and bottom of the middle cell.
        assertSame(space, KeyGeometry.hitTestHex(keys, space.centerX, space.top + 1f))
        assertSame(space, KeyGeometry.hitTestHex(keys, space.centerX, space.bottom - 1f))
    }

    @Test
    fun `the cap keeps its regular proportion and the block is three and three quarter cells tall`() {
        val cellHeight = HexBoard.cellHeight(cellWidth)
        val capHeight = HexBoard.capHeight(cellHeight)

        // "Regular pointy-top hexagon" is exactly cap height = cap width * 2/sqrt(3).
        assertEquals(cellWidth * 2f / sqrt(3f), capHeight, 0.01f)
        assertEquals(4f / 3f, capHeight / cellHeight, 0.0001f)
        // Four rows at their own pitch, and the block the caps actually draw: three pitches plus
        // one cap. That is the approved "3.75 cells", whose exact value is 3.7528 because one
        // pitch is sqrt(3)/2 of a cell — the same relation that makes the cap regular.
        assertEquals(4f * cellHeight, HexBoard.contentHeight(boardWidth), 0.01f)
        assertEquals(3f * cellHeight + capHeight, HexBoard.boardHeight(boardWidth), 0.01f)
        assertEquals(3.75f * cellWidth, HexBoard.boardHeight(boardWidth), 0.5f)
    }

    @Test
    fun `a frame shorter than the board scales the whole board instead of flattening the hexagon`() {
        val natural = HexBoard.boardHeight(boardWidth)

        val whole = placement(availableHeight = natural)
        assertEquals(1f, whole.scale, 0.0001f)
        assertEquals(cellWidth, whole.cellWidth, 0.01f)
        assertTrue(whole.spansViewport)

        val cramped = placement(availableHeight = natural * 0.8f)
        assertEquals(0.8f, cramped.scale, 0.0001f)
        assertEquals(cellWidth * 0.8f, cramped.cellWidth, 0.01f)
        // The block now fits the frame it was given, exactly: the height is what binds.
        assertEquals(natural * 0.8f, cramped.boardHeight, 0.01f)
        assertTrue(HexBoard.heightBinds(boardWidth, natural * 0.8f))
        // Both axes take the same factor, so the cap stays regular at any scale — the hexagon's
        // proportion is never the thing that gives.
        val scaledCap = HexBoard.capHeight(HexBoard.cellHeight(cramped.cellWidth))
        assertEquals(cramped.cellWidth * 2f / sqrt(3f), scaledCap, 0.01f)
        assertEquals(scale(cramped.cellWidth), scale(cellWidth), 0.0001f)
    }

    @Test
    fun `portrait and landscape are the same board, sized to fit and centred`() {
        // Portrait, 1080 px wide: the frame holds the width-derived cell, so ten columns span the
        // width and there is no side margin.
        val portrait = placement(viewportWidth = 1080f, availableHeight = HexBoard.boardHeight(1080f))
        // Landscape, 2400 x 1080, with the frame the IME gets there — a 594 px block, 55% of the
        // height. Nothing here is hard-coded: 158 px cells are what the available height yields.
        val landscape = placement(viewportWidth = 2400f, availableHeight = 594f)

        assertEquals(108f, portrait.cellWidth, 0.01f)
        assertEquals(240f, HexBoard.cellWidth(2400f), 0.01f)
        assertEquals(158f, landscape.cellWidth, 1f)
        assertEquals(594f, landscape.boardHeight, 2f)
        assertEquals(0.55f, landscape.boardHeight / 1080f, 0.01f)

        val orientations = listOf(
            "portrait" to (portrait to (1080f to HexBoard.boardHeight(1080f))),
            "landscape" to (landscape to (2400f to 594f)),
        )
        for ((name, pair) in orientations) {
            val (placed, viewport) = pair
            val (viewportWidth, availableHeight) = viewport
            assertFitsCentredAndInside(name, placed, viewportWidth, availableHeight)
        }

        // The same single board in both: the stagger, the row shape and the hexagon ratio do not
        // change with the orientation — only the cell size and the margins do.
        val portraitOffsets = HexBoard.rowOffsetsInCells(portrait.keys, portrait.cellWidth)
        val landscapeOffsets = HexBoard.rowOffsetsInCells(landscape.keys, landscape.cellWidth)
        assertEquals(portraitOffsets.size, landscapeOffsets.size)
        portraitOffsets.zip(landscapeOffsets).forEach { (above, below) ->
            assertEquals("same row offsets in both orientations", above, below, 0.0001f)
        }
        for (name in listOf("portrait", "landscape")) {
            val placed = if (name == "portrait") portrait else landscape
            assertCells(
                listOf(0.5f, 0.5f, 0.5f),
                HexBoard.adjacentStaggerStepsInCells(placed.keys, placed.cellWidth),
                "$name: adjacent stagger steps",
            )
            assertEquals(listOf(10, 9, 9, 7), (0..3).map { row -> placed.keys.count { it.row == row } })
        }
        assertEquals(scale(portrait.cellWidth), scale(landscape.cellWidth), 0.0001f)
    }

    /** The orientation's four promises, as one check so neither orientation gets a weaker test. */
    private fun assertFitsCentredAndInside(
        name: String,
        placement: HexBoard.HexPlacement,
        viewportWidth: Float,
        availableHeight: Float,
    ) {
        // 1. The derived cell makes the block fit the available height — and fill it exactly when
        //    the height is what binds, which is the landscape case.
        assertTrue("$name: block fits", placement.boardHeight <= availableHeight + 0.01f)
        if (HexBoard.heightBinds(viewportWidth, availableHeight)) {
            assertEquals("$name: block fills the frame", availableHeight, placement.boardHeight, 0.01f)
        }

        // 2. The hexagon ratio is identical in both orientations: cap height = cap width x 1.1547.
        val capHeight = HexBoard.capHeight(HexBoard.cellHeight(placement.cellWidth))
        assertEquals("$name: cap ratio", 1.1547f, capHeight / placement.cellWidth, 0.0001f)
        assertEquals("$name: cap ratio exact", placement.cellWidth * 2f / sqrt(3f), capHeight, 0.01f)

        // 3. The board is centred, and its side margins are equal — the leftover width is empty.
        assertEquals("$name: board is ten cells wide", placement.boardWidth, placement.cellWidth * 10f, 0.01f)
        assertTrue("$name: board no wider than viewport", placement.boardWidth <= viewportWidth + 0.01f)
        assertEquals(
            "$name: equal side margins",
            placement.leftInset,
            viewportWidth - placement.boardWidth - placement.leftInset,
            0.01f,
        )

        // 4. No key's geometry leaves the viewport, caps included — a cap is taller than its cell,
        //    so the overhang is where a board would be clipped.
        placement.keys.forEach { key ->
            val label = key.key.label.ifEmpty { key.key.type.name }
            assertTrue("$name: $label cell within width", key.left >= -0.01f && key.right <= viewportWidth + 0.01f)
            val cap = KeyGeometry.hexShapeFor(key)
            assertTrue("$name: $label cap within width", cap.left >= -0.01f && cap.right <= viewportWidth + 0.01f)
            assertTrue("$name: $label cap below the top edge", cap.top >= -0.1f)
            assertTrue("$name: $label cap above the bottom edge", cap.bottom <= availableHeight + 0.1f)
        }
    }

    /** Cap height over cap width, which is the one number that says "regular hexagon". */
    private fun scale(cellWidth: Float): Float =
        HexBoard.capHeight(HexBoard.cellHeight(cellWidth)) / cellWidth

    @Test
    fun `only the hex layout is drawn as the honeycomb, and the number row stays off`() {
        assertTrue(Layouts.HexQwertyEn.hexKeys)
        assertFalse(Layouts.QwertyEn.hexKeys)
        assertFalse(Layouts.SymbolsEn.hexKeys)
        assertFalse(Layouts.EmailEn.hexKeys)

        // The hex board does not take the digit row, and QWERTY's own geometry does not move.
        assertSame(Layouts.HexQwertyEn, Layouts.withNumberRow(Layouts.HexQwertyEn, true))
        assertEquals(10, Layouts.QwertyEn.rows[0].keys.size)
        assertEquals(6, Layouts.QwertyEn.rows[3].keys.size)
    }

    @Test
    fun `the palette is exactly the six slots the renderer reads, at the approved jade values`() {
        // The slots are the class's only int-valued state, so a seventh colour added later shows
        // up here rather than slipping in beside the six the renderer is written against. (Filtered
        // by type, not by name: the companion's singleton is a HexPalette and its defaults are not
        // ints, and neither should be mistaken for a slot.)
        val slots = HexPalette::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .filter { it.type == Int::class.javaPrimitiveType }
            .map { it.name }
            .toSet()
        assertEquals(6, slots.size)
        assertEquals(
            setOf("background", "key", "specialKey", "enterKey", "label", "pressed"),
            slots,
        )

        assertEquals(0xFF0E1417.toInt(), HexPalette.Jade.background)
        assertEquals(0xFF1F6F5C.toInt(), HexPalette.Jade.key)
        assertEquals(0xFF17564A.toInt(), HexPalette.Jade.specialKey)
        assertEquals(0xFFA8452B.toInt(), HexPalette.Jade.enterKey)
        assertEquals(0xFFF5F7F6.toInt(), HexPalette.Jade.label)
        assertEquals(0x40FFFFFF, HexPalette.Jade.pressed)
    }
}
