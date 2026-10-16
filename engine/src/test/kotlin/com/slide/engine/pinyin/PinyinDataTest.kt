package com.slide.engine.pinyin

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the real shipped assets rather than a fixture.
 *
 * The packed format has two independent implementations — the Python writer in
 * `tools/build_pinyin.py` and this Kotlin reader — and nothing but this test stops them drifting
 * apart. A silent disagreement here does not throw: it produces a dictionary of noise, which is
 * exactly the failure that is hardest to notice from the outside.
 */
class PinyinDataTest {

    @Test
    fun `the shipped assets load and carry a real dictionary`() {
        val data = loaded()
        assertTrue("Suspiciously small dictionary: ${data.words.size}", data.words.size > 100_000)
        assertTrue("Suspiciously small syllable set: ${data.syllables.size}", data.syllables.size > 400)
        assertTrue("No word pairs at all", data.bigrams.isNotEmpty())
    }

    @Test
    fun `words are grouped by key and the best word leads each group`() {
        val data = loaded()
        var previousKey = ""
        var previousFrequency = Int.MAX_VALUE
        for (index in data.words.indices) {
            val key = data.keys[index]
            assertTrue("Keys out of order at $index: '$previousKey' then '$key'", previousKey <= key)
            if (key == previousKey) {
                assertTrue(
                    "Frequencies must fall inside a key group at $index (${data.words[index]})",
                    data.frequencies[index] <= previousFrequency,
                )
            } else {
                previousFrequency = Int.MAX_VALUE
            }
            previousFrequency = data.frequencies[index]
            previousKey = key
        }
    }

    @Test
    fun `every key range points at exactly the words that share that key`() {
        val data = loaded()
        var covered = 0
        for ((key, range) in data.keyRanges) {
            assertTrue("Empty range for '$key'", !range.isEmpty())
            for (index in range) {
                covered++
                assertEquals("Range for '$key' holds '${data.keys[index]}'", key, data.keys[index])
            }
        }
        assertEquals("Key ranges must cover every word exactly once", data.words.size, covered)
    }

    @Test
    fun `the syllable set comes from the data and holds the awkward ones`() {
        val syllables = File("src/main/assets/${PinyinData.SYLLABLES_ASSET}").readLines().filter(String::isNotEmpty)
        assertEquals("Blank lines in the syllable table", syllables.size, syllables.toSet().size)
        for (expected in listOf("ni", "hao", "zhong", "shuang", "lv", "er")) {
            assertTrue("Syllable table is missing '$expected'", expected in syllables)
        }
    }

    @Test
    fun `a known word carries a pinyin key a human would recognise`() {
        val data = loaded()
        val index = data.indexOf("北京")
        assertTrue("北京 is not in the dictionary", index >= 0)
        assertEquals("beijing", data.keys[index])
        assertTrue("北京 has no frequency", data.frequencies[index] > 0)
    }

    // region Corrupt assets of the right shape

    @Test
    fun `rejects the wrong magic`() {
        val corrupt = packed(listOf(Triple("ni", "你", 5))).also { it[0] = 'X'.code.toByte() }
        val error = assertThrows(IOException::class.java) { PinyinData.readRecords(corrupt.inputStream()) }
        assertTrue(error.message!!, error.message!!.contains("magic"))
    }

    @Test
    fun `rejects an unknown version`() {
        val corrupt = packed(listOf(Triple("ni", "你", 5)), version = 99)
        val error = assertThrows(IOException::class.java) { PinyinData.readRecords(corrupt.inputStream()) }
        assertTrue(error.message!!, error.message!!.contains("version"))
    }

    @Test
    fun `rejects a record count of zero`() {
        val corrupt = packed(listOf(Triple("ni", "你", 5)), declaredCount = 0)
        val error = assertThrows(IOException::class.java) { PinyinData.readRecords(corrupt.inputStream()) }
        assertTrue(error.message!!, error.message!!.contains("records"))
    }

    @Test
    fun `rejects a record whose byte lengths run past the file`() {
        val corrupt = packed(listOf(Triple("ni", "你", 5)), keyLengthOverride = 400)
        assertThrows(IOException::class.java) { PinyinData.readRecords(corrupt.inputStream()) }
    }

    @Test
    fun `rejects trailing data`() {
        val corrupt = packed(listOf(Triple("ni", "你", 5))) + byteArrayOf(0x42)
        val error = assertThrows(IOException::class.java) { PinyinData.readRecords(corrupt.inputStream()) }
        assertTrue(error.message!!, error.message!!.contains("trailing"))
    }

    @Test
    fun `rejects an empty syllable table`() {
        val error = assertThrows(IOException::class.java) {
            PinyinData.build(listOf(PinyinData.Record("ni", "你", 5)), emptyList(), emptySet())
        }
        assertTrue(error.message!!, error.message!!.contains("syllable"))
    }

    /**
     * The frequency column carries a decision, and asserting it here is the only place that can
     * catch a rebuild from the wrong kind of text.
     *
     * `zaijian` used to read as 在建 ("under construction") and `xingqi` as 兴起 ("to rise"), each
     * with the word a person means sitting at rank 2. Nothing in the decoder was wrong: a news
     * corpus counts 在建 (444) above 再见 (343), and because that ratio is all the decoder sees, the
     * sign of it is the sign of the answer. Counts of spoken Chinese have the same two words at 62
     * and 28,825, and the column is now 70% spoken (see `tools/build_pinyin.py`). In the shipped
     * bytes the ratio is 237,740 against 2,724 — the right way round, by two orders of magnitude.
     *
     * The ratio is the assertion, not the ordering: any future source that puts 再见 merely first,
     * by a hair, has not reproduced the fix and should fail here.
     */
    @Test
    fun `the frequency column ranks spoken usage above news usage`() {
        val dict = File("src/main/assets/${PinyinData.DICT_ASSET}")
        assertTrue("Missing ${dict.absolutePath}; run tools/build_pinyin.py", dict.exists())

        val counts = HashMap<String, Int>()
        dict.inputStream().use { stream ->
            for (record in PinyinData.readRecords(stream)) {
                // A word can sit under more than one key (那 is both `na` and `nei`), so the best
                // count is the one that matters.
                if (record.count > (counts[record.word] ?: 0)) counts[record.word] = record.count
            }
        }

        val goodbye = counts["再见"] ?: 0
        val construction = counts["在建"] ?: 0
        val week = counts["星期"] ?: 0
        val rise = counts["兴起"] ?: 0
        println("再见 $goodbye vs 在建 $construction ($goodbye / $construction)   |   星期 $week vs 兴起 $rise ($week / $rise)")
        println("CC-CEDICT words carrying a frequency: " + counts.size)

        assertTrue("再见 is not in the dictionary at all", goodbye > 0)
        assertTrue("在建 outranks 再见 ($construction > $goodbye); the column is news-only again", goodbye > construction)
        assertTrue("兴起 outranks 星期 ($rise > $week); the column is news-only again", week > rise)
        assertTrue("再见 leads 在建 by only $goodbye to $construction, not the two orders of magnitude spoken counts give", goodbye > 50 * construction)
        assertTrue("星期 leads 兴起 by only $week to $rise", week > 4 * rise)
        // 92,178 of the 120,232 CC-CEDICT words carry a count. A dictionary-only build leaves a
        // quarter of them at zero, so this floor is the "the spoken half is still wired in" check.
        assertTrue("Only " + counts.size + " words carry a frequency; the spoken half is missing", counts.size > 80_000)
    }

    /** Writes the layout `tools/build_pinyin.py:write_strings` produces, with room to write it wrongly. */
    private fun packed(
        records: List<Triple<String, String, Int>>,
        version: Int = 1,
        declaredCount: Int = records.size,
        keyLengthOverride: Int? = null,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        for ((key, word, count) in records) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val wordBytes = word.toByteArray(Charsets.UTF_8)
            body.write(
                ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((keyLengthOverride ?: keyBytes.size).toShort())
                    .putShort(wordBytes.size.toShort())
                    .putInt(count)
                    .array(),
            )
            body.write(keyBytes)
            body.write(wordBytes)
        }
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf('P'.code.toByte(), 'Y'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte()))
            .putInt(version)
            .putInt(declaredCount)
            .array()
        return header + body.toByteArray()
    }

    // endregion

    private fun loaded(): PinyinData {
        val dict = File("src/main/assets/${PinyinData.DICT_ASSET}")
        val bigrams = File("src/main/assets/${PinyinData.BIGRAMS_ASSET}")
        val syllables = File("src/main/assets/${PinyinData.SYLLABLES_ASSET}")
        assertTrue("Missing ${dict.absolutePath}; run tools/build_pinyin.py", dict.exists())
        assertTrue("Missing ${bigrams.absolutePath}; run tools/build_pinyin.py", bigrams.exists())
        assertTrue("Missing ${syllables.absolutePath}; run tools/build_pinyin.py", syllables.exists())
        val data = PinyinData.build(
            dict.inputStream().use(PinyinData::readRecords),
            bigrams.inputStream().use(PinyinData::readRecords),
            syllables.readLines().filter(String::isNotEmpty).toSet(),
        )
        assertNotNull(data)
        return data
    }
}
