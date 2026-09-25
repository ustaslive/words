package com.ustas.words

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID

private const val SAVED_GAME_FILE_NAME = "saved_game.bin"
private const val SAVED_GAME_MAGIC = 0x574F5244
private const val LEGACY_SAVED_GAME_VERSION = 1
private const val GAME_ID_SAVED_GAME_VERSION = 2
private const val SAVED_GAME_VERSION = 3
private const val MIN_SAVED_ENTRY_COUNT = 1
private const val MIN_SAVED_NET_VERSION = 1
private const val MAX_SAVED_WORDS = MAX_CROSSWORD_ROWS * MAX_CROSSWORD_COLUMNS
private const val MAX_SAVED_MISSING_WORDS = 10_000
private const val MAX_SAVED_NET_PLAYERS = 128
private const val MAX_SAVED_FILE_BYTES = 1_048_576

internal data class SavedNetPlayer(
    val playerId: String,
    val playerName: String,
    val playerColorId: String
)

internal data class SavedNetProgress(
    val participated: Boolean = false,
    val confirmedVersion: Int = MIN_SAVED_NET_VERSION,
    val solvedBy: Map<String, String> = emptyMap(),
    val solvedWordOrder: List<String> = emptyList(),
    val pendingWords: List<String> = emptyList(),
    val pendingReveals: List<GridPosition> = emptyList(),
    val players: List<SavedNetPlayer> = emptyList()
) {
    fun isValid(): Boolean = confirmedVersion >= MIN_SAVED_NET_VERSION &&
        solvedBy.size <= MAX_SAVED_WORDS &&
        solvedBy.all { (word, playerId) -> word.isNotBlank() && playerId.isNotBlank() } &&
        solvedWordOrder.size <= MAX_SAVED_WORDS &&
        solvedWordOrder.distinct().size == solvedWordOrder.size &&
        solvedWordOrder.all { it in solvedBy } &&
        pendingWords.size <= MAX_SAVED_WORDS &&
        pendingWords.distinct().size == pendingWords.size &&
        pendingWords.all { it.isNotBlank() } &&
        pendingReveals.size <= MAX_SAVED_WORDS &&
        pendingReveals.distinct().size == pendingReveals.size &&
        pendingReveals.all { it.row in 0 until MAX_CROSSWORD_ROWS &&
            it.col in 0 until MAX_CROSSWORD_COLUMNS } &&
        players.size <= MAX_SAVED_NET_PLAYERS &&
        players.map { it.playerId }.distinct().size == players.size &&
        players.all { it.playerId.isNotBlank() && it.playerColorId.isNotBlank() }
}

internal fun mergeSavedNetPlayers(
    known: List<SavedNetPlayer>,
    current: List<SavedNetPlayer>
): List<SavedNetPlayer> {
    val players = linkedMapOf<String, SavedNetPlayer>()
    (known + current).forEach { player ->
        players.remove(player.playerId)
        players[player.playerId] = player
    }
    return players.values.toList().takeLast(MAX_SAVED_NET_PLAYERS)
}

internal fun mergeOfflineNetProgress(
    saved: SavedNetProgress,
    solvedBy: Map<String, String>,
    solvedWordOrder: List<String>,
    pendingWords: List<String>,
    pendingReveals: List<GridPosition>
): SavedNetProgress {
    val mergedSolvedBy = saved.solvedBy + solvedBy
    return saved.copy(
        solvedBy = mergedSolvedBy,
        solvedWordOrder = reconcileSolvedWordOrder(
            saved.solvedWordOrder + solvedWordOrder,
            mergedSolvedBy
        ),
        pendingWords = (saved.pendingWords + pendingWords).distinct(),
        pendingReveals = (saved.pendingReveals + pendingReveals).distinct()
    )
}

internal data class SavedGame(
    val gameId: String,
    val seedLetters: String,
    val wheelLetters: String,
    val gridRows: List<String>,
    val words: Map<String, CrosswordWord>,
    val missingWords: MissingWordsState,
    val netProgress: SavedNetProgress = SavedNetProgress()
) {
    fun isValid(): Boolean {
        if (gameId.isBlank()) {
            return false
        }
        if (seedLetters.isEmpty() || seedLetters.any { it !in CROSSWORD_GENERATOR_ALPHABET }) {
            return false
        }
        if (wheelLetters.length != seedLetters.length ||
            wheelLetters.groupingBy { it }.eachCount() != seedLetters.groupingBy { it }.eachCount()
        ) {
            return false
        }
        if (gridRows.isEmpty() || gridRows.size > MAX_CROSSWORD_ROWS) {
            return false
        }
        val width = gridRows.first().length
        if (width < MIN_SAVED_ENTRY_COUNT || width > MAX_CROSSWORD_COLUMNS || gridRows.any { row ->
                row.length != width || row.any { cell ->
                    cell != CROSSWORD_EMPTY_CELL && cell.uppercaseChar() !in CROSSWORD_GENERATOR_ALPHABET
                }
            }
        ) {
            return false
        }
        if (words.isEmpty() || words.size > MAX_SAVED_WORDS || words.any { (key, word) ->
                key != word.word || word.word.isBlank() ||
                    word.word.any { it !in CROSSWORD_GENERATOR_ALPHABET } ||
                    word.positions.size != word.word.length || word.positions.any { position ->
                        position.row !in gridRows.indices || position.col !in gridRows.first().indices ||
                            gridRows[position.row][position.col] == CROSSWORD_EMPTY_CELL
                    }
            }
        ) {
            return false
        }
        if (missingWords.entries.size > MAX_SAVED_MISSING_WORDS ||
            missingWords.entries.keys.any { word ->
                word.isBlank() || word.any { it !in CROSSWORD_GENERATOR_ALPHABET }
            } ||
            missingWords.remainingCount != missingWords.entries.values.count { !it } ||
            (missingWords.lastGuessedWord != null &&
                missingWords.lastGuessedWord !in missingWords.entries)
        ) {
            return false
        }
        return netProgress.isValid()
    }
}

internal fun snapshotSavedGame(
    gameId: String,
    seedLetters: String,
    wheelLetters: List<Char>,
    grid: List<List<CrosswordCell>>,
    words: Map<String, CrosswordWord>,
    missingWords: MissingWordsState,
    netProgress: SavedNetProgress = SavedNetProgress()
): SavedGame? {
    if (seedLetters.isBlank() || grid.isEmpty()) {
        return null
    }
    val rows = grid.map { row ->
        buildString {
            row.forEach { cell ->
                val letter = cell.letter
                append(
                    when {
                        letter == null -> CROSSWORD_EMPTY_CELL
                        cell.isRevealed -> letter.uppercaseChar()
                        else -> letter.lowercaseChar()
                    }
                )
            }
        }
    }
    return SavedGame(gameId, seedLetters, wheelLetters.joinToString(""), rows, words, missingWords, netProgress)
        .takeIf { it.isValid() }
}

internal object SavedGameCodec {
    fun write(output: DataOutputStream, game: SavedGame) {
        require(game.isValid())
        output.writeInt(SAVED_GAME_MAGIC)
        output.writeInt(SAVED_GAME_VERSION)
        output.writeUTF(game.gameId)
        output.writeUTF(game.seedLetters)
        output.writeUTF(game.wheelLetters)
        output.writeInt(game.gridRows.size)
        game.gridRows.forEach(output::writeUTF)
        output.writeInt(game.words.size)
        game.words.values.forEach { word ->
            output.writeUTF(word.word)
            output.writeInt(word.positions.size)
            word.positions.forEach { position ->
                output.writeInt(position.row)
                output.writeInt(position.col)
            }
        }
        output.writeInt(game.missingWords.entries.size)
        game.missingWords.entries.forEach { (word, guessed) ->
            output.writeUTF(word)
            output.writeBoolean(guessed)
        }
        output.writeBoolean(game.missingWords.lastGuessedWord != null)
        game.missingWords.lastGuessedWord?.let(output::writeUTF)
        val netProgress = game.netProgress
        output.writeBoolean(netProgress.participated)
        output.writeInt(netProgress.confirmedVersion)
        output.writeInt(netProgress.solvedBy.size)
        netProgress.solvedBy.forEach { (word, playerId) ->
            output.writeUTF(word)
            output.writeUTF(playerId)
        }
        output.writeInt(netProgress.solvedWordOrder.size)
        netProgress.solvedWordOrder.forEach(output::writeUTF)
        output.writeInt(netProgress.pendingWords.size)
        netProgress.pendingWords.forEach(output::writeUTF)
        output.writeInt(netProgress.pendingReveals.size)
        netProgress.pendingReveals.forEach { position ->
            output.writeInt(position.row)
            output.writeInt(position.col)
        }
        output.writeInt(netProgress.players.size)
        netProgress.players.forEach { player ->
            output.writeUTF(player.playerId)
            output.writeUTF(player.playerName)
            output.writeUTF(player.playerColorId)
        }
    }

    fun read(input: DataInputStream): SavedGame? {
        if (input.readInt() != SAVED_GAME_MAGIC) {
            return null
        }
        val version = input.readInt()
        if (version != LEGACY_SAVED_GAME_VERSION &&
            version != GAME_ID_SAVED_GAME_VERSION &&
            version != SAVED_GAME_VERSION
        ) {
            return null
        }
        val gameId = if (version == LEGACY_SAVED_GAME_VERSION) {
            UUID.randomUUID().toString()
        } else {
            input.readUTF()
        }
        val seedLetters = input.readUTF()
        val wheelLetters = input.readUTF()
        val rowCount = input.readInt()
        if (rowCount !in MIN_SAVED_ENTRY_COUNT..MAX_CROSSWORD_ROWS) {
            return null
        }
        val rows = List(rowCount) { input.readUTF() }
        val wordCount = input.readInt()
        if (wordCount !in MIN_SAVED_ENTRY_COUNT..MAX_SAVED_WORDS) {
            return null
        }
        val words = buildMap {
            repeat(wordCount) {
                val word = input.readUTF()
                val positionCount = input.readInt()
                if (positionCount !in MIN_SAVED_ENTRY_COUNT..MAX_CROSSWORD_ROWS.coerceAtLeast(MAX_CROSSWORD_COLUMNS)) {
                    return null
                }
                val positions = List(positionCount) {
                    GridPosition(input.readInt(), input.readInt())
                }.toSet()
                put(word, CrosswordWord(word, positions))
            }
        }
        if (words.size != wordCount) {
            return null
        }
        val missingCount = input.readInt()
        if (missingCount !in 0..MAX_SAVED_MISSING_WORDS) {
            return null
        }
        val missingEntries = buildMap {
            repeat(missingCount) { put(input.readUTF(), input.readBoolean()) }
        }
        if (missingEntries.size != missingCount) {
            return null
        }
        val lastGuessedWord = if (input.readBoolean()) input.readUTF() else null
        val missingWords = MissingWordsState(
            entries = missingEntries,
            remainingCount = missingEntries.values.count { !it },
            lastGuessedWord = lastGuessedWord
        )
        val netProgress = if (version == SAVED_GAME_VERSION) {
            readNetProgress(input) ?: return null
        } else {
            SavedNetProgress()
        }
        return SavedGame(gameId, seedLetters, wheelLetters, rows, words, missingWords, netProgress)
            .takeIf { it.isValid() }
    }

    private fun readNetProgress(input: DataInputStream): SavedNetProgress? {
        val participated = input.readBoolean()
        val confirmedVersion = input.readInt()
        val solvedCount = input.readInt()
        if (solvedCount !in 0..MAX_SAVED_WORDS) return null
        val solvedBy = buildMap {
            repeat(solvedCount) { put(input.readUTF(), input.readUTF()) }
        }
        if (solvedBy.size != solvedCount) return null
        val orderCount = input.readInt()
        if (orderCount !in 0..MAX_SAVED_WORDS) return null
        val solvedWordOrder = List(orderCount) { input.readUTF() }
        val pendingWordCount = input.readInt()
        if (pendingWordCount !in 0..MAX_SAVED_WORDS) return null
        val pendingWords = List(pendingWordCount) { input.readUTF() }
        val pendingRevealCount = input.readInt()
        if (pendingRevealCount !in 0..MAX_SAVED_WORDS) return null
        val pendingReveals = List(pendingRevealCount) {
            GridPosition(input.readInt(), input.readInt())
        }
        val playerCount = input.readInt()
        if (playerCount !in 0..MAX_SAVED_NET_PLAYERS) return null
        val players = List(playerCount) {
            SavedNetPlayer(input.readUTF(), input.readUTF(), input.readUTF())
        }
        return SavedNetProgress(
            participated,
            confirmedVersion,
            solvedBy,
            solvedWordOrder,
            pendingWords,
            pendingReveals,
            players
        ).takeIf { it.isValid() }
    }
}

internal class SavedGameStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, SAVED_GAME_FILE_NAME))

    fun load(): SavedGame? = runCatching {
        if (file.baseFile.length() > MAX_SAVED_FILE_BYTES) {
            return null
        }
        DataInputStream(file.openRead().buffered()).use { input ->
            SavedGameCodec.read(input)
        }
    }.getOrNull()

    fun save(game: SavedGame): Boolean {
        if (!game.isValid()) {
            return false
        }
        val bytes = runCatching {
            ByteArrayOutputStream().also { buffer ->
                DataOutputStream(buffer).use { output -> SavedGameCodec.write(output, game) }
            }.toByteArray()
        }.getOrNull() ?: return false
        if (bytes.size > MAX_SAVED_FILE_BYTES) {
            return false
        }
        val output = runCatching { file.startWrite() }.getOrNull() ?: return false
        return try {
            output.write(bytes)
            file.finishWrite(output)
            true
        } catch (error: Exception) {
            file.failWrite(output)
            false
        }
    }
}
