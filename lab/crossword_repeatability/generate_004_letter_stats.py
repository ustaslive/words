#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[1]
DEFAULT_DICTIONARY_PATH = PROJECT_ROOT / "app/src/main/assets/words.txt"
DEFAULT_FORBIDDEN_PATH = PROJECT_ROOT / "app/src/main/assets/forbidden_words.txt"
DEFAULT_OUTPUT_PATH = SCRIPT_DIR / "004.letters.txt"

ASCII_A = "a"
ASCII_Z = "z"
INDEX_STEP = 1
ZERO_COUNT = 0
TOP_SAMPLE_COUNT = 10


@dataclass(frozen=True)
class GenerationSummary:
    dictionary_words: int
    forbidden_words: int
    allowed_words: int
    skipped_non_ascii_words: int
    total_letters_counted: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Generate 004.letters.txt from words.txt and forbidden_words.txt. "
            "Output format is '<letter>:<count>' with '#' comments."
        )
    )
    parser.add_argument(
        "-d",
        "--dictionary",
        type=Path,
        default=DEFAULT_DICTIONARY_PATH,
        help=f"Dictionary file path (default: {DEFAULT_DICTIONARY_PATH}).",
    )
    parser.add_argument(
        "-f",
        "--forbidden",
        type=Path,
        default=DEFAULT_FORBIDDEN_PATH,
        help=(
            f"Forbidden words file path (default: {DEFAULT_FORBIDDEN_PATH}). "
            "If missing, no forbidden words are applied."
        ),
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT_PATH,
        help=f"Output path for 004.letters.txt (default: {DEFAULT_OUTPUT_PATH}).",
    )
    return parser.parse_args()


def load_words(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(f"Dictionary file not found: {path}")
    with path.open("r", encoding="utf-8") as input_file:
        return [line.strip().lower() for line in input_file if line.strip()]


def load_optional_word_set(path: Path) -> set[str]:
    if not path.exists():
        return set()
    with path.open("r", encoding="utf-8") as input_file:
        return {line.strip().lower() for line in input_file if line.strip()}


def is_ascii_lower_word(word: str) -> bool:
    if not word:
        return False
    return all(ASCII_A <= char <= ASCII_Z for char in word)


def generate_letter_counts(
    words: list[str],
    forbidden: set[str],
) -> tuple[Counter[str], GenerationSummary]:
    filtered_words = [word for word in words if word not in forbidden]
    counts: Counter[str] = Counter()
    skipped_non_ascii_words = ZERO_COUNT

    for word in filtered_words:
        if not is_ascii_lower_word(word):
            skipped_non_ascii_words += INDEX_STEP
            continue
        for char in word:
            counts[char] += INDEX_STEP

    total_letters_counted = sum(counts.values())
    summary = GenerationSummary(
        dictionary_words=len(words),
        forbidden_words=len(forbidden),
        allowed_words=len(filtered_words),
        skipped_non_ascii_words=skipped_non_ascii_words,
        total_letters_counted=total_letters_counted,
    )
    return counts, summary


def render_output(
    counts: Counter[str],
    summary: GenerationSummary,
    dictionary_path: Path,
    forbidden_path: Path,
) -> str:
    lines = [
        "# format=v1",
        "# generated_by=generate_004_letter_stats.py",
        f"# source_dictionary={dictionary_path}",
        f"# source_forbidden={forbidden_path}",
        f"# dictionary_words={summary.dictionary_words}",
        f"# forbidden_words={summary.forbidden_words}",
        f"# allowed_words_before_ascii_filter={summary.allowed_words}",
        f"# skipped_non_ascii_words={summary.skipped_non_ascii_words}",
        f"# total_letters_counted={summary.total_letters_counted}",
    ]

    sorted_items = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
    lines.extend(f"{letter}:{count}" for letter, count in sorted_items)
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    words = load_words(args.dictionary)
    forbidden = load_optional_word_set(args.forbidden)
    counts, summary = generate_letter_counts(words, forbidden)
    report = render_output(
        counts=counts,
        summary=summary,
        dictionary_path=args.dictionary,
        forbidden_path=args.forbidden,
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report + "\n", encoding="utf-8")

    print(f"Saved: {args.output}")
    print(f"Dictionary words: {summary.dictionary_words}")
    print(f"Forbidden words: {summary.forbidden_words}")
    print(f"Allowed words: {summary.allowed_words}")
    print(f"Skipped non-ASCII words: {summary.skipped_non_ascii_words}")
    print(f"Total letters counted: {summary.total_letters_counted}")
    top_items = sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:TOP_SAMPLE_COUNT]
    print("Top letters:")
    for letter, count in top_items:
        print(f"  {letter}: {count}")


if __name__ == "__main__":
    main()
