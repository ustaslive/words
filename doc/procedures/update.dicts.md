# Update dictionaries

1. Collect words manually or from the app with **Send words to review**.
2. Analyze the collected words with the Gemini Gem `words`:
   <https://gemini.google.com/gem/e85ab88ab09a>
3. Copy the reviewed decisions into a dated file such as
   `data/dictionary_updates/YYYY-MM-DD_word_review.txt`. See
   `data/dictionary_updates/README.md` for the format.
4. Preview the exact dictionary changes from the repository root:

   ```bash
   python3 tools/apply_dictionary_review.py
   ```

   With no file argument, the newest `*_word_review.txt` file is selected and
   printed. Pass an explicit path when updating an older batch.

5. Check the preview, then apply it:

   ```bash
   python3 tools/apply_dictionary_review.py --apply
   ```

6. Regenerate and synchronize mode 005 statistics:

   ```bash
   python3 tools/update_mode005_stats.py 10000 --verbose
   ```

7. Review the Git diff and run the relevant tests.
8. Commit and push the changes to the `develop` branch.
9. Wait for the remote update, then select **Update dictionary** in the app.
