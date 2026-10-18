package com.slide.core.layout

import com.slide.core.BuildConfig

/**
 * The one place a language is decided.
 *
 * Everything that varies by language — which board is drawn, which lexicon and which models are
 * loaded — keys off the string this object returns, and nothing else decides it. A second
 * decision point (a setting, a per-screen guess, a layout's own `languageTag`) would be a second
 * answer to the same question, and the failure that produces is quiet: the board in Spanish and
 * the dictionary in English, with every individual piece behaving as designed.
 *
 * There are two doors onto that one decision, and they are deliberately not two decisions:
 *
 * - The user's own choice, stored in `KeyboardSettings.languageTag`. This is what the settings
 *   app's Languages card and the keyboard's globe key both write, and it is the door that made the
 *   keyboard look like it had no language control at all when that card did not exist.
 * - Android's current subtype, which is a platform-owned copy of the same fact
 *   (`ime/src/main/res/xml/method.xml`). A *declared* subtype is not an *enabled* one: Android
 *   enables the entries its own keyboard-language list is ticked for, so with one enabled subtype
 *   the globe key was correctly switching to nothing, and the only way to change language was a
 *   screen the user had no reason to find.
 *
 * [SWITCHABLE] is the list both doors speak in terms of, and it is also the whole of the in-app
 * control: one row per entry, in this order, cycling from the last back to the first.
 */
object KeyboardLanguages {

    /** English. The shipped data, and the answer for anything unknown or unsupported. */
    const val FALLBACK = "en"

    /** Spanish (Spain). */
    const val SPANISH = "es"

    /**
     * Mandarin Chinese.
     *
     * Its data is not a lexicon beside the others: pinyin input needs a word table keyed by
     * romanisation plus a language model, both of which live in the engine module behind
     * `PinyinEngine`. That engine has shipped inside this APK since the pinyin groundwork landed and
     * had no caller until the keyboard learned to read it, so the board here is the same QWERTY the
     * user already knows and only the input method's own handling of a keystroke differs.
     */
    const val CHINESE = "zh"

    /**
     * Languages with shipped data — a board, a lexicon and models.
     *
     * Membership is not a licence decision by itself: the *data* behind each entry is pinned in
     * `tools/language_sources.json` and rebuilt from there. Adding a language here without an
     * asset to load would leave it typing with no dictionary at all, so an unsupported tag falls
     * back rather than reaching a board with nothing behind it.
     */
    val SUPPORTED: Set<String> = if (BuildConfig.ENGLISH_ONLY) {
        setOf(FALLBACK)
    } else {
        setOf(FALLBACK, SPANISH, CHINESE)
    }

    /**
     * One selectable language: the shipped language it resolves to, the subtype tag it is declared
     * under, and the name a person reads.
     *
     * [label] is the language's own name for itself rather than its English name, because this list
     * is read by someone looking for the language they type in. [tag] is what Android expects in a
     * subtype and what the settings store holds, so a stored value is always a tag rather than a
     * bare language and can be handed to either door unchanged.
     */
    data class Entry(val language: String, val tag: String, val label: String)

    /**
     * Every language this build can switch to, in the order the globe key and the settings card
     * both walk. English is first because it is the fallback the board is drawn in before anything
     * has been chosen.
     */
    /**
     * The list both doors speak in terms of, narrowed to what this build carries. A slim build
     * ships English data only, and offering a language whose lexicon is absent would be the quiet
     * failure this object exists to prevent: a Spanish board reading an English dictionary. Both
     * doors read this one list, so neither can disagree with the other.
     */
    val SWITCHABLE: List<Entry> = listOf(
        Entry(FALLBACK, "en-US", "English"),
        Entry(SPANISH, "es-ES", "Español"),
        Entry(CHINESE, "zh-CN", "中文"),
    )
        .filter { !BuildConfig.ENGLISH_ONLY || it.language == FALLBACK }

    /** The entry for a resolved [language], or the fallback's when nothing matches. */
    fun entryFor(language: String): Entry =
        SWITCHABLE.firstOrNull { it.language == language } ?: SWITCHABLE.first()

    /**
     * The entry for a stored or reported tag, matching on the tag's language only.
     *
     * `en-GB` and `en_US` are both English, so a device reporting a regional variant of a language
     * this build ships gets that language rather than the fallback.
     */
    fun entryForTag(tag: String?): Entry = entryFor(resolve(tag))

    /**
     * The languages the globe key actually cycles, in [SWITCHABLE] order.
     *
     * An empty [stored] set means every shipped language is on — that is a fresh install, and it is
     * also how a backup from before this control existed has to read. A set that names nothing this
     * build ships is treated the same way, rather than leaving the globe with nowhere to go.
     */
    fun enabledLanguages(stored: Set<String>): List<Entry> {
        val known = stored.filter { it in SUPPORTED }.toSet()
        val list = SWITCHABLE.filter { it.language in known }
        return list.ifEmpty { SWITCHABLE }
    }

    /** Whether [language] is in the globe's cycle given the stored on/off set. */
    fun languageIsOn(stored: Set<String>, language: String): Boolean =
        enabledLanguages(stored).any { it.language == language }

    /**
     * The on/off set after flipping [language]. The last remaining language cannot be turned off:
     * a keyboard with no language is not a state this object will return.
     */
    fun withLanguageOn(stored: Set<String>, language: String, on: Boolean): Set<String> {
        if (language !in SUPPORTED) {
            return enabledLanguages(stored).map { it.language }.toSet()
        }
        val current = enabledLanguages(stored).map { it.language }.toMutableSet()
        if (on) {
            current += language
        } else if (current.size > 1) {
            current -= language
        }
        return current
    }

    /**
     * The next language after a resolved [language], wrapping at the end of the languages that
     * are on. [enabled] is the stored on/off set; empty means all of [SWITCHABLE].
     *
     * The argument is a resolved language rather than a tag on purpose: the keyboard holds a
     * resolved language while it is typing, and cycling from a tag would let a regional variant in
     * the store change the order of the list.
     */
    fun next(language: String, enabled: Set<String> = emptySet()): Entry {
        val list = enabledLanguages(enabled)
        val index = list.indexOfFirst { it.language == language }
        return if (index < 0) list.first() else list[(index + 1) % list.size]
    }

    /**
     * Resolves a reported subtype language tag to a shipped language.
     *
     * Accepts whatever form the platform uses — `en_US`, `es-ES`, `es`, `es-419` — by taking the
     * primary subtag, and returns [FALLBACK] for anything else, including null. A tag for a
     * language this build has no data for (French, Japanese) resolves to English rather than to a
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
