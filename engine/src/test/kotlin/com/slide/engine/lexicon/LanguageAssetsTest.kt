package com.slide.engine.lexicon

import com.slide.core.layout.KeyboardLanguages
import com.slide.engine.TestLexicon
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Spanish assets are read with the same loaders as the English ones, and neither language's
 * data can be loaded as the other's.
 *
 * Reading the real assets from disk is the point. The Spanish lexicon is the first one this code
 * has read that is not pure ASCII, so parsing it is what proves the UTF-8 decoder and the word
 * alphabet agree with what `tools/build_lexicon.py --alphabet latin` wrote; and reading the Spanish
 * models against the English lexicon is what proves the fingerprint the Python builder writes is
 * the fingerprint the Kotlin loader computes, without which every Spanish correction would silently
 * lose its word context.
 */
class LanguageAssetsTest {

    private val spanishLexicon: Lexicon by lazy { lexicon("lexicon_es.bin") }

    @Test
    fun `asset names carry the language, and English keeps the name it always had`() {
        assertEquals("lexicon_es.bin", LexiconLoader.assetNameFor("es"))
        assertEquals("bigrams_es.bin", BigramLoader.assetNameFor("es"))
        assertEquals("trigrams_es.bin", TrigramLoader.assetNameFor("es"))

        // The fallback language the resolver hands back is the language the packaged assets are
        // named for: if those two ever disagree, an unknown subtype loads nothing at all.
        assertEquals(LexiconLoader.ASSET_NAME, LexiconLoader.assetNameFor(KeyboardLanguages.FALLBACK))
        assertEquals(BigramLoader.ASSET_NAME, BigramLoader.assetNameFor(KeyboardLanguages.FALLBACK))
        assertEquals(TrigramLoader.ASSET_NAME, TrigramLoader.assetNameFor(KeyboardLanguages.FALLBACK))
    }

    @Test
    fun `the shipped Spanish lexicon parses and holds the letters Spanish is written with`() {
        assertTrue("Spanish lexicon is empty", spanishLexicon.size > 200_000)
        for (word in listOf("año", "está", "niño", "más", "español", "día", "qué", "señor")) {
            assertTrue("$word is not in the Spanish lexicon", spanishLexicon.contains(word))
        }
        // The accented forms are the same words to a Spanish speaker and different strings to the
        // lexicon, so both spellings have to be present rather than one standing in for the other.
        assertTrue(spanishLexicon.contains("esta"))
        assertTrue(spanishLexicon.contains("el"))
    }

    @Test
    fun `the English lexicon is still ASCII-only and still holds no accented word`() {
        val english = TestLexicon.instance
        assertFalse(english.contains("año"))
        assertFalse(english.contains("está"))
        for (index in 0 until english.size) {
            for (position in 0 until english.lengthAt(index)) {
                val character = english.charAt(index, position)
                if (character !in 'a'..'z' && character != '\'') {
                    throw AssertionError("English entry $index holds '$character'")
                }
            }
        }
    }

    @Test
    fun `the Spanish models load against the Spanish lexicon`() {
        val bigrams = readModel(BigramLoader.assetNameFor("es")) { BigramLoader.read(it, spanishLexicon) }
        val trigrams = readModel(TrigramLoader.assetNameFor("es")) { TrigramLoader.read(it, spanishLexicon) }
        assertTrue("no Spanish bigram contexts", bigrams.pairCount > 10_000)
        assertTrue("no Spanish trigram contexts", trigrams.tripleCount > 10_000)
    }

    @Test
    fun `a model from one language is refused for the other rather than mis-scored`() {
        val english = TestLexicon.instance
        // Indices in these models address the lexicon they were built from. Loading the Spanish one
        // against English words is not a smaller version of the same thing, it is meaningless.
        assertRefused(BigramLoader.assetNameFor("es")) { BigramLoader.read(it, english) }
        assertRefused(TrigramLoader.assetNameFor("es")) { TrigramLoader.read(it, english) }
        assertRefused(BigramLoader.assetNameFor("en")) { BigramLoader.read(it, spanishLexicon) }
        assertRefused(TrigramLoader.assetNameFor("en")) { TrigramLoader.read(it, spanishLexicon) }
    }

    private fun lexicon(name: String): Lexicon =
        File("src/main/assets/$name").inputStream().use(LexiconLoader::read)

    private fun <T> readModel(name: String, read: (java.io.InputStream) -> T): T =
        File("src/main/assets/$name").inputStream().use(read)

    private fun assertRefused(name: String, read: (java.io.InputStream) -> Any) {
        try {
            readModel(name, read)
        } catch (expected: IOException) {
            return
        }
        throw AssertionError("$name was accepted for the wrong language's lexicon")
    }
}
