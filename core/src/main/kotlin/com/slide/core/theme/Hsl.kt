package com.slide.core.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The seed-derivation machinery, as plain arithmetic.
 *
 * This is the exact algorithm [Themes.solidTheme] has always used to turn one seed colour into a
 * coherent set — move a colour's lightness, pin its saturation, pin its lightness — and it is a
 * straight port of `androidx.core.graphics.ColorUtils`' `RGBToHSL`/`HSLToColor` (same ranges, same
 * `%`/rounding, so the same inputs give the same ints).
 *
 * It lives here rather than in ColorUtils because ColorUtils reaches `android.graphics.Color`, whose
 * methods are not mocked in a pure JVM unit test: any colour computed through it throws off-device,
 * so the derivation could not be asserted at all. Porting the two conversions keeps one machinery
 * that both the built-in presets and the user's palette are derived from, and makes it testable
 * without a device.
 *
 * Ranges and channel order match ColorUtils: hsl[0] hue in 0..360, hsl[1] saturation in 0..1,
 * hsl[2] lightness in 0..1.
 */
internal object Hsl {

    /** HSL of an ARGB colour, alpha ignored — the same triple ColorUtils.colorToHSL would produce. */
    fun colorToHsl(color: Int): FloatArray {
        val red = ((color shr 16) and 0xFF) / 255f
        val green = ((color shr 8) and 0xFF) / 255f
        val blue = (color and 0xFF) / 255f

        val high = max(max(red, green), blue)
        val low = min(min(red, green), blue)
        val delta = high - low
        val lightness = (high + low) / 2f

        val hueDegrees: Float
        val saturation: Float
        if (high == low) {
            hueDegrees = 0f
            saturation = 0f
        } else {
            val sector = when (high) {
                red -> ((green - blue) / delta) % 6f
                green -> ((blue - red) / delta) + 2f
                else -> ((red - green) / delta) + 4f
            }
            hueDegrees = (sector * 60f) % 360f
            saturation = delta / (1f - abs(2f * lightness - 1f))
        }

        return floatArrayOf(
            constrain(if (hueDegrees < 0f) hueDegrees + 360f else hueDegrees, 0f, 360f),
            constrain(saturation, 0f, 1f),
            constrain(lightness, 0f, 1f),
        )
    }

    /** Packs HSL back to an opaque ARGB int, the arithmetic behind ColorUtils.HSLToColor. */
    fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
        val h = constrain(hue, 0f, 360f) % 360f
        val s = constrain(saturation, 0f, 1f)
        val l = constrain(lightness, 0f, 1f)

        val chroma = (1f - abs(2f * l - 1f)) * s
        val sector = h / 60f
        val second = chroma * (1f - abs(sector % 2f - 1f))
        val match = l - chroma / 2f

        val (r, g, b) = when {
            sector < 1f -> Triple(chroma, second, 0f)
            sector < 2f -> Triple(second, chroma, 0f)
            sector < 3f -> Triple(0f, chroma, second)
            sector < 4f -> Triple(0f, second, chroma)
            sector < 5f -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }

        return pack(
            red = ((r + match) * 255f).roundToInt().coerceIn(0, 255),
            green = ((g + match) * 255f).roundToInt().coerceIn(0, 255),
            blue = ((b + match) * 255f).roundToInt().coerceIn(0, 255),
        )
    }

    /** [color] with its lightness moved by [delta], keeping hue, saturation and alpha. */
    fun shiftLightness(color: Int, delta: Float): Int {
        val hsl = colorToHsl(color)
        val shifted = hslToColor(hsl[0], hsl[1], constrain(hsl[2] + delta, 0f, 1f))
        return withAlpha(shifted, alpha(color))
    }

    /** [color] with its lightness pinned to [lightness], hue and saturation kept. */
    fun withLightness(color: Int, lightness: Float): Int {
        val hsl = colorToHsl(color)
        return hslToColor(hsl[0], hsl[1], lightness)
    }

    /** [color] with its saturation pinned to [saturation], hue and lightness kept. */
    fun saturate(color: Int, saturation: Float): Int {
        val hsl = colorToHsl(color)
        return hslToColor(hsl[0], saturation, hsl[2])
    }

    /** True when [color] reads as a dark surface, by the lightness a board would be drawn on. */
    fun isDark(color: Int): Boolean = colorToHsl(color)[2] < 0.5f

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun pack(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun constrain(value: Float, low: Float, high: Float): Float = when {
        value < low -> low
        value > high -> high
        else -> value
    }
}
