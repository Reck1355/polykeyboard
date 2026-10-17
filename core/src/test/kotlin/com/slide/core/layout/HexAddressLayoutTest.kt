package com.slide.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A browser's address bar and an email field are the two modes the outside world asks for most, and
 * they were the two the honeycomb did not cover: the editor mode chose the stock keyboard's
 * rectangular address boards and the hex board disappeared exactly there.
 *
 * These boards put the delimiter on the honeycomb's own bottom row instead. Two things are pinned
 * here, and the first is the one that fixes the report: **every board the table hands out for a
 * text-ish mode is a hex board**, so the mode a field asks for can no longer change the keyboard's
 * shape. The second is the shape itself: four rows of 10 / 9 / 7 / 6 keys with the same half-cell
 * stagger, a space bar, and one cell per key otherwise, which is the ten-column board the size
 * ladder and the landscape split are all measured against. The cells each row covers are what those
 * two are measured against and those are unchanged: caps and backspace took the cells the z-row's
 * ends gave up, and space took the two the globe and the emoji key left behind.
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
    fun `each address board is the same honeycomb with a delimiter in it`() {
        for (board in englishAddress + spanishAddress) {
            val counts = board.rows.map { it.keys.size }
            assertEquals("${board.id}: four rows of 10 / 9 / 8 / 6", listOf(10, 9, 8, 6), counts)
            // The board as authored is four rows. A digit row is added on top by the setting, not baked
            // in, and these boards now accept one: a number row over an email address is worth having, and
            // the honeycomb's own row is what arrives.
            assertTrue("${board.id}: accepts a digit row", board.supportsNumberRow)
            val spaces = board.rows.flatMap { it.keys }.filter { it.type == KeyType.SPACE }
            assertEquals("${board.id}: one space bar", 1, spaces.size)
            // Four, not three: the row still covers nine cells with the globe and the emoji key
            // gone, and the space bar is where the slack went.
            assertEquals("${board.id}: over four cells", 4f, spaces.single().widthWeight, 0f)
            assertEquals("${board.id}: every key but space is one cell",
                1f, board.rows.flatMap { it.keys }.filter { it.type != KeyType.SPACE }
                    .maxOf { it.widthWeight }, 0f)
            val bottom = board.rows.last()
            assertEquals("${board.id}: the staggered bottom row", 0.5f, bottom.leadingGap, 0f)
            assertEquals("${board.id}: nine cells and two half gaps make ten", 10f,
                1f + bottom.keys.sumOf { it.widthWeight.toDouble() }.toFloat(), 0f)
        }
    }

    @Test
    fun `email carries at and uri carries slash, and both carry dot com`() {
        val emailOutputs = Layouts.HexEmailEn.rows.last().keys.map { it.outputText }
        val uriOutputs = Layouts.HexUriEn.rows.last().keys.map { it.outputText }
        assertTrue(emailOutputs.contains("@"))
        assertFalse("the email row is not the URL row", emailOutputs.contains("/"))
        assertTrue(uriOutputs.contains("/"))
        assertFalse("the URL row is not the email row", uriOutputs.contains("@"))
        for (outputs in listOf(emailOutputs, uriOutputs)) {
            assertTrue(outputs.contains(".com"))
            assertTrue(outputs.contains("."))
        }
        for (board in listOf(Layouts.HexEmailEn, Layouts.HexUriEn)) {
            assertTrue(
                "${board.id} keeps the way into the symbols page",
                board.rows.last().keys.any { it.type == KeyType.SYMBOLS },
            )
            // Not on the board any more. The globe and the emoji key are buttons on the strip
            // above it, which is the one place they are reachable from every page.
            assertFalse(
                "${board.id} still carries a strip button",
                board.rows.flatMap { it.keys }
                    .any { it.type == KeyType.LANGUAGE || it.type == KeyType.EMOJI },
            )
        }
    }

    @Test
    fun `the letters rows are the letters board's own rows, shared rather than copied`() {
        for (board in englishAddress) {
            assertEquals(3, board.rows.dropLast(1).size)
            for (index in 0..2) {
                assertSame(
                    "row $index of ${board.id} is HexQwertyEn's own row",
                    Layouts.HexQwertyEn.rows[index],
                    board.rows[index],
                )
            }
        }
        for (board in spanishAddress) {
            for (index in 0..2) {
                assertSame(
                    "row $index of ${board.id} is HexQwertyEs's own row",
                    Layouts.HexQwertyEs.rows[index],
                    board.rows[index],
                )
            }
        }
    }

    @Test
    fun `the letters board keeps its comma and its period`() {
        // The address rows give the comma's cell up to the delimiter and the period's to `.com`. The
        // letters board must not have given either up with them: they are the row's two ends, and
        // the enter and the backspace sit between them and the space bar.
        val bottom = Layouts.HexQwertyEn.rows.last().keys.map { it.outputText }
        assertEquals("five keys, the comma first and the period last", 5, Layouts.HexQwertyEn.rows.last().keys.size)
        assertEquals(",", bottom.first())
        assertEquals(".", bottom.last())
    }

    @Test
    fun `Spanish and English address boards differ only in their letters`() {
        assertSame(
            "one delimiter row serves both languages",
            Layouts.HexEmailEn.rows.last(),
            Layouts.HexEmailEs.rows.last(),
        )
        assertSame(Layouts.HexUriEn.rows.last(), Layouts.HexUriEs.rows.last())
        assertNotEquals(
            "the two modes are not the same board",
            Layouts.HexEmailEn.id,
            Layouts.HexUriEn.id,
        )
    }
}
