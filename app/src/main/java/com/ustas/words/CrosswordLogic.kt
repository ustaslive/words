package com.ustas.words

import java.io.InputStream
import kotlin.random.Random

private const val ALPHABET_SIZE = 26
internal const val CROSSWORD_EMPTY_CELL = '.'
internal const val MIN_CROSSWORD_WORD_LENGTH = 4
internal const val MIN_CROSSWORD_WORD_COUNT = 9
internal const val MAX_CROSSWORD_ROWS = 14
internal const val MAX_CROSSWORD_COLUMNS = 14
internal const val MAX_CROSSWORD_GENERATION_ATTEMPTS = 100
private const val ORIGIN_INDEX = 0
private const val INDEX_STEP = 1
private const val LAST_INDEX_OFFSET = 1
private const val PRIORITY_LONGEST_WORDS_COUNT = 3

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

internal data class CrosswordLayout(
    val grid: List<List<CrosswordCell>>,
    val words: Map<String, CrosswordWord>
)

internal sealed interface CrosswordGenerationResult {
    data class Success(
        val seedLetters: String,
        val layout: CrosswordLayout,
        val rejectedSeedLetters: List<String>,
        val attempts: Int
    ) : CrosswordGenerationResult

    data class Failure(
        val rejectedSeedLetters: List<String>,
        val attempts: Int
    ) : CrosswordGenerationResult
}

private enum class WordOrientation {
    HORIZONTAL,
    VERTICAL
}

private data class WordPlacement(
    val word: String,
    val start: GridPosition,
    val orientation: WordOrientation
)

private data class CellState(
    val letter: Char,
    var hasHorizontal: Boolean,
    var hasVertical: Boolean
)

private data class CrosswordBounds(
    var minRow: Int,
    var maxRow: Int,
    var minCol: Int,
    var maxCol: Int
) {
    fun update(position: GridPosition) {
        minRow = minOf(minRow, position.row)
        maxRow = maxOf(maxRow, position.row)
        minCol = minOf(minCol, position.col)
        maxCol = maxOf(maxCol, position.col)
    }
}

internal sealed interface WordResult {
    data object Success : WordResult
    data object AlreadySolved : WordResult
    data object MissingWordFound : WordResult
    data object MissingWordAlreadyFound : WordResult
    data object NotFound : WordResult
}

internal data class MissingWordEntry(
    val word: String,
    val isGuessed: Boolean
)

internal data class MissingWordsState(
    val entries: Map<String, MissingWordEntry>,
    val remainingCount: Int,
    val lastGuessedWord: String?
)

internal enum class MissingWordMatch {
    None,
    AlreadyGuessed,
    NewlyGuessed
}

internal data class MissingWordGuessResult(
    val state: MissingWordsState,
    val match: MissingWordMatch
)

internal fun emptyMissingWordsState(): MissingWordsState {
    return MissingWordsState(emptyMap(), ORIGIN_INDEX, null)
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

internal fun buildMiniDictionary(seedLetters: String, dictionary: Iterable<String>): List<String> {
    val normalizedSeed = seedLetters.trim().uppercase()
    if (normalizedSeed.isEmpty()) {
        return emptyList()
    }
    val seedCounts = countLetters(normalizedSeed) ?: return emptyList()
    val seedLength = normalizedSeed.length

    return dictionary.mapNotNull { rawWord ->
        val candidate = rawWord.trim().uppercase()
        if (candidate.isEmpty() || candidate.length > seedLength) {
            return@mapNotNull null
        }
        if (!canBuildWord(candidate, seedCounts)) {
            return@mapNotNull null
        }
        candidate
    }
}

internal fun generateLetterWheel(seedLetters: String): List<Char> {
    return seedLetters.uppercase().toList()
}

internal fun generateCrosswordGrid(seedLetters: String): List<List<CrosswordCell>> {
    val normalized = seedLetters.uppercase()
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

internal fun generateCrosswordWords(seedLetters: String): List<CrosswordWord> {
    val normalized = seedLetters.uppercase()
    val row = normalized.length / 2
    return listOf(horizontalWord(normalized, row, startCol = 0))
}

internal fun buildCrosswordLayout(
    seedLetters: String,
    dictionary: List<String>,
    random: Random = Random.Default
): CrosswordLayout {
    val miniDictionary = buildMiniDictionary(seedLetters, dictionary)
        .filter { it.length >= MIN_CROSSWORD_WORD_LENGTH }
    val rows = generateRandomCrossword(miniDictionary, random)
    val grid = buildCrosswordGridFromRows(rows)
    val words = extractCrosswordWords(rows).associateBy { it.word }
    return CrosswordLayout(grid = grid, words = words)
}

internal fun generateCrosswordWithQuality(
    seedLetterCandidates: List<String>,
    dictionary: List<String>,
    minWordCount: Int = MIN_CROSSWORD_WORD_COUNT,
    maxAttempts: Int = MAX_CROSSWORD_GENERATION_ATTEMPTS,
    random: Random = Random.Default,
    isValidLayout: (String, CrosswordLayout) -> Boolean = { _, _ -> true }
): CrosswordGenerationResult {
    if (seedLetterCandidates.isEmpty()) {
        return CrosswordGenerationResult.Failure(emptyList(), ORIGIN_INDEX)
    }
    val remainingSeedLetters = seedLetterCandidates.toMutableList()
    val rejectedSeedLetters = mutableListOf<String>()
    var attempts = ORIGIN_INDEX
    while (attempts < maxAttempts && remainingSeedLetters.isNotEmpty()) {
        val seedIndex = random.nextInt(remainingSeedLetters.size)
        val seedLetters = remainingSeedLetters.removeAt(seedIndex)
        attempts += INDEX_STEP
        val layout = buildCrosswordLayout(seedLetters, dictionary, random)
        val wordCount = layout.words.size
        if (wordCount >= minWordCount && isValidLayout(seedLetters, layout)) {
            return CrosswordGenerationResult.Success(
                seedLetters = seedLetters,
                layout = layout,
                rejectedSeedLetters = rejectedSeedLetters,
                attempts = attempts
            )
        }
        rejectedSeedLetters.add(seedLetters)
    }
    return CrosswordGenerationResult.Failure(rejectedSeedLetters, attempts)
}

internal fun areAllSeedLettersUsed(seedLetters: String, layout: CrosswordLayout): Boolean {
    val usedLetters = BooleanArray(ALPHABET_SIZE)
    for (word in layout.words.keys) {
        for (char in word) {
            if (char !in 'A'..'Z') {
                continue
            }
            usedLetters[char - 'A'] = true
        }
    }
    for (char in seedLetters.uppercase()) {
        if (char !in 'A'..'Z') {
            return false
        }
        if (!usedLetters[char - 'A']) {
            return false
        }
    }
    return true
}

internal fun generateRandomCrossword(
    words: List<String>,
    random: Random = Random.Default
): List<String> {
    val normalizedWords = normalizeCrosswordWords(words)
    if (normalizedWords.isEmpty()) {
        return emptyList()
    }

    val longestWords = pickLongestWords(normalizedWords)
    val baseWord = longestWords[random.nextInt(longestWords.size)]
    val priorityWords = pickPriorityWords(normalizedWords, baseWord)
    val priorityWordSet = priorityWords.toSet()
    val remainingWords = normalizedWords
        .filterNot { priorityWordSet.contains(it) }
        .shuffled(random)

    val baseFitsHorizontal = baseWord.length <= MAX_CROSSWORD_COLUMNS
    val baseFitsVertical = baseWord.length <= MAX_CROSSWORD_ROWS
    if (!baseFitsHorizontal && !baseFitsVertical) {
        return emptyList()
    }

    val grid = mutableMapOf<GridPosition, CellState>()
    val letterIndex = mutableMapOf<Char, MutableList<GridPosition>>()
    val bounds = CrosswordBounds(
        minRow = ORIGIN_INDEX,
        maxRow = ORIGIN_INDEX,
        minCol = ORIGIN_INDEX,
        maxCol = ORIGIN_INDEX
    )

    val baseOrientation = when {
        baseFitsHorizontal && baseFitsVertical -> {
            if (random.nextBoolean()) WordOrientation.HORIZONTAL else WordOrientation.VERTICAL
        }
        baseFitsHorizontal -> WordOrientation.HORIZONTAL
        else -> WordOrientation.VERTICAL
    }
    placeWord(
        placement = WordPlacement(
            word = baseWord,
            start = GridPosition(ORIGIN_INDEX, ORIGIN_INDEX),
            orientation = baseOrientation
        ),
        grid = grid,
        letterIndex = letterIndex,
        bounds = bounds
    )

    val orderedWords = priorityWords.drop(INDEX_STEP) + remainingWords
    for (word in orderedWords) {
        val candidates = findCandidatePlacements(word, grid, letterIndex, bounds)
        if (candidates.isEmpty()) {
            continue
        }
        val selected = candidates[random.nextInt(candidates.size)]
        placeWord(selected, grid, letterIndex, bounds)
    }

    return buildCrosswordRows(grid, bounds)
}

internal fun buildCrosswordGridFromRows(rows: List<String>): List<List<CrosswordCell>> {
    if (rows.isEmpty()) {
        return emptyList()
    }
    val normalizedRows = normalizeCrosswordRows(rows)
    return normalizedRows.map { row ->
        row.map { char -> toCrosswordCell(char) }
    }
}

internal fun extractCrosswordWords(
    rows: List<String>,
    minWordLength: Int = MIN_CROSSWORD_WORD_LENGTH
): List<CrosswordWord> {
    if (rows.isEmpty()) {
        return emptyList()
    }
    val normalizedRows = normalizeCrosswordRows(rows)
    val words = mutableListOf<CrosswordWord>()
    for ((rowIndex, row) in normalizedRows.withIndex()) {
        collectWordsFromLine(
            letters = row.toCharArray(),
            minWordLength = minWordLength,
            positionForIndex = { colIndex -> GridPosition(rowIndex, colIndex) },
            results = words
        )
    }
    val columnCount = normalizedRows.first().length
    val columnLetters = CharArray(normalizedRows.size)
    for (colIndex in ORIGIN_INDEX until columnCount) {
        for (rowIndex in normalizedRows.indices) {
            columnLetters[rowIndex] = normalizedRows[rowIndex][colIndex]
        }
        collectWordsFromLine(
            letters = columnLetters,
            minWordLength = minWordLength,
            positionForIndex = { rowIndex -> GridPosition(rowIndex, colIndex) },
            results = words
        )
    }
    return words
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

internal fun buildMissingWordsState(
    seedLetters: String,
    dictionary: List<String>,
    crosswordWords: Map<String, CrosswordWord>
): MissingWordsState {
    if (seedLetters.isBlank()) {
        return emptyMissingWordsState()
    }
    val candidates = buildMiniDictionary(seedLetters, dictionary)
        .filter { it.length >= MIN_CROSSWORD_WORD_LENGTH }
    val normalizedCandidates = normalizeCrosswordWords(candidates)
    val missingWords = normalizedCandidates.filterNot { crosswordWords.containsKey(it) }
    val entries = missingWords.associateWith { MissingWordEntry(word = it, isGuessed = false) }
    return MissingWordsState(entries, entries.size, null)
}

internal fun applyMissingWordGuess(
    selectedWord: String,
    state: MissingWordsState
): MissingWordGuessResult {
    val normalized = selectedWord.trim().uppercase()
    if (normalized.isEmpty()) {
        return MissingWordGuessResult(state, MissingWordMatch.None)
    }
    val entry = state.entries[normalized] ?: return MissingWordGuessResult(state, MissingWordMatch.None)
    if (entry.isGuessed) {
        val updatedState = state.copy(lastGuessedWord = normalized)
        return MissingWordGuessResult(updatedState, MissingWordMatch.AlreadyGuessed)
    }
    val updatedEntries = state.entries.toMutableMap()
    updatedEntries[normalized] = entry.copy(isGuessed = true)
    val updatedCount = (state.remainingCount - INDEX_STEP).coerceAtLeast(ORIGIN_INDEX)
    val updatedState = state.copy(
        entries = updatedEntries.toMap(),
        remainingCount = updatedCount,
        lastGuessedWord = normalized
    )
    return MissingWordGuessResult(updatedState, MissingWordMatch.NewlyGuessed)
}

private fun horizontalWord(word: String, row: Int, startCol: Int): CrosswordWord {
    val positions = word.indices
        .map { colOffset -> GridPosition(row, startCol + colOffset) }
        .toSet()
    return CrosswordWord(word = word, positions = positions)
}

private fun normalizeCrosswordWords(words: List<String>): List<String> {
    return words.asSequence()
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .filter { candidate -> candidate.all { char -> char in 'A'..'Z' } }
        .distinct()
        .toList()
}

private fun pickLongestWords(words: List<String>): List<String> {
    val maxLength = words.maxOf { it.length }
    return words.filter { it.length == maxLength }
}

private fun pickPriorityWords(words: List<String>, baseWord: String): List<String> {
    val orderedByLength = words.sortedByDescending { it.length }
    val priorityWords = mutableListOf<String>()
    priorityWords.add(baseWord)
    for (word in orderedByLength) {
        if (word == baseWord) {
            continue
        }
        priorityWords.add(word)
        if (priorityWords.size == PRIORITY_LONGEST_WORDS_COUNT) {
            break
        }
    }
    return priorityWords
}

private fun findCandidatePlacements(
    word: String,
    grid: Map<GridPosition, CellState>,
    letterIndex: Map<Char, List<GridPosition>>,
    bounds: CrosswordBounds
): List<WordPlacement> {
    val placements = LinkedHashSet<WordPlacement>()
    for ((index, letter) in word.withIndex()) {
        val positions = letterIndex[letter] ?: continue
        for (position in positions) {
            val horizontalStart = GridPosition(position.row, position.col - index)
            val horizontalPlacement = WordPlacement(word, horizontalStart, WordOrientation.HORIZONTAL)
            if (canPlaceWord(horizontalPlacement, grid, bounds)) {
                placements.add(horizontalPlacement)
            }
            val verticalStart = GridPosition(position.row - index, position.col)
            val verticalPlacement = WordPlacement(word, verticalStart, WordOrientation.VERTICAL)
            if (canPlaceWord(verticalPlacement, grid, bounds)) {
                placements.add(verticalPlacement)
            }
        }
    }
    return placements.toList()
}

private fun placeWord(
    placement: WordPlacement,
    grid: MutableMap<GridPosition, CellState>,
    letterIndex: MutableMap<Char, MutableList<GridPosition>>,
    bounds: CrosswordBounds
) {
    val step = orientationStep(placement.orientation)
    for (index in placement.word.indices) {
        val row = placement.start.row + step.row * index
        val col = placement.start.col + step.col * index
        val position = GridPosition(row, col)
        val letter = placement.word[index]
        val existing = grid[position]
        if (existing == null) {
            grid[position] = CellState(
                letter = letter,
                hasHorizontal = placement.orientation == WordOrientation.HORIZONTAL,
                hasVertical = placement.orientation == WordOrientation.VERTICAL
            )
            letterIndex.getOrPut(letter) { mutableListOf() }.add(position)
        } else {
            when (placement.orientation) {
                WordOrientation.HORIZONTAL -> existing.hasHorizontal = true
                WordOrientation.VERTICAL -> existing.hasVertical = true
            }
        }
        bounds.update(position)
    }
}

private fun buildCrosswordRows(
    grid: Map<GridPosition, CellState>,
    bounds: CrosswordBounds
): List<String> {
    val columnCount = (bounds.maxCol - bounds.minCol) + INDEX_STEP
    val rows = mutableListOf<String>()
    for (row in bounds.minRow..bounds.maxRow) {
        val rowChars = CharArray(columnCount) { CROSSWORD_EMPTY_CELL }
        for ((position, cell) in grid) {
            if (position.row == row) {
                val columnIndex = position.col - bounds.minCol
                rowChars[columnIndex] = cell.letter.lowercaseChar()
            }
        }
        rows.add(rowChars.concatToString())
    }
    return rows
}

private fun orientationStep(orientation: WordOrientation): GridPosition {
    return when (orientation) {
        WordOrientation.HORIZONTAL -> GridPosition(ORIGIN_INDEX, INDEX_STEP)
        WordOrientation.VERTICAL -> GridPosition(INDEX_STEP, ORIGIN_INDEX)
    }
}

private fun normalizeCrosswordRows(rows: List<String>): List<String> {
    val columnCount = rows.maxOf { it.length }
    return rows.map { row -> row.padEnd(columnCount, CROSSWORD_EMPTY_CELL) }
}

private fun toCrosswordCell(char: Char): CrosswordCell {
    if (char == CROSSWORD_EMPTY_CELL) {
        return CrosswordCell(letter = null, isActive = false, isRevealed = false)
    }
    return CrosswordCell(
        letter = char.uppercaseChar(),
        isActive = true,
        isRevealed = char.isUpperCase()
    )
}

private fun collectWordsFromLine(
    letters: CharArray,
    minWordLength: Int,
    positionForIndex: (Int) -> GridPosition,
    results: MutableList<CrosswordWord>
) {
    val wordBuilder = StringBuilder()
    val positions = mutableListOf<GridPosition>()
    fun flushWord() {
        if (wordBuilder.length >= minWordLength) {
            results.add(
                CrosswordWord(
                    word = wordBuilder.toString(),
                    positions = positions.toSet()
                )
            )
        }
        wordBuilder.setLength(ORIGIN_INDEX)
        positions.clear()
    }
    for (index in letters.indices) {
        val char = letters[index]
        if (char == CROSSWORD_EMPTY_CELL) {
            flushWord()
        } else {
            wordBuilder.append(char.uppercaseChar())
            positions.add(positionForIndex(index))
        }
    }
    flushWord()
}

private fun canPlaceWord(
    placement: WordPlacement,
    grid: Map<GridPosition, CellState>,
    bounds: CrosswordBounds
): Boolean {
    val step = orientationStep(placement.orientation)
    val beforeStart = GridPosition(
        placement.start.row - step.row,
        placement.start.col - step.col
    )
    if (grid.containsKey(beforeStart)) {
        return false
    }
    val lastIndex = placement.word.length - LAST_INDEX_OFFSET
    val end = GridPosition(
        placement.start.row + step.row * lastIndex,
        placement.start.col + step.col * lastIndex
    )
    val afterEnd = GridPosition(end.row + step.row, end.col + step.col)
    if (grid.containsKey(afterEnd)) {
        return false
    }

    var minRow = bounds.minRow
    var maxRow = bounds.maxRow
    var minCol = bounds.minCol
    var maxCol = bounds.maxCol

    var addsNewCell = false
    for (index in placement.word.indices) {
        val row = placement.start.row + step.row * index
        val col = placement.start.col + step.col * index
        val position = GridPosition(row, col)
        val letter = placement.word[index]
        val existing = grid[position]
        if (existing == null) {
            if (!perpendicularNeighborsEmpty(position, placement.orientation, grid)) {
                return false
            }
            addsNewCell = true
        } else {
            if (existing.letter != letter) {
                return false
            }
            val blocked = when (placement.orientation) {
                WordOrientation.HORIZONTAL -> existing.hasHorizontal
                WordOrientation.VERTICAL -> existing.hasVertical
            }
            if (blocked) {
                return false
            }
        }
        minRow = minOf(minRow, row)
        maxRow = maxOf(maxRow, row)
        minCol = minOf(minCol, col)
        maxCol = maxOf(maxCol, col)
    }
    val rowCount = (maxRow - minRow) + INDEX_STEP
    val columnCount = (maxCol - minCol) + INDEX_STEP
    if (rowCount > MAX_CROSSWORD_ROWS || columnCount > MAX_CROSSWORD_COLUMNS) {
        return false
    }
    return addsNewCell
}

private fun perpendicularNeighborsEmpty(
    position: GridPosition,
    orientation: WordOrientation,
    grid: Map<GridPosition, CellState>
): Boolean {
    return when (orientation) {
        WordOrientation.HORIZONTAL -> {
            val up = GridPosition(position.row - INDEX_STEP, position.col)
            val down = GridPosition(position.row + INDEX_STEP, position.col)
            grid[up] == null && grid[down] == null
        }
        WordOrientation.VERTICAL -> {
            val left = GridPosition(position.row, position.col - INDEX_STEP)
            val right = GridPosition(position.row, position.col + INDEX_STEP)
            grid[left] == null && grid[right] == null
        }
    }
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
