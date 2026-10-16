package com.slide.ime

/**
 * The pinyin being typed, before any dictionary is consulted.
 *
 * Kept apart from the Latin [StringBuilder] the input method already carries, because the two are
 * composing regions with different rules: theirs grows a word that the lexicon will suggest from,
 * this one grows a romanisation that a Chinese word table is searched by. Sharing one buffer would
 * mean the English suggestion path could be handed a half-typed pinyin string.
 *
 * Deliberately holds no Android and no engine: what a keystroke does to it is the part worth
 * pinning in a test, and the lookup is the caller's business.
 */
internal class PinyinTyping {

    private val letters = StringBuilder()

    /** What is typed so far, which is also what the editor is showing as composing text. */
    val composing: CharSequence get() = letters

    /** True while a romanisation is in progress, so the keyboard knows to answer rather than pass through. */
    val isActive: Boolean get() = letters.isNotEmpty()

    /**
     * Whether [text] is a pinyin letter this buffer should take.
     *
     * Only a single ASCII letter counts. Anything else the keyboard can send — a digit, a mark, a
     * multi-character insert, an emoji — belongs to the editor, and letting it in here would put a
     * character into the romanisation that no word table can ever match.
     */
    fun accepts(text: String): Boolean =
        text.length == 1 && (text[0] in 'a'..'z' || text[0] in 'A'..'Z')

    /**
     * Appends a letter. Returns false and changes nothing when [text] is not one.
     *
     * Folded to lowercase by arithmetic rather than by a case-mapping call, so the fold is the same
     * one on every API level this keyboard supports: pinyin is typed lowercase, and a `NIHAO` that
     * reached the buffer in caps would be a word table miss for every word.
     */
    fun append(text: String): Boolean {
        if (!accepts(text)) return false
        val letter = text[0]
        letters.append(if (letter in 'A'..'Z') (letter.code + 32).toChar() else letter)
        return true
    }

    /** Removes the last letter. Returns false when there was nothing left to remove. */
    fun backspace(): Boolean {
        if (letters.isEmpty()) return false
        letters.setLength(letters.length - 1)
        return true
    }

    /** Forgets the romanisation, on commit or on leaving the language. */
    fun clear() {
        letters.setLength(0)
    }
}
