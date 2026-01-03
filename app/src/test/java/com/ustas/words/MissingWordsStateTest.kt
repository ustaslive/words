package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingWordsStateTest {
    @Test
    fun buildsMissingWordsStateFromMiniDictionary() {
        val baseWord = "STONE"
        val dictionary = listOf("STONE", "NOTE", "TONE", "ONES", "TON")
        val crosswordWords = mapOf(
            "STONE" to CrosswordWord("STONE", setOf(GridPosition(ORIGIN_INDEX, ORIGIN_INDEX))),
            "NOTE" to CrosswordWord("NOTE", setOf(GridPosition(ORIGIN_INDEX, ORIGIN_INDEX)))
        )

        val state = buildMissingWordsState(baseWord, dictionary, crosswordWords)

        val expectedMissing = setOf("TONE", "ONES")
        assertEquals(expectedMissing, state.entries.keys)
        assertEquals(expectedMissing.size, state.remainingCount)
        assertEquals(null, state.lastGuessedWord)
        assertTrue(state.entries.values.all { !it.isGuessed })
    }

    @Test
    fun marksMissingWordAsGuessed() {
        val baseWord = "STONE"
        val dictionary = listOf("STONE", "NOTE", "TONE", "ONES")
        val crosswordWords = mapOf(
            "STONE" to CrosswordWord("STONE", setOf(GridPosition(ORIGIN_INDEX, ORIGIN_INDEX)))
        )
        val initial = buildMissingWordsState(baseWord, dictionary, crosswordWords)

        val result = applyMissingWordGuess("tone", initial)

        assertEquals(MissingWordMatch.NewlyGuessed, result.match)
        assertEquals(initial.remainingCount - INDEX_STEP, result.state.remainingCount)
        assertEquals("TONE", result.state.lastGuessedWord)
        assertTrue(result.state.entries["TONE"]?.isGuessed == true)

        val repeat = applyMissingWordGuess("TONE", result.state)

        assertEquals(MissingWordMatch.AlreadyGuessed, repeat.match)
        assertEquals(result.state, repeat.state)
    }

    companion object {
        private const val ORIGIN_INDEX = 0
        private const val INDEX_STEP = 1
    }
}
