# Pinyin engine data: sources, licences, obligations

Every file the engine loads at runtime is built from the sources below by
`tools/build_pinyin.py`, which is deterministic: two runs produce byte-identical
assets. `--verify` rebuilds them into a temp directory and compares.

| file | built from | licence | obligation |
|---|---|---|---|
| `dict.bin`, `syllables.txt` | CC-CEDICT (`cedict_1_0_ts_utf-8_mdbg.txt.gz`, mdbg.net) | **CC BY-SA 4.0** | credit CC-CEDICT in the app's About, and keep any redistributed copy of the *data* under CC BY-SA |
| `dict.bin` (frequency column) | jieba `dict.txt` (github.com/fxsjy/jieba) | **MIT** | keep the copyright notice with redistributed data |
| `bigrams.bin` | cmn_sentences.tsv.bz2 | **CC BY 2.0** (Tatoeba) if the default corpus; otherwise the corpus's own | credit Tatoeba; the derived bigram counts are a share-alike derivative of the corpus |
| `syllables.txt` | derived from CC-CEDICT, not typed by hand | as above | none beyond the above |

Nothing here restricts the application's own source code: these are data
obligations (attribution, share-alike on the data), and Apache-2.0 code may
carry CC BY-SA data. The attribution text must exist in the app's resources
even before the UI that shows it lands - see gate G9.
