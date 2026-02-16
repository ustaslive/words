from __future__ import annotations

import random
import time
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable


ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
ALPHABET_SIZE = 26
ORD_A = ord("A")

INDEX_STEP = 1
ORIGIN_INDEX = 0
ONE_UNIT = 1.0
ZERO_FLOAT = 0.0
EPSILON_SCORE = 1e-12
MILLISECONDS_PER_SECOND = 1000.0

DEFAULT_EXCLUDE_MIN = 1
DEFAULT_EXCLUDE_MAX = 3
DEFAULT_OVERLAP_REJECT_THRESHOLD = 0.10
DEFAULT_SEED_SELECTION_APPROACH_ID = 0
SEED_SELECTION_APPROACH_RANDOM_ALPHABET = 0
SEED_SELECTION_APPROACH_RARE_WORD_MERGE = 1
DEFAULT_RARE_WORD_PICK_MULTIPLIER = 4
MIN_RARE_WORD_PICK_ATTEMPTS = 20
MAX_TRACE_REJECTED_SOURCE_WORDS = 40
MAX_TRACE_PICK_HISTORY = 80

WEIGHT_LAYOUT = 2.0
WEIGHT_RARE_FULL = 2.0
WEIGHT_MISSING = 1.0
WEIGHT_OVERLAP = 1.0
WEIGHT_RARE_LAYOUT = 1.0
WEIGHT_SEED_DIFF = 1.0

PHASE_EXCLUDE = "exclude_letters"
PHASE_SEED = "seed_generation"
PHASE_FULL_SET = "build_full_word_set"
PHASE_OVERLAP = "overlap_check"
PHASE_LAYOUT_INPUT = "prepare_layout_input"
PHASE_LAYOUT_BUILD = "layout_build"
PHASE_VALIDATION = "validation"
PHASE_SCORE = "score"
PHASE_COMPARE = "compare_best"

REJECT_EMPTY_FULL_SET = "empty_full_word_set"
REJECT_OVERLAP_THRESHOLD = "overlap_ratio_ge_threshold"
REJECT_EMPTY_LAYOUT_INPUT = "empty_layout_input_word_set"
REJECT_MIN_WORD_COUNT = "layout_words_below_minimum"
REJECT_UNUSED_SEED_LETTERS = "seed_letters_not_fully_used"

STEP_SELECT_EXCLUDED_LETTERS = "select_excluded_letters"
STEP_BUILD_SEED = "build_seed"
STEP_BUILD_FULL_WORD_SET = "build_full_word_set"
STEP_CHECK_OVERLAP = "check_overlap"
STEP_BUILD_LAYOUT_INPUT = "build_layout_input_word_set"
STEP_BUILD_LAYOUT_WORD_SET = "build_layout_word_set"
STEP_VALIDATE_LAYOUT = "validate_layout"
STEP_SCORE_CANDIDATE = "score_candidate"
STEP_COMPARE_CANDIDATE = "compare_candidate"

STEP_INDEX_SELECT_EXCLUDED_LETTERS = 1
STEP_INDEX_BUILD_SEED = 2
STEP_INDEX_BUILD_FULL_WORD_SET = 3
STEP_INDEX_CHECK_OVERLAP = 4
STEP_INDEX_BUILD_LAYOUT_INPUT = 5
STEP_INDEX_BUILD_LAYOUT_WORD_SET = 6
STEP_INDEX_VALIDATE_LAYOUT = 7
STEP_INDEX_SCORE_CANDIDATE = 8
STEP_INDEX_COMPARE_CANDIDATE = 9

CRITERION_FULL_WORD_SET_EMPTY = "full_word_set_is_empty"
CRITERION_OVERLAP_THRESHOLD = "overlap_ratio_meets_or_exceeds_threshold"
CRITERION_LAYOUT_INPUT_EMPTY = "layout_input_word_set_is_empty"
CRITERION_LAYOUT_WORD_COUNT = "layout_word_count_below_minimum"
CRITERION_UNUSED_SEED_LETTERS = "not_all_seed_letters_used_in_layout_words"


BuildMiniDictionaryFn = Callable[[str, Iterable[str]], list[str]]
BuildLayoutWordsFn = Callable[[set[str], random.Random], set[str]]
AreAllSeedLettersUsedFn = Callable[[str, set[str]], bool]
SeedLengthRangeFn = Callable[[int], tuple[int, int]]


@dataclass(frozen=True)
class Mode004Hooks:
    build_mini_dictionary: BuildMiniDictionaryFn
    build_layout_words_from_input: BuildLayoutWordsFn
    are_all_seed_letters_used: AreAllSeedLettersUsedFn
    seed_letter_length_range: SeedLengthRangeFn


@dataclass(frozen=True)
class Mode004Config:
    max_letter_set_size: int
    max_generation_attempts: int
    min_word_length: int
    min_crossword_word_count: int
    overlap_reject_threshold: float = DEFAULT_OVERLAP_REJECT_THRESHOLD
    exclude_min: int = DEFAULT_EXCLUDE_MIN
    exclude_max: int = DEFAULT_EXCLUDE_MAX
    seed_selection_approach_id: int = DEFAULT_SEED_SELECTION_APPROACH_ID


@dataclass
class Mode004State:
    previous_seed_letters: str = ""
    previous_full_word_set: set[str] = field(default_factory=set)
    previous_crossword_word_set: set[str] = field(default_factory=set)


@dataclass(frozen=True)
class ScoreParts:
    p_layout: float
    p_rare_full: float
    p_missing: float
    p_overlap: float
    p_rare_layout: float
    p_seed_diff: float
    score: float


@dataclass(frozen=True)
class Mode004Candidate:
    seed_letters: str
    full_word_set: frozenset[str]
    layout_input_word_set: frozenset[str]
    layout_word_set: frozenset[str]
    missing_words: frozenset[str]
    overlap_ratio: float
    score_parts: ScoreParts


@dataclass(frozen=True)
class RejectDecision:
    reason: str
    step_index: int
    step_name: str
    criterion: str


@dataclass(frozen=True)
class SeedSelectionResult:
    seed_letters: str
    exclude_count: int
    excluded_letters: str
    emit_step1: bool
    step1_payload: dict[str, object]
    step2_payload: dict[str, object]
    exclude_seconds: float
    seed_seconds: float


@dataclass
class TimingStats:
    totals_seconds: dict[str, float] = field(default_factory=dict)
    counts: dict[str, int] = field(default_factory=dict)

    def add(self, phase: str, seconds: float) -> None:
        self.totals_seconds[phase] = self.totals_seconds.get(phase, ZERO_FLOAT) + seconds
        self.counts[phase] = self.counts.get(phase, ORIGIN_INDEX) + INDEX_STEP


@dataclass(frozen=True)
class Mode004SimulationResult:
    frequency: Counter[str]
    successful_runs: int
    failed_runs: int
    timing_stats: TimingStats


def load_word_stats(path: Path) -> dict[str, int]:
    return _load_count_mapping(path, key_kind="word")


def load_letter_stats(path: Path) -> dict[str, int]:
    return _load_count_mapping(path, key_kind="letter")


def _load_count_mapping(path: Path, key_kind: str) -> dict[str, int]:
    if not path.exists():
        raise FileNotFoundError(f"Stats file not found: {path}")

    result: dict[str, int] = {}
    with path.open("r", encoding="utf-8") as input_file:
        for line_number, raw_line in enumerate(input_file, start=INDEX_STEP):
            stripped = raw_line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            if ":" not in stripped:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Expected '<key>:<count>'."
                )
            key_raw, count_raw = stripped.split(":", maxsplit=INDEX_STEP)
            key = key_raw.strip().upper()
            count_text = count_raw.strip()
            if not count_text:
                raise ValueError(
                    f"Invalid stats line at {path}:{line_number}. Missing count value."
                )
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
            if key_kind == "letter":
                _validate_letter_key(key, path, line_number)
            else:
                _validate_word_key(key, path, line_number)
            result[key] = count_value
    return result


def _validate_letter_key(key: str, path: Path, line_number: int) -> None:
    if len(key) != INDEX_STEP or key[ORIGIN_INDEX] not in ALPHABET:
        raise ValueError(
            f"Invalid letter key at {path}:{line_number}. Expected single A-Z letter."
        )


def _validate_word_key(key: str, path: Path, line_number: int) -> None:
    if not key or any(char not in ALPHABET for char in key):
        raise ValueError(
            f"Invalid word key at {path}:{line_number}. Expected only A-Z letters."
        )


def simulate_mode004_word_frequency(
    dictionary: list[str],
    runs: int,
    rng: random.Random,
    config: Mode004Config,
    hooks: Mode004Hooks,
    word_stats_path: Path,
    letter_stats_path: Path,
    trace_log_path: Path | None,
) -> Mode004SimulationResult:
    word_stats = load_word_stats(word_stats_path)
    letter_stats = load_letter_stats(letter_stats_path)

    frequency: Counter[str] = Counter()
    successful_runs = ORIGIN_INDEX
    failed_runs = ORIGIN_INDEX
    state = Mode004State()
    timing_stats = TimingStats()

    trace_file = _open_trace_file(trace_log_path)
    try:
        for run_index in range(INDEX_STEP, runs + INDEX_STEP):
            best_candidate: Mode004Candidate | None = None
            rejected_by_reason: Counter[str] = Counter()
            _trace(
                trace_file,
                {
                    "event": "run_start",
                    "run": run_index,
                    "previous_seed": state.previous_seed_letters,
                    "previous_full_word_count": len(state.previous_full_word_set),
                    "previous_crossword_word_count": len(state.previous_crossword_word_set),
                },
            )

            for attempt in range(INDEX_STEP, config.max_generation_attempts + INDEX_STEP):
                _trace(
                    trace_file,
                    {
                        "event": "attempt_start",
                        "run": run_index,
                        "attempt": attempt,
                        "previous_seed": state.previous_seed_letters,
                        "previous_full_word_count": len(state.previous_full_word_set),
                        "previous_crossword_word_count": len(state.previous_crossword_word_set),
                        "current_best_score": (
                            best_candidate.score_parts.score
                            if best_candidate is not None
                            else None
                        ),
                    },
                )
                attempt_trace: dict[str, object] = {
                    "event": "attempt_end",
                    "run": run_index,
                    "attempt": attempt,
                }
                candidate, reject_decision = _evaluate_candidate(
                    dictionary=dictionary,
                    rng=rng,
                    config=config,
                    hooks=hooks,
                    state=state,
                    word_stats=word_stats,
                    letter_stats=letter_stats,
                    timing_stats=timing_stats,
                    trace=attempt_trace,
                    trace_file=trace_file,
                    run_index=run_index,
                    attempt_index=attempt,
                )

                if candidate is None:
                    if reject_decision is None:
                        raise RuntimeError("Mode004 candidate rejected without reject decision.")
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
                            "attempt": attempt,
                            "reject_reason": reject_decision.reason,
                            "reject_step_index": reject_decision.step_index,
                            "reject_step_name": reject_decision.step_name,
                            "reject_criterion": reject_decision.criterion,
                            "seed": attempt_trace.get("seed"),
                            "full_word_count": attempt_trace.get("full_word_count"),
                            "layout_input_word_count": attempt_trace.get("layout_input_word_count"),
                            "layout_word_count": attempt_trace.get("layout_word_count"),
                            "overlap_ratio": attempt_trace.get("overlap_ratio"),
                        },
                    )
                    _trace(trace_file, attempt_trace)
                    continue

                compare_start = time.perf_counter()
                better, compare_reason = _is_candidate_better(candidate, best_candidate)
                _record_phase_timing(timing_stats, attempt_trace, PHASE_COMPARE, compare_start)
                _trace(
                    trace_file,
                    {
                        "event": "attempt_compare",
                        "run": run_index,
                        "attempt": attempt,
                        "step_index": STEP_INDEX_COMPARE_CANDIDATE,
                        "step_name": STEP_COMPARE_CANDIDATE,
                        "seed": candidate.seed_letters,
                        "candidate_score": candidate.score_parts.score,
                        "best_score_before": (
                            best_candidate.score_parts.score
                            if best_candidate is not None
                            else None
                        ),
                        "is_better": better,
                        "compare_reason": compare_reason,
                    },
                )
                if better:
                    best_candidate = candidate
                    attempt_trace["status"] = "accepted_as_best"
                else:
                    attempt_trace["status"] = "accepted_not_best"
                attempt_trace["candidate_score"] = candidate.score_parts.score
                _trace(trace_file, attempt_trace)

            if best_candidate is None:
                failed_runs += INDEX_STEP
                _trace(
                    trace_file,
                    {
                        "event": "run_end",
                        "run": run_index,
                        "status": "failure",
                        "rejected_by_reason": dict(rejected_by_reason),
                    },
                )
                continue

            successful_runs += INDEX_STEP
            for word in best_candidate.full_word_set:
                frequency[word] += INDEX_STEP
            state.previous_seed_letters = best_candidate.seed_letters
            state.previous_full_word_set = set(best_candidate.full_word_set)
            state.previous_crossword_word_set = set(best_candidate.layout_word_set)

            _trace(
                trace_file,
                {
                    "event": "run_end",
                    "run": run_index,
                    "status": "success",
                    "best_seed": best_candidate.seed_letters,
                    "best_score": best_candidate.score_parts.score,
                    "best_layout_word_count": len(best_candidate.layout_word_set),
                    "best_full_word_count": len(best_candidate.full_word_set),
                    "best_missing_word_count": len(best_candidate.missing_words),
                    "best_full_words": _sorted_words(best_candidate.full_word_set),
                    "best_layout_words": _sorted_words(best_candidate.layout_word_set),
                    "best_missing_words": _sorted_words(best_candidate.missing_words),
                    "rejected_by_reason": dict(rejected_by_reason),
                },
            )
    finally:
        if trace_file is not None:
            trace_file.close()

    return Mode004SimulationResult(
        frequency=frequency,
        successful_runs=successful_runs,
        failed_runs=failed_runs,
        timing_stats=timing_stats,
    )


def build_mode004_timing_header_lines(result: Mode004SimulationResult) -> list[str]:
    lines = []
    total_seconds = sum(result.timing_stats.totals_seconds.values())
    lines.append(f"# mode004_timing_total_ms={total_seconds * MILLISECONDS_PER_SECOND:.3f}")
    for phase in sorted(result.timing_stats.totals_seconds.keys()):
        total = result.timing_stats.totals_seconds[phase]
        count = result.timing_stats.counts.get(phase, ORIGIN_INDEX)
        average = total / count if count > ORIGIN_INDEX else ZERO_FLOAT
        lines.append(f"# mode004_timing_{phase}_total_ms={total * MILLISECONDS_PER_SECOND:.3f}")
        lines.append(f"# mode004_timing_{phase}_avg_ms={average * MILLISECONDS_PER_SECOND:.3f}")
        lines.append(f"# mode004_timing_{phase}_calls={count}")
    return lines


def _evaluate_candidate(
    dictionary: list[str],
    rng: random.Random,
    config: Mode004Config,
    hooks: Mode004Hooks,
    state: Mode004State,
    word_stats: dict[str, int],
    letter_stats: dict[str, int],
    timing_stats: TimingStats,
    trace: dict[str, object],
    trace_file,
    run_index: int,
    attempt_index: int,
) -> tuple[Mode004Candidate | None, RejectDecision | None]:
    seed_length_range = hooks.seed_letter_length_range(config.max_letter_set_size)

    seed_selection = _select_seed_letters(
        dictionary=dictionary,
        rng=rng,
        config=config,
        state=state,
        word_stats=word_stats,
        letter_stats=letter_stats,
        seed_length_range=seed_length_range,
    )
    timing_stats.add(PHASE_EXCLUDE, seed_selection.exclude_seconds)
    trace[f"{PHASE_EXCLUDE}_ms"] = seed_selection.exclude_seconds * MILLISECONDS_PER_SECOND
    trace["exclude_count"] = seed_selection.exclude_count
    trace["excluded_letters"] = seed_selection.excluded_letters
    if seed_selection.emit_step1:
        _trace_attempt_step(
            trace_file=trace_file,
            run_index=run_index,
            attempt_index=attempt_index,
            step_index=STEP_INDEX_SELECT_EXCLUDED_LETTERS,
            step_name=STEP_SELECT_EXCLUDED_LETTERS,
            payload=seed_selection.step1_payload,
        )

    seed_letters = seed_selection.seed_letters
    timing_stats.add(PHASE_SEED, seed_selection.seed_seconds)
    trace[f"{PHASE_SEED}_ms"] = seed_selection.seed_seconds * MILLISECONDS_PER_SECOND
    trace["seed"] = seed_letters
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_BUILD_SEED,
        step_name=STEP_BUILD_SEED,
        payload=seed_selection.step2_payload,
    )

    full_set_start = time.perf_counter()
    raw_words = hooks.build_mini_dictionary(seed_letters, dictionary)
    full_word_set: set[str] = set()
    ignored_short_words_count = ORIGIN_INDEX
    for raw_word in raw_words:
        candidate = raw_word.strip().upper()
        if not candidate:
            continue
        if len(candidate) < config.min_word_length:
            ignored_short_words_count += INDEX_STEP
            continue
        full_word_set.add(candidate)
    _record_phase_timing(timing_stats, trace, PHASE_FULL_SET, full_set_start)
    trace["full_word_count"] = len(full_word_set)
    trace["full_words"] = _sorted_words(full_word_set)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_BUILD_FULL_WORD_SET,
        step_name=STEP_BUILD_FULL_WORD_SET,
        payload={
            "seed": seed_letters,
            "raw_word_count": len(raw_words),
            "ignored_short_words_count": ignored_short_words_count,
            "min_word_length": config.min_word_length,
            "full_word_count": len(full_word_set),
            "full_words": _sorted_words(full_word_set),
        },
    )
    if not full_word_set:
        return None, RejectDecision(
            reason=REJECT_EMPTY_FULL_SET,
            step_index=STEP_INDEX_BUILD_FULL_WORD_SET,
            step_name=STEP_BUILD_FULL_WORD_SET,
            criterion=CRITERION_FULL_WORD_SET_EMPTY,
        )

    overlap_start = time.perf_counter()
    overlap_ratio = _compute_overlap_ratio(full_word_set, state.previous_full_word_set)
    overlap_words = full_word_set.intersection(state.previous_full_word_set)
    _record_phase_timing(timing_stats, trace, PHASE_OVERLAP, overlap_start)
    trace["overlap_ratio"] = overlap_ratio
    trace["overlap_word_count"] = len(overlap_words)
    trace["overlap_words"] = _sorted_words(overlap_words)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_CHECK_OVERLAP,
        step_name=STEP_CHECK_OVERLAP,
        payload={
            "current_seed": seed_letters,
            "previous_seed": state.previous_seed_letters,
            "current_full_word_count": len(full_word_set),
            "current_full_words": _sorted_words(full_word_set),
            "previous_full_word_count": len(state.previous_full_word_set),
            "previous_full_words": _sorted_words(state.previous_full_word_set),
            "overlap_ratio": overlap_ratio,
            "overlap_threshold": config.overlap_reject_threshold,
            "overlap_word_count": len(overlap_words),
            "overlap_words": _sorted_words(overlap_words),
            "passes_overlap_check": overlap_ratio < config.overlap_reject_threshold,
        },
    )
    if overlap_ratio >= config.overlap_reject_threshold:
        return None, RejectDecision(
            reason=REJECT_OVERLAP_THRESHOLD,
            step_index=STEP_INDEX_CHECK_OVERLAP,
            step_name=STEP_CHECK_OVERLAP,
            criterion=CRITERION_OVERLAP_THRESHOLD,
        )

    layout_input_start = time.perf_counter()
    layout_input_word_set = full_word_set - state.previous_crossword_word_set
    pre_marked_missing_words = full_word_set.intersection(state.previous_crossword_word_set)
    _record_phase_timing(timing_stats, trace, PHASE_LAYOUT_INPUT, layout_input_start)
    trace["layout_input_word_count"] = len(layout_input_word_set)
    trace["layout_input_words"] = _sorted_words(layout_input_word_set)
    trace["pre_marked_missing_word_count"] = len(pre_marked_missing_words)
    trace["pre_marked_missing_words"] = _sorted_words(pre_marked_missing_words)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_BUILD_LAYOUT_INPUT,
        step_name=STEP_BUILD_LAYOUT_INPUT,
        payload={
            "layout_input_word_count": len(layout_input_word_set),
            "layout_input_words": _sorted_words(layout_input_word_set),
            "pre_marked_missing_word_count": len(pre_marked_missing_words),
            "pre_marked_missing_words": _sorted_words(pre_marked_missing_words),
            "passes_layout_input_check": len(layout_input_word_set) > ORIGIN_INDEX,
        },
    )
    if not layout_input_word_set:
        return None, RejectDecision(
            reason=REJECT_EMPTY_LAYOUT_INPUT,
            step_index=STEP_INDEX_BUILD_LAYOUT_INPUT,
            step_name=STEP_BUILD_LAYOUT_INPUT,
            criterion=CRITERION_LAYOUT_INPUT_EMPTY,
        )

    layout_build_start = time.perf_counter()
    layout_word_set = hooks.build_layout_words_from_input(layout_input_word_set, rng)
    layout_word_set = {
        word.strip().upper()
        for word in layout_word_set
        if word.strip() and len(word.strip()) >= config.min_word_length
    }
    _record_phase_timing(timing_stats, trace, PHASE_LAYOUT_BUILD, layout_build_start)
    trace["layout_word_count"] = len(layout_word_set)
    trace["layout_words"] = _sorted_words(layout_word_set)
    dropped_by_layout = layout_input_word_set - layout_word_set
    trace["dropped_by_layout_word_count"] = len(dropped_by_layout)
    trace["dropped_by_layout_words"] = _sorted_words(dropped_by_layout)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_BUILD_LAYOUT_WORD_SET,
        step_name=STEP_BUILD_LAYOUT_WORD_SET,
        payload={
            "layout_word_count": len(layout_word_set),
            "layout_words": _sorted_words(layout_word_set),
            "dropped_by_layout_word_count": len(dropped_by_layout),
            "dropped_by_layout_words": _sorted_words(dropped_by_layout),
        },
    )

    validation_start = time.perf_counter()
    layout_word_count_ok = len(layout_word_set) >= config.min_crossword_word_count
    all_seed_letters_used = hooks.are_all_seed_letters_used(seed_letters, layout_word_set)
    unused_seed_letters = _find_unused_seed_letters(seed_letters, layout_word_set)
    _record_phase_timing(timing_stats, trace, PHASE_VALIDATION, validation_start)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_VALIDATE_LAYOUT,
        step_name=STEP_VALIDATE_LAYOUT,
        payload={
            "layout_word_count": len(layout_word_set),
            "minimum_layout_word_count": config.min_crossword_word_count,
            "layout_word_count_ok": layout_word_count_ok,
            "all_seed_letters_used": all_seed_letters_used,
            "unused_seed_letters": unused_seed_letters,
        },
    )
    if not layout_word_count_ok:
        return None, RejectDecision(
            reason=REJECT_MIN_WORD_COUNT,
            step_index=STEP_INDEX_VALIDATE_LAYOUT,
            step_name=STEP_VALIDATE_LAYOUT,
            criterion=CRITERION_LAYOUT_WORD_COUNT,
        )
    if not all_seed_letters_used:
        return None, RejectDecision(
            reason=REJECT_UNUSED_SEED_LETTERS,
            step_index=STEP_INDEX_VALIDATE_LAYOUT,
            step_name=STEP_VALIDATE_LAYOUT,
            criterion=CRITERION_UNUSED_SEED_LETTERS,
        )

    score_start = time.perf_counter()
    missing_words = full_word_set - layout_word_set
    score_parts = _build_score_parts(
        seed_letters=seed_letters,
        previous_seed_letters=state.previous_seed_letters,
        full_word_set=full_word_set,
        layout_input_word_set=layout_input_word_set,
        layout_word_set=layout_word_set,
        missing_words=missing_words,
        overlap_ratio=overlap_ratio,
        word_stats=word_stats,
    )
    _record_phase_timing(timing_stats, trace, PHASE_SCORE, score_start)
    trace["missing_word_count"] = len(missing_words)
    trace["score"] = score_parts.score
    trace["p_layout"] = score_parts.p_layout
    trace["p_rare_full"] = score_parts.p_rare_full
    trace["p_missing"] = score_parts.p_missing
    trace["p_overlap"] = score_parts.p_overlap
    trace["p_rare_layout"] = score_parts.p_rare_layout
    trace["p_seed_diff"] = score_parts.p_seed_diff
    trace["missing_words"] = _sorted_words(missing_words)
    _trace_attempt_step(
        trace_file=trace_file,
        run_index=run_index,
        attempt_index=attempt_index,
        step_index=STEP_INDEX_SCORE_CANDIDATE,
        step_name=STEP_SCORE_CANDIDATE,
        payload={
            "missing_word_count": len(missing_words),
            "missing_words": _sorted_words(missing_words),
            "score": score_parts.score,
            "p_layout": score_parts.p_layout,
            "p_rare_full": score_parts.p_rare_full,
            "p_missing": score_parts.p_missing,
            "p_overlap": score_parts.p_overlap,
            "p_rare_layout": score_parts.p_rare_layout,
            "p_seed_diff": score_parts.p_seed_diff,
            "weights": {
                "layout": WEIGHT_LAYOUT,
                "rare_full": WEIGHT_RARE_FULL,
                "missing": WEIGHT_MISSING,
                "overlap": WEIGHT_OVERLAP,
                "rare_layout": WEIGHT_RARE_LAYOUT,
                "seed_diff": WEIGHT_SEED_DIFF,
            },
        },
    )

    candidate = Mode004Candidate(
        seed_letters=seed_letters,
        full_word_set=frozenset(full_word_set),
        layout_input_word_set=frozenset(layout_input_word_set),
        layout_word_set=frozenset(layout_word_set),
        missing_words=frozenset(missing_words),
        overlap_ratio=overlap_ratio,
        score_parts=score_parts,
    )
    return candidate, None


def _select_excluded_letters(
    previous_seed_letters: str,
    exclude_count: int,
    letter_stats: dict[str, int],
) -> list[str]:
    previous_letters = {
        char
        for char in previous_seed_letters.strip().upper()
        if char in ALPHABET
    }
    if not previous_letters:
        return []
    ordered_letters = sorted(
        previous_letters,
        key=lambda char: (-letter_stats.get(char, ORIGIN_INDEX), char),
    )
    take_count = min(max(exclude_count, ORIGIN_INDEX), len(ordered_letters))
    return ordered_letters[:take_count]


def _select_seed_letters(
    dictionary: list[str],
    rng: random.Random,
    config: Mode004Config,
    state: Mode004State,
    word_stats: dict[str, int],
    letter_stats: dict[str, int],
    seed_length_range: tuple[int, int],
) -> SeedSelectionResult:
    if config.seed_selection_approach_id == SEED_SELECTION_APPROACH_RANDOM_ALPHABET:
        return _select_seed_letters_approach_random_alphabet(
            rng=rng,
            config=config,
            state=state,
            letter_stats=letter_stats,
            seed_length_range=seed_length_range,
        )
    if config.seed_selection_approach_id == SEED_SELECTION_APPROACH_RARE_WORD_MERGE:
        return _select_seed_letters_approach_rare_word_merge(
            dictionary=dictionary,
            rng=rng,
            config=config,
            state=state,
            word_stats=word_stats,
            seed_length_range=seed_length_range,
        )
    raise ValueError(
        "Unsupported mode004 seed selection approach id: "
        f"{config.seed_selection_approach_id}. Supported: "
        f"{SEED_SELECTION_APPROACH_RANDOM_ALPHABET}, {SEED_SELECTION_APPROACH_RARE_WORD_MERGE}."
    )


def _select_seed_letters_approach_random_alphabet(
    rng: random.Random,
    config: Mode004Config,
    state: Mode004State,
    letter_stats: dict[str, int],
    seed_length_range: tuple[int, int],
) -> SeedSelectionResult:
    exclude_start = time.perf_counter()
    exclude_count = rng.randint(config.exclude_min, config.exclude_max)
    previous_seed_letters = state.previous_seed_letters.strip().upper()
    previous_seed_unique = sorted(
        {char for char in previous_seed_letters if char in ALPHABET},
        key=lambda char: (-letter_stats.get(char, ORIGIN_INDEX), char),
    )
    excluded_letters = _select_excluded_letters(
        previous_seed_letters=state.previous_seed_letters,
        exclude_count=exclude_count,
        letter_stats=letter_stats,
    )
    exclude_seconds = time.perf_counter() - exclude_start

    seed_start = time.perf_counter()
    reduced_alphabet = [char for char in ALPHABET if char not in excluded_letters]
    if not reduced_alphabet:
        reduced_alphabet = list(ALPHABET)
    seed_letters = _generate_seed_letters(seed_length_range, reduced_alphabet, rng)
    seed_seconds = time.perf_counter() - seed_start
    step1_payload = {
        "seed_selection_approach_id": SEED_SELECTION_APPROACH_RANDOM_ALPHABET,
        "seed_selection_approach_name": "random_alphabet",
        "previous_seed": state.previous_seed_letters,
        "previous_seed_letters_sorted_by_frequency": previous_seed_unique,
        "previous_seed_letter_counts": {
            letter: letter_stats.get(letter, ORIGIN_INDEX)
            for letter in previous_seed_unique
        },
        "exclude_count_requested": exclude_count,
        "excluded_letters": "".join(excluded_letters),
        "excluded_letter_counts": {
            letter: letter_stats.get(letter, ORIGIN_INDEX)
            for letter in excluded_letters
        },
    }
    step2_payload = {
        "seed_selection_approach_id": SEED_SELECTION_APPROACH_RANDOM_ALPHABET,
        "seed_selection_approach_name": "random_alphabet",
        "seed": seed_letters,
        "seed_length": len(seed_letters),
        "seed_length_range_min": seed_length_range[ORIGIN_INDEX],
        "seed_length_range_max": seed_length_range[INDEX_STEP],
        "reduced_alphabet": "".join(reduced_alphabet),
        "reduced_alphabet_count": len(reduced_alphabet),
    }
    return SeedSelectionResult(
        seed_letters=seed_letters,
        exclude_count=exclude_count,
        excluded_letters="".join(excluded_letters),
        emit_step1=True,
        step1_payload=step1_payload,
        step2_payload=step2_payload,
        exclude_seconds=exclude_seconds,
        seed_seconds=seed_seconds,
    )


def _select_seed_letters_approach_rare_word_merge(
    dictionary: list[str],
    rng: random.Random,
    config: Mode004Config,
    state: Mode004State,
    word_stats: dict[str, int],
    seed_length_range: tuple[int, int],
) -> SeedSelectionResult:
    seed_start = time.perf_counter()
    min_seed_length, max_seed_length = seed_length_range
    if min_seed_length > max_seed_length:
        min_seed_length, max_seed_length = max_seed_length, min_seed_length

    source_words = _build_seed_source_word_pool(dictionary)
    available_words = list(source_words)
    selected_source_words: list[str] = []
    rejected_source_words_sample: list[str] = []
    rejected_source_words_total = ORIGIN_INDEX
    reset_round_count = ORIGIN_INDEX
    pick_history_sample: list[str] = []
    pick_history_total = ORIGIN_INDEX

    merged_counts = [ORIGIN_INDEX] * ALPHABET_SIZE
    merged_length = ORIGIN_INDEX
    pick_attempt_limit = max(
        MIN_RARE_WORD_PICK_ATTEMPTS,
        len(source_words) * DEFAULT_RARE_WORD_PICK_MULTIPLIER,
    )
    pick_attempts = ORIGIN_INDEX

    while (
        merged_length < min_seed_length
        and source_words
        and pick_attempts < pick_attempt_limit
    ):
        if not available_words:
            reset_round_count += INDEX_STEP
            pick_history_total += INDEX_STEP
            if len(pick_history_sample) < MAX_TRACE_PICK_HISTORY:
                pick_history_sample.append(
                    "pick=pool_exhausted "
                    f"before=[{','.join(selected_source_words)}] "
                    f"merged_length={merged_length} action=reset_round"
                )
            merged_counts = [ORIGIN_INDEX] * ALPHABET_SIZE
            merged_length = ORIGIN_INDEX
            selected_source_words = []
            available_words = list(source_words)
            continue

        pick_attempts += INDEX_STEP
        word = _pick_word_by_rarity(available_words, word_stats, rng)
        word_count_value = word_stats.get(word, ORIGIN_INDEX)
        before_words = list(selected_source_words)
        word_counts = _count_letter_matches(word)
        candidate_counts = _merge_letter_counts_by_max(merged_counts, word_counts)
        candidate_length = sum(candidate_counts)
        pick_history_total += INDEX_STEP
        if candidate_length > max_seed_length:
            rejected_source_words_total += INDEX_STEP
            if len(rejected_source_words_sample) < MAX_TRACE_REJECTED_SOURCE_WORDS:
                rejected_source_words_sample.append(
                    "pick="
                    f"{pick_attempts} before=[{','.join(before_words)}] "
                    f"picked={word}(count={word_count_value}) "
                    f"candidate_length={candidate_length} range={min_seed_length}..{max_seed_length} "
                    "action=reset_exceeds_range"
                )
            if len(pick_history_sample) < MAX_TRACE_PICK_HISTORY:
                pick_history_sample.append(
                    "pick="
                    f"{pick_attempts} before=[{','.join(before_words)}] "
                    f"picked={word}(count={word_count_value}) "
                    f"candidate_length={candidate_length} range={min_seed_length}..{max_seed_length} "
                    "action=reset_exceeds_range after=[]"
                )
            reset_round_count += INDEX_STEP
            merged_counts = [ORIGIN_INDEX] * ALPHABET_SIZE
            merged_length = ORIGIN_INDEX
            selected_source_words = []
            available_words = list(source_words)
            continue
        if candidate_length == merged_length:
            rejected_source_words_total += INDEX_STEP
            if len(rejected_source_words_sample) < MAX_TRACE_REJECTED_SOURCE_WORDS:
                rejected_source_words_sample.append(
                    "pick="
                    f"{pick_attempts} before=[{','.join(before_words)}] "
                    f"picked={word}(count={word_count_value}) "
                    f"candidate_length={candidate_length} range={min_seed_length}..{max_seed_length} "
                    "action=skip_no_progress"
                )
            if len(pick_history_sample) < MAX_TRACE_PICK_HISTORY:
                pick_history_sample.append(
                    "pick="
                    f"{pick_attempts} before=[{','.join(before_words)}] "
                    f"picked={word}(count={word_count_value}) "
                    f"candidate_length={candidate_length} range={min_seed_length}..{max_seed_length} "
                    f"action=skip_no_progress after=[{','.join(before_words)}]"
                )
            available_words.remove(word)
            continue
        merged_counts = candidate_counts
        merged_length = candidate_length
        selected_source_words.append(word)
        available_words.remove(word)
        if len(pick_history_sample) < MAX_TRACE_PICK_HISTORY:
            pick_history_sample.append(
                "pick="
                f"{pick_attempts} before=[{','.join(before_words)}] "
                f"picked={word}(count={word_count_value}) "
                f"candidate_length={candidate_length} range={min_seed_length}..{max_seed_length} "
                f"action=apply after=[{','.join(selected_source_words)}]"
            )

    seed_letters = _build_seed_from_counts(merged_counts, rng)
    fallback_used = False
    fallback_reason = ""
    if len(seed_letters) < min_seed_length:
        fallback_used = True
        fallback_reason = "rare_word_merge_insufficient_letters_fallback_to_random_alphabet"
        fallback_result = _select_seed_letters_approach_random_alphabet(
            rng=rng,
            config=config,
            state=state,
            letter_stats={},
            seed_length_range=seed_length_range,
        )
        seed_letters = fallback_result.seed_letters
        exclude_count = fallback_result.exclude_count
        excluded_letters = fallback_result.excluded_letters
        reduced_alphabet = fallback_result.step2_payload.get("reduced_alphabet", "")
        reduced_alphabet_count = fallback_result.step2_payload.get("reduced_alphabet_count", 0)
    else:
        exclude_count = ORIGIN_INDEX
        excluded_letters = ""
        reduced_alphabet = ""
        reduced_alphabet_count = ORIGIN_INDEX
    seed_seconds = time.perf_counter() - seed_start

    selected_source_word_counts = {
        word: word_stats.get(word, ORIGIN_INDEX)
        for word in selected_source_words
    }
    step1_payload = {
        "seed_selection_approach_id": SEED_SELECTION_APPROACH_RARE_WORD_MERGE,
    }
    step2_payload = {
        "seed_selection_approach_id": SEED_SELECTION_APPROACH_RARE_WORD_MERGE,
        "seed_selection_approach_name": "rare_word_merge",
        "seed": seed_letters,
        "seed_length": len(seed_letters),
        "seed_length_range_min": min_seed_length,
        "seed_length_range_max": max_seed_length,
        "seed_length_rule": "accept_any_length_within_range",
        "final_merged_length": len(seed_letters),
        "source_word_pool_size": len(source_words),
        "pick_attempt_limit": pick_attempt_limit,
        "pick_attempts_used": pick_attempts,
        "reset_round_count": reset_round_count,
        "pick_history_count": pick_history_total,
        "pick_history_sample": pick_history_sample,
        "pick_history_sample_truncated": (
            pick_history_total > len(pick_history_sample)
        ),
        "selected_source_words": selected_source_words,
        "selected_source_word_counts": selected_source_word_counts,
        "rejected_source_word_count": rejected_source_words_total,
        "rejected_source_words_sample": rejected_source_words_sample,
        "rejected_source_words_sample_truncated": (
            rejected_source_words_total > len(rejected_source_words_sample)
        ),
        "fallback_used": fallback_used,
        "fallback_reason": fallback_reason,
        "reduced_alphabet": reduced_alphabet,
        "reduced_alphabet_count": reduced_alphabet_count,
    }
    return SeedSelectionResult(
        seed_letters=seed_letters,
        exclude_count=exclude_count,
        excluded_letters=excluded_letters,
        emit_step1=False,
        step1_payload=step1_payload,
        step2_payload=step2_payload,
        exclude_seconds=ZERO_FLOAT,
        seed_seconds=seed_seconds,
    )


def _build_seed_source_word_pool(dictionary: list[str]) -> list[str]:
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


def _pick_word_by_rarity(
    words: list[str],
    word_stats: dict[str, int],
    rng: random.Random,
) -> str:
    if len(words) == INDEX_STEP:
        return words[ORIGIN_INDEX]
    min_count = min(word_stats.get(word, ORIGIN_INDEX) for word in words)
    rarest_words = [word for word in words if word_stats.get(word, ORIGIN_INDEX) == min_count]
    return rarest_words[rng.randrange(len(rarest_words))]


def _merge_letter_counts_by_max(base_counts: list[int], word_counts: list[int]) -> list[int]:
    merged = [ORIGIN_INDEX] * ALPHABET_SIZE
    for index in range(ALPHABET_SIZE):
        merged[index] = max(base_counts[index], word_counts[index])
    return merged


def _build_seed_from_counts(counts: list[int], rng: random.Random) -> str:
    letters: list[str] = []
    for index in range(ALPHABET_SIZE):
        count = counts[index]
        if count <= ORIGIN_INDEX:
            continue
        letters.extend([chr(ORD_A + index)] * count)
    rng.shuffle(letters)
    return "".join(letters)


def _generate_seed_letters(
    seed_length_range: tuple[int, int],
    reduced_alphabet: list[str],
    rng: random.Random,
) -> str:
    min_length, max_length = seed_length_range
    if min_length > max_length:
        min_length, max_length = max_length, min_length
    seed_length = rng.randint(min_length, max_length)
    if seed_length <= len(reduced_alphabet):
        letters = rng.sample(reduced_alphabet, seed_length)
    else:
        letters = [rng.choice(reduced_alphabet) for _ in range(seed_length)]
    rng.shuffle(letters)
    return "".join(letters)


def _compute_overlap_ratio(current: set[str], previous: set[str]) -> float:
    denominator = max(len(current), INDEX_STEP)
    shared = len(current.intersection(previous))
    return shared / denominator


def _build_score_parts(
    seed_letters: str,
    previous_seed_letters: str,
    full_word_set: set[str],
    layout_input_word_set: set[str],
    layout_word_set: set[str],
    missing_words: set[str],
    overlap_ratio: float,
    word_stats: dict[str, int],
) -> ScoreParts:
    p_layout = len(layout_word_set) / max(len(layout_input_word_set), INDEX_STEP)
    p_rare_full = _average_rarity(full_word_set, word_stats)
    p_missing = ONE_UNIT - (len(missing_words) / max(len(full_word_set), INDEX_STEP))
    p_overlap = ONE_UNIT - overlap_ratio
    p_rare_layout = _average_rarity(layout_word_set, word_stats)
    p_seed_diff = _seed_difference_ratio(seed_letters, previous_seed_letters)

    score = (
        WEIGHT_LAYOUT * p_layout
        + WEIGHT_RARE_FULL * p_rare_full
        + WEIGHT_MISSING * p_missing
        + WEIGHT_OVERLAP * p_overlap
        + WEIGHT_RARE_LAYOUT * p_rare_layout
        + WEIGHT_SEED_DIFF * p_seed_diff
    )
    return ScoreParts(
        p_layout=p_layout,
        p_rare_full=p_rare_full,
        p_missing=p_missing,
        p_overlap=p_overlap,
        p_rare_layout=p_rare_layout,
        p_seed_diff=p_seed_diff,
        score=score,
    )


def _average_rarity(words: set[str], word_stats: dict[str, int]) -> float:
    if not words:
        return ZERO_FLOAT
    rarity_sum = ZERO_FLOAT
    for word in words:
        count_value = word_stats.get(word, ORIGIN_INDEX)
        rarity_sum += ONE_UNIT / (ONE_UNIT + float(count_value))
    return rarity_sum / float(len(words))


def _seed_difference_ratio(seed_letters: str, previous_seed_letters: str) -> float:
    current = seed_letters.strip().upper()
    previous = previous_seed_letters.strip().upper()
    if not previous:
        return ONE_UNIT
    current_counts = _count_letter_matches(current)
    previous_counts = _count_letter_matches(previous)
    shared_count = ORIGIN_INDEX
    for index in range(ALPHABET_SIZE):
        shared_count += min(current_counts[index], previous_counts[index])
    denominator = max(len(current), len(previous), INDEX_STEP)
    return ONE_UNIT - (shared_count / denominator)


def _count_letter_matches(word: str) -> list[int]:
    counts = [ORIGIN_INDEX] * ALPHABET_SIZE
    for char in word:
        if char not in ALPHABET:
            continue
        counts[ord(char) - ORD_A] += INDEX_STEP
    return counts


def _find_unused_seed_letters(seed_letters: str, words: set[str]) -> list[str]:
    used_letters = set()
    for word in words:
        used_letters.update(char for char in word if char in ALPHABET)
    seed_unique_letters = {char for char in seed_letters.strip().upper() if char in ALPHABET}
    return sorted(seed_unique_letters - used_letters)


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


def _is_candidate_better(
    candidate: Mode004Candidate,
    current_best: Mode004Candidate | None,
) -> tuple[bool, str]:
    if current_best is None:
        return True, "no_current_best"
    if candidate.score_parts.score > current_best.score_parts.score + EPSILON_SCORE:
        return True, "higher_total_score"
    if abs(candidate.score_parts.score - current_best.score_parts.score) <= EPSILON_SCORE:
        if len(candidate.layout_word_set) != len(current_best.layout_word_set):
            if len(candidate.layout_word_set) > len(current_best.layout_word_set):
                return True, "tie_break_layout_word_count"
            return False, "tie_break_layout_word_count"
        if candidate.overlap_ratio != current_best.overlap_ratio:
            if candidate.overlap_ratio < current_best.overlap_ratio:
                return True, "tie_break_overlap_ratio"
            return False, "tie_break_overlap_ratio"
        if candidate.seed_letters < current_best.seed_letters:
            return True, "tie_break_seed_lexicographic"
        return False, "tie_break_seed_lexicographic"
    return False, "lower_total_score"


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
        value = payload[key]
        trace_file.write(f"  {key}: {_format_trace_value(value)}\n")
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
    trace: dict[str, object],
    phase_name: str,
    start_time: float,
) -> None:
    elapsed = time.perf_counter() - start_time
    timing_stats.add(phase_name, elapsed)
    timing_field = f"{phase_name}_ms"
    trace[timing_field] = elapsed * MILLISECONDS_PER_SECOND
