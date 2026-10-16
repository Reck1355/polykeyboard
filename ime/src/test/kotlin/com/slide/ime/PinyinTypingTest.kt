package com.slide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a keystroke does to a romanisation, pinned without an editor and without the word table.
 *
 * The lookup itself is the engine's business and is pinned there; what lives here is the rule that
 * decides whether a keystroke is part of the romanisation at all. Getting that wrong is quiet: a
 * digit or a mark folded into the buffer is a romanisation no word table can ever match, and the
 * user sees a strip that stops offering words for no visible reason.
 */
class PinyinTypingTest {

    @Test
    fun `letters build the romanisation, and are folded to lowercase`() {
        val typing = PinyinTyping()
        for (letter in "nihao") assertTrue(typing.append(letter.toString()))
        assertEquals("nihao", typing.composing.toString())
        assertTrue(typing.isActive)

        typing.clear()
        assertTrue(typing.append("N"))
        assertEquals("n", typing.composing.toString())
    }

    @Test
    fun `anything that is not a single letter is refused and changes nothing`() {
        val typing = PinyinTyping()
        for (text in listOf("1", " ", ",", "!", "你", "ni", "", "\uD83D\uDE00")) {
            assertFalse("$text should not be a pinyin letter", typing.accepts(text))
            assertFalse(typing.append(text))
        }
        assertEquals("", typing.composing.toString())
        assertFalse(typing.isActive)
    }

    @Test
    fun `backspace shortens the romanisation and stops at empty`() {
        val typing = PinyinTyping()
        assertFalse("nothing to remove yet", typing.backspace())
        for (letter in "xian") typing.append(letter.toString())
        assertTrue(typing.backspace())
        assertEquals("xia", typing.composing.toString())
        repeat(3) { assertTrue(typing.backspace()) }
        assertFalse(typing.isActive)
        assertFalse("empty again", typing.backspace())
    }

    @Test
    fun `clearing leaves nothing behind`() {
        val typing = PinyinTyping()
        for (letter in "zaijian") typing.append(letter.toString())
        typing.clear()
        assertEquals("", typing.composing.toString())
        assertFalse(typing.isActive)
    }
}
