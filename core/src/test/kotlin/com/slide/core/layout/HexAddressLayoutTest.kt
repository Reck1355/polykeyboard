package com.slide.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A browser's address bar and an email field are the two modes the outside world asks for most, and
 * they used to be the two that asked for a different keyboard.
 *
 * First the stock board took over there and the honeycomb disappeared. Then the honeycomb came back
 * with an address row of its own - `?123`, `@` or `/`, a four-cell space, the period, `.com`, enter -
 * which was worse in the way that is hardest to notice from a desk: it was a *second* keyboard, with
 * no backspace on it at all and every key a different distance from his thumb, so the muscle memory
 * he had just built on the letters board was wrong one field over. He reported it plainly: *my
 * keyboard is also different when I'm in the browser*.
 *
 * What is pinned here is that there is now one board. The modes still have their own ids, and a
 * field still names the mode it wants; they all resolve to the letters board, row for row, the same
 * row objects rather than copies. The keys the address row carried are still reachable - `@` and `/`
 * are on the `?123` page - so nothing was given up for it but the surprise.
 */
class HexAddressLayoutTest {

    private val englishAddress = listOf(Layouts.HexEmailEn, Layouts.HexUriEn)
    private val spanishAddress = listOf(Layouts.HexEmailEs, Layouts.HexUriEs)

    @Test
    fun `no board a field can ask for is a rectangle`() {
        for (tag in listOf(KeyboardLanguages.FALLBACK, KeyboardLanguages.SPANISH)) {
            val boards = Layouts.boardsFor(tag)
            assertTrue("$tag letters", boards.hex.hexKeys)
            assertTrue("$tag email", boards.email.hexKeys)
            assertTrue("$tag uri", boards.uri.hexKeys)
            assertFalse("the stock board still exists for the classic path", boards.qwerty.hexKeys)
        }
    }

    @Test
    fun `the address boards are wired by language, not by accident`() {
        assertSame(Layouts.HexEmailEn, Layouts.boardsFor(KeyboardLanguages.FALLBACK).email)
        assertSame(Layouts.HexUriEn, Layouts.boardsFor(KeyboardLanguages.FALLBACK).uri)
        assertSame(Layouts.HexEmailEs, Layouts.boardsFor(KeyboardLanguages.SPANISH).email)
        assertSame(Layouts.HexUriEs, Layouts.boardsFor(KeyboardLanguages.SPANISH).uri)
        assertEquals(Layouts.ID_HEX_EMAIL, Layouts.HexEmailEn.id)
        assertEquals(Layouts.ID_HEX_URI, Layouts.HexUriEn.id)
        assertEquals(Layouts.ID_HEX_EMAIL_ES, Layouts.HexEmailEs.id)
        assertEquals(Layouts.ID_HEX_URI_ES, Layouts.HexUriEs.id)
    }

    @Test
    fun `the board a browser gets is the letters board, row for row`() {
        for (board in englishAddress) {
            assertEquals(
                "${board.id} is not four rows tall",
                Layouts.HexQwertyEn.rows.size,
                board.rows.size,
            )
            for ((index, row) in board.rows.withIndex()) {
                assertSame(
                    "row $index of ${board.id} is HexQwertyEn's own row, not a copy",
                    Layouts.HexQwertyEn.rows[index],
                    row,
                )
            }
        }
        for (board in spanishAddress) {
            assertEquals(Layouts.HexQwertyEs.rows.size, board.rows.size)
            for ((index, row) in board.rows.withIndex()) {
                assertSame(
                    "row $index of ${board.id} is HexQwertyEs's own row, not a copy",
                    Layouts.HexQwertyEs.rows[index],
                    row,
                )
            }
        }
    }

    @Test
    fun `every key of a browser field is where the letters board puts it`() {
        // The whole point of the change, stated as the property rather than as a key list: for each
        // mode the outside world asks for, key n of row m is the same key in the same cell with the
        // same width as it is on the letters board. A backspace that exists on one board and not the
        // other fails this, and so does a space bar that is three cells in one field and four in the
        // next - both of which the address row did.
        val modes = listOf(
            Layouts.HexEmailEn to Layouts.HexQwertyEn,
            Layouts.HexUriEn to Layouts.HexQwertyEn,
            Layouts.HexEmailEs to Layouts.HexQwertyEs,
            Layouts.HexUriEs to Layouts.HexQwertyEs,
        )
        for ((mode, letters) in modes) {
            for ((rowIndex, row) in mode.rows.withIndex()) {
                val lettersRow = letters.rows[rowIndex]
                assertEquals("${mode.id}: row $rowIndex key count", lettersRow.keys.size, row.keys.size)
                assertEquals("${mode.id}: row $rowIndex leading gap", lettersRow.leadingGap, row.leadingGap, 0f)
                assertEquals("${mode.id}: row $rowIndex trailing gap", lettersRow.trailingGap, row.trailingGap, 0f)
                for ((keyIndex, key) in row.keys.withIndex()) {
                    val lettersKey = lettersRow.keys[keyIndex]
                    assertEquals("${mode.id}: row $rowIndex key $keyIndex", lettersKey, key)
                }
            }
            assertEquals(
                "${mode.id}: one backspace, like the letters board",
                1,
                mode.rows.flatMap { it.keys }.count { it.type == KeyType.DELETE },
            )
            assertEquals(
                "${mode.id}: one space bar, over the letters board's three cells",
                1,
                mode.rows.flatMap { it.keys }.filter { it.type == KeyType.SPACE }.size,
            )
            assertEquals(
                "${mode.id}: the space bar is the letters board's width",
                letters.rows.flatMap { it.keys }.first { it.type == KeyType.SPACE }.widthWeight,
                mode.rows.flatMap { it.keys }.first { it.type == KeyType.SPACE }.widthWeight,
                0f,
            )
        }
    }

    @Test
    fun `the delimiters an address row used to carry are on the symbols page`() {
        // Nothing was given up for the single board but the surprise: `@` and `/` are one tap away
        // on the `?123` page, which every board opens, browser and email field included.
        for (pages in listOf(
            Layouts.pagesFor(hexKeys = true),
            Layouts.pagesFor(hexKeys = false),
        )) {
            val symbols = pages.symbols.rows.flatMap { it.keys }.map { it.outputText }
            assertTrue("the symbols page carries @", "@" in symbols)
            assertTrue("the symbols page carries /", "/" in symbols)
        }
    }

    @Test
    fun `the letters board keeps its comma and its period`() {
        // The address rows used to give the comma's cell up to the delimiter and the period's to
        // `.com`. Neither is given up now. The comma still opens the bottom row; the period ends the
        // z-row, which is where he moved it.
        val bottom = Layouts.HexQwertyEn.rows.last().keys.map { it.outputText }
        assertEquals("five keys, the comma first", 5, Layouts.HexQwertyEn.rows.last().keys.size)
        assertEquals(",", bottom.first())
        assertEquals(".", Layouts.HexQwertyEn.rows[2].keys.last().outputText)
    }
}
