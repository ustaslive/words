#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path


PROJECT_ROOT_PARENT_INDEX = 1
PROJECT_ROOT = Path(__file__).resolve().parents[PROJECT_ROOT_PARENT_INDEX]
LAB_DIR = PROJECT_ROOT / "lab/crossword_repeatability"
GENERATOR_PATH = LAB_DIR / "generate_005_word_stats.py"
LAB_STATS_PATH = LAB_DIR / "005.stat.txt"
APP_STATS_PATH = PROJECT_ROOT / "app/src/main/assets/005.stat.txt"
DEFAULT_RUNS = 10_000
MINIMUM_RUNS = 1
TIMESTAMP_DIGIT_COUNT = 14
LINE_BUFFERING = 1
SUCCESS_EXIT_CODE = 0
GENERATED_FILE_PATTERN = re.compile(
    rf"^005\.[0-9]{{{TIMESTAMP_DIGIT_COUNT}}}\.txt$"
)
SAVED_LINE_PREFIX = "Saved: "


def positive_run_count(value: str) -> int:
    runs = int(value)
    if runs < MINIMUM_RUNS:
        raise argparse.ArgumentTypeError("runs must be at least 1.")
    return runs


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Generate mode 005 word statistics and synchronize both canonical "
            "005.stat.txt files."
        )
    )
    parser.add_argument(
        "runs",
        nargs="?",
        type=positive_run_count,
        default=DEFAULT_RUNS,
        help=f"Number of simulated games (default: {DEFAULT_RUNS}).",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Show progress from the statistics generator.",
    )
    parser.add_argument(
        "--input-stats",
        type=Path,
        default=LAB_STATS_PATH,
        help=f"Input statistics path (default: {LAB_STATS_PATH}).",
    )
    return parser.parse_args()


def generated_path_from_line(line: str) -> Path | None:
    if not line.startswith(SAVED_LINE_PREFIX):
        return None
    path = Path(line.removeprefix(SAVED_LINE_PREFIX).strip()).resolve()
    if path.parent != LAB_DIR or GENERATED_FILE_PATTERN.fullmatch(path.name) is None:
        raise ValueError(f"Generator returned an unexpected output path: {path}")
    return path


def run_generator(runs: int, verbose: bool, input_stats: Path) -> Path:
    command = [
        sys.executable,
        "-u",
        str(GENERATOR_PATH),
        str(runs),
        "--input-stats",
        str(input_stats),
    ]
    if verbose:
        command.append("--verbose")

    generated_path: Path | None = None
    process = subprocess.Popen(
        command,
        cwd=LAB_DIR,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=LINE_BUFFERING,
    )
    if process.stdout is None:
        raise RuntimeError("Failed to capture generator output.")
    for line in process.stdout:
        print(line, end="", flush=True)
        parsed_path = generated_path_from_line(line.strip())
        if parsed_path is not None:
            generated_path = parsed_path

    return_code = process.wait()
    if return_code != SUCCESS_EXIT_CODE:
        raise RuntimeError(f"Statistics generator failed with exit code {return_code}.")
    if generated_path is None or not generated_path.is_file():
        raise RuntimeError("Statistics generator did not report a valid output file.")
    return generated_path


def copy_file_atomically(source: Path, destination: Path) -> None:
    temporary_path: Path | None = None
    destination_mode = stat.S_IMODE(destination.stat().st_mode)
    try:
        with tempfile.NamedTemporaryFile(
            dir=destination.parent,
            prefix=f".{destination.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            temporary_path = Path(handle.name)
        shutil.copyfile(source, temporary_path)
        temporary_path.chmod(destination_mode)
        os.replace(temporary_path, destination)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()


def synchronize_stats(generated_path: Path) -> None:
    copy_file_atomically(generated_path, LAB_STATS_PATH)
    copy_file_atomically(generated_path, APP_STATS_PATH)
    if LAB_STATS_PATH.read_bytes() != APP_STATS_PATH.read_bytes():
        raise RuntimeError("Canonical statistics files differ after synchronization.")


def main() -> None:
    args = parse_args()
    try:
        generated_path = run_generator(
            runs=args.runs,
            verbose=args.verbose,
            input_stats=args.input_stats.resolve(),
        )
        synchronize_stats(generated_path)
    except (OSError, RuntimeError, ValueError) as error:
        raise SystemExit(f"Error: {error}") from error

    print(f"Synchronized: {LAB_STATS_PATH}")
    print(f"Synchronized: {APP_STATS_PATH}")
    print(f"Timestamped snapshot kept at: {generated_path}")


if __name__ == "__main__":
    main()
