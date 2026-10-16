package com.slide.engine.pinyin

/**
 * Turns a run of pinyin letters into Chinese words, ranked.
 *
 * The design was settled by a throwaway probe before any of this was written, and the probe failed
 * first: a greedy syllable split could never reach `xi` + `an` for `xian`, and a unigram search got
 * 4 of 10 cases right. Both fixes are here:
 *
 *  * **A lattice, not a split.** Every legal syllable is an arc, and a word is any run of
 *    consecutive arcs whose concatenated pinyin is a dictionary key. `xian` therefore offers
 *    `xian`, and `xi` + `an`, and `xi` + `a` + `n` if those were legal.
 *  * **The pairs in [model].** A word's score is its own frequency, adjusted by what the word
 *    before it makes likely.
 *
 * Two limits keep it fast, both measured rather than guessed: the beam keeps only the best
 * [beamWidth] partial readings at each position, and each dictionary key contributes at most
 * [wordsPerKey] words. Without the second, the key `shi` alone would expand to ninety words at
 * every position it appears at.
 *
 * This is deliberately not a sentence translator. It is the piece a candidate strip sits on, so it
 * answers with readable strings — whole input as one candidate first, then the paths that differ
 * from it.
 */
class PinyinDecoder(
    private val data: PinyinData,
    private val model: PinyinLanguageModel,
    private val beamWidth: Int = 60,
    private val wordsPerKey: Int = 8,
    private val maxWordSyllables: Int = 6,
) {

    /**
     * Every dictionary-word run reachable from each position: `runs[i]` holds `(endPosition, key)`
     * pairs whose key exists in the dictionary, and the word indices that key maps to.
     *
     * Built once per input, because the alternative — walking the lattice inside the dynamic
     * program — does the same work at every beam node and cost 43 ms on a single short word in the
     * probe.
     */
    private class Run(val end: Int, val words: IntRange)

    private fun runsFor(key: String): Array<MutableList<Run>> {
        val runs = Array(key.length) { mutableListOf<Run>() }
        for (start in key.indices) {
            // Depth-first over the syllable lattice, bounded by how many syllables one dictionary
            // word can be made of.
            fun walk(position: Int, syllables: Int, built: StringBuilder) {
                if (syllables > maxWordSyllables || position >= key.length) return
                for (length in 1..data.maxSyllableLength) {
                    if (position + length > key.length) break
                    val syllable = key.substring(position, position + length)
                    if (syllable !in data.syllables) continue
                    val marker = built.length
                    built.append(syllable)
                    val range = data.wordsFor(built.toString())
                    if (range != null) runs[start] += Run(position + length, range)
                    walk(position + length, syllables + 1, built)
                    built.setLength(marker)
                }
            }
            walk(start, 0, StringBuilder())
        }
        return runs
    }

    private class Node(val score: Double, val words: IntArray, val last: Int)

    /**
     * The best readings of [input], best first.
     *
     * Returns fewer than [limit] entries when the input does not tile at all — a half-typed word
     * has no full reading, and inventing one would be worse than answering with the prefix
     * candidates, which is what the caller does in that case.
     */
    fun decode(input: String, limit: Int = 8): List<String> {
        val key = sanitize(input)
        if (key.isEmpty()) return emptyList()

        val runs = runsFor(key)
        val states = Array(key.length + 1) { mutableListOf<Node>() }
        states[0] += Node(0.0, IntArray(0), -1)

        for (position in key.indices) {
            val here = states[position]
            if (here.isEmpty()) continue
            here.sortByDescending { it.score }
            for (node in here.take(beamWidth)) {
                for (run in runs[position]) {
                    var taken = 0
                    for (word in run.words) {
                        if (taken++ >= wordsPerKey) break
                        val score = node.score + model.score(word, node.last)
                        states[run.end] += Node(score, node.words + word, word)
                    }
                }
            }
            // Prune early as well as late: the last position gets the same treatment.
            if (states[position].size > beamWidth * 4) {
                states[position] = ArrayList(states[position].sortedByDescending { it.score }.take(beamWidth))
            }
        }

        val finished = states[key.length]
        if (finished.isEmpty()) return emptyList()

        val rendered = LinkedHashMap<String, Double>()
        for (node in finished.sortedByDescending { it.score }) {
            val text = node.words.joinToString("") { data.words[it] }
            if (text !in rendered) rendered[text] = node.score
            if (rendered.size >= limit) break
        }
        return rendered.keys.toList()
    }

    /**
     * Words whose pinyin starts with [input], best first — what a candidate strip shows while the
     * user is still typing. Deduped, because one word can be reachable by more than one key.
     */
    fun prefixCandidates(input: String, limit: Int = 8): List<String> {
        val key = sanitize(input)
        if (key.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Double, Int>>()
        for ((candidateKey, range) in data.keyRanges) {
            if (!candidateKey.startsWith(key)) continue
            for (word in range) scored += model.score(word, -1) to word
        }
        scored.sortWith(compareByDescending<Pair<Double, Int>> { it.first }.thenBy { it.second })
        // Deduplicated by the word itself, not by its index: a word can be reachable through more
        // than one pinyin key (那 is both `na` and `nei`), and a strip that shows 那 twice looks
        // broken even though each entry is correct on its own.
        val seen = HashSet<String>()
        val out = ArrayList<String>(limit)
        for ((_, word) in scored) {
            if (seen.add(data.words[word])) out += data.words[word]
            if (out.size >= limit) break
        }
        return out
    }

    private fun sanitize(input: String): String {
        val out = StringBuilder(input.length)
        for (character in input) {
            when {
                character in 'a'..'z' -> out.append(character)
                character in 'A'..'Z' -> out.append(character.lowercaseChar())
                character == 'v' -> out.append(character)
                // An apostrophe is the user's own syllable separator; the key space has no room for
                // it, so it is dropped rather than treated as a letter.
                character == '\'' -> Unit
                else -> Unit
            }
        }
        return out.toString()
    }
}
