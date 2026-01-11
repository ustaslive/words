package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Test

class ForbiddenWordsFilterTest {
    @Test
    fun removesForbiddenWords() {
        val words = listOf("SAFE", "BAD", "UGLY")
        val forbiddenWords = setOf("BAD", "UGLY")

        val filtered = filterForbiddenWords(words, forbiddenWords)

        assertEquals(listOf("SAFE"), filtered)
    }

    @Test
    fun keepsWordsWhenForbiddenListEmpty() {
        val words = listOf("SAFE", "BAD")

        val filtered = filterForbiddenWords(words, emptySet())

        assertEquals(words, filtered)
    }
}
