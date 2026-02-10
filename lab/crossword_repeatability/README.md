# Crossword Repeatability Lab

This project simulates crossword seed selection from the Android app and counts how often each matching word appears across multiple runs.

## Script

`simulate_word_frequency.py`

## Defaults

- Selection mode: `random_word`
- Max letter set size: `9`
- Runs: `1`
- Dictionary: `app/src/main/assets/words.txt`
- Forbidden words: `app/src/main/assets/forbidden_words.txt` (if present)

## Selection modes

- `random_word`
- `least_similar` (same logic as app `low_overlap`)
- `random_letters` (same logic as app `vowel_rich_letters`)

Aliases are also supported: `random`, `low_overlap`, `vowel_rich_letters`.

## Usage (from this folder)

```bash
cd lab/crossword_repeatability
python3 simulate_word_frequency.py -r 1000 -o word_frequency_1000.tsv
```

If you run from this folder, dictionary defaults still work as-is.

More examples:

```bash
# Print to stdout
python3 simulate_word_frequency.py -r 100

# Least similar mode
python3 simulate_word_frequency.py -m least_similar -r 1000 -o least_similar.tsv

# Random letters mode
python3 simulate_word_frequency.py -m random_letters -r 1000 -o random_letters.tsv
```

## Short options

- `-m` == `--selection-mode`
- `-l` == `--max-letter-set-size`
- `-r` == `--runs`
- `-o` == `--output`
- `-d` == `--dictionary`
- `-f` == `--forbidden`
- `-s` == `--seed`

Output format:

- Header lines with run metadata.
- Tab-separated table sorted by descending frequency (`word<TAB>count`).
