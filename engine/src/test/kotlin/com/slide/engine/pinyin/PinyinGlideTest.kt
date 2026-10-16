package com.slide.engine.pinyin

import com.slide.engine.gesture.GestureModels
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A swipe in Mandarin, as far as the engine is concerned.
 *
 * A swipe passes over letters and comes back with a string that has to be matched against a list of
 * strings. For an alphabetic language that list is a lexicon of words. Mandarin has no such list and
 * wants none: what a swipe there must come back with is a romanisation, so the list is the pinyin
 * table's own key set, built by [PinyinEngine.swipeLexicon]. Everything after that is the path a typed
 * romanisation already takes, which is why this was a question of which list is handed over rather than
 * of new decoding.
 *
 * What is asserted here is the pair that has to hold together: the key set exists and holds the keys,
 * and the reading a person would expect comes back out of it. A key set missing `nihao`, or a `nihao`
 * whose first reading is not 你好, is a swipe that answers a Chinese board with nothing or with nonsense.
 * The list itself is not a lexicon of words and must never be handed one, because a word list here
 * answers a Chinese board in English.
 */
class PinyinGlideTest {

    @Test
    fun `Mandarin is a language a swipe can be decoded in`() {
        assertTrue(GestureModels.supports(GestureModels.MANDARIN))
    }

    @Test
    fun `the swipe list is the pinyin table's keys`() {
        val lexicon = engine().swipeLexicon()
        assertTrue("No keys at all: ${lexicon.size}", lexicon.size > 10_000)
        val keys = HashSet<String>(lexicon.size * 2)
        for (index in 0 until lexicon.size) keys.add(lexicon.lowercaseAt(index))
        // A romanisation is lower case by nature, so the decoder must be finding forms a swipe can
        // actually produce. A list carrying capitalised entries would be matched against forms no
        // gesture can spell.
        assertTrue("jia is not a key", "jia" in keys)
        assertTrue("nihao is not a key", "nihao" in keys)
        // A phrase is not asserted here, and the reason is worth keeping: 我爱你 is not in the table at
        // all. `woaini` appears zero times in dict.bin, and the reading comes from decoding 我 爱 你
        // through the lattice, which is what keyboardCandidates does and what the next test checks. So
        // a swipe that lands on `woai` is answered with 我爱 and one near a phrase's spelling is answered
        // with the phrase; a swipe does not need the whole phrase to be a key for the phrase to arrive.
        assertTrue("The key list is not a table's worth of keys", keys.size > 5_000)
    }

    @Test
    fun `a swiped key gives the reading a Chinese keyboard would give`() {
        val engine = engine()
        // The point of the list being keys at all: a swipe passing over n-i-h-a-o arrives here as
        // `nihao` and is answered by the very call a typed `nihao` is answered by.
        assertEquals("你好", engine.keyboardCandidates("nihao", 1).first())
        assertEquals("家", engine.keyboardCandidates("jia", 1).first())
        assertEquals("我爱你", engine.keyboardCandidates("woaini", 1).first())
    }

    @Test
    fun `no key in the swipe list is a dead end`() {
        val engine = engine()
        val lexicon = engine.swipeLexicon()
        // Sampled rather than exhaustive: the full table is forty thousand keys and a reading for each
        // is a hundred seconds of test. Every two-hundredth key plus the ones named above is enough to
        // catch a list that has drifted out of step with the table, which is the failure that matters.
        var checked = 0
        var empty = 0
        for (index in 0 until lexicon.size step 200) {
            val key = lexicon.lowercaseAt(index)
            checked++
            if (engine.keyboardCandidates(key, 1).isEmpty()) empty++
        }
        // A key with no reading would be a swipe landing on roman letters the user then has to read as
        // if they were the word, which is the one outcome the board must never show. So this is exact.
        assertTrue("Sampled nothing", checked > 50)
        assertEquals("Keys with no reading", 0, empty)
    }
}

/**
 * The engine over the shipped assets, read the same way [PinyinEngineTest] reads them. The assets take a
 * moment to parse and this class asks the same questions of them three times.
 */
private val sharedGlideEngine: PinyinEngine by lazy {
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

private fun engine(): PinyinEngine = sharedGlideEngine
