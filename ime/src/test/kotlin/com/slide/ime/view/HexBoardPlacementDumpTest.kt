package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.KeyboardLayout
import com.slide.core.layout.Layouts
import com.slide.core.settings.KeyboardSize
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board as it actually is, written out as numbers and checked.
 *
 * A preview picture that drifts from [HexBoard.place] is a picture that lies, so the picture is
 * drawn from what this test writes rather than from anyone's memory of the board. Two things are
 * pinned here because both were read off a stale preview and doubted:
 *
 *  - portrait's space bar is ONE key spanning THREE cells ([Layouts.HexQwertyEn]'s bottom row), and
 *    it is the only key that spans more than one, so the board is ten columns on every row;
 *  - landscape's two-handed split hands that same three-cell bar ONE SINGLE CELL TO EACH HALF, so
 *    two single-cell space hexagons type a space, one at the inner end of each hand, and no half
 *    carries a deformed key.
 *
 * The JSON lands under `build/`, which is not tracked: it is a fixture for a renderer, not a
 * checked-in expectation. Nothing here needs a device.
 */
class HexBoardPlacementDumpTest {

    /** One screen the keyboard is drawn on, and the height the view hands the board. */
    private data class Screen(
        val name: String,
        val orientation: HexBoard.Orientation,
        val width: Float,
        val available: Float,
        val layout: KeyboardLayout = Layouts.HexQwertyEn,
    )

    private val portrait = Screen("portrait", HexBoard.Orientation.PORTRAIT, 1080f, 2000f)
    private val landscape = Screen("landscape", HexBoard.Orientation.LANDSCAPE, 2400f, 0f)

    /**
     * The two boards a browser's address bar and an email field ask for, on portrait's own screen.
     *
     * They exist because those modes used to drop to the stock keyboard's rectangles; the picture is
     * the check that they are the same honeycomb with a delimiter in it, not a lookalike.
     */
    private val portraitEmail = portrait.copy(name = "portrait-email", layout = Layouts.HexEmailEn)
    private val portraitUri = portrait.copy(name = "portrait-uri", layout = Layouts.HexUriEn)

    /**
     * The `?123` page, in both orientations.
     *
     * It is a fixture of its own because the symbols pages are the newest two boards and the ones
     * whose shape is most easily taken for granted: the first report of the honeycomb disappearing
     * was here, and a page that is hex-shaped but has a row one cell too long would push itself off
     * the board rather than looking wrong.
     */
    private val portraitSymbols = portrait.copy(name = "portrait-symbols", layout = Layouts.HexSymbolsEn)
    private val landscapeSymbols =
        landscape.copy(name = "landscape-symbols", layout = Layouts.HexSymbolsEn)

    /**
     * What the view hands the board in landscape: the display is 1080 px tall and the board may take
     * no more of it than [HexBoard.LANDSCAPE_HEIGHT_FRACTION], which is the cap the *view* is measured
     * to. Passing the whole display height here instead was the bug this test was born from — with
     * 1080 px to spend the board fills the width, the cell goes to 228.6 px, and the channel between
     * the halves collapses to nothing.
     */
    private fun available(screen: Screen, step: Int): Float = when (screen.orientation) {
        HexBoard.Orientation.LANDSCAPE -> HexBoard.heightCap(1080f, HexBoard.sizing(screen.orientation, step))
        HexBoard.Orientation.PORTRAIT -> screen.available
    }

    private fun place(screen: Screen, step: Int) = HexBoard.place(
        layout = screen.layout,
        viewportWidth = screen.width,
        availableHeight = available(screen, step),
        sizing = HexBoard.sizing(screen.orientation, step),
    )

    @Test
    fun `portrait's space bar is one key over three cells and nothing else spans a cell`() {
        val placement = place(portrait, KeyboardSize.DEFAULT_STEP)
        val spaces = placement.keys.filter { it.key.type == KeyType.SPACE }
        assertEquals("one space key", 1, spaces.size)
        assertEquals("over three cells", 3, HexBoard.spanCells(spaces.single().key.widthWeight))
        assertTrue(
            "every other key is one cell",
            placement.keys.filter { it.key.type != KeyType.SPACE }
                .all { HexBoard.spanCells(it.key.widthWeight) == 1 },
        )
        assertNull("no channel on the single board", placement.channel)
        assertEquals("ten columns wide", 10f * placement.cellWidth, placement.boardWidth, 0.01f)
    }

    @Test
    fun `the landscape split hands the space bar two cells to each half`() {
        val halves = HexBoard.splitHalves(Layouts.HexQwertyEn)
        val leftSpaces = halves.left.rows.flatMap { it.keys }.filter { it.type == KeyType.SPACE }
        val rightSpaces = halves.right.rows.flatMap { it.keys }.filter { it.type == KeyType.SPACE }
        assertEquals("one space cell on the left", 1, leftSpaces.size)
        assertEquals("one space cell on the right", 1, rightSpaces.size)
        // Half the three-cell space to each hand, rounded up, so the key both thumbs use is two
        // joined hexagons per side rather than a single one.
        assertEquals(2f, leftSpaces.single().widthWeight, 0f)
        assertEquals(2f, rightSpaces.single().widthWeight, 0f)

        val placement = place(landscape, KeyboardSize.DEFAULT_STEP)
        assertEquals("two space hexagons are drawn", 2, placement.keys.filter { it.key.type == KeyType.SPACE }.size)
        assertNotNull("the split leaves a channel", placement.channel)
        assertTrue("the channel is open", placement.channel!!.width > 0f)
    }

    @Test
    fun `every stop of the size ladder is written out for the renderer`() {
        val out = StringBuilder("[\n")
        val cases = ArrayList<Pair<Screen, Int>>()
        for (step in -2..2) {
            cases += portrait to step
            cases += landscape to step
        }
        // The address boards once each at the default step: enough to draw them beside portrait.
        cases += portraitEmail to KeyboardSize.DEFAULT_STEP
        cases += portraitUri to KeyboardSize.DEFAULT_STEP
        cases += portraitSymbols to KeyboardSize.DEFAULT_STEP
        cases += landscapeSymbols to KeyboardSize.DEFAULT_STEP
        cases.forEachIndexed { index, (screen, step) ->
            val placement = place(screen, step)
            out.append("  {\"screen\":\"${screen.name}\",\"step\":$step,")
            out.append("\"viewportWidth\":${screen.width},\"viewportHeight\":${screen.available},")
            out.append("\"cellWidth\":${placement.cellWidth},\"boardWidth\":${placement.boardWidth},")
            out.append("\"boardHeight\":${placement.boardHeight},\"leftInset\":${placement.leftInset},")
            out.append("\"heightRatio\":${placement.heightRatio},")
            out.append("\"channelStart\":${placement.channel?.start ?: -1f},")
            out.append("\"channelEnd\":${placement.channel?.end ?: -1f},\"keys\":[")
            placement.keys.forEachIndexed { keyIndex, placed ->
                if (keyIndex > 0) out.append(",")
                // A label can contain a backslash (the second symbols page types one), and the
                // fixture is read back as JSON, so both characters that mean something to a parser
                // are neutralised here rather than trusted not to appear.
                val label = placed.key.label.replace("\\", "\\\\").replace("\"", "'")
                out.append("{\"label\":\"$label\",")
                out.append("\"type\":\"${placed.key.type}\",")
                out.append("\"span\":${HexBoard.spanCells(placed.key.widthWeight)},")
                out.append("\"row\":${placed.row},")
                out.append("\"left\":${placed.left},\"top\":${placed.top},")
                out.append("\"width\":${placed.width},\"height\":${placed.height}}")
            }
            out.append("]}")
            out.append(if (index == cases.lastIndex) "\n" else ",\n")
        }
        out.append("]\n")
        val file = File("build/hexboard-placement.json")
        file.parentFile?.mkdirs()
        file.writeText(out.toString())
        assertTrue("the fixture was written", file.length() > 1000L)
    }

    @Test
    fun `the address boards place on the same ten columns, delimiter and all`() {
        for (screen in listOf(portraitEmail, portraitUri)) {
            val placement = place(screen, KeyboardSize.DEFAULT_STEP)
            assertEquals(
                "${screen.name}: ten columns",
                10f * placement.cellWidth,
                placement.boardWidth,
                0.01f,
            )
            assertEquals("${screen.name}: four rows", 4, placement.keys.map { it.row }.distinct().size)
            assertEquals(
                "${screen.name}: one space key, over three cells",
                1,
                placement.keys.count { it.key.type == KeyType.SPACE },
            )
            assertNull("${screen.name}: one board, no channel", placement.channel)
            assertTrue(
                "${screen.name}: the delimiter is on the bottom row",
                placement.keys.any { it.row == 3 && it.key.outputText in setOf("@", "/") },
            )
        }
    }

    @Test
    fun `the symbols page is a honeycomb in both orientations, split included`() {
        // The page a person meets when they go looking for a number. If its rows are not the letters
        // board's cell counts the honeycomb is not merely ugly here, it is off the board, so the
        // counts and the split are both checked rather than the page being assumed from the letters
        // board's tests.
        val upright = place(portraitSymbols, KeyboardSize.DEFAULT_STEP)
        assertEquals(
            "portrait symbols: ten columns",
            10f * upright.cellWidth,
            upright.boardWidth,
            0.01f,
        )
        assertEquals("portrait symbols: four rows", 4, upright.keys.map { it.row }.distinct().size)
        assertNull("portrait symbols: one board, no channel", upright.channel)
        assertEquals(
            "portrait symbols: one space key, over three cells",
            1,
            upright.keys.count { it.key.type == KeyType.SPACE },
        )

        val wide = place(landscapeSymbols, KeyboardSize.DEFAULT_STEP)
        assertEquals(
            "landscape symbols: one space hexagon per thumb",
            2,
            wide.keys.count { it.key.type == KeyType.SPACE },
        )
        assertNotNull("landscape symbols: the halves are held apart", wide.channel)
        assertTrue(
            "landscape symbols: every key is one cell, except the space, which is two",
            wide.keys.all {
                it.width <= wide.cellWidth + 0.01f ||
                    (it.key.type == KeyType.SPACE && it.width <= 2f * wide.cellWidth + 0.01f)
            },
        )
    }

    @Test
    fun `landscape gives each thumb a two-hexagon space bar, with a channel between`() {
        // His ask in his words: another hexagon on each side of the space bar, to give it more room.
        // The split hands a key that straddles the cut half its cells to each hand, rounded up, so the
        // three-cell space arrives here as two cells per thumb — two joined hexagons — and neither
        // thumb gets the smaller key. The halves reach half a cell further inward for it, and that
        // comes out of the channel rather than the cell, because the split's cell width is decided by
        // the height. The first report that produced this test was that landscape had no second space
        // hexagon at all; a claim about the board is settled by the board.
        val placement = place(landscape, KeyboardSize.DEFAULT_STEP)
        val spaces = placement.keys.filter { it.key.type == KeyType.SPACE }
        assertEquals("one space key per half", 2, spaces.size)
        for (space in spaces) {
            assertEquals(
                "each space is two cells of joined hexagons, not one cell and not a fused bar",
                2f * placement.cellWidth,
                space.width,
                0.01f,
            )
        }
        val left = spaces.minBy { it.centerX }
        val right = spaces.maxBy { it.centerX }
        assertTrue(
            "the left thumb's space sits in the left half",
            left.centerX < placement.boardWidth / 2f,
        )
        assertTrue(
            "the right thumb's space sits in the right half",
            right.centerX > placement.boardWidth / 2f,
        )
        assertNotNull("the two halves are held apart by a channel", placement.channel)
    }

    @Test
    fun `the portrait block keeps its shape when the frame is too short for it`() {
        // A stepped-up board in a frame that cannot hold it keeps ten columns and flattens its
        // hexagons rather than shrinking into a centred strip: the clamp is portrait's one seam.
        val natural = place(portrait, 2)
        val squeezed = HexBoard.place(
            layout = Layouts.HexQwertyEn,
            viewportWidth = portrait.width,
            availableHeight = natural.boardHeight * 0.75f,
            sizing = HexBoard.sizing(portrait.orientation, 2),
        )
        assertEquals("columns intact", 10f * natural.cellWidth, squeezed.boardWidth, 0.01f)
        assertTrue("hexagons flattened", squeezed.heightRatio < natural.heightRatio)
        assertTrue("no margins", squeezed.spansViewport)
    }
}
