package com.ustas.words

internal data class NetPlayerScore(
    val wordCount: Int = 0,
    val points: Int = 0
)

internal fun calculateNetPlayerScores(
    solvedWordOrder: List<String>,
    solvedBy: Map<String, String>
): Map<String, NetPlayerScore> {
    val scores = mutableMapOf<String, NetPlayerScore>()
    val orderedWords = reconcileSolvedWordOrder(solvedWordOrder, solvedBy)
    orderedWords.forEachIndexed { index, word ->
        val playerId = solvedBy[word] ?: return@forEachIndexed
        val current = scores[playerId] ?: NetPlayerScore()
        scores[playerId] = current.copy(
            wordCount = current.wordCount + 1,
            points = current.points + index + 1
        )
    }
    return scores.toMap()
}

internal fun reconcileSolvedWordOrder(
    solvedWordOrder: List<String>,
    solvedBy: Map<String, String>
): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (word in solvedWordOrder) {
        if (word in solvedBy && seen.add(word)) {
            result.add(word)
        }
    }
    for (word in solvedBy.keys) {
        if (seen.add(word)) {
            result.add(word)
        }
    }
    return result
}

internal fun appendSolvedWord(
    solvedWordOrder: List<String>,
    word: String
): List<String> {
    if (word.isBlank() || word in solvedWordOrder) {
        return solvedWordOrder
    }
    return solvedWordOrder + word
}
