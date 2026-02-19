package com.ustas.words

import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

private const val MODE005_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val MODE005_CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ"
private const val MODE005_VOWELS = "AEIOU"
private const val MODE005_MIN_VOWEL_COUNT = 2
private const val MODE005_MID_VOWEL_COUNT = 3
private const val MODE005_MAX_VOWEL_COUNT = 4
private const val MODE005_TWO_VOWELS_WEIGHT = 3
private const val MODE005_THREE_VOWELS_WEIGHT = 5
private const val MODE005_FOUR_VOWELS_WEIGHT = 2
private const val MODE005_DEFAULT_MAX_LETTER_SWAP_CYCLES = 5
private const val MODE005_DEFAULT_MAX_REPEAT_SHARE = 0.40
private const val MODE005_DEFAULT_TOP_FREQUENT_SHARE = 0.10
private const val MODE005_LINE_COMMENT_PREFIX = "#"
private const val MODE005_VALUE_SEPARATOR = ':'
private const val MODE005_CROSSWORDS_GENERATED_PREFIX = "crosswords_generated="
private const val MODE005_ORIGIN_INDEX = 0
private const val MODE005_INDEX_STEP = 1
private const val MODE005_MIN_TOP_WORD_COUNT = 1
private const val MODE005_SINGLETON_COUNT = 1
private const val MODE005_MIN_REPEAT_DENOMINATOR = 1
private const val MODE005_EMPTY_WORD_COUNT = 0

internal data class Mode005WordStats(
    val frequencies: Map<String, Int>,
    val crosswordsGenerated: Int?
)

internal data class Mode005GeneratorConfig(
    val minWordLength: Int = MIN_CROSSWORD_WORD_LENGTH,
    val minCrosswordWordCount: Int = MIN_CROSSWORD_WORD_COUNT,
    val maxGenerationAttempts: Int = MAX_CROSSWORD_GENERATION_ATTEMPTS,
    val maxLetterSwapCycles: Int = MODE005_DEFAULT_MAX_LETTER_SWAP_CYCLES,
    val maxRepeatShareWithControlSet: Double = MODE005_DEFAULT_MAX_REPEAT_SHARE
)

internal fun emptyMode005WordStats(): Mode005WordStats {
    return Mode005WordStats(frequencies = emptyMap(), crosswordsGenerated = null)
}

internal fun parseMode005WordStats(lines: Sequence<String>): Mode005WordStats {
    val frequencies = linkedMapOf<String, Int>()
    var crosswordsGenerated: Int? = null
    lines.forEachIndexed { index, rawLine ->
        val lineNumber = index + MODE005_INDEX_STEP
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return@forEachIndexed
        }
        if (line.startsWith(MODE005_LINE_COMMENT_PREFIX)) {
            val comment = line.removePrefix(MODE005_LINE_COMMENT_PREFIX).trim()
            val parsedCrosswordCount = parseCrosswordsGeneratedComment(comment)
            if (parsedCrosswordCount != null) {
                crosswordsGenerated = parsedCrosswordCount
            }
            return@forEachIndexed
        }

        val separatorIndex = line.indexOf(MODE005_VALUE_SEPARATOR)
        require(separatorIndex >= MODE005_ORIGIN_INDEX) {
            "Invalid stats line at $lineNumber. Expected '<word>:<count>'."
        }
        val word = line.substring(MODE005_ORIGIN_INDEX, separatorIndex).trim().uppercase()
        require(word.isNotEmpty()) {
            "Invalid stats line at $lineNumber. Missing word key."
        }
        require(word.all { char -> isMode005UpperLetter(char) }) {
            "Invalid stats line at $lineNumber. Word key must contain only A-Z letters."
        }

        val countText = line.substring(separatorIndex + MODE005_INDEX_STEP).trim()
        val count = countText.toIntOrNull()
            ?: throw IllegalArgumentException(
                "Invalid stats line at $lineNumber. Count must be integer."
            )
        require(count >= MODE005_ORIGIN_INDEX) {
            "Invalid stats line at $lineNumber. Count must be non-negative."
        }
        frequencies[word] = count
    }
    return Mode005WordStats(
        frequencies = frequencies.toMap(),
        crosswordsGenerated = crosswordsGenerated
    )
}

internal fun buildMode005TopFrequentWordSet(
    dictionary: List<String>,
    wordStats: Map<String, Int>,
    topFrequentWordShare: Double = MODE005_DEFAULT_TOP_FREQUENT_SHARE
): Set<String> {
    if (dictionary.isEmpty() || topFrequentWordShare <= MODE005_ORIGIN_INDEX.toDouble()) {
        return emptySet()
    }
    val uniqueDictionary = normalizeMode005Dictionary(dictionary)
    if (uniqueDictionary.isEmpty()) {
        return emptySet()
    }
    val takeCount = max(
        MODE005_MIN_TOP_WORD_COUNT,
        ceil(uniqueDictionary.size * topFrequentWordShare).toInt()
    ).coerceAtMost(uniqueDictionary.size)
    val sorted = uniqueDictionary.sortedWith(
        compareByDescending<String> { word -> wordStats[word] ?: MODE005_EMPTY_WORD_COUNT }
            .thenBy { word -> word }
    )
    return sorted.take(takeCount).toSet()
}

internal fun generateCrosswordWithMode005(
    dictionary: List<String>,
    previousRoundWordSet: Set<String>,
    topFrequentWordSet: Set<String>,
    seedLengthRange: IntRange,
    config: Mode005GeneratorConfig = Mode005GeneratorConfig(),
    random: Random = Random.Default
): CrosswordGenerationResult {
    if (dictionary.isEmpty() || config.maxGenerationAttempts < MODE005_INDEX_STEP) {
        return CrosswordGenerationResult.Failure(
            rejectedSeedLetters = emptyList(),
            attempts = MODE005_ORIGIN_INDEX
        )
    }
    val repeatControlSet = if (previousRoundWordSet.isNotEmpty()) {
        normalizeMode005WordSet(previousRoundWordSet)
    } else {
        normalizeMode005WordSet(topFrequentWordSet)
    }
    val rejectedSeedLetters = mutableListOf<String>()
    var attemptsUsed = MODE005_ORIGIN_INDEX
    val maxSwapCycles = config.maxLetterSwapCycles.coerceAtLeast(MODE005_INDEX_STEP)

    while (attemptsUsed < config.maxGenerationAttempts) {
        attemptsUsed += MODE005_INDEX_STEP
        val initialSeed = generateMode005SeedLetters(seedLengthRange, random)
        if (initialSeed.isEmpty()) {
            rejectedSeedLetters.add(initialSeed)
            continue
        }

        var seedLetters = initialSeed
        val remainingAdditionAlphabet = buildMode005RemainingAdditionAlphabet(seedLetters).toMutableSet()
        val blockedReturnLetters = mutableSetOf<Char>()

        for (swapCycle in MODE005_INDEX_STEP..maxSwapCycles) {
            val fullWordSet = buildMode005FullWordSet(
                seedLetters = seedLetters,
                dictionary = dictionary,
                minWordLength = config.minWordLength
            )
            if (fullWordSet.size < config.minCrosswordWordCount) {
                break
            }

            val repeatSourceWordSet = fullWordSet.intersect(repeatControlSet)
            val denominator = max(MODE005_MIN_REPEAT_DENOMINATOR, fullWordSet.size)
            val repeatShare = repeatSourceWordSet.size.toDouble() / denominator.toDouble()
            if (repeatShare <= config.maxRepeatShareWithControlSet) {
                val layout = buildCrosswordLayout(seedLetters, dictionary, random)
                if (layout.words.size >= config.minCrosswordWordCount) {
                    val finalizedSeedLetters = trimMode005SeedLettersToUsedLetters(
                        seedLetters = seedLetters,
                        allWordSet = fullWordSet
                    )
                    return CrosswordGenerationResult.Success(
                        seedLetters = finalizedSeedLetters,
                        layout = layout,
                        rejectedSeedLetters = rejectedSeedLetters,
                        attempts = attemptsUsed
                    )
                }
                break
            }

            val removedLetter = pickMode005LetterToRemove(
                seedLetters = seedLetters,
                repeatSourceWordSet = repeatSourceWordSet,
                random = random
            )
            if (removedLetter == null) {
                break
            }
            val seedAfterRemoval = removeMode005Letter(seedLetters, removedLetter)
            if (seedAfterRemoval.isEmpty()) {
                break
            }
            blockedReturnLetters.add(removedLetter)
            val additionPool = buildMode005AdditionPool(
                seedLetters = seedAfterRemoval,
                remainingAdditionAlphabet = remainingAdditionAlphabet,
                blockedReturnLetters = blockedReturnLetters
            )
            if (additionPool.isEmpty()) {
                break
            }
            val addedLetter = additionPool[random.nextInt(additionPool.size)]
            remainingAdditionAlphabet.remove(addedLetter)
            seedLetters = shuffleMode005Letters(seedAfterRemoval + addedLetter, random)
        }

        rejectedSeedLetters.add(initialSeed)
    }
    return CrosswordGenerationResult.Failure(
        rejectedSeedLetters = rejectedSeedLetters,
        attempts = attemptsUsed
    )
}

private fun parseCrosswordsGeneratedComment(comment: String): Int? {
    val normalized = comment.lowercase()
    if (!normalized.startsWith(MODE005_CROSSWORDS_GENERATED_PREFIX)) {
        return null
    }
    val valueText = comment.substring(MODE005_CROSSWORDS_GENERATED_PREFIX.length).trim()
    val value = valueText.toIntOrNull() ?: return null
    return value.takeIf { it >= MODE005_ORIGIN_INDEX }
}

private fun normalizeMode005Dictionary(dictionary: List<String>): List<String> {
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (rawWord in dictionary) {
        val normalized = normalizeMode005Word(rawWord) ?: continue
        if (seen.add(normalized)) {
            result.add(normalized)
        }
    }
    return result
}

private fun normalizeMode005WordSet(words: Set<String>): Set<String> {
    return words.mapNotNull { rawWord -> normalizeMode005Word(rawWord) }.toSet()
}

private fun normalizeMode005Word(rawWord: String): String? {
    val candidate = rawWord.trim().uppercase()
    if (candidate.isEmpty()) {
        return null
    }
    if (!candidate.all { char -> isMode005UpperLetter(char) }) {
        return null
    }
    return candidate
}

private fun isMode005UpperLetter(char: Char): Boolean {
    return char in 'A'..'Z'
}

private fun generateMode005SeedLetters(seedLengthRange: IntRange, random: Random): String {
    if (seedLengthRange.isEmpty()) {
        return ""
    }
    val minLength = minOf(seedLengthRange.first, seedLengthRange.last)
    val maxLength = maxOf(seedLengthRange.first, seedLengthRange.last)
    if (maxLength < MODE005_INDEX_STEP) {
        return ""
    }
    val seedLength = if (minLength == maxLength) {
        maxLength
    } else {
        random.nextInt(maxLength - minLength + MODE005_INDEX_STEP) + minLength
    }
    if (seedLength < MODE005_INDEX_STEP) {
        return ""
    }
    val vowelCount = pickMode005SeedVowelCount(seedLength, random)
    val consonantCount = (seedLength - vowelCount).coerceAtLeast(MODE005_ORIGIN_INDEX)
    val letters = mutableListOf<Char>()
    repeat(vowelCount) {
        letters.add(MODE005_VOWELS[random.nextInt(MODE005_VOWELS.length)])
    }
    repeat(consonantCount) {
        letters.add(MODE005_CONSONANTS[random.nextInt(MODE005_CONSONANTS.length)])
    }
    while (letters.size < seedLength) {
        letters.add(MODE005_ALPHABET[random.nextInt(MODE005_ALPHABET.length)])
    }
    letters.shuffle(random)
    return letters.joinToString(separator = "")
}

private fun pickMode005SeedVowelCount(seedLength: Int, random: Random): Int {
    val weighted = listOf(
        MODE005_MIN_VOWEL_COUNT to MODE005_TWO_VOWELS_WEIGHT,
        MODE005_MID_VOWEL_COUNT to MODE005_THREE_VOWELS_WEIGHT,
        MODE005_MAX_VOWEL_COUNT to MODE005_FOUR_VOWELS_WEIGHT
    )
    val allowed = weighted.filter { (count, _) -> count <= seedLength }
    if (allowed.isEmpty()) {
        return seedLength.coerceAtMost(MODE005_MAX_VOWEL_COUNT).coerceAtLeast(MODE005_ORIGIN_INDEX)
    }
    val totalWeight = allowed.sumOf { (_, weight) -> weight }
    val pickedWeight = random.nextInt(totalWeight)
    var cumulative = MODE005_ORIGIN_INDEX
    for ((count, weight) in allowed) {
        cumulative += weight
        if (pickedWeight < cumulative) {
            return count
        }
    }
    return allowed.last().first
}

private fun buildMode005FullWordSet(
    seedLetters: String,
    dictionary: List<String>,
    minWordLength: Int
): Set<String> {
    if (seedLetters.isBlank()) {
        return emptySet()
    }
    return buildMiniDictionary(seedLetters, dictionary)
        .asSequence()
        .mapNotNull { rawWord -> normalizeMode005Word(rawWord) }
        .filter { word -> word.length >= minWordLength }
        .toSet()
}

internal fun trimMode005SeedLettersToUsedLetters(
    seedLetters: String,
    allWordSet: Set<String>
): String {
    if (seedLetters.isEmpty()) {
        return seedLetters
    }
    val usedLetters = mutableSetOf<Char>()
    for (word in allWordSet) {
        for (char in word) {
            if (char in MODE005_ALPHABET) {
                usedLetters.add(char)
            }
        }
    }
    if (usedLetters.isEmpty()) {
        return seedLetters
    }
    return seedLetters.filter { char -> usedLetters.contains(char) }
}

private fun pickMode005LetterToRemove(
    seedLetters: String,
    repeatSourceWordSet: Set<String>,
    random: Random
): Char? {
    if (seedLetters.isEmpty()) {
        return null
    }
    val seedCounts = seedLetters.groupingBy { char -> char }.eachCount()
    val singletonLetters = seedCounts
        .filterValues { count -> count == MODE005_SINGLETON_COUNT }
        .keys
        .toList()
    val singletonConsonants = singletonLetters.filter { letter -> letter in MODE005_CONSONANTS }
    val dominantConsonant = pickDominantMode005Consonant(
        repeatSourceWordSet = repeatSourceWordSet,
        singletonConsonants = singletonConsonants,
        random = random
    )
    if (dominantConsonant != null) {
        return dominantConsonant
    }
    if (singletonConsonants.isNotEmpty()) {
        return singletonConsonants[random.nextInt(singletonConsonants.size)]
    }
    if (singletonLetters.isNotEmpty()) {
        return singletonLetters[random.nextInt(singletonLetters.size)]
    }
    return seedLetters[random.nextInt(seedLetters.length)]
}

private fun pickDominantMode005Consonant(
    repeatSourceWordSet: Set<String>,
    singletonConsonants: List<Char>,
    random: Random
): Char? {
    if (repeatSourceWordSet.isEmpty() || singletonConsonants.isEmpty()) {
        return null
    }
    val singletonSet = singletonConsonants.toSet()
    val consonantCounts = mutableMapOf<Char, Int>()
    for (word in repeatSourceWordSet) {
        for (char in word) {
            if (!singletonSet.contains(char)) {
                continue
            }
            val currentCount = consonantCounts[char] ?: MODE005_ORIGIN_INDEX
            consonantCounts[char] = currentCount + MODE005_INDEX_STEP
        }
    }
    if (consonantCounts.isEmpty()) {
        return null
    }
    val maxCount = consonantCounts.values.maxOrNull() ?: return null
    val bestLetters = consonantCounts
        .filterValues { count -> count == maxCount }
        .keys
        .toList()
    return bestLetters[random.nextInt(bestLetters.size)]
}

private fun removeMode005Letter(seedLetters: String, letterToRemove: Char): String {
    val index = seedLetters.indexOf(letterToRemove)
    if (index < MODE005_ORIGIN_INDEX) {
        return seedLetters
    }
    return seedLetters.removeRange(index, index + MODE005_INDEX_STEP)
}

private fun shuffleMode005Letters(letters: String, random: Random): String {
    val chars = letters.toMutableList()
    chars.shuffle(random)
    return chars.joinToString(separator = "")
}

private fun buildMode005RemainingAdditionAlphabet(seedLetters: String): Set<Char> {
    val currentLetters = seedLetters.filter { char -> char in MODE005_ALPHABET }.toSet()
    return MODE005_ALPHABET.filterNot { char -> currentLetters.contains(char) }.toSet()
}

private fun buildMode005AdditionPool(
    seedLetters: String,
    remainingAdditionAlphabet: Set<Char>,
    blockedReturnLetters: Set<Char>
): List<Char> {
    val currentLetters = seedLetters.filter { char -> char in MODE005_ALPHABET }.toSet()
    return remainingAdditionAlphabet
        .asSequence()
        .filter { char -> !blockedReturnLetters.contains(char) }
        .filter { char -> !currentLetters.contains(char) }
        .sorted()
        .toList()
}
