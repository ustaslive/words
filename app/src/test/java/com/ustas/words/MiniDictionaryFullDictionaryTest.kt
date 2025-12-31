package com.ustas.words

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class MiniDictionaryFullDictionaryTest {
    @Test
    fun buildsMiniDictionaryFromFullDictionary() {
        val startNanos = System.nanoTime()
        val selectedWords = buildMiniDictionary(BASE_WORD, dictionary)
        val elapsedNanos = System.nanoTime() - startNanos

        val elapsedMillis = elapsedNanos / NANOS_PER_MILLI
        val minutes = elapsedMillis / MILLIS_PER_MINUTE
        val seconds = (elapsedMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
        val millis = elapsedMillis % MILLIS_PER_SECOND

        println("Base word: $BASE_WORD")
        println("Selected words count: ${selectedWords.size}")
        println("Selected words: ${selectedWords.joinToString()}")
        println("Full dictionary scan took ${minutes}m ${seconds}s ${millis}ms.")

        assertTrue(selectedWords.containsAll(expectedSubset))
        assertFalse(selectedWords.contains(NOT_BUILDABLE_WORD))
    }

    companion object {
        private const val BASE_WORD = "ACCURATE"
        private const val NOT_BUILDABLE_WORD = "CREATE"
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE

        private lateinit var dictionary: List<String>

        private val expectedSubset = listOf(
            "ACCURATE",
            "ACUTE",
            "AURA",
            "CARE",
            "CATER",
            "CUTE",
            "RACE",
            "RATE",
            "REACT",
            "TRACE"
        )

        @BeforeClass
        @JvmStatic
        fun loadDictionary() {
            val dictionaryFile = findDictionaryFile()
            dictionary = loadWordList { dictionaryFile.inputStream() }
        }

        private fun findDictionaryFile(): File {
            val userDir = File(System.getProperty("user.dir") ?: ".")
            val candidates = listOf(
                File(userDir, "app/src/main/assets/words.txt"),
                File(userDir, "src/main/assets/words.txt")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw AssertionError(
                    "Dictionary file not found. Checked: ${candidates.joinToString { it.path }}"
                )
        }
    }
}
