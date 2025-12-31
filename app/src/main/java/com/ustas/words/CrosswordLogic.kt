package com.ustas.words

import java.io.InputStream

private const val ALPHABET_SIZE = 26

internal data class CrosswordCell(
    val letter: Char?,
    val isActive: Boolean,
    val isRevealed: Boolean
)

internal data class GridPosition(
    val row: Int,
    val col: Int
)

internal data class CrosswordWord(
    val word: String,
    val positions: Set<GridPosition>
)

internal sealed interface WordResult {
    data object Success : WordResult
    data object AlreadySolved : WordResult
    data object NotFound : WordResult
}

internal fun loadWordList(openStream: () -> InputStream): List<String> {
    return openStream().bufferedReader()
        .useLines { lines -> loadWordListFromLines(lines) }
}

internal fun loadWordListFromLines(lines: Sequence<String>): List<String> {
    return lines.map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.uppercase() }
        .toList()
}

internal fun pickRandomBaseWord(words: List<String>): String {
    return if (words.isEmpty()) {
        "WORDS"
    } else {
        words.random()
    }
}

internal fun buildMiniDictionary(baseWord: String, dictionary: Iterable<String>): List<String> {
    val normalizedBase = baseWord.trim().uppercase()
    if (normalizedBase.isEmpty()) {
        return emptyList()
    }
    val baseCounts = countLetters(normalizedBase) ?: return emptyList()
    val baseLength = normalizedBase.length

    return dictionary.mapNotNull { rawWord ->
        val candidate = rawWord.trim().uppercase()
        if (candidate.isEmpty() || candidate.length > baseLength) {
            return@mapNotNull null
        }
        if (!canBuildWord(candidate, baseCounts)) {
            return@mapNotNull null
        }
        candidate
    }
}

internal fun generateLetterWheel(word: String): List<Char> {
    return word.uppercase().toList()
}

internal fun generateCrosswordGrid(word: String): List<List<CrosswordCell>> {
    val normalized = word.uppercase()
    val size = normalized.length
    val wordRow = size / 2

    return List(size) { rowIndex ->
        List(size) { colIndex ->
            if (rowIndex == wordRow) {
                CrosswordCell(
                    letter = normalized[colIndex],
                    isActive = true,
                    isRevealed = false
                )
            } else {
                CrosswordCell(letter = null, isActive = false, isRevealed = false)
            }
        }
    }
}

internal fun revealCells(
    grid: List<List<CrosswordCell>>,
    positions: Set<GridPosition>
): List<List<CrosswordCell>> {
    if (positions.isEmpty()) {
        return grid
    }
    return grid.mapIndexed { rowIndex, row ->
        row.mapIndexed { colIndex, cell ->
            if (positions.contains(GridPosition(rowIndex, colIndex))) {
                cell.copy(isRevealed = true)
            } else {
                cell
            }
        }
    }
}

internal fun generateCrosswordWords(word: String): List<CrosswordWord> {
    val normalized = word.uppercase()
    val row = normalized.length / 2
    return listOf(horizontalWord(normalized, row, startCol = 0))
}

internal fun applySelectedWord(
    selectedWord: String,
    crosswordWords: Map<String, CrosswordWord>,
    grid: List<List<CrosswordCell>>
): Pair<List<List<CrosswordCell>>, WordResult> {
    val match = crosswordWords[selectedWord.uppercase()]
    return if (match != null) {
        val alreadySolved = match.positions.all { pos -> grid[pos.row][pos.col].isRevealed }
        val updatedGrid = revealCells(grid, match.positions)
        updatedGrid to if (alreadySolved) WordResult.AlreadySolved else WordResult.Success
    } else {
        grid to WordResult.NotFound
    }
}

private fun horizontalWord(word: String, row: Int, startCol: Int): CrosswordWord {
    val positions = word.indices
        .map { colOffset -> GridPosition(row, startCol + colOffset) }
        .toSet()
    return CrosswordWord(word = word, positions = positions)
}

private fun countLetters(word: String): IntArray? {
    val counts = IntArray(ALPHABET_SIZE)
    for (char in word) {
        if (char !in 'A'..'Z') {
            return null
        }
        counts[char - 'A']++
    }
    return counts
}

private fun canBuildWord(word: String, baseCounts: IntArray): Boolean {
    val counts = IntArray(ALPHABET_SIZE)
    for (char in word) {
        if (char !in 'A'..'Z') {
            return false
        }
        val index = char - 'A'
        val next = counts[index] + 1
        if (next > baseCounts[index]) {
            return false
        }
        counts[index] = next
    }
    return true
}
