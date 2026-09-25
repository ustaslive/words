package com.ustas.words

internal class NetWordFeedback {
    private val awaitingConfirmation = mutableSetOf<String>()

    fun awaitConfirmation(word: String) {
        awaitingConfirmation.add(word)
    }

    fun confirm(confirmedWords: List<String>, pendingWords: List<String>): Boolean {
        val shouldPlay = confirmedWords.any { it in awaitingConfirmation }
        awaitingConfirmation.retainAll(pendingWords.toSet())
        return shouldPlay
    }

    fun clear() {
        awaitingConfirmation.clear()
    }
}
