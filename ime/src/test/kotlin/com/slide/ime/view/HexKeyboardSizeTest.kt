package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.Layouts
import com.slide.core.settings.KeyboardSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard-size ladder: five stops, one multiplier, and the board each stop draws — pure
 * measurements, no device.
 *
 * The ask (2026-10-16): "I want it to be a sliding thing where i van adjust size." One step of the
 * ladder is one tenth of the size, and [KeyboardSize.DEFAULT_STEP] is the board exactly as it was
 * approved — which is the load-bearing assertion here, because "the default is unchanged" is a
 * stronger claim than "the default looks the same" and only the pinned numbers can carry it. The
 * default board's numbers are pinned as the earlier slices pinned them (portrait ratio 1.5 with a
 * 108 px cell, a 162 px cap and a 526.5 px block; landscape ratio 0.95 with a 486 px block, a
 * 157.40892 px cell, a 112.15385 px pitch, 787.04462 px halves and a 747.20630 px channel) and the
 * step is added *around* them rather than through them, so a drifted ladder fails beside the numbers
 * it drifted from.
 *
 * The two orientations spend the step differently, and both are asserted at all five stops: portrait
 * is width-bound, so it multiplies its hexagon ratio (1.2 .. 1.8) and a bigger step can only make
 * the hexagons *taller*; landscape multiplies its share of the display height (0.36 .. 0.54) and the
 * cell follows the cap. Two promises ride on the last of those: portrait's ten columns never give up
 * the width — the block is clamped to the frame by lowering the ratio instead — and the landscape
 * board stays a *split* at every step, with both space hexagons present and the channel between the
 * halves still open at the largest one.
 */
class HexKeyboardSizeTest {

    /** The five stops, in order — read from the ladder rather than restated. */
    private val steps = KeyboardSize.STEPS.toList()

    private val portraitWidth = 1080f
    private val portraitHeight = 2400f
    private val landscapeWidth = 2400f
    private val landscapeHeight = 1080f

    private fun portraitSizing(step: Int) = HexBoard.sizing(HexBoard.Orientation.PORTRAIT, step)

    private fun landscapeSizing(step: Int) = HexBoard.sizing(HexBoard.Orientation.LANDSCAPE, step)

    /**
     * Portrait as the IME places it on a 1080 x 2400 screen at [step]: the frame the height share
     * hands the board there, which for portrait is its own natural block.
     */
    private fun portraitBoard(step: Int): HexBoard.HexPlacement {
        val sizing = portraitSizing(step)
        return HexBoard.place(
            Layouts.HexQwertyEn,
            portraitWidth,
            HexBoard.cappedBoardHeight(portraitWidth, portraitHeight, sizing),
            sizing,
        )
    }

    /** Landscape as the IME places it on a 2400 x 1080 screen at [step] — the capped share. */
    private fun landscapeBoard(step: Int): HexBoard.HexPlacement {
        val sizing = landscapeSizing(step)
        return HexBoard.place(
            Layouts.HexQwertyEn,
            landscapeWidth,
            HexBoard.cappedBoardHeight(landscapeWidth, landscapeHeight, sizing),
            sizing,
        )
    }

    private fun row(keys: List<PlacedKey>, index: Int): List<PlacedKey> =
        keys.filter { it.row == index }.sortedBy { it.left }

    /** A half's letters, row by row left to right. */
    private fun letters(keys: List<PlacedKey>): List<String> =
        (0..3).flatMap { index -> row(keys, index).map { it.key.outputText } }
            .filter { it.length == 1 && it[0] in 'a'..'z' }

    private fun spaces(board: HexBoard.HexPlacement): List<PlacedKey> =
        board.keys.filter { it.key.type == KeyType.SPACE }

    private fun assertCells(expected: List<Float>, actual: List<Float>, message: String) {
        assertEquals("$message: count", expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) -> assertEquals(message, want, got, 0.0001f) }
    }

    @Test
    fun `the default stop is the approved board, byte for byte, in both orientations`() {
        assertEquals(listOf(-2, -1, 0, 1, 2), steps)

        // One tenth per stop, so the ladder is 0.8 / 0.9 / 1.0 / 1.1 / 1.2 and the default is exactly
        // 1 — an exact 1, because everything downstream of it is expected to come out unchanged.
        steps.forEachIndexed { index, step ->
            assertEquals("step $step multiplier", listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f)[index], HexBoard.sizeMultiplier(step), 0.0001f)
        }
        assertEquals(1f, HexBoard.sizeMultiplier(KeyboardSize.DEFAULT_STEP), 0f)

        // The default stop *is* the sizing with no step argument at all, so every caller that
        // predates the ladder still asks for exactly the board it always got.
        assertEquals(portraitSizing(KeyboardSize.DEFAULT_STEP), HexBoard.sizing(HexBoard.Orientation.PORTRAIT))
        assertEquals(landscapeSizing(KeyboardSize.DEFAULT_STEP), HexBoard.sizing(HexBoard.Orientation.LANDSCAPE))

        val portrait = portraitSizing(KeyboardSize.DEFAULT_STEP)
        assertEquals(1f, portrait.heightFraction, 0f)
        assertEquals(1.5f, portrait.heightRatio, 0f)
        assertEquals(HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION, portrait.heightRatio, 0f)
        assertFalse(portrait.split)

        val landscape = landscapeSizing(KeyboardSize.DEFAULT_STEP)
        assertEquals(0.45f, landscape.heightFraction, 0f)
        assertEquals(HexBoard.LANDSCAPE_HEIGHT_FRACTION, landscape.heightFraction, 0f)
        assertEquals(0.95f, landscape.heightRatio, 0f)
        assertTrue(landscape.split)

        // Portrait at the default stop: ten 108 px cells, a 162 px cap at a 121.5 px pitch, and the
        // 526.5 px block — 3.25 x 1.5 x 108, with none of it hard-coded.
        val portraitBoard = portraitBoard(KeyboardSize.DEFAULT_STEP)
        assertEquals(108f, portraitBoard.cellWidth, 0.001f)
        assertEquals(121.5f, HexBoard.cellHeight(portraitBoard.cellWidth, portrait), 0.001f)
        assertEquals(162f, HexBoard.capHeight(HexBoard.cellHeight(portraitBoard.cellWidth, portrait)), 0.001f)
        assertEquals(526.5f, portraitBoard.boardHeight, 0.001f)
        assertEquals(108f * HexBoard.blockHeightInCells(portrait), portraitBoard.boardHeight, 0.001f)

        // Landscape at the default stop: the 486 px block, and the cell, pitch, halves and channel
        // that fall out of it — the split's own pinned numbers, unchanged by the ladder's arrival.
        val split = landscapeBoard(KeyboardSize.DEFAULT_STEP)
        val cell = split.cellWidth
        assertEquals(486f, HexBoard.cappedBoardHeight(landscapeWidth, landscapeHeight, landscape), 0.001f)
        assertEquals(486f, split.boardHeight, 0.01f)
        assertEquals(486f / HexBoard.blockHeightInCells(landscape), cell, 0.001f)
        assertEquals(157.40892f, cell, 0.001f)
        assertEquals(112.15385f, HexBoard.cellHeight(cell, landscape), 0.001f)
        assertEquals(787.04462f, HexBoard.SPLIT_COLUMNS_PER_HALF * cell, 0.001f)
        assertEquals(747.20630f, split.channel?.width ?: 0f, 0.001f)
    }

    @Test
    fun `every step moves the size one way and only the axis it is allowed to move`() {
        // Portrait: the block and the hexagon grow with the step, and the cell does not move at all.
        // The ten columns spend the whole width at every stop, which is what makes a bigger step
        // mean taller keys and nothing else.
        val portraitBlocks = steps.map { portraitBoard(it).boardHeight }
        val portraitRatios = steps.map { portraitSizing(it).heightRatio }
        portraitBlocks.zipWithNext { below, above ->
            assertTrue("portrait block grows with the step: $below -> $above", above > below)
        }
        portraitRatios.zipWithNext { below, above ->
            assertTrue("portrait hexagon grows with the step: $below -> $above", above > below)
        }
        steps.forEach { step ->
            assertEquals("step $step keeps the ten columns at the full width", 108f, portraitBoard(step).cellWidth, 0.001f)
            assertTrue("step $step spans the width", portraitBoard(step).spansViewport)
        }

        // Landscape: the block, the cell and each half all grow with the step, and the channel — what
        // is left of the width — shrinks by exactly as much. Nothing here changes side: both halves
        // keep a positive span and the channel stays open at every stop.
        val landscapeBlocks = steps.map { landscapeBoard(it).boardHeight }
        val landscapeCells = steps.map { landscapeBoard(it).cellWidth }
        val halves = steps.map { HexBoard.SPLIT_COLUMNS_PER_HALF * landscapeBoard(it).cellWidth }
        val channels = steps.map { landscapeBoard(it).channel?.width ?: 0f }
        landscapeBlocks.zipWithNext { below, above ->
            assertTrue("landscape block grows with the step: $below -> $above", above > below)
        }
        landscapeCells.zipWithNext { below, above ->
            assertTrue("landscape cell grows with the step: $below -> $above", above > below)
        }
        halves.zipWithNext { below, above ->
            assertTrue("landscape halves reach further with the step: $below -> $above", above > below)
        }
        channels.zipWithNext { wider, narrower ->
            assertTrue("landscape channel shrinks with the step: $wider -> $narrower", narrower < wider)
        }
        channels.forEach { assertTrue("the channel stays open: $it", it > 0f) }
    }

    @Test
    fun `the five stops derive the five ratios and the five landscape shares`() {
        val ratios = listOf(1.2f, 1.35f, 1.5f, 1.65f, 1.8f)
        val shares = listOf(0.36f, 0.405f, 0.45f, 0.495f, 0.54f)

        steps.forEachIndexed { index, step ->
            val multiplier = HexBoard.sizeMultiplier(step)

            // Portrait spends the multiplier on the hexagon, and stays uncapped: its share is 1.0 at
            // every step, because it is the ratio that carries the size and portrait has no display
            // height to give up.
            val portrait = portraitSizing(step)
            assertEquals("step $step ratio", ratios[index], portrait.heightRatio, 0.0001f)
            assertEquals(
                "step $step ratio is the approved one scaled",
                HexKeyMetrics.PORTRAIT_HEIGHT_FRACTION * multiplier,
                portrait.heightRatio,
                0.0001f,
            )
            assertEquals("step $step portrait share", 1f, portrait.heightFraction, 0f)
            assertFalse("step $step portrait is single", portrait.split)

            // Landscape spends it on the share of the display height, and keeps the flat hexagon:
            // the step resizes the board, it does not reshape it.
            val landscape = landscapeSizing(step)
            assertEquals("step $step share", shares[index], landscape.heightFraction, 0.0001f)
            assertEquals(
                "step $step share is the approved one scaled",
                HexBoard.LANDSCAPE_HEIGHT_FRACTION * multiplier,
                landscape.heightFraction,
                0.0001f,
            )
            assertEquals("step $step landscape ratio", 0.95f, landscape.heightRatio, 0f)
            assertEquals(
                "step $step cap",
                landscapeHeight * shares[index],
                HexBoard.cappedBoardHeight(landscapeWidth, landscapeHeight, landscape),
                0.01f,
            )
            assertTrue("step $step landscape is the split", landscape.split)
        }
    }

    @Test
    fun `the five steps draw these five boards`() {
        // The ladder's numbers, all of them, at each stop: portrait's ratio, pitch, cap and block,
        // and the split's share, block, cell, pitch, each half's reach and the channel. The default
        // row is the approved board; the four rows around it are what the step buys, and pinning
        // them is what makes "each step resizes the board" a measurement rather than a claim about
        // something moving in roughly the right direction.
        //
        // Nothing here is hard-coded in the geometry: every number is derived, so a drifted constant
        // fails beside the row it drifted in.
        val portraitRatio = listOf(1.2f, 1.35f, 1.5f, 1.65f, 1.8f)
        val portraitPitch = listOf(97.2f, 109.35f, 121.5f, 133.65f, 145.8f)
        val portraitCap = listOf(129.6f, 145.8f, 162f, 178.2f, 194.4f)
        val portraitBlock = listOf(421.2f, 473.85f, 526.5f, 579.15f, 631.8f)
        val landscapeShare = listOf(0.36f, 0.405f, 0.45f, 0.495f, 0.54f)
        val landscapeBlock = listOf(388.8f, 437.4f, 486f, 534.6f, 583.2f)
        val landscapeCell = listOf(125.92713f, 141.66802f, 157.40892f, 173.1498f, 188.89069f)
        val landscapePitch = listOf(89.72308f, 100.93846f, 112.15385f, 123.36923f, 134.58462f)
        val landscapeHalves = listOf(629.63563f, 708.34008f, 787.04462f, 865.74899f, 944.45344f)
        val landscapeChannel = listOf(1077.76518f, 912.48583f, 747.20630f, 581.92713f, 416.64777f)

        steps.forEachIndexed { index, step ->
            val portrait = portraitBoard(step)
            val row = "step $step portrait"
            assertEquals("$row ratio", portraitRatio[index], portrait.sizing.heightRatio, 0.0001f)
            assertEquals("$row cell", 108f, portrait.cellWidth, 0.001f)
            assertEquals("$row pitch", portraitPitch[index], HexBoard.cellHeight(portrait.cellWidth, portrait.sizing), 0.001f)
            assertEquals("$row cap", portraitCap[index], HexBoard.capHeight(HexBoard.cellHeight(portrait.cellWidth, portrait.sizing)), 0.001f)
            assertEquals("$row block", portraitBlock[index], portrait.boardHeight, 0.001f)

            val split = landscapeBoard(step)
            val cell = split.cellWidth
            val name = "step $step landscape"
            assertEquals("$name share", landscapeShare[index], split.sizing.heightFraction, 0.0001f)
            assertEquals("$name block", landscapeBlock[index], split.boardHeight, 0.01f)
            assertEquals("$name cell", landscapeCell[index], cell, 0.001f)
            assertEquals("$name pitch", landscapePitch[index], HexBoard.cellHeight(cell, split.sizing), 0.001f)
            assertEquals("$name five cells", landscapeHalves[index], HexBoard.SPLIT_COLUMNS_PER_HALF * cell, 0.001f)
            assertEquals("$name channel", landscapeChannel[index], split.channel?.width ?: 0f, 0.001f)
            // The split's own accounting, checked at every stop: each half reaches in from its own
            // edge, and the width is those two reaches plus the channel between them — the channel is
            // what the halves leave, not a reserved band. (A half's *reach* is its widest row's span,
            // which is half a cell more than the five cells above, so it is read from the layout
            // rather than restated.)
            val halves = HexBoard.splitHalves(Layouts.HexQwertyEn)
            val channel = split.channel ?: error("a split board has a channel")
            assertEquals("$name: the channel opens at the left half's reach", halves.left.spanInCells * cell, channel.start, 0.01f)
            assertEquals("$name: the channel closes at the right half's reach", landscapeWidth - halves.right.spanInCells * cell, channel.end, 0.01f)
            assertEquals("$name: both reaches plus the channel are the width", landscapeWidth, (halves.spanInCells * cell) + channel.width, 0.01f)
        }
    }

    @Test
    fun `portrait keeps its rows, offsets, letters and fused space at every step`() {
        val default = portraitBoard(KeyboardSize.DEFAULT_STEP)
        val defaultOffsets = HexBoard.rowOffsetsInCells(default.keys, default.cellWidth)
        val defaultLetters = letters(default.keys)

        steps.forEach { step ->
            val board = portraitBoard(step)

            assertFalse("step $step: portrait is never the split", board.split)
            assertNull("step $step: portrait has no channel", board.channel)
            assertTrue("step $step: the board spans the width", board.spansViewport)
            assertTrue("step $step: no key leaves the viewport", board.keys.all { it.left >= -0.01f && it.right <= portraitWidth + 0.01f })

            // The grid itself: four rows of ten, nine, nine and seven keys, at the same stagger —
            // measured off the placed keys, so a resize that moved a row would fail here.
            assertEquals("step $step rows", listOf(10, 9, 9, 7), (0..3).map { row(board.keys, it).size })
            assertCells(defaultOffsets, HexBoard.rowOffsetsInCells(board.keys, board.cellWidth), "step $step row offsets")
            assertCells(
                listOf(0.5f, 0.5f, 0.5f),
                HexBoard.adjacentStaggerStepsInCells(board.keys, board.cellWidth),
                "step $step adjacent stagger",
            )
            // Same letters, same order left to right — resizing rearranges nothing.
            assertEquals("step $step letters", defaultLetters, letters(board.keys))
            assertEquals("step $step letter set", 26, letters(board.keys).toSet().size)

            // One fused three-cell space, centred on the board, at the step's own cell.
            val space = spaces(board).single()
            assertEquals("step $step: one fused space", 1, spaces(board).size)
            assertEquals("step $step: three cells", 3f, space.key.widthWeight, 0.0001f)
            assertEquals("step $step: three cells wide", 3f * board.cellWidth, space.width, 0.01f)
            assertEquals(3, HexBoard.spanCells(space.key.widthWeight))
            assertEquals(
                "step $step: the space is centred",
                board.boardWidth / 2f,
                space.centerX - board.keys.minOf { it.left },
                0.01f,
            )

            // And the rows are still one pitch apart, at the ratio this step was drawn at.
            val pitch = HexBoard.cellHeight(board.cellWidth, board.sizing)
            board.keys.forEach { key ->
                assertEquals("step $step: ${key.key.label.ifEmpty { key.key.type.name }} pitch", pitch, key.height, 0.0001f)
            }
        }
    }

    @Test
    fun `landscape stays split, with both spaces and a live channel, at every step`() {
        steps.forEach { step ->
            val board = landscapeBoard(step)
            val channel = board.channel ?: error("step $step: a split board has a channel")

            assertTrue("step $step is the split", board.split)
            assertTrue("step $step: the channel is positive", channel.width > 0f)
            assertTrue("step $step: the board's middle is in the channel", channel.contains(landscapeWidth / 2f))
            assertEquals("step $step: no side margin", 0f, board.leftInset, 0.01f)
            assertEquals("step $step: the board spans the viewport", landscapeWidth, board.boardWidth, 0.01f)

            val left = board.keys.filter { it.centerX < channel.start }
            val right = board.keys.filter { it.centerX > channel.end }
            assertEquals("step $step: the left half is anchored to the left edge", 0f, left.minOf { it.left }, 0.01f)
            assertEquals("step $step: the right half is anchored to the right edge", landscapeWidth, right.maxOf { it.right }, 0.01f)
            assertEquals("step $step left rows", listOf(5, 5, 5, 4), (0..3).map { row(left, it).size })
            assertEquals("step $step right rows", listOf(5, 4, 4, 4), (0..3).map { row(right, it).size })
            assertEquals(
                "step $step: the channel opens at the left inner edge",
                left.maxOf { it.right },
                channel.start,
                0.01f,
            )
            assertEquals(
                "step $step: the channel opens at the right inner edge",
                right.minOf { it.left },
                channel.end,
                0.01f,
            )

            // Both space hexagons, one per thumb, one cell each — the split's two-space bottom row,
            // which the size ladder must not quietly fuse back into one bar.
            val allSpaces = spaces(board)
            assertEquals("step $step: two spaces", 2, allSpaces.size)
            allSpaces.forEach { space ->
                assertEquals(KeyType.SPACE, space.key.type)
                assertEquals("step $step: one cell", 1f, space.key.widthWeight, 0.0001f)
                assertEquals("step $step: one cell wide", board.cellWidth, space.width, 0.01f)
            }
            assertEquals("step $step: one space on the left", 1, left.count { it.key.type == KeyType.SPACE })
            assertEquals("step $step: one space on the right", 1, right.count { it.key.type == KeyType.SPACE })

            // No key crosses the corridor at any step — including the largest, where the halves reach
            // furthest in — and every row leaves the whole channel open.
            board.keys.forEach { key ->
                val label = key.key.label.ifEmpty { key.key.type.name }
                assertTrue(
                    "step $step: $label does not cross the channel",
                    key.right <= channel.start + 0.01f || key.left >= channel.end - 0.01f,
                )
                assertTrue(
                    "step $step: $label stays inside the viewport",
                    key.left >= -0.01f && key.right <= landscapeWidth + 0.01f,
                )
            }
            for (index in 0..3) {
                val keys = row(board.keys, index)
                val widestGap = keys.zipWithNext { a, b -> b.left - a.right }.maxOrNull() ?: 0f
                assertTrue(
                    "step $step row $index leaves the channel open: $widestGap >= ${channel.width}",
                    widestGap >= channel.width - 0.01f,
                )
            }

            // And the block is the capped share exactly: the cell follows the cap, which is what the
            // step multiplies — the board is never taller than the share it was given.
            assertEquals(
                "step $step: the block is the share",
                landscapeHeight * board.sizing.heightFraction,
                board.boardHeight,
                0.01f,
            )
        }
    }

    @Test
    fun `a stepped-up portrait board is clamped to a short screen instead of losing its columns`() {
        val shortFrame = 400f
        val step = KeyboardSize.MAX_STEP
        val sizing = portraitSizing(step)
        val natural = HexBoard.boardHeight(portraitWidth, sizing)
        assertTrue(
            "the stepped-up block really does overrun this frame: $natural > $shortFrame",
            natural > shortFrame,
        )

        val board = HexBoard.place(Layouts.HexQwertyEn, portraitWidth, shortFrame, sizing)

        // The columns keep the whole width and the board is drawn at full scale: that is the promise
        // a bigger portrait step must not break. (Before the clamp a short frame *shrank* the board —
        // 0.8 scale, an 86.4 px cell and margins either side — which gave away the width instead.)
        assertEquals("the cell is still the width's", 108f, board.cellWidth, 0.01f)
        assertEquals(0f, board.leftInset, 0.01f)
        assertEquals(1f, board.scale, 0.0001f)
        assertTrue(board.spansViewport)

        // And the hexagon came down to exactly what fits: block = 3.25 x ratio x cell = the frame.
        assertEquals("the block fits the frame", shortFrame, board.boardHeight, 0.01f)
        assertEquals(
            "the drawn ratio is the one the frame allows",
            shortFrame / (HexBoard.BLOCK_CAP_FACTOR * board.cellWidth),
            board.sizing.heightRatio,
            0.0001f,
        )
        assertTrue(
            "the clamp is a ceiling, never a raise: ${board.sizing.heightRatio} < ${sizing.heightRatio}",
            board.sizing.heightRatio < sizing.heightRatio,
        )
        // The drawn caps keep the ratio the placement reports, so the honeycomb stays a honeycomb.
        assertEquals(
            "the drawn cap keeps the clamped ratio",
            board.sizing.heightRatio,
            HexBoard.capHeight(HexBoard.cellHeight(board.cellWidth, board.sizing)) / board.cellWidth,
            0.0001f,
        )
        board.keys.forEach { key ->
            val cap = KeyGeometry.hexShapeFor(key)
            assertTrue("${key.key.label.ifEmpty { key.key.type.name }} is below the top edge", cap.top >= -0.1f)
            assertTrue("${key.key.label.ifEmpty { key.key.type.name }} is inside the frame", cap.bottom <= shortFrame + 0.1f)
        }
        // It is still the untouched portrait grid, only a flatter hexagon.
        assertEquals(listOf(10, 9, 9, 7), (0..3).map { row(board.keys, it).size })

        // A frame with room for it changes nothing at all — the clamp is only ever a response to
        // height that is not there.
        val roomy = HexBoard.place(Layouts.HexQwertyEn, portraitWidth, natural, sizing)
        assertEquals(sizing.heightRatio, roomy.sizing.heightRatio, 0f)
        assertEquals(natural, roomy.boardHeight, 0.01f)

        // And the split is never clamped: its display-height share *is* the bound, so there is no
        // ratio for a frame to take away. Even a frame of ten pixels leaves the sizing alone.
        assertEquals(landscapeSizing(step), HexBoard.fittedSizing(landscapeSizing(step), landscapeWidth, 10f))
    }

    @Test
    fun `a step outside the ladder is clamped rather than accepted`() {
        assertEquals(KeyboardSize.MIN_STEP, KeyboardSize.clampStep(-9))
        assertEquals(KeyboardSize.MAX_STEP, KeyboardSize.clampStep(9))
        assertEquals(KeyboardSize.DEFAULT_STEP, KeyboardSize.clampStep(KeyboardSize.DEFAULT_STEP))

        // The multiplier is the ladder's arithmetic, so an out-of-range step derives the board of the
        // stop it clamps to — the same numbers, not merely similar ones.
        assertEquals(HexBoard.sizeMultiplier(KeyboardSize.MIN_STEP), HexBoard.sizeMultiplier(-9), 0f)
        assertEquals(HexBoard.sizeMultiplier(KeyboardSize.MAX_STEP), HexBoard.sizeMultiplier(7), 0f)
        assertTrue(HexBoard.sizeMultiplier(99) <= HexBoard.sizeMultiplier(KeyboardSize.MAX_STEP))
        assertTrue(HexBoard.sizeMultiplier(-99) >= HexBoard.sizeMultiplier(KeyboardSize.MIN_STEP))

        assertEquals(portraitSizing(KeyboardSize.MIN_STEP), portraitSizing(-100))
        assertEquals(landscapeSizing(KeyboardSize.MAX_STEP), landscapeSizing(100))
        assertEquals(
            portraitBoard(KeyboardSize.MAX_STEP).boardHeight,
            portraitBoard(3).boardHeight,
            0f,
        )
        assertEquals(
            landscapeBoard(KeyboardSize.MIN_STEP).cellWidth,
            landscapeBoard(-3).cellWidth,
            0f,
        )
    }
}
