package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val TEST_SEED_LENGTH = 6
private const val TEST_GENERATION_ATTEMPTS = 1
private const val TEST_MIN_HIDDEN_WORD_COUNT = 1
private const val TEST_MIN_CROSSWORD_WORD_COUNT = 2
private const val TEST_RANDOM_SEED = 1234
private const val TEST_WORD = "ABAB"
private const val TEST_SECOND_WORD = "BABA"

class Mode005SelectionTest {
    @Test
    fun parseMode005WordStatsReadsWordsAndIgnoresMetadata() {
        val lines = sequenceOf(
            "# format=v1",
            "# crosswords_generated=123",
            "near:7",
            "idea:5"
        )

        val result = parseMode005WordStats(lines)

        assertEquals(7, result.frequencies["NEAR"])
        assertEquals(5, result.frequencies["IDEA"])
    }

    @Test
    fun buildMode005TopFrequentWordSetUsesFrequencyAndAlphabeticOrder() {
        val dictionary = listOf("beta", "alpha", "gamma", "alpha")
        val wordStats = mapOf(
            "ALPHA" to 10,
            "BETA" to 10,
            "GAMMA" to 3
        )

        val result = buildMode005TopFrequentWordSet(
            dictionary = dictionary,
            wordStats = wordStats,
            topFrequentWordShare = 0.34
        )

        assertEquals(2, result.size)
        assertTrue(result.contains("ALPHA"))
        assertTrue(result.contains("BETA"))
    }

    @Test
    fun generateCrosswordWithMode005FailsWithEmptyDictionary() {
        val result = generateCrosswordWithMode005(
            dictionary = emptyList(),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = 6..6
        )

        assertTrue(result is CrosswordGenerationResult.Failure)
    }

    @Test
    fun generateCrosswordWithMode005UsesOnlyAllowedLetters() {
        val allowedLetters = setOf('A', 'B')
        val result = generateCrosswordWithMode005(
            dictionary = listOf(TEST_WORD),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = TEST_SEED_LENGTH..TEST_SEED_LENGTH,
            config = CrosswordGeneratorConfig(
                minCrosswordWordCount = MIN_CONFIGURED_CROSSWORD_WORD_COUNT,
                excludedLetters = CROSSWORD_GENERATOR_ALPHABET
                    .filterNot { letter -> letter in allowedLetters }
                    .toSet(),
                maxGenerationAttempts = TEST_GENERATION_ATTEMPTS
            ),
            random = Random(TEST_RANDOM_SEED)
        )

        assertTrue(result is CrosswordGenerationResult.Success)
        val success = result as CrosswordGenerationResult.Success
        assertTrue(success.seedLetters.all { letter -> letter in allowedLetters })
    }

    @Test
    fun generateCrosswordWithMode005RejectsPoolSmallerThanCombinedMinimums() {
        val result = generateCrosswordWithMode005(
            dictionary = listOf(TEST_WORD),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = TEST_SEED_LENGTH..TEST_SEED_LENGTH,
            config = CrosswordGeneratorConfig(
                minCrosswordWordCount = MIN_CONFIGURED_CROSSWORD_WORD_COUNT,
                minHiddenWordCount = TEST_MIN_HIDDEN_WORD_COUNT,
                excludedLetters = CROSSWORD_GENERATOR_ALPHABET
                    .filterNot { letter -> letter == 'A' || letter == 'B' }
                    .toSet(),
                maxGenerationAttempts = TEST_GENERATION_ATTEMPTS
            ),
            random = Random(TEST_RANDOM_SEED)
        )

        assertTrue(result is CrosswordGenerationResult.Failure)
    }

    @Test
    fun generateCrosswordWithMode005ChecksHiddenMinimumAfterLayout() {
        val result = generateCrosswordWithMode005(
            dictionary = listOf(TEST_WORD, TEST_SECOND_WORD),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = TEST_SEED_LENGTH..TEST_SEED_LENGTH,
            config = CrosswordGeneratorConfig(
                minCrosswordWordCount = MIN_CONFIGURED_CROSSWORD_WORD_COUNT,
                minHiddenWordCount = TEST_MIN_HIDDEN_WORD_COUNT,
                excludedLetters = CROSSWORD_GENERATOR_ALPHABET
                    .filterNot { letter -> letter == 'A' || letter == 'B' }
                    .toSet(),
                maxGenerationAttempts = TEST_GENERATION_ATTEMPTS
            ),
            random = Random(TEST_RANDOM_SEED)
        )

        assertTrue(result is CrosswordGenerationResult.Failure)
    }

    @Test
    fun generateCrosswordWithMode005RequiresMinimumCrosswordWordCount() {
        val result = generateCrosswordWithMode005(
            dictionary = listOf(TEST_WORD),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = TEST_SEED_LENGTH..TEST_SEED_LENGTH,
            config = CrosswordGeneratorConfig(
                minCrosswordWordCount = TEST_MIN_CROSSWORD_WORD_COUNT,
                excludedLetters = CROSSWORD_GENERATOR_ALPHABET
                    .filterNot { letter -> letter == 'A' || letter == 'B' }
                    .toSet(),
                maxGenerationAttempts = TEST_GENERATION_ATTEMPTS
            ),
            random = Random(TEST_RANDOM_SEED)
        )

        assertTrue(result is CrosswordGenerationResult.Failure)
    }

    @Test
    fun generateCrosswordWithMode005FailsWhenNoLettersAreAvailable() {
        val result = generateCrosswordWithMode005(
            dictionary = listOf(TEST_WORD),
            previousRoundWordSet = emptySet(),
            topFrequentWordSet = emptySet(),
            seedLengthRange = TEST_SEED_LENGTH..TEST_SEED_LENGTH,
            config = CrosswordGeneratorConfig(
                minCrosswordWordCount = MIN_CONFIGURED_CROSSWORD_WORD_COUNT,
                excludedLetters = CROSSWORD_GENERATOR_ALPHABET.toSet(),
                maxGenerationAttempts = TEST_GENERATION_ATTEMPTS
            ),
            random = Random(TEST_RANDOM_SEED)
        )

        assertTrue(result is CrosswordGenerationResult.Failure)
    }

    @Test
    fun trimMode005SeedLettersToUsedLettersDropsUnusedLetters() {
        val result = trimMode005SeedLettersToUsedLetters(
            seedLetters = "ABCDE",
            allWordSet = setOf("ABBA", "DEED")
        )

        assertEquals("ABDE", result)
    }

    @Test
    fun trimMode005SeedLettersToUsedLettersKeepsDuplicatesForUsedLetters() {
        val result = trimMode005SeedLettersToUsedLetters(
            seedLetters = "AABCF",
            allWordSet = setOf("CAB")
        )

        assertEquals("AABC", result)
        assertFalse(result.contains('F'))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseMode005WordStatsRejectsInvalidLines() {
        parseMode005WordStats(sequenceOf("invalid_line"))
    }
}
