package com.slide.engine.pinyin

import kotlin.math.ln

/**
 * The language model that makes this an engine rather than a lookup table.
 *
 * Measured, and the reason the model exists: with unigram frequencies alone the decoder reads
 * `woai` as 我 + 啊, because 啊 is a far more frequent word than 爱 and nothing knows what tends to
 * follow 我. Same shape of error for `beijing` → 北极 and `shengri` → 设. With pair counts from a
 * corpus the same decoder gets 36 of 40 cases right.
 *
 * Two tables, interpolated:
 *
 *  * unigram  `P(w)` from the dictionary's frequencies — every word has one, since jieba's
 *    dictionary is the frequency source and CC-CEDICT is the pinyin source.
 *  * bigram   `P(w | prev)` from the corpus, backed off to the unigram whenever the pair was never
 *    seen, so a rare-but-valid word is still reachable.
 *
 * The interpolation weight is deliberately below 1: pair counts come from a small corpus, and
 * trusting them outright would make the model worse than the unigram for pairs that simply never
 * came up.
 */
class PinyinLanguageModel(private val data: PinyinData) {

    private val total: Long = data.frequencies.sumOf { it.toLong() }.coerceAtLeast(1L)

    /** `ln P(w)`, with a floor for the ~28% of words the corpus never counted. */
    private val logUnigram: DoubleArray = DoubleArray(data.words.size) { index ->
        val count = data.frequencies[index]
        if (count > 0) ln(count.toDouble() / total) else FLOOR
    }

    private val logFloor: Double = ln(1.0 / 1e9)

    /**
     * The score of [wordIndex] following [previous], in log space.
     *
     * [previous] of -1 means the start of an utterance, where only the unigram applies.
     */
    fun score(wordIndex: Int, previous: Int): Double {
        val unigram = if (data.frequencies[wordIndex] > 0) logUnigram[wordIndex] else logFloor
        if (previous < 0) return unigram
        val count = data.bigrams[(previous.toLong() shl 32) or wordIndex.toLong()] ?: return unigram + UNSEEN_PENALTY
        val previousTotal = data.bigramTotals[previous] ?: return unigram + UNSEEN_PENALTY
        val bigram = count.toDouble() / previousTotal
        return ln(LAMBDA * bigram + (1.0 - LAMBDA) * kotlin.math.exp(unigram))
    }

    /** The same score, but reported next to the unigram so a test can show the model doing work. */
    fun margin(wordIndex: Int, previous: Int): Double = score(wordIndex, previous) - score(wordIndex, -1)

    private companion object {
        const val LAMBDA = 0.7
        val UNSEEN_PENALTY = ln(1e-7)
        val FLOOR = ln(1.0 / 1e9)
    }
}
