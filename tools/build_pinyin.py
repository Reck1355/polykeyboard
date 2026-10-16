#!/usr/bin/env python3
"""Compile the pinyin engine's data assets. Deterministic, verifiable, no network at build time by default.

Sources (all public; licences recorded in the emitted PINYIN-LICENSES.md):
  * CC-CEDICT              - Chinese->English with pinyin, CC BY-SA 4.0
  * jieba's dict.txt       - word frequencies, MIT
  * OpenSubtitles2018 token counts
                           - how often words are actually SPOKEN, CC BY-SA 4.0
  * a Chinese text corpus  - word bigrams; Tatoeba sentences (CC BY 2.0) by
                             default, and any UTF-8 text file can be substituted

Why two frequency sources rather than one, measured on a fixed 40-case table
(see cache/scratch/pinyin_probe3.py and the gates file):

  * jieba's counts come from news text, where 在建 ("under construction", a
    roadside sign) outranks 再见 ("goodbye", a thing people say) - 444 against
    343. In subtitle-derived counts of spoken Chinese the same two words are
    62 against 28,825, a reversal of about 465x. The keyboard was therefore
    offering "under construction" for `zaijian`. Same shape for 星期 against
    兴起: 7,842 against 207, where jieba had it backwards.
  * dropping jieba entirely breaks the other direction: 北京 ("Beijing") falls
    behind 背景 ("background") because a subtitle corpus is thin on proper
    nouns. Keeping jieba as a minority share holds that up.

So the frequency column is a weighted blend, and the weight is measured rather than chosen: on a
fixed table of common inputs, news-only counts read 36 of 40 correctly, the blend at any weight from
0.5 to 0.9 reads 39, and spoken counts alone drop to 38 because 北京 loses to 背景. 0.7 sits in the
middle of the safe band.

Emits into engine/src/main/assets/pinyin/:
  syllables.txt   one legal syllable per line, sorted (derived from CC-CEDICT)
  dict.bin        word + pinyin key + frequency
  bigrams.bin     word pair + count, pruned to --bigrams

  --verify   rebuild into a temp dir and compare sha256 against what is on disk.

The build is byte-for-byte reproducible: every container is sorted before it is
written and nothing timestamped goes in.
"""
from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
import os
import re
import struct
import sys
from collections import Counter, defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
OUT = os.path.join(REPO, "engine", "src", "main", "assets", "pinyin")

CEDICT_URL = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz"
JIEBA_URL = "https://raw.githubusercontent.com/fxsjy/jieba/master/jieba/dict.txt"
TATOEBA_URL = "https://downloads.tatoeba.org/exports/per_language/cmn/cmn_sentences.tsv.bz2"
# OpenSubtitles2018 token counts, content CC BY-SA 4.0 (hermitdave/FrequencyWords).
SUBTITLES_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/zh_cn/zh_cn_full.txt"

# The measured weight of spoken usage in the frequency column, and its complement
# for the news-leaning dictionary. See the module docstring for the numbers.
SPOKEN_WEIGHT = 0.7

CJK = re.compile(r"^[\u4e00-\u9fff]+$")
ENTRY = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/$")

MAGIC = b"PYEN"
VERSION = 1


def tone_strip(syllable: str) -> str:
    """`ni3` -> `ni`, `lu:4` -> `lv`, `nu:3` -> `nv`, `r5` -> `r`."""
    s = syllable.lower().replace("u:", "v").replace("\u00fc", "v")
    return re.sub(r"[0-9]", "", s)


def need(path: str, url: str, timeout: int = 300) -> str:
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        print(f"fetching {url}")
        import urllib.request

        urllib.request.urlretrieve(url, path)
    return path


def load_cedict(path: str):
    by_pinyin: dict[str, list[tuple[str, tuple[str, ...]]]] = defaultdict(list)
    syllables: Counter[str] = Counter()
    kept = skipped = 0
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("#"):
                continue
            m = ENTRY.match(line.rstrip("\r\n"))
            if not m:
                skipped += 1
                continue
            _trad, simp, pinyin, _defs = m.groups()
            if not CJK.match(simp):
                skipped += 1
                continue
            parts = [tone_strip(p) for p in pinyin.split()]
            if not all(parts):
                skipped += 1
                continue
            for p in parts:
                syllables[p] += 1
            by_pinyin["".join(parts)].append((simp, tuple(parts)))
            kept += 1
    return by_pinyin, syllables, kept, skipped


def load_frequencies(path: str) -> dict[str, int]:
    freq: dict[str, int] = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            bits = line.split()
            if len(bits) >= 2 and CJK.match(bits[0]):
                try:
                    freq[bits[0]] = int(bits[1])
                except ValueError:
                    pass
    return freq


def tokenise(freq: dict[str, int]):
    maxlen = max(len(w) for w in freq)

    def cut(text: str) -> list[str]:
        out: list[str] = []
        i, n = 0, len(text)
        while i < n:
            hit = None
            for ln in range(min(maxlen, n - i), 0, -1):
                cand = text[i:i + ln]
                if cand in freq:
                    hit = cand
                    break
            if hit is None:
                i += 1
            else:
                out.append(hit)
                i += len(hit)
        return out

    return cut


def load_counts(path: str) -> dict[str, int]:
    """`word count` per line, the shape both jieba's dict and the subtitle lists use."""
    counts: dict[str, int] = {}
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            bits = line.split()
            if len(bits) >= 2:
                try:
                    counts[bits[0]] = int(bits[1])
                except ValueError:
                    pass
    return counts


def blend_frequencies(dict_freq: dict[str, int], spoken_freq: dict[str, int], spoken_weight: float) -> dict[str, int]:
    """Weighted mix of two frequency tables, returned as integer counts.

    The count column is only ever read as a relative weight, so what matters is the ratio between
    words, not the absolute scale; both sides are normalised to a probability first so that a
    corpus three times larger cannot simply shout louder. Words the spoken table has never seen
    keep their dictionary weight scaled down (that is the `(1 - w)` term), and words only the
    spoken table knows are admitted at `w` of their own share - which is how 再见 gets in at all.
    """
    n_dict = sum(dict_freq.values()) or 1
    n_spoken = sum(spoken_freq.values()) or 1
    out: dict[str, int] = {}
    for word, count in dict_freq.items():
        p = (1.0 - spoken_weight) * (count / n_dict)
        if word in spoken_freq:
            p += spoken_weight * (spoken_freq[word] / n_spoken)
        out[word] = max(1, int(round(p * 1e9)))
    for word, count in spoken_freq.items():
        if word not in out:
            out[word] = max(1, int(round(spoken_weight * (count / n_spoken) * 1e9)))
    return out


def load_corpus(path: str) -> str:
    if path.endswith(".bz2"):
        return bz2.open(path, "rt", encoding="utf-8", errors="replace").read()
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def build_bigrams(text: str, freq: dict[str, int], keep: int) -> Counter:
    cut = tokenise(freq)
    pairs: Counter = Counter()
    for line in text.splitlines():
        bits = line.split("\t")
        body = bits[2] if len(bits) >= 3 else line
        words = cut(body)
        for a, b in zip(words, words[1:]):
            pairs[(a, b)] += 1
    return Counter(dict(pairs.most_common(keep)))


def write_strings(records, path: str) -> None:
    """records: sorted list of (key:str, word:str, count:int).

    Layout, little-endian throughout: `PYEN` + version(u32) + count(u32), then per record
    keyLength(u16) + wordLength(u16) + count(u32) + key bytes + word bytes. One packed group per
    record so the Kotlin reader has a single shape to match rather than two.
    """
    with open(path, "wb") as fh:
        fh.write(MAGIC + struct.pack("<II", VERSION, len(records)))
        for key, word, count in records:
            kb = key.encode("utf-8")
            wb = word.encode("utf-8")
            fh.write(struct.pack("<HHI", len(kb), len(wb), count))
            fh.write(kb)
            fh.write(wb)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=os.path.join(os.path.expanduser("~"), "cache", "scratch", "pinyin-data"))
    ap.add_argument("--corpus", default=None, help="any UTF-8 (or .bz2) text file; default Tatoeba Chinese")
    ap.add_argument("--subtitles", default=None, help="word-per-line token counts for SPOKEN frequency; default OpenSubtitles zh_cn")
    ap.add_argument("--spoken-weight", type=float, default=SPOKEN_WEIGHT, help="share of the frequency column taken from spoken usage, 0 to 1")
    ap.add_argument("--bigrams", type=int, default=250_000)
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--verify", action="store_true", help="rebuild to a temp dir and compare")
    args = ap.parse_args()

    out = args.out
    if args.verify:
        import tempfile

        out = tempfile.mkdtemp(prefix="pinyin-verify-")
    os.makedirs(out, exist_ok=True)

    cedict = need(os.path.join(args.data, "cedict.txt.gz"), CEDICT_URL)
    jieba = need(os.path.join(args.data, "jieba-dict.txt"), JIEBA_URL)
    subtitles = args.subtitles or need(os.path.join(args.data, "subtitles_zh_cn_full.txt"), SUBTITLES_URL)
    corpus_path = args.corpus or need(os.path.join(args.data, "cmn_sentences.tsv.bz2"), TATOEBA_URL)

    by_pinyin, syllables, kept, skipped = load_cedict(cedict)
    dict_freq = load_frequencies(jieba)
    text = load_corpus(corpus_path)
    # Bigrams are still tokenised against the dictionary's own vocabulary: that combination is the
    # one the table was measured with, and widening the tokeniser's vocabulary changes segmentation
    # and therefore every pair count. Not a thing to slip in beside a frequency change.
    bigrams = build_bigrams(text, dict_freq, args.bigrams)
    spoken_freq = load_counts(subtitles)
    freq = blend_frequencies(dict_freq, spoken_freq, args.spoken_weight)

    print(f"cedict entries kept {kept} (skipped {skipped}); distinct words {len({w for v in by_pinyin.values() for w, _ in v})}")
    print(f"syllables {len(syllables)}; dictionary frequencies {len(dict_freq)}; spoken frequencies {len(spoken_freq)}")
    print(f"blended frequencies {len(freq)} at {args.spoken_weight:.2f} spoken / {1 - args.spoken_weight:.2f} news")
    print(f"bigrams {len(bigrams)}; corpus chars {len(text)}")

    with open(os.path.join(out, "syllables.txt"), "w", encoding="utf-8", newline="\n") as fh:
        for s in sorted(syllables):
            fh.write(s + "\n")

    dict_records = []
    for key, words in by_pinyin.items():
        for word, _parts in words:
            dict_records.append((key, word, freq.get(word, 0)))
    # Key first, then frequency DESCENDING, then the word to break ties. The reader relies on the
    # second key: it takes the leading words of a key group as that key's best readings, so a plain
    # alphabetical sort would hand it arbitrary words and 爱 would never reach the decoder after 我.
    dict_records.sort(key=lambda r: (r[0], -r[2], r[1]))
    write_strings(dict_records, os.path.join(out, "dict.bin"))

    pair_records = sorted((a, b, c) for (a, b), c in bigrams.items())
    write_strings(pair_records, os.path.join(out, "bigrams.bin"))

    licences = f"""# Pinyin engine data: sources, licences, obligations

Every file the engine loads at runtime is built from the sources below by
`tools/build_pinyin.py`, which is deterministic: two runs produce byte-identical
assets. `--verify` rebuilds them into a temp directory and compares.

| file | built from | licence | obligation |
|---|---|---|---|
| `dict.bin`, `syllables.txt` | CC-CEDICT (`cedict_1_0_ts_utf-8_mdbg.txt.gz`, mdbg.net) | **CC BY-SA 4.0** | credit CC-CEDICT in the app's About, and keep any redistributed copy of the *data* under CC BY-SA |
| `dict.bin` (frequency column) | OpenSubtitles2018 token counts (`zh_cn_full.txt`, github.com/hermitdave/FrequencyWords) | **CC BY-SA 4.0** | credit OpenSubtitles/FrequencyWords in the app's About; share-alike on the redistributed *data* |
| `dict.bin` (frequency column) | jieba `dict.txt` (github.com/fxsjy/jieba), at {1 - SPOKEN_WEIGHT:.2f} weight | **MIT** | keep the copyright notice with redistributed data |
| `bigrams.bin` | {os.path.basename(corpus_path)} | **CC BY 2.0** (Tatoeba) if the default corpus; otherwise the corpus's own | credit Tatoeba; the derived bigram counts are a share-alike derivative of the corpus |
| `syllables.txt` | derived from CC-CEDICT, not typed by hand | as above | none beyond the above |

The frequency column mixes two sources on purpose, with the weight measured rather than chosen:
a keyboard has to rank `zaijian` as 再见 ("goodbye") and not 在建 ("under construction"), and
spoken-usage counts are the only ones that know that. The mixture is
{SPOKEN_WEIGHT:.2f} OpenSubtitles / {1 - SPOKEN_WEIGHT:.2f} jieba; see `tools/build_pinyin.py`'s
module docstring for the measurement.

Nothing here restricts the application's own source code: these are data
obligations (attribution, share-alike on the data), and Apache-2.0 code may
carry CC BY-SA data. The attribution text must exist in the app's resources
even before the UI that shows it lands - see gate G9.
"""
    with open(os.path.join(out, "PINYIN-LICENSES.md"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(licences)

    print("\nassets:")
    for name in sorted(os.listdir(out)):
        p = os.path.join(out, name)
        h = hashlib.sha256(open(p, "rb").read()).hexdigest()
        print(f"  {name:<20} {os.path.getsize(p):>10,} B  {h}")

    if args.verify:
        ok = True
        for name in sorted(os.listdir(out)):
            a, b = os.path.join(out, name), os.path.join(OUT, name)
            if not os.path.exists(b) or open(a, "rb").read() != open(b, "rb").read():
                print(f"VERIFY FAIL: {name} differs from the shipped asset")
                ok = False
        print("VERIFY OK: rebuilt assets are byte-identical to the shipped ones" if ok else "VERIFY FAILED")
        return 0 if ok else 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
