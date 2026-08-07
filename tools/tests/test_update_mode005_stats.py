from __future__ import annotations

import stat
import tempfile
import unittest
from pathlib import Path

from tools.update_mode005_stats import (
    LAB_DIR,
    copy_file_atomically,
    generated_path_from_line,
)


PROJECT_ROOT_PARENT_INDEX = 2
PROJECT_ROOT = Path(__file__).resolve().parents[PROJECT_ROOT_PARENT_INDEX]
TEST_FILE_MODE = 0o640


class UpdateMode005StatsTest(unittest.TestCase):
    def test_generated_path_from_line_accepts_timestamped_lab_file(self) -> None:
        expected = LAB_DIR / "005.20260725123456.txt"

        actual = generated_path_from_line(f"Saved: {expected}")

        self.assertEqual(expected, actual)

    def test_generated_path_from_line_ignores_other_output(self) -> None:
        self.assertIsNone(generated_path_from_line("Generating statistics"))

    def test_generated_path_from_line_rejects_unexpected_file(self) -> None:
        with self.assertRaises(ValueError):
            generated_path_from_line(f"Saved: {PROJECT_ROOT / 'unexpected.txt'}")

    def test_copy_file_atomically_replaces_destination(self) -> None:
        with tempfile.TemporaryDirectory(dir=PROJECT_ROOT) as directory:
            directory_path = Path(directory)
            source = directory_path / "source.txt"
            destination = directory_path / "destination.txt"
            source.write_text("new stats\n", encoding="utf-8")
            destination.write_text("old stats\n", encoding="utf-8")
            destination.chmod(TEST_FILE_MODE)

            copy_file_atomically(source, destination)

            self.assertEqual("new stats\n", destination.read_text(encoding="utf-8"))
            self.assertEqual(TEST_FILE_MODE, stat.S_IMODE(destination.stat().st_mode))


if __name__ == "__main__":
    unittest.main()
