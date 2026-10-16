package com.slide.engine.pinyin

import android.content.Context

/**
 * The pinyin engine: letters in, Chinese candidate words out.
 *
 * This slice is the engine only. Nothing in the keyboard, the settings or the board calls it yet —
 * the Chinese board and the candidate strip are the next slice, and they are deliberately absent
 * here so that the engine can be judged on its own numbers first. Its only caller today is its own
 * test suite.
 *
 * Two entry points, because a keyboard needs two different things:
 *
 *  * [readings] — the whole input as words. `woaini` gives 我爱你. This is what a commit takes.
 *  * [candidates] — words that begin with what has been typed so far, for the strip above the keys.
 *
 * Both degrade honestly. Letters the engine cannot place produce an empty list, never a guess:
 * a Chinese keyboard that offers an English word for unrecognised pinyin is worse than one that
 * offers nothing, which is the same rule this fork already applies to Spanish gesture typing.
 */
class PinyinEngine(private val data: PinyinData) {

    private val model = PinyinLanguageModel(data)
    private val decoder = PinyinDecoder(data, model)

    /** The input read as whole words, best first. Empty when the input does not tile. */
    fun readings(input: String, limit: Int = 8): List<String> = decoder.decode(input, limit)

    /** Words beginning with [input], best first, for a candidate strip. */
    fun candidates(input: String, limit: Int = 8): List<String> = decoder.prefixCandidates(input, limit)

    /**
     * What a keyboard should offer for [input], best first.
     *
     * Three answers, in the order a person expects them, because the three are not the same question:
     *
     * 1. Words whose pinyin IS what has been typed. `jia` is 家 before 加 before 假, and none of those
     *    is a guess about what comes next.
     * 2. The best readings of everything typed, segmented across syllables. This is the sentence path,
     *    and it is where `woaini` becomes 我爱你: a string that is not any single word's key still has a
     *    reading, and without this the strip offers nothing at all for it.
     * 3. Words that merely start with what has been typed, so a half-typed word still shows what it
     *    could become.
     *
     * [prefixCandidates] alone is only the third of these, and calling it alone is why a phrase used to
     * come back empty and a bare syllable used to answer with the wrong words.
     */
    fun keyboardCandidates(input: String, limit: Int = 8): List<String> {
        val seen = LinkedHashSet<String>()
        for (word in decoder.exactCandidates(input, limit)) {
            seen += word
            if (seen.size >= limit) return seen.toList()
        }
        for (word in decoder.decode(input, limit)) {
            seen += word
            if (seen.size >= limit) return seen.toList()
        }
        for (word in decoder.prefixCandidates(input, limit)) {
            seen += word
            if (seen.size >= limit) break
        }
        return seen.toList()
    }

    /** The best single reading, or null when the input is not pinyin this engine knows. */
    fun best(input: String): String? = readings(input, limit = 1).firstOrNull()

    /** How much the pair model moved a word, for tests and for future tuning. */
    fun modelMargin(word: String, previous: String?): Double? {
        val index = data.indexOf(word)
        if (index < 0) return null
        val previousIndex = previous?.let { data.indexOf(it) } ?: -1
        return model.margin(index, previousIndex)
    }

    /** The syllable table the assets carry, exposed so a test can prove it came from the data. */
    val syllableCount: Int get() = data.syllables.size

    /** How many dictionary words the engine can emit. */
    val wordCount: Int get() = data.words.size

    companion object {
        /** The whole engine, or null when the assets are missing or unreadable. Null is survivable. */
        fun load(context: Context): PinyinEngine? = dataOrNull(context)?.let(::PinyinEngine)

        internal fun dataOrNull(context: Context): PinyinData? = PinyinData.load(context)
    }
}
