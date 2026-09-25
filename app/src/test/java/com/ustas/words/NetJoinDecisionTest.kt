package com.ustas.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MULTIPLE_ACTIVE_COUNT = 2

class NetJoinDecisionTest {
    @Test
    fun firstPlayerPublishesLocalGameEvenWhenServerHasOldSnapshot() {
        assertEquals(
            NetJoinDecision.UploadLocal,
            decideNetJoin("local-game", "old-server-game", activeCount = FIRST_ACTIVE_COUNT, isHost = true)
        )
        assertEquals(
            NetJoinDecision.UploadLocal,
            decideNetJoin("local-game", null, activeCount = FIRST_ACTIVE_COUNT, isHost = true)
        )
    }

    @Test
    fun laterPlayerKeepsSharedGameAndDiscardsDifferentLocalGame() {
        assertEquals(
            NetJoinDecision.ContinueSameGame,
            decideNetJoin("shared-game", "shared-game", activeCount = MULTIPLE_ACTIVE_COUNT, isHost = false)
        )
        assertEquals(
            NetJoinDecision.JoinOtherGame,
            decideNetJoin("local-game", "shared-game", activeCount = MULTIPLE_ACTIVE_COUNT, isHost = true)
        )
    }

    @Test
    fun laterPlayerWaitsUntilFirstPlayerPublishes() {
        assertEquals(
            NetJoinDecision.WaitForGame,
            decideNetJoin("local-game", null, activeCount = MULTIPLE_ACTIVE_COUNT, isHost = false)
        )
    }

    @Test
    fun waitingPlayerDiscardsLocalProgressEvenForSameGameId() {
        assertTrue(shouldDiscardLocalProgress(true, "shared-game", "shared-game"))
        assertTrue(shouldDiscardLocalProgress(false, "local-game", "server-game"))
        assertFalse(shouldDiscardLocalProgress(false, "shared-game", "shared-game"))
    }
}
