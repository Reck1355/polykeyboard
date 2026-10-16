# Tatoeba source snapshots

`tatoeba-eng-sentences-2026-08-08.tsv.bz2` is the exact English per-language export used to build
Slide's committed English bigram/trigram models and held-out evaluation sample. It is tracked because
Tatoeba's official download URL rotates in place and does not offer dated archives.

`tatoeba-spa-sentences-2026-10-10.tsv.bz2` is the same thing for Spanish, fetched and snapshotted the
same way. Both are per-language exports from `https://downloads.tatoeba.org/exports/per_language/`,
so they are separate files rather than one shared corpus: a Spanish model built over English
sentences would be an English model wearing a Spanish name.

Each file's byte length, SHA-256, origin timestamp, generated outputs, and rebuild procedure are
locked in `tools/language_sources.json` and documented in `docs/data-provenance.md`. The sentences
remain copyright their individual Tatoeba contributors and are redistributed under CC BY 2.0 FR;
Slide's packaged third-party notices contain the attribution and licence link.
