package com.slide.engine.lexicon

import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads the packed lexicon asset produced by `tools/build_lexicon.py`.
 *
 * The asset is front-coded: each entry stores only the suffix that differs from the entry before
 * it. That halves the file, and costs nothing to undo because we read straight through once.
 *
 * See the script's docstring for the byte layout.
 */
object LexiconLoader {

    /** The language the packaged English assets are named for; see [assetNameFor]. */
    const val LANGUAGE_DEFAULT = "en"

    /** The English lexicon, for the places that must name an asset without holding a language. */
    const val ASSET_NAME = "lexicon_en.bin"

    /**
     * The lexicon asset for [language] — `lexicon_es.bin` for Spanish, `lexicon_en.bin` for English.
     *
     * The name is the only thing that varies by language; the format is identical, so the same
     * reader serves every language this build ships. [language] is expected to be a resolved tag
     * from `KeyboardLanguages`, not a raw subtype tag.
     */
    fun assetNameFor(language: String) = "lexicon_$language.bin"

    private const val TAG = "SlideLexicon"
    private const val MAGIC = 0x534C4558 // "SLEX"
    private const val SUPPORTED_VERSION = 1

    /** The English lexicon, for callers with no language of their own. */
    fun load(context: Context): Lexicon? = load(context, LANGUAGE_DEFAULT)

    /**
     * Loads the lexicon for [language], or returns null if the asset is missing or unreadable.
     *
     * Null is a survivable outcome: the keyboard still types, it just cannot decode gestures.
     * That is a much better failure than refusing to open at all — which is also why every
     * [Exception] is caught rather than [IOException] alone. A file of the right length holding
     * the wrong bytes fails an index check, not a read, and this is called from an IME coroutine
     * with nothing above it to catch anything. Errors (out of memory and friends) still propagate.
     */
    fun load(context: Context, language: String): Lexicon? {
        val name = assetNameFor(language)
        return try {
            context.assets.open(name).use(::read)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read $name; gesture typing will be unavailable", e)
            null
        }
    }

    fun read(input: InputStream): Lexicon {
        val data = DataInputStream(input.buffered(BUFFER_SIZE))

        val magic = data.readInt()
        if (magic != MAGIC) {
            throw IOException("Not a Slide lexicon: magic was 0x${magic.toString(16)}")
        }
        val version = data.readUnsignedByte()
        if (version != SUPPORTED_VERSION) {
            throw IOException("Lexicon version $version, expected $SUPPORTED_VERSION")
        }

        val wordCount = data.readInt()
        val blockLength = data.readInt()
        // Front coding shrinks the file but expands on decode, so the character count cannot be
        // derived from the block length; the writer records it for us.
        val charCount = data.readInt()
        if (
            wordCount !in 1..MAX_WORD_COUNT ||
            blockLength !in 1..MAX_BLOCK_LENGTH ||
            charCount !in wordCount..MAX_CHAR_COUNT
        ) {
            throw IOException("Lexicon header claims $wordCount words, $blockLength bytes, $charCount chars")
        }

        val block = ByteArray(blockLength)
        data.readFully(block)

        val chars = CharArray(charCount)
        val offsets = IntArray(wordCount + 1)

        var read = 0
        var written = 0
        for (index in 0 until wordCount) {
            offsets[index] = written

            // The two length bytes and the suffix they introduce all have to be inside the block,
            // and the word they build has to fit the character count the header promised. Every
            // one of these is an index into an array on the next line, so a corrupt file that
            // happens to be the right length would otherwise crash the keyboard rather than
            // disable gesture typing.
            if (read + 2 > blockLength) {
                throw IOException("Entry $index starts past the end of the block")
            }
            val shared = block[read].toInt() and 0xFF
            val suffixLength = block[read + 1].toInt() and 0xFF
            read += 2
            if (read + suffixLength > blockLength) {
                throw IOException("Entry $index claims a $suffixLength-byte suffix past the block")
            }

            // The shared prefix is carried forward from the previous word, which sits immediately
            // behind us in the same buffer. For the first entry that span is empty, so a non-zero
            // prefix there is caught by the same check.
            val previousStart = if (index == 0) 0 else offsets[index - 1]
            if (shared > written - previousStart) {
                // A prefix longer than the word it refers to means the file is corrupt. Failing
                // here beats silently emitting garbage words into the user's messages.
                throw IOException("Entry $index claims a $shared-char prefix of a shorter word")
            }
            if (written + shared + suffixLength > charCount) {
                throw IOException("Entry $index runs past the $charCount characters the header declared")
            }

            for (i in 0 until shared) {
                chars[written++] = chars[previousStart + i]
            }
            // Words are UTF-8 (see build_lexicon.py): one byte per letter in English, and a two-byte
            // sequence for each accented letter in Spanish. Decoded by hand rather than through
            // String, because these characters are written into one shared CharArray — 236k words
            // is a lot of garbage to hand the collector on a keyboard's startup path.
            var position = 0
            while (position < suffixLength) {
                val byte = block[read + position].toInt() and 0xFF
                val code: Int
                val width: Int
                when {
                    byte < 0x80 -> {
                        code = byte
                        width = 1
                    }
                    byte in 0xC2..0xDF && position + 1 < suffixLength -> {
                        code = ((byte and 0x1F) shl 6) or continuation(block[read + position + 1], index)
                        width = 2
                    }
                    byte in 0xE0..0xEF && position + 2 < suffixLength -> {
                        code = ((byte and 0x0F) shl 12) or
                            (continuation(block[read + position + 1], index) shl 6) or
                            continuation(block[read + position + 2], index)
                        width = 3
                    }
                    else -> throw IOException("Entry $index contains invalid byte 0x${byte.toString(16)}")
                }
                val decoded = code.toChar()
                if (!isWordLetter(decoded)) {
                    throw IOException("Entry $index contains invalid byte 0x${byte.toString(16)}")
                }
                chars[written++] = decoded
                position += width
            }
            read += suffixLength

            val currentStart = offsets[index]
            if (written == currentStart || !isWordStart(chars[currentStart])) {
                throw IOException("Entry $index is not a non-empty lowercase word")
            }
            if (
                index > 0 &&
                compareWords(chars, offsets[index - 1], currentStart, currentStart, written) >= 0
            ) {
                throw IOException("Entry $index is not strictly ordered after entry ${index - 1}")
            }
        }
        offsets[wordCount] = written
        if (read != blockLength) {
            throw IOException("Lexicon word block has ${blockLength - read} unused bytes")
        }
        if (written != charCount) {
            throw IOException("Lexicon decoded $written characters, expected $charCount")
        }

        val frequencies = ByteArray(wordCount)
        data.readFully(frequencies)
        val flags = ByteArray(wordCount)
        data.readFully(flags)
        for ((index, flag) in flags.withIndex()) {
            val unknown = (flag.toInt() and 0xFF) and KNOWN_FLAGS.inv()
            if (unknown != 0) throw IOException("Entry $index has unknown flags 0x${unknown.toString(16)}")
        }
        if (data.read() != -1) throw IOException("Lexicon has trailing data")

        return Lexicon(chars, offsets, frequencies, flags)
    }

    private fun compareWords(
        chars: CharArray,
        leftStart: Int,
        leftEnd: Int,
        rightStart: Int,
        rightEnd: Int,
    ): Int {
        val shared = minOf(leftEnd - leftStart, rightEnd - rightStart)
        for (position in 0 until shared) {
            val difference = chars[leftStart + position] - chars[rightStart + position]
            if (difference != 0) return difference
        }
        return (leftEnd - leftStart) - (rightEnd - rightStart)
    }

    /** The second or third byte of a UTF-8 sequence, as the six bits it carries. */
    private fun continuation(byte: Byte, index: Int): Int {
        val value = byte.toInt() and 0xFF
        if (value !in 0x80..0xBF) {
            throw IOException("Entry $index contains invalid UTF-8 continuation byte 0x${value.toString(16)}")
        }
        return value and 0x3F
    }

    /**
     * Whether [character] may appear inside a packed word.
     *
     * Exactly the lowercase letters the two build alphabets admit — Basic Latin, plus the Latin-1
     * letters with the multiplication and division signs that sit inside that range left out — and
     * the apostrophe. Nothing wider: a corrupt file that decodes to Cyrillic or Greek must fail
     * here rather than reach a candidate list.
     */
    private fun isWordLetter(character: Char): Boolean =
        character in 'a'..'z' ||
            character in '\u00e0'..'\u00f6' ||
            character in '\u00f8'..'\u00ff' ||
            character == '\''

    /** A word's first character: [isWordLetter] minus the apostrophe, which cannot open a word. */
    private fun isWordStart(character: Char): Boolean =
        character != '\'' && isWordLetter(character)

    private const val BUFFER_SIZE = 1 shl 16
    private const val MAX_WORD_COUNT = 2_000_000
    private const val MAX_BLOCK_LENGTH = 32 * 1024 * 1024
    private const val MAX_CHAR_COUNT = 32 * 1024 * 1024
    private const val KNOWN_FLAGS = 0x07
}
