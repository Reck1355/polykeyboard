#!/usr/bin/env python3
"""Check a Polykeyboard artifact against the contract of the tier it claims to be.

Two builds ship from this tree and they differ in exactly one way: what data they carry.

  full  everything  - all three languages, on-device dictation with the 181 MiB Whisper model
  slim  English only - no speech model, no speech libraries, no Spanish data

Both are the same keyboard. The tier never changes behaviour, only what is present, and the code
reads a flag at runtime so that a control whose data is absent is not drawn at all.

This script exists because "we stripped it" is a claim and the artifact is the evidence. It is
deliberately able to fail in both directions: it rejects a slim build that still carries the model
(which is what a stale build directory produces, and what a size comparison alone would miss,
since an APK rebuilt without the model is smaller but an APK rebuilt from a stale merged-assets
directory is not), and it rejects a full build that lost the model, dictation libraries, or the
Spanish and Mandarin data.

Usage:
    tools/verify_polykeyboard_tiers.py full <apk>
    tools/verify_polykeyboard_tiers.py slim <apk>

Exit code is the number of contract violations.
"""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path

SPEECH_MODEL = "assets/ggml-small.en-q5_1.bin"
DICTATION_LIBS = ("libggml", "libwhisper", "libslide_asr")
SPANISH = ("lexicon_es.bin", "bigrams_es.bin", "trigrams_es.bin")
ENGLISH = ("lexicon_en.bin", "bigrams_en.bin", "trigrams_en.bin")
MANDARIN = ("pinyin/dict.bin", "pinyin/bigrams.bin")
SWIPE = "assets/swipe/encoder.pte"
EMOJI = "assets/emoji.bin"


def names(apk: Path) -> list[str]:
    with zipfile.ZipFile(apk) as z:
        return z.namelist()


def check(tier: str, apk: Path) -> list[str]:
    n = names(apk)
    problems: list[str] = []

    def present(*needles: str) -> list[str]:
        return [x for x in n if any(needle in x for needle in needles)]

    if tier == "slim":
        if present(SPEECH_MODEL):
            problems.append(f"slim must not carry the speech model, found {SPEECH_MODEL}")
        libs = present(*DICTATION_LIBS)
        if libs:
            problems.append(f"slim must not carry a speech library, found {libs[:3]}")
        for name in SPANISH:
            if present(name):
                problems.append(f"slim must not carry Spanish data, found {name}")
        for name in MANDARIN:
            if present(name):
                problems.append(f"slim must not carry Mandarin data, found {name}")
    else:
        if not present(SPEECH_MODEL):
            problems.append("full must carry the speech model and does not")
        for lib in DICTATION_LIBS:
            if not present(lib):
                problems.append(f"full must carry the {lib} library and does not")
        for name in SPANISH + MANDARIN:
            if not present(name):
                problems.append(f"full must carry {name} and does not")

    # Both tiers type: the keyboard is the same keyboard.
    for name in ENGLISH:
        if not present(name):
            problems.append(f"{tier} must keep the English data, missing {name}")
    if not present(SWIPE):
        problems.append(f"{tier} must keep the swipe encoder, without it gesture typing dies")
    if not present(EMOJI):
        problems.append(f"{tier} must keep the emoji data")
    return problems


def main() -> int:
    if len(sys.argv) != 3 or sys.argv[1] not in ("full", "slim"):
        print(__doc__)
        return 2
    tier, apk = sys.argv[1], Path(sys.argv[2])
    if not apk.is_file():
        print(f"no such artifact: {apk}")
        return 2
    size = apk.stat().st_size
    problems = check(tier, apk)
    print(f"{apk.name}: {size:,} bytes ({size / 1048576:.1f} MiB) claiming tier '{tier}'")
    for p in problems:
        print(f"  VIOLATION {p}")
    if not problems:
        print(f"  OK {tier} contract holds")
    return len(problems)


if __name__ == "__main__":
    raise SystemExit(main())
