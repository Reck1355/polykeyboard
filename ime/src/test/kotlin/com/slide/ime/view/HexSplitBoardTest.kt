package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-handed landscape split: two halves anchored to the screen's edges, one single-cell hexagon
 * per key, and the empty channel between them.
 *
 * The ask (2026-10-16): "for landscape probably have it separated in the middle for my left and
 * right hand", and of the space, "None of those fit in, for space make it two hexagons one on each
 * side instead". That supersedes the single uniformly scaled landscape board: the letters are the
 * same 26, but divided 5 / 5, 5 / 4, 5 / 4 and 4 / 4 between a left and a right half, every key one
 * flat hexagon, and no fused trio anywhere.
 *
 * These are pure measurements — the same [HexBoard.place] the view calls, read back as numbers, with
 * no device in the loop. The numbers are pinned the way the ask gave them, with a px tolerance because
 * they are rounded: the exact cell is 486 / 3.0875 = 157.4089, so the ask's 157.42 is 0.011 high, its
 * 787.1 half is 787.045, its 112.16 pitch is 112.154, its 149.55 cap is 149.538, and its 668.5
 * channel is 668.502. The derived value is asserted directly beside the rounded one rather than
 * adjusted to it, so a drift in either is caught.
 *
 * Portrait is asserted alongside, unchanged, so the split cannot leak into it: one board, 10 / 9 / 9 /
 * 7 keys, the fused three-cell space centred, ratio 1.5, no channel.
 */
class HexSplitBoardTest {

    private val portrait = HexBoard.sizing(HexBoard.Orientation.PORTRAIT)
    private val landscape = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE)

    private val displayWidth = 2400f
    private val displayHeight = 1080f

    /** QWERTY, row by row — what the split's letters must read as, left half then right. */
    private val qwerty = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m",
    )

    /** Landscape as the IME places it on a 2400 x 1080 screen, at the 0.45 height share. */
    private fun landscapeBoard(): HexBoard.HexPlacement = HexBoard.place(
        Layouts.HexQwertyEn,
        displayWidth,
        HexBoard.cappedBoardHeight(displayWidth, displayHeight, landscape),
        landscape,
    )

    /** Portrait as the IME places it on a 1080 x 2400 screen — the untouched single board. */
    private fun portraitBoard(): HexBoard.HexPlacement =
        HexBoard.place(Layouts.HexQwertyEn, 1080f, HexBoard.boardHeight(1080f, portrait), portrait)

    private fun channelOf(board: HexBoard.HexPlacement): HexBoard.Channel =
        board.channel ?: error("the landscape board must be the split")

    private fun leftHalf(board: HexBoard.HexPlacement): List<PlacedKey> {
        val channel = channelOf(board)
        return board.keys.filter { it.centerX < channel.start }
    }

    private fun rightHalf(board: HexBoard.HexPlacement): List<PlacedKey> {
        val channel = channelOf(board)
        return board.keys.filter { it.centerX > channel.end }
    }

    private fun row(keys: List<PlacedKey>, index: Int): List<PlacedKey> =
        keys.filter { it.row == index }.sortedBy { it.left }

    /** The board's letter keys, row by row left to right — the order a QWERTY read gives. */
    private fun letters(keys: List<PlacedKey>): List<String> =
        (0..3).flatMap { index -> row(keys, index).map { it.key.outputText } }
            .filter { it.length == 1 && it[0] in 'a'..'z' }

    private fun spaces(board: HexBoard.HexPlacement): List<PlacedKey> =
        board.keys.filter { it.key.type == KeyType.SPACE }

    @Test
    fun `landscape splits the letters 5-5, 5-4, 5-4 and 4-4 between the two halves`() {
        val board = landscapeBoard()
        val left = leftHalf(board)
        val right = rightHalf(board)

        assertEquals(listOf(5, 5, 4, 4), (0..3).map { row(left, it).size })
        assertEquals(listOf(5, 4, 3, 4), (0..3).map { row(right, it).size })

        // Each half's own letters, in the order they are drawn.
        assertEquals(
            listOf("q", "w", "e", "r", "t", "a", "s", "d", "f", "g", "z", "x", "c", "v"),
            letters(left),
        )
        assertEquals(
            listOf("y", "u", "i", "o", "p", "h", "j", "k", "l", "b", "n", "m"),
            letters(right),
        )

        // Across both halves, left to right, it is QWERTY — exactly the single board's letter set,
        // nothing duplicated and nothing dropped.
        assertEquals(qwerty, letters(board.keys))
        assertEquals("26 letters", 26, letters(board.keys).size)
        assertEquals("none duplicated", 26, letters(board.keys).toSet().size)
        assertEquals(letters(portraitBoard().keys).toSet(), letters(board.keys).toSet())
        assertEquals(
            Layouts.HexQwertyEn.rows.flatMap { it.keys }.map { it.outputText }
                .filter { it.length == 1 && it[0] in 'a'..'z' }.toSet(),
            letters(board.keys).toSet(),
        )
    }

    @Test
    fun `every key's centre is in its own half and the middle of the board is empty`() {
        val board = landscapeBoard()
        val channel = channelOf(board)

        assertTrue("the channel is real", channel.width > 0f)
        assertTrue("the board's middle is inside the channel", channel.contains(displayWidth / 2f))

        board.keys.forEach { key ->
            val label = key.key.label.ifEmpty { key.key.type.name }
            assertTrue(
                "$label centre is in one half",
                key.centerX < channel.start || key.centerX > channel.end,
            )
            assertFalse("$label centre is not in the channel", channel.contains(key.centerX))
            // No key crosses the corridor, cap included (the cap's width is the cell's).
            assertTrue(
                "$label does not cross the channel",
                key.right <= channel.start + 0.01f || key.left >= channel.end - 0.01f,
            )
        }

        // A scan across the middle of the board finds an empty gap of at least the channel in every
        // row, with no key centre in it.
        for (index in 0..3) {
            val keys = row(board.keys, index)
            val widestGap = keys.zipWithNext { a, b -> b.left - a.right }.maxOrNull() ?: 0f
            assertTrue(
                "row $index leaves the channel open: $widestGap >= ${channel.width}",
                widestGap >= channel.width - 0.01f,
            )
            keys.forEach { assertFalse("row $index: no key centre in the channel", channel.contains(it.centerX)) }
        }
    }

    @Test
    fun `both space keys are two cells wide, one per half, and both type a space`() {
        val board = landscapeBoard()
        val all = spaces(board)

        assertEquals("two spaces, one per thumb", 2, all.size)
        all.forEach { space ->
            // Asserted on the key type, not the label: both are real SPACE keys.
            assertEquals(KeyType.SPACE, space.key.type)
            assertEquals("two cells", 2f, space.key.widthWeight, 0.0001f)
            assertEquals("two cells", 2f * board.cellWidth, space.width, 0.01f)
            assertEquals(" ", space.key.outputText)
        }
        assertEquals("one in the left half", 1, leftHalf(board).count { it.key.type == KeyType.SPACE })
        assertEquals("one in the right half", 1, rightHalf(board).count { it.key.type == KeyType.SPACE })
    }

    @Test
    fun `a tap in the channel resolves no key, and each half resolves its own`() {
        val board = landscapeBoard()
        val channel = channelOf(board)
        val y = row(board.keys, 0).first().centerY

        // The corridor is dead across its whole width, not just at its centre: a finger aimed at
        // neither hand types nothing rather than snapping to the nearest edge key.
        assertNull("the channel centre is dead", KeyGeometry.hitTestHex(board.keys, channel.center, y))
        assertNull("the cell grid agrees", KeyGeometry.hitTest(board.keys, channel.center, y))
        for (step in 1..5) {
            val x = channel.start + channel.width * step / 6f
            assertNull("channel at $x", KeyGeometry.hitTestHex(board.keys, x, y))
        }

        // A point in either half still resolves to that half's key, by nearest centre.
        val leftKey = row(board.keys, 0).first()
        val rightKey = row(board.keys, 0).last()
        assertTrue("the left point is in the left half", leftKey.centerX < channel.start)
        assertTrue("the right point is in the right half", rightKey.centerX > channel.end)
        assertSame(leftKey, KeyGeometry.hitTestHex(board.keys, leftKey.centerX, leftKey.centerY))
        assertSame(rightKey, KeyGeometry.hitTestHex(board.keys, rightKey.centerX, rightKey.centerY))
    }

    @Test
    fun `landscape numbers - flat 0_95, a 486 px block, two 787 px halves and a 668 px channel`() {
        val available = HexBoard.cappedBoardHeight(displayWidth, displayHeight, landscape)
        val board = landscapeBoard()
        val cell = board.cellWidth

        // The geometry, straight from the sizing.
        assertEquals(0.95f, landscape.heightRatio, 0f)
        assertEquals(HexBoard.LANDSCAPE_HEIGHT_RATIO, landscape.heightRatio, 0f)
        assertEquals(0.45f, landscape.heightFraction, 0f)
        assertTrue(landscape.split)
        assertFalse(portrait.split)
        assertEquals(1.1547f, HexBoard.REGULAR_HEIGHT_RATIO, 0.0001f)
        assertTrue("the flat hexagon is shorter than the regular one", landscape.heightRatio < HexBoard.REGULAR_HEIGHT_RATIO)

        // The block, and the cell it yields: 486 / 3.0875 = 157.4089, not the 157.42 the ask rounds it
        // to. Both are pinned — the derived value exactly, the rounded one with the px tolerance.
        assertEquals(486f, available, 0.01f)
        assertEquals(486f, board.boardHeight, 0.01f)
        assertEquals(available / HexBoard.blockHeightInCells(landscape), cell, 0.0001f)
        assertEquals(157.42f, cell, 0.5f)
        assertEquals(112.16f, HexBoard.cellHeight(cell, landscape), 0.5f)
        assertEquals(149.55f, HexBoard.capHeight(HexBoard.cellHeight(cell, landscape)), 0.5f)

        // Each half is five cells from its own edge, plus its row's half-cell phase; the channel is
        // what is left. It narrowed by half a cell when the space bar became two hexagons per side.
        assertEquals(787.1f, HexBoard.SPLIT_COLUMNS_PER_HALF * cell, 0.5f)
        val channel = channelOf(board)
        assertEquals(668.5f, channel.width, 0.5f)

        // The board spans the viewport, so there is no side margin any more — the free width is the
        // channel, between the halves.
        assertEquals(displayWidth, board.boardWidth, 0.01f)
        assertEquals(0f, board.leftInset, 0.01f)
        assertTrue(board.spansViewport)

        // The invariant: the channel opens exactly where each half's inner edge ends, so a future cap
        // or phase change moves both together.
        val halves = HexBoard.splitHalves(Layouts.HexQwertyEn)
        assertEquals(5.5f, halves.left.spanInCells, 0.0001f)
        assertEquals(5.5f, halves.right.spanInCells, 0.0001f)
        // The layout-free fit rule carries the same width, so the halves cannot be drawn wider than
        // the viewport holds: 11 cells — both halves now reach their five cells plus the row's
        // half-cell phase, which is the space bar's second hexagon arriving on both sides.
        assertEquals(11f, HexBoard.SPLIT_WIDTH_IN_CELLS, 0.0001f)
        assertEquals(halves.spanInCells, HexBoard.SPLIT_WIDTH_IN_CELLS, 0.0001f)
        assertEquals(HexBoard.SPLIT_WIDTH_IN_CELLS, HexBoard.widthInCells(landscape), 0f)
        assertEquals(HexBoard.COLUMNS.toFloat(), HexBoard.widthInCells(portrait), 0f)
        assertEquals(halves.left.spanInCells * cell, channel.start, 0.01f)
        assertEquals(displayWidth - halves.right.spanInCells * cell, channel.end, 0.01f)
        assertEquals(channel.start, leftHalf(board).maxOf { it.right }, 0.01f)
        assertEquals(channel.end, rightHalf(board).minOf { it.left }, 0.01f)
    }

    @Test
    fun `portrait is untouched - one board, 10 9 7 7, the fused centred space, ratio 1_5`() {
        val board = portraitBoard()

        assertFalse(portrait.split)
        assertFalse(board.split)
        assertNull("portrait has no channel", board.channel)

        assertEquals(listOf(10, 9, 7, 7), (0..3).map { row(board.keys, it).size })

        val offsets = HexBoard.rowOffsetsInCells(board.keys, board.cellWidth)
        assertEquals(4, offsets.size)
        assertEquals(0f, offsets[0], 0.0001f)
        assertEquals(0.5f, offsets[1], 0.0001f)
        // The z-row is inset a cell at each end since caps and backspace left it, which puts it
        // at 1.0: still exactly half a cell from the a-row and from the bottom row, which is what
        // the honeycomb needs, and an even number of half-cells, so its phase is unmoved.
        assertEquals(1f, offsets[2], 0.0001f)
        assertEquals(0.5f, offsets[3], 0.0001f)

        // One space, still the fused three-cell bar, still centred on the board.
        val all = spaces(board)
        assertEquals(1, all.size)
        assertEquals(3f, all.single().key.widthWeight, 0.0001f)
        assertEquals(3f * board.cellWidth, all.single().width, 0.01f)
        assertEquals(3, HexBoard.spanCells(all.single().key.widthWeight))
        val boardLeft = board.keys.minOf { it.left }
        assertEquals(board.boardWidth / 2f, all.single().centerX - boardLeft, 0.01f)

        // And the portrait hexagon is still the tall one.
        assertEquals(1.5f, portrait.heightRatio, 0.0001f)
        assertEquals(HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION, portrait.heightRatio, 0f)
        assertEquals(108f, board.cellWidth, 0.01f)
        assertEquals(526.5f, board.boardHeight, 0.01f)
        assertTrue(board.spansViewport)
    }

    @Test
    fun `rotating portrait to landscape and back leaves both layouts correct`() {
        val portraitBefore = portraitBoard()
        val rotated = landscapeBoard()
        val portraitAfter = portraitBoard()

        // Landscape is the split, before anything is rotated back.
        assertEquals(2, spaces(rotated).size)
        assertEquals(qwerty, letters(rotated.keys))
        assertEquals(listOf(5, 5, 4, 4), (0..3).map { row(leftHalf(rotated), it).size })
        assertEquals(listOf(5, 4, 3, 4), (0..3).map { row(rightHalf(rotated), it).size })

        // Portrait, before and after the rotation, is the same untouched board key for key — the split
        // did not leak into it.
        assertEquals(listOf(10, 9, 7, 7), (0..3).map { row(portraitAfter.keys, it).size })
        assertNull(portraitAfter.channel)
        assertEquals(1, spaces(portraitAfter).size)
        assertEquals(3f, spaces(portraitAfter).single().key.widthWeight, 0.0001f)
        assertEquals(portraitBefore.cellWidth, portraitAfter.cellWidth, 0f)
        assertEquals(portraitBefore.boardHeight, portraitAfter.boardHeight, 0f)
        assertEquals(portraitBefore.keys.size, portraitAfter.keys.size)
        portraitBefore.keys.zip(portraitAfter.keys).forEach { (before, after) ->
            assertEquals(before.key.label, after.key.label)
            assertEquals(before.key.type, after.key.type)
            assertEquals(before.left, after.left, 0f)
            assertEquals(before.top, after.top, 0f)
            assertEquals(before.right, after.right, 0f)
            assertEquals(before.bottom, after.bottom, 0f)
        }
    }
}
