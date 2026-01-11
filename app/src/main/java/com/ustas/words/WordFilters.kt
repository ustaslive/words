package com.ustas.words

internal fun filterForbiddenWords(
    words: List<String>,
    forbiddenWords: Set<String>
): List<String> {
    if (words.isEmpty() || forbiddenWords.isEmpty()) {
        return words
    }
    return words.filterNot { it in forbiddenWords }
}
