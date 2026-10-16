package com.slide.app

import com.slide.core.theme.ColourChoice
import com.slide.core.theme.Colours
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The fork's identity, as text and vector — no device, no rendering.
 *
 * Two rules that must not drift in opposite directions. The app must never call itself by its
 * upstream name in anything a person reads: the launcher label, the name Android shows in its
 * keyboard picker, the settings copy, the TalkBack description, the update dialogs. And it must
 * keep naming Slide in the About attribution, because Apache-2.0 requires that credit and the
 * notices it points at. The first rule without the second is a licence problem; the second
 * without the first is a fork that still reads as the thing it forked.
 *
 * So "Slide" is forbidden in a branding string and required in the attribution string, and the
 * scan below is what keeps those two sets from quietly swapping places.
 */
class BrandingTest {

    private companion object {
        const val UPSTREAM = "Slide"
        const val FORK = "Polykeyboard"

        const val ANDROID = "http://schemas.android.com/apk/res/android"

        /** Android's adaptive-icon safe circle: 66 dp of diameter inside a 108 dp viewport. */
        const val SAFE_RADIUS_DP = 33.0
        const val VIEWPORT_CENTRE_DP = 54.0

        val ICON_VECTORS = listOf(
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "app/src/main/res/drawable/ic_launcher_monochrome.xml",
        )

        val ADAPTIVE_ICONS = listOf(
            "app/src/main/res/mipmap-anydpi/ic_launcher.xml",
            "app/src/main/res/mipmap-anydpi/ic_launcher_round.xml",
        )
    }

    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "settings.gradle.kts").isFile }

    private fun source(relative: String) = File(root, relative).readText()

    private fun document(relative: String) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(root, relative))

    private fun elements(relative: String, tag: String): List<Element> {
        val nodes = document(relative).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun strings(relative: String): Map<String, String> =
        elements(relative, "string").associate { it.getAttribute("name") to it.textContent }

    private fun pathData(relative: String): List<String> =
        elements(relative, "path").map { it.getAttributeNS(ANDROID, "pathData") }

    private fun fills(relative: String): Set<String> =
        elements(relative, "path").map { it.getAttributeNS(ANDROID, "fillColor") }.toSet()

    private fun drawable(relative: String, tag: String): String? =
        elements(relative, tag).firstOrNull()?.getAttributeNS(ANDROID, "drawable")

    /**
     * Every double-quoted literal in the file, line by line, so a literal split across a `+` join
     * is still seen as text. Quotes are read in pairs from the start of each line.
     */
    private fun quotedLiterals(text: String): List<String> =
        text.lineSequence()
            .flatMap { line -> Regex("\"([^\"\\\\]*)\"").findAll(line).map { it.groupValues[1] } }
            .toList()

    // region The name a person sees

    @Test
    fun `the launcher and the keyboard picker show the fork's name`() {
        assertEquals(FORK, strings("app/src/main/res/values/strings.xml")["app_name"])

        val ime = strings("ime/src/main/res/values/strings.xml")
        assertEquals(FORK, ime["ime_name"])
        // Not branding: the language names Android lists under the keyboard, left exactly as they
        // were when Spanish was added.
        assertEquals("English (US)", ime["subtype_en"])
        assertEquals("Español (España)", ime["subtype_es"])

        assertTrue(
            "an app string still carries the upstream name: " +
                strings("app/src/main/res/values/strings.xml").filterValues { UPSTREAM in it },
            strings("app/src/main/res/values/strings.xml").values.none { UPSTREAM in it },
        )
        assertTrue(
            "an IME string still carries the upstream name: " +
                ime.filterValues { UPSTREAM in it },
            ime.values.none { UPSTREAM in it },
        )
    }

    @Test
    fun `nothing the settings screen says still calls the app Slide`() {
        val text = source("app/src/main/kotlin/com/slide/app/MainActivity.kt")

        // Every heading, button label, step card, switch description and dialog in the file. The
        // attribution lives in AboutCopy, asserted separately, so no literal here may name it.
        val literals = quotedLiterals(text)
        assertTrue("the scan found no strings to check", literals.size > 50)
        val branded = literals.filter { UPSTREAM in it }
        assertTrue("settings copy still names the upstream app: $branded", branded.isEmpty())

        // The same lines, pinned by name, so none can be reintroduced one at a time.
        listOf(
            "Enable Slide",
            "Switch to Slide",
            "Turn Slide on in the keyboard list.",
            "Pick Slide from the input method picker.",
            "Slide is your active keyboard.",
            "Allow Slide to install apps",
            "Slide releases page",
            "Stops Slide from learning",
            "Slide keeps learned words",
            "memory while Slide transcribes",
            "sent to a server by Slide",
            "Slide packages one verified model",
            "Slide contacts GitHub",
            "Slide is Apache-2.0 licensed",
            "Slide \${update.version} is available",
            "Downloading Slide",
            "A newer signed Slide release is available.",
            "Slide will forget the personal words",
        ).forEach { assertFalse("still branded: $it", text.contains(it)) }

        // And what replaced them.
        assertTrue(text.contains("""Text("$FORK","""))
        assertTrue(text.contains("""Text("$FORK ${'$'}{update.version} is available")"""))
    }

    @Test
    fun `the keyboard never announces itself by its upstream name`() {
        val view = source("ime/src/main/kotlin/com/slide/ime/view/KeyboardView.kt")

        assertFalse(view.contains("\"Slide keyboard"))
        assertTrue(view.contains("\"$FORK keyboard, emoji search."))
        assertTrue(view.contains("\"$FORK keyboard, \$layer, \$shift."))
    }

    @Test
    fun `the update checker asks the fork's repository and names it in what you read`() {
        val updater = source("app/src/main/kotlin/com/slide/app/UpdateManager.kt")

        assertTrue(
            updater.contains("\"https://api.github.com/repos/Reck1355/polykeyboard/releases?per_page=100\""),
        )
        assertTrue(updater.contains("\"/repos/Reck1355/polykeyboard/releases\""))
        assertFalse("updates are still fetched from the upstream repository", updater.contains("Robertg761"))

        assertTrue(updater.contains("\"The downloaded file is not $FORK\""))
        assertTrue(updater.contains("\"Expected $FORK \$expectedVersion"))
        assertTrue(updater.contains("\"Downloaded APK is not signed by $FORK's release key\""))
    }

    // endregion

    // region The attribution that must survive

    @Test
    fun `the About copy keeps the Slide attribution and carries the fork note`() {
        val forkNote = AboutCopy.FORK_NOTE
        assertTrue(forkNote.contains("This is a fork"))
        assertTrue(forkNote.contains("built on $UPSTREAM (github.com/Robertg761/$UPSTREAM)"))
        assertTrue(forkNote.contains("Apache License 2.0"))
        assertTrue(forkNote.contains("notices are kept in full"))
        assertTrue(forkNote.contains("built by Reck1355 with JadeSoul"))

        // The pitch, in the words the fork was asked for, and it never renames itself Slide.
        assertTrue(AboutCopy.PITCH.startsWith("$FORK - hexagonal keys"))
        assertTrue(AboutCopy.PITCH.contains("two-handed split layout in landscape"))
        assertTrue(AboutCopy.PITCH.contains("adjustable key size"))
        assertTrue(AboutCopy.PITCH.contains("colour control and offline voice typing"))
        assertFalse(AboutCopy.PITCH.contains(UPSTREAM))

        // Both sit on the About card, above the licence block, which is itself above the button
        // that opens the untouched notices loader.
        val text = source("app/src/main/kotlin/com/slide/app/MainActivity.kt")
        val pitch = text.indexOf("AboutCopy.PITCH")
        val note = text.indexOf("AboutCopy.FORK_NOTE")
        val licence = text.indexOf("public language data. Gesture typing is powered by FUTO Swipe")
        val notices = text.indexOf("Button(onClick = { showThirdPartyNotices = true })")
        assertTrue("the About card must show the fork note", pitch in 0 until note)
        assertTrue("the fork note belongs above the licence sentence", note < licence)
        assertTrue("the licence block belongs above the notices button", licence < notices)

        assertTrue(text.contains("""context.assets.open("THIRD_PARTY_NOTICES.txt")"""))
        assertTrue(text.contains("""context.assets.open("swipe/FUTO_MODEL_LICENSE.md")"""))
    }

    // endregion

    // region The icon

    @Test
    fun `every icon vertex stays inside the adaptive-icon safe circle`() {
        val vertex = Regex("""(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
        val command = Regex("""[A-Za-z]""")

        ICON_VECTORS.forEach { icon ->
            val paths = pathData(icon)
            assertEquals("$icon is not a seven-hex rosette", 7, paths.size)

            val vertices = paths.flatMap { data ->
                assertTrue(
                    "$icon draws a curve or an arc; only M, L and Z keep the vertices exact: $data",
                    command.findAll(data).map { it.value }.all { it in setOf("M", "L", "Z") },
                )
                vertex.findAll(data).map { match ->
                    val (x, y) = match.destructured
                    x.toDouble() to y.toDouble()
                }.toList().also {
                    assertEquals("$icon: a hexagon has six corners", 6, it.size)
                }
            }
            assertEquals("$icon: seven hexagons, forty-two corners", 42, vertices.size)

            val farthest = vertices.maxOf { (x, y) ->
                hypot(x - VIEWPORT_CENTRE_DP, y - VIEWPORT_CENTRE_DP)
            }
            assertTrue(
                "$icon reaches $farthest dp from the centre, outside Android's safe circle",
                farthest <= SAFE_RADIUS_DP,
            )
            assertTrue("$icon is too small to read at launcher size ($farthest dp)", farthest >= 30.0)
        }
    }

    @Test
    fun `the monochrome layer is the silhouette, not the seven-colour rosette`() {
        ADAPTIVE_ICONS.forEach { icon ->
            assertEquals("@drawable/ic_launcher_foreground", drawable(icon, "foreground"))
            // Reusing the foreground here would tint a seven-colour rosette as a silhouette.
            assertEquals("@drawable/ic_launcher_monochrome", drawable(icon, "monochrome"))
            assertEquals("@color/ic_launcher_background", drawable(icon, "background"))
        }

        val monochrome = fills("app/src/main/res/drawable/ic_launcher_monochrome.xml")
        assertEquals("a themed icon is one colour: $monochrome", setOf("#FFFFFF"), monochrome)

        val foreground = fills("app/src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue("the rosette kept its palette: $foreground", foreground.size > 1)
        assertFalse("the silhouette leaked into the rosette", "#FFFFFF" in foreground)
    }

    @Test
    fun `the icon plate is the keyboard's jade, not the old blue`() {
        val plate = elements("app/src/main/res/values/colors.xml", "color")
            .single()
        assertEquals("ic_launcher_background", plate.getAttribute("name"))
        val hex = plate.textContent.trim()

        assertNotEquals("the stock Google-blue plate is back", "#1A73E8", hex)
        assertEquals("#0E1417", hex)
        // The keyboard paints the same background, so the plate cannot drift away from the board.
        assertEquals(Colours.palette(ColourChoice.Default).background and 0x00FFFFFF, hex.removePrefix("#").toInt(16))
    }

    // endregion
}
