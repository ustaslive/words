#!/usr/bin/env python3
from __future__ import annotations

import argparse
import random
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from mode004_simulation import (
    Mode004Config,
    Mode004Hooks,
    build_mode004_timing_header_lines,
    simulate_mode004_word_frequency,
)
from simulate_word_frequency import (
    DEFAULT_DICTIONARY_PATH,
    DEFAULT_FORBIDDEN_PATH,
    MAX_CROSSWORD_GENERATION_ATTEMPTS,
    MIN_CROSSWORD_WORD_COUNT,
    MIN_CROSSWORD_WORD_LENGTH,
    are_all_seed_letters_used,
    build_layout_words_from_input_word_set,
    build_mini_dictionary,
    filter_forbidden_words,
    load_optional_word_set,
    load_word_list,
    seed_letter_length_range,
)


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_WORD_STATS_PATH = SCRIPT_DIR / "004.txt"
DEFAULT_LETTER_STATS_PATH = SCRIPT_DIR / "004.letters.txt"
OUTPUT_PREFIX = "004."
OUTPUT_SUFFIX = ".txt"
TIMESTAMP_FORMAT = "%Y%m%d%H%M%S"
ONE_ITEM = 1
ZERO_COUNT = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run stats_004 simulation for N runs and produce "
            "004.<YYYYMMDDHHMMSS>.txt in the current folder."
        )
    )
    parser.add_argument(
        "runs",
        type=int,
        help="Number of simulated games.",
    )
    args = parser.parse_args()
    if args.runs < ONE_ITEM:
        parser.error("runs must be at least 1.")
    return args


def build_bootstrap_word_stats_file() -> Path:
    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        prefix=".tmp_004_word_bootstrap_",
        suffix=".txt",
        dir=SCRIPT_DIR,
        delete=False,
    )
    with handle:
        handle.write("# format=v1\n")
        handle.write("# bootstrap=empty_word_stats\n")
    return Path(handle.name)


def build_bootstrap_letter_stats_file(dictionary: list[str], forbidden: set[str]) -> Path:
    filtered_words = [word for word in dictionary if word not in forbidden]
    counts: dict[str, int] = {}
    for word in filtered_words:
        for char in word:
            if "A" <= char <= "Z":
                counts[char] = counts.get(char, ZERO_COUNT) + ONE_ITEM

    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        prefix=".tmp_004_letter_bootstrap_",
        suffix=".txt",
        dir=SCRIPT_DIR,
        delete=False,
    )
    with handle:
        handle.write("# format=v1\n")
        handle.write("# bootstrap=from_dictionary\n")
        sorted_items = sorted(counts.items(), key=lambda item: (-item[1], item[0]))
        for letter, count in sorted_items:
            handle.write(f"{letter.lower()}:{count}\n")
    return Path(handle.name)


def build_output_path() -> Path:
    timestamp = datetime.now(timezone.utc).strftime(TIMESTAMP_FORMAT)
    return SCRIPT_DIR / f"{OUTPUT_PREFIX}{timestamp}{OUTPUT_SUFFIX}"


def render_word_stats_output(
    runs: int,
    output_path: Path,
    dictionary_path: Path,
    forbidden_path: Path,
    used_word_stats_path: Path,
    used_letter_stats_path: Path,
    successful_runs: int,
    failed_runs: int,
    timing_header_lines: list[str],
    frequency: dict[str, int],
) -> str:
    lines = [
        "# format=v1",
        "# generated_by=generate_004_word_stats.py",
        f"# generated_at_utc={datetime.now(timezone.utc).isoformat()}",
        f"# output_path={output_path.name}",
        f"# runs={runs}",
        f"# successful_runs={successful_runs}",
        f"# failed_runs={failed_runs}",
        f"# source_dictionary={dictionary_path}",
        f"# source_forbidden={forbidden_path}",
        f"# input_word_stats={used_word_stats_path}",
        f"# input_letter_stats={used_letter_stats_path}",
    ]
    lines.extend(timing_header_lines)

    sorted_items = sorted(frequency.items(), key=lambda item: (-item[1], item[0]))
    lines.extend(f"{word.lower()}:{count}" for word, count in sorted_items)
    return "\n".join(lines)


def main() -> None:
    args = parse_args()

    dictionary = load_word_list(DEFAULT_DICTIONARY_PATH)
    forbidden_words = load_optional_word_set(DEFAULT_FORBIDDEN_PATH)
    dictionary = filter_forbidden_words(dictionary, forbidden_words)

    cleanup_paths: list[Path] = []
    used_word_stats_path = DEFAULT_WORD_STATS_PATH
    used_letter_stats_path = DEFAULT_LETTER_STATS_PATH

    if not used_word_stats_path.exists():
        used_word_stats_path = build_bootstrap_word_stats_file()
        cleanup_paths.append(used_word_stats_path)
        print(f"Input word stats not found. Using temporary bootstrap: {used_word_stats_path.name}")

    if not used_letter_stats_path.exists():
        used_letter_stats_path = build_bootstrap_letter_stats_file(dictionary, forbidden_words)
        cleanup_paths.append(used_letter_stats_path)
        print(f"Input letter stats not found. Using temporary bootstrap: {used_letter_stats_path.name}")

    hooks = Mode004Hooks(
        build_mini_dictionary=build_mini_dictionary,
        build_layout_words_from_input=build_layout_words_from_input_word_set,
        are_all_seed_letters_used=are_all_seed_letters_used,
        seed_letter_length_range=seed_letter_length_range,
    )
    config = Mode004Config(
        max_letter_set_size=9,
        max_generation_attempts=MAX_CROSSWORD_GENERATION_ATTEMPTS,
        min_word_length=MIN_CROSSWORD_WORD_LENGTH,
        min_crossword_word_count=MIN_CROSSWORD_WORD_COUNT,
    )

    try:
        result = simulate_mode004_word_frequency(
            dictionary=dictionary,
            runs=args.runs,
            rng=random.Random(),
            config=config,
            hooks=hooks,
            word_stats_path=used_word_stats_path,
            letter_stats_path=used_letter_stats_path,
            trace_log_path=None,
        )
    finally:
        for path in cleanup_paths:
            if path.exists():
                path.unlink()

    output_path = build_output_path()
    header_lines = build_mode004_timing_header_lines(result)
    output_text = render_word_stats_output(
        runs=args.runs,
        output_path=output_path,
        dictionary_path=DEFAULT_DICTIONARY_PATH,
        forbidden_path=DEFAULT_FORBIDDEN_PATH,
        used_word_stats_path=used_word_stats_path,
        used_letter_stats_path=used_letter_stats_path,
        successful_runs=result.successful_runs,
        failed_runs=result.failed_runs,
        timing_header_lines=header_lines,
        frequency=dict(result.frequency),
    )
    output_path.write_text(output_text + "\n", encoding="utf-8")
    print(f"Saved: {output_path}")


if __name__ == "__main__":
    main()
