#!/usr/bin/env python3
from __future__ import annotations

import argparse
import random
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from mode005_simulation import (
    Mode005Config,
    Mode005Hooks,
    build_mode005_timing_header_lines,
    simulate_mode005_word_frequency,
)
from simulate_word_frequency import (
    DEFAULT_DICTIONARY_PATH,
    DEFAULT_FORBIDDEN_PATH,
    MAX_CROSSWORD_GENERATION_ATTEMPTS,
    MIN_CROSSWORD_WORD_COUNT,
    MIN_CROSSWORD_WORD_LENGTH,
    build_layout_words_from_input_word_set,
    build_mini_dictionary,
    filter_forbidden_words,
    load_optional_word_set,
    load_word_list,
    seed_letter_length_range,
)


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_WORD_STATS_PATH = SCRIPT_DIR / "005.stat.txt"
OUTPUT_PREFIX = "005."
OUTPUT_SUFFIX = ".txt"
TIMESTAMP_FORMAT = "%Y%m%d%H%M%S"
DEFAULT_MAX_LETTER_SET_SIZE = 9
DEFAULT_TOP_FREQUENT_WORD_SHARE = 0.10
DEFAULT_MAX_REPEAT_SHARE = 0.40
DEFAULT_MAX_SWAP_CYCLES = 5
VERBOSE_PROGRESS_INTERVAL = 100
ONE_ITEM = 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run stats_005 simulation for N runs and produce "
            "005.<YYYYMMDDHHMMSS>.txt in the current folder."
        )
    )
    parser.add_argument(
        "runs",
        type=int,
        help="Number of simulated games.",
    )
    parser.add_argument(
        "--input-stats",
        type=Path,
        default=DEFAULT_WORD_STATS_PATH,
        help=f"Input 005 stats path (default: {DEFAULT_WORD_STATS_PATH}).",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help=(
            "Print progress during generation: one dot every "
            f"{VERBOSE_PROGRESS_INTERVAL} runs with current speed."
        ),
    )
    args = parser.parse_args()
    if args.runs < ONE_ITEM:
        parser.error("runs must be at least 1.")
    return args


def build_bootstrap_word_stats_file() -> Path:
    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        prefix=".tmp_005_word_bootstrap_",
        suffix=".txt",
        dir=SCRIPT_DIR,
        delete=False,
    )
    with handle:
        handle.write("# format=v1\n")
        handle.write("# crosswords_generated=0\n")
        handle.write("# bootstrap=empty_word_stats\n")
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
    successful_runs: int,
    failed_runs: int,
    timing_header_lines: list[str],
    frequency: dict[str, int],
) -> str:
    lines = [
        "# format=v1",
        "# generated_by=generate_005_word_stats.py",
        f"# generated_at_utc={datetime.now(timezone.utc).isoformat()}",
        f"# output_path={output_path.name}",
        f"# runs={runs}",
        f"# successful_runs={successful_runs}",
        f"# failed_runs={failed_runs}",
        f"# crosswords_generated={successful_runs}",
        f"# source_dictionary={dictionary_path}",
        f"# source_forbidden={forbidden_path}",
        f"# input_word_stats={used_word_stats_path}",
        f"# mode005_top_frequent_share={DEFAULT_TOP_FREQUENT_WORD_SHARE}",
        f"# mode005_max_repeat_share={DEFAULT_MAX_REPEAT_SHARE}",
        f"# mode005_max_swap_cycles={DEFAULT_MAX_SWAP_CYCLES}",
        f"# mode005_max_generation_attempts={MAX_CROSSWORD_GENERATION_ATTEMPTS}",
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
    used_word_stats_path = args.input_stats
    if not used_word_stats_path.exists():
        used_word_stats_path = build_bootstrap_word_stats_file()
        cleanup_paths.append(used_word_stats_path)
        print(f"Input stats not found. Using temporary bootstrap: {used_word_stats_path.name}")

    hooks = Mode005Hooks(
        build_mini_dictionary=build_mini_dictionary,
        build_layout_words_from_input=build_layout_words_from_input_word_set,
        seed_letter_length_range=seed_letter_length_range,
    )
    config = Mode005Config(
        max_letter_set_size=DEFAULT_MAX_LETTER_SET_SIZE,
        max_generation_attempts=MAX_CROSSWORD_GENERATION_ATTEMPTS,
        min_word_length=MIN_CROSSWORD_WORD_LENGTH,
        min_crossword_word_count=MIN_CROSSWORD_WORD_COUNT,
        max_letter_swap_cycles=DEFAULT_MAX_SWAP_CYCLES,
        top_frequent_word_share=DEFAULT_TOP_FREQUENT_WORD_SHARE,
        max_repeat_share_with_control_set=DEFAULT_MAX_REPEAT_SHARE,
    )

    start_time = time.perf_counter()

    def on_progress(run_index: int) -> None:
        if not args.verbose:
            return
        should_print = (
            run_index % VERBOSE_PROGRESS_INTERVAL == 0
            or run_index == args.runs
        )
        if not should_print:
            return
        elapsed = time.perf_counter() - start_time
        speed = run_index / elapsed if elapsed > 0 else 0.0
        print(f". {run_index}/{args.runs} ({speed:.1f} runs/s)")

    try:
        result = simulate_mode005_word_frequency(
            dictionary=dictionary,
            runs=args.runs,
            rng=random.Random(),
            config=config,
            hooks=hooks,
            word_stats_path=used_word_stats_path,
            trace_log_path=None,
            progress_callback=on_progress,
        )
    finally:
        for path in cleanup_paths:
            if path.exists():
                path.unlink()

    output_path = build_output_path()
    header_lines = build_mode005_timing_header_lines(result)
    output_text = render_word_stats_output(
        runs=args.runs,
        output_path=output_path,
        dictionary_path=DEFAULT_DICTIONARY_PATH,
        forbidden_path=DEFAULT_FORBIDDEN_PATH,
        used_word_stats_path=used_word_stats_path,
        successful_runs=result.successful_runs,
        failed_runs=result.failed_runs,
        timing_header_lines=header_lines,
        frequency=dict(result.frequency),
    )
    output_path.write_text(output_text + "\n", encoding="utf-8")
    print(f"Saved: {output_path}")


if __name__ == "__main__":
    main()
