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
 * Two ratios, one board. The hexagon's proportion is per orientation — portrait is width-bound and
 * spends its free height on a tall hexagon, landscape is height-bound and keeps the regular one —
 * so the ratio, the pitch and the block are asserted at *both* ratios, and the letter grid is
 * asserted to be the same in both. That separation is what stops a ratio change leaking into letter
 * placement.
 *
 * Numbers are chosen so the arithmetic is checkable by hand: a 1080 px phone is ten 108 px cells,
 * so half a key is 54 px.
 */
class HexKeyboardLayoutTest {

    private val boardWidth = 1080f
    private val cellWidth = HexBoard.cellWidth(boardWidth)

    /** The two sizings the board is drawn at, read from [HexBoard.sizing] rather than restated. */
    private val portrait = HexBoard.sizing(HexBoard.Orientation.PORTRAIT)
    private val landscape = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE)
    private val portraitRatio = portrait.heightRatio
    private val landscapeRatio = landscape.heightRatio

    /**
     * The board as the view places it. Portrait by default: [boardWidth] is a portrait frame and
     * portrait is what the width-bound path produces.
     */
    private fun placement(
        sizing: HexBoard.Sizing = portrait,
        viewportWidth: Float = boardWidth,
        availableHeight: Float = HexBoard.boardHeight(viewportWidth, sizing),
    ): HexBoard.HexPlacement =
        HexBoard.place(Layouts.HexQwertyEn, viewportWidth, availableHeight, sizing)

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

    /** Cap height over cap width — the one number that says which hexagon the board is drawn at. */
    private fun scale(cellWidth: Float, sizing: HexBoard.Sizing): Float =
        HexBoard.capHeight(HexBoard.cellHeight(cellWidth, sizing)) / cellWidth

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
    fun `the stagger holds at both hexagon ratios`() {
        for ((name, sizing) in listOf("portrait" to portrait, "landscape" to landscape)) {
            val board = placement(sizing = sizing)
            val offsets = HexBoard.rowOffsetsInCells(board.keys, board.cellWidth)
            assertCells(listOf(0f, 0.5f, 0f, 0.5f), offsets, "$name: row offsets")
            assertCells(
                listOf(0.5f, 0.5f, 0.5f),
                HexBoard.adjacentStaggerStepsInCells(board.keys, board.cellWidth),
                "$name: adjacent stagger steps",
            )
        }
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
    fun `the landscape hexagon is regular and its block is three and three quarter cells tall`() {
        val cell = HexBoard.cellWidth(boardWidth)
        val cellHeight = HexBoard.cellHeight(cell, landscape)
        val capHeight = HexBoard.capHeight(cellHeight)

        // "Regular pointy-top hexagon" is exactly cap height = cap width * 2/sqrt(3). This is the
        // landscape hexagon: it used to be the *only* ratio, so this assertion used to run against
        // the board's default. It now names landscape, because portrait is deliberately taller.
        assertEquals(cell * 2f / sqrt(3f), capHeight, 0.01f)
        assertEquals(4f / 3f, capHeight / cellHeight, 0.0001f)
        assertEquals(1.1547f, landscapeRatio, 0.0001f)
        // Four rows at their own pitch, and the block the caps actually draw: three pitches plus
        // one cap. That is the approved "3.75 cells", whose exact value is 3.7528 because one
        // pitch is sqrt(3)/2 of a cell — the same relation that makes the cap regular.
        assertEquals(4f * cellHeight, HexBoard.contentHeight(boardWidth, landscape), 0.01f)
        assertEquals(3.25f * landscapeRatio * cell, HexBoard.boardHeight(boardWidth, landscape), 0.01f)
        assertEquals(3.75f * cell, HexBoard.boardHeight(boardWidth, landscape), 0.5f)
    }

    @Test
    fun `the block is three and a quarter caps - 3_25 x ratio x cell - at both ratios`() {
        for ((name, sizing) in listOf("portrait" to portrait, "landscape" to landscape)) {
            val ratio = sizing.heightRatio
            for (cell in listOf(HexBoard.cellWidth(1080f), 137.5f, 240f)) {
                assertEquals(
                    "$name: block = 3.25 x ratio x cell at $cell px",
                    HexBoard.BLOCK_CAP_FACTOR * ratio * cell,
                    HexBoard.blockHeight(cell, sizing),
                    0.0001f,
                )
            }
            assertEquals("$name: block in cells", 3.25f * ratio, HexBoard.blockHeightInCells(sizing), 0.0001f)
            assertEquals("$name: factor", 3.25f, HexBoard.BLOCK_CAP_FACTOR, 0.0001f)
        }
        // The invariant's whole point: the regular ratio is the one the removed 3.7528 belonged to,
        // and it falls out of the derivation instead of being written down.
        assertEquals(3.7528f, HexBoard.blockHeightInCells(landscape), 0.0001f)
        assertEquals(4.875f, HexBoard.blockHeightInCells(portrait), 0.0001f)
    }

    @Test
    fun `portrait spends the free height axis - the cell is fixed and the caps are tall`() {
        val board = placement(sizing = portrait, viewportWidth = 1080f, availableHeight = 2400f)
        val cell = board.cellWidth
        val pitch = HexBoard.cellHeight(cell, portrait)
        val cap = HexBoard.capHeight(pitch)

        assertEquals(1.5f, portraitRatio, 0.0001f)
        assertEquals(HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION, portraitRatio, 0f)
        // cell = width / 10, unchanged: width is the axis QWERTY's ten columns spend.
        assertEquals(108f, cell, 0.0001f)
        assertEquals(162f, cap, 0.0001f)
        assertEquals(121.5f, pitch, 0.0001f)
        assertEquals(526.5f, board.boardHeight, 0.0001f)
        // The ratio is exactly cap height / cell width.
        assertEquals(cell * portraitRatio, cap, 0.0001f)
        // 22% of a 2400 px screen, against the 17% the regular hexagon gave.
        assertEquals(0.22f, board.boardHeight / 2400f, 0.001f)
        assertTrue(board.spansViewport)
    }

    @Test
    fun `landscape is capped shorter - 45 percent of the height, so the app keeps the rest`() {
        val displayHeight = 1080f
        val available = HexBoard.cappedBoardHeight(2400f, displayHeight, landscape)

        // The share, straight from the constant. 0.45, not the 0.55 that swallowed the typing
        // screen: "because landscape is so big there also is no space on the screen to actually see
        // the words I'm typing".
        assertEquals(0.45f, HexBoard.LANDSCAPE_HEIGHT_FRACTION, 0f)
        assertEquals(486f, available, 0.01f)
        assertTrue(HexBoard.heightBinds(2400f, available, landscape))

        val board = placement(sizing = landscape, viewportWidth = 2400f, availableHeight = available)
        val cell = board.cellWidth

        assertEquals(1.1547f, landscapeRatio, 0.0001f)
        // 486 / 3.7527766 — the *derived* block-in-cells, not the 3.7528 it rounds to. (Dividing by
        // the rounded 3.7528 is what makes this 129.53; the exact value is 129.5041.)
        assertEquals(129.5041f, cell, 0.001f)
        assertEquals(486f, board.boardHeight, 0.01f)
        assertEquals(10f * cell, board.boardWidth, 0.01f)
        assertEquals(1295.04f, board.boardWidth, 0.05f)

        // The block is 45% of the screen and the app above keeps the other 55%.
        assertEquals(0.45f, board.boardHeight / displayHeight, 0.001f)
        assertTrue("the app keeps more than half the height", displayHeight - board.boardHeight > displayHeight * 0.5f)

        // Centred, with the leftover width split into two equal bands — 552.48 px each. This is the
        // centring assertion a left/right split board would deliberately break; it is pinned here so
        // that change cannot arrive silently.
        val band = (2400f - board.boardWidth) / 2f
        assertEquals(552.48f, band, 0.05f)
        assertEquals(band, board.leftInset, 0.01f)
        assertEquals(band, 2400f - board.boardWidth - board.leftInset, 0.01f)
    }

    @Test
    fun `a pitch is three quarters of a cap at both ratios`() {
        for ((name, sizing) in listOf("portrait" to portrait, "landscape" to landscape)) {
            val cell = 150f
            val pitch = HexBoard.cellHeight(cell, sizing)
            val cap = HexBoard.capHeight(pitch)
            assertEquals("$name: pitch = 0.75 cap", HexBoard.PITCH_CAP_FRACTION * cap, pitch, 0.0001f)
            assertEquals("$name: cap = ratio x cell", sizing.heightRatio * cell, cap, 0.0001f)
            assertEquals("$name: cap / pitch = 4/3", HexKeyMetrics.HEIGHT_FRACTION, cap / pitch, 0.0001f)
        }

        // And through a real placement, so the ratio cannot be right on paper and wrong on the board.
        for ((name, sizing) in listOf("portrait" to portrait, "landscape" to landscape)) {
            val board = placement(sizing = sizing)
            val pitch = HexBoard.cellHeight(board.cellWidth, sizing)
            board.keys.forEach { key ->
                assertEquals(
                    "$name: ${key.key.label.ifEmpty { key.key.type.name }} row pitch",
                    pitch,
                    key.height,
                    0.0001f,
                )
            }
            val cap = KeyGeometry.hexShapeFor(board.keys.first()).halfHeight * 2f
            assertEquals("$name: drawn cap = ratio x cell", sizing.heightRatio * board.cellWidth, cap, 0.0001f)
        }
    }

    @Test
    fun `the letter grid is identical in both orientations - only the cell's aspect moves`() {
        val portraitBoard = placement(sizing = portrait)
        val landscapeBoard = placement(
            sizing = landscape,
            viewportWidth = 2400f,
            availableHeight = HexBoard.cappedBoardHeight(2400f, 1080f, landscape),
        )

        // Same rows, same counts.
        val portraitCounts = (0..3).map { row -> portraitBoard.keys.count { it.row == row } }
        val landscapeCounts = (0..3).map { row -> landscapeBoard.keys.count { it.row == row } }
        assertEquals(listOf(10, 9, 9, 7), portraitCounts)
        assertEquals(portraitCounts, landscapeCounts)

        // Same stagger, measured in cells so the different cell size cannot matter.
        assertCells(
            listOf(0f, 0.5f, 0f, 0.5f),
            HexBoard.rowOffsetsInCells(portraitBoard.keys, portraitBoard.cellWidth),
            "portrait row offsets",
        )
        assertCells(
            HexBoard.rowOffsetsInCells(portraitBoard.keys, portraitBoard.cellWidth),
            HexBoard.rowOffsetsInCells(landscapeBoard.keys, landscapeBoard.cellWidth),
            "same row offsets in both orientations",
        )

        // Same letters, in the same row and the same column — in cells, not pixels, so the ratio
        // cannot leak into letter placement.
        fun letterCells(board: HexBoard.HexPlacement): List<Triple<String, Int, Float>> {
            val boardLeft = board.keys.minOf { it.left }
            return board.keys
                .filter { it.key.type == KeyType.CHARACTER && it.key.outputText.length == 1 }
                .sortedBy { it.key.outputText }
                .map { Triple(it.key.outputText, it.row, (it.centerX - boardLeft) / board.cellWidth) }
        }

        val portraitLetters = letterCells(portraitBoard)
        val landscapeLetters = letterCells(landscapeBoard)
        assertEquals("letter count", portraitLetters.size, landscapeLetters.size)
        portraitLetters.zip(landscapeLetters).forEach { (p, l) ->
            assertEquals("same letter", p.first, l.first)
            assertEquals("same row for ${p.first}", p.second, l.second)
            assertEquals("same column for ${p.first}", p.third, l.third, 0.0001f)
        }

        // The fused three-cell space, centred on the board, in both.
        for ((name, board) in listOf("portrait" to portraitBoard, "landscape" to landscapeBoard)) {
            val spaces = board.keys.filter { it.row == 3 && it.key.type == KeyType.SPACE }
            assertEquals("$name: one fused space", 1, spaces.size)
            val space = spaces.single()
            val boardLeft = board.keys.minOf { it.left }
            assertEquals("$name: space spans three cells", 3f * board.cellWidth, space.width, 0.01f)
            assertEquals(
                "$name: space centred on the board",
                board.boardWidth / 2f,
                space.centerX - boardLeft,
                0.01f,
            )
        }
    }

    @Test
    fun `a frame shorter than the board scales the whole board instead of stretching its hexagon`() {
        val natural = HexBoard.boardHeight(boardWidth, portrait)

        val whole = placement(sizing = portrait, availableHeight = natural)
        assertEquals(1f, whole.scale, 0.0001f)
        assertEquals(cellWidth, whole.cellWidth, 0.01f)
        assertTrue(whole.spansViewport)

        val cramped = placement(sizing = portrait, availableHeight = natural * 0.8f)
        assertEquals(0.8f, cramped.scale, 0.0001f)
        assertEquals(cellWidth * 0.8f, cramped.cellWidth, 0.01f)
        // The block now fits the frame it was given, exactly: the height is what binds.
        assertEquals(natural * 0.8f, cramped.boardHeight, 0.01f)
        assertTrue(HexBoard.heightBinds(boardWidth, natural * 0.8f, portrait))
        // Both axes take the same factor, so the hexagon's proportion is never the thing that
        // gives — it just gets smaller, at the ratio the orientation asked for.
        assertEquals(portraitRatio, scale(cramped.cellWidth, portrait), 0.0001f)
        assertEquals(scale(cramped.cellWidth, portrait), scale(cellWidth, portrait), 0.0001f)
    }

    @Test
    fun `portrait and landscape are the same board, sized to fit and centred`() {
        // Portrait, 1080 px wide: the frame holds the width-derived cell, so ten columns span the
        // width and there is no side margin.
        val portraitBoard = placement(
            sizing = portrait,
            viewportWidth = 1080f,
            availableHeight = HexBoard.boardHeight(1080f, portrait),
        )
        // Landscape, 2400 x 1080, with the frame the IME gets there — a 486 px block, 45% of the
        // height. Nothing here is hard-coded: the cells are what the available height yields.
        val landscapeAvailable = HexBoard.cappedBoardHeight(2400f, 1080f, landscape)
        val landscapeBoard = placement(
            sizing = landscape,
            viewportWidth = 2400f,
            availableHeight = landscapeAvailable,
        )

        assertEquals(108f, portraitBoard.cellWidth, 0.01f)
        assertEquals(240f, HexBoard.cellWidth(2400f), 0.01f)
        assertEquals(129.5041f, landscapeBoard.cellWidth, 0.001f)
        assertEquals(486f, landscapeBoard.boardHeight, 0.01f)
        assertEquals(0.45f, landscapeBoard.boardHeight / 1080f, 0.01f)

        val orientations = listOf(
            "portrait" to (portraitBoard to (1080f to HexBoard.boardHeight(1080f, portrait))),
            "landscape" to (landscapeBoard to (2400f to landscapeAvailable)),
        )
        for ((name, pair) in orientations) {
            val (board, viewport) = pair
            val (viewportWidth, availableHeight) = viewport
            assertFitsCentredAndInside(name, board, viewportWidth, availableHeight)
        }

        // The same single board in both: the stagger and the row shape do not change with the
        // orientation — only the cell size, the ratio and the margins do.
        val portraitOffsets = HexBoard.rowOffsetsInCells(portraitBoard.keys, portraitBoard.cellWidth)
        val landscapeOffsets = HexBoard.rowOffsetsInCells(landscapeBoard.keys, landscapeBoard.cellWidth)
        assertEquals(portraitOffsets.size, landscapeOffsets.size)
        portraitOffsets.zip(landscapeOffsets).forEach { (above, below) ->
            assertEquals("same row offsets in both orientations", above, below, 0.0001f)
        }
        for ((name, board) in listOf("portrait" to portraitBoard, "landscape" to landscapeBoard)) {
            assertCells(
                listOf(0.5f, 0.5f, 0.5f),
                HexBoard.adjacentStaggerStepsInCells(board.keys, board.cellWidth),
                "$name: adjacent stagger steps",
            )
            assertEquals(listOf(10, 9, 9, 7), (0..3).map { row -> board.keys.count { it.row == row } })
        }

        // The one thing the orientations deliberately do NOT share: the hexagon ratio. Portrait's
        // is the tall 1.5 that spends the free height; landscape's is the regular 1.1547. The old
        // test asserted these two were equal, which was true only while both were 1.1547.
        assertEquals(1.5f, scale(portraitBoard.cellWidth, portrait), 0.0001f)
        assertEquals(1.1547f, scale(landscapeBoard.cellWidth, landscape), 0.0001f)
        assertTrue(
            "portrait's hexagon is the taller one",
            scale(portraitBoard.cellWidth, portrait) > scale(landscapeBoard.cellWidth, landscape),
        )
    }

    /** The orientation's four promises, as one check so neither orientation gets a weaker test. */
    private fun assertFitsCentredAndInside(
        name: String,
        placement: HexBoard.HexPlacement,
        viewportWidth: Float,
        availableHeight: Float,
    ) {
        val sizing = placement.sizing

        // 1. The derived cell makes the block fit the available height — and fill it exactly when
        //    the height is what binds, which is the landscape case.
        assertTrue("$name: block fits", placement.boardHeight <= availableHeight + 0.01f)
        if (HexBoard.heightBinds(viewportWidth, availableHeight, sizing)) {
            assertEquals("$name: block fills the frame", availableHeight, placement.boardHeight, 0.01f)
        }

        // 2. The hexagon keeps its orientation's ratio: portrait 1.5, landscape 1.1547.
        val capHeight = HexBoard.capHeight(HexBoard.cellHeight(placement.cellWidth, sizing))
        assertEquals("$name: cap ratio", sizing.heightRatio, capHeight / placement.cellWidth, 0.0001f)
        assertEquals("$name: placement reports its ratio", sizing.heightRatio, placement.heightRatio, 0f)

        // 3. The board is centred, and its side margins are equal — the leftover width is empty.
        assertEquals("$name: board is ten cells wide", placement.boardWidth, placement.cellWidth * 10f, 0.01f)
        assertTrue("$name: board no wider than viewport", placement.boardWidth <= viewportWidth + 0.01f)
        // Centred, equal bands. Landscape is expected to change shape — the request is a left/right
        // split board, one half per thumb — and if it does, this is the assertion that will fail,
        // on purpose and with this message rather than as a mystery.
        assertEquals(
            "$name: centred, equal bands (a landscape split into two halves would move this)",
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
