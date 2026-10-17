package com.slide.core.layout

/**
 * Built-in layouts for English. Additional languages and arrangements are added here as data.
 */
object Layouts {

    private fun letter(
        label: String,
        alternates: String = "",
        hint: String? = null,
    ) = Key(
        label = label,
        type = KeyType.CHARACTER,
        alternates = alternates.map(Char::toString),
        hint = hint,
    )

    private val shift = Key("⇧", KeyType.SHIFT, widthWeight = 1.5f, gestureEligible = false)
    private val delete = Key("⌫", KeyType.DELETE, widthWeight = 1.5f, repeatable = true, gestureEligible = false)
    private val toSymbols = Key("?123", KeyType.SYMBOLS, widthWeight = 1.5f, gestureEligible = false)
    private val toSymbolsAlt = Key("=\\<", KeyType.SYMBOLS_ALT, widthWeight = 1.5f, gestureEligible = false)
    private val toAlpha = Key("ABC", KeyType.ALPHA, widthWeight = 1.5f, gestureEligible = false)
    private val enter = Key("⏎", KeyType.ENTER, widthWeight = 1.5f, gestureEligible = false)
    private val space = Key(" ", KeyType.SPACE, output = " ", widthWeight = 5f, gestureEligible = false)
    /**
     * Text presentation is forced on the smiley so it draws in the key's own colour like ⇧ and ⌫,
     * rather than as a yellow emoji sitting among monochrome glyphs.
     */
    private val emoji = Key("\u263A\uFE0E", KeyType.EMOJI, gestureEligible = false)
    /** The comma has its own bottom-row key, as on Gboard, so it is not among the alternates. */
    private val period = Key(
        ".",
        KeyType.CHARACTER,
        alternates = listOf("!", "?", "…", "-", "_", "/", ";", ":"),
        gestureEligible = false,
    )
    private val comma = Key(",", KeyType.CHARACTER, gestureEligible = false)

    /** Optional persistent digit row (setting A8). */
    val numberRow = KeyRow(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            Key(it, KeyType.CHARACTER, gestureEligible = false)
        },
        heightWeight = 0.8f,
    )

    /**
     * The same ten digits in the honeycomb's own shape.
     *
     * The stock row above is a rectangle grid's row: ten equal columns, no stagger, and squeezed to
     * eight tenths of a row's height because a rectangle board can afford a shorter row. On the honeycomb
     * none of that fits. Every key there is one regular hexagon, so the row carries no height squeeze or
     * ten squat keys appear along the top; and it is offset by half a cell so it interleaves with the
     * ten keys of the a-row below it, exactly as those interleave with the s-row. Sitting directly above
     * them instead, every digit would straddle two letters and the board's own pattern would break at the
     * first row.
     */
    val hexNumberRow = KeyRow(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            Key(it, KeyType.CHARACTER, gestureEligible = false)
        },
        HEX_ROW_OFFSET,
        HEX_ROW_OFFSET,
    )

    val QwertyEn = KeyboardLayout(
        id = "qwerty_en",
        label = "English (US)",
        languageTag = "en",
        supportsNumberRow = true,
        rows = listOf(
            KeyRow(
                listOf(
                    letter("q", "1%", hint = "1"),
                    letter("w", "2\\", hint = "2"),
                    letter("e", "3èéêëēėę|", hint = "3"),
                    letter("r", "4=", hint = "4"),
                    letter("t", "5[", hint = "5"),
                    letter("y", "6ÿ]", hint = "6"),
                    letter("u", "7ûüùúū<", hint = "7"),
                    letter("i", "8îïíīįì>", hint = "8"),
                    letter("o", "9ôöòóœøōõ{", hint = "9"),
                    letter("p", "0}", hint = "0"),
                ),
            ),
            KeyRow(
                listOf(
                    letter("a", "@àáâäæãåā", hint = "@"),
                    letter("s", "#ßśš", hint = "#"),
                    letter("d", "$", hint = "$"),
                    letter("f", "-", hint = "-"),
                    letter("g", "&", hint = "&"),
                    letter("h", "_", hint = "_"),
                    letter("j", "+", hint = "+"),
                    letter("k", "(", hint = "("),
                    letter("l", ")ł", hint = ")"),
                ),
                leadingGap = 0.5f,
                trailingGap = 0.5f,
            ),
            KeyRow(
                listOf(
                    shift,
                    letter("z", "*žźż", hint = "*"),
                    letter("x", "\"", hint = "\""),
                    letter("c", "'çćč", hint = "'"),
                    letter("v", ":", hint = ":"),
                    letter("b", ";", hint = ";"),
                    letter("n", "!ñń", hint = "!"),
                    letter("m", "?", hint = "?"),
                    delete,
                ),
            ),
            // Space donates a unit to the comma so the row keeps the grid's total width.
            KeyRow(listOf(toSymbols, comma, emoji, space.copy(widthWeight = 4f), period, enter)),
        ),
    )

    /**
     * The Spanish accents, in the order a Spanish speaker reaches for them.
     *
     * `ü` is here rather than on the `u` entry because that key offers two: the diaeresis is not an
     * optional decoration in Spanish (`pingüino`, `vergüenza`), it is a different letter.
     *
     * Declared above [QwertyEs] because that board is built by calling [spanish] on the English
     * keys: a `val` read by an earlier `val`'s initializer would still be null at that moment.
     */
    private val SPANISH_ACCENT_FIRST = mapOf(
        "a" to listOf("á"),
        "e" to listOf("é"),
        "i" to listOf("í"),
        "o" to listOf("ó"),
        "u" to listOf("ú", "ü"),
        "n" to listOf("ñ"),
    )

    /**
     * One key of the Spanish board: the English key with the Spanish letters in front of its popup.
     *
     * `distinct` keeps the promotion from listing an accent twice — the English `a` popup already
     * contains `á`, and a popup that offers the same character in two cells reads as two
     * characters.
     */
    private fun spanish(key: Key): Key {
        val accents = SPANISH_ACCENT_FIRST[key.label] ?: return key
        return key.copy(alternates = (accents + key.alternates).distinct())
    }

    /**
     * QWERTY with the Spanish accents on the letters that carry them.
     *
     * The letters are the English board's own keys at the English board's own positions, in the
     * English board's own order, with the same widths, gaps and digit hints: a Spanish board that
     * moved a key would cost a bilingual user the muscle memory of the one they already had. Only
     * each key's long-press popup changes, and only by promoting the Spanish form of that letter
     * to the front (ñ on the n key, and ü beside ú on the u key) — the rest of the English popup,
     * accents and punctuation included, is still there behind it.
     */
    val QwertyEs = QwertyEn.copy(
        id = "qwerty_es",
        label = "Español (España)",
        languageTag = KeyboardLanguages.SPANISH,
        rows = QwertyEn.rows.map { row ->
            KeyRow(
                row.keys.map(::spanish),
                row.leadingGap,
                row.trailingGap,
                row.heightWeight,
            )
        },
    )

    /** QWERTY with the characters people need most often in an email address. */
    val EmailEn = QwertyEn.copy(
        id = "email_en",
        label = "English (US)",
        rows = QwertyEn.rows.dropLast(1) + KeyRow(
            listOf(
                toSymbols,
                sym("@"),
                Key(" ", KeyType.SPACE, output = " ", widthWeight = 3.5f, gestureEligible = false),
                period,
                Key(".com", KeyType.CHARACTER, output = ".com", widthWeight = 1.5f, gestureEligible = false),
                enter,
            ),
        ),
    )

    /** QWERTY with URL delimiters on the primary layer. */
    val UriEn = QwertyEn.copy(
        id = "uri_en",
        label = "English (US)",
        rows = QwertyEn.rows.dropLast(1) + KeyRow(
            listOf(
                toSymbols,
                sym("/"),
                Key(" ", KeyType.SPACE, output = " ", widthWeight = 3.5f, gestureEligible = false),
                period,
                Key(".com", KeyType.CHARACTER, output = ".com", widthWeight = 1.5f, gestureEligible = false),
                enter,
            ),
        ),
    )

    /** [EmailEn]'s board, in Spanish; the delimiters and the letter positions are identical. */
    val EmailEs = QwertyEs.copy(
        id = "email_es",
        label = "Español (España)",
        rows = QwertyEs.rows.dropLast(1) + KeyRow(
            listOf(
                toSymbols,
                sym("@"),
                Key(" ", KeyType.SPACE, output = " ", widthWeight = 3.5f, gestureEligible = false),
                period,
                Key(".com", KeyType.CHARACTER, output = ".com", widthWeight = 1.5f, gestureEligible = false),
                enter,
            ),
        ),
    )

    /** [UriEn]'s board, in Spanish; the delimiters and the letter positions are identical. */
    val UriEs = QwertyEs.copy(
        id = "uri_es",
        label = "Español (España)",
        rows = QwertyEs.rows.dropLast(1) + KeyRow(
            listOf(
                toSymbols,
                sym("/"),
                Key(" ", KeyType.SPACE, output = " ", widthWeight = 3.5f, gestureEligible = false),
                period,
                Key(".com", KeyType.CHARACTER, output = ".com", widthWeight = 1.5f, gestureEligible = false),
                enter,
            ),
        ),
    )

    /**
     * The hexagonal letters board — the approved form.
     *
     * Four rows of 10 / 9 / 9 / 7 keys, every one a regular pointy-top hexagon except the space
     * bar, which is one key over three cells. Row starts step by half a cell (leading gaps of
     * 0 / 0.5 / 0 / 0.5), so every adjacent row sits exactly half a key from the one above it —
     * the stagger a pointy-top honeycomb interlocks on, and the one the first hex attempt got
     * wrong by aligning the a-row and the z-row on top of each other.
     *
     * The letter keys are QWERTY's own objects, so positions, alternates and the decoder keep one
     * source; only their digit hints go, because the approved board draws a bare hexagon. The
     * shift, delete, `?123` and enter keys are the wide QWERTY ones narrowed to a single cell,
     * since on this board every key but space is one cell wide.
     */
    val HexQwertyEn = QwertyEn.copy(
        id = ID_HEX_QWERTY,
        supportsNumberRow = true,
        hexKeys = true,
        rows = listOf(
            KeyRow(QwertyEn.rows[0].keys.map { it.copy(hint = null) }),
            KeyRow(
                QwertyEn.rows[1].keys.map { it.copy(hint = null) },
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
            KeyRow(
                // Backspace used to sit at this row's end, which meant reaching past the letters for
                // it: it landed beside M, one slip from the letter a thumb had just typed. It is on
                // the bottom row, at the space bar's right, and the caps key has come up here in its
                // place. Caps is not destructive, so reaching past the letters for it costs a
                // mis-tap and nothing else. Seven letters alone would leave half a cell of slack on
                // each side while the row above sits half a cell in, which steps a whole cell
                // instead of half of one; the shift key fills the eighth, so the row is centred on
                // the ten-cell grid and still half a cell out of step with its neighbours.
                QwertyEn.rows[2].keys.drop(1).dropLast(1) + listOf(shift.copy(widthWeight = 1f)),
                leadingGap = 1f,
                trailingGap = 1f,
            ),
            KeyRow(
                // Two keys, a three-cell space, two keys: seven cells, one hex in from each edge
                // more than the row above, so the board narrows by a cell a side all the way down —
                // ten, nine, eight, seven. The enter and the backspace sit either side of the space
                // bar, which is where a thumb already is when it goes for either of them.
                //
                // Seven is also the smallest row that centres on the ten-cell grid and stays half a
                // cell out of step with the row above, and the space keeps all three of its cells:
                // the landscape split hands a straddling wide key half its cells rounded up, so
                // three is the fewest that still gives each thumb two.
                //
                // The width came from `?123`, which is a mode rather than a character and now sits
                // on the strip with the emoji and the language switch. The comma and the period
                // keep their places at this row's ends rather than being spent on it.
                listOf(
                    comma,
                    enter.copy(widthWeight = 1f),
                    space.copy(widthWeight = 3f),
                    delete.copy(widthWeight = 1f),
                    period,
                ),
                leadingGap = 1.5f,
                trailingGap = 1.5f,
            ),
        ),
    )

    /**
     * [HexQwertyEn]'s board in Spanish: the same honeycomb, over [QwertyEs]'s keys.
     *
     * The two differ only in the accents inside the letter popups, which is exactly the difference
     * between [QwertyEn] and [QwertyEs]; the stagger, the widths, the bare hexagons and the bottom
     * row are the English board's. The bottom row is shared rather than copied, because nothing in
     * it is a letter and two boards that drifted apart there would be a difference no language
     * asked for.
     */
    val HexQwertyEs = QwertyEs.copy(
        id = ID_HEX_QWERTY_ES,
        supportsNumberRow = true,
        hexKeys = true,
        rows = listOf(
            KeyRow(QwertyEs.rows[0].keys.map { it.copy(hint = null) }),
            KeyRow(
                QwertyEs.rows[1].keys.map { it.copy(hint = null) },
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
            KeyRow(
                // Backspace has moved to the bottom row, which Spanish shares with English, so this
                // row is the letters plus the caps key English carries at its right end: eight
                // cells, centred, and half a cell out of step with the row above. The two boards
                // have to be the same board or a bilingual user loses one of them.
                QwertyEs.rows[2].keys.drop(1).dropLast(1) + listOf(shift.copy(widthWeight = 1f)),
                leadingGap = 1f,
                trailingGap = 1f,
            ),
            HexQwertyEn.rows[3],
        ),
    )

    /**
     * The honeycomb's bottom row with one address delimiter in it (`@` for email, `/` for a URL).
     *
     * Nine cells exactly, like the letters board's own bottom row: `?123`, the delimiter where the
     * comma sits, the space bar, the period, `.com`, and enter. The globe and the emoji key are not
     * here because they are not on any board now — they sit on the strip above, where they are
     * reachable from every page. Space takes up the two cells they gave back, and the ten-column
     * board is what stays: a row one cell wider would scale the whole honeycomb down to fit it.
     */
    private fun hexDelimiterRow(delimiter: String) = KeyRow(
        listOf(
            toSymbols.copy(widthWeight = 1f),
            sym(delimiter),
            space.copy(widthWeight = 4f),
            period,
            Key(".com", KeyType.CHARACTER, output = ".com", gestureEligible = false),
            enter.copy(widthWeight = 1f),
        ),
        HEX_ROW_OFFSET,
        HEX_ROW_OFFSET,
    )

    /**
     * One delimiter row per mode, shared by both languages.
     *
     * The letters board shares its bottom row between English and Spanish for the same reason
     * written above: nothing in it is a letter, so two copies that drifted apart would be a
     * difference no language asked for.
     */
    private val hexEmailRow = hexDelimiterRow("@")

    /** The URL delimiter row, shared by both languages. */
    private val hexUriRow = hexDelimiterRow("/")

    /**
     * [HexQwertyEn] with the email row's delimiters.
     *
     * The stock keyboard answers an email field and a browser's address bar with its own
     * rectangular boards, which is why the hex board disappeared exactly there: the two modes the
     * outside world asks for most often were the two the honeycomb did not cover. These boards put
     * `@` and `/` on the bottom row of the same honeycomb, so the mode a field asks for no longer
     * changes the board's shape.
     */
    val HexEmailEn = HexQwertyEn.copy(
        id = ID_HEX_EMAIL,
        rows = HexQwertyEn.rows.dropLast(1) + hexEmailRow,
    )

    /** [HexEmailEn]'s URL counterpart, with `/` on the bottom row. */
    val HexUriEn = HexQwertyEn.copy(
        id = ID_HEX_URI,
        rows = HexQwertyEn.rows.dropLast(1) + hexUriRow,
    )

    /** [HexEmailEn] in Spanish; the delimiters and the letter positions are identical. */
    val HexEmailEs = HexQwertyEs.copy(
        id = ID_HEX_EMAIL_ES,
        rows = HexQwertyEs.rows.dropLast(1) + hexEmailRow,
    )

    /** [HexUriEs]'s counterpart, with `/` on the bottom row. */
    val HexUriEs = HexQwertyEs.copy(
        id = ID_HEX_URI_ES,
        rows = HexQwertyEs.rows.dropLast(1) + hexUriRow,
    )

    /**
     * The honeycomb's half-key stagger, in width units — 0.5 cells, one half of a key.
     *
     * Named here rather than written as a `0.5f` in each row so the invariant the drawing depends
     * on reads as an intention instead of a magic number.
     */
    private const val HEX_ROW_OFFSET = 0.5f

    /** Layout id of [HexQwertyEn], for the places that must name it without holding it. */
    const val ID_HEX_QWERTY = "hex_qwerty_en"

    /** Layout id of [HexQwertyEs], its Spanish counterpart. */
    const val ID_HEX_QWERTY_ES = "hex_qwerty_es"

    /** Layout id of [HexEmailEn]. */
    const val ID_HEX_EMAIL = "hex_email_en"

    /** Layout id of [HexUriEn]. */
    const val ID_HEX_URI = "hex_uri_en"

    /** Layout id of [HexEmailEs]. */
    const val ID_HEX_EMAIL_ES = "hex_email_es"

    /** Layout id of [HexUriEs]. */
    const val ID_HEX_URI_ES = "hex_uri_es"

    /** First symbols page on the honeycomb (`?123`). */
    const val ID_HEX_SYMBOLS = "hex_symbols_en"

    /** Second symbols page on the honeycomb (`=\<`). */
    const val ID_HEX_SYMBOLS_ALT = "hex_symbols_alt_en"

    private fun digit(value: String, alternates: List<String> = emptyList()) =
        Key(value, KeyType.CHARACTER, alternates = alternates, gestureEligible = false)

    private fun digitRows(rowGap: Float = 0.5f): List<KeyRow> = listOf(
        KeyRow(listOf(digit("1"), digit("2"), digit("3")), rowGap, rowGap),
        KeyRow(listOf(digit("4"), digit("5"), digit("6")), rowGap, rowGap),
        KeyRow(listOf(digit("7"), digit("8"), digit("9")), rowGap, rowGap),
    )

    /** Unsigned number/PIN pad. Zero is deliberately a full two-unit thumb target. */
    val NumberPad = KeyboardLayout(
        id = "number_pad",
        label = "Numbers",
        languageTag = "und",
        rows = digitRows() + KeyRow(
            listOf(
                digit("0").copy(widthWeight = 2f),
                delete.copy(widthWeight = 1f),
                enter.copy(widthWeight = 1f),
            ),
        ),
    )

    val SignedNumberPad = NumberPad.copy(
        id = "signed_number_pad",
        rows = digitRows() + KeyRow(
            listOf(sym("-"), digit("0"), delete.copy(widthWeight = 1f), enter.copy(widthWeight = 1f)),
        ),
    )

    val DecimalPad = NumberPad.copy(
        id = "decimal_pad",
        rows = digitRows() + KeyRow(
            listOf(sym("."), digit("0"), delete.copy(widthWeight = 1f), enter.copy(widthWeight = 1f)),
        ),
    )

    val SignedDecimalPad = NumberPad.copy(
        id = "signed_decimal_pad",
        rows = digitRows() + KeyRow(
            listOf(
                sym("-").copy(widthWeight = 0.8f),
                digit("0").copy(widthWeight = 0.8f),
                sym(".").copy(widthWeight = 0.8f),
                delete.copy(widthWeight = 0.8f),
                enter.copy(widthWeight = 0.8f),
            ),
        ),
    )

    /** Dial pad. Long-pressing zero exposes the international-prefix plus sign. */
    val PhonePad = KeyboardLayout(
        id = "phone_pad",
        label = "Phone",
        languageTag = "und",
        rows = digitRows() + KeyRow(
            listOf(
                sym("*").copy(widthWeight = 0.8f),
                digit("0", alternates = listOf("+")).copy(widthWeight = 0.8f),
                sym("#").copy(widthWeight = 0.8f),
                delete.copy(widthWeight = 0.8f),
                enter.copy(widthWeight = 0.8f),
            ),
        ),
    )

    /** Date fields need explicit separators because many editors do not insert them themselves. */
    val DatePad = NumberPad.copy(
        id = "date_pad",
        label = "Date",
        rows = digitRows() + KeyRow(
            listOf(
                sym("/").copy(widthWeight = 0.65f),
                sym("-").copy(widthWeight = 0.65f),
                digit("0").copy(widthWeight = 0.65f),
                sym(".").copy(widthWeight = 0.65f),
                delete.copy(widthWeight = 0.65f),
                enter.copy(widthWeight = 0.65f),
            ),
        ),
    )

    /** Time fields support 24-hour, fractional, and common 12-hour forms without another layer. */
    val TimePad = NumberPad.copy(
        id = "time_pad",
        label = "Time",
        rows = digitRows() + KeyRow(
            listOf(
                sym(":").copy(widthWeight = 0.58f),
                digit("0").copy(widthWeight = 0.58f),
                sym(".").copy(widthWeight = 0.58f),
                Key("AM", output = "AM", widthWeight = 0.58f, gestureEligible = false),
                Key("PM", output = "PM", widthWeight = 0.58f, gestureEligible = false),
                delete.copy(widthWeight = 0.58f),
                enter.copy(widthWeight = 0.58f),
            ),
        ),
    )

    /** Generic date-time editors get the union of date and time punctuation plus AM/PM. */
    val DateTimePad = NumberPad.copy(
        id = "datetime_pad",
        label = "Date and time",
        rows = digitRows() + listOf(
            KeyRow(
                listOf(sym("/"), sym("-"), digit("0"), sym(":"), sym("."))
                    .map { it.copy(widthWeight = 0.8f) },
            ),
            KeyRow(
                listOf(
                    Key("AM", output = "AM", widthWeight = 1f, gestureEligible = false),
                    Key("PM", output = "PM", widthWeight = 1f, gestureEligible = false),
                    delete.copy(widthWeight = 1f),
                    enter.copy(widthWeight = 1f),
                ),
                heightWeight = 0.8f,
            ),
        ),
    )

    /** First symbols page (`?123`), arranged as Gboard's: `_` and `/` earn first-page spots. */
    val SymbolsEn = KeyboardLayout(
        id = "symbols_en",
        label = "Symbols",
        languageTag = "en",
        rows = listOf(
            KeyRow("1234567890".map { sym(it.toString()) }),
            KeyRow(
                listOf(
                    sym("@"), sym("#"), sym("$", "€£¥¢"), sym("_"), sym("&"),
                    sym("-", "–—~"), sym("+", "±"), sym("(", "[{<"), sym(")", "]}>"), sym("/"),
                ),
            ),
            KeyRow(
                listOf(toSymbolsAlt) +
                    listOf(sym("*"), sym("\""), sym("'"), sym(":"), sym(";"), sym("!"), sym("?")) +
                    listOf(delete),
            ),
            KeyRow(listOf(toAlpha, comma, space, period, enter)),
        ),
    )

    /**
     * Second symbols page (`=\<`), reached from the `=\<` key on [SymbolsEn].
     *
     * This is where the characters people go looking for and cannot find on the first page live:
     * the slash, the equals sign, the brackets and the currency symbols.
     */
    val SymbolsAltEn = KeyboardLayout(
        id = "symbols_alt_en",
        label = "Symbols",
        languageTag = "en",
        rows = listOf(
            KeyRow(
                listOf(
                    sym("~"), sym("`"), sym("|"), sym("•"), sym("√"),
                    sym("π"), sym("÷"), sym("×"), sym("§", "¶"), sym("∆"),
                ),
            ),
            KeyRow(
                listOf(
                    sym("£"), sym("¢"), sym("€"), sym("¥"), sym("^"),
                    sym("°"), sym("="), sym("{"), sym("}"), sym("\\"),
                ),
            ),
            KeyRow(
                listOf(toSymbols) +
                    listOf(sym("%", "‰"), sym("©"), sym("®"), sym("™"), sym("✓"), sym("["), sym("]")) +
                    listOf(delete),
            ),
            // The angle brackets flank the space bar here, exactly where Gboard keeps them.
            KeyRow(listOf(toAlpha, sym("<", "‹«"), space, sym(">", "›»"), enter)),
        ),
    )

    /**
     * The first symbols page on the honeycomb, opened by `?123`.
     *
     * Same characters as [SymbolsEn] and the same four rows as the letters board: ten cells, then
     * nine at the half-cell stagger, nine, and nine with three fused into the space bar. The counts
     * are the letters board's on purpose — a tenth cell on a staggered row would push the row half a
     * cell past the board it is drawn on.
     *
     * The stock page is what the `?123` key used to answer with, so the honeycomb disappeared
     * exactly when the user went looking for a number: the same board-swap the browser and email
     * fields had, in the place a person is most likely to meet it.
     *
     * Every key is one cell wide, as on the letters board, so the lattice holds. That is why the
     * first page carries nine of the stock page's ten top-row symbols rather than all ten: the
     * underscore moves to the second page, and the two rows of nine carry the rest in the same
     * order the stock page has them.
     */
    val HexSymbolsEn = KeyboardLayout(
        id = ID_HEX_SYMBOLS,
        label = "Symbols",
        languageTag = "en",
        supportsNumberRow = false,
        hexKeys = true,
        rows = listOf(
            KeyRow("1234567890".map { sym(it.toString()) }),
            KeyRow(
                listOf(
                    sym("@"), sym("#"), sym("$", "€£¥¢"), sym("&"),
                    sym("-", "–—~"), sym("+", "±"), sym("(", "[{<"), sym(")", "]}>"), sym("/"),
                ),
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
            KeyRow(
                listOf(toSymbolsAlt.copy(widthWeight = 1f)) +
                    listOf(sym("*"), sym("\""), sym("'"), sym(":"), sym(";"), sym("!"), sym("?")) +
                    listOf(delete.copy(widthWeight = 1f)),
            ),
            KeyRow(
                // Same nine cells, with the globe and the emoji key gone to the strip above. Space
                // takes what they left: a wide bar is no bad thing on the page where the letters
                // are the thing you are reaching back for.
                listOf(
                    toAlpha.copy(widthWeight = 1f),
                    comma,
                    space.copy(widthWeight = 5f),
                    period,
                    enter.copy(widthWeight = 1f),
                ),
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
        ),
    )

    /**
     * The second symbols page on the honeycomb, reached from `=\<` on [HexSymbolsEn].
     *
     * The characters people go looking for and cannot find on the first page: the slash, the equals
     * sign, the brackets and the currency symbols. The same rows as [SymbolsAltEn], on the
     * honeycomb's own cell counts, with the underscore the first page gave up kept here.
     */
    val HexSymbolsAltEn = KeyboardLayout(
        id = ID_HEX_SYMBOLS_ALT,
        label = "Symbols",
        languageTag = "en",
        supportsNumberRow = false,
        hexKeys = true,
        rows = listOf(
            KeyRow(
                listOf(
                    sym("~"), sym("`"), sym("|"), sym("•"), sym("√"),
                    sym("π"), sym("÷"), sym("×"), sym("§", "¶"), sym("\\"),
                ),
            ),
            KeyRow(
                listOf(
                    sym("£"), sym("¢"), sym("€"), sym("¥"), sym("^"),
                    sym("°"), sym("="), sym("{"), sym("}"),
                ),
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
            KeyRow(
                listOf(toSymbols.copy(widthWeight = 1f)) +
                    listOf(sym("%", "‰"), sym("©"), sym("_"), sym("™"), sym("✓"), sym("["), sym("]")) +
                    listOf(delete.copy(widthWeight = 1f)),
            ),
            KeyRow(
                listOf(
                    toAlpha.copy(widthWeight = 1f),
                    sym("<", "‹«"),
                    space.copy(widthWeight = 5f),
                    sym(">", "›»"),
                    enter.copy(widthWeight = 1f),
                ),
                HEX_ROW_OFFSET,
                HEX_ROW_OFFSET,
            ),
        ),
    )

    /** One symbols page per shape: the honeycomb's own when [hexKeys], the stock rectangles otherwise. */
    data class SymbolPages(val symbols: KeyboardLayout, val symbolsAlt: KeyboardLayout)

    /**
     * The two symbols pages for the shape in use.
     *
     * A board's shape is not a property of the symbols it types, so the pages are chosen by the
     * *shape* of the board on screen rather than fixed. Before this existed, `?123` and `=&#92;<`
     * answered every board with the stock rectangles, so the honeycomb vanished exactly when the
     * user went looking for a number, and the two shapes are otherwise the same characters in the
     * same order.
     */
    fun pagesFor(hexKeys: Boolean): SymbolPages =
        if (hexKeys) SymbolPages(HexSymbolsEn, HexSymbolsAltEn)
        else SymbolPages(SymbolsEn, SymbolsAltEn)

    private fun sym(s: String, alternates: String = "") =
        Key(s, KeyType.CHARACTER, alternates = alternates.map(Char::toString), gestureEligible = false)

    /**
     * The honeycomb's digit row, where a honeycomb can hold one.
     *
     * A digit row is a fifth row, and on the honeycomb a fifth row is not free. Portrait takes it:
     * that board is uncapped, so its block grows by a row and every key keeps its size. Landscape
     * does not, and both reasons are things already decided about that board. It is pinned to an
     * approved height its block is already at, so a fifth row could only be paid for by shortening
     * every row, which flattens the flat hexagon past its ratio and deforms the key shape that board
     * exists to avoid, or by raising that cap, the one direction the landscape board may not move.
     * So the digits are not added there, and the setting reads as what it is: a portrait setting.
     *
     * It lives here rather than in the view because it is the board's rule, and a rule written
     * inside a view is one no test can see.
     */
    fun withNumberRowForHoneycomb(
        layout: KeyboardLayout,
        enabled: Boolean,
        portrait: Boolean,
    ): KeyboardLayout = withNumberRow(layout, enabled && (portrait || !layout.hexKeys))

    /**
     * Inserts the digit row at the top when the setting is enabled.
     *
     * Which of the two rows is inserted is decided by the board, not by the setting: a honeycomb needs
     * [hexNumberRow] for the reasons written on it, and a rectangular board needs [numberRow]. Asking the
     * board rather than the caller keeps one answer to the question and means a board cannot be handed the
     * other shape's row by mistake.
     */
    fun withNumberRow(layout: KeyboardLayout, enabled: Boolean): KeyboardLayout {
        val row = if (layout.hexKeys) hexNumberRow else numberRow
        return if (!enabled || !layout.supportsNumberRow || layout.rows.firstOrNull() == row) {
            layout
        } else {
            layout.copy(rows = listOf(row) + layout.rows)
        }
    }

    /**
     * Every board one language needs, in the shape the input method asks for them.
     *
     * The symbols pages are deliberately absent: `?123` and `=&#92;<` are the same characters in
     * every language this keyboard ships, and duplicating them per language would be three more
     * boards to keep identical for no difference the user could see.
     */
    data class LanguageBoards(
        val qwerty: KeyboardLayout,
        val hex: KeyboardLayout,
        val email: KeyboardLayout,
        val uri: KeyboardLayout,
    )

    /**
     * The language-to-board table, the only thing that turns a resolved language into a board.
     *
     * Keyed by [KeyboardLanguages]'s own tags rather than by a copy of them so that a language
     * added to one and not the other cannot compile a board table that disagrees with the
     * resolver: [boardsFor] falls back to the entries under [KeyboardLanguages.FALLBACK].
     */
    /**
     * The same boards, stamped for the language they will be used in.
     *
     * A board copied from another language has to carry that language's own two names, and it is the
     * *label* that matters here rather than the tag. The space bar is the one place a keyboard says which
     * language it is — `KeyboardView.drawSpaceLabel` draws this label there and nothing else — so a copy
     * that renamed only its tag told the user, in the single place that is meant to answer the question,
     * that they were typing the language it was copied from. The name comes from the registry rather than
     * being spelled again here, so the board and the settings card say one thing.
     */
    private fun LanguageBoards.copiedFor(language: String): LanguageBoards {
        val name = KeyboardLanguages.entryFor(language).label
        fun KeyboardLayout.stamped() = copy(languageTag = language, label = name)
        return LanguageBoards(
            qwerty = qwerty.stamped(),
            hex = hex.stamped(),
            email = email.stamped(),
            uri = uri.stamped(),
        )
    }

    private val BOARDS: Map<String, LanguageBoards> = mapOf(
        KeyboardLanguages.FALLBACK to LanguageBoards(QwertyEn, HexQwertyEn, HexEmailEn, HexUriEn),
        KeyboardLanguages.SPANISH to LanguageBoards(QwertyEs, HexQwertyEs, HexEmailEs, HexUriEs),
        // Mandarin needs no board of its own. Pinyin is typed on the letters a person already knows
        // the positions of, so the English boards are the Chinese boards, and the difference between
        // the two languages lives in how a keystroke is handled rather than in where the keys are.
        // They are copies stamped for Chinese rather than the English boards themselves, because a
        // board declares the language it is drawn for — in its tag, which the resolver reads, and in
        // its label, which its space bar draws. A copy carrying the tag alone announced English on a
        // keyboard typing Chinese.
        KeyboardLanguages.CHINESE to LanguageBoards(QwertyEn, HexQwertyEn, HexEmailEn, HexUriEn)
            .copiedFor(KeyboardLanguages.CHINESE),
    )

    /**
     * The boards for [language], or the fallback language's when this build ships none for it.
     *
     * Unsupported input cannot come from a resolved tag, but it can come from a caller that never
     * resolved one, and a board with no dictionary behind it is worse than an English board.
     */
    fun boardsFor(language: String): LanguageBoards =
        BOARDS[language] ?: BOARDS.getValue(KeyboardLanguages.FALLBACK)
}
