package com.slide.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pinyin data carries attribution obligations, and nothing else in the suite would notice if
 * the credit quietly disappeared from an edit.
 *
 * The credit lives in a string resource rather than in this test on purpose: the screen that shows
 * it arrives with the Chinese board, but the obligation exists the moment the data ships in the
 * APK, which it already does. `tools/build_pinyin.py` writes the same names into
 * `PINYIN-LICENSES.md` beside the data, and the release verifier now requires that file to be
 * present in the built APK.
 */
class PinyinAttributionTest {

    @Test
    fun `the pinyin data is credited with every source and its licence`() {
        val root = File(".").absoluteFile.parentFile // app/ when Gradle runs the module's tests
        val strings = File(root, "src/main/res/values/strings.xml")
        assertTrue("Missing ${strings.absolutePath}", strings.exists())

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(strings)
        val nodes = document.getElementsByTagName("string")
        var credit: String? = null
        for (index in 0 until nodes.length) {
            val element = nodes.item(index)
            if (element.attributes?.getNamedItem("name")?.nodeValue == KEY) {
                credit = element.textContent
            }
        }
        assertTrue("$KEY is missing from strings.xml", credit != null)

        for (expected in listOf("CC-CEDICT", "CC BY-SA 4.0", "jieba", "MIT", "Tatoeba", "CC BY 2.0")) {
            assertTrue("The pinyin credit no longer names $expected: $credit", credit!!.contains(expected))
        }
    }

    @Test
    fun `the licence file ships beside the data it describes`() {
        val licences = File("engine/src/main/assets/pinyin/PINYIN-LICENSES.md")
            .let { if (it.exists()) it else File("../engine/src/main/assets/pinyin/PINYIN-LICENSES.md") }
        assertTrue("Run tools/build_pinyin.py; ${licences.absolutePath} is missing", licences.exists())
        val text = licences.readText()
        for (expected in listOf("CC-CEDICT", "jieba", "Tatoeba", "CC BY-SA", "MIT", "CC BY 2.0")) {
            assertTrue("PINYIN-LICENSES.md no longer names $expected", text.contains(expected))
        }
    }

    private companion object {
        const val KEY = "pinyin_attribution"
    }
}
