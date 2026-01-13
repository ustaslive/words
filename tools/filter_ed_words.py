#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

ED_SUFFIX = "ed"
IED_SUFFIX = "ied"
CKED_SUFFIX = "cked"
ING_SUFFIX = "ing"
Y_SUFFIX = "y"
C_SUFFIX = "c"
E_SUFFIX = "e"

CK_ING_SUFFIX = "cking"

EVIDENCE_ING = "ing"
EVIDENCE_NONE = "none"
VALID_EVIDENCE = {EVIDENCE_ING, EVIDENCE_NONE}
MISSING_BASE_MIN_LENGTH = 3
DOUBLE_STEM_MIN_LENGTH = 2
INDEX_LAST = -1
INDEX_PENULT = -2
VOWELS = set("aeiou")
DOUBLE_CONSONANT_EXCLUDED = set("wxy")

DEFAULT_INPUT = Path("app/src/main/assets/words.txt")
DEFAULT_OUTPUT = Path("app/src/main/assets/words.no_ed.txt")
DEFAULT_REMOVED = Path("app/src/main/assets/words.removed_ed.txt")
DEFAULT_EVIDENCE = {EVIDENCE_ING}


def load_word_list(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    words: list[str] = []
    for line in lines:
        word = line.strip()
        if word:
            words.append(word)
    return words


def load_allow_list(path: Path | None) -> set[str]:
    if path is None:
        return set()
    return set(load_word_list(path))


def parse_evidence(value: str) -> set[str]:
    parts = [item.strip() for item in value.split(",") if item.strip()]
    if not parts:
        raise argparse.ArgumentTypeError("Evidence must be a non-empty list.")
    invalid = [item for item in parts if item not in VALID_EVIDENCE]
    if invalid:
        invalid_text = ", ".join(invalid)
        raise argparse.ArgumentTypeError(f"Unsupported evidence: {invalid_text}")
    return set(parts)


def has_verb_evidence(base: str, words: set[str], evidence: set[str]) -> bool:
    if EVIDENCE_NONE in evidence:
        return True
    if EVIDENCE_ING in evidence and f"{base}{ING_SUFFIX}" in words:
        return True
    return False


def has_verb_evidence_y_base(base: str, words: set[str], evidence: set[str]) -> bool:
    if EVIDENCE_NONE in evidence:
        return True
    if EVIDENCE_ING in evidence and f"{base}{ING_SUFFIX}" in words:
        return True
    return False


def has_verb_evidence_c_base(base: str, words: set[str], evidence: set[str]) -> bool:
    if EVIDENCE_NONE in evidence:
        return True
    if EVIDENCE_ING in evidence:
        if f"{base}{ING_SUFFIX}" in words:
            return True
        if base.endswith(C_SUFFIX):
            stem = base[:-len(C_SUFFIX)]
            if stem and f"{stem}{CK_ING_SUFFIX}" in words:
                return True
    return False


def has_verb_evidence_e_base(base: str, words: set[str], evidence: set[str]) -> bool:
    if EVIDENCE_NONE in evidence:
        return True
    if EVIDENCE_ING in evidence:
        if f"{base}{ING_SUFFIX}" in words:
            return True
        if base.endswith(E_SUFFIX):
            stem = base[:-len(E_SUFFIX)]
            if stem and f"{stem}{ING_SUFFIX}" in words:
                return True
    return False


def has_verb_evidence_doubled_base(
    base: str,
    words: set[str],
    evidence: set[str],
) -> bool:
    if EVIDENCE_NONE in evidence:
        return True
    if EVIDENCE_ING in evidence and base:
        doubled_ing = f"{base}{base[INDEX_LAST]}{ING_SUFFIX}"
        if doubled_ing in words:
            return True
        if f"{base}{ING_SUFFIX}" in words:
            return True
    return False


def should_remove_with_base(
    base: str,
    words: set[str],
    evidence: set[str],
    evidence_fn,
) -> bool:
    if not base:
        return False
    if base in words:
        return evidence_fn(base, words, evidence)
    if len(base) < MISSING_BASE_MIN_LENGTH:
        return False
    evidence_without_none = set(evidence) - {EVIDENCE_NONE}
    if not evidence_without_none:
        return False
    return evidence_fn(base, words, evidence_without_none)


def is_double_consonant(base: str) -> bool:
    if len(base) < DOUBLE_STEM_MIN_LENGTH:
        return False
    last = base[INDEX_LAST]
    prev = base[INDEX_PENULT]
    if last != prev:
        return False
    if last in VOWELS or last in DOUBLE_CONSONANT_EXCLUDED:
        return False
    return True


def is_simple_ed_form(word: str, words: set[str], evidence: set[str]) -> bool:
    if word.endswith(IED_SUFFIX):
        base = f"{word[: -len(IED_SUFFIX)]}{Y_SUFFIX}"
        return should_remove_with_base(base, words, evidence, has_verb_evidence_y_base)
    if word.endswith(CKED_SUFFIX):
        base = f"{word[: -len(CKED_SUFFIX)]}{C_SUFFIX}"
        return should_remove_with_base(base, words, evidence, has_verb_evidence_c_base)
    if word.endswith(ED_SUFFIX):
        stem = word[: -len(ED_SUFFIX)]
        if is_double_consonant(stem):
            base = stem[:INDEX_LAST]
            if should_remove_with_base(
                base,
                words,
                evidence,
                has_verb_evidence_doubled_base,
            ):
                return True
        base_with_e = f"{stem}{E_SUFFIX}"
        if base_with_e in words and has_verb_evidence_e_base(
            base_with_e,
            words,
            evidence,
        ):
            return True
        return should_remove_with_base(stem, words, evidence, has_verb_evidence)
    return False


def write_word_list(path: Path, words: list[str]) -> None:
    text = "\n".join(words) + "\n"
    path.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Filter words that look like regular past tense forms built as base + ed. "
            "If a base word is missing, still drop the -ed form when -ing or -s exists."
        )
    )
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--apply", action="store_true", help="Overwrite --input.")
    parser.add_argument("--removed-out", type=Path, default=None)
    parser.add_argument("--allow-list", type=Path, default=None)
    parser.add_argument(
        "--evidence",
        type=parse_evidence,
        default=DEFAULT_EVIDENCE,
        help="Comma list: ing,none. Default is ing.",
    )

    args = parser.parse_args()

    words = load_word_list(args.input)
    word_set = set(words)
    allow_set = load_allow_list(args.allow_list)

    kept: list[str] = []
    removed: list[str] = []

    for word in words:
        if word in allow_set:
            kept.append(word)
            continue
        if is_simple_ed_form(word, word_set, args.evidence):
            removed.append(word)
            continue
        kept.append(word)

    if args.apply:
        if args.output is not None:
            parser.error("--apply cannot be used with --output.")
        output_path = args.input
    else:
        output_path = args.output or DEFAULT_OUTPUT

    removed_path = args.removed_out or DEFAULT_REMOVED

    write_word_list(output_path, kept)
    write_word_list(removed_path, removed)

    print(
        f"Wrote {len(kept)} words to {output_path} "
        f"and {len(removed)} removed words to {removed_path}."
    )


if __name__ == "__main__":
    main()
