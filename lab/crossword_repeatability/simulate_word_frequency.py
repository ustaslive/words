#!/usr/bin/env python3
from __future__ import annotations

import argparse
import math
import random
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Sequence


ALPHABET_START = "A"
ALPHABET_END = "Z"
ALPHABET_SIZE = 26
ORD_A = ord(ALPHABET_START)

CROSSWORD_EMPTY_CELL = "."
MIN_CROSSWORD_WORD_LENGTH = 4
MIN_CROSSWORD_WORD_COUNT = 9
MAX_CROSSWORD_ROWS = 14
MAX_CROSSWORD_COLUMNS = 14
MAX_CROSSWORD_GENERATION_ATTEMPTS = 100

ORIGIN_INDEX = 0
INDEX_STEP = 1
LAST_INDEX_OFFSET = 1
PRIORITY_LONGEST_WORDS_COUNT = 3

MIN_SEED_LETTER_SET_SIZE = 6
MAX_SEED_LETTER_SET_SIZE = 9
DEFAULT_MAX_LETTER_SET_SIZE = MAX_SEED_LETTER_SET_SIZE
LOW_OVERLAP_MAX_SHARED_RATIO = 0.2
VOWELS = "AEIOU"
CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ"
MIN_RANDOM_VOWEL_COUNT = 2
MAX_RANDOM_VOWEL_COUNT = 3
COUNT_STEP = 1

DEFAULT_RUNS = 1
DEFAULT_SELECTION_MODE = "random_word"
MODE_RANDOM_WORD = "random_word"
MODE_LEAST_SIMILAR = "least_similar"
MODE_RANDOM_LETTERS = "random_letters"

MODE_ALIAS_RANDOM_WORD = "random"
MODE_ALIAS_LOW_OVERLAP = "low_overlap"
MODE_ALIAS_VOWEL_RICH_LETTERS = "vowel_rich_letters"

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parents[1]
DEFAULT_DICTIONARY_PATH = PROJECT_ROOT / "app/src/main/assets/words.txt"
DEFAULT_FORBIDDEN_PATH = PROJECT_ROOT / "app/src/main/assets/forbidden_words.txt"


@dataclass(frozen=True)
class GridPosition:
    row: int
    col: int


@dataclass
class CellState:
    letter: str
    has_horizontal: bool
    has_vertical: bool


@dataclass
class CrosswordBounds:
    min_row: int
    max_row: int
    min_col: int
    max_col: int

    def update(self, position: GridPosition) -> None:
        self.min_row = min(self.min_row, position.row)
        self.max_row = max(self.max_row, position.row)
        self.min_col = min(self.min_col, position.col)
        self.max_col = max(self.max_col, position.col)


@dataclass(frozen=True)
class WordPlacement:
    word: str
    start: GridPosition
    orientation: str


@dataclass
class GenerationResult:
    success: bool
    seed_letters: str | None
    layout_words: set[str]
    rejected_seed_letters: list[str]
    attempts: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simulate crossword seed selection and count word repetition frequency."
    )
    parser.add_argument(
        "-m",
        "--selection-mode",
        default=DEFAULT_SELECTION_MODE,
        help=(
            "Selection mode. Supported values: random_word, least_similar, random_letters "
            "(aliases: random, low_overlap, vowel_rich_letters)."
        ),
    )
    parser.add_argument(
        "-l",
        "--max-letter-set-size",
        type=int,
        default=DEFAULT_MAX_LETTER_SET_SIZE,
        help=f"Maximum seed letter set size (default: {DEFAULT_MAX_LETTER_SET_SIZE}).",
    )
    parser.add_argument(
        "-r",
        "--runs",
        type=int,
        default=DEFAULT_RUNS,
        help=f"Number of simulation runs (default: {DEFAULT_RUNS}).",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Optional output file path. If omitted, prints to stdout.",
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
        "-s",
        "--seed",
        type=int,
        default=None,
        help="Optional random seed for reproducible runs.",
    )
    args = parser.parse_args()
    if args.runs < COUNT_STEP:
        parser.error("--runs must be at least 1.")
    return args


def normalize_selection_mode(mode: str) -> str:
    normalized = mode.strip().lower()
    mapping = {
        MODE_RANDOM_WORD: MODE_RANDOM_WORD,
        MODE_ALIAS_RANDOM_WORD: MODE_RANDOM_WORD,
        MODE_LEAST_SIMILAR: MODE_LEAST_SIMILAR,
        MODE_ALIAS_LOW_OVERLAP: MODE_LEAST_SIMILAR,
        MODE_RANDOM_LETTERS: MODE_RANDOM_LETTERS,
        MODE_ALIAS_VOWEL_RICH_LETTERS: MODE_RANDOM_LETTERS,
    }
    resolved = mapping.get(normalized)
    if resolved is None:
        allowed = ", ".join(sorted(mapping.keys()))
        raise ValueError(f"Unsupported selection mode '{mode}'. Supported values: {allowed}.")
    return resolved


def load_word_list(path: Path) -> list[str]:
    if not path.exists():
        raise FileNotFoundError(f"Dictionary file not found: {path}")
    with path.open("r", encoding="utf-8") as input_file:
        return [line.strip().upper() for line in input_file if line.strip()]


def load_optional_word_set(path: Path) -> set[str]:
    if not path.exists():
        return set()
    with path.open("r", encoding="utf-8") as input_file:
        return {line.strip().upper() for line in input_file if line.strip()}


def filter_forbidden_words(words: list[str], forbidden_words: set[str]) -> list[str]:
    if not words or not forbidden_words:
        return words
    return [word for word in words if word not in forbidden_words]


def is_english_upper_letter(char: str) -> bool:
    return ALPHABET_START <= char <= ALPHABET_END


def count_letters(word: str) -> list[int] | None:
    counts = [ORIGIN_INDEX] * ALPHABET_SIZE
    for char in word:
        if not is_english_upper_letter(char):
            return None
        counts[ord(char) - ORD_A] += INDEX_STEP
    return counts


def can_build_word(word: str, base_counts: Sequence[int]) -> bool:
    counts = [ORIGIN_INDEX] * ALPHABET_SIZE
    for char in word:
        if not is_english_upper_letter(char):
            return False
        index = ord(char) - ORD_A
        next_value = counts[index] + INDEX_STEP
        if next_value > base_counts[index]:
            return False
        counts[index] = next_value
    return True


def build_mini_dictionary(seed_letters: str, dictionary: Iterable[str]) -> list[str]:
    normalized_seed = seed_letters.strip().upper()
    if not normalized_seed:
        return []
    seed_counts = count_letters(normalized_seed)
    if seed_counts is None:
        return []
    seed_length = len(normalized_seed)
    result: list[str] = []
    for raw_word in dictionary:
        candidate = raw_word.strip().upper()
        if not candidate or len(candidate) > seed_length:
            continue
        if can_build_word(candidate, seed_counts):
            result.append(candidate)
    return result


def normalize_crossword_words(words: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    normalized_words: list[str] = []
    for raw_word in words:
        candidate = raw_word.strip().upper()
        if not candidate:
            continue
        if not all(is_english_upper_letter(char) for char in candidate):
            continue
        if candidate in seen:
            continue
        seen.add(candidate)
        normalized_words.append(candidate)
    return normalized_words


def pick_longest_words(words: Sequence[str]) -> list[str]:
    max_length = max(len(word) for word in words)
    return [word for word in words if len(word) == max_length]


def pick_priority_words(words: Sequence[str], base_word: str) -> list[str]:
    ordered_by_length = sorted(words, key=len, reverse=True)
    priority_words = [base_word]
    for word in ordered_by_length:
        if word == base_word:
            continue
        priority_words.append(word)
        if len(priority_words) == PRIORITY_LONGEST_WORDS_COUNT:
            break
    return priority_words


def orientation_step(orientation: str) -> GridPosition:
    if orientation == "horizontal":
        return GridPosition(ORIGIN_INDEX, INDEX_STEP)
    return GridPosition(INDEX_STEP, ORIGIN_INDEX)


def perpendicular_neighbors_empty(
    position: GridPosition,
    orientation: str,
    grid: dict[GridPosition, CellState],
) -> bool:
    if orientation == "horizontal":
        up = GridPosition(position.row - INDEX_STEP, position.col)
        down = GridPosition(position.row + INDEX_STEP, position.col)
        return up not in grid and down not in grid
    left = GridPosition(position.row, position.col - INDEX_STEP)
    right = GridPosition(position.row, position.col + INDEX_STEP)
    return left not in grid and right not in grid


def can_place_word(
    placement: WordPlacement,
    grid: dict[GridPosition, CellState],
    bounds: CrosswordBounds,
) -> bool:
    step = orientation_step(placement.orientation)
    before_start = GridPosition(
        placement.start.row - step.row,
        placement.start.col - step.col,
    )
    if before_start in grid:
        return False

    last_index = len(placement.word) - LAST_INDEX_OFFSET
    end = GridPosition(
        placement.start.row + (step.row * last_index),
        placement.start.col + (step.col * last_index),
    )
    after_end = GridPosition(end.row + step.row, end.col + step.col)
    if after_end in grid:
        return False

    min_row = bounds.min_row
    max_row = bounds.max_row
    min_col = bounds.min_col
    max_col = bounds.max_col

    adds_new_cell = False
    for index, letter in enumerate(placement.word):
        row = placement.start.row + (step.row * index)
        col = placement.start.col + (step.col * index)
        position = GridPosition(row, col)
        existing = grid.get(position)
        if existing is None:
            if not perpendicular_neighbors_empty(position, placement.orientation, grid):
                return False
            adds_new_cell = True
        else:
            if existing.letter != letter:
                return False
            if placement.orientation == "horizontal" and existing.has_horizontal:
                return False
            if placement.orientation == "vertical" and existing.has_vertical:
                return False
        min_row = min(min_row, row)
        max_row = max(max_row, row)
        min_col = min(min_col, col)
        max_col = max(max_col, col)

    row_count = (max_row - min_row) + INDEX_STEP
    col_count = (max_col - min_col) + INDEX_STEP
    if row_count > MAX_CROSSWORD_ROWS or col_count > MAX_CROSSWORD_COLUMNS:
        return False
    return adds_new_cell


def place_word(
    placement: WordPlacement,
    grid: dict[GridPosition, CellState],
    letter_index: dict[str, list[GridPosition]],
    bounds: CrosswordBounds,
) -> None:
    step = orientation_step(placement.orientation)
    for index, letter in enumerate(placement.word):
        row = placement.start.row + (step.row * index)
        col = placement.start.col + (step.col * index)
        position = GridPosition(row, col)
        existing = grid.get(position)
        if existing is None:
            grid[position] = CellState(
                letter=letter,
                has_horizontal=placement.orientation == "horizontal",
                has_vertical=placement.orientation == "vertical",
            )
            letter_index.setdefault(letter, []).append(position)
        else:
            if placement.orientation == "horizontal":
                existing.has_horizontal = True
            else:
                existing.has_vertical = True
        bounds.update(position)


def find_candidate_placements(
    word: str,
    grid: dict[GridPosition, CellState],
    letter_index: dict[str, list[GridPosition]],
    bounds: CrosswordBounds,
) -> list[WordPlacement]:
    placements: dict[WordPlacement, None] = {}
    for index, letter in enumerate(word):
        positions = letter_index.get(letter)
        if not positions:
            continue
        for position in positions:
            horizontal_start = GridPosition(position.row, position.col - index)
            horizontal = WordPlacement(word=word, start=horizontal_start, orientation="horizontal")
            if can_place_word(horizontal, grid, bounds):
                placements[horizontal] = None

            vertical_start = GridPosition(position.row - index, position.col)
            vertical = WordPlacement(word=word, start=vertical_start, orientation="vertical")
            if can_place_word(vertical, grid, bounds):
                placements[vertical] = None
    return list(placements.keys())


def build_crossword_rows(
    grid: dict[GridPosition, CellState],
    bounds: CrosswordBounds,
) -> list[str]:
    column_count = (bounds.max_col - bounds.min_col) + INDEX_STEP
    rows: list[str] = []
    for row in range(bounds.min_row, bounds.max_row + INDEX_STEP):
        row_chars = [CROSSWORD_EMPTY_CELL] * column_count
        for position, cell in grid.items():
            if position.row == row:
                column_index = position.col - bounds.min_col
                row_chars[column_index] = cell.letter.lower()
        rows.append("".join(row_chars))
    return rows


def normalize_crossword_rows(rows: list[str]) -> list[str]:
    if not rows:
        return []
    column_count = max(len(row) for row in rows)
    return [row.ljust(column_count, CROSSWORD_EMPTY_CELL) for row in rows]


def collect_words_from_line(
    letters: Sequence[str],
    min_word_length: int,
    results: list[str],
) -> None:
    word_builder: list[str] = []

    def flush_word() -> None:
        if len(word_builder) >= min_word_length:
            results.append("".join(word_builder).upper())
        word_builder.clear()

    for char in letters:
        if char == CROSSWORD_EMPTY_CELL:
            flush_word()
        else:
            word_builder.append(char.upper())
    flush_word()


def extract_crossword_words(rows: list[str], min_word_length: int = MIN_CROSSWORD_WORD_LENGTH) -> list[str]:
    if not rows:
        return []

    normalized_rows = normalize_crossword_rows(rows)
    words: list[str] = []
    for row in normalized_rows:
        collect_words_from_line(list(row), min_word_length, words)

    column_count = len(normalized_rows[ORIGIN_INDEX])
    row_count = len(normalized_rows)
    for col_index in range(column_count):
        column_letters = [normalized_rows[row_index][col_index] for row_index in range(row_count)]
        collect_words_from_line(column_letters, min_word_length, words)
    return words


def generate_random_crossword(words: list[str], rng: random.Random) -> list[str]:
    normalized_words = normalize_crossword_words(words)
    if not normalized_words:
        return []

    longest_words = pick_longest_words(normalized_words)
    base_word = longest_words[rng.randrange(len(longest_words))]

    priority_words = pick_priority_words(normalized_words, base_word)
    priority_word_set = set(priority_words)
    remaining_words = [word for word in normalized_words if word not in priority_word_set]
    rng.shuffle(remaining_words)

    base_fits_horizontal = len(base_word) <= MAX_CROSSWORD_COLUMNS
    base_fits_vertical = len(base_word) <= MAX_CROSSWORD_ROWS
    if not base_fits_horizontal and not base_fits_vertical:
        return []

    grid: dict[GridPosition, CellState] = {}
    letter_index: dict[str, list[GridPosition]] = {}
    bounds = CrosswordBounds(
        min_row=ORIGIN_INDEX,
        max_row=ORIGIN_INDEX,
        min_col=ORIGIN_INDEX,
        max_col=ORIGIN_INDEX,
    )

    if base_fits_horizontal and base_fits_vertical:
        base_orientation = "horizontal" if rng.choice((True, False)) else "vertical"
    elif base_fits_horizontal:
        base_orientation = "horizontal"
    else:
        base_orientation = "vertical"

    place_word(
        placement=WordPlacement(
            word=base_word,
            start=GridPosition(ORIGIN_INDEX, ORIGIN_INDEX),
            orientation=base_orientation,
        ),
        grid=grid,
        letter_index=letter_index,
        bounds=bounds,
    )

    for word in priority_words + remaining_words:
        if word == base_word:
            continue
        placements = find_candidate_placements(word, grid, letter_index, bounds)
        if not placements:
            continue
        placement = placements[rng.randrange(len(placements))]
        place_word(placement, grid, letter_index, bounds)

    return build_crossword_rows(grid, bounds)


def build_crossword_layout_words(
    seed_letters: str,
    dictionary: list[str],
    rng: random.Random,
) -> set[str]:
    mini_dictionary = [
        word
        for word in build_mini_dictionary(seed_letters, dictionary)
        if len(word) >= MIN_CROSSWORD_WORD_LENGTH
    ]
    rows = generate_random_crossword(mini_dictionary, rng)
    extracted_words = extract_crossword_words(rows)
    return set(extracted_words)


def are_all_seed_letters_used(seed_letters: str, layout_words: set[str]) -> bool:
    used_letters = [False] * ALPHABET_SIZE
    for word in layout_words:
        for char in word:
            if not is_english_upper_letter(char):
                continue
            used_letters[ord(char) - ORD_A] = True

    for char in seed_letters.strip().upper():
        if not is_english_upper_letter(char):
            return False
        if not used_letters[ord(char) - ORD_A]:
            return False
    return True


def generate_crossword_with_quality(
    seed_letter_candidates: list[str],
    dictionary: list[str],
    rng: random.Random,
    is_valid_layout: Callable[[str, set[str]], bool],
) -> GenerationResult:
    if not seed_letter_candidates:
        return GenerationResult(
            success=False,
            seed_letters=None,
            layout_words=set(),
            rejected_seed_letters=[],
            attempts=ORIGIN_INDEX,
        )

    remaining_seed_letters = list(seed_letter_candidates)
    rejected_seed_letters: list[str] = []
    attempts = ORIGIN_INDEX

    while attempts < MAX_CROSSWORD_GENERATION_ATTEMPTS and remaining_seed_letters:
        seed_index = rng.randrange(len(remaining_seed_letters))
        seed_letters = remaining_seed_letters.pop(seed_index)
        attempts += INDEX_STEP

        layout_words = build_crossword_layout_words(seed_letters, dictionary, rng)
        word_count = len(layout_words)
        if word_count >= MIN_CROSSWORD_WORD_COUNT and is_valid_layout(seed_letters, layout_words):
            return GenerationResult(
                success=True,
                seed_letters=seed_letters,
                layout_words=layout_words,
                rejected_seed_letters=rejected_seed_letters,
                attempts=attempts,
            )
        rejected_seed_letters.append(seed_letters)

    return GenerationResult(
        success=False,
        seed_letters=None,
        layout_words=set(),
        rejected_seed_letters=rejected_seed_letters,
        attempts=attempts,
    )


def seed_letter_length_range(max_letter_set_size: int) -> tuple[int, int]:
    clamped_max = max(MIN_SEED_LETTER_SET_SIZE, min(max_letter_set_size, MAX_SEED_LETTER_SET_SIZE))
    min_size = max(MIN_SEED_LETTER_SET_SIZE, clamped_max - COUNT_STEP)
    return (min_size, clamped_max)


def count_letter_matches(word: str) -> list[int] | None:
    counts = [ORIGIN_INDEX] * ALPHABET_SIZE
    for char in word:
        if not is_english_upper_letter(char):
            return None
        counts[ord(char) - ORD_A] += INDEX_STEP
    return counts


def has_low_letter_overlap(candidate: str, previous_seed_letters: str) -> bool:
    if not previous_seed_letters.strip():
        return True

    normalized_candidate = candidate.upper()
    normalized_previous = previous_seed_letters.upper()
    candidate_counts = count_letter_matches(normalized_candidate)
    previous_counts = count_letter_matches(normalized_previous)
    if candidate_counts is None or previous_counts is None:
        return True

    shared_count = sum(
        min(candidate_counts[index], previous_counts[index])
        for index in range(ALPHABET_SIZE)
    )
    max_shared = math.floor(len(normalized_candidate) * LOW_OVERLAP_MAX_SHARED_RATIO)
    return shared_count <= max_shared


def build_available_consonants(previous_seed_letters: str) -> list[str]:
    if not previous_seed_letters.strip():
        return list(CONSONANTS)
    previous_consonants = {char for char in previous_seed_letters.upper() if char in CONSONANTS}
    return [char for char in CONSONANTS if char not in previous_consonants]


def pick_random_vowel_count(seed_length: int, rng: random.Random) -> int:
    min_vowels = min(MIN_RANDOM_VOWEL_COUNT, seed_length)
    max_vowels = min(MAX_RANDOM_VOWEL_COUNT, seed_length)
    if min_vowels == max_vowels:
        return min_vowels
    return min_vowels if rng.choice((True, False)) else max_vowels


def pick_random_vowels(vowel_count: int, rng: random.Random) -> list[str]:
    if vowel_count < COUNT_STEP:
        return []
    return [VOWELS[rng.randrange(len(VOWELS))] for _ in range(vowel_count)]


def pick_random_consonants(
    consonant_pool: list[str],
    consonant_count: int,
    rng: random.Random,
) -> list[str]:
    if consonant_count < COUNT_STEP or not consonant_pool:
        return []
    if consonant_count <= len(consonant_pool):
        shuffled = list(consonant_pool)
        rng.shuffle(shuffled)
        return shuffled[:consonant_count]
    return [consonant_pool[rng.randrange(len(consonant_pool))] for _ in range(consonant_count)]


def build_random_seed_letters(
    seed_length: int,
    vowel_count: int,
    consonant_pool: list[str],
    rng: random.Random,
) -> str:
    vowels = pick_random_vowels(vowel_count, rng)
    consonant_count = seed_length - len(vowels)
    consonants = pick_random_consonants(consonant_pool, consonant_count, rng)
    letters = vowels + consonants
    rng.shuffle(letters)
    return "".join(letters)


def build_random_seed_letter_candidates(
    seed_length_range: tuple[int, int],
    candidate_count: int,
    previous_seed_letters: str,
    rng: random.Random,
) -> list[str]:
    consonant_pool = build_available_consonants(previous_seed_letters)
    min_length, max_length = seed_length_range
    length_range = (max_length - min_length) + COUNT_STEP
    total_count = max(candidate_count, COUNT_STEP)

    result: list[str] = []
    for _ in range(total_count):
        seed_length = min_length + rng.randrange(length_range)
        vowel_count = pick_random_vowel_count(seed_length, rng)
        result.append(
            build_random_seed_letters(
                seed_length=seed_length,
                vowel_count=vowel_count,
                consonant_pool=consonant_pool,
                rng=rng,
            )
        )
    return result


def build_seed_letter_candidates(
    eligible_words: list[str],
    previous_seed_letters: str,
    selection_mode: str,
    max_letter_set_size: int,
    rng: random.Random,
) -> list[str]:
    seed_length_range = seed_letter_length_range(max_letter_set_size)
    if selection_mode == MODE_RANDOM_WORD:
        return eligible_words
    if selection_mode == MODE_LEAST_SIMILAR:
        filtered = [
            candidate for candidate in eligible_words
            if has_low_letter_overlap(candidate, previous_seed_letters)
        ]
        return filtered if filtered else eligible_words
    return build_random_seed_letter_candidates(
        seed_length_range=seed_length_range,
        candidate_count=MAX_CROSSWORD_GENERATION_ATTEMPTS,
        previous_seed_letters=previous_seed_letters,
        rng=rng,
    )


def build_eligible_words(dictionary: list[str], max_letter_set_size: int) -> list[str]:
    min_length, max_length = seed_letter_length_range(max_letter_set_size)
    dictionary_set = set(dictionary)
    return [word for word in dictionary_set if min_length <= len(word) <= max_length]


def build_matching_word_list(seed_letters: str, dictionary: list[str]) -> list[str]:
    candidates = [
        word
        for word in build_mini_dictionary(seed_letters, dictionary)
        if len(word) >= MIN_CROSSWORD_WORD_LENGTH
    ]
    return normalize_crossword_words(candidates)


def simulate_word_frequency(
    dictionary: list[str],
    selection_mode: str,
    max_letter_set_size: int,
    runs: int,
    rng: random.Random,
) -> tuple[Counter[str], int, int]:
    eligible_words = build_eligible_words(dictionary, max_letter_set_size)
    frequency: Counter[str] = Counter()
    successful_runs = ORIGIN_INDEX
    failed_runs = ORIGIN_INDEX
    seed_letters = ""

    for _ in range(runs):
        candidates = build_seed_letter_candidates(
            eligible_words=eligible_words,
            previous_seed_letters=seed_letters,
            selection_mode=selection_mode,
            max_letter_set_size=max_letter_set_size,
            rng=rng,
        )

        if selection_mode == MODE_RANDOM_LETTERS:
            validator = are_all_seed_letters_used
        else:
            validator = lambda _seed, _layout_words: True

        result = generate_crossword_with_quality(
            seed_letter_candidates=candidates,
            dictionary=dictionary,
            rng=rng,
            is_valid_layout=validator,
        )

        if not result.success or result.seed_letters is None:
            failed_runs += INDEX_STEP
            continue

        seed_letters = result.seed_letters
        successful_runs += INDEX_STEP
        for word in build_matching_word_list(seed_letters, dictionary):
            frequency[word] += INDEX_STEP

    return (frequency, successful_runs, failed_runs)


def render_report(
    frequency: Counter[str],
    selection_mode: str,
    max_letter_set_size: int,
    runs: int,
    successful_runs: int,
    failed_runs: int,
) -> str:
    lines = [
        f"# selection_mode={selection_mode}",
        f"# max_letter_set_size={max_letter_set_size}",
        f"# runs={runs}",
        f"# successful_runs={successful_runs}",
        f"# failed_runs={failed_runs}",
        "word\tcount",
    ]
    sorted_items = sorted(frequency.items(), key=lambda item: (-item[1], item[0]))
    lines.extend(f"{word}\t{count}" for word, count in sorted_items)
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    selection_mode = normalize_selection_mode(args.selection_mode)
    rng = random.Random(args.seed)

    dictionary = load_word_list(args.dictionary)
    forbidden_words = load_optional_word_set(args.forbidden)
    dictionary = filter_forbidden_words(dictionary, forbidden_words)

    frequency, successful_runs, failed_runs = simulate_word_frequency(
        dictionary=dictionary,
        selection_mode=selection_mode,
        max_letter_set_size=args.max_letter_set_size,
        runs=args.runs,
        rng=rng,
    )
    report = render_report(
        frequency=frequency,
        selection_mode=selection_mode,
        max_letter_set_size=args.max_letter_set_size,
        runs=args.runs,
        successful_runs=successful_runs,
        failed_runs=failed_runs,
    )

    if args.output is None:
        print(report)
        return

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report + "\n", encoding="utf-8")
    print(f"Saved report: {args.output}")


if __name__ == "__main__":
    main()
