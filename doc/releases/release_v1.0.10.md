In this release v1.0.10:

1. Improved crossword generation:
   - Added settings for the minimum number of crossword and hidden words.
   - Added the ability to exclude selected letters from generated crosswords.
   - Generation continues until a suitable crossword is found, stopped by the player, or reaches the ten-minute limit.
   - The generator retries crossword layouts from a promising word set before selecting new letters.
2. Improved the hidden-words interface:
   - Found hidden words can be viewed while the crossword is still in progress.
   - After completion, found and not-found hidden words are shown in separate scrollable lists.
3. Added clearer animated feedback while a new crossword is being generated.
4. Updated and cleaned up the word dictionary.
5. Removed obsolete generation code and improved application stability.
