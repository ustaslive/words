package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniDictionaryTest {
    @Test
    fun buildsMiniDictionaryFromAccurate() {
        val dictionary = listOf(
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
            "CURT",
            "ACCAA",
            "CREATE",
            "ACCURATES",
            "CURATOR",
            "TART",
            "BEE"
        )

        val expected = listOf(
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

        val result = buildMiniDictionary("ACCURATE", dictionary)

        println("Dictionary: ${dictionary.joinToString()}")
        println("Base word: ACCURATE")
        println("Mini dictionary: ${result.joinToString()}")

        assertEquals(expected, result)
    }
}
