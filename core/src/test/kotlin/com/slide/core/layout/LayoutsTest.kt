package com.slide.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    @Test
    fun `every board names its own language on the space bar`() {
        // The space bar is the one place the keyboard says which language it is: KeyboardView draws the
        // board's own label there and nothing else. So a board copied from another language and stamped
        // only with its tag told the user they were typing the language it was copied from, in the single
        // place meant to answer that question. Asserted for every board and every language, and
        // negatively too: a board may not be named for a language it is not, which is the half that
        // would actually have caught this.
        for (entry in KeyboardLanguages.SWITCHABLE) {
            val boards = Layouts.boardsFor(entry.language)
            val all = listOf(
                "qwerty" to boards.qwerty,
                "hex" to boards.hex,
                "email" to boards.email,
                "uri" to boards.uri,
            )
            for ((kind, board) in all) {
                assertTrue(
                    "${entry.language} $kind is labelled '${board.label}'",
                    board.label.startsWith(entry.label),
                )
                for (other in KeyboardLanguages.SWITCHABLE) {
                    if (other.language == entry.language) continue
                    assertFalse(
                        "${entry.language} $kind is labelled '${board.label}', another language's name",
                        board.label.startsWith(other.label),
                    )
                }
            }
        }
    }

    @Test
    fun `number row is limited to compatible alphabetic layouts`() {
        assertEquals(Layouts.QwertyEn.rows.size + 1, Layouts.withNumberRow(Layouts.QwertyEn, true).rows.size)
        assertEquals(Layouts.EmailEn.rows.size + 1, Layouts.withNumberRow(Layouts.EmailEn, true).rows.size)
        assertEquals(Layouts.UriEn.rows.size + 1, Layouts.withNumberRow(Layouts.UriEn, true).rows.size)
        assertSame(Layouts.SymbolsEn, Layouts.withNumberRow(Layouts.SymbolsEn, true))
        assertSame(Layouts.SymbolsAltEn, Layouts.withNumberRow(Layouts.SymbolsAltEn, true))
        assertSame(Layouts.NumberPad, Layouts.withNumberRow(Layouts.NumberPad, true))
    }

    @Test
    fun `specialized text layouts expose their primary delimiters`() {
        assertTrue(outputs(Layouts.EmailEn).containsAll(listOf("@", ".", ".com")))
        assertTrue(outputs(Layouts.UriEn).containsAll(listOf("/", ".", ".com")))
        assertTrue(Layouts.EmailEn.supportsNumberRow)
        assertTrue(Layouts.UriEn.supportsNumberRow)
    }

    @Test
    fun `every numeric pad retains delete and editor action keys`() {
        for (layout in numericLayouts()) {
            val types = layout.rows.flatMap { row -> row.keys.map(Key::type) }
            assertTrue("${layout.id} lacks Delete", KeyType.DELETE in types)
            assertTrue("${layout.id} lacks Enter", KeyType.ENTER in types)
            assertFalse(layout.supportsNumberRow)
        }
    }

    @Test
    fun `numeric affordances match signed decimal and phone modes`() {
        assertFalse("-" in outputs(Layouts.NumberPad))
        assertFalse("." in outputs(Layouts.NumberPad))
        assertTrue("-" in outputs(Layouts.SignedNumberPad))
        assertTrue("." in outputs(Layouts.DecimalPad))
        assertTrue(outputs(Layouts.SignedDecimalPad).containsAll(listOf("-", ".")))
        assertTrue(outputs(Layouts.PhonePad).containsAll(listOf("*", "#")))
        val phoneZero = Layouts.PhonePad.rows.flatMap(KeyRow::keys).first { it.outputText == "0" }
        assertTrue("+" in phoneZero.alternates)
    }

    @Test
    fun `date and time pads expose required separators and meridiems`() {
        assertTrue(outputs(Layouts.DatePad).containsAll(listOf("/", "-", ".")))
        assertTrue(outputs(Layouts.TimePad).containsAll(listOf(":", ".", "AM", "PM")))
        assertTrue(
            outputs(Layouts.DateTimePad).containsAll(listOf("/", "-", ":", ".", "AM", "PM")),
        )
    }

    private fun outputs(layout: KeyboardLayout): Set<String> =
        layout.rows.flatMap(KeyRow::keys).map(Key::outputText).toSet()

    private fun numericLayouts() = listOf(
        Layouts.NumberPad,
        Layouts.SignedNumberPad,
        Layouts.DecimalPad,
        Layouts.SignedDecimalPad,
        Layouts.PhonePad,
        Layouts.DatePad,
        Layouts.TimePad,
        Layouts.DateTimePad,
    )
}
