package com.ustas.words

internal const val FIRST_ACTIVE_COUNT = 1

internal enum class NetJoinDecision {
    UploadLocal,
    ContinueSameGame,
    JoinOtherGame,
    WaitForGame
}

internal fun decideNetJoin(
    localGameId: String,
    serverGameId: String?,
    activeCount: Int,
    isHost: Boolean
): NetJoinDecision {
    if (activeCount == FIRST_ACTIVE_COUNT && isHost &&
        (localGameId.isNotBlank() || serverGameId == null)
    ) {
        return NetJoinDecision.UploadLocal
    }
    if (serverGameId == null) {
        return NetJoinDecision.WaitForGame
    }
    return if (localGameId.isNotBlank() && localGameId == serverGameId) {
        NetJoinDecision.ContinueSameGame
    } else {
        NetJoinDecision.JoinOtherGame
    }
}

internal fun shouldDiscardLocalProgress(
    awaitingServerGame: Boolean,
    localGameId: String,
    incomingGameId: String
): Boolean = awaitingServerGame || localGameId != incomingGameId
