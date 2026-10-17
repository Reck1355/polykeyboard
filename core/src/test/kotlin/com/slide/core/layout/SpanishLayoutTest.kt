package com.slide.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spanish is meant to be English with the Spanish letters reachable, and nothing else about it
 * changed: same keys, same positions in the same rows, same widths and gaps. Two kinds of assertion
 * do that work here.
 *
 * The first is the English guard. This change touches only the popups of six English keys and adds
 * new boards beside them, so the English board's own letters are pinned to their exact values —
 * label, hint and popup, in order — for all 26. A test that only compared Spanish to English would
 * pass happily if both had been altered together.
 *
 * The second is the Spanish contract: the accented forms first in the popup of the letter they
 * belong to, ñ on the n key, and every other key and position untouched.
 */
class SpanishLayoutTest {

    /** Every English letter, by row, as `label` to the alternates its popup offers. */
    private val englishLetters: List<List<Pair<String, String>>> = listOf(
        listOf(
            "q" to "1%", "w" to "2\\", "e" to "3èéêëēėę|", "r" to "4=", "t" to "5[",
            "y" to "6ÿ]", "u" to "7ûüùúū<", "i" to "8îïíīįì>", "o" to "9ôöòóœøōõ{", "p" to "0}",
        ),
        listOf(
            "a" to "@àáâäæãåā", "s" to "#ßśš", "d" to "$", "f" to "-", "g" to "&",
            "h" to "_", "j" to "+", "k" to "(", "l" to ")ł",
        ),
        listOf("z" to "*žźż", "x" to "\"", "c" to "'çćč", "v" to ":", "b" to ";", "n" to "!ñń", "m" to "?"),
    )

    /** What the Spanish board's popups hold for the letters Spanish adds accents to. */
    private val spanishPopups = mapOf(
        "a" to listOf("á", "@", "à", "â", "ä", "æ", "ã", "å", "ā"),
        "e" to listOf("é", "3", "è", "ê", "ë", "ē", "ė", "ę", "|"),
        "i" to listOf("í", "8", "î", "ï", "ī", "į", "ì", ">"),
        "o" to listOf("ó", "9", "ô", "ö", "ò", "œ", "ø", "ō", "õ", "{"),
        "u" to listOf("ú", "ü", "7", "û", "ù", "ū", "<"),
        "n" to listOf("ñ", "!", "ń"),
    )

    // region English is untouched

    @Test
    fun `the English board still holds every letter where it was, popup and hint included`() {
        for ((rowIndex, expected) in englishLetters.withIndex()) {
            val actual = Layouts.QwertyEn.rows[rowIndex].keys.filter { it.type == KeyType.CHARACTER }
            assertEquals("row $rowIndex letters", expected.map { it.first }, actual.map { it.label })
            for ((index, pair) in expected.withIndex()) {
                val key = actual[index]
                assertEquals("${pair.first} popup", pair.second.toAlternates(), key.alternates)
                assertEquals("${pair.first} hint", pair.second.first().toString(), key.hint)
            }
        }
    }

    @Test
    fun `the hex board's letters are the English board's, unaccented`() {
        val letters = Layouts.HexQwertyEn.rows.flatMap { row -> row.keys.filter(::isLetterKey) }
        val english = Layouts.QwertyEn.rows.flatMap { row -> row.keys.filter(::isLetterKey) }
        assertEquals(english.map { it.label }, letters.map { it.label })
        assertEquals(english.map { it.alternates }, letters.map { it.alternates })
        // The approved board draws a bare hexagon on the two rows it draws as a honeycomb, and
        // leaves the third row's glyph hints alone. Spanish inherits both decisions untouched.
        for (row in 0..1) {
            for (key in Layouts.HexQwertyEn.rows[row].keys) {
                assertNull("row $row ${key.label}", key.hint)
            }
        }
        // The hex z-row is the classic row's letters, neither of its outer keys: caps and
        // backspace are on the bottom row now, on both boards.
        assertEquals(
            Layouts.QwertyEn.rows[2].keys.drop(1).dropLast(1).map { it.hint },
            Layouts.HexQwertyEn.rows[2].keys.map { it.hint },
        )
    }

    @Test
    fun `only the six Spanish letters differ between the English and Spanish boards`() {
        val english = letterKeys(Layouts.QwertyEn)
        val spanish = letterKeys(Layouts.QwertyEs)
        assertEquals(english.size, spanish.size)
        for ((index, key) in english.withIndex()) {
            val translated = spanish[index]
            assertEquals(key.label, translated.label)
            assertEquals(key.type, translated.type)
            assertEquals(key.widthWeight, translated.widthWeight, 0f)
            assertEquals(key.gestureEligible, translated.gestureEligible)
            if (key.label in spanishPopups) {
                assertNotEquals(key.label, key.alternates, translated.alternates)
            } else {
                assertEquals(key.label, key.alternates, translated.alternates)
            }
        }
    }

    // endregion

    // region Spanish letters are reachable

    @Test
    fun `the Spanish accented letters come first in the popup of the letter they belong to`() {
        val spanish = letterKeys(Layouts.QwertyEs)
        for ((letter, popup) in spanishPopups) {
            val key = spanish.single { it.label == letter }
            assertEquals("$letter popup", popup, key.alternates)
        }
    }

    @Test
    fun `every accented letter the Spanish language writes with is offered`() {
        val offered = letterKeys(Layouts.QwertyEs).flatMap { it.alternates }.toSet()
        for (letter in listOf("á", "é", "í", "ó", "ú", "ü", "ñ")) {
            assertTrue("$letter is not offered by any key", letter in offered)
        }
    }

    @Test
    fun `ñ is on the n key and nowhere else`() {
        val spanish = letterKeys(Layouts.QwertyEs)
        // One key offers it, that key is the n, and it is the first thing the key offers: the
        // Spanish board is still twenty-six keys wide.
        assertEquals(listOf("n"), spanish.filter { "ñ" in it.alternates }.map { it.label })
        assertEquals("ñ", spanish.single { it.label == "n" }.alternates.first())
        assertEquals(26, spanish.size)
    }

    @Test
    fun `the Spanish board is the hex board the input method draws, in the Spanish language`() {
        assertEquals(KeyboardLanguages.SPANISH, Layouts.HexQwertyEs.languageTag)
        assertEquals(Layouts.ID_HEX_QWERTY_ES, Layouts.HexQwertyEs.id)
        assertTrue(Layouts.HexQwertyEs.hexKeys)
        assertEquals(Layouts.HexQwertyEn.rows.size, Layouts.HexQwertyEs.rows.size)
        for ((index, row) in Layouts.HexQwertyEn.rows.withIndex()) {
            val spanish = Layouts.HexQwertyEs.rows[index]
            assertEquals("row $index key count", row.keys.size, spanish.keys.size)
            assertEquals("row $index leading gap", row.leadingGap, spanish.leadingGap, 0f)
            assertEquals("row $index trailing gap", row.trailingGap, spanish.trailingGap, 0f)
            assertEquals("row $index height", row.heightWeight, spanish.heightWeight, 0f)
            assertEquals("row $index keys", row.keys.map { it.label }, spanish.keys.map { it.label })
            // The glyph hints are the approved board's own, so only the popups differ.
            assertEquals("row $index hints", row.keys.map { it.hint }, spanish.keys.map { it.hint })
        }
    }

    @Test
    fun `the Spanish email and URI boards keep the delimiters and the letters`() {
        for ((english, spanish) in listOf(
            Layouts.EmailEn to Layouts.EmailEs,
            Layouts.UriEn to Layouts.UriEs,
        )) {
            val englishOutputs = english.rows.flatMap { it.keys }.map { it.outputText }
            val spanishOutputs = spanish.rows.flatMap { it.keys }.map { it.outputText }
            assertEquals(englishOutputs, spanishOutputs)
            assertEquals(KeyboardLanguages.SPANISH, spanish.languageTag)
        }
        assertTrue(Layouts.EmailEs.rows.flatMap { it.keys }.map { it.outputText }.contains("@"))
        assertTrue(Layouts.UriEs.rows.flatMap { it.keys }.map { it.outputText }.contains("/"))
    }

    @Test
    fun `the number row and the symbols pages are the same boards in both languages`() {
        assertTrue(Layouts.QwertyEs.supportsNumberRow)
        assertTrue(Layouts.EmailEs.supportsNumberRow)
        assertTrue(Layouts.UriEs.supportsNumberRow)
        // The `?123` pages are language-independent and shared, so Spanish keeps them as they are.
        assertEquals(Layouts.SymbolsEn, Layouts.withNumberRow(Layouts.SymbolsEn, true))
    }

    // endregion

    private fun letterKeys(layout: KeyboardLayout): List<Key> = layout.rows.flatMap { row ->
        row.keys.filter(::isLetterKey)
    }

    /**
     * A letter key, not the comma or the period.
     *
     * Both of those are [KeyType.CHARACTER] with their own popups, so "type is a character" is not
     * the test; the letter board's letters are the keys whose label is one Latin letter.
     */
    private fun isLetterKey(key: Key): Boolean =
        key.type == KeyType.CHARACTER && key.label.length == 1 && key.label[0] in 'a'..'z'

    /** `"!ñń"` means the popup cells `!`, `ñ`, `ń` — one character each. */
    private fun String.toAlternates(): List<String> = map { it.toString() }
}
