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
        // The letters board's ladder, not one of the pages' own: ten, nine, eight, seven cells, each
        // row half a cell in from the one above and centred on the same midline the space bar sits
        // on. The pages used to be 10 / 9 / 9 / 9, which left the second row off centre and the third
        // one ragged against no edge in particular.
        val expectedCells = listOf(10, 9, 8, 7)
        for (page in listOf(Layouts.HexSymbolsEn, Layouts.HexSymbolsAltEn)) {
            assertTrue("${page.id} is a hex board", page.hexKeys)
            assertFalse("${page.id} has no number row to insert", page.supportsNumberRow)
            assertEquals(
                "${page.id}: four rows of ten, nine, eight and seven cells",
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
            // Three cells, the same as the letters board's: a page of this keyboard is a page of
            // this keyboard, and the space bar is the key the thumb finds without looking. He
            // reported it as *in symbols its space bar is more than three hexagons*.
            assertEquals("${page.id}: one wide key, the space bar over three cells", listOf(KeyType.SPACE to 3), wide)
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

    /**
     * Every character a key types, its alternates included.
     *
     * Two characters share a key where the row has one cell fewer than the characters it carries -
     * the quote with the apostrophe, the closing bracket with the opening one - so a page's
     * vocabulary is what its keys type *including* what a hold on them reaches.
     */
    private fun vocabularyOf(page: KeyboardLayout): Set<String> = page.rows
        .flatMap { it.keys }
        .flatMap { key -> listOf(key.outputText) + key.alternates }
        .toSet()

    @Test
    fun `the second hex page still carries the characters that are hard to find`() {
        val all = vocabularyOf(Layouts.HexSymbolsAltEn)
        for (character in listOf("~", "|", "=", "{", "}", "\\", "£", "€", "[", "]", "<", ">", "%")) {
            assertTrue("the second hex page types $character", character in all)
        }
    }

    @Test
    fun `every character the stock first page types is still typeable on the two hex pages`() {
        // The honeycomb's rows are narrower than the stock page's, so characters move between the two
        // pages and some share a key. A symbol quietly vanishing to make a layout fit is the kind of
        // change nobody notices until they need it, so this reads the two hex pages - one or the
        // other, on a key or on a hold - and requires every character the stock page leads with to be
        // in there. The reverse is not required: the hex pages carry more than the stock page does.
        val reachable = vocabularyOf(Layouts.HexSymbolsEn) + vocabularyOf(Layouts.HexSymbolsAltEn)
        val stockFirst = Layouts.SymbolsEn.rows
            .flatMap { it.keys }
            .flatMap { key -> listOf(key.outputText) + key.alternates }
            .filter { it.isNotEmpty() }
            .toSet()
        val missing = stockFirst - reachable
        assertEquals("characters the stock page types and the hex pages do not: $missing", emptySet<String>(), missing)
    }
}
