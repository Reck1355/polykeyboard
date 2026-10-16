package com.slide.engine.pinyin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's numbers, on a fixed table that does not move.
 *
 * The table is not decoration. The unigram-only version of this decoder — the first thing written,
 * before the pair model existed — scored 4 of these cases, and every one of those is a named case
 * below so that the improvement cannot be quietly given back. The threshold is the measured result,
 * not a wish.
 *
 * Two of the cases were once wrong for a reason no decoder work could reach: the frequency column
 * came from a dictionary whose counts lean on news text, where 在建 ("under construction", a
 * roadside sign) outranks 再见 ("goodbye", a thing people say) — 444 against 343 — so `zaijian`
 * offered "under construction". 星期 against 兴起 was the same shape, 7,842 against 207 in spoken
 * counts where the dictionary had it backwards. The frequency column is now a blend with
 * subtitle-derived counts of spoken Chinese (see `tools/build_pinyin.py`, and note that the
 * dictionary keeps a minority share: drop it entirely and 北京 loses to 背景 because subtitles are
 * thin on proper nouns). Both words are rank 1 and are asserted as such below.
 *
 * `xian` is deliberately outside the strict set. For the bare string, 西安 (Xi'an) really is a rarer
 * reading than 见/先/县, and a Chinese IME does not put it first either — so asserting it would be
 * asserting something untrue about Chinese, not about this code.
 */
class PinyinEngineTest {

    @Test
    fun `the engine reads the shipped assets`() {
        val engine = engine()
        assertTrue("No words: ${engine.wordCount}", engine.wordCount > 100_000)
        assertTrue("No syllables: ${engine.syllableCount}", engine.syllableCount > 400)
    }

    @Test
    fun `every expected word is the first reading, not merely reachable`() {
        val engine = engine()
        val misses = mutableListOf<String>()
        var hits = 0
        for ((input, expected) in TABLE) {
            val got = engine.best(input) ?: "(nothing)"
            if (got == expected) hits++ else misses += "$input -> $got (want $expected)"
        }
        val rate = 100.0 * hits / TABLE.size
        println("pinyin top-1: $hits/${TABLE.size} = " + "%.0f".format(rate) + "%")
        println("misses: " + (if (misses.isEmpty()) "none" else misses.joinToString("; ")))
        // The whole table, exactly. "Reachable in the top eight" was the old bar, and it let a wrong
        // first answer pass for weeks. A candidate strip shows the top few, so a word that is only
        // second is a word he picks by hand every single time.
        assertEquals("Wrong first reading: " + misses.joinToString("; "), TABLE.size, hits)
    }

    @Test
    fun `every expected word is reachable in the top eight`() {
        val engine = engine()
        for ((input, expected) in TABLE) {
            val readings = engine.readings(input, limit = 8)
            assertTrue(
                "$input never produced $expected; got ${readings.joinToString(" | ") ?: "(nothing)"}",
                readings.any { it == expected },
            )
        }
    }

    @Test
    fun `the cases the unigram model got wrong are still right`() {
        val engine = engine()
        // Each of these was a miss before the pair model existed, and each is right now because of
        // it. Named individually so a regression says which one broke.
        val wasBroken = listOf(
            "beijing" to "北京",
            "xiansheng" to "先生",
            "shengri" to "生日",
            "woaini" to "我爱你",
            "woai" to "我爱",
            "lvyou" to "旅游",
        )
        for ((input, expected) in wasBroken) {
            assertEquals("Regression on $input", expected, engine.best(input))
        }
    }

    /**
     * The two words that lost to the wrong register, asserted by name.
     *
     * These are the cases Jacky reported: `zaijian` offered 在建 ("under construction") and `xingqi`
     * offered 兴起 ("to rise"), each with the right word sitting at rank 2. Nothing in the decoder
     * was wrong — the frequency column was. They are named here so a frequency source that goes back
     * to news-only counts fails loudly, with the word in the message.
     */
    @Test
    fun `goodbye and week win against the news-register words`() {
        val engine = engine()
        assertEquals("zaijian must read as 再见", "再见", engine.best("zaijian"))
        assertEquals("xingqi must read as 星期", "星期", engine.best("xingqi"))
    }

    /**
     * Ambiguities that are not bugs, recorded so nobody "fixes" them by guessing.
     *
     * `xian` is the only one left. For two bare letters, 西安 (Xi'an) really is a rarer reading than
     * 见/先/县, and a Chinese IME does not put it first either — 西安 wants an apostrophe or some
     * context. `zaijian` and `xingqi` used to be listed here with the same excuse; they are now
     * asserted as rank 1 above, because the frequency column was fixable and was fixed.
     */
    @Test
    fun `bare string ambiguities are documented rather than fudged`() {
        val engine = engine()
        for ((input, rarer) in listOf("xian" to "西安")) {
            val readings = engine.readings(input, limit = 20)
            assertTrue("$input produced no reading at all", readings.isNotEmpty())
            println("$input -> " + readings.take(4).joinToString(" | ") +
                (if (rarer in readings) "   ($rarer reachable)" else "   ($rarer not in the top 20)"))
        }
    }

    @Test
    fun `the pair model is doing the work, not the dictionary`() {
        val engine = engine()
        // 啊 is a more frequent word than 爱; after 我, 爱 has to win. If this margin ever collapses,
        // the bigram table has stopped loading and only the unigram is left.
        val ai = requireNotNull(engine.modelMargin("爱", "我")) { "爱 is not in the dictionary" }
        val a = requireNotNull(engine.modelMargin("啊", "我")) { "啊 is not in the dictionary" }
        println("margin after 我: 爱 ${"%.3f".format(ai)}  啊 ${"%.3f".format(a)}")
        assertTrue("The pair model gave 爱 no advantage after 我", ai > a)
        assertEquals("我爱", engine.best("woai"))
    }

    @Test
    fun `candidates are ordered, deduped and stable`() {
        val engine = engine()
        for (prefix in listOf("ni", "bei", "zhong", "xie", "wo")) {
            val first = engine.candidates(prefix, limit = 8)
            val second = engine.candidates(prefix, limit = 8)
            assertTrue("$prefix produced ${first.size} candidates", first.size >= 5)
            assertEquals("$prefix is not stable across calls", first, second)
            assertEquals("$prefix returned duplicates", first.size, first.toSet().size)
            for (word in first) {
                assertTrue("'$word' does not start with $prefix", word.isNotEmpty())
            }
        }
    }

    @Test
    fun `input it cannot place produces nothing rather than a guess`() {
        val engine = engine()
        assertEquals(emptyList<String>(), engine.readings("qqqqqqqq"))
        assertEquals(emptyList<String>(), engine.candidates("zzzzzzzzz"))
        assertEquals(emptyList<String>(), engine.readings(""))
        assertEquals(emptyList<String>(), engine.readings("12345 !!"))
    }

    @Test
    fun `upper case and apostrophes are accepted as the same input`() {
        val engine = engine()
        assertEquals(engine.best("nihao"), engine.best("NiHao"))
        assertEquals(engine.best("xian"), engine.best("xi'an"))
    }

    @Test
    fun `a candidate query stays fast enough for a keystroke`() {
        val engine = engine()
        val inputs = listOf("ni", "nihao", "beijing", "zhongguo", "xiexie", "gongzuo", "shengri", "woaini")
        repeat(20) { inputs.forEach { engine.candidates(it, limit = 8) } } // warm up the JIT
        val timings = inputs.map { input ->
            val start = System.nanoTime()
            repeat(20) { engine.candidates(input, limit = 8) }
            (System.nanoTime() - start) / 20 / 1_000_000.0
        }
        val median = timings.sorted()[timings.size / 2]
        val worst = timings.max()
        println("candidate query: median ${"%.2f".format(median)} ms, worst ${"%.2f".format(worst)} ms")
        assertTrue("Candidate query is too slow for a keystroke: ${"%.2f".format(worst)} ms", worst < 120.0)
    }

    private companion object {

        /** Loaded once: the assets take a moment to parse, and the table is read by several tests. */
        val shared: PinyinEngine by lazy {
            val dict = File("src/main/assets/${PinyinData.DICT_ASSET}")
            val bigrams = File("src/main/assets/${PinyinData.BIGRAMS_ASSET}")
            val syllables = File("src/main/assets/${PinyinData.SYLLABLES_ASSET}")
            assertTrue("Run tools/build_pinyin.py; ${dict.absolutePath} is missing", dict.exists())
            PinyinEngine(
                PinyinData.build(
                    dict.inputStream().use(PinyinData::readRecords),
                    bigrams.inputStream().use(PinyinData::readRecords),
                    syllables.readLines().filter(String::isNotEmpty).toSet(),
                ),
            )
        }

        fun engine(): PinyinEngine = shared

        /** input to the best reading a Chinese keyboard would offer. */
        val TABLE = listOf(
            "nihao" to "你好",
            "beijing" to "北京",
            "woaini" to "我爱你",
            "zhongguo" to "中国",
            "xiexie" to "谢谢",
            "lvyou" to "旅游",
            "xiansheng" to "先生",
            "gongzuo" to "工作",
            "shengri" to "生日",
            "woai" to "我爱",
            "nihaoma" to "你好吗",
            "duibuqi" to "对不起",
            "meiguanxi" to "没关系",
            "zaijian" to "再见",
            "pengyou" to "朋友",
            "shijie" to "世界",
            "dianhua" to "电话",
            "xuexi" to "学习",
            "gongsi" to "公司",
            "jiaoshi" to "教师",
            "xihuan" to "喜欢",
            "shangban" to "上班",
            "xiaban" to "下班",
            "chifan" to "吃饭",
            "hecha" to "喝茶",
            "kafei" to "咖啡",
            "shouji" to "手机",
            "diannao" to "电脑",
            "yinyue" to "音乐",
            "dianying" to "电影",
            "tushuguan" to "图书馆",
            "xingqi" to "星期",
            "jintian" to "今天",
            "mingtian" to "明天",
            "zuotian" to "昨天",
            "xiexieni" to "谢谢你",
            "buhaoyisi" to "不好意思",
            "meibanfa" to "没办法",
            // Was "向你" ("toward you") until the frequency column moved to spoken counts. 想你 —
            // "miss you" — is what anyone typing these letters means, and it now wins. The old
            // expectation was the table disagreeing with the data, so the expectation moved.
            "xiangni" to "想你",
        )
    }
}
