package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DictionaryLoaderTest {
    @Test
    fun loadsAllLinesFromDictionaryFile() {
        val dictionaryFile = findDictionaryFile()

        val rawLines = dictionaryFile.readLines(Charsets.UTF_8)
        val loadedWords = dictionaryFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
            loadWordListFromLines(lines)
        }

        assertEquals(rawLines.size, loadedWords.size)
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
