package com.ustas.words

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetWordFeedbackTest {
    @Test
    fun restoringOfflineDiscoveriesIsSilent() {
        val feedback = NetWordFeedback()

        assertFalse(feedback.confirm(listOf("CAT", "TEAM"), emptyList()))
    }

    @Test
    fun restoredWordDoesNotConsumeFeedbackForNewOnlineDiscovery() {
        val feedback = NetWordFeedback()
        feedback.awaitConfirmation("TEAM")

        assertFalse(feedback.confirm(listOf("CAT"), listOf("TEAM")))
        assertTrue(feedback.confirm(listOf("TEAM"), emptyList()))
        assertFalse(feedback.confirm(listOf("TEAM"), emptyList()))
    }

    @Test
    fun reconnectDoesNotReplayFeedbackFromPreviousConnection() {
        val feedback = NetWordFeedback()
        feedback.awaitConfirmation("CAT")
        feedback.clear()

        assertFalse(feedback.confirm(listOf("CAT"), emptyList()))

        feedback.awaitConfirmation("TEAM")
        assertTrue(feedback.confirm(listOf("TEAM"), emptyList()))
    }

    @Test
    fun discardedDiscoveryDoesNotLeavePendingFeedback() {
        val feedback = NetWordFeedback()
        feedback.awaitConfirmation("CAT")

        assertFalse(feedback.confirm(emptyList(), emptyList()))
        assertFalse(feedback.confirm(listOf("CAT"), emptyList()))
    }
}
