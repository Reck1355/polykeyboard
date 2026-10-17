package com.slide.ime.view

import com.slide.core.layout.KeyType
import com.slide.core.layout.KeyboardLayout
import com.slide.core.layout.Layouts
import com.slide.core.settings.KeyboardSettings
import com.slide.core.theme.HexPalette
import java.io.File
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
                "${shot.name}: backspace is drawn on the bottom row",
                bottom.any { it.key.type == com.slide.core.layout.KeyType.DELETE },
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
        // build, so the microphone is one of the buttons and the three squares sit beside the words
        // rather than the two that were reserved here when it was off.
        val voiceEnabled = true
        val trailing = SuggestionStripLayout.trailingSquares(voiceEnabled)
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
        out.append(stripButton(shot.width, voiceEnabled, 3, ::globeGlyph))
        out.append(stripButton(shot.width, voiceEnabled, 2, ::emojiGlyph))
        if (voiceEnabled) out.append(stripButton(shot.width, voiceEnabled, 1, ::micGlyph))

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

    private fun stripButton(
        width: Float,
        voiceEnabled: Boolean,
        fromEdge: Int,
        glyph: (StringBuilder) -> Unit,
    ): String {
        val mic = if (voiceEnabled) 1 else 0
        val left = SuggestionStripLayout.squareLeft(width, stripHeight, fromEdge + mic)
        val right = SuggestionStripLayout.squareRight(width, stripHeight, fromEdge + mic)
        val out = StringBuilder()
        out.append(
            """<rect x="$left" y="0" width="${right - left}" height="$stripHeight" """ +
                """fill="${hex(palette.background)}"/>""",
        )
        glyph(out)
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

    private fun micGlyph(out: StringBuilder) {
        // A rounded capsule and its stand, in the view's stroke family rather than the real glyph.
        val cx = stripHeight * 3.5f
        val cy = stripHeight / 2f
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

    private fun emojiGlyph(out: StringBuilder) {
        val cx = stripHeight * 2.5f
        val cy = stripHeight / 2f
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

    private fun globeGlyph(out: StringBuilder) {
        val cx = stripHeight * 1.5f
        val cy = stripHeight / 2f
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
