from __future__ import annotations

import math
import random
import time
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable


ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ"
VOWELS = "AEIOU"

INDEX_STEP = 1
ORIGIN_INDEX = 0
ONE_UNIT = 1.0
ZERO_FLOAT = 0.0
MILLISECONDS_PER_SECOND = 1000.0

DEFAULT_MAX_LETTER_SWAP_CYCLES = 5
DEFAULT_TOP_FREQUENT_WORD_SHARE = 0.10
DEFAULT_MAX_REPEAT_SHARE_WITH_CONTROL_SET = 0.40
MIN_SEED_VOWEL_COUNT = 2
MID_SEED_VOWEL_COUNT = 3
MAX_SEED_VOWEL_COUNT = 4
VOWEL_WEIGHT_FOR_TWO = 3
VOWEL_WEIGHT_FOR_THREE = 5
VOWEL_WEIGHT_FOR_FOUR = 2

PHASE_SEED_INIT = "seed_initialization"
PHASE_FULL_SET = "build_full_word_set"
PHASE_REPEAT_ANALYSIS = "repeat_analysis"
PHASE_LAYOUT_BUILD = "layout_build"
PHASE_VALIDATION = "validation"
PHASE_MUTATION = "mutation"

STEP_BUILD_SEED = "build_seed"
STEP_BUILD_FULL_WORD_SET = "build_full_word_set"
STEP_BUILD_REPEAT_SOURCE = "build_repeat_source_word_set"
STEP_BUILD_LAYOUT_WORD_SET = "build_layout_word_set"
STEP_VALIDATE_LAYOUT = "validate_layout"
STEP_MUTATE_SEED = "mutate_seed"

STEP_INDEX_BUILD_SEED = 1
STEP_INDEX_BUILD_FULL_WORD_SET = 2
STEP_INDEX_BUILD_REPEAT_SOURCE = 3
STEP_INDEX_BUILD_LAYOUT_WORD_SET = 4
STEP_INDEX_VALIDATE_LAYOUT = 5
STEP_INDEX_MUTATE_SEED = 6

REJECT_SWAP_CYCLES_EXHAUSTED = "swap_cycles_exhausted"
REJECT_ADDITION_POOL_EMPTY = "addition_pool_empty"
REJECT_EMPTY_SEED = "empty_seed"
REJECT_FULL_WORD_SET_TOO_SMALL = "full_word_set_below_minimum"
REJECT_LAYOUT_INVALID_AFTER_REPEAT_PASS = "layout_invalid_after_repeat_pass"

CRITERION_SWAP_CYCLES_EXHAUSTED = "swap_cycles_limit_reached"
CRITERION_ADDITION_POOL_EMPTY = "addition_pool_has_no_candidates"
CRITERION_EMPTY_SEED = "seed_became_empty"
CRITERION_FULL_WORD_SET_TOO_SMALL = "full_word_set_below_minimum_required_for_crossword"
CRITERION_LAYOUT_INVALID_AFTER_REPEAT_PASS = "layout_invalid_when_repeat_threshold_passed"

CROSSWORDS_GENERATED_COMMENT_PREFIX = "crosswords_generated="

REPEAT_CONTROL_PREVIOUS_ROUND = "previous_round_words"
REPEAT_CONTROL_TOP_FREQUENT = "top_frequent_words"


BuildMiniDictionaryFn = Callable[[str, Iterable[str]], list[str]]
BuildLayoutWordsFn = Callable[[set[str], random.Random], set[str]]
SeedLengthRangeFn = Callable[[int], tuple[int, int]]
ProgressCallbackFn = Callable[[int], None]


@dataclass(frozen=True)
class Mode005Hooks:
    build_mini_dictionary: BuildMiniDictionaryFn
    build_layout_words_from_input: BuildLayoutWordsFn
    seed_letter_length_range: SeedLengthRangeFn


@dataclass(frozen=True)
class Mode005Config:
    max_letter_set_size: int
    max_generation_attempts: int
    min_word_length: int
    min_crossword_word_count: int
    max_letter_swap_cycles: int = DEFAULT_MAX_LETTER_SWAP_CYCLES
    top_frequent_word_share: float = DEFAULT_TOP_FREQUENT_WORD_SHARE
    max_repeat_share_with_control_set: float = DEFAULT_MAX_REPEAT_SHARE_WITH_CONTROL_SET


@dataclass
class Mode005State:
    previous_crossword_word_set: set[str] = field(default_factory=set)


@dataclass(frozen=True)
class Mode005Candidate:
    seed_letters: str
    full_word_set: frozenset[str]
    layout_word_set: frozenset[str]
    repeat_source_word_set: frozenset[str]
    repeat_share: float
    repeat_control_name: str


@dataclass(frozen=True)
class RejectDecision:
    reason: str
    step_index: int
    step_name: str
    criterion: str


@dataclass
class TimingStats:
    totals_seconds: dict[str, float] = field(default_factory=dict)
    counts: dict[str, int] = field(default_factory=dict)

    def add(self, phase: str, seconds: float) -> None:
        self.totals_seconds[phase] = self.totals_seconds.get(phase, ZERO_FLOAT) + seconds
        self.counts[phase] = self.counts.get(phase, ORIGIN_INDEX) + INDEX_STEP


@dataclass(frozen=True)
class Mode005WordStats:
    frequencies: dict[str, int]
    crosswords_generated: int | None


@dataclass(frozen=True)
class Mode005SimulationResult:
    frequency: Counter[str]
    successful_runs: int
    failed_runs: int
    timing_stats: TimingStats
    crosswords_generated_from_stats: int | None
    run_summaries: tuple[str, ...]


def load_mode005_word_stats(path: Path) -> Mode005WordStats:
    if not path.exists():
        raise FileNotFoundError(f"Stats file not found: {path}")

    frequencies: dict[str, int] = {}
    crosswords_generated: int | None = None
    with path.open("r", encoding="utf-8") as input_file:
        for line_number, raw_line in enumerate(input_file, start=INDEX_STEP):
            stripped = raw_line.strip()
            if not stripped:
                continue
            if stripped.startswith("#"):
                comment = stripped[INDEX_STEP:].strip()
                parsed_value = _parse_crosswords_generated_comment(comment)
                if parsed_value is not None:
                    crosswords_generated = parsed_value
                continue

            if ":" not in stripped:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Expected '<word>:<count>'."
                )
            key_raw, count_raw = stripped.split(":", maxsplit=INDEX_STEP)
            word = key_raw.strip().upper()
            count_text = count_raw.strip()
            if not word:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Missing word key."
                )
            _validate_word_key(word, path, line_number)
            try:
                count_value = int(count_text)
            except ValueError as error:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Count must be integer."
                ) from error
            if count_value < ORIGIN_INDEX:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Count must be non-negative."
                )
            frequencies[word] = count_value

    return Mode005WordStats(
        frequencies=frequencies,
        crosswords_generated=crosswords_generated,
    )


def simulate_mode005_word_frequency(
    dictionary: list[str],
    runs: int,
    rng: random.Random,
    config: Mode005Config,
    hooks: Mode005Hooks,
    word_stats_path: Path,
    trace_log_path: Path | None,
    progress_callback: ProgressCallbackFn | None = None,
) -> Mode005SimulationResult:
    stats = load_mode005_word_stats(word_stats_path)
    dictionary_words = _build_unique_dictionary_words(dictionary)
    top_frequent_word_set = _build_top_frequent_word_set(
        dictionary_words=dictionary_words,
        word_stats=stats.frequencies,
        top_share=config.top_frequent_word_share,
    )

    frequency: Counter[str] = Counter()
    successful_runs = ORIGIN_INDEX
    failed_runs = ORIGIN_INDEX
    run_summaries: list[str] = []
    state = Mode005State()
    timing_stats = TimingStats()

    trace_file = _open_trace_file(trace_log_path)
    try:
        for run_index in range(INDEX_STEP, runs + INDEX_STEP):
            _trace(
                trace_file,
                {
                    "event": "run_start",
                    "run": run_index,
                    "previous_round_word_count": len(state.previous_crossword_word_set),
                    "top_frequent_word_count": len(top_frequent_word_set),
                },
            )

            candidate: Mode005Candidate | None = None
            rejected_by_reason: Counter[str] = Counter()
            attempts_used = ORIGIN_INDEX
            for attempt_index in range(INDEX_STEP, config.max_generation_attempts + INDEX_STEP):
                attempts_used = attempt_index
                _trace(
                    trace_file,
                    {
                        "event": "attempt_start",
                        "run": run_index,
                        "attempt": attempt_index,
                        "max_swap_cycles": config.max_letter_swap_cycles,
                        "repeat_share_threshold": config.max_repeat_share_with_control_set,
                    },
                )

                attempt_trace: dict[str, object] = {
                    "event": "attempt_end",
                    "run": run_index,
                    "attempt": attempt_index,
                }
                candidate, reject_decision = _run_single_attempt(
                    dictionary=dictionary_words,
                    rng=rng,
                    config=config,
                    hooks=hooks,
                    state=state,
                    top_frequent_word_set=top_frequent_word_set,
                    timing_stats=timing_stats,
                    trace_file=trace_file,
                    run_index=run_index,
                    attempt_index=attempt_index,
                )
                if candidate is None:
                    if reject_decision is None:
                        raise RuntimeError("Mode005 attempt rejected without reject decision.")
                    rejected_by_reason[reject_decision.reason] += INDEX_STEP
                    attempt_trace["status"] = "rejected"
                    attempt_trace["reject_reason"] = reject_decision.reason
                    attempt_trace["reject_step_index"] = reject_decision.step_index
                    attempt_trace["reject_step_name"] = reject_decision.step_name
                    attempt_trace["reject_criterion"] = reject_decision.criterion
                    _trace(
                        trace_file,
                        {
                            "event": "attempt_reject",
                            "run": run_index,
                            "attempt": attempt_index,
                            "reject_reason": reject_decision.reason,
                            "reject_step_index": reject_decision.step_index,
                            "reject_step_name": reject_decision.step_name,
                            "reject_criterion": reject_decision.criterion,
                        },
                    )
                    _trace(trace_file, attempt_trace)
                    continue

                attempt_trace["status"] = "accepted"
                attempt_trace["seed"] = candidate.seed_letters
                attempt_trace["repeat_share"] = candidate.repeat_share
                attempt_trace["full_word_count"] = len(candidate.full_word_set)
                attempt_trace["layout_word_count"] = len(candidate.layout_word_set)
                attempt_trace["repeat_source_word_count"] = len(candidate.repeat_source_word_set)
                attempt_trace["repeat_control_name"] = candidate.repeat_control_name
                _trace(trace_file, attempt_trace)
                break

            if candidate is None:
                failed_runs += INDEX_STEP
                run_summaries.append(f"- {attempts_used} 0 0 []")
                _trace(
                    trace_file,
                    {
                        "event": "run_end",
                        "run": run_index,
                        "status": "failure",
                        "rejected_by_reason": dict(rejected_by_reason),
                    },
                )
                if progress_callback is not None:
                    progress_callback(run_index)
                continue

            successful_runs += INDEX_STEP
            state.previous_crossword_word_set = set(candidate.layout_word_set)
            for word in candidate.full_word_set:
                frequency[word] += INDEX_STEP
            full_words_sorted = _sorted_words(candidate.full_word_set)
            run_summaries.append(
                f"{candidate.seed_letters} "
                f"{attempts_used} "
                f"{len(full_words_sorted)} "
                f"{len(candidate.layout_word_set)} "
                f"[{', '.join(full_words_sorted)}]"
            )

            _trace(
                trace_file,
                {
                    "event": "run_end",
                    "run": run_index,
                    "status": "success",
                    "seed": candidate.seed_letters,
                    "repeat_share": candidate.repeat_share,
                    "repeat_control_name": candidate.repeat_control_name,
                    "full_word_count": len(candidate.full_word_set),
                    "layout_word_count": len(candidate.layout_word_set),
                    "repeat_source_word_count": len(candidate.repeat_source_word_set),
                    "full_words": _sorted_words(candidate.full_word_set),
                    "layout_words": _sorted_words(candidate.layout_word_set),
                    "repeat_source_words": _sorted_words(candidate.repeat_source_word_set),
                    "rejected_by_reason": dict(rejected_by_reason),
                },
            )
            if progress_callback is not None:
                progress_callback(run_index)
    finally:
        if trace_file is not None:
            trace_file.close()

    return Mode005SimulationResult(
        frequency=frequency,
        successful_runs=successful_runs,
        failed_runs=failed_runs,
        timing_stats=timing_stats,
        crosswords_generated_from_stats=stats.crosswords_generated,
        run_summaries=tuple(run_summaries),
    )


def build_mode005_timing_header_lines(result: Mode005SimulationResult) -> list[str]:
    lines = []
    total_seconds = sum(result.timing_stats.totals_seconds.values())
    lines.append(f"# mode005_timing_total_ms={total_seconds * MILLISECONDS_PER_SECOND:.3f}")
    for phase in sorted(result.timing_stats.totals_seconds.keys()):
        total = result.timing_stats.totals_seconds[phase]
        count = result.timing_stats.counts.get(phase, ORIGIN_INDEX)
        average = total / count if count > ORIGIN_INDEX else ZERO_FLOAT
        lines.append(f"# mode005_timing_{phase}_total_ms={total * MILLISECONDS_PER_SECOND:.3f}")
        lines.append(f"# mode005_timing_{phase}_avg_ms={average * MILLISECONDS_PER_SECOND:.3f}")
        lines.append(f"# mode005_timing_{phase}_calls={count}")
    return lines


def _run_single_attempt(
    dictionary: list[str],
    rng: random.Random,
    config: Mode005Config,
    hooks: Mode005Hooks,
    state: Mode005State,
    top_frequent_word_set: set[str],
    timing_stats: TimingStats,
    trace_file,
    run_index: int,
    attempt_index: int,
) -> tuple[Mode005Candidate | None, RejectDecision | None]:
    seed_init_start = time.perf_counter()
    seed_length_range = hooks.seed_letter_length_range(config.max_letter_set_size)
    seed_letters = _generate_initial_seed_letters(seed_length_range, rng)
    seed_vowel_count = _count_vowels(seed_letters)
    seed_consonant_count = len(seed_letters) - seed_vowel_count
    remaining_addition_alphabet = _build_remaining_addition_alphabet(seed_letters)
    blocked_return_letters: set[str] = set()
    seed_length = len(seed_letters)
    _record_phase_timing(
        timing_stats=timing_stats,
        phase_name=PHASE_SEED_INIT,
        start_time=seed_init_start,
    )
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_BUILD_SEED,
        step_name=STEP_BUILD_SEED,
        payload={
            "seed": seed_letters,
            "seed_length": seed_length,
            "seed_length_range_min": min(seed_length_range),
            "seed_length_range_max": max(seed_length_range),
            "seed_vowel_count": seed_vowel_count,
            "seed_consonant_count": seed_consonant_count,
            "seed_vowel_distribution": {
                str(MIN_SEED_VOWEL_COUNT): VOWEL_WEIGHT_FOR_TWO,
                str(MID_SEED_VOWEL_COUNT): VOWEL_WEIGHT_FOR_THREE,
                str(MAX_SEED_VOWEL_COUNT): VOWEL_WEIGHT_FOR_FOUR,
            },
            "remaining_addition_alphabet_size": len(remaining_addition_alphabet),
        },
    )

    if state.previous_crossword_word_set:
        repeat_control_set = set(state.previous_crossword_word_set)
        repeat_control_name = REPEAT_CONTROL_PREVIOUS_ROUND
    else:
        repeat_control_set = set(top_frequent_word_set)
        repeat_control_name = REPEAT_CONTROL_TOP_FREQUENT

    for swap_cycle in range(INDEX_STEP, config.max_letter_swap_cycles + INDEX_STEP):
        full_set_start = time.perf_counter()
        full_word_set = _build_full_word_set(
            seed_letters=seed_letters,
            dictionary=dictionary,
            hooks=hooks,
            min_word_length=config.min_word_length,
        )
        _record_phase_timing(
            timing_stats=timing_stats,
            phase_name=PHASE_FULL_SET,
            start_time=full_set_start,
        )
        _trace_attempt_step(
            trace_file=trace_file,
            run_index=run_index,
            attempt_index=attempt_index,
            step_index=STEP_INDEX_BUILD_FULL_WORD_SET,
            step_name=STEP_BUILD_FULL_WORD_SET,
            payload={
                "swap_cycle": swap_cycle,
                "seed": seed_letters,
                "full_word_count": len(full_word_set),
                "full_words": _sorted_words(full_word_set),
                "minimum_full_word_count_required": config.min_crossword_word_count,
                "full_word_count_ok": len(full_word_set) >= config.min_crossword_word_count,
            },
        )
        if len(full_word_set) < config.min_crossword_word_count:
            return None, RejectDecision(
                reason=REJECT_FULL_WORD_SET_TOO_SMALL,
                step_index=STEP_INDEX_BUILD_FULL_WORD_SET,
                step_name=STEP_BUILD_FULL_WORD_SET,
                criterion=CRITERION_FULL_WORD_SET_TOO_SMALL,
            )

        repeat_start = time.perf_counter()
        repeat_source_word_set = full_word_set.intersection(repeat_control_set)
        repeat_share = len(repeat_source_word_set) / max(len(full_word_set), INDEX_STEP)
        passes_repeat_threshold = (
            repeat_share <= config.max_repeat_share_with_control_set
        )
        _record_phase_timing(
            timing_stats=timing_stats,
            phase_name=PHASE_REPEAT_ANALYSIS,
            start_time=repeat_start,
        )
        _trace_attempt_step(
            trace_file=trace_file,
            run_index=run_index,
            attempt_index=attempt_index,
            step_index=STEP_INDEX_BUILD_REPEAT_SOURCE,
            step_name=STEP_BUILD_REPEAT_SOURCE,
            payload={
                "swap_cycle": swap_cycle,
                "repeat_control_name": repeat_control_name,
                "repeat_control_word_count": len(repeat_control_set),
                "repeat_source_word_count": len(repeat_source_word_set),
                "repeat_source_words": _sorted_words(repeat_source_word_set),
                "repeat_share": repeat_share,
                "repeat_share_threshold": config.max_repeat_share_with_control_set,
                "passes_repeat_threshold": passes_repeat_threshold,
            },
        )

        if passes_repeat_threshold:
            layout_build_start = time.perf_counter()
            layout_word_set = _build_layout_word_set(
                full_word_set=full_word_set,
                rng=rng,
                hooks=hooks,
                min_word_length=config.min_word_length,
            )
            _record_phase_timing(
                timing_stats=timing_stats,
                phase_name=PHASE_LAYOUT_BUILD,
                start_time=layout_build_start,
            )
            _trace_attempt_step(
                trace_file=trace_file,
                run_index=run_index,
                attempt_index=attempt_index,
                step_index=STEP_INDEX_BUILD_LAYOUT_WORD_SET,
                step_name=STEP_BUILD_LAYOUT_WORD_SET,
                payload={
                    "swap_cycle": swap_cycle,
                    "layout_word_count": len(layout_word_set),
                    "layout_words": _sorted_words(layout_word_set),
                },
            )

            validation_start = time.perf_counter()
            layout_word_count_ok = len(layout_word_set) >= config.min_crossword_word_count
            _record_phase_timing(
                timing_stats=timing_stats,
                phase_name=PHASE_VALIDATION,
                start_time=validation_start,
            )
            _trace_attempt_step(
                trace_file=trace_file,
                run_index=run_index,
                attempt_index=attempt_index,
                step_index=STEP_INDEX_VALIDATE_LAYOUT,
                step_name=STEP_VALIDATE_LAYOUT,
                payload={
                    "swap_cycle": swap_cycle,
                    "layout_word_count": len(layout_word_set),
                    "minimum_layout_word_count": config.min_crossword_word_count,
                    "layout_word_count_ok": layout_word_count_ok,
                },
            )
            if layout_word_count_ok:
                return (
                    Mode005Candidate(
                        seed_letters=seed_letters,
                        full_word_set=frozenset(full_word_set),
                        layout_word_set=frozenset(layout_word_set),
                        repeat_source_word_set=frozenset(repeat_source_word_set),
                        repeat_share=repeat_share,
                        repeat_control_name=repeat_control_name,
                    ),
                    None,
                )
            return None, RejectDecision(
                reason=REJECT_LAYOUT_INVALID_AFTER_REPEAT_PASS,
                step_index=STEP_INDEX_VALIDATE_LAYOUT,
                step_name=STEP_VALIDATE_LAYOUT,
                criterion=CRITERION_LAYOUT_INVALID_AFTER_REPEAT_PASS,
            )

        mutation_start = time.perf_counter()
        removed_letter = _pick_letter_to_remove(
            seed_letters=seed_letters,
            repeat_source_word_set=repeat_source_word_set,
            rng=rng,
        )
        if removed_letter is None:
            _record_phase_timing(
                timing_stats=timing_stats,
                phase_name=PHASE_MUTATION,
                start_time=mutation_start,
            )
            _trace_attempt_step(
                trace_file=trace_file,
                run_index=run_index,
                attempt_index=attempt_index,
                step_index=STEP_INDEX_MUTATE_SEED,
                step_name=STEP_MUTATE_SEED,
                payload={
                    "swap_cycle": swap_cycle,
                    "seed_before": seed_letters,
                    "mutation_result": "failed_empty_seed",
                },
            )
            return None, RejectDecision(
                reason=REJECT_EMPTY_SEED,
                step_index=STEP_INDEX_MUTATE_SEED,
                step_name=STEP_MUTATE_SEED,
                criterion=CRITERION_EMPTY_SEED,
            )

        seed_before_mutation = seed_letters
        seed_after_removal = _remove_one_letter(seed_letters, removed_letter)
        blocked_return_letters.add(removed_letter)
        add_pool = _build_addition_pool(
            seed_letters=seed_after_removal,
            remaining_addition_alphabet=remaining_addition_alphabet,
            blocked_return_letters=blocked_return_letters,
        )
        if not add_pool:
            _record_phase_timing(
                timing_stats=timing_stats,
                phase_name=PHASE_MUTATION,
                start_time=mutation_start,
            )
            _trace_attempt_step(
                trace_file=trace_file,
                run_index=run_index,
                attempt_index=attempt_index,
                step_index=STEP_INDEX_MUTATE_SEED,
                step_name=STEP_MUTATE_SEED,
                payload={
                    "swap_cycle": swap_cycle,
                    "seed_before": seed_before_mutation,
                    "removed_letter": removed_letter,
                    "seed_after_removal": seed_after_removal,
                    "blocked_return_letters": sorted(blocked_return_letters),
                    "remaining_addition_alphabet_size": len(remaining_addition_alphabet),
                    "mutation_result": "failed_addition_pool_empty",
                },
            )
            return None, RejectDecision(
                reason=REJECT_ADDITION_POOL_EMPTY,
                step_index=STEP_INDEX_MUTATE_SEED,
                step_name=STEP_MUTATE_SEED,
                criterion=CRITERION_ADDITION_POOL_EMPTY,
            )

        added_letter = add_pool[rng.randrange(len(add_pool))]
        remaining_addition_alphabet.discard(added_letter)
        seed_letters = _shuffle_letters(seed_after_removal + added_letter, rng)
        _record_phase_timing(
            timing_stats=timing_stats,
            phase_name=PHASE_MUTATION,
            start_time=mutation_start,
        )
        _trace_attempt_step(
            trace_file=trace_file,
            run_index=run_index,
            attempt_index=attempt_index,
            step_index=STEP_INDEX_MUTATE_SEED,
            step_name=STEP_MUTATE_SEED,
            payload={
                "swap_cycle": swap_cycle,
                "seed_before": seed_before_mutation,
                "removed_letter": removed_letter,
                "added_letter": added_letter,
                "seed_after": seed_letters,
                "blocked_return_letters": sorted(blocked_return_letters),
                "remaining_addition_alphabet_size": len(remaining_addition_alphabet),
            },
        )

    return None, RejectDecision(
        reason=REJECT_SWAP_CYCLES_EXHAUSTED,
        step_index=STEP_INDEX_MUTATE_SEED,
        step_name=STEP_MUTATE_SEED,
        criterion=CRITERION_SWAP_CYCLES_EXHAUSTED,
    )


def _build_unique_dictionary_words(dictionary: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for raw_word in dictionary:
        word = raw_word.strip().upper()
        if not word:
            continue
        if any(char not in ALPHABET for char in word):
            continue
        if word in seen:
            continue
        seen.add(word)
        result.append(word)
    return result


def _build_top_frequent_word_set(
    dictionary_words: list[str],
    word_stats: dict[str, int],
    top_share: float,
) -> set[str]:
    if not dictionary_words:
        return set()

    take_count = max(
        INDEX_STEP,
        int(math.ceil(len(dictionary_words) * top_share)),
    )
    sorted_words = sorted(
        dictionary_words,
        key=lambda word: (-word_stats.get(word, ORIGIN_INDEX), word),
    )
    return set(sorted_words[:take_count])


def _generate_initial_seed_letters(
    seed_length_range: tuple[int, int],
    rng: random.Random,
) -> str:
    min_length, max_length = seed_length_range
    if min_length > max_length:
        min_length, max_length = max_length, min_length
    seed_length = rng.randint(min_length, max_length)
    vowel_count = _pick_seed_vowel_count(seed_length, rng)
    consonant_count = max(seed_length - vowel_count, ORIGIN_INDEX)
    letters = _pick_letters_from_pool(VOWELS, vowel_count, rng) + _pick_letters_from_pool(
        CONSONANTS,
        consonant_count,
        rng,
    )
    if len(letters) < seed_length:
        letters.extend(
            _pick_letters_from_pool(
                ALPHABET,
                seed_length - len(letters),
                rng,
            )
        )
    rng.shuffle(letters)
    return "".join(letters)


def _pick_seed_vowel_count(seed_length: int, rng: random.Random) -> int:
    weighted_options = [
        (MIN_SEED_VOWEL_COUNT, VOWEL_WEIGHT_FOR_TWO),
        (MID_SEED_VOWEL_COUNT, VOWEL_WEIGHT_FOR_THREE),
        (MAX_SEED_VOWEL_COUNT, VOWEL_WEIGHT_FOR_FOUR),
    ]
    allowed = [
        (count_value, weight_value)
        for count_value, weight_value in weighted_options
        if count_value <= seed_length
    ]
    if not allowed:
        return max(ORIGIN_INDEX, min(seed_length, MAX_SEED_VOWEL_COUNT))

    total_weight = sum(weight_value for _, weight_value in allowed)
    pick_value = rng.randrange(total_weight)
    cumulative_weight = ORIGIN_INDEX
    for count_value, weight_value in allowed:
        cumulative_weight += weight_value
        if pick_value < cumulative_weight:
            return count_value
    return allowed[-INDEX_STEP][ORIGIN_INDEX]


def _pick_letters_from_pool(
    pool: str,
    count: int,
    rng: random.Random,
) -> list[str]:
    if count < INDEX_STEP or not pool:
        return []
    return [pool[rng.randrange(len(pool))] for _ in range(count)]


def _count_vowels(seed_letters: str) -> int:
    return sum(INDEX_STEP for char in seed_letters if char in VOWELS)


def _build_full_word_set(
    seed_letters: str,
    dictionary: list[str],
    hooks: Mode005Hooks,
    min_word_length: int,
) -> set[str]:
    result: set[str] = set()
    raw_words = hooks.build_mini_dictionary(seed_letters, dictionary)
    for raw_word in raw_words:
        word = raw_word.strip().upper()
        if not word or len(word) < min_word_length:
            continue
        if any(char not in ALPHABET for char in word):
            continue
        result.add(word)
    return result


def _build_layout_word_set(
    full_word_set: set[str],
    rng: random.Random,
    hooks: Mode005Hooks,
    min_word_length: int,
) -> set[str]:
    result: set[str] = set()
    raw_words = hooks.build_layout_words_from_input(set(full_word_set), rng)
    for raw_word in raw_words:
        word = raw_word.strip().upper()
        if not word or len(word) < min_word_length:
            continue
        if any(char not in ALPHABET for char in word):
            continue
        result.add(word)
    return result


def _pick_letter_to_remove(
    seed_letters: str,
    repeat_source_word_set: set[str],
    rng: random.Random,
) -> str | None:
    if not seed_letters:
        return None

    seed_counts = Counter(seed_letters)
    singleton_letters = [letter for letter, count in seed_counts.items() if count == INDEX_STEP]
    singleton_consonants = [letter for letter in singleton_letters if letter in CONSONANTS]

    dominant_letter = _pick_dominant_consonant(
        repeat_source_word_set=repeat_source_word_set,
        singleton_consonants=singleton_consonants,
        rng=rng,
    )
    if dominant_letter is not None:
        return dominant_letter

    if singleton_consonants:
        return singleton_consonants[rng.randrange(len(singleton_consonants))]
    if singleton_letters:
        return singleton_letters[rng.randrange(len(singleton_letters))]
    return seed_letters[rng.randrange(len(seed_letters))]


def _pick_dominant_consonant(
    repeat_source_word_set: set[str],
    singleton_consonants: list[str],
    rng: random.Random,
) -> str | None:
    if not repeat_source_word_set or not singleton_consonants:
        return None

    singleton_set = set(singleton_consonants)
    consonant_counts: Counter[str] = Counter()
    for word in repeat_source_word_set:
        for char in word:
            if char in singleton_set:
                consonant_counts[char] += INDEX_STEP
    if not consonant_counts:
        return None

    max_count = max(consonant_counts.values())
    best_letters = [
        letter
        for letter, count in consonant_counts.items()
        if count == max_count
    ]
    return best_letters[rng.randrange(len(best_letters))]


def _remove_one_letter(seed_letters: str, letter_to_remove: str) -> str:
    chars = list(seed_letters)
    for index, letter in enumerate(chars):
        if letter == letter_to_remove:
            del chars[index]
            return "".join(chars)
    return seed_letters


def _shuffle_letters(letters: str, rng: random.Random) -> str:
    chars = list(letters)
    rng.shuffle(chars)
    return "".join(chars)


def _build_remaining_addition_alphabet(seed_letters: str) -> set[str]:
    current_letters = {char for char in seed_letters if char in ALPHABET}
    return {char for char in ALPHABET if char not in current_letters}


def _build_addition_pool(
    seed_letters: str,
    remaining_addition_alphabet: set[str],
    blocked_return_letters: set[str],
) -> list[str]:
    current_letters = {char for char in seed_letters if char in ALPHABET}
    pool = [
        char
        for char in sorted(remaining_addition_alphabet)
        if char not in blocked_return_letters and char not in current_letters
    ]
    return pool


def _parse_crosswords_generated_comment(comment: str) -> int | None:
    lowered = comment.lower()
    prefix = CROSSWORDS_GENERATED_COMMENT_PREFIX
    if not lowered.startswith(prefix):
        return None
    value_text = comment[len(prefix):].strip()
    if not value_text:
        return None
    try:
        value = int(value_text)
    except ValueError:
        return None
    if value < ORIGIN_INDEX:
        return None
    return value


def _validate_word_key(word: str, path: Path, line_number: int) -> None:
    if any(char not in ALPHABET for char in word):
        raise ValueError(
            f"Invalid word key at {path}:{line_number}. Expected only A-Z letters."
        )


def _sorted_words(words: Iterable[str]) -> list[str]:
    return sorted(word.strip().upper() for word in words if word.strip())


def _trace_attempt_step(
    trace_file,
    run_index: int,
    attempt_index: int,
    step_index: int,
    step_name: str,
    payload: dict[str, object],
) -> None:
    step_payload: dict[str, object] = {
        "event": "attempt_step",
        "run": run_index,
        "attempt": attempt_index,
        "step_index": step_index,
        "step_name": step_name,
    }
    step_payload.update(payload)
    _trace(trace_file, step_payload)


def _open_trace_file(trace_log_path: Path | None):
    if trace_log_path is None:
        return None
    trace_log_path.parent.mkdir(parents=True, exist_ok=True)
    return trace_log_path.open("w", encoding="utf-8")


def _trace(trace_file, payload: dict[str, object]) -> None:
    if trace_file is None:
        return

    event = str(payload.get("event", "event"))
    run_value = payload.get("run")
    attempt_value = payload.get("attempt")
    step_index = payload.get("step_index")
    step_name = payload.get("step_name")

    header_parts = [f"event={event}"]
    if run_value is not None:
        header_parts.append(f"run={run_value}")
    if attempt_value is not None:
        header_parts.append(f"attempt={attempt_value}")
    if step_index is not None:
        header_parts.append(f"step={step_index}")
    if step_name is not None:
        header_parts.append(f"step_name={step_name}")
    trace_file.write(" | ".join(header_parts) + "\n")

    ignored_keys = {"event", "run", "attempt", "step_index", "step_name"}
    for key in sorted(payload.keys()):
        if key in ignored_keys:
            continue
        trace_file.write(f"  {key}: {_format_trace_value(payload[key])}\n")
    trace_file.write("\n")


def _format_trace_value(value: object) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float):
        return f"{value:.6f}"
    if isinstance(value, list):
        if not value:
            return "[]"
        rendered = ", ".join(_format_trace_value(item) for item in value)
        return f"[{rendered}]"
    if isinstance(value, dict):
        if not value:
            return "{}"
        items = ", ".join(
            f"{key}={_format_trace_value(value[key])}"
            for key in sorted(value.keys())
        )
        return f"{{{items}}}"
    return str(value)


def _record_phase_timing(
    timing_stats: TimingStats,
    phase_name: str,
    start_time: float,
) -> None:
    elapsed = time.perf_counter() - start_time
    timing_stats.add(phase_name, elapsed)
