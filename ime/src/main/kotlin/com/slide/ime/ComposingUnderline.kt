package com.slide.ime

import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan

/**
 * The composing word as the editor should see it.
 *
 * The word being typed is handed to the editor as composing text, which is also what lets the whole
 * word be replaced atomically when it is corrected. Until now that text went over as a plain string,
 * so the only sign the keyboard had anything to say about the word was the candidate in the strip.
 * Underlining it in the text puts the same statement where the user is looking.
 *
 * An [UnderlineSpan] is deliberately the plain one: it draws in the field's own text colour, so it
 * reads as "this word is in question" in every app without the keyboard having to know the field's
 * palette. The span only lives as long as the composing region does, and the settle and commit paths
 * pass unspanned text, so a word that has been corrected stops being underlined the moment it is
 * settled.
 */
internal object ComposingUnderline {

    fun apply(text: CharSequence, underline: Boolean): CharSequence {
        if (!underline || text.isEmpty()) return text
        return SpannableString(text).apply {
            setSpan(UnderlineSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
