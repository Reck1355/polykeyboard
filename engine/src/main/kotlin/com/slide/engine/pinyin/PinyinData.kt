package com.slide.engine.pinyin

import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the pinyin engine's data assets, built by `tools/build_pinyin.py`.
 *
 * Three files, all under `assets/pinyin/`:
 *
 *  * `dict.bin` — one record per dictionary entry: the tone-stripped pinyin key, the word, and the
 *    word's frequency. Sorted by key, then by frequency descending, so a key's words are a
 *    contiguous run and the best word for that key is its first. The reader relies on both.
 *  * `bigrams.bin` — word pairs and the count of how often the second followed the first in the
 *    corpus. This is what makes the engine a language model rather than a lookup table: without it
 *    `woai` reads as 我 + 啊, because 啊 is a far more frequent *word* than 爱.
 *  * `syllables.txt` — the legal syllable set, derived from CC-CEDICT by the same script rather
 *    than typed from memory.
 *
 * The format is little-endian, like the writer's `struct.pack("<…")`, and unlike
 * [DataInputStream]'s method names would suggest — so every multi-byte field is read through an
 * explicitly ordered [ByteBuffer] rather than through `readInt`. Getting that wrong does not throw;
 * it silently produces a dictionary of noise.
 */
class PinyinData(
    /** Every word, sorted by key then by descending frequency. */
    val words: Array<String>,
    /** The pinyin key of each word, parallel to [words]. */
    val keys: Array<String>,
    /** Word frequencies, parallel to [words]. */
    val frequencies: IntArray,
    /** Key to the half-open range of [words] that share it. */
    val keyRanges: Map<String, IntRange>,
    /** Legal syllables. Membership is the lattice's only rule. */
    val syllables: Set<String>,
    /** Word pair counts, keyed by `(firstIndex.toLong() shl 32) or secondIndex`. */
    val bigrams: Map<Long, Int>,
    /** For each word index, the total count of bigrams that start with it. */
    val bigramTotals: Map<Int, Int>,
) {

    val maxSyllableLength: Int = syllables.maxOfOrNull(String::length) ?: 0

    /** A record as the asset stores it: a key, a word, and a count. */
    data class Record(val key: String, val word: String, val count: Int)

    /** The words for [key], best first, or null when the key is not in the dictionary. */
    fun wordsFor(key: String): IntRange? = keyRanges[key]

    /** A word's index, or -1. Used by tests and by the user-dictionary merge in a later slice. */
    fun indexOf(word: String): Int = words.indexOf(word)

    companion object {

        const val DICT_ASSET = "pinyin/dict.bin"
        const val BIGRAMS_ASSET = "pinyin/bigrams.bin"
        const val SYLLABLES_ASSET = "pinyin/syllables.txt"

        private const val TAG = "SlidePinyin"
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'Y'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte())
        private const val SUPPORTED_VERSION = 1
        private const val MAX_RECORDS = 4_000_000
        private const val MAX_STRING_BYTES = 4_096
        private const val BUFFER_SIZE = 1 shl 16

        /**
         * Loads every asset, or returns null if any of them is missing or unreadable.
         *
         * Null is survivable on purpose, exactly as in [com.slide.engine.lexicon.LexiconLoader]:
         * without this data the keyboard still types pinyin as Latin letters, which is a much
         * better failure than refusing to open. Every [Exception] is caught rather than
         * [IOException] alone because a file of the right length holding the wrong bytes fails an
         * index check, not a read, and this is called from an IME coroutine with nothing above it.
         */
        fun load(context: Context): PinyinData? =
            try {
                val dict = context.assets.open(DICT_ASSET).use(::readRecords)
                val bigrams = context.assets.open(BIGRAMS_ASSET).use(::readRecords)
                val syllables = context.assets.open(SYLLABLES_ASSET)
                    .use { it.bufferedReader(Charsets.UTF_8).readLines() }
                    .filter(String::isNotEmpty)
                    .toSet()
                build(dict, bigrams, syllables)
            } catch (e: Exception) {
                Log.e(TAG, "Could not read the pinyin assets; pinyin typing will be unavailable", e)
                null
            }

        /** The pure build step, so tests can exercise it without an Android context. */
        fun build(dict: List<Record>, bigrams: List<Record>, syllables: Set<String>): PinyinData {
            if (syllables.isEmpty()) throw IOException("The syllable table is empty")
            val words = Array(dict.size) { dict[it].word }
            val keys = Array(dict.size) { dict[it].key }
            val frequencies = IntArray(dict.size) { dict[it].count }

            val index = HashMap<String, Int>(dict.size * 2)
            for (position in words.indices) index[words[position]] = position

            val ranges = HashMap<String, IntRange>(dict.size)
            var start = 0
            while (start < keys.size) {
                var end = start
                while (end + 1 < keys.size && keys[end + 1] == keys[start]) end++
                ranges[keys[start]] = start..end
                start = end + 1
            }

            val pairs = HashMap<Long, Int>(bigrams.size * 2)
            val totals = HashMap<Int, Int>()
            for (record in bigrams) {
                val first = index[record.key] ?: continue
                val second = index[record.word] ?: continue
                pairs[(first.toLong() shl 32) or second.toLong()] = record.count
                totals[first] = (totals[first] ?: 0) + record.count
            }
            return PinyinData(words, keys, frequencies, ranges, syllables, pairs, totals)
        }

        /**
         * Reads one asset. The layout is written by `write_strings` in `tools/build_pinyin.py`:
         * `PYEN` + version (u32) + count (u32), then per record keyLength (u16) + wordLength (u16) +
         * count (u32) + key bytes + word bytes, little-endian. One packed group per record, so this
         * reader and that writer have exactly one shape to agree on.
         */
        fun readRecords(input: InputStream): List<Record> {
            DataInputStream(input.buffered(BUFFER_SIZE)).use { data ->
                val header = ByteArray(12)
                data.readFully(header)
                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val magic = ByteArray(4).also { buffer.get(it) }
                if (!magic.contentEquals(MAGIC)) {
                    throw IOException("Not a pinyin asset: magic was ${magic.joinToString("") { b -> "%02x".format(b) }}")
                }
                val version = buffer.int
                if (version != SUPPORTED_VERSION) {
                    throw IOException("Pinyin asset version $version, expected $SUPPORTED_VERSION")
                }
                val count = buffer.int
                if (count !in 1..MAX_RECORDS) throw IOException("Pinyin asset claims $count records")

                val out = ArrayList<Record>(count)
                val head = ByteArray(8)
                for (position in 0 until count) {
                    data.readFully(head)
                    val fields = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
                    val keyLength = fields.short.toInt() and 0xFFFF
                    val wordLength = fields.short.toInt() and 0xFFFF
                    val value = fields.int
                    if (keyLength !in 1..MAX_STRING_BYTES || wordLength !in 1..MAX_STRING_BYTES) {
                        throw IOException("Record $position claims a $keyLength-byte key and a $wordLength-byte word")
                    }
                    val keyBytes = readExactly(data, keyLength, "key", position)
                    val wordBytes = readExactly(data, wordLength, "word", position)
                    out += Record(String(keyBytes, Charsets.UTF_8), String(wordBytes, Charsets.UTF_8), value)
                }
                if (data.read() != -1) throw IOException("Pinyin asset has trailing data")
                return out
            }
        }

        /**
         * Reads exactly [length] bytes, or says which record ran short and by how much.
         *
         * `readFully` rather than `InputStream.readNBytes`, which needs API 33 while this app ships
         * to API 26: that short read would have been a `NoSuchMethodError` on a real phone, at load,
         * with nothing to catch it here at compile time. `readFully` throws `EOFException` with no
         * context of its own, so the message is rewritten to name the record.
         */
        private fun readExactly(data: DataInputStream, length: Int, part: String, position: Int): ByteArray {
            val bytes = ByteArray(length)
            try {
                data.readFully(bytes)
            } catch (e: IOException) {
                throw IOException("Record $position claims a $length-byte $part past the end of the file", e)
            }
            return bytes
        }
    }
}
