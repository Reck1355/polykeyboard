package com.slide.engine.pinyin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order a keyboard offers words in, pinned to what a person typing asked for.
 *
 * This file exists because the table was right and the offer was wrong. 家 is the most frequent
 * reading of `jia` by a factor of four over 加, yet the strip put 将, 叫 and 见 first, because they are
 * common words whose pinyin merely *starts* with `jia` — jiang, jiao, jian — and the search ranked by
 * frequency without asking whether the key was the string that had been typed. A user typed `jia`,
 * did not find 家 in the first three, and reported it. The dictionary was never at fault.
 *
 * The second failure was quieter: `woaini` produced an empty strip, because no single word's pinyin is
 * that string and the prefix search only knows words. The engine already read whole strings and
 * answered 我爱你; nothing had ever asked it to.
 */
class PinyinKeyboardOrderTest {

    private fun engine(): PinyinEngine {
        val dict = File("src/main/assets/${PinyinData.DICT_ASSET}")
        assertTrue("Run tools/build_pinyin.py; ${dict.absolutePath} is missing", dict.exists())
        return PinyinEngine(
            PinyinData.build(
                dict.inputStream().use(PinyinData::readRecords),
                File("src/main/assets/${PinyinData.BIGRAMS_ASSET}").inputStream().use(PinyinData::readRecords),
                File("src/main/assets/${PinyinData.SYLLABLES_ASSET}").readLines().filter(String::isNotEmpty).toSet(),
            ),
        )
    }

    @Test
    fun `the string he typed offers the word he wanted, first, at every width`() {
        val engine = engine()
        assertEquals("jia, first of everything", "家", engine.keyboardCandidates("jia").first())
        assertEquals(
            "jia, the three a narrow strip would draw",
            listOf("家", "加", "假"),
            engine.keyboardCandidates("jia", 3),
        )
    }

    @Test
    fun `a whole word outranks a longer word that merely starts the same way`() {
        val engine = engine()
        // Every word a strip's worth of `jia` offers is a word whose own pinyin IS `jia`. The longer
        // words that merely begin with those letters are 将 (jiang), 叫 (jiao) and 见 (jian), and they
        // are the three that used to be offered first; now they only appear once every exact reading
        // has been passed. Asserting their absence from a narrow strip is asserting the fix.
        val narrow = engine.candidates("jia")
        assertEquals(listOf("家", "加", "假", "架"), narrow.take(4))
        for (longer in listOf("将", "叫", "见")) {
            assertFalse("$longer is a longer key and must not lead the strip: $narrow", narrow.contains(longer))
        }
        // And they are still reachable, so the search did not start hiding words: the list is ordered,
        // not filtered.
        val wide = engine.candidates("jia", 200)
        assertTrue("将 vanished from the wider list: size ${wide.size}", wide.contains("将"))
        assertTrue("家 should still be ahead of 将", wide.indexOf("家") < wide.indexOf("将"))
    }

    @Test
    fun `a phrase no single word spells is still read`() {
        val engine = engine()
        assertEquals("woaini", listOf("我爱你"), engine.keyboardCandidates("woaini").take(1))
        assertEquals("woaini ma", listOf("我爱你吗"), engine.keyboardCandidates("woaini ma").take(1))
        // The old path, kept as the record of what the strip used to be drawn from: it cannot answer
        // this at all, which is why a phrase used to leave the strip empty.
        assertEquals(emptyList<String>(), engine.candidates("woaini"))
    }

    @Test
    fun `every word he typed comes back first`() {
        val engine = engine()
        for ((input, expected) in listOf(
            "jia" to "家",
            "huijia" to "回家",
            "jiaju" to "家具",
            "nihao" to "你好",
            "women" to "我们",
            "zaijian" to "再见",
            "xingqi" to "星期",
        )) {
            assertEquals(input, expected, engine.keyboardCandidates(input).first())
        }
    }
}
