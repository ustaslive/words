package com.ustas.words

internal const val CROSSWORD_GENERATOR_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
internal const val MIN_CONFIGURED_CROSSWORD_WORD_COUNT = 1
internal const val MAX_CONFIGURED_CROSSWORD_WORD_COUNT = 50
internal const val MIN_CONFIGURED_HIDDEN_WORD_COUNT = 0
internal const val MAX_CONFIGURED_HIDDEN_WORD_COUNT = 200
internal const val DEFAULT_MIN_HIDDEN_WORD_COUNT = MIN_CONFIGURED_HIDDEN_WORD_COUNT
private const val DEFAULT_MAX_LETTER_SWAP_CYCLES = 5
private const val DEFAULT_MAX_LAYOUT_ATTEMPTS = 5
private const val DEFAULT_MAX_REPEAT_SHARE = 0.40

internal data class CrosswordGeneratorConfig(
    val minWordLength: Int = MIN_CROSSWORD_WORD_LENGTH,
    val minCrosswordWordCount: Int = MIN_CROSSWORD_WORD_COUNT,
    val minHiddenWordCount: Int = DEFAULT_MIN_HIDDEN_WORD_COUNT,
    val excludedLetters: Set<Char> = emptySet(),
    val maxGenerationAttempts: Int = MAX_CROSSWORD_GENERATION_ATTEMPTS,
    val maxLetterSwapCycles: Int = DEFAULT_MAX_LETTER_SWAP_CYCLES,
    val maxLayoutAttempts: Int = DEFAULT_MAX_LAYOUT_ATTEMPTS,
    val maxRepeatShareWithControlSet: Double = DEFAULT_MAX_REPEAT_SHARE
)

internal fun CrosswordGeneratorConfig.normalizedUserSettings(): CrosswordGeneratorConfig {
    return copy(
        minCrosswordWordCount = minCrosswordWordCount.coerceIn(
            MIN_CONFIGURED_CROSSWORD_WORD_COUNT,
            MAX_CONFIGURED_CROSSWORD_WORD_COUNT
        ),
        minHiddenWordCount = minHiddenWordCount.coerceIn(
            MIN_CONFIGURED_HIDDEN_WORD_COUNT,
            MAX_CONFIGURED_HIDDEN_WORD_COUNT
        ),
        excludedLetters = excludedLetters
            .map { letter -> letter.uppercaseChar() }
            .filter { letter -> letter in CROSSWORD_GENERATOR_ALPHABET }
            .toSet()
    )
}
