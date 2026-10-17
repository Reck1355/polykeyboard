package com.slide.core.layout

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The symbols pages follow the *shape* of the board on screen.
 *
 * This is the regression the honeycomb met twice: first in a browser's address bar and an email
 * field, and then at `?123`. Both times a fixed stock board was handed to a shaped one, so the
 * shape the user had chosen disappeared at exactly the place they went looking for something — a
 * number, a slash, an `@`.
 *
 * The pages themselves are checked here as well as the choice, because a page that is hex-shaped but
 * carries a wide key would break the lattice it is drawn on rather than quietly look wrong.
 */
class SymbolPagesTest {

    @Test
    fun `a hex board gets the hex pages, a stock board the stock ones`() {
        assertSame(Layouts.HexSymbolsEn, Layouts.pagesFor(hexKeys = true).symbols)
        assertSame(Layouts.HexSymbolsAltEn, Layouts.pagesFor(hexKeys = true).symbolsAlt)
        assertSame(Layouts.SymbolsEn, Layouts.pagesFor(hexKeys = false).symbols)
        assertSame(Layouts.SymbolsAltEn, Layouts.pagesFor(hexKeys = false).symbolsAlt)
    }

    @Test
    fun `both hex pages are honeycombs on the letters board's own cell counts`() {
        val expectedCells = listOf(10, 9, 9, 9)
        for (page in listOf(Layouts.HexSymbolsEn, Layouts.HexSymbolsAltEn)) {
            assertTrue("${page.id} is a hex board", page.hexKeys)
            assertFalse("${page.id} has no number row to insert", page.supportsNumberRow)
            assertEquals(
                "${page.id}: four rows of ten, ten, nine and nine cells",
                expectedCells,
                page.rows.map { row -> row.keys.sumOf { it.widthWeight.roundToInt() } },
            )
        }
    }

    @Test
    fun `the only key wider than one cell on either hex page is the space bar`() {
        for (page in listOf(Layouts.HexSymbolsEn, Layouts.HexSymbolsAltEn)) {
            val wide = page.rows
                .flatMap { it.keys }
                .filter { it.widthWeight != 1f }
                .map { it.type to it.widthWeight.roundToInt() }
            // Five cells, not three: the globe and the emoji key left the row for the strip, and
            // the page still covers its nine, so the space bar carries what they gave up.
            assertEquals("${page.id}: one wide key, the space bar", listOf(KeyType.SPACE to 5), wide)
        }
    }

    @Test
    fun `every hex page gets back to letters, and the first reaches the second`() {
        for (page in listOf(Layouts.HexSymbolsEn, Layouts.HexSymbolsAltEn)) {
            assertTrue(
                "${page.id}: letters are reachable",
                page.rows.flatMap { it.keys }.any { it.type == KeyType.ALPHA },
            )
        }
        assertTrue(
            "the second page is reachable from the first",
            Layouts.HexSymbolsEn.rows.flatMap { it.keys }.any { it.type == KeyType.SYMBOLS_ALT },
        )
        assertTrue(
            "the first page is reachable from the second",
            Layouts.HexSymbolsAltEn.rows.flatMap { it.keys }.any { it.type == KeyType.SYMBOLS },
        )
    }

    @Test
    fun `the first hex page carries the digits and the characters the stock page leads with`() {
        val typed = Layouts.HexSymbolsEn.rows.first().keys.map { it.outputText }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), typed)
        val all = Layouts.HexSymbolsEn.rows.flatMap { it.keys }.map { it.outputText }.toSet()
        for (character in listOf("@", "#", "$", "&", "-", "+", "(", ")", "/", "*", "?", "!")) {
            assertTrue("the first hex page types $character", character in all)
        }
    }

    @Test
    fun `the second hex page still carries the characters that are hard to find`() {
        val all = Layouts.HexSymbolsAltEn.rows.flatMap { it.keys }.map { it.outputText }.toSet()
        for (character in listOf("~", "|", "=", "{", "}", "\\", "£", "€", "[", "]", "<", ">", "%")) {
            assertTrue("the second hex page types $character", character in all)
        }
    }

    @Test
    fun `every character the first hex page gives up is still typed on the second`() {
        // The honeycomb's rows hold one cell fewer than the stock page's wide-key rows, so exactly
        // one character moves between them. It has to still exist somewhere: a symbol quietly
        // vanishing to make a layout fit is the kind of change nobody notices until they need it.
        val first = Layouts.HexSymbolsEn.rows.flatMap { it.keys }.map { it.outputText }.toSet()
        val second = Layouts.HexSymbolsAltEn.rows.flatMap { it.keys }.map { it.outputText }.toSet()
        val stockFirst = Layouts.SymbolsEn.rows.flatMap { it.keys }
            .map { it.outputText }.filter { it.isNotEmpty() }.toSet()
        val dropped = stockFirst - first
        assertTrue(
            "the first page gave up something: $dropped",
            dropped.isNotEmpty(),
        )
        for (character in dropped) {
            assertTrue(
                "$character left the first hex page and is not on the second either",
                character in second,
            )
        }
    }
}
