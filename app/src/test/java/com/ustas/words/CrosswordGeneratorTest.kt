package com.ustas.words

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CrosswordGeneratorTest {
    @Test
    fun generatesCrosswordContainingLongestWord() {
        val words = buildMiniDictionary(BASE_WORD, SAMPLE_DICTIONARY)
        val grid = generateRandomCrossword(words, Random(RANDOM_SEED))

        assertTrue(grid.isNotEmpty())

        val longestWords = longestWords(words)
        assertTrue(longestWords.any { word -> gridContainsWord(grid, word.lowercase()) })
        assertTrue(grid.all { row -> row.all { it == CROSSWORD_EMPTY_CELL || it.isLowerCase() } })
    }

    @Test
    fun printsRandomCrossword() {
        val words = buildMiniDictionary(BASE_WORD, SAMPLE_DICTIONARY)
        val grid = generateRandomCrossword(words, Random.Default)

        println("Random crossword:")
        grid.forEach { println(it) }

        assertTrue(grid.isNotEmpty())
    }

    @Test
    fun avoidsAdjacentInvalidWords() {
        val words = buildMiniDictionary(BASE_WORD, SAMPLE_DICTIONARY)
        val grid = generateRandomCrossword(words, Random(RANDOM_SEED))
        val wordSet = words.map { it.lowercase() }.toSet()

        val sequences = collectSequences(grid)
        assertTrue(sequences.isNotEmpty())
        assertTrue(
            sequences.filter { it.length >= MIN_SEQUENCE_LENGTH }
                .all { candidate -> wordSet.contains(candidate) }
        )
    }

    private fun longestWords(words: List<String>): List<String> {
        val maxLength = words.maxOf { it.length }
        return words.filter { it.length == maxLength }
    }

    private fun gridContainsWord(grid: List<String>, word: String): Boolean {
        if (grid.isEmpty() || word.isEmpty()) {
            return false
        }
        if (grid.any { row -> row.contains(word) }) {
            return true
        }
        val columnCount = grid.first().length
        val columnBuilder = StringBuilder()
        for (column in ORIGIN_INDEX until columnCount) {
            columnBuilder.setLength(ORIGIN_INDEX)
            for (row in grid) {
                columnBuilder.append(row[column])
            }
            if (columnBuilder.toString().contains(word)) {
                return true
            }
        }
        return false
    }

    private fun collectSequences(grid: List<String>): List<String> {
        if (grid.isEmpty()) {
            return emptyList()
        }
        val sequences = mutableListOf<String>()
        for (row in grid) {
            sequences.addAll(extractSequencesFromLine(row))
        }
        val columnCount = grid.first().length
        val columnBuilder = StringBuilder()
        for (column in ORIGIN_INDEX until columnCount) {
            columnBuilder.setLength(ORIGIN_INDEX)
            for (row in grid) {
                columnBuilder.append(row[column])
            }
            sequences.addAll(extractSequencesFromLine(columnBuilder.toString()))
        }
        return sequences
    }

    private fun extractSequencesFromLine(line: String): List<String> {
        val sequences = mutableListOf<String>()
        val builder = StringBuilder()
        for (char in line) {
            if (char == CROSSWORD_EMPTY_CELL) {
                if (builder.isNotEmpty()) {
                    sequences.add(builder.toString())
                    builder.setLength(ORIGIN_INDEX)
                }
            } else {
                builder.append(char)
            }
        }
        if (builder.isNotEmpty()) {
            sequences.add(builder.toString())
        }
        return sequences
    }

    companion object {
        private const val BASE_WORD = "ACCURATE"
        private const val RANDOM_SEED = 42L
        private const val ORIGIN_INDEX = 0
        private const val MIN_SEQUENCE_LENGTH = 2

        private val SAMPLE_DICTIONARY = listOf(
            "ACCURATE",
            "ACUTE",
            "CURATE",
            "REACT",
            "TRACE",
            "CATER",
            "CARE",
            "RACE",
            "AURA",
            "CUT",
            "CUTE",
            "TAR",
            "RATE",
            "RAT",
            "TEA",
            "EAT",
            "CURT"
        )
    }
}
