package com.slide.engine.suggest

import com.slide.engine.gesture.GestureFixtures
import com.slide.engine.lexicon.Lexicon
import com.slide.engine.lexicon.LexiconLoader
import com.slide.engine.pinyin.PinyinData
import com.slide.engine.pinyin.PinyinEngine
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shipped language can fill more than one strip's worth of candidates.
 *
 * The assets the app loads are `lexicon_en.bin` and `lexicon_es.bin` - asked of
 * [LexiconLoader.assetNameFor] rather than assumed, which is how the name was found (`lexicon.bin`
 * is not it).
 *
 * The strip draws three at a time and scrolls through the rest, so a language whose dictionary stops at
 * three has no scroll at all: the gesture reads as broken in that language and works in the others, with
 * every individual piece behaving as designed. The list a language hands over is a property of its own
 * data, not of the view, which is why this reads the assets the app ships rather than a fixture.
 *
 * The counts are printed rather than only asserted. "Spanish scrolls" is a claim about a dictionary, and
 * the number is the evidence for it: 4 and 40 read differently when a language's data is later pruned.
 */
class SuggestionReachTest {

    /** Three, because that is the strip's window (`SuggestionStripView.MAX_VISIBLE`). */
    private val oneStrip = 3

    @Test
    fun `English offers more than one strip's worth`() {
        assertReach("en", "th")
    }

    @Test
    fun `Spanish offers more than one strip's worth`() {
        assertReach("es", "co")
    }

    /**
     * Mandarin's candidates do not come from a lexicon: they are readings of the pinyin table, and the
     * keyboard asks for forty of them (`PINYIN_OFFERED`, which is the strip's own hold). The engine's
     * default is eight, so a limit of eight is what any caller that does not ask gets.
     */
    @Test
    fun `Mandarin offers more than one strip's worth`() {
        val engine = pinyinEngine()
        for (typed in listOf("jia", "ni", "wo", "shi")) {
            val offered = engine.keyboardCandidates(typed, limit = 40)
            println("zh $typed -> ${offered.size}: ${offered.take(6).joinToString(" ")}")
            assertTrue(
                "Mandarin offers ${offered.size} for '$typed', which does not fill one strip",
                offered.size > oneStrip,
            )
        }
    }

    /**
     * The engine's own default is a strip's worth and then some, so a caller that passes no limit still
     * has something to scroll to. [com.slide.engine.pinyin.PinyinEngine] documents eight; this pins it,
     * because a default that quietly became three would take the scroll away from every caller at once.
     */
    @Test
    fun `the pinyin engine's default limit fills more than one strip`() {
        val offered = pinyinEngine().keyboardCandidates("jia")
        assertTrue("default limit offers ${offered.size}", offered.size > oneStrip)
    }

    private fun assertReach(language: String, typed: String) {
        val suggester = TypingSuggester(lexiconFor(language))
        val offered = suggester.suggest(typed, GestureFixtures.qwerty(), blockOffensive = false)
        println("$language $typed -> ${offered.words.size}: ${offered.words.take(6).joinToString { it.word }}")
        assertTrue(
            "$language offers ${offered.words.size} for '$typed', which does not fill one strip",
            offered.words.size > oneStrip,
        )
    }

    /** The asset the app itself would load for this language, not a fixture standing in for it. */
    private fun lexiconFor(language: String): Lexicon {
        val name = LexiconLoader.assetNameFor(language)
        val file = File("src/main/assets/$name")
        assertTrue("Run tools/build_lexicon.py; ${file.absolutePath} is missing", file.exists())
        return file.inputStream().use(LexiconLoader::read)
    }

    private fun pinyinEngine(): PinyinEngine {
        val dict = File("src/main/assets/${PinyinData.DICT_ASSET}")
        val bigrams = File("src/main/assets/${PinyinData.BIGRAMS_ASSET}")
        val syllables = File("src/main/assets/${PinyinData.SYLLABLES_ASSET}")
        assertTrue("Run tools/build_pinyin.py; ${dict.absolutePath} is missing", dict.exists())
        return PinyinEngine(
            PinyinData.build(
                dict.inputStream().use(PinyinData::readRecords),
                bigrams.inputStream().use(PinyinData::readRecords),
                syllables.readLines().filter(String::isNotEmpty).toSet(),
            ),
        )
    }
}
