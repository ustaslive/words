#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import stat
import tempfile
from dataclasses import dataclass
from pathlib import Path


PROJECT_ROOT_PARENT_INDEX = 1
PROJECT_ROOT = Path(__file__).resolve().parents[PROJECT_ROOT_PARENT_INDEX]
DEFAULT_DICTIONARY_PATH = PROJECT_ROOT / "app/src/main/assets/words.txt"
DEFAULT_FORBIDDEN_PATH = PROJECT_ROOT / "app/src/main/assets/forbidden_words.txt"
DEFAULT_REVIEW_DIRECTORY = PROJECT_ROOT / "data/dictionary_updates"
REVIEW_FILE_PATTERN = "*_word_review.txt"

SECTION_DICTIONARY_ADD = "dictionary.add"
SECTION_DICTIONARY_REMOVE = "dictionary.remove"
SECTION_FORBIDDEN_ADD = "forbidden.add"
SECTION_FORBIDDEN_REMOVE = "forbidden.remove"
VALID_SECTIONS = {
    SECTION_DICTIONARY_ADD,
    SECTION_DICTIONARY_REMOVE,
    SECTION_FORBIDDEN_ADD,
    SECTION_FORBIDDEN_REMOVE,
}
WORD_AND_REST_MAX_SPLIT = 1
WORD_PATTERN = re.compile(r"^[a-z]+$")


class ReviewFormatError(ValueError):
    pass


@dataclass(frozen=True)
class ReviewDecisions:
    dictionary_add: frozenset[str]
    dictionary_remove: frozenset[str]
    forbidden_add: frozenset[str]
    forbidden_remove: frozenset[str]


@dataclass(frozen=True)
class UpdatePlan:
    dictionary_words: tuple[str, ...]
    forbidden_words: tuple[str, ...]
    dictionary_added: tuple[str, ...]
    dictionary_removed: tuple[str, ...]
    dictionary_already_present: tuple[str, ...]
    dictionary_already_absent: tuple[str, ...]
    forbidden_added: tuple[str, ...]
    forbidden_removed: tuple[str, ...]
    forbidden_already_present: tuple[str, ...]
    forbidden_already_absent: tuple[str, ...]

    @property
    def dictionary_changed(self) -> bool:
        return bool(self.dictionary_added or self.dictionary_removed)

    @property
    def forbidden_changed(self) -> bool:
        return bool(self.forbidden_added or self.forbidden_removed)


def parse_review(path: Path) -> ReviewDecisions:
    section_words = {section: set() for section in VALID_SECTIONS}
    current_section: str | None = None

    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8").splitlines(),
        start=1,
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1].strip()
            if section not in VALID_SECTIONS:
                expected = ", ".join(sorted(VALID_SECTIONS))
                raise ReviewFormatError(
                    f"{path}:{line_number}: unknown section {line!r}; "
                    f"expected one of: {expected}."
                )
            current_section = section
            continue
        if current_section is None:
            raise ReviewFormatError(
                f"{path}:{line_number}: word appears before a section header."
            )

        word = line.split(maxsplit=WORD_AND_REST_MAX_SPLIT)[0].lower()
        if WORD_PATTERN.fullmatch(word) is None:
            raise ReviewFormatError(
                f"{path}:{line_number}: invalid English word {word!r}; "
                "use ASCII letters only and put a space before the note."
            )
        section_words[current_section].add(word)

    conflicting_words = (
        section_words[SECTION_DICTIONARY_ADD]
        & section_words[SECTION_DICTIONARY_REMOVE]
    )
    if conflicting_words:
        words = ", ".join(sorted(conflicting_words))
        raise ReviewFormatError(
            f"Words cannot be both added to and removed from the dictionary: {words}."
        )
    conflicting_forbidden_words = (
        section_words[SECTION_FORBIDDEN_ADD]
        & section_words[SECTION_FORBIDDEN_REMOVE]
    )
    if conflicting_forbidden_words:
        words = ", ".join(sorted(conflicting_forbidden_words))
        raise ReviewFormatError(
            "Words cannot be both added to and removed from the forbidden "
            f"dictionary: {words}."
        )

    return ReviewDecisions(
        dictionary_add=frozenset(section_words[SECTION_DICTIONARY_ADD]),
        dictionary_remove=frozenset(section_words[SECTION_DICTIONARY_REMOVE]),
        forbidden_add=frozenset(section_words[SECTION_FORBIDDEN_ADD]),
        forbidden_remove=frozenset(section_words[SECTION_FORBIDDEN_REMOVE]),
    )


def load_word_list(path: Path) -> list[str]:
    words = [
        line.strip().lower()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    invalid_words = sorted(
        {word for word in words if WORD_PATTERN.fullmatch(word) is None}
    )
    if invalid_words:
        invalid = ", ".join(invalid_words)
        raise ValueError(f"{path} contains invalid words: {invalid}.")
    if len(words) != len(set(words)):
        raise ValueError(f"{path} contains duplicate words.")
    return words


def insert_in_lexical_position(words: list[str], additions: set[str]) -> list[str]:
    result = list(words)
    for word in sorted(additions):
        insertion_index = next(
            (index for index, existing in enumerate(result) if existing > word),
            len(result),
        )
        result.insert(insertion_index, word)
    return result


def build_update_plan(
    decisions: ReviewDecisions,
    dictionary_words: list[str],
    forbidden_words: list[str],
) -> UpdatePlan:
    # TODO: Analyze and report cross-dictionary conflicts, including words that
    # are present in both the main and forbidden dictionaries. Decide whether
    # this check covers only reviewed words or the complete dictionaries, how
    # conflicts are displayed, and whether they should block --apply.
    dictionary_set = set(dictionary_words)
    forbidden_set = set(forbidden_words)

    dictionary_added = decisions.dictionary_add - dictionary_set
    dictionary_removed = decisions.dictionary_remove & dictionary_set
    dictionary_kept = [
        word for word in dictionary_words if word not in decisions.dictionary_remove
    ]
    updated_dictionary = insert_in_lexical_position(
        dictionary_kept,
        set(dictionary_added),
    )

    forbidden_added = decisions.forbidden_add - forbidden_set
    forbidden_removed = decisions.forbidden_remove & forbidden_set
    forbidden_kept = [
        word for word in forbidden_words if word not in decisions.forbidden_remove
    ]
    updated_forbidden = insert_in_lexical_position(
        forbidden_kept,
        set(forbidden_added),
    )

    return UpdatePlan(
        dictionary_words=tuple(updated_dictionary),
        forbidden_words=tuple(updated_forbidden),
        dictionary_added=tuple(sorted(dictionary_added)),
        dictionary_removed=tuple(sorted(dictionary_removed)),
        dictionary_already_present=tuple(
            sorted(decisions.dictionary_add & dictionary_set)
        ),
        dictionary_already_absent=tuple(
            sorted(decisions.dictionary_remove - dictionary_set)
        ),
        forbidden_added=tuple(sorted(forbidden_added)),
        forbidden_removed=tuple(sorted(forbidden_removed)),
        forbidden_already_present=tuple(
            sorted(decisions.forbidden_add & forbidden_set)
        ),
        forbidden_already_absent=tuple(
            sorted(decisions.forbidden_remove - forbidden_set)
        ),
    )


def render_words(words: tuple[str, ...]) -> str:
    if not words:
        return "none"
    return ", ".join(words)


def print_word_group(label: str, words: tuple[str, ...]) -> None:
    print(f"  {label} ({len(words)}):")
    if not words:
        print("    none")
        return
    for word in words:
        print(f"    {word}")


def print_plan(plan: UpdatePlan) -> None:
    print("Dictionary:")
    print_word_group("add", plan.dictionary_added)
    print_word_group("remove", plan.dictionary_removed)
    print_word_group("already present", plan.dictionary_already_present)
    print_word_group("already absent", plan.dictionary_already_absent)
    print("Forbidden dictionary:")
    print_word_group("add", plan.forbidden_added)
    print_word_group("remove", plan.forbidden_removed)
    print_word_group("already present", plan.forbidden_already_present)
    print_word_group("already absent", plan.forbidden_already_absent)


def write_word_list_atomically(path: Path, words: tuple[str, ...]) -> None:
    temporary_path: Path | None = None
    destination_mode = stat.S_IMODE(path.stat().st_mode)
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as handle:
            handle.write("\n".join(words) + "\n")
            temporary_path = Path(handle.name)
        temporary_path.chmod(destination_mode)
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()


def find_latest_review_file(directory: Path) -> Path | None:
    candidates = sorted(
        path.resolve()
        for path in directory.glob(REVIEW_FILE_PATTERN)
        if path.is_file()
    )
    if not candidates:
        return None
    return candidates[-1]


def resolve_review_file(
    requested_path: Path | None,
    current_directory: Path,
) -> Path:
    if requested_path is not None:
        resolved_path = requested_path.resolve()
        if not resolved_path.is_file():
            raise FileNotFoundError(f"Review file not found: {requested_path}")
        return resolved_path

    current_review = find_latest_review_file(current_directory)
    if current_review is not None:
        return current_review

    default_review = find_latest_review_file(DEFAULT_REVIEW_DIRECTORY)
    if default_review is not None:
        return default_review

    raise FileNotFoundError(
        f"No {REVIEW_FILE_PATTERN} file found in {current_directory} "
        f"or {DEFAULT_REVIEW_DIRECTORY}."
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Preview or apply a reviewed word list to the main and forbidden "
            "dictionaries. Notes after the first whitespace are ignored."
        ),
        epilog=(
            "Examples:\n"
            "  apply_dictionary_review.py\n"
            "      Automatically preview the newest *_word_review.txt file.\n"
            "  apply_dictionary_review.py 2026-07-25_word_review.txt\n"
            "      Preview a specific review file.\n"
            "  apply_dictionary_review.py --apply\n"
            "      Apply the newest review file after showing the plan."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "review_file",
        nargs="?",
        type=Path,
        metavar="REVIEW_FILE",
        help=(
            "Optional path to a review file. If omitted, the newest "
            "*_word_review.txt is selected from the current directory, then "
            f"from {DEFAULT_REVIEW_DIRECTORY}."
        ),
    )
    parser.add_argument(
        "--dictionary",
        type=Path,
        default=DEFAULT_DICTIONARY_PATH,
        help=f"Main dictionary path (default: {DEFAULT_DICTIONARY_PATH}).",
    )
    parser.add_argument(
        "--forbidden",
        type=Path,
        default=DEFAULT_FORBIDDEN_PATH,
        help=f"Forbidden dictionary path (default: {DEFAULT_FORBIDDEN_PATH}).",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Write the planned changes. Without this flag, only preview them.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:
        review_file = resolve_review_file(args.review_file, Path.cwd())
        decisions = parse_review(review_file)
        dictionary_words = load_word_list(args.dictionary)
        forbidden_words = load_word_list(args.forbidden)
        plan = build_update_plan(decisions, dictionary_words, forbidden_words)
    except (OSError, ReviewFormatError, ValueError) as error:
        raise SystemExit(f"Error: {error}") from error

    print(f"Review file: {review_file}")
    print_plan(plan)
    shared_additions = decisions.dictionary_add & decisions.forbidden_add
    if shared_additions:
        print(
            "Warning: words requested in both dictionary.add and forbidden.add: "
            f"{render_words(tuple(sorted(shared_additions)))}"
        )

    if not args.apply:
        print("Preview only; no files changed. Re-run with --apply to write changes.")
        return

    if plan.dictionary_changed:
        write_word_list_atomically(args.dictionary, plan.dictionary_words)
        print(f"Updated: {args.dictionary}")
    else:
        print(f"Unchanged: {args.dictionary}")
    if plan.forbidden_changed:
        write_word_list_atomically(args.forbidden, plan.forbidden_words)
        print(f"Updated: {args.forbidden}")
    else:
        print(f"Unchanged: {args.forbidden}")


if __name__ == "__main__":
    main()
