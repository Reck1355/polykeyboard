# Pinyin engine data: sources, licences, obligations

Every file the engine loads at runtime is built from the sources below by
`tools/build_pinyin.py`, which is deterministic: two runs produce byte-identical
assets. `--verify` rebuilds them into a temp directory and compares.

| file | built from | licence | obligation |
|---|---|---|---|
| `dict.bin`, `syllables.txt` | CC-CEDICT (`cedict_1_0_ts_utf-8_mdbg.txt.gz`, mdbg.net) | **CC BY-SA 4.0** | credit CC-CEDICT in the app's About, and keep any redistributed copy of the *data* under CC BY-SA |
| `dict.bin` (frequency column) | OpenSubtitles2018 token counts (`zh_cn_full.txt`, github.com/hermitdave/FrequencyWords) | **CC BY-SA 4.0** | credit OpenSubtitles/FrequencyWords in the app's About; share-alike on the redistributed *data* |
| `dict.bin` (frequency column) | jieba `dict.txt` (github.com/fxsjy/jieba), at 0.30 weight | **MIT** | keep the copyright notice with redistributed data |
| `bigrams.bin` | cmn_sentences.tsv.bz2 | **CC BY 2.0** (Tatoeba) if the default corpus; otherwise the corpus's own | credit Tatoeba; the derived bigram counts are a share-alike derivative of the corpus |
| `syllables.txt` | derived from CC-CEDICT, not typed by hand | as above | none beyond the above |

The frequency column mixes two sources on purpose, with the weight measured rather than chosen:
a keyboard has to rank `zaijian` as 再见 ("goodbye") and not 在建 ("under construction"), and
spoken-usage counts are the only ones that know that. The mixture is
0.70 OpenSubtitles / 0.30 jieba; see `tools/build_pinyin.py`'s
module docstring for the measurement.

Nothing here restricts the application's own source code: these are data
obligations (attribution, share-alike on the data), and Apache-2.0 code may
carry CC BY-SA data. The attribution text must exist in the app's resources
even before the UI that shows it lands - see gate G9.
