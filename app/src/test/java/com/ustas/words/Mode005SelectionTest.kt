package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mode005SelectionTest {
    @Test
    fun parseMode005WordStatsReadsMetadataAndWords() {
        val lines = sequenceOf(
            "# format=v1",
            "# crosswords_generated=123",
            "near:7",
            "idea:5"
        )

        val result = parseMode005WordStats(lines)

        assertEquals(123, result.crosswordsGenerated)
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
        assertEquals(0, (result as CrosswordGenerationResult.Failure).attempts)
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
