package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Test

class NetScoringTest {
    @Test
    fun laterWordsAwardMorePointsAcrossPlayers() {
        val solvedBy = linkedMapOf(
            "FIRST" to "player-one",
            "SECOND" to "player-two",
            "THIRD" to "player-two"
        )

        val scores = calculateNetPlayerScores(
            solvedWordOrder = listOf("FIRST", "SECOND", "THIRD"),
            solvedBy = solvedBy
        )

        assertEquals(NetPlayerScore(wordCount = 1, points = 1), scores["player-one"])
        assertEquals(NetPlayerScore(wordCount = 2, points = 5), scores["player-two"])
    }

    @Test
    fun reconciliationRemovesDuplicatesAndAppendsLegacyEntries() {
        val solvedBy = linkedMapOf(
            "FIRST" to "player-one",
            "SECOND" to "player-two",
            "THIRD" to "player-two"
        )

        val order = reconcileSolvedWordOrder(
            solvedWordOrder = listOf("SECOND", "UNKNOWN", "SECOND", "FIRST"),
            solvedBy = solvedBy
        )

        assertEquals(listOf("SECOND", "FIRST", "THIRD"), order)
    }

    @Test
    fun appendSolvedWordAddsOnlyNewNonBlankWords() {
        val original = listOf("FIRST")

        assertEquals(listOf("FIRST", "SECOND"), appendSolvedWord(original, "SECOND"))
        assertEquals(original, appendSolvedWord(original, "FIRST"))
        assertEquals(original, appendSolvedWord(original, ""))
    }
}
