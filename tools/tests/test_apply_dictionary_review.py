from __future__ import annotations

import stat
import tempfile
import unittest
from pathlib import Path

from tools.apply_dictionary_review import (
    ReviewDecisions,
    ReviewFormatError,
    build_update_plan,
    find_latest_review_file,
    parse_review,
    resolve_review_file,
    write_word_list_atomically,
)


PROJECT_ROOT_PARENT_INDEX = 2
PROJECT_ROOT = Path(__file__).resolve().parents[PROJECT_ROOT_PARENT_INDEX]
TEST_FILE_MODE = 0o640


class ApplyDictionaryReviewTest(unittest.TestCase):
    def test_find_latest_review_file_uses_date_in_file_name(self) -> None:
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            directory_path = Path(directory)
            older_path = directory_path / "2026-07-24_word_review.txt"
            latest_path = directory_path / "2026-07-25_word_review.txt"
            ignored_path = directory_path / "notes.txt"
            older_path.write_text("older\n", encoding="utf-8")
            latest_path.write_text("latest\n", encoding="utf-8")
            ignored_path.write_text("ignored\n", encoding="utf-8")

            actual = find_latest_review_file(directory_path)

            self.assertEqual(latest_path.resolve(), actual)

    def test_resolve_review_file_accepts_explicit_path(self) -> None:
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            directory_path = Path(directory)
            review_path = directory_path / "custom.txt"
            review_path.write_text("review\n", encoding="utf-8")

            actual = resolve_review_file(review_path, directory_path)

            self.assertEqual(review_path.resolve(), actual)

    def test_resolve_review_file_uses_current_directory_without_argument(self) -> None:
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            directory_path = Path(directory)
            review_path = directory_path / "2026-07-25_word_review.txt"
            review_path.write_text("review\n", encoding="utf-8")

            actual = resolve_review_file(None, directory_path)

            self.assertEqual(review_path.resolve(), actual)

    def test_parse_review_uses_first_value_and_all_sections(self) -> None:
        review_text = """\
[dictionary.add]
Quilt — blanket

[dictionary.remove]
Letal typo; use lethal

[forbidden.add]
Fart — unsuitable

[forbidden.remove]
Example — allow again
"""
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            review_path = Path(directory) / "review.txt"
            review_path.write_text(review_text, encoding="utf-8")

            decisions = parse_review(review_path)

        self.assertEqual(frozenset({"quilt"}), decisions.dictionary_add)
        self.assertEqual(frozenset({"letal"}), decisions.dictionary_remove)
        self.assertEqual(frozenset({"fart"}), decisions.forbidden_add)
        self.assertEqual(frozenset({"example"}), decisions.forbidden_remove)

    def test_parse_review_rejects_conflicting_forbidden_actions(self) -> None:
        review_text = """\
[forbidden.add]
word
[forbidden.remove]
word
"""
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            review_path = Path(directory) / "review.txt"
            review_path.write_text(review_text, encoding="utf-8")

            with self.assertRaises(ReviewFormatError):
                parse_review(review_path)

    def test_parse_review_allows_indented_comment_with_non_ascii_text(self) -> None:
        review_text = """\
[dictionary.add]
    # \u041b\u044e\u0431\u043e\u0439 \u0442\u0435\u043a\u0441\u0442 \u0432 \u043a\u043e\u043c\u043c\u0435\u043d\u0442\u0430\u0440\u0438\u0438
quilt note
"""
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            review_path = Path(directory) / "review.txt"
            review_path.write_text(review_text, encoding="utf-8")

            decisions = parse_review(review_path)

        self.assertEqual(frozenset({"quilt"}), decisions.dictionary_add)

    def test_parse_review_rejects_non_ascii_text_at_start_of_data_line(self) -> None:
        review_text = """\
[dictionary.add]
\u041d\u0435\u0430\u043d\u0433\u043b\u0438\u0439\u0441\u043a\u043e\u0435:\n
"""
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            review_path = Path(directory) / "review.txt"
            review_path.write_text(review_text, encoding="utf-8")

            with self.assertRaises(ReviewFormatError):
                parse_review(review_path)

    def test_build_update_plan_handles_all_actions_and_existing_state(self) -> None:
        decisions = ReviewDecisions(
            dictionary_add=frozenset({"alpha", "bravo"}),
            dictionary_remove=frozenset({"delta", "missing"}),
            forbidden_add=frozenset({"bad", "nasty"}),
            forbidden_remove=frozenset({"old", "clean"}),
        )

        plan = build_update_plan(
            decisions=decisions,
            dictionary_words=["alpha", "delta"],
            forbidden_words=["bad", "old"],
        )

        self.assertEqual(("alpha", "bravo"), plan.dictionary_words)
        self.assertEqual(("bad", "nasty"), plan.forbidden_words)
        self.assertEqual(("bravo",), plan.dictionary_added)
        self.assertEqual(("delta",), plan.dictionary_removed)
        self.assertEqual(("alpha",), plan.dictionary_already_present)
        self.assertEqual(("missing",), plan.dictionary_already_absent)
        self.assertEqual(("nasty",), plan.forbidden_added)
        self.assertEqual(("old",), plan.forbidden_removed)
        self.assertEqual(("bad",), plan.forbidden_already_present)
        self.assertEqual(("clean",), plan.forbidden_already_absent)

        repeated_plan = build_update_plan(
            decisions=decisions,
            dictionary_words=list(plan.dictionary_words),
            forbidden_words=list(plan.forbidden_words),
        )

        self.assertFalse(repeated_plan.dictionary_changed)
        self.assertFalse(repeated_plan.forbidden_changed)

    def test_write_word_list_atomically_writes_one_word_per_line(self) -> None:
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            output_path = Path(directory) / "words.txt"
            output_path.write_text("old\n", encoding="utf-8")
            output_path.chmod(TEST_FILE_MODE)

            write_word_list_atomically(output_path, ("alpha", "bravo"))

            self.assertEqual("alpha\nbravo\n", output_path.read_text(encoding="utf-8"))
            self.assertEqual(TEST_FILE_MODE, stat.S_IMODE(output_path.stat().st_mode))


if __name__ == "__main__":
    unittest.main()
