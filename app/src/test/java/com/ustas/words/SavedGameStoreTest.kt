package com.ustas.words

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LEGACY_SAVED_GAME_TEST_VERSION = 1
private const val GAME_ID_SAVED_GAME_TEST_VERSION = 2
private const val NET_TEST_VERSION = 7

class SavedGameStoreTest {
    private val gameId = "01234567-89ab-cdef-0123-456789abcdef"
    private val rows = listOf("caT.", "..e.", "..a.", "..m.")
    private val words = listOf(
        CrosswordWord("CAT", setOf(GridPosition(0, 0), GridPosition(0, 1), GridPosition(0, 2))),
        CrosswordWord("TEAM", setOf(
            GridPosition(0, 2), GridPosition(1, 2), GridPosition(2, 2), GridPosition(3, 2)
        ))
    ).associateBy { it.word }

    @Test
    fun roundTripPreservesPuzzleAndProgress() {
        val missing = MissingWordsState(mapOf("MATE" to true, "TAME" to false), 1, "MATE")
        val netProgress = SavedNetProgress(
            participated = true,
            confirmedVersion = NET_TEST_VERSION,
            solvedBy = mapOf("CAT" to "player-one", "TEAM" to "player-two"),
            solvedWordOrder = listOf("CAT", "TEAM"),
            pendingWords = listOf("TEAM"),
            pendingReveals = listOf(GridPosition(0, 2)),
            players = listOf(
                SavedNetPlayer("player-one", "Alice", "yellow"),
                SavedNetPlayer("player-two", "Bob", "red")
            )
        )
        val saved = snapshotSavedGame(
            gameId = gameId,
            seedLetters = "CATEM",
            wheelLetters = "TEMAC".toList(),
            grid = buildCrosswordGridFromRows(rows),
            words = words,
            missingWords = missing,
            netProgress = netProgress
        )
        requireNotNull(saved)

        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { SavedGameCodec.write(it, saved) }
        }.toByteArray()
        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(SavedGameCodec::read)

        assertEquals(saved, restored)
        assertEquals(rows, restored?.gridRows)
        assertEquals(netProgress, restored?.netProgress)
        assertTrue(buildCrosswordGridFromRows(restored!!.gridRows)[0][2].isRevealed)
        assertFalse(buildCrosswordGridFromRows(restored.gridRows)[0][0].isRevealed)
    }

    @Test
    fun playerRosterRetainsDisconnectedPlayersAndUpdatesDetails() {
        val known = listOf(
            SavedNetPlayer("player-one", "Alice", "yellow"),
            SavedNetPlayer("player-two", "Bob", "red")
        )
        val current = listOf(SavedNetPlayer("player-one", "Alice New", "white"))

        assertEquals(
            listOf(known.last(), current.single()),
            mergeSavedNetPlayers(known, current)
        )
    }

    @Test
    fun offlineDiscoveriesExtendSavedNetworkProgress() {
        val saved = SavedNetProgress(
            participated = true,
            confirmedVersion = NET_TEST_VERSION,
            solvedBy = mapOf("CAT" to "player-two"),
            solvedWordOrder = listOf("CAT"),
            pendingWords = listOf("TEAM"),
            pendingReveals = listOf(GridPosition(0, 2))
        )

        val updated = mergeOfflineNetProgress(
            saved = saved,
            solvedBy = mapOf("TEAM" to "player-one"),
            solvedWordOrder = listOf("TEAM"),
            pendingWords = listOf("TEAM"),
            pendingReveals = listOf(GridPosition(0, 2), GridPosition(1, 2))
        )

        assertEquals(mapOf("CAT" to "player-two", "TEAM" to "player-one"), updated.solvedBy)
        assertEquals(listOf("CAT", "TEAM"), updated.solvedWordOrder)
        assertEquals(listOf("TEAM"), updated.pendingWords)
        assertEquals(listOf(GridPosition(0, 2), GridPosition(1, 2)), updated.pendingReveals)
        assertEquals(NET_TEST_VERSION, updated.confirmedVersion)
    }

    @Test
    fun malformedSaveIsRejected() {
        val saved = SavedGame(
            gameId = gameId,
            seedLetters = "CATEM",
            wheelLetters = "TEMAC",
            gridRows = rows,
            words = words,
            missingWords = emptyMissingWordsState()
        )
        val bytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { SavedGameCodec.write(it, saved) }
        }.toByteArray()

        assertNull(DataInputStream(ByteArrayInputStream(bytes.copyOfRange(0, bytes.size / 2)))
            .use { runCatching { SavedGameCodec.read(it) }.getOrNull() })
        assertFalse(saved.copy(wheelLetters = "CATEMX").isValid())
        assertFalse(saved.copy(words = mapOf("CAT" to CrosswordWord("CAT", setOf(GridPosition(8, 8)))))
            .isValid())
        assertFalse(saved.copy(netProgress = SavedNetProgress(pendingWords = listOf("CAT", "CAT")))
            .isValid())
    }

    @Test
    fun versionTwoSaveRestoresWithoutNetworkProgress() {
        val saved = SavedGame(
            gameId = gameId,
            seedLetters = "CATEM",
            wheelLetters = "TEMAC",
            gridRows = rows,
            words = words,
            missingWords = MissingWordsState(mapOf("MATE" to true), 0, "MATE"),
            netProgress = SavedNetProgress(solvedBy = mapOf("CAT" to "player-one"))
        )

        val restored = DataInputStream(ByteArrayInputStream(encodeVersionTwo(saved)))
            .use(SavedGameCodec::read)

        assertEquals(saved.copy(netProgress = SavedNetProgress()), restored)
    }

    @Test
    fun legacySaveRestoresPuzzleAndGetsGameId() {
        val saved = SavedGame(
            gameId = gameId,
            seedLetters = "CATEM",
            wheelLetters = "TEMAC",
            gridRows = rows,
            words = words,
            missingWords = MissingWordsState(mapOf("MATE" to true), 0, "MATE")
        )
        val versionTwoBytes = encodeVersionTwo(saved)
        val header = DataInputStream(ByteArrayInputStream(versionTwoBytes))
        val magic = header.readInt()
        header.readInt()
        header.readUTF()
        val payloadOffset = versionTwoBytes.size - header.available()
        val legacyBytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(magic)
                output.writeInt(LEGACY_SAVED_GAME_TEST_VERSION)
                output.write(versionTwoBytes, payloadOffset, versionTwoBytes.size - payloadOffset)
            }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(legacyBytes)).use(SavedGameCodec::read)

        assertEquals(saved.copy(gameId = restored?.gameId.orEmpty()), restored)
        assertTrue(restored!!.gameId.isNotBlank())
    }

    private fun encodeVersionTwo(saved: SavedGame): ByteArray {
        val currentBytes = ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { SavedGameCodec.write(it, saved) }
        }.toByteArray()
        val input = DataInputStream(ByteArrayInputStream(currentBytes))
        val magic = input.readInt()
        input.readInt()
        input.readUTF()
        input.readUTF()
        input.readUTF()
        repeat(input.readInt()) { input.readUTF() }
        repeat(input.readInt()) {
            input.readUTF()
            repeat(input.readInt()) {
                input.readInt()
                input.readInt()
            }
        }
        repeat(input.readInt()) {
            input.readUTF()
            input.readBoolean()
        }
        if (input.readBoolean()) input.readUTF()
        val oldPayloadLength = currentBytes.size - input.available()
        val headerLength = Int.SIZE_BYTES + Int.SIZE_BYTES
        return ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { output ->
                output.writeInt(magic)
                output.writeInt(GAME_ID_SAVED_GAME_TEST_VERSION)
                output.write(currentBytes, headerLength, oldPayloadLength - headerLength)
            }
        }.toByteArray()
    }
}
