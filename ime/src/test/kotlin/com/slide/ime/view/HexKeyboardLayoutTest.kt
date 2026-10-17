package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.Layouts
import com.slide.core.theme.HexPalette
import kotlin.math.sqrt
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
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
 * Two ratios, one letter set. The board's shape is per orientation — portrait is width-bound, single,
 * and spends its free height on a tall hexagon; landscape is height-bound, flat, and split into two
 * halves (see [HexSplitBoardTest]) — so the ratio, the pitch and the block are asserted at *both*
 * ratios, and the letter set is asserted to be the same in both. That separation is what stops a
 * ratio change leaking into letter placement.
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
    fun `the board is four rows, and every row spans the same width`() {
        val keys = placed()
        assertEquals(4, keys.maxOf { it.row } + 1)

        // The z-row gave up a key at each end when caps and backspace moved to the bottom row, and
        // the bottom row took both on. What must not move is the width: a row one cell narrow or
        // wide would scale the whole honeycomb to fit it, so the cells each row covers is the
        // property, not the number of keys in it.
        assertEquals(listOf(10, 9, 8, 5), (0..3).map { rowKeys(keys, it).size })

        // Both insets pinned to numbers, not inferred from what is left over: an assertion that
        // derives its expected value from the placed keys passes whatever the row does, which is
        // how the first version of this test survived the insets being deleted.
        val insets = listOf(0f, 0.5f, 1f, 1.5f)
        for (row in 0..3) {
            val inRow = rowKeys(keys, row)
            val cells = inRow.sumOf { it.key.widthWeight.toDouble() }.toFloat()
            assertEquals(
                "row $row leading inset",
                insets[row] * cellWidth,
                inRow.first().left,
                cellWidth * 0.01f,
            )
            assertEquals(
                "row $row trailing inset",
                (10f - insets[row] - cells) * cellWidth,
                boardWidth - inRow.last().right,
                cellWidth * 0.01f,
            )
        }
    }

    @Test
    fun `every row starts half a key from the row above it`() {
        val keys = placed()

        val offsets = HexBoard.rowOffsetsInCells(keys, cellWidth)
        assertEquals(4, offsets.size)
        assertEquals(0f, offsets[0], 0.0001f)
        assertEquals(0.5f, offsets[1], 0.0001f)
        // The z-row is inset a cell since backspace left it, and the bottom row a cell and a half
        // since it came in from both edges. Each steps half a cell from the row above, which is the
        // honeycomb, and the space bar still lands on the board's midline.
        assertEquals(1f, offsets[2], 0.0001f)
        assertEquals(1.5f, offsets[3], 0.0001f)

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
            assertCells(listOf(0f, 0.5f, 1f, 1.5f), offsets, "$name: row offsets")
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
    fun `the bottom row is comma, backspace, space, enter, period`() {
        val bottom = rowKeys(placed(), 3)

        assertEquals(
            listOf(
                KeyType.CHARACTER,
                KeyType.DELETE,
                KeyType.SPACE,
                KeyType.ENTER,
                KeyType.CHARACTER,
            ),
            bottom.map { it.key.type },
        )
        assertEquals(",", bottom[0].key.label)
        assertEquals(".", bottom[4].key.label)
        // The backspace sits at the space bar's left and the enter at its right, on his word: the
        // two sides were the other way round in the first pass and he corrected them.
        assertEquals(KeyType.DELETE, bottom[1].key.type)
        assertEquals(KeyType.ENTER, bottom[3].key.type)
        // A hex and a half at each end: seven cells centred on the ten-cell grid, so the board
        // narrows a cell a side all the way down and the row above is still half a cell out of step.
        assertEquals(1.5f * cellWidth, bottom.first().left, 0.01f)
        assertEquals(1.5f * cellWidth, boardWidth - bottom.last().right, 0.01f)
    }

    @Test
    fun `backspace is not beside a letter, and caps beside one is deliberate`() {
        val keys = placed()

        // Backspace sat at the end of the z-row, one slip from the M a thumb had just pressed. It
        // is not on a letter row now, and that rule is the one that holds: a destructive key at the
        // end of a letter row is a mis-tap that eats the letter just typed.
        //
        // The caps key has come up to that end instead, on Jacky's call: caps reaches past M, but a
        // mis-tap there shifts a letter and nothing else. The eighth cell is what keeps the row
        // centred on the ten-cell grid and exactly half a cell out of step with the row above.
        val zRow = rowKeys(keys, 2)
        assertEquals(
            listOf("z", "x", "c", "v", "b", "n", "m", "⇧"),
            zRow.map { it.key.label },
        )
        assertEquals(KeyType.SHIFT, zRow.last().key.type)
        assertEquals(
            "no destructive key sits at the end of a letter row",
            emptyList<KeyType>(),
            zRow.map { it.key.type }.filter { it == KeyType.DELETE || it == KeyType.ENTER },
        )

        // And on the bottom row backspace is at the space bar's left, out of every letter's reach.
        val bottom = rowKeys(keys, 3)
        assertEquals(",", bottom[0].key.label)
        assertEquals(KeyType.DELETE, bottom[1].key.type)
        assertEquals(KeyType.SPACE, bottom[2].key.type)
    }

    @Test
    fun `the number key has left the board for the strip, and caps has left for the z-row`() {
        val keys = placed()

        // He asked for the caps key beside the M and for the bottom row to come in a hex at each
        // end. Moving the number key to the strip is what paid for both: the row is five keys and
        // seven cells, and the ?123 page is one tap above the board.
        assertEquals(
            "the hex letters board carries no symbols key",
            emptyList<KeyType>(),
            rowKeys(keys, 3).map { it.key.type }.filter { it == KeyType.SYMBOLS },
        )
        assertEquals(KeyType.SHIFT, rowKeys(keys, 2).last().key.type)
    }

    @Test
    fun `no board carries the globe, the emoji or the symbols key - the strip does`() {
        val boards = listOf(
            Layouts.HexQwertyEn,
            Layouts.HexQwertyEs,
            Layouts.HexSymbolsEn,
            Layouts.HexSymbolsAltEn,
        )
        for (board in boards) {
            val types = board.rows.flatMap { row -> row.keys.map { it.type } }
            assertEquals(
                "board ${board.id} still carries a strip button",
                emptyList<KeyType>(),
                types.filter { it == KeyType.LANGUAGE || it == KeyType.EMOJI },
            )
        }
        // The symbols key is a mode, and it left the letters board with the other two. The symbol
        // pages keep theirs: it is how a page is reached from another page.
        for (board in listOf(Layouts.HexQwertyEn, Layouts.HexQwertyEs)) {
            val types = board.rows.flatMap { row -> row.keys.map { it.type } }
            assertEquals(
                "board ${board.id} still carries the symbols key",
                emptyList<KeyType>(),
                types.filter { it == KeyType.SYMBOLS },
            )
        }
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
    fun `the landscape hexagon is the flat one - 0_95 - and its block follows from it`() {
        val cell = HexBoard.cellWidth(boardWidth)
        val cellHeight = HexBoard.cellHeight(cell, landscape)
        val capHeight = HexBoard.capHeight(cellHeight)

        // The flat hexagon he picked for the split board: cap height = 0.95 x cap width, a cap almost
        // exactly as wide as it is tall. This assertion used to pin the *regular* 2/sqrt(3) = 1.1547,
        // which landscape no longer draws — it is rewritten, not dropped, and the regular ratio stays
        // as the reference hexagon both orientations are read against.
        assertEquals(0.95f, landscapeRatio, 0.0001f)
        assertEquals(HexBoard.LANDSCAPE_HEIGHT_RATIO, landscapeRatio, 0f)
        assertEquals(2f / sqrt(3f), HexBoard.REGULAR_HEIGHT_RATIO, 0.0001f)
        assertTrue(
            "the flat hexagon is shorter than the regular one",
            landscapeRatio < HexBoard.REGULAR_HEIGHT_RATIO,
        )
        assertEquals(cell * 0.95f, capHeight, 0.01f)
        assertEquals(4f / 3f, capHeight / cellHeight, 0.0001f)
        // Four rows at their own pitch, and the block the caps actually draw: three pitches plus one
        // cap — 3.25 x 0.95 = 3.0875 cells.
        assertEquals(4f * cellHeight, HexBoard.contentHeight(boardWidth, landscape), 0.01f)
        assertEquals(3.25f * landscapeRatio * cell, HexBoard.boardHeight(boardWidth, landscape), 0.01f)
        assertEquals(3.0875f * cell, HexBoard.boardHeight(boardWidth, landscape), 0.5f)
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
        // The block in cells falls out of the derivation instead of being written down: landscape's
        // flat 0.95 gives 3.25 x 0.95 = 3.0875 cells (the 3.7528 this used to pin belonged to the
        // regular hexagon, which landscape no longer draws — rewritten, not dropped), and portrait
        // keeps 3.25 x 1.5 = 4.875.
        assertEquals(3.0875f, HexBoard.blockHeightInCells(landscape), 0.0001f)
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
    fun `landscape is capped shorter - 45 percent of the height - and split into two halves`() {
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

        // The flat landscape ratio, and the 157.4089 px cell that falls out of it: the capped 486 px
        // block over the derived 3.25 x 0.95 = 3.0875 cells. This assertion used to pin the regular
        // 1.1547 and its 129.5041 px cell, which the flat hexagon replaced — rewritten, not dropped.
        assertEquals(0.95f, landscapeRatio, 0.0001f)
        assertEquals(486f / HexBoard.blockHeightInCells(landscape), cell, 0.0001f)
        assertEquals(157.42f, cell, 0.5f)
        assertEquals(486f, board.boardHeight, 0.01f)

        // The block is 45% of the screen and the app above keeps the other 55%.
        assertEquals(0.45f, board.boardHeight / displayHeight, 0.001f)
        assertTrue("the app keeps more than half the height", displayHeight - board.boardHeight > displayHeight * 0.5f)

        // The approved split: two five-cell halves anchored to the two edges — 787.1 px each — with
        // the empty 668.5 px channel between them (half a cell narrower than before the space bar
        // became two hexagons per side). This replaced the single centred board's two
        // 552.48 px side bands; the old band assertion is rewritten, not dropped, and the board now
        // spans the viewport instead of sitting in the middle of it.
        assertTrue(board.split)
        assertEquals(0f, board.leftInset, 0.01f)
        assertEquals(2400f, board.boardWidth, 0.01f)
        assertEquals(787.1f, HexBoard.SPLIT_COLUMNS_PER_HALF * cell, 0.5f)
        val channel = board.channel!!
        assertEquals(668.5f, channel.width, 0.5f)
        assertEquals(channel.start, board.keys.filter { it.centerX < channel.start }.maxOf { it.right }, 0.01f)
        assertEquals(channel.end, board.keys.filter { it.centerX > channel.end }.minOf { it.left }, 0.01f)
        // The screen's middle is dead space: a tap there is aimed at neither hand.
        assertTrue(channel.contains(1200f))
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
    fun `the letter set is the same in both orientations - the split only rearranges it`() {
        val portraitBoard = placement(sizing = portrait)
        val landscapeBoard = placement(
            sizing = landscape,
            viewportWidth = 2400f,
            availableHeight = HexBoard.cappedBoardHeight(2400f, 1080f, landscape),
        )

        // Same stagger, measured in cells so the different cell size cannot matter. The split keeps
        // each row's phase, read from its half's own edge, so the offsets off the board's own left edge
        // still read 0 / 0.5 / 1 / 1.5 — the honeycomb invariant survives the split.
        for ((name, board) in listOf("portrait" to portraitBoard, "landscape" to landscapeBoard)) {
            assertCells(
                listOf(0f, 0.5f, 1f, 1.5f),
                HexBoard.rowOffsetsInCells(board.keys, board.cellWidth),
                "$name row offsets",
            )
            assertCells(
                listOf(0.5f, 0.5f, 0.5f),
                HexBoard.adjacentStaggerStepsInCells(board.keys, board.cellWidth),
                "$name: adjacent stagger steps",
            )
        }

        // Same letters, in the same QWERTY order left to right. This test used to compare letter
        // *columns* and assert the whole grid identical in both orientations — which the split
        // deliberately breaks, and the halves' own division is pinned in HexSplitBoardTest. The
        // letter-set comparison below is what that assertion is rewritten to.
        fun letterOrder(board: HexBoard.HexPlacement): List<String> = (0..3).flatMap { row ->
            board.keys.filter { it.row == row }.sortedBy { it.left }.map { it.key.outputText }
        }.filter { it.length == 1 && it[0] in 'a'..'z' }

        val expected = listOf("q", "w", "e", "r", "t") + listOf("y", "u", "i", "o", "p") +
            listOf("a", "s", "d", "f", "g") + listOf("h", "j", "k", "l") +
            listOf("z", "x", "c", "v") + listOf("b", "n", "m")
        assertEquals(expected, letterOrder(portraitBoard))
        assertEquals(expected, letterOrder(landscapeBoard))

        // The space is the one key the split reshapes: one fused three-cell bar, centred, in portrait;
        // two single cells in landscape, one per half.
        val portraitSpace = portraitBoard.keys.filter { it.key.type == KeyType.SPACE }
        assertEquals("portrait: one fused space", 1, portraitSpace.size)
        assertEquals(3f, portraitSpace.single().key.widthWeight, 0.0001f)
        val portraitLeft = portraitBoard.keys.minOf { it.left }
        assertEquals(
            "portrait: space centred on the board",
            portraitBoard.boardWidth / 2f,
            portraitSpace.single().centerX - portraitLeft,
            0.01f,
        )

        val landscapeSpaces = landscapeBoard.keys.filter { it.key.type == KeyType.SPACE }
        assertEquals("landscape: two spaces, one per half", 2, landscapeSpaces.size)
        // Two cells each: the space bar is two joined hexagons per thumb, not one.
        landscapeSpaces.forEach { assertEquals(2f, it.key.widthWeight, 0.0001f) }
    }

    @Test
    fun `a frame shorter than the board flattens its hexagon instead of giving up the columns`() {
        val natural = HexBoard.boardHeight(boardWidth, portrait)

        val whole = placement(sizing = portrait, availableHeight = natural)
        assertEquals(1f, whole.scale, 0.0001f)
        assertEquals(cellWidth, whole.cellWidth, 0.01f)
        assertTrue(whole.spansViewport)
        assertEquals(portraitRatio, whole.sizing.heightRatio, 0.0001f)

        // Portrait is width-bound — ten columns already spend the whole width — so the only axis a
        // short frame can take anything from is the height, and what comes down is the hexagon.
        //
        // This test used to assert the *whole board* scaling to 0.8: an 86.4 px cell, side margins,
        // and the ratio kept at 1.5. That is what the board did before the size clamp, and a bigger
        // user-chosen step is exactly the case it fails — a stepped-up board on a short frame would
        // have been drawn narrower than the screen instead of with taller keys. Rewritten, not
        // dropped: the columns keeping the width is the promise, so the cell's width is asserted
        // where the shrink factor used to be, and the ratio is now the thing that moves.
        val cramped = placement(sizing = portrait, availableHeight = natural * 0.8f)
        assertEquals("the board is still drawn at full scale", 1f, cramped.scale, 0.0001f)
        assertEquals("the cell is still the width's", cellWidth, cramped.cellWidth, 0.01f)
        assertTrue("the board still spans the width", cramped.spansViewport)
        assertEquals("no side margin opens up", 0f, cramped.leftInset, 0.01f)
        // The block fits the frame exactly, by lowering the ratio to what fits: the block is
        // 3.25 x ratio x cell, and that is the frame — so the width was never the thing that gave.
        assertEquals(natural * 0.8f, cramped.boardHeight, 0.01f)
        assertEquals(
            natural * 0.8f / (HexBoard.BLOCK_CAP_FACTOR * cramped.cellWidth),
            cramped.sizing.heightRatio,
            0.0001f,
        )
        // The clamp is a ceiling, never a raise — it only ever comes down from the orientation's
        // ratio, and only when the frame cannot hold the block.
        assertTrue(
            "the clamp lowered the ratio: ${cramped.sizing.heightRatio} < $portraitRatio",
            cramped.sizing.heightRatio < portraitRatio,
        )
        // And the caps are drawn at the ratio the placement reports, so a clamped board is still a
        // honeycomb — flatter, not stretched.
        assertEquals(portraitRatio, scale(cellWidth, portrait), 0.0001f)
        assertEquals(cramped.sizing.heightRatio, scale(cramped.cellWidth, cramped.sizing), 0.0001f)
        // A frame with room for the board leaves the ratio exactly alone.
        assertEquals(portraitRatio, whole.sizing.heightRatio, 0f)
    }

    @Test
    fun `portrait is one centred board and landscape is two halves anchored to the edges`() {
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
        assertEquals(157.42f, landscapeBoard.cellWidth, 0.5f)
        assertEquals(486f, landscapeBoard.boardHeight, 0.01f)
        assertEquals(0.45f, landscapeBoard.boardHeight / 1080f, 0.01f)

        // Portrait keeps every promise of a single board — sized to fit, centred, no side margin.
        assertFitsCentredAndInside("portrait", portraitBoard, 1080f, HexBoard.boardHeight(1080f, portrait))
        assertCells(
            listOf(0f, 0.5f, 1f, 1.5f),
            HexBoard.rowOffsetsInCells(portraitBoard.keys, portraitBoard.cellWidth),
            "portrait row offsets",
        )
        assertEquals(listOf(10, 9, 8, 5), (0..3).map { row -> portraitBoard.keys.count { it.row == row } })

        // Landscape is the split: two halves pinned to the two edges with the channel between them.
        // It no longer shares portrait's centring or its side bands — that is the change, and this is
        // where the old shared assertions are rewritten to the two orientations' own promises.
        assertSplitFitsBothEdges("landscape", landscapeBoard, 2400f, landscapeAvailable)
        assertCells(
            listOf(0f, 0.5f, 1f, 1.5f),
            HexBoard.rowOffsetsInCells(landscapeBoard.keys, landscapeBoard.cellWidth),
            "landscape row offsets",
        )
        val landscapeChannel = landscapeBoard.channel!!
        assertEquals(
            listOf(5, 5, 4, 3),
            (0..3).map { row ->
                landscapeBoard.keys.count { it.row == row && it.centerX < landscapeChannel.start }
            },
        )
        assertEquals(
            listOf(5, 4, 4, 3),
            (0..3).map { row ->
                landscapeBoard.keys.count { it.row == row && it.centerX > landscapeChannel.end }
            },
        )

        // The one thing the orientations deliberately do NOT share: the hexagon ratio. Portrait's is
        // the tall 1.5 that spends the free height; landscape's is the flat 0.95 he picked for the
        // split. The old test asserted these two were equal, which was true only while both were
        // 1.1547.
        assertEquals(1.5f, scale(portraitBoard.cellWidth, portrait), 0.0001f)
        assertEquals(0.95f, scale(landscapeBoard.cellWidth, landscape), 0.0001f)
        assertTrue(
            "portrait's hexagon is the taller one",
            scale(portraitBoard.cellWidth, portrait) > scale(landscapeBoard.cellWidth, landscape),
        )
    }

    /**
     * The split's promises, as one check: two halves anchored to the viewport's edges, the block
     * filling the frame, the flat hexagon kept, and the channel empty between them.
     *
     * The landscape counterpart of [assertFitsCentredAndInside] — the split deliberately does not
     * centre, so it gets its own promises rather than a weakened version of the single board's.
     */
    private fun assertSplitFitsBothEdges(
        name: String,
        placement: HexBoard.HexPlacement,
        viewportWidth: Float,
        availableHeight: Float,
    ) {
        val sizing = placement.sizing
        val channel = placement.channel ?: error("$name: a split board has a channel")
        val left = placement.keys.filter { it.centerX < channel.start }
        val right = placement.keys.filter { it.centerX > channel.end }

        // 1. The derived cell makes the block fit the available height, and fill it exactly — the
        //    height is what binds in landscape.
        assertTrue("$name: block fits", placement.boardHeight <= availableHeight + 0.01f)
        assertEquals("$name: block fills the frame", availableHeight, placement.boardHeight, 0.01f)
        assertTrue("$name: height binds", HexBoard.heightBinds(viewportWidth, availableHeight, sizing))

        // 2. The hexagon keeps the orientation's ratio — the flat 0.95.
        val capHeight = HexBoard.capHeight(HexBoard.cellHeight(placement.cellWidth, sizing))
        assertEquals("$name: cap ratio", sizing.heightRatio, capHeight / placement.cellWidth, 0.0001f)
        assertEquals("$name: placement reports its ratio", sizing.heightRatio, placement.heightRatio, 0f)

        // 3. Each half is anchored to its own edge: the outer rows sit on the edges, so there is no
        //    side margin, and the channel is the whole of the space between the halves.
        assertEquals("$name: anchored to the left edge", 0f, left.minOf { it.left }, 0.01f)
        assertEquals("$name: anchored to the right edge", viewportWidth, right.maxOf { it.right }, 0.01f)
        assertEquals("$name: no side margin", 0f, placement.leftInset, 0.01f)
        assertTrue("$name: the channel is real", channel.width > 1f)
        assertEquals(
            "$name: channel opens at the left inner edge",
            left.maxOf { it.right },
            channel.start,
            0.01f,
        )
        assertEquals(
            "$name: channel opens at the right inner edge",
            right.minOf { it.left },
            channel.end,
            0.01f,
        )

        // 4. No key crosses the channel, caps included, and nothing leaves the viewport.
        placement.keys.forEach { key ->
            val label = key.key.label.ifEmpty { key.key.type.name }
            assertTrue(
                "$name: $label is on one side of the channel",
                key.right <= channel.start + 0.01f || key.left >= channel.end - 0.01f,
            )
            assertTrue("$name: $label cell within width", key.left >= -0.01f && key.right <= viewportWidth + 0.01f)
            val cap = KeyGeometry.hexShapeFor(key)
            assertTrue("$name: $label cap within width", cap.left >= -0.01f && cap.right <= viewportWidth + 0.01f)
            assertTrue("$name: $label cap below the top edge", cap.top >= -0.1f)
            assertTrue("$name: $label cap above the bottom edge", cap.bottom <= availableHeight + 0.1f)
        }
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

        // The hex board takes the honeycomb's own digit row, never the rectangle's: ten squat columns
        // would break the pattern the board is built on. QWERTY's own geometry does not move either way.
        val hexRow = Layouts.withNumberRow(Layouts.HexQwertyEn, true)
        assertNotSame(Layouts.HexQwertyEn, hexRow)
        assertEquals(Layouts.hexNumberRow, hexRow.rows.first())
        assertNotEquals(Layouts.numberRow, hexRow.rows.first())
        assertSame(Layouts.numberRow, Layouts.withNumberRow(Layouts.QwertyEn, true).rows.first())
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
        assertEquals(0xFF3A947C.toInt(), HexPalette.Jade.enterKey)
        assertEquals(0xFFF5F7F6.toInt(), HexPalette.Jade.label)
        assertEquals(0x40FFFFFF, HexPalette.Jade.pressed)
    }
}
