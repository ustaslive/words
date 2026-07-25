# Dictionary update

Work in the `develop` branch. Review files are named
`YYYY-MM-DD_word_review.txt`.

```text
[dictionary.add]
quilt — note

[dictionary.remove]
letal — note

[forbidden.add]
fart — note

[forbidden.remove]
example — note
```

Only the first word is used. Lines whose first non-space character is `#` are
comments. Invalid lines stop the script.

From `data/dictionary_updates`:

```bash
../../tools/apply_dictionary_review.py
../../tools/apply_dictionary_review.py --apply

cd /words
python3 tools/update_mode005_stats.py 10000 --verbose

git branch --show-current
git status --short
git add data/dictionary_updates
git add app/src/main/assets/words.txt app/src/main/assets/forbidden_words.txt
git add lab/crossword_repeatability/005.stat.txt app/src/main/assets/005.stat.txt
git commit -m "Update dictionaries YYYY-MM-DD"
git push origin develop
```

Wait about 10 minutes, then select **Update dictionary** in the app.
