- collect words, manually, or sent from the app via (...) -> Send words to review -> ustas@live.com

- use Gemini Gem `words`, https://gemini.google.com/gem/e85ab88ab09a
    - copy all collected words there
    - get 2 lists: valid words and not valid
    - check/add/remove the words in the dictionaries files
        - app/src/main/assets/words.txt
        - app/src/main/assets/forbidden_words.txt

- run script to reevaluate words frequences in dictionaries
```
cd /words/lab/crossword_repeatability
python3 generate_005_word_stats.py 10000 -v
latest_stats="$(ls -t 005.20*.txt | head -n 1)"
cp "$latest_stats" 005.stat.txt
cp 005.stat.txt /words/app/src/main/assets/005.stat.txt
```


- commit to `develop` branch
- push `develop` branch to github

- wait for 10 minutes, and update phone app with
    (...) -> Update dictionary
