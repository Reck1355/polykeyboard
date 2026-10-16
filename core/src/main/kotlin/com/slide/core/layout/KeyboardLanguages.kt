package com.slide.core.layout

/**
 * The one place a language is decided.
 *
 * Everything that varies by language — which board is drawn, which lexicon and which models are
 * loaded — keys off the string this object returns, and nothing else decides it. A second
 * decision point (a setting, a per-screen guess, a layout's own `languageTag`) would be a second
 * answer to the same question, and the failure that produces is quiet: the board in Spanish and
 * the dictionary in English, with every individual piece behaving as designed.
 *
 * The input is whatever Android reports as the current subtype's language tag. A subtype is
 * declarative and platform-owned (`ime/src/main/res/xml/method.xml`), so this is also the whole of
 * the selection mechanism: the system's own keyboard-language list is what changes it.
 */
object KeyboardLanguages {

    /** English. The shipped data, and the answer for anything unknown or unsupported. */
    const val FALLBACK = "en"

    /** Spanish (Spain). */
    const val SPANISH = "es"

    /**
     * Languages with shipped data — a board, a lexicon and models.
     *
     * Membership is not a licence decision by itself: the *data* behind each entry is pinned in
     * `tools/language_sources.json` and rebuilt from there. Adding a language here without an
     * asset to load would leave it typing with no dictionary at all, so an unsupported tag falls
     * back rather than reaching a board with nothing behind it.
     */
    val SUPPORTED: Set<String> = setOf(FALLBACK, SPANISH)

    /**
     * Resolves a reported subtype language tag to a shipped language.
     *
     * Accepts whatever form the platform uses — `en_US`, `es-ES`, `es`, `es-419` — by taking the
     * primary subtag, and returns [FALLBACK] for anything else, including null. A tag for a
     * language this build has no data for (French, Chinese) resolves to English rather than to a
     * half-installed board, because the alternative on a phone is a keyboard that types but
     * suggests nothing.
     */
    fun resolve(subtypeLanguageTag: String?): String {
        val tag = subtypeLanguageTag?.trim()?.lowercase().orEmpty()
        if (tag.isEmpty()) return FALLBACK
        val primary = tag.split('-', '_').first()
        return if (primary in SUPPORTED) primary else FALLBACK
    }

    /** Whether this build ships a board and dictionary for [language]. */
    fun isSupported(language: String): Boolean = language in SUPPORTED
}
