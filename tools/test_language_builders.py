#!/usr/bin/env python3
"""Tests for the builders' per-language alphabets, and for English not moving.

The two alphabets are the whole difference between the English assets and the Spanish ones, and
both ways of getting them wrong are silent. Too narrow, and a language quietly loses the accented
words it is written with -- `año` becomes no word at all, and the corrector simply never offers it.
Too wide, and entries the lexicon loader rejects at startup get packed into the asset, which turns
into a keyboard with no dictionary rather than a build error.

So: the lexicon builder is checked against a wordlist that has both plain and accented forms, and
the invariant that actually protects English is asserted directly -- byte for byte, an ASCII
wordlist encodes to the same asset under either alphabet.
"""

from __future__ import annotations

import hashlib
import struct
import tempfile
import unittest
from pathlib import Path

import build_bigrams
import build_lexicon

WORDS = """ word=casa,f=200,flags=
 word=año,f=180,flags=
 word=Árbol,f=120,flags=
 word=está,f=160,flags=
 word=computer,f=190,flags=
 word=niño,f=90,flags=abbreviation
 word=pingüino,f=40,flags=
 word=3d,f=10,flags=
 word=casa-de,f=5,flags=
 word=dont,f=7,flags=nonword
"""


def write(text: str) -> Path:
    handle = tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8")
    handle.write(text)
    handle.close()
    return Path(handle.name)


def read_lexicon(path: Path) -> list[str]:
    """The front-coded reader, mirroring LexiconLoader's own decode."""
    data = path.read_bytes()
    assert data[:4] == build_lexicon.MAGIC, data[:4]
    count, block_length, char_count = struct.unpack(">III", data[5:17])
    block = data[17 : 17 + block_length]
    words: list[str] = []
    previous = ""
    read = 0
    for _ in range(count):
        shared, suffix_length = block[read], block[read + 1]
        read += 2
        word = previous[:shared] + block[read : read + suffix_length].decode("utf-8")
        read += suffix_length
        words.append(word)
        previous = word
    assert read == block_length
    assert char_count == sum(len(word) for word in words)
    return words


class LexiconAlphabetTest(unittest.TestCase):
    def test_ascii_alphabet_keeps_only_unaccented_ascii_words(self) -> None:
        entries = build_lexicon.parse(write(WORDS), "ascii")
        # "año", "Árbol", "está", "niño" and "pingüino" are outside the alphabet; "3d" and
        # "casa-de" are not words at all, and "dont" is flagged nonword in the source.
        self.assertEqual(["casa", "computer"], [word for word, _, _ in entries])
        self.assertEqual([], [word for word, _, flags in entries if flags & build_lexicon.FLAG_CAPITALIZED])

    def test_latin_alphabet_keeps_the_accented_forms_spanish_is_written_with(self) -> None:
        entries = build_lexicon.parse(write(WORDS), "latin")
        words = [word for word, _, _ in entries]
        # Stored lowercase and ordered by code point, which is the order both the loader and the
        # decoder's binary search assume.
        self.assertEqual(
            ["año", "casa", "computer", "está", "niño", "pingüino", "árbol"], words
        )
        self.assertEqual(words, sorted(words))

    def test_capitals_are_folded_and_remembered_rather_than_dropped(self) -> None:
        entries = dict((word, flags) for word, _, flags in build_lexicon.parse(write(WORDS), "latin"))
        self.assertIn("árbol", entries)
        self.assertTrue(entries["árbol"] & build_lexicon.FLAG_CAPITALIZED)
        self.assertFalse(entries["casa"] & build_lexicon.FLAG_CAPITALIZED)

    def test_the_accented_words_survive_the_round_trip_as_utf8(self) -> None:
        target = Path(tempfile.mkdtemp()) / "lexicon_es.bin"
        entries = build_lexicon.parse(write(WORDS), "latin")
        target.write_bytes(build_lexicon.encode(entries))
        self.assertEqual(
            ["año", "casa", "computer", "está", "niño", "pingüino", "árbol"],
            read_lexicon(target),
        )

    def test_an_ascii_wordlist_encodes_byte_for_byte_under_either_alphabet(self) -> None:
        """The English assets do not move: the alphabet is a filter, not an encoding."""
        ascii_only = """ word=computer,f=190,flags=\n word=dont,f=7,flags=\n"""
        source = write(ascii_only)
        by_ascii = build_lexicon.encode(build_lexicon.parse(source, "ascii"))
        by_latin = build_lexicon.encode(build_lexicon.parse(source, "latin"))
        self.assertEqual(by_ascii, by_latin)
        self.assertEqual(
            hashlib.sha256(by_ascii).hexdigest(), hashlib.sha256(by_latin).hexdigest()
        )

    def test_an_unknown_alphabet_is_refused_rather_than_guessed(self) -> None:
        source = write(WORDS)
        with self.assertRaises(SystemExit):
            build_lexicon.main(["--alphabet", "klingon", str(source), "/dev/null"])

    def test_apostrophes_stay_inside_words_in_both_alphabets(self) -> None:
        source = write(" word=don't,f=190,flags=\n")
        for alphabet in ("ascii", "latin"):
            entries = build_lexicon.parse(source, alphabet)
            self.assertEqual(["don't"], [word for word, _, _ in entries], alphabet)


class BigramAlphabetTest(unittest.TestCase):
    def test_the_latin_tokenizer_keeps_accented_words_whole(self) -> None:
        sentence = "el niño está aquí, pingüino"
        self.assertEqual(
            ["el", "niño", "está", "aquí", "pingüino"],
            build_bigrams.TOKEN_RE["latin"].findall(sentence.lower()),
        )
        # The same split is what the English alphabet would do to Spanish, and it is why the two
        # cannot share one tokenizer.
        self.assertNotIn("está", build_bigrams.TOKEN_RE["ascii"].findall(sentence.lower()))

    def test_the_fingerprint_is_the_low_byte_of_each_character(self) -> None:
        """The Kotlin loader hashes `.code.toByte()`; Latin-1 is that byte for every letter shipped."""
        words = ["año", "casa", "está"]
        expected = hashlib.sha256(
            b"".join(
                bytes(ord(character) & 0xFF for character in word) + b"\0" for word in words
            )
        ).hexdigest()
        self.assertEqual(expected, build_bigrams.lexicon_fingerprint(words, "latin").hex())

    def test_english_fingerprints_are_unchanged_by_the_second_alphabet(self) -> None:
        words = ["a", "casa", "computer", "don't"]
        self.assertEqual(
            build_bigrams.lexicon_fingerprint(words, "ascii"),
            build_bigrams.lexicon_fingerprint(words, "latin"),
        )

    def test_a_fingerprint_cannot_be_computed_for_a_word_outside_its_alphabet(self) -> None:
        with self.assertRaises(SystemExit):
            build_bigrams.lexicon_fingerprint(["año"], "ascii")

    def test_the_fingerprint_covers_the_word_order_the_indices_address(self) -> None:
        """Pair indices are positions in the list, so the same words in another order are another model."""
        spanish = build_lexicon.parse(write(WORDS), "latin")
        names = [word for word, _, _ in spanish]
        self.assertNotEqual(
            build_bigrams.lexicon_fingerprint(names, "latin"),
            build_bigrams.lexicon_fingerprint(list(reversed(names)), "latin"),
        )

    def test_the_pair_indices_address_the_lexicon_that_was_actually_built(self) -> None:
        spanish = build_lexicon.parse(write(WORDS), "latin")
        words = [word for word, _, _ in spanish]
        index_of = {word: index for index, word in enumerate(words)}
        pairs = build_bigrams.count_pairs(["casa niño está"], index_of, "latin")
        self.assertEqual({index_of["niño"]: 1}, dict(pairs[index_of["casa"]]))


if __name__ == "__main__":
    unittest.main()
