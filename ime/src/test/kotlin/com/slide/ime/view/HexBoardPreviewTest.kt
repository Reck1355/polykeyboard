package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.KeyboardLayout
import com.slide.core.layout.Layouts
import com.slide.core.settings.KeyboardSettings
import com.slide.core.theme.HexPalette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draws the board as an SVG, from the geometry the keyboard itself draws from.
 *
 * This exists because "send a picture of what it looks like" cannot be answered from this seat by
 * any ordinary route: there is no emulator here (no /dev/kvm) and no device attached, so a
 * screenshot of the real keyboard is not something this machine can take. A hand-drawn mock would be
 * worse than nothing, because it would be looked at as if it were the board. So this asks
 * [HexBoard] where every key is, [KeyGeometry] what shape it is, and [HexKeyStyle] what colour it
 * is, exactly as the view does, and writes the answer out.
 *
 * What it does not reproduce, and cannot: the typeface is whatever the browser has, the action keys
 * are drawn as their labels rather than as the view's stroke icons, the strip's glyphs are simple
 * shapes rather than [MicGlyph]/[EmojiGlyph]/[GlobeGlyph], and the candidates are two placeholder
 * words. The arrangement, the shapes, the fills and the spacing are the real ones.
 *
 * Run it with `./gradlew :ime:test --tests '*HexBoardPreviewTest*'`; the SVGs land in
 * `ime/build/preview/` and their paths are printed.
 */
class HexBoardPreviewTest {

    private val palette = HexPalette.Jade

    /** A 440 dpi phone, so the strip's 48 dp is about this many pixels. */
    private val stripHeight = 132f

    /** What the strip shows when there are words to suggest. Placeholders, not a real prediction. */
    private val candidates = listOf("the", "then")

    private data class Shot(
        val name: String,
        val orientation: HexBoard.Orientation,
        val width: Float,
        val available: Float,
        val layout: KeyboardLayout,
        /**
         * The key that board's bottom row is expected to carry besides the space bar.
         *
         * The letters board and the browser's board carry the backspace there; a symbols page
         * carries the way back to the letters instead, which is the key he asked for on it.
         */
        val bottomRowCarries: com.slide.core.layout.KeyType = com.slide.core.layout.KeyType.DELETE,
    )

    private val shots = listOf(
        Shot("portrait", HexBoard.Orientation.PORTRAIT, 1080f, 2000f, Layouts.HexQwertyEn),
        Shot(
            "landscape",
            HexBoard.Orientation.LANDSCAPE,
            2400f,
            HexBoard.heightCap(1080f, HexBoard.sizing(HexBoard.Orientation.LANDSCAPE)),
            Layouts.HexQwertyEn,
        ),
        // What a browser's address bar answers with. It is the letters board, and the picture is how
        // that is checked without a phone: the assertions below hold for every shot, so if a field
        // ever gets a board of its own again - and the address row that did have no backspace on it -
        // this picture is what fails first.
        Shot("portrait-browser", HexBoard.Orientation.PORTRAIT, 1080f, 2000f, Layouts.HexEmailEn),
        // The `?123` page, drawn because he reported two things about it in one breath: the symbols
        // key should turn back into letters, and its space bar should be three hexagons and not five.
        Shot(
            name = "portrait-symbols",
            orientation = HexBoard.Orientation.PORTRAIT,
            width = 1080f,
            available = 2000f,
            layout = Layouts.HexSymbolsEn,
            bottomRowCarries = com.slide.core.layout.KeyType.ALPHA,
        ),
    )

    @Test
    fun `writes the board as an svg he can look at`() {
        val out = File("build/preview").apply { mkdirs() }
        for (shot in shots) {
            val svg = render(shot)
            val file = File(out, "${shot.name}.svg")
            file.writeText(svg)
            println("PREVIEW ${file.absolutePath}")

            // A picture that drew nothing would still be a file, so count what went into it.
            val placement = place(shot)
            val cells = placement.keys.sumOf { HexBoard.spanCells(it.key.widthWeight) }
            assertTrue(
                "${shot.name}: one polygon per cell of every key",
                svg.split("<polygon").size - 1 == cells,
            )
            // The two things a picture of THIS board must show: backspace on the bottom row rather
            // than at the end of the letters, and the board's own name on the space bar.
            val bottom = placement.keys.filter { it.row == placement.keys.maxOf { k -> k.row } }
            assertTrue(
                "${shot.name}: the bottom row carries its own key, ${shot.bottomRowCarries}",
                bottom.any { it.key.type == shot.bottomRowCarries },
            )
            assertTrue(
                "${shot.name}: the space bar is drawn",
                bottom.any { it.key.type == com.slide.core.layout.KeyType.SPACE },
            )
            assertTrue(
                "${shot.name}: the board's own name is drawn on the space bar",
                svg.contains(shot.layout.label),
            )
        }
    }

    private fun place(shot: Shot) = HexBoard.place(
        layout = boardLayout(shot),
        viewportWidth = shot.width,
        availableHeight = shot.available,
        sizing = HexBoard.sizing(shot.orientation),
        // The strip is drawn above the board, so the board starts below it. Without this the two
        // are drawn on top of one another and the strip is invisible, which is what the first
        // render of this picture did.
        topOffset = stripHeight,
    )

    @Test
    fun `every drawn point of the board is inside the picture, bottom row included`() {
        // The failure this guards: the picture's height came from the caps, every hexagon hangs a
        // point below its cap, and the bottom row arrived with its points sliced off. This reads the
        // SVG the way a viewer does - the lowest vertex drawn has to sit inside the viewBox.
        for (shot in shots) {
            val svg = render(shot)
            val viewHeight = Regex("""height="([0-9.]+)" viewBox""").find(svg)!!.groupValues[1].toFloat()
            val lowest = Regex("""points="([^"]+)"""").findAll(svg)
                .flatMap { m -> m.groupValues[1].split(" ").filter { it.contains(",") } }
                .map { pair -> pair.substringAfter(",").toFloat() }
                .max()
            assertTrue(
                "${shot.name}: the lowest hexagon point ($lowest) sits inside the picture ($viewHeight)",
                lowest < viewHeight - 2f,
            )
        }
    }

    /**
     * The board as the keyboard actually builds it, digit row and all.
     *
     * Portrait's honeycomb carries the digit row when the setting is on, which it is by default, and
     * landscape's does not — five rows do not fit under the cap the landscape block is held to. A
     * preview drawn from the bare layout would be a row short of the board he types on.
     */
    private fun boardLayout(shot: Shot) = Layouts.withNumberRowForHoneycomb(
        layout = shot.layout,
        // Read from the settings rather than written here: the digit row is off by default now, and
        // a preview that switched it on would picture a board he does not type on. The setting still
        // works — it is only its default that moved.
        enabled = KeyboardSettings().showNumberRow,
        portrait = shot.orientation == HexBoard.Orientation.PORTRAIT,
    )

    private fun render(shot: Shot): String {
        val placement = place(shot)
        val boardTop = stripHeight
        // Measured off the drawn hexagons rather than the caps: a cap's bottom is the bottom of its
        // cell, and every hexagon hangs a point below that, so a height taken from the caps cut the
        // bottom row's points off the picture — which is what he saw and reported twice. This is the
        // same shape function the polygons below are drawn from, so the two cannot drift apart.
        val height = placement.keys.maxOf { placed ->
            val shape = KeyGeometry.hexShapeFor(placed)
            shape.centerY + shape.halfHeight
        } + 18f
        val out = StringBuilder()
        out.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${shot.width.round()}" """)
        out.append("""height="${height.round()}" viewBox="0 0 ${shot.width} $height">""")
        out.append("""<rect width="${shot.width}" height="$height" fill="${hex(palette.background)}"/>""")

        // The strip, drawn from the same numbers the view lays it out with. Voice is on in this
        // build, so the microphone holds the square at the left end and the three mode buttons sit
        // at the right, beside the words.
        val voiceEnabled = true
        val trailing = SuggestionStripLayout.trailingSquares()
        val suggestionLeft = SuggestionStripLayout.suggestionLeft(stripHeight)
        val suggestionWidth = SuggestionStripLayout.suggestionWidth(
            shot.width,
            stripHeight,
            voiceEnabled,
            trailing,
        )
        out.append(
            """<rect x="0" y="0" width="${shot.width}" height="$stripHeight" fill="${hex(palette.background)}"/>""",
        )
        out.append(
            """<line x1="0" y1="$stripHeight" x2="${shot.width}" y2="$stripHeight" """ +
                """stroke="${hex(palette.specialKey)}" stroke-width="2"/>""",
        )
        candidates.forEachIndexed { index, word ->
            val cell = suggestionWidth / 3f
            val centerX = suggestionLeft + cell * (index + 0.5f)
            out.append(
                text(
                    word,
                    centerX,
                    stripHeight / 2f,
                    stripHeight * 0.40f,
                    hex(palette.label),
                ),
            )
        }
        out.append(stripButton(shot.width, 3, ::symbolsGlyph))
        out.append(stripButton(shot.width, 2, ::globeGlyph))
        out.append(stripButton(shot.width, 1, ::emojiGlyph))
        if (voiceEnabled) out.append(micButton(suggestionLeft / 2f))

        // The board: one polygon per cell of every key, with the seams of a wide key painted out.
        for (placed in placement.keys) {
            val cells = HexBoard.spanCells(placed.key.widthWeight)
            val fill = HexKeyStyle.backgroundFor(placed.key, palette)
            val edge = HexKeyStyle.edgeColour(false, palette)
            for (cell in 0 until cells) {
                val shape = KeyGeometry.hexShapeForCell(placed, cell, cells)
                out.append(
                    """<polygon points="${shape.vertices().joinToString(" ") { "${it.x.round()},${it.y.round()}" }}"""" +
                        """ fill="${hex(fill)}" stroke="${hex(edge)}" stroke-width="1.5"/>""",
                )
            }
            if (cells > 1) {
                val seam = KeyGeometry.hexShapeForCell(placed, 0, cells)
                val cellWidth = placed.width / cells
                for (cell in 1 until cells) {
                    val seamX = placed.left + cell * cellWidth
                    out.append(
                        """<line x1="$seamX" y1="${seam.shoulderTop}" x2="$seamX" """ +
                            """y2="${seam.shoulderBottom}" stroke="${hex(fill)}" stroke-width="4"/>""",
                    )
                }
            }
            when (placed.key.type) {
                // Caps, backspace and enter are stroke icons in the view rather than characters, so
                // drawing their label text here put the browser font's "no glyph" box in the three
                // places a thumb looks for. These are the same simple outlines.
                KeyType.SHIFT -> out.append(shiftGlyph(placed))
                KeyType.DELETE -> out.append(deleteGlyph(placed))
                KeyType.ENTER -> out.append(enterGlyph(placed))
                KeyType.SPACE -> out.append(
                    text(
                        shot.layout.label,
                        placed.centerX,
                        placed.centerY,
                        placed.height * 0.22f,
                        hex(palette.label),
                    ),
                )
                else -> if (placed.key.label.isNotEmpty()) {
                    out.append(
                        text(
                            placed.key.label,
                            placed.centerX,
                            placed.centerY,
                            placed.height * 0.30f,
                            hex(palette.label),
                        ),
                    )
                }
            }
        }

        out.append("</svg>")
        return out.toString()
    }

    /** The microphone's square, at the left end of the strip, exactly where the view draws it. */
    /**
     * The strip alone, in the two states he reported on: with the candidates dragged along, and
     * with the microphone switched off.
     *
     * The whole board is the wrong zoom for either. The bar is 132 px of a 2000 px picture, so the
     * hole at the left end and the words entering from the side are both invisible at that size.
     * These are drawn from [SuggestionStripLayout]'s own numbers, the ones the view lays out and hit
     * tests with, so the picture cannot drift from the arithmetic the tests pin.
     */
    @Test
    fun `writes the strip alone, dragged and with the microphone away`() {
        val out = File("build/preview").apply { mkdirs() }
        val eight = listOf("th", "the", "tho", "this", "that", "thy", "then", "they")
        val cell = SuggestionStripLayout.suggestionWidth(
            STRIP_WIDTH,
            stripHeight,
            voiceEnabled = true,
            trailingSquares = SuggestionStripLayout.trailingSquares(),
        ) / 3f

        val rest = stripSvg(eight, scrollPx = 0f, voiceEnabled = true)
        val dragged = stripSvg(eight, scrollPx = cell * 2f, voiceEnabled = true)
        val noMic = stripSvg(emptyList(), scrollPx = 0f, voiceEnabled = false)

        for ((name, svg) in listOf("strip-rest" to rest, "strip-dragged" to dragged, "strip-no-mic" to noMic)) {
            val file = File(out, "$name.svg")
            file.writeText(svg)
            println("PREVIEW ${file.absolutePath}")
        }

        // A picture that drew nothing is still a file, so the two claims are asserted on the SVG
        // itself: a drag two cells along brings candidates in that were off the rest picture, and
        // with the microphone away the first control sits on the strip's own left edge.
        // A picture that drew nothing is still a file, so the two claims are asserted on the SVG:
        // a drag two cells along brings in candidates the resting picture does not show, and with
        // the microphone away the two text controls sit on the strip's own left edge with no third
        // control beside them. That last one is the hole, and it is what the picture is for.
        assertTrue("the dragged strip shows a candidate the resting one does not", dragged.contains(">that<"))
        assertTrue("the resting strip does not show it", !rest.contains(">that<"))

        // Only the left-hand group: the emoji button's own glyph is a circle too, so a plain count of
        // every circle in the picture found six of them and said nothing about the hole.
        val controls = Regex("""<circle cx="([0-9.]+)"""").findAll(noMic)
            .map { it.groupValues[1].toFloat() }
            .filter { it < STRIP_WIDTH / 3f }
            .toList()
        assertEquals("with the microphone away exactly the two text controls are on the left", 2, controls.size)
        assertEquals("the first control sits on the strip's own left edge", stripHeight * 0.5f, controls.min(), 0.01f)
        assertEquals("the second sits in the square beside it", stripHeight * 1.5f, controls.max(), 0.01f)

        // With the microphone on there is nothing on the left at all: its square is at the other
        // end of the bar, which is the seat he asked for.
        val leftWithMic = Regex("""<circle cx="([0-9.]+)"""").findAll(rest)
            .map { it.groupValues[1].toFloat() }
            .filter { it < STRIP_WIDTH / 3f }
            .toList()
        assertTrue("with the microphone on the left edge belongs to the words", leftWithMic.isEmpty())
        val micSeat = SuggestionStripLayout.micLeft(STRIP_WIDTH, stripHeight, voiceEnabled = true)
        assertTrue(
            "the microphone is drawn in the square inside the trailing buttons",
            rest.contains("""<circle cx="${micSeat + stripHeight / 2f}"""),
        )
    }

    /** The strip on its own, at [STRIP_WIDTH], drawn with the layout's own scroll arithmetic. */
    private fun stripSvg(words: List<String>, scrollPx: Float, voiceEnabled: Boolean): String {
        val trailing = SuggestionStripLayout.trailingSquares()
        val windowLeft = SuggestionStripLayout.suggestionLeft(stripHeight)
        val windowWidth = SuggestionStripLayout.suggestionWidth(STRIP_WIDTH, stripHeight, voiceEnabled, trailing)
        // With the microphone away there are no words at all, because that is the state the hole was
        // in: the strip empty, the two text controls on the left, the microphone gone.
        val shown = if (voiceEnabled) words else emptyList()
        val cell = windowWidth / 3f
        val scrollSlot = if (cell > 0f) kotlin.math.round(scrollPx / cell).toInt() else 0
        val height = stripHeight
        val out = StringBuilder()
        out.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${STRIP_WIDTH.round()}" """)
        out.append("""height="$height" viewBox="0 0 $STRIP_WIDTH $height">""")
        out.append("""<rect width="$STRIP_WIDTH" height="$height" fill="${hex(palette.background)}"/>""")
        out.append("""<clipPath id="window"><rect x="$windowLeft" y="0" width="$windowWidth" height="$height"/></clipPath>""")

        if (shown.size > 3) {
            val trackWidth = cell * 3f
            val shownShare = (scrollPx + trackWidth) / (cell * shown.size)
            out.append(
                """<line x1="$windowLeft" y1="${height - 2f}" x2="${windowLeft + trackWidth * shownShare}" """ +
                    """y2="${height - 2f}" stroke="${hex(palette.label)}" stroke-width="2"/>""",
            )
        }
        out.append("""<g clip-path="url(#window)">""")
        for (slot in -1..3) {
            val index = scrollSlot + slot
            val word = shown.getOrNull(index) ?: continue
            val left = windowLeft + slot * cell - scrollPx
            out.append(text(word, left + cell / 2f, height / 2f, height * 0.40f, hex(palette.label)))
        }
        for (slot in 1..2) {
            val x = windowLeft + slot * cell - scrollPx
            out.append(
                """<line x1="$x" y1="10" x2="$x" y2="${height - 10}" """ +
                    """stroke="${hex(palette.specialKey)}" stroke-width="1"/>""",
            )
        }
        out.append("</g>")

        // The controls on the left, packed against the edge: the microphone while it is on, and the
        // two text controls while the strip has nothing to suggest.
        val groupStart = SuggestionStripLayout.leftGroupStart()

        if (shown.isEmpty()) {
            for (step in 0..1) {
                val cx = stripHeight * (groupStart + 0.5f + step)
                out.append(
                    """<circle cx="$cx" cy="${height / 2f}" r="${height * 0.36f}" """ +
                        """fill="${hex(palette.specialKey)}"/>""",
                )
                if (step == 0) {
                    out.append(
                        """<rect x="${cx - height * 0.12f}" y="${height * 0.38f}" """ +
                            """width="${height * 0.24f}" height="${height * 0.24f}" fill="none" """ +
                            """stroke="${hex(palette.label)}" stroke-width="2"/>""",
                    )
                } else {
                    out.append(
                        """<line x1="${cx - height * 0.12f}" y1="${height * 0.60f}" """ +
                            """x2="${cx + height * 0.12f}" y2="${height * 0.40f}" """ +
                            """stroke="${hex(palette.label)}" stroke-width="2"/>""",
                    )
                }
            }
        }

        out.append(stripButton(STRIP_WIDTH, 3, ::symbolsGlyph))
        out.append(stripButton(STRIP_WIDTH, 2, ::globeGlyph))
        out.append(stripButton(STRIP_WIDTH, 1, ::emojiGlyph))
        // The microphone's own square, the first one inside the trailing buttons.
        if (voiceEnabled && shown.isNotEmpty()) {
            out.append(
                micButton(
                    SuggestionStripLayout.micLeft(STRIP_WIDTH, stripHeight, voiceEnabled) + stripHeight / 2f,
                ),
            )
        }
        out.append("</svg>")
        return out.toString()
    }

    private val STRIP_WIDTH = 1080f

    private fun micButton(centerX: Float): String {
        val out = StringBuilder()
        out.append(
            """<circle cx="$centerX" cy="${stripHeight / 2f}" r="${stripHeight * 0.36f}" """ +
                """fill="${hex(palette.specialKey)}"/>""",
        )
        micGlyph(out, centerX, stripHeight / 2f)
        return out.toString()
    }

    /**
     * A button's square, from the right edge, with its glyph centred in it. The glyph is handed the
     * square's own centre: drawn from a fixed offset instead, the buttons landed wherever the strip
     * happened to be wide, which is how the picture came to disagree with the keyboard.
     */
    private fun stripButton(
        width: Float,
        fromEdge: Int,
        glyph: (StringBuilder, Float, Float) -> Unit,
    ): String {
        val left = SuggestionStripLayout.squareLeft(width, stripHeight, fromEdge)
        val right = SuggestionStripLayout.squareRight(width, stripHeight, fromEdge)
        val out = StringBuilder()
        out.append(
            """<rect x="$left" y="0" width="${right - left}" height="$stripHeight" """ +
                """fill="${hex(palette.background)}"/>""",
        )
        glyph(out, (left + right) / 2f, stripHeight / 2f)
        return out.toString()
    }

    private fun shiftGlyph(placed: PlacedKey): String {
        val w = placed.height * 0.20f
        val h = placed.height * 0.14f
        val stroke = hex(palette.label)
        val cx = placed.centerX
        val cy = placed.centerY
        return """<path d="M ${cx - w} ${cy + h} L $cx ${cy - h} L ${cx + w} ${cy + h}" """ +
            """fill="none" stroke="$stroke" stroke-width="4" stroke-linecap="round" """ +
            """stroke-linejoin="round"/>"""
    }

    private fun deleteGlyph(placed: PlacedKey): String {
        val w = placed.height * 0.20f
        val h = placed.height * 0.13f
        val stroke = hex(palette.label)
        val cx = placed.centerX
        val cy = placed.centerY
        return """<path d="M ${cx + w} ${cy - h} L ${cx - w * 0.6f} $cy L ${cx + w} ${cy + h} Z" """ +
            """fill="none" stroke="$stroke" stroke-width="4" stroke-linejoin="round"/>""" +
            """<line x1="${cx - w * 0.2f}" y1="${cy - h * 0.4f}" x2="${cx + w * 0.35f}" """ +
            """y2="${cy + h * 0.4f}" stroke="$stroke" stroke-width="4" stroke-linecap="round"/>""" +
            """<line x1="${cx - w * 0.2f}" y1="${cy + h * 0.4f}" x2="${cx + w * 0.35f}" """ +
            """y2="${cy - h * 0.4f}" stroke="$stroke" stroke-width="4" stroke-linecap="round"/>"""
    }

    private fun enterGlyph(placed: PlacedKey): String {
        val w = placed.height * 0.17f
        val h = placed.height * 0.12f
        val stroke = hex(palette.label)
        val cx = placed.centerX
        val cy = placed.centerY
        return """<path d="M ${cx + w} ${cy - h} L ${cx + w} ${cy + h} L ${cx - w} ${cy + h}" """ +
            """fill="none" stroke="$stroke" stroke-width="4" stroke-linecap="round" """ +
            """stroke-linejoin="round"/>"""
    }

    private fun micGlyph(out: StringBuilder, cx: Float, cy: Float) {
        // A rounded capsule and its stand, in the view's stroke family rather than the real glyph.
        val r = stripHeight * 0.16f
        out.append(
            """<rect x="${cx - r * 0.6f}" y="${cy - r}" width="${r * 1.2f}" height="${r * 1.8f}" """ +
                """rx="${r * 0.6f}" fill="none" stroke="${hex(palette.label)}" stroke-width="3"/>""",
        )
        out.append(
            """<path d="M ${cx - r * 1.1f} ${cy} a ${r * 1.1f} ${r * 0.9f} 0 0 0 ${r * 2.2f} 0" """ +
                """fill="none" stroke="${hex(palette.label)}" stroke-width="3"/>""",
        )
    }

    private fun emojiGlyph(out: StringBuilder, cx: Float, cy: Float) {
        val r = stripHeight * 0.22f
        out.append(
            """<circle cx="$cx" cy="$cy" r="$r" fill="none" stroke="${hex(palette.label)}" """ +
                """stroke-width="3"/>""",
        )
        for (dx in listOf(-r * 0.35f, r * 0.35f)) {
            out.append(
                """<circle cx="${cx + dx}" cy="${cy - r * 0.25f}" r="${r * 0.1f}" """ +
                    """fill="${hex(palette.label)}"/>""",
            )
        }
        out.append(
            """<path d="M ${cx - r * 0.45f} ${cy + r * 0.25f} a ${r * 0.45f} ${r * 0.35f} 0 0 0 """ +
                """${r * 0.9f} 0" fill="none" stroke="${hex(palette.label)}" stroke-width="3"/>""",
        )
    }

    private fun globeGlyph(out: StringBuilder, cx: Float, cy: Float) {
        val r = stripHeight * 0.22f
        out.append(
            """<circle cx="$cx" cy="$cy" r="$r" fill="none" stroke="${hex(palette.label)}" """ +
                """stroke-width="3"/>""",
        )
        out.append(
            """<line x1="${cx - r}" y1="$cy" x2="${cx + r}" y2="$cy" """ +
                """stroke="${hex(palette.label)}" stroke-width="3"/>""",
        )
        out.append(
            """<path d="M $cx ${cy - r} a ${r * 0.55f} $r 0 0 1 0 ${r * 2f}" """ +
                """fill="none" stroke="${hex(palette.label)}" stroke-width="3"/>""",
        )
    }

    /** The letters/symbols square, whose label is text in the view as well. */
    private fun symbolsGlyph(out: StringBuilder, cx: Float, cy: Float) {
        out.append(text("?123", cx, cy, stripHeight * 0.30f, hex(palette.label)))
    }

    private fun text(value: String, centerX: Float, centerY: Float, size: Float, colour: String) =
        """<text x="$centerX" y="$centerY" fill="$colour" font-family="sans-serif" """ +
            """font-size="$size" text-anchor="middle" dominant-baseline="central">""" +
            "${escape(value)}</text>"

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun hex(colour: Int) = "#%06X".format(colour and 0xFFFFFF)

    private fun Float.round() = "%.2f".format(this)

}
