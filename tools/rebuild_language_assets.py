#!/usr/bin/env python3
"""Rebuild every committed language asset from the authenticated source lock."""

from __future__ import annotations

import argparse
import bz2
import gzip
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

from fetch_language_sources import load_lock, materialize_source

ROOT = Path(__file__).resolve().parent.parent

# The emoji catalogue is language-independent: one asset serves every board.
EMOJI_SOURCE_KEYS = {
    "emoji": "unicode_emoji_test",
    "annotations": "cldr_annotations_en",
    "derived": "cldr_annotations_derived_en",
}

EMOJI_OUTPUT = "core/src/main/assets/emoji.bin"


@dataclass(frozen=True)
class LanguageBuild:
    """One language's locked sources and the shape of the assets built from them.

    [alphabet] is passed to both builders and is what keeps two languages' assets from being
    built to different rules by accident; see build_lexicon.py. The decompressed names are only
    scratch filenames, but they are here rather than inline so the two languages cannot collide
    in the shared temporary directory.
    """

    alphabet: str
    lexicon_key: str
    context_key: str
    lexicon_filename: str
    context_filename: str

    def outputs(self, language: str) -> dict[str, str]:
        """Every asset this language produces, mapped to the source role that feeds it."""
        return {
            f"engine/src/main/assets/lexicon_{language}.bin": "lexicon",
            f"engine/src/main/assets/bigrams_{language}.bin": "context",
            f"engine/src/main/assets/trigrams_{language}.bin": "context",
            f"engine/src/test/resources/heldout_{language}.txt": "context",
        }


LANGUAGES = {
    "en": LanguageBuild(
        alphabet="ascii",
        lexicon_key="aosp_english_wordlist",
        context_key="tatoeba_english_sentences",
        lexicon_filename="aosp-en-wordlist.combined",
        context_filename="tatoeba-eng-sentences.tsv",
    ),
    "es": LanguageBuild(
        alphabet="latin",
        lexicon_key="aosp_spanish_wordlist",
        context_key="tatoeba_spanish_sentences",
        lexicon_filename="aosp-es-wordlist.combined",
        context_filename="tatoeba-spa-sentences.tsv",
    ),
}


def expected_outputs() -> dict[str, tuple[str, str]]:
    """Every locked output, as (language, source role), for the plan above.

    The language is "" for the shared emoji asset. Kept as one mapping so a language added to
    [LANGUAGES] without a matching lock entry, or a lock entry no language produces, is a
    startup error rather than an asset that is silently never rebuilt.
    """
    outputs = {EMOJI_OUTPUT: ("", "emoji")}
    for language, build in LANGUAGES.items():
        for target, role in build.outputs(language).items():
            outputs[target] = (language, role)
    return outputs


def source_keys() -> dict[str, str]:
    """Source role -> lock key, across every language."""
    keys = dict(EMOJI_SOURCE_KEYS)
    for language, build in LANGUAGES.items():
        keys[f"{language}:lexicon"] = build.lexicon_key
        keys[f"{language}:context"] = build.context_key
    return keys


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def decompress(source: Path, target: Path, kind: str) -> None:
    opener = gzip.open if kind == "gzip" else bz2.open
    with opener(source, "rb") as compressed, target.open("wb") as plain:
        shutil.copyfileobj(compressed, plain, length=1024 * 1024)


def run(command: list[str]) -> None:
    subprocess.run(command, cwd=ROOT, check=True)


def replace_file(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    target_mode = stat.S_IMODE(target.stat().st_mode) if target.exists() else 0o644
    descriptor, temporary_name = tempfile.mkstemp(
        dir=target.parent, prefix=f".{target.name}."
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as handle, source.open("rb") as built:
            shutil.copyfileobj(built, handle, length=1024 * 1024)
            handle.flush()
            os.fsync(handle.fileno())
        os.chmod(temporary, target_mode)
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--check", action="store_true", help="verify committed assets (the default)"
    )
    mode.add_argument(
        "--write",
        action="store_true",
        help="replace committed assets after verification",
    )
    parser.add_argument(
        "--language",
        action="append",
        choices=["all", *sorted(LANGUAGES)],
        help="rebuild and verify only this language; repeat for more than one (default: every one)",
    )
    parser.add_argument(
        "--sources-dir",
        type=Path,
        help="use an archived set of locked source files instead of downloading retrieval hints",
    )
    args = parser.parse_args()

    try:
        lock = load_lock()
        plan = expected_outputs()
        # A language whose assets are not locked would be built and then thrown away; a locked
        # asset no language produces would never be rebuilt and never be verified. Both are
        # programming errors in this file, not build inputs, so they stop the run.
        if set(plan) != set(lock.generated_outputs):
            unbuilt = sorted(set(plan) - set(lock.generated_outputs))
            unlocked = sorted(set(lock.generated_outputs) - set(plan))
            raise ValueError(
                f"the source lock and the build plan disagree: unbuilt {unbuilt}, unlocked {unlocked}"
            )

        requested = args.language or ["all"]
        selected = set(LANGUAGES) if "all" in requested else set(requested)
        # The emoji catalogue is shared, so it is rebuilt and verified whichever language was
        # asked for rather than being rebuilt by whichever one happens to be first.
        targets = {
            target
            for target, (language, _) in plan.items()
            if language == "" or language in selected
        }

        with tempfile.TemporaryDirectory(
            prefix="slide-language-build-"
        ) as temporary_name:
            work = Path(temporary_name)
            source_dir = work / "sources"
            sources: dict[str, Path] = {}
            for role, key in source_keys().items():
                language = role.split(":", 1)[0]
                if language in LANGUAGES and language not in selected:
                    continue
                spec = lock.sources[key]
                local = (
                    args.sources_dir / spec.filename
                    if args.sources_dir is not None
                    else None
                )
                sources[role] = materialize_source(spec, source_dir, local)

            built = work / "built"
            outputs = {target: built / target for target in targets}
            run(
                [
                    sys.executable,
                    str(ROOT / "tools/build_emoji.py"),
                    "--emoji-test",
                    str(sources["emoji"]),
                    "--annotations",
                    str(sources["annotations"]),
                    "--derived",
                    str(sources["derived"]),
                    "--output",
                    str(outputs[EMOJI_OUTPUT]),
                ]
            )

            for language, build in LANGUAGES.items():
                if language not in selected:
                    continue
                # Each language decompresses its own copies: the builders take paths, and two
                # languages sharing one scratch file would be a race the day this is parallel.
                lexicon_text = work / build.lexicon_filename
                context_text = work / build.context_filename
                decompress(sources[f"{language}:lexicon"], lexicon_text, "gzip")
                decompress(sources[f"{language}:context"], context_text, "bzip2")

                language_outputs = build.outputs(language)
                if not set(language_outputs) <= targets:
                    raise ValueError(f"language {language} does not build every locked output")
                run(
                    [
                        sys.executable,
                        str(ROOT / "tools/build_lexicon.py"),
                        "--alphabet",
                        build.alphabet,
                        str(lexicon_text),
                        str(outputs[f"engine/src/main/assets/lexicon_{language}.bin"]),
                    ]
                )
                run(
                    [
                        sys.executable,
                        str(ROOT / "tools/build_bigrams.py"),
                        "--alphabet",
                        build.alphabet,
                        str(context_text),
                        str(outputs[f"engine/src/main/assets/lexicon_{language}.bin"]),
                        str(outputs[f"engine/src/main/assets/bigrams_{language}.bin"]),
                        str(outputs[f"engine/src/test/resources/heldout_{language}.txt"]),
                        str(outputs[f"engine/src/main/assets/trigrams_{language}.bin"]),
                    ]
                )
                built_targets = set(language_outputs)
                if not built_targets <= targets:
                    raise ValueError(f"language {language} did not build every locked output")

            for target_name in sorted(targets):
                expected_hash = lock.generated_outputs[target_name]
                actual_hash = sha256(outputs[target_name])
                if actual_hash != expected_hash:
                    raise ValueError(
                        f"rebuilt {target_name} has SHA-256 {actual_hash}, expected {expected_hash}"
                    )

            if args.write:
                for target_name in sorted(targets):
                    replace_file(outputs[target_name], ROOT / target_name)
                print(
                    f"Rebuilt and replaced the locked language assets for {', '.join(sorted(selected))}."
                )
            else:
                for target_name in sorted(targets):
                    committed = ROOT / target_name
                    if (
                        sha256(committed) != lock.generated_outputs[target_name]
                        or committed.read_bytes() != outputs[target_name].read_bytes()
                    ):
                        raise ValueError(
                            f"committed {target_name} differs from the reproducible build"
                        )
                print(
                    "All committed language assets reproduce exactly from the locked sources."
                )
    except (
        OSError,
        ValueError,
        KeyError,
        json.JSONDecodeError,
        subprocess.CalledProcessError,
    ) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
