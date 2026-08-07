package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Test

class CrosswordGeneratorConfigTest {
    @Test
    fun normalizesUserControlledValues() {
        val config = CrosswordGeneratorConfig(
            minCrosswordWordCount = MIN_CONFIGURED_CROSSWORD_WORD_COUNT - 1,
            minHiddenWordCount = MAX_CONFIGURED_HIDDEN_WORD_COUNT + 1,
            excludedLetters = setOf('a', 'Z', '1')
        )

        val normalized = config.normalizedUserSettings()

        assertEquals(MIN_CONFIGURED_CROSSWORD_WORD_COUNT, normalized.minCrosswordWordCount)
        assertEquals(MAX_CONFIGURED_HIDDEN_WORD_COUNT, normalized.minHiddenWordCount)
        assertEquals(setOf('A', 'Z'), normalized.excludedLetters)
    }
}
