package com.ustas.words

import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ustas.words.ui.theme.AccentOrange
import com.ustas.words.ui.theme.DeepGreen
import com.ustas.words.ui.theme.GoldHighlight
import com.ustas.words.ui.theme.GoldMid
import com.ustas.words.ui.theme.GoldShadow
import com.ustas.words.ui.theme.IconBase
import com.ustas.words.ui.theme.LightGreen
import com.ustas.words.ui.theme.MidGreen
import com.ustas.words.ui.theme.NetPlayLampOn
import com.ustas.words.ui.theme.NetPlayToggleOff
import com.ustas.words.ui.theme.NewWordHighlightAura
import com.ustas.words.ui.theme.NewWordHighlightBackground
import com.ustas.words.ui.theme.NewWordHighlightText
import com.ustas.words.ui.theme.TileColor
import com.ustas.words.ui.theme.TileHiddenColor
import com.ustas.words.ui.theme.TileText
import com.ustas.words.ui.theme.WheelBackground
import com.ustas.words.ui.theme.WheelLetter
import com.ustas.words.ui.theme.WordsTheme
import kotlin.math.floor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            WordsTheme {
                GameScreen()
            }
        }
    }
}

private const val PREFS_NAME = "words_settings"
private const val KEY_MUTE = "mute"
private const val KEY_MAX_LETTER_SET_SIZE = "max_letter_set_size"
private const val LEGACY_KEY_MAX_WORD_LENGTH = "max_word_length"
private const val KEY_CROSSWORD_SELECTION_MODE = "crossword_selection_mode"
private const val KEY_REVIEW_WORDS = "review_words"
private const val KEY_NET_PLAYER_NAME = "net_player_name"
private const val KEY_NET_PLAYER_COLOR = "net_player_color"
private const val KEY_NET_SERVER_IP = "net_server_ip"
private const val KEY_NET_SERVER_PORT = "net_server_port"
private const val DEFAULT_NET_PLAYER_NAME = ""
private const val NET_PLAYER_COLOR_WHITE = "white"
private const val NET_PLAYER_COLOR_YELLOW = "yellow"
private const val NET_PLAYER_COLOR_RED = "red"
private const val NET_PLAYER_COLOR_LIGHT_GREEN = "light_green"
private const val DEFAULT_NET_SERVER_IP = "199.99.9.9"
private const val DEFAULT_NET_SERVER_PORT = 9999
private const val MIN_NET_SERVER_PORT = 1
private const val MAX_NET_SERVER_PORT = 65535
private const val NET_PLAY_WEBSOCKET_SCHEME = "ws://"
private const val NET_PLAY_WEBSOCKET_PATH = "/ws"
private const val NET_PLAY_RETRY_DELAY_MS = 5_000L
private const val NET_PLAY_PING_INTERVAL_SECONDS = 20L
private const val NET_PLAY_NORMAL_CLOSE_CODE = 1000
private const val NET_PLAY_CLOSE_REASON = "client_disconnect"
private const val NET_PLAY_STATUS_BLINK_DURATION_MS = 650
private const val NET_PLAY_STATUS_ALPHA_DIM = 0.3f
private const val NET_PLAY_STATUS_ALPHA_FULL = 1f
private const val NET_PLAY_STATUS_OFF_ALPHA = 0.4f
private const val NET_PLAY_TOGGLE_ANIMATION_MS = 160
private const val NET_PLAY_CORNER_DIVISOR = 2f
private const val NET_PLAY_PADDING_MULTIPLIER = 2f
private const val NET_PLAY_DEFAULT_SCORE = 0
private const val NET_STATE_VERSION_INITIAL = 1
private const val NET_INVALID_INDEX = -1
private const val NET_MESSAGE_TYPE_JOIN = "join"
private const val NET_MESSAGE_TYPE_SNAPSHOT = "snapshot"
private const val NET_MESSAGE_TYPE_STATE_UPDATE = "stateUpdate"
private const val NET_MESSAGE_TYPE_NEW_GAME = "newGame"
private const val NET_MESSAGE_TYPE_SUBMIT_WORD = "submitWord"
private const val NET_MESSAGE_TYPE_ERROR = "error"
private const val NET_ERROR_CONFLICT = "conflict"
private const val NET_ROLE_HOST = "host"
private const val NET_ROLE_GUEST = "guest"
private const val NET_JSON_TYPE = "type"
private const val NET_JSON_PLAYER_ID = "playerId"
private const val NET_JSON_PLAYER_NAME = "playerName"
private const val NET_JSON_PLAYER_COLOR = "playerColor"
private const val NET_JSON_ROLE = "role"
private const val NET_JSON_SNAPSHOT = "snapshot"
private const val NET_JSON_PLAYERS = "players"
private const val NET_JSON_STATE_VERSION = "stateVersion"
private const val NET_JSON_BASE_VERSION = "baseVersion"
private const val NET_JSON_SEED_LETTERS = "seedLetters"
private const val NET_JSON_WHEEL_LETTERS = "wheelLetters"
private const val NET_JSON_GRID_ROWS = "gridRows"
private const val NET_JSON_REVEALED = "revealed"
private const val NET_JSON_WORDS = "words"
private const val NET_JSON_SOLVED_BY = "solvedBy"
private const val NET_JSON_WORD = "word"
private const val NET_JSON_POSITIONS = "positions"
private const val NET_JSON_ROW = "row"
private const val NET_JSON_COL = "col"
private const val NET_JSON_SETTINGS = "settings"
private const val NET_JSON_SELECTION_MODE = "selectionMode"
private const val NET_JSON_MAX_LETTER_SET_SIZE = "maxLetterSetSize"
private const val NET_JSON_MESSAGE = "message"
private const val MIN_SEED_LETTER_SET_SIZE = 6
private const val MAX_SEED_LETTER_SET_SIZE = 9
private const val DEFAULT_MAX_LETTER_SET_SIZE = MAX_SEED_LETTER_SET_SIZE
private const val MIN_PROBLEM_LOG_WORD_COUNT = 5
private const val PROBLEM_LOG_TYPE_WORD = "word"
private const val PROBLEM_LOG_TYPE_LOGIC = "logic"
private const val PROBLEM_LOG_ATTEMPT_LIMIT_COMMENT = "Crossword generation attempt limit exceeded."
private const val WHEEL_SIZE_RATIO = 0.8f
private val WHEEL_LETTER_SIZE = 48.dp
private const val WHEEL_LETTER_SIZE_RATIO = 0.1875f
private val WHEEL_MAX_SIZE = 320.dp
private val WHEEL_CONTAINER_EXTRA_HEIGHT = 24.dp
private val WHEEL_VERTICAL_OFFSET = WHEEL_LETTER_SIZE
private const val DIAMETER_TO_RADIUS_DIVISOR = 2f
private const val WHEEL_LETTER_RING_INSET_FACTOR = 0.7f
private const val NEW_GAME_DIAMETER_REDUCTION_FACTOR = 0f
private const val NEW_GAME_GRADIENT_CENTER_RATIO = 0.3f
private const val NEW_GAME_GRADIENT_RADIUS_RATIO = 0.85f
private const val NEW_GAME_CIRCLE_RADIUS_RATIO = 0.5f
private val NEW_GAME_BUTTON_ELEVATION = 10.dp
private val NEW_GAME_TEXT_SIZE = 18.sp
private val CROSSWORD_MAX_SIZE = WHEEL_MAX_SIZE
private val WHEEL_SELECTION_TEXT_SIZE = 26.sp
private val WHEEL_SELECTION_LETTER_SPACING = 4.sp
private const val WHEEL_LETTER_TEXT_RATIO = 0.8333333f
private val WHEEL_CORNER_BUTTON_SIZE = 56.dp
private val WHEEL_CORNER_BUTTON_PADDING = 4.dp
private const val WHEEL_HIT_RADIUS_FACTOR = 0.55f
private const val WHEEL_HIT_RADIUS_EXPANSION_FACTOR = 1.2f
private val MISSING_WORD_COUNT_TEXT_SIZE = 20.sp
private val MISSING_WORD_LABEL_TEXT_SIZE = 12.sp
private val MISSING_WORD_LABEL_SPACING = 4.dp
private const val MISSING_WORD_LABEL_ALPHA = 0.8f
private val ABOUT_DIALOG_LINE_SPACING = 8.dp
private const val NEW_WORD_HIGHLIGHT_DURATION_MS = 2_000
private const val NEW_WORD_HIGHLIGHT_NONE = 0f
private const val NEW_WORD_HIGHLIGHT_FULL = 1f
private const val NEW_WORD_HIGHLIGHT_AURA_ALPHA = 0.9f
private const val NEW_WORD_HIGHLIGHT_AURA_RADIUS_RATIO = 1.1f
private const val NEW_WORD_HIGHLIGHT_CENTER_RATIO = 0.5f
private const val HIGHLIGHT_TRIGGER_STEP = 1
private const val SELECTION_FADE_DURATION_MS = 200
private const val SELECTION_FADE_START = 0f
private const val SELECTION_FADE_END = 1f
private const val SELECTION_FADE_TARGET_RATIO = 0.5f
private const val REVIEW_WORDS_MIME_TYPE = "text/plain"
private const val REVIEW_WORDS_SUBJECT_DATE_PATTERN = "yyyy-MM-dd"
private const val REVIEW_WORDS_SUBJECT_SUFFIX = " words to review"
private const val REVIEW_WORDS_SEPARATOR = "\n"
private const val SOUND_POOL_MAX_STREAMS = 4
private const val SOUND_POOL_DEFAULT_PRIORITY = 1
private const val SOUND_POOL_NO_LOOP = 0
private const val SOUND_POOL_INVALID_SOUND_ID = 0
private const val SOUND_POOL_LOAD_SUCCESS = 0
private const val SOUND_POOL_BASE_RATE = 1.0f
private const val SOUND_POOL_MIN_RATE = 0.5f
private const val SOUND_POOL_MAX_RATE = 2.0f
private const val SOUND_POOL_TAP_VOLUME = 0.6f
private const val SOUND_POOL_BELL_VOLUME = SOUND_POOL_TAP_VOLUME
private const val SOUND_POOL_SIDE_WORD_VOLUME = SOUND_POOL_TAP_VOLUME
private const val SOUND_POOL_COMPLETED_VOLUME = SOUND_POOL_TAP_VOLUME
private const val CROSSWORD_MODE_RANDOM_WORD = "random_word"
private const val CROSSWORD_MODE_LOW_OVERLAP = "low_overlap"
private const val CROSSWORD_MODE_VOWEL_RICH_LETTERS = "vowel_rich_letters"
private const val DEFAULT_CROSSWORD_SELECTION_MODE_ID = CROSSWORD_MODE_RANDOM_WORD
private const val LOW_OVERLAP_MAX_SHARED_RATIO = 0.2f
private const val FULL_WEIGHT = 1f
private val SETTINGS_DIALOG_SPACING = 12.dp
private val SETTINGS_CONTROL_SPACING = 8.dp
private val PLAYER_COLOR_SWATCH_SIZE = 16.dp
private val NET_PLAY_FIELD_HORIZONTAL_PADDING = 12.dp
private val NET_PLAY_FIELD_VERTICAL_PADDING = 8.dp
private val NET_PLAY_TOGGLE_WIDTH = 38.dp
private val NET_PLAY_TOGGLE_HEIGHT = 26.dp
private val NET_PLAY_TOGGLE_PADDING = 3.dp
private val NET_PLAY_LAMP_SIZE = 10.dp
private val NET_PLAY_HEADER_SPACING = 6.dp
private val NET_PLAY_STATS_SPACING = 6.dp
private val NET_PLAY_STATS_HEIGHT = 22.dp
private val NET_PLAY_STATS_HORIZONTAL_PADDING = 10.dp
private val NET_PLAY_STATS_ITEM_SPACING = 6.dp
private val NET_PLAY_STATS_TEXT_SIZE = 14.sp
private val NET_PLAY_TOGGLE_ELEVATION = 6.dp
private val NET_PLAY_STATS_ELEVATION = 6.dp
private val NET_PLAY_TOGGLE_KNOB_SIZE = NET_PLAY_TOGGLE_HEIGHT -
    (NET_PLAY_TOGGLE_PADDING * NET_PLAY_PADDING_MULTIPLIER)
private const val PLAYER_ID_TEXT_SIZE_DIVISOR = 1.5f
private const val VOWELS = "AEIOU"
private const val CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ"
private const val MIN_RANDOM_VOWEL_COUNT = 2
private const val MAX_RANDOM_VOWEL_COUNT = 3
private const val COUNT_STEP = 1
private const val ALREADY_SOLVED_CONFIRM_REPEAT_DELAY_MS = 90L
private const val REVIEW_WORD_CONFIRM_TONE_HZ = 320.0
private const val REVIEW_WORD_CONFIRM_DURATION_MS = 60
private const val REVIEW_WORD_CONFIRM_VOLUME = 0.45f

private data class UserSettings(
    val muted: Boolean = false,
    val maxLetterSetSize: Int = DEFAULT_MAX_LETTER_SET_SIZE,
    val selectionMode: CrosswordSelectionMode = DEFAULT_CROSSWORD_SELECTION_MODE,
    val playerId: String = "",
    val playerName: String = DEFAULT_NET_PLAYER_NAME,
    val playerColor: NetPlayerColor = NetPlayerColor.White,
    val serverIp: String = DEFAULT_NET_SERVER_IP,
    val serverPort: Int = DEFAULT_NET_SERVER_PORT
)

private enum class SettingsTab(val labelResId: Int) {
    General(R.string.settings_tab_general),
    NetPlay(R.string.settings_tab_net_play)
}

private enum class NetPlayerColor(
    val id: String,
    val labelResId: Int,
    val swatch: Color
) {
    White(NET_PLAYER_COLOR_WHITE, R.string.settings_net_play_color_white, NewWordHighlightText),
    Yellow(NET_PLAYER_COLOR_YELLOW, R.string.settings_net_play_color_yellow, GoldMid),
    Red(NET_PLAYER_COLOR_RED, R.string.settings_net_play_color_red, NewWordHighlightBackground),
    LightGreenColor(NET_PLAYER_COLOR_LIGHT_GREEN, R.string.settings_net_play_color_light_green, LightGreen);

    companion object {
        fun fromId(id: String): NetPlayerColor {
            return values().firstOrNull { it.id == id } ?: White
        }
    }
}

private enum class CrosswordSelectionMode(
    val id: String,
    val labelResId: Int
) {
    RandomWord(CROSSWORD_MODE_RANDOM_WORD, R.string.crossword_mode_random_word),
    LowOverlapWord(CROSSWORD_MODE_LOW_OVERLAP, R.string.crossword_mode_low_overlap),
    VowelRichLetters(CROSSWORD_MODE_VOWEL_RICH_LETTERS, R.string.crossword_mode_vowel_rich_letters);

    companion object {
        fun fromId(id: String): CrosswordSelectionMode {
            return values().firstOrNull { it.id == id } ?: DEFAULT_CROSSWORD_SELECTION_MODE
        }
    }
}

private val DEFAULT_CROSSWORD_SELECTION_MODE = CrosswordSelectionMode.RandomWord

private enum class HammerMode {
    Off,
    Single,
    Caps
}

private enum class NetConnectionStatus {
    Off,
    Connecting,
    Connected,
    Disconnected
}

private enum class NetPlayRole(val id: String) {
    None(""),
    Host(NET_ROLE_HOST),
    Guest(NET_ROLE_GUEST);

    companion object {
        fun fromId(id: String): NetPlayRole {
            return values().firstOrNull { it.id == id } ?: None
        }
    }
}

private data class NetPlayerStat(
    val color: Color,
    val count: Int
)

private data class NetPlayerInfo(
    val playerId: String,
    val playerName: String,
    val playerColor: NetPlayerColor
)

private fun buildNetStats(
    players: List<NetPlayerInfo>,
    solvedBy: Map<String, String>
): List<NetPlayerStat> {
    if (players.isEmpty()) {
        return emptyList()
    }
    val counts = solvedBy.values.groupingBy { it }.eachCount()
    return players.map { player ->
        val count = counts[player.playerId] ?: NET_PLAY_DEFAULT_SCORE
        NetPlayerStat(color = player.playerColor.swatch, count = count)
    }
}

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val tonePlayer = remember { TonePlayer() }
    val letterTapSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_letter_tap, SOUND_POOL_TAP_VOLUME) }
    val bellSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_bell, SOUND_POOL_BELL_VOLUME) }
    val sideWordSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_side_word, SOUND_POOL_SIDE_WORD_VOLUME) }
    val completedSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_completed, SOUND_POOL_COMPLETED_VOLUME) }
    val soundEffects = remember(tonePlayer, letterTapSample, bellSample, sideWordSample, completedSample) {
        SoundEffects(tonePlayer, letterTapSample, bellSample, sideWordSample, completedSample)
    }
    val coroutineScope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(loadSettings(appContext)) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    DisposableEffect(tonePlayer, letterTapSample, bellSample, sideWordSample, completedSample) {
        onDispose {
            tonePlayer.dispose()
            letterTapSample.release()
            bellSample.release()
            sideWordSample.release()
            completedSample.release()
        }
    }
    soundEffects.muted = settings.muted
    var dictionary by remember { mutableStateOf(loadDictionaryWords(appContext)) }
    val dictionarySet = remember(dictionary) { dictionary.toHashSet() }
    val seedLengthRange = seedLetterLengthRange(settings.maxLetterSetSize)
    val eligibleWords = remember(dictionarySet, seedLengthRange) {
        dictionarySet.filter { it.length in seedLengthRange }
    }
    var dictionaryUpdateInProgress by remember { mutableStateOf(false) }
    var seedLetters by remember { mutableStateOf("") }
    var letters by remember { mutableStateOf(emptyList<Char>()) }
    var grid by remember { mutableStateOf(emptyList<List<CrosswordCell>>()) }
    var crosswordWords by remember { mutableStateOf(emptyMap<String, CrosswordWord>()) }
    var missingWordsState by remember { mutableStateOf(emptyMissingWordsState()) }
    val reviewWords = remember(appContext) {
        mutableStateListOf<String>().apply { addAll(loadReviewWords(appContext)) }
    }
    var highlightedPositions by remember { mutableStateOf(emptySet<GridPosition>()) }
    var highlightTrigger by remember { mutableStateOf(0) }
    val highlightFade = remember { Animatable(NEW_WORD_HIGHLIGHT_NONE) }
    var hammerMode by remember { mutableStateOf(HammerMode.Off) }
    var generationError by remember { mutableStateOf(false) }
    var netPlayEnabled by remember { mutableStateOf(false) }
    var netConnectionStatus by remember { mutableStateOf(NetConnectionStatus.Off) }
    var netRole by remember { mutableStateOf(NetPlayRole.None) }
    var netHostNeedsUpload by remember { mutableStateOf(false) }
    var netJoined by remember { mutableStateOf(false) }
    var netConfirmedVersion by remember { mutableStateOf(NET_STATE_VERSION_INITIAL) }
    var netPendingWords by remember { mutableStateOf(emptyList<String>()) }
    var netSubmissionInFlight by remember { mutableStateOf(false) }
    var netNeedsResend by remember { mutableStateOf(false) }
    var netSolvedBy by remember { mutableStateOf(emptyMap<String, String>()) }
    var netPlayers by remember { mutableStateOf(emptyList<NetPlayerInfo>()) }
    val netPlayEnabledState = rememberUpdatedState(netPlayEnabled)
    val netClient = remember {
        OkHttpClient.Builder()
            .pingInterval(NET_PLAY_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    val applyNetSnapshot: (NetSnapshot) -> Pair<List<List<CrosswordCell>>, Map<String, CrosswordWord>> =
        { snapshot ->
            val baseGrid = buildCrosswordGridFromRows(snapshot.gridRows)
            val revealedGrid = applyRevealedPositions(baseGrid, snapshot.revealedPositions)
            val wordMap = snapshot.words.associateBy { it.word }
            seedLetters = snapshot.seedLetters
            letters = if (snapshot.wheelLetters.isEmpty()) {
                generateLetterWheel(snapshot.seedLetters)
            } else {
                snapshot.wheelLetters
            }
            grid = revealedGrid
            crosswordWords = wordMap
            missingWordsState = buildMissingWordsState(snapshot.seedLetters, dictionary, wordMap)
            highlightedPositions = emptySet()
            hammerMode = HammerMode.Off
            generationError = false
            netConfirmedVersion = snapshot.stateVersion
            netSolvedBy = snapshot.solvedBy
            revealedGrid to wordMap
        }
    fun rebasePendingWordsIfNeeded(
        baseGrid: List<List<CrosswordCell>>,
        wordMap: Map<String, CrosswordWord>,
        allowResend: Boolean,
        playDiscardFeedback: Boolean
    ) {
        val pending = netPendingWords
        if (pending.isEmpty()) {
            return
        }
        val rebaseResult = rebasePendingWords(
            baseGrid = baseGrid,
            crosswordWords = wordMap,
            pendingWords = pending,
            baseSolvedBy = netSolvedBy,
            playerId = settings.playerId
        )
        if (rebaseResult.grid != baseGrid) {
            grid = rebaseResult.grid
        }
        netSolvedBy = rebaseResult.solvedBy
        netPendingWords = rebaseResult.pendingWords
        netNeedsResend = allowResend && rebaseResult.pendingWords.isNotEmpty()
        if (playDiscardFeedback && rebaseResult.discardedWords.isNotEmpty()) {
            soundEffects.alreadySolvedConfirm()
        }
    }
    val handleNetMessage = rememberUpdatedState<(NetMessage) -> Unit> { message ->
        if (!netPlayEnabled) {
            return@rememberUpdatedState
        }
        when (message) {
            is NetMessage.Snapshot -> {
                netRole = message.role
                netSubmissionInFlight = false
                if (message.players.isNotEmpty()) {
                    netPlayers = message.players
                }
                if (message.snapshot != null) {
                    val (baseGrid, wordMap) = applyNetSnapshot(message.snapshot)
                    netHostNeedsUpload = false
                    if (message.snapshot.stateVersion == NET_STATE_VERSION_INITIAL) {
                        netPendingWords = emptyList()
                    }
                    rebasePendingWordsIfNeeded(
                        baseGrid = baseGrid,
                        wordMap = wordMap,
                        allowResend = true,
                        playDiscardFeedback = false
                    )
                } else {
                    netHostNeedsUpload = message.role == NetPlayRole.Host
                }
            }
            is NetMessage.StateUpdate -> {
                netSubmissionInFlight = false
                if (message.players.isNotEmpty()) {
                    netPlayers = message.players
                }
                val (baseGrid, wordMap) = applyNetSnapshot(message.snapshot)
                if (message.snapshot.stateVersion == NET_STATE_VERSION_INITIAL) {
                    netPendingWords = emptyList()
                }
                rebasePendingWordsIfNeeded(
                    baseGrid = baseGrid,
                    wordMap = wordMap,
                    allowResend = true,
                    playDiscardFeedback = false
                )
            }
            is NetMessage.Conflict -> {
                netSubmissionInFlight = false
                if (message.players.isNotEmpty()) {
                    netPlayers = message.players
                }
                val (baseGrid, wordMap) = applyNetSnapshot(message.snapshot)
                if (message.snapshot.stateVersion == NET_STATE_VERSION_INITIAL) {
                    netPendingWords = emptyList()
                }
                rebasePendingWordsIfNeeded(
                    baseGrid = baseGrid,
                    wordMap = wordMap,
                    allowResend = true,
                    playDiscardFeedback = true
                )
            }
            is NetMessage.Error -> {
                if (message.players.isNotEmpty()) {
                    netPlayers = message.players
                }
            }
        }
    }
    val netConnection = remember(netClient) {
        NetPlayConnection(
            client = netClient,
            onStatusChange = { status ->
                if (netPlayEnabledState.value) {
                    netConnectionStatus = status
                }
            },
            onMessage = { rawMessage ->
                val parsed = parseNetMessage(rawMessage) ?: return@NetPlayConnection
                handleNetMessage.value(parsed)
            }
        )
    }
    DisposableEffect(netConnection) {
        onDispose {
            netConnection.shutdown()
        }
    }
    LaunchedEffect(
        netNeedsResend,
        netPendingWords,
        netSubmissionInFlight,
        netPlayEnabled,
        netConnectionStatus,
        netConfirmedVersion,
        seedLetters,
        letters,
        grid,
        crosswordWords,
        settings
    ) {
        if (!netNeedsResend) {
            return@LaunchedEffect
        }
        if (netSubmissionInFlight) {
            return@LaunchedEffect
        }
        if (!netPlayEnabled || netConnectionStatus != NetConnectionStatus.Connected) {
            return@LaunchedEffect
        }
        if (netPendingWords.isEmpty()) {
            netNeedsResend = false
            return@LaunchedEffect
        }
        val snapshot = buildNetSnapshotFromState(
            seedLetters = seedLetters,
            wheelLetters = letters,
            grid = grid,
            crosswordWords = crosswordWords,
            settings = settings,
            solvedBy = netSolvedBy,
            stateVersion = netConfirmedVersion
        )
        if (snapshot == null) {
            netNeedsResend = false
            return@LaunchedEffect
        }
        if (netConnection.send(buildNetSubmitWordMessage(snapshot, netConfirmedVersion))) {
            netSubmissionInFlight = true
            netNeedsResend = false
        }
    }
    val netServerUrl = remember(settings.serverIp, settings.serverPort) {
        buildNetServerUrl(settings.serverIp, settings.serverPort)
    }
    val netStats = remember(netConnectionStatus, netPlayers, netSolvedBy) {
        if (netConnectionStatus == NetConnectionStatus.Connected) {
            buildNetStats(netPlayers, netSolvedBy)
        } else {
            emptyList()
        }
    }

    fun showDictionaryUpdateToast(result: DictionaryUpdateResult) {
        val messageRes = when (result) {
            is DictionaryUpdateResult.Updated -> R.string.dictionary_update_success
            DictionaryUpdateResult.NotModified -> R.string.dictionary_update_up_to_date
            is DictionaryUpdateResult.Failed -> R.string.dictionary_update_failed
            DictionaryUpdateResult.Skipped -> null
        }
        if (messageRes != null) {
            Toast.makeText(appContext, appContext.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchDictionaryUpdate(reason: DictionaryUpdateReason, showToast: Boolean) {
        if (dictionaryUpdateInProgress) {
            return
        }
        dictionaryUpdateInProgress = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    updateDictionaryIfNeeded(appContext, reason)
                }
                if (result is DictionaryUpdateResult.Updated) {
                    dictionary = result.words
                }
                if (showToast) {
                    showDictionaryUpdateToast(result)
                }
            } finally {
                dictionaryUpdateInProgress = false
            }
        }
    }

    fun sendWordsToReview() {
        val snapshot = reviewWords.toList()
        shareWordsToReview(context, snapshot)
        reviewWords.clear()
        saveReviewWords(appContext, reviewWords)
    }

    fun startNewGame() {
        if (netPlayEnabled && netRole == NetPlayRole.Guest) {
            return
        }
        val previousSeedLetters = seedLetters
        highlightedPositions = emptySet()
        val seedLetterCandidates = buildSeedLetterCandidates(
            eligibleWords = eligibleWords,
            previousSeedLetters = previousSeedLetters,
            selectionMode = settings.selectionMode,
            maxLetterSetSize = settings.maxLetterSetSize
        )
        val result = generateCrosswordWithQuality(
            seedLetterCandidates = seedLetterCandidates,
            dictionary = dictionary,
            isValidLayout = { seedLetters, layout ->
                if (settings.selectionMode != CrosswordSelectionMode.VowelRichLetters) {
                    return@generateCrosswordWithQuality true
                }
                areAllSeedLettersUsed(seedLetters, layout)
            }
        )
        val rejectedSeedLetters = when (result) {
            is CrosswordGenerationResult.Success -> result.rejectedSeedLetters
            is CrosswordGenerationResult.Failure -> result.rejectedSeedLetters
        }
        logRejectedSeedLetters(appContext, rejectedSeedLetters)
        when (result) {
            is CrosswordGenerationResult.Success -> {
                seedLetters = result.seedLetters
                letters = generateLetterWheel(result.seedLetters).shuffled()
                grid = result.layout.grid
                crosswordWords = result.layout.words
                missingWordsState = buildMissingWordsState(result.seedLetters, dictionary, result.layout.words)
                hammerMode = HammerMode.Off
                generationError = false
                netSolvedBy = emptyMap()
                if (netPlayEnabled && netRole == NetPlayRole.Host) {
                    netHostNeedsUpload = true
                }
            }
            is CrosswordGenerationResult.Failure -> {
                generationError = true
                appendProblemLogEntry(
                    appContext,
                    PROBLEM_LOG_TYPE_LOGIC,
                    PROBLEM_LOG_ATTEMPT_LIMIT_COMMENT
                )
                if (grid.isEmpty()) {
                    seedLetters = ""
                    letters = emptyList()
                    crosswordWords = emptyMap()
                    missingWordsState = emptyMissingWordsState()
                }
            }
        }
    }

    LaunchedEffect(netPlayEnabled, netServerUrl) {
        if (netPlayEnabled) {
            netRole = NetPlayRole.None
            netHostNeedsUpload = false
            netJoined = false
            netConfirmedVersion = NET_STATE_VERSION_INITIAL
            netPendingWords = emptyList()
            netSubmissionInFlight = false
            netNeedsResend = false
            netSolvedBy = emptyMap()
            netPlayers = emptyList()
            netConnectionStatus = NetConnectionStatus.Connecting
            netConnection.connect(netServerUrl)
        } else {
            netConnection.disconnect()
            netConnectionStatus = NetConnectionStatus.Off
            netRole = NetPlayRole.None
            netHostNeedsUpload = false
            netJoined = false
            netConfirmedVersion = NET_STATE_VERSION_INITIAL
            netPendingWords = emptyList()
            netSubmissionInFlight = false
            netNeedsResend = false
            netSolvedBy = emptyMap()
            netPlayers = emptyList()
        }
    }

    LaunchedEffect(netPlayEnabled, netConnectionStatus, netServerUrl) {
        if (netPlayEnabled && netConnectionStatus == NetConnectionStatus.Disconnected) {
            delay(NET_PLAY_RETRY_DELAY_MS)
            if (netPlayEnabled && netConnectionStatus == NetConnectionStatus.Disconnected) {
                netConnectionStatus = NetConnectionStatus.Connecting
                netConnection.connect(netServerUrl)
            }
        }
    }

    LaunchedEffect(netConnectionStatus) {
        if (netConnectionStatus != NetConnectionStatus.Connected) {
            netJoined = false
            netSubmissionInFlight = false
        }
    }

    LaunchedEffect(
        netPlayEnabled,
        netConnectionStatus,
        settings.playerId,
        settings.playerName,
        settings.playerColor,
        netJoined
    ) {
        if (netPlayEnabled && netConnectionStatus == NetConnectionStatus.Connected && !netJoined) {
            if (netConnection.send(buildNetJoinMessage(settings))) {
                netJoined = true
            }
        }
    }

    LaunchedEffect(
        netPlayEnabled,
        netConnectionStatus,
        netRole,
        netHostNeedsUpload,
        seedLetters,
        letters,
        grid,
        crosswordWords,
        settings
    ) {
        if (
            netPlayEnabled &&
            netConnectionStatus == NetConnectionStatus.Connected &&
            netRole == NetPlayRole.Host &&
            netHostNeedsUpload
        ) {
            val snapshot = buildNetSnapshotFromState(
                seedLetters = seedLetters,
                wheelLetters = letters,
                grid = grid,
                crosswordWords = crosswordWords,
                settings = settings,
                solvedBy = netSolvedBy,
                stateVersion = NET_STATE_VERSION_INITIAL
            )
            if (snapshot != null && netConnection.send(buildNetNewGameMessage(snapshot))) {
                netHostNeedsUpload = false
            }
        }
    }

    LaunchedEffect(Unit) {
        launchDictionaryUpdate(DictionaryUpdateReason.Scheduled, showToast = false)
    }

    LaunchedEffect(seedLetters) {
        logSmallWordListIfNeeded(appContext, seedLetters, dictionary)
    }

    LaunchedEffect(eligibleWords) {
        if (grid.isEmpty() || !eligibleWords.contains(seedLetters)) {
            startNewGame()
        }
    }
    LaunchedEffect(highlightedPositions, highlightTrigger) {
        if (highlightedPositions.isEmpty()) {
            highlightFade.snapTo(NEW_WORD_HIGHLIGHT_NONE)
            return@LaunchedEffect
        }
        highlightFade.snapTo(NEW_WORD_HIGHLIGHT_FULL)
        highlightFade.animateTo(
            targetValue = NEW_WORD_HIGHLIGHT_NONE,
            animationSpec = tween(
                durationMillis = NEW_WORD_HIGHLIGHT_DURATION_MS,
                easing = LinearEasing
            )
        )
    }
    val isSolved = grid.isNotEmpty() && grid.all { row -> row.all { cell -> !cell.isActive || cell.isRevealed } }
    val hammerActive = hammerMode != HammerMode.Off
    val canStartNewGame = !netPlayEnabled || netRole == NetPlayRole.Host
    val showNewGameButton = (isSolved || generationError) && canStartNewGame

    LaunchedEffect(isSolved) {
        if (isSolved) {
            soundEffects.crosswordCompleted()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AbstractBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            TopBar(
                netPlayEnabled = netPlayEnabled,
                netConnectionStatus = netConnectionStatus,
                netStats = netStats,
                onNetPlayToggle = { enabled ->
                    netPlayEnabled = enabled
                },
                onSettings = { showSettings = true },
                onNewGame = {
                    startNewGame()
                },
                onUpdateDictionary = {
                    launchDictionaryUpdate(DictionaryUpdateReason.Manual, showToast = true)
                },
                dictionaryUpdateInProgress = dictionaryUpdateInProgress,
                onSendWordsToReview = { sendWordsToReview() },
                onShareProblems = { shareProblemLog(context) },
                onResetProblems = { resetProblemLog(appContext) },
                onExit = { (context as? ComponentActivity)?.finishAffinity() },
                onAbout = { showAbout = true }
            )
            if (generationError) {
                Text(
                    text = stringResource(R.string.crossword_generation_failed),
                    color = AccentOrange,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = NEW_GAME_TEXT_SIZE,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CrosswordSection(
                grid = grid,
                highlightedPositions = highlightedPositions,
                highlightStrength = highlightFade.value,
                hammerActive = hammerActive,
                isSolved = isSolved,
                onCellTap = { rowIndex, colIndex ->
                    val cell = grid[rowIndex][colIndex]
                    if (hammerMode != HammerMode.Off && cell.isActive && !cell.isRevealed) {
                        grid = grid.mapIndexed { row, rowCells ->
                            rowCells.mapIndexed { col, item ->
                                if (row == rowIndex && col == colIndex) {
                                    item.copy(isRevealed = true)
                                } else {
                                    item
                                }
                            }
                        }
                        if (hammerMode == HammerMode.Single) {
                            hammerMode = HammerMode.Off
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            LetterWheelSection(
                letters = letters,
                hammerActive = hammerActive,
                showNewGameButton = showNewGameButton,
                hasMissingWords = missingWordsState.entries.isNotEmpty(),
                missingWordsCount = missingWordsState.remainingCount,
                lastMissingWord = missingWordsState.lastGuessedWord,
                onShuffle = { letters = letters.shuffled() },
                onNewGame = {
                    startNewGame()
                },
                onHammerTap = {
                    hammerMode = when (hammerMode) {
                        HammerMode.Off -> HammerMode.Single
                        HammerMode.Single -> HammerMode.Off
                        HammerMode.Caps -> HammerMode.Single
                    }
                },
                onHammerLongPress = {
                    if (hammerMode != HammerMode.Caps) {
                        hammerMode = HammerMode.Caps
                    }
                },
                onSelectionStart = { hammerMode = HammerMode.Off },
                onWordTapped = { tappedWord ->
                    reviewWords.add(tappedWord.lowercase(Locale.US))
                    saveReviewWords(appContext, reviewWords)
                    soundEffects.reviewWordConfirm()
                },
                onWordSelected = { selectedWord ->
                    val normalizedWord = selectedWord.uppercase()
                    val match = crosswordWords[normalizedWord]
                    val (updatedGrid, result) = applySelectedWord(selectedWord, crosswordWords, grid)
                    grid = updatedGrid
                    if ((result == WordResult.Success || result == WordResult.AlreadySolved) && match != null) {
                        highlightedPositions = match.positions
                        highlightTrigger += HIGHLIGHT_TRIGGER_STEP
                    }
                    if (result == WordResult.Success && netPlayEnabled) {
                        if (settings.playerId.isNotBlank()) {
                            netSolvedBy = netSolvedBy + (normalizedWord to settings.playerId)
                        }
                        if (!netPendingWords.contains(normalizedWord)) {
                            netPendingWords = netPendingWords + normalizedWord
                        }
                        if (!netSubmissionInFlight && netConnectionStatus == NetConnectionStatus.Connected) {
                            val snapshot = buildNetSnapshotFromState(
                                seedLetters = seedLetters,
                                wheelLetters = letters,
                                grid = grid,
                                crosswordWords = crosswordWords,
                                settings = settings,
                                solvedBy = netSolvedBy,
                                stateVersion = netConfirmedVersion
                            )
                            if (snapshot != null && netConnection.send(
                                    buildNetSubmitWordMessage(snapshot, netConfirmedVersion)
                                )
                            ) {
                                netSubmissionInFlight = true
                                netNeedsResend = false
                            } else {
                                netNeedsResend = true
                            }
                        } else {
                            netNeedsResend = true
                        }
                    }
                    if (result != WordResult.NotFound) {
                        result
                    } else {
                        val missingResult = applyMissingWordGuess(selectedWord, missingWordsState)
                        missingWordsState = missingResult.state
                        when (missingResult.match) {
                            MissingWordMatch.NewlyGuessed -> WordResult.MissingWordFound
                            MissingWordMatch.AlreadyGuessed -> WordResult.MissingWordAlreadyFound
                            MissingWordMatch.None -> WordResult.NotFound
                        }
                    }
                },
                soundEffects = soundEffects,
                modifier = Modifier.weight(0.9f)
            )
        }
        if (showSettings) {
            SettingsDialog(
                current = settings,
                onSave = { updated ->
                    val normalized = updated.copy(
                        maxLetterSetSize = updated.maxLetterSetSize
                            .coerceIn(MIN_SEED_LETTER_SET_SIZE, MAX_SEED_LETTER_SET_SIZE)
                    )
                    settings = normalized
                    saveSettings(appContext, normalized)
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }
        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
    }
}

@Composable
private fun TopBar(
    netPlayEnabled: Boolean,
    netConnectionStatus: NetConnectionStatus,
    netStats: List<NetPlayerStat>,
    onNetPlayToggle: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onNewGame: () -> Unit,
    onUpdateDictionary: () -> Unit,
    dictionaryUpdateInProgress: Boolean,
    onSendWordsToReview: () -> Unit,
    onShareProblems: () -> Unit,
    onResetProblems: () -> Unit,
    onExit: () -> Unit,
    onAbout: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetPlayHeader(
            enabled = netPlayEnabled,
            status = netConnectionStatus,
            stats = netStats,
            onToggle = onNetPlayToggle
        )
        Spacer(modifier = Modifier.weight(FULL_WEIGHT))
        CircleIconButton(
            icon = Icons.Filled.Autorenew,
            contentDescription = stringResource(R.string.new_game),
            onClick = {
                menuExpanded = false
                onNewGame()
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            CircleIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.menu),
                onClick = { menuExpanded = true }
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings)) },
                    onClick = {
                        menuExpanded = false
                        onSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_update_dictionary)) },
                    enabled = !dictionaryUpdateInProgress,
                    onClick = {
                        menuExpanded = false
                        onUpdateDictionary()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_send_words_review)) },
                    onClick = {
                        menuExpanded = false
                        onSendWordsToReview()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_share_problems)) },
                    onClick = {
                        menuExpanded = false
                        onShareProblems()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_reset_problems)) },
                    onClick = {
                        menuExpanded = false
                        onResetProblems()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_exit)) },
                    onClick = {
                        menuExpanded = false
                        onExit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.menu_about)) },
                    onClick = {
                        menuExpanded = false
                        onAbout()
                    }
                )
            }
        }
    }
}

@Composable
private fun NetPlayHeader(
    enabled: Boolean,
    status: NetConnectionStatus,
    stats: List<NetPlayerStat>,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetPlayToggle(enabled = enabled, onToggle = onToggle)
        Spacer(modifier = Modifier.width(NET_PLAY_HEADER_SPACING))
        NetPlayStatusLamp(enabled = enabled, status = status)
        if (enabled && status == NetConnectionStatus.Connected && stats.isNotEmpty()) {
            Spacer(modifier = Modifier.width(NET_PLAY_STATS_SPACING))
            NetPlayStatsPill(stats = stats)
        }
    }
}

@Composable
private fun NetPlayToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val knobOffset by animateDpAsState(
        targetValue = if (enabled) {
            NET_PLAY_TOGGLE_WIDTH - NET_PLAY_TOGGLE_KNOB_SIZE -
                (NET_PLAY_TOGGLE_PADDING * NET_PLAY_PADDING_MULTIPLIER)
        } else {
            0.dp
        },
        animationSpec = tween(durationMillis = NET_PLAY_TOGGLE_ANIMATION_MS, easing = LinearEasing),
        label = "netPlayToggleOffset"
    )
    val trackColor = if (enabled) LightGreen else NetPlayToggleOff
    val toggleDescription = stringResource(
        if (enabled) R.string.net_play_toggle_on else R.string.net_play_toggle_off
    )
    Surface(
        color = trackColor,
        shape = RoundedCornerShape(NET_PLAY_TOGGLE_HEIGHT / NET_PLAY_CORNER_DIVISOR),
        shadowElevation = NET_PLAY_TOGGLE_ELEVATION,
        modifier = modifier
            .width(NET_PLAY_TOGGLE_WIDTH)
            .height(NET_PLAY_TOGGLE_HEIGHT)
            .semantics { contentDescription = toggleDescription }
            .clickable(role = Role.Switch) { onToggle(!enabled) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(NET_PLAY_TOGGLE_PADDING)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset)
                    .size(NET_PLAY_TOGGLE_KNOB_SIZE)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun NetPlayStatusLamp(
    enabled: Boolean,
    status: NetConnectionStatus,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "netPlayStatusBlink")
    val blinkAlpha by transition.animateFloat(
        initialValue = NET_PLAY_STATUS_ALPHA_DIM,
        targetValue = NET_PLAY_STATUS_ALPHA_FULL,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = NET_PLAY_STATUS_BLINK_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "netPlayStatusAlpha"
    )
    val lampColor = when {
        !enabled -> WheelBackground.copy(alpha = NET_PLAY_STATUS_OFF_ALPHA)
        status == NetConnectionStatus.Connected -> NetPlayLampOn
        else -> NewWordHighlightBackground.copy(alpha = blinkAlpha)
    }
    val statusDescription = stringResource(netPlayStatusLabelResId(enabled, status))
    Box(
        modifier = modifier
            .size(NET_PLAY_LAMP_SIZE)
            .clip(CircleShape)
            .background(lampColor)
            .semantics { contentDescription = statusDescription }
    )
}

@Composable
private fun NetPlayStatsPill(
    stats: List<NetPlayerStat>,
    modifier: Modifier = Modifier
) {
    if (stats.isEmpty()) {
        return
    }
    Surface(
        color = IconBase,
        shape = RoundedCornerShape(NET_PLAY_STATS_HEIGHT / NET_PLAY_CORNER_DIVISOR),
        shadowElevation = NET_PLAY_STATS_ELEVATION,
        modifier = modifier.height(NET_PLAY_STATS_HEIGHT)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NET_PLAY_STATS_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stats.forEachIndexed { index, stat ->
                if (index >= COUNT_STEP) {
                    Spacer(modifier = Modifier.width(NET_PLAY_STATS_ITEM_SPACING))
                }
                Text(
                    text = stat.count.toString(),
                    color = stat.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = NET_PLAY_STATS_TEXT_SIZE
                )
            }
        }
    }
}

private fun netPlayStatusLabelResId(
    enabled: Boolean,
    status: NetConnectionStatus
): Int {
    return when {
        !enabled -> R.string.net_play_status_off
        status == NetConnectionStatus.Connected -> R.string.net_play_status_connected
        status == NetConnectionStatus.Connecting -> R.string.net_play_status_connecting
        else -> R.string.net_play_status_disconnected
    }
}

@Composable
private fun SettingsDialog(
    current: UserSettings,
    onSave: (UserSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var muted by remember(current.muted) { mutableStateOf(current.muted) }
    var maxLetterSetSize by remember(current.maxLetterSetSize) {
        mutableStateOf(current.maxLetterSetSize.toFloat())
    }
    var selectionMode by remember(current.selectionMode) { mutableStateOf(current.selectionMode) }
    var selectionExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SettingsTab.General) }
    var playerName by remember(current.playerName) { mutableStateOf(current.playerName) }
    var playerColor by remember(current.playerColor) { mutableStateOf(current.playerColor) }
    var serverIp by remember(current.serverIp) { mutableStateOf(current.serverIp) }
    var serverPortText by remember(current.serverPort) { mutableStateOf(current.serverPort.toString()) }
    var colorExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedPlayerName = playerName.trim()
                    val trimmedServerIp = serverIp.trim()
                    val fallbackServerIp = if (current.serverIp.isBlank()) DEFAULT_NET_SERVER_IP else current.serverIp
                    val normalizedServerIp = if (trimmedServerIp.isBlank()) fallbackServerIp else trimmedServerIp
                    val parsedPort = serverPortText.trim().toIntOrNull()
                    val normalizedPort = (parsedPort ?: current.serverPort)
                        .coerceIn(MIN_NET_SERVER_PORT, MAX_NET_SERVER_PORT)
                    onSave(
                        UserSettings(
                            muted = muted,
                            maxLetterSetSize = maxLetterSetSize.roundToInt(),
                            selectionMode = selectionMode,
                            playerId = current.playerId,
                            playerName = trimmedPlayerName,
                            playerColor = playerColor,
                            serverIp = normalizedServerIp,
                            serverPort = normalizedPort
                        )
                    )
                }
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        title = { Text(text = stringResource(R.string.settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(SETTINGS_DIALOG_SPACING)
            ) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    for (tab in SettingsTab.values()) {
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(text = stringResource(tab.labelResId)) }
                        )
                    }
                }
                when (selectedTab) {
                    SettingsTab.General -> {
                        Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_DIALOG_SPACING)) {
                            Column {
                                Text(text = stringResource(R.string.settings_next_crossword))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { selectionExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(selectionMode.labelResId),
                                                modifier = Modifier.weight(FULL_WEIGHT),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = stringResource(R.string.dropdown_open)
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = selectionExpanded,
                                        onDismissRequest = { selectionExpanded = false }
                                    ) {
                                        for (mode in CrosswordSelectionMode.values()) {
                                            DropdownMenuItem(
                                                text = { Text(text = stringResource(mode.labelResId)) },
                                                onClick = {
                                                    selectionMode = mode
                                                    selectionExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = muted, onCheckedChange = { muted = it })
                                Spacer(modifier = Modifier.width(SETTINGS_CONTROL_SPACING))
                                Text(text = "Mute sounds")
                            }
                            Column {
                                Text(text = "Max letter set size: ${maxLetterSetSize.roundToInt()}")
                                Slider(
                                    value = maxLetterSetSize,
                                    onValueChange = { maxLetterSetSize = it.roundToInt().toFloat() },
                                    valueRange = MIN_SEED_LETTER_SET_SIZE.toFloat()..MAX_SEED_LETTER_SET_SIZE.toFloat(),
                                    steps = (MAX_SEED_LETTER_SET_SIZE - MIN_SEED_LETTER_SET_SIZE - COUNT_STEP)
                                        .coerceAtLeast(0)
                                )
                            }
                        }
                    }
                    SettingsTab.NetPlay -> {
                        Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_DIALOG_SPACING)) {
                            Text(
                                text = stringResource(R.string.settings_net_play_player),
                                fontWeight = FontWeight.Bold
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_CONTROL_SPACING)) {
                                NetPlayLabeledTextField(
                                    labelResId = R.string.settings_net_play_player_name,
                                    value = playerName,
                                    onValueChange = { playerName = it }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.settings_net_play_player_color)}:",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(SETTINGS_CONTROL_SPACING))
                                    Box(modifier = Modifier.weight(FULL_WEIGHT)) {
                                        NetPlayFieldSurface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { colorExpanded = true }
                                        ) {
                                            NetPlayColorSwatch(color = playerColor.swatch)
                                            Spacer(modifier = Modifier.width(SETTINGS_CONTROL_SPACING))
                                            Text(
                                                text = stringResource(playerColor.labelResId),
                                                modifier = Modifier.weight(FULL_WEIGHT),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = stringResource(R.string.dropdown_open)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = colorExpanded,
                                            onDismissRequest = { colorExpanded = false }
                                        ) {
                                            for (color in NetPlayerColor.values()) {
                                                DropdownMenuItem(
                                                    text = { NetPlayColorRow(color = color) },
                                                    onClick = {
                                                        playerColor = color
                                                        colorExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_CONTROL_SPACING)) {
                                Text(
                                    text = stringResource(R.string.settings_net_play_server),
                                    fontWeight = FontWeight.Bold
                                )
                                NetPlayLabeledTextField(
                                    labelResId = R.string.settings_net_play_server_ip,
                                    value = serverIp,
                                    onValueChange = { serverIp = it }
                                )
                                NetPlayLabeledTextField(
                                    labelResId = R.string.settings_net_play_server_port,
                                    value = serverPortText,
                                    onValueChange = { serverPortText = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(SETTINGS_CONTROL_SPACING)) {
                                Text(text = stringResource(R.string.settings_net_play_player_id))
                                val baseStyle = MaterialTheme.typography.bodyMedium
                                Text(
                                    text = current.playerId,
                                    style = baseStyle.copy(
                                        fontSize = baseStyle.fontSize / PLAYER_ID_TEXT_SIZE_DIVISOR
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NetPlayLabeledTextField(
    labelResId: Int,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyMedium
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${stringResource(labelResId)}:",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(SETTINGS_CONTROL_SPACING))
        NetPlayFieldSurface(modifier = Modifier.weight(FULL_WEIGHT)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                textStyle = textStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NetPlayFieldSurface(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = NET_PLAY_FIELD_HORIZONTAL_PADDING,
                vertical = NET_PLAY_FIELD_VERTICAL_PADDING
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun NetPlayColorRow(
    color: NetPlayerColor,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetPlayColorSwatch(color = color.swatch)
        Spacer(modifier = Modifier.width(SETTINGS_CONTROL_SPACING))
        Text(text = stringResource(color.labelResId))
    }
}

@Composable
private fun NetPlayColorSwatch(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(PLAYER_COLOR_SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.about_close))
            }
        },
        title = { Text(text = stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ABOUT_DIALOG_LINE_SPACING)) {
                Text(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Text(text = stringResource(R.string.about_date, BuildConfig.BUILD_TIME_UTC))
                Text(text = stringResource(R.string.about_open_source))
                Text(text = stringResource(R.string.about_copyright))
            }
        }
    )
}

@Composable
private fun CrosswordSection(
    grid: List<List<CrosswordCell>>,
    highlightedPositions: Set<GridPosition>,
    highlightStrength: Float,
    hammerActive: Boolean,
    isSolved: Boolean,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CrosswordGrid(
            grid = grid,
            highlightedPositions = highlightedPositions,
            highlightStrength = highlightStrength,
            onCellTap = onCellTap,
            modifier = Modifier.fillMaxWidth()
        )
        if (hammerActive && !isSolved) {
            Text(
                text = stringResource(R.string.reveal_hint),
                color = TileText.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun CrosswordGrid(
    grid: List<List<CrosswordCell>>,
    highlightedPositions: Set<GridPosition>,
    highlightStrength: Float,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = grid.size
    val columns = grid.firstOrNull()?.size ?: 0
    if (rows == 0 || columns == 0) {
        return
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val maxGridWidth = maxWidth.coerceAtMost(CROSSWORD_MAX_SIZE)
        val cellSpacing = 4.dp
        val cellSizeByWidth = (maxGridWidth - cellSpacing * (columns - 1)) / columns
        val cellSizeByHeight = (maxHeight - cellSpacing * (rows - 1)) / rows
        val cellSize = minOf(cellSizeByWidth, cellSizeByHeight)
        val gridWidth = cellSize * columns + cellSpacing * (columns - 1)

        Column(modifier = Modifier.width(gridWidth)) {
            for (rowIndex in 0 until rows) {
                Row {
                    for (colIndex in 0 until columns) {
                        val cell = grid[rowIndex][colIndex]
                        if (cell.isActive) {
                            val cellHighlight = if (highlightedPositions.contains(GridPosition(rowIndex, colIndex))) {
                                highlightStrength
                            } else {
                                NEW_WORD_HIGHLIGHT_NONE
                            }
                            CrosswordCellItem(
                                cell = cell,
                                size = cellSize,
                                highlightStrength = cellHighlight,
                                onClick = { onCellTap(rowIndex, colIndex) }
                            )
                        } else {
                            Spacer(modifier = Modifier.size(cellSize))
                        }
                        if (colIndex < columns - 1) {
                            Spacer(modifier = Modifier.width(cellSpacing))
                        }
                    }
                }
                if (rowIndex < rows - 1) {
                    Spacer(modifier = Modifier.height(cellSpacing))
                }
            }
        }
    }
}

@Composable
private fun CrosswordCellItem(
    cell: CrosswordCell,
    size: Dp,
    highlightStrength: Float,
    onClick: () -> Unit
) {
    val highlight = if (cell.isRevealed) highlightStrength else NEW_WORD_HIGHLIGHT_NONE
    val baseBackground = if (cell.isRevealed) TileColor else TileHiddenColor
    val background = if (highlight > NEW_WORD_HIGHLIGHT_NONE) {
        lerp(baseBackground, NewWordHighlightBackground, highlight)
    } else {
        baseBackground
    }
    val auraAlpha = highlight * NEW_WORD_HIGHLIGHT_AURA_ALPHA
    val letterColor = if (highlight > NEW_WORD_HIGHLIGHT_NONE) {
        lerp(TileText, NewWordHighlightText, highlight)
    } else {
        TileText
    }
    val letterWeight = if (highlight > NEW_WORD_HIGHLIGHT_NONE) {
        FontWeight.ExtraBold
    } else {
        FontWeight.Bold
    }
    val letterSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
    Box(
        modifier = Modifier
            .size(size)
            .background(background, RoundedCornerShape(4.dp))
            .drawWithCache {
                val showAura = auraAlpha > NEW_WORD_HIGHLIGHT_NONE
                val drawSize = this.size
                val auraRadius = drawSize.minDimension * NEW_WORD_HIGHLIGHT_AURA_RADIUS_RATIO
                val auraCenter = Offset(
                    x = drawSize.width * NEW_WORD_HIGHLIGHT_CENTER_RATIO,
                    y = drawSize.height * NEW_WORD_HIGHLIGHT_CENTER_RATIO
                )
                val auraBrush = if (showAura) {
                    Brush.radialGradient(
                        colors = listOf(
                            NewWordHighlightAura.copy(alpha = auraAlpha),
                            Color.Transparent
                        ),
                        center = auraCenter,
                        radius = auraRadius
                    )
                } else {
                    null
                }
                onDrawWithContent {
                    if (showAura && auraBrush != null) {
                        drawRect(brush = auraBrush)
                    }
                    drawContent()
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isRevealed && cell.letter != null) {
            Text(
                text = cell.letter.toString(),
                color = letterColor,
                fontWeight = letterWeight,
                fontSize = letterSize
            )
        }
    }
}

@Composable
private fun LetterWheelSection(
    letters: List<Char>,
    hammerActive: Boolean,
    showNewGameButton: Boolean,
    hasMissingWords: Boolean,
    missingWordsCount: Int,
    lastMissingWord: String?,
    onShuffle: () -> Unit,
    onNewGame: () -> Unit,
    onHammerTap: () -> Unit,
    onHammerLongPress: () -> Unit,
    onSelectionStart: () -> Unit,
    onWordTapped: (String) -> Unit,
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    var selectedLetters by remember { mutableStateOf(emptyList<Char>()) }
    var settledLetters by remember { mutableStateOf(emptyList<Char>()) }
    var selectionActive by remember { mutableStateOf(false) }
    val selectionFade = remember { Animatable(SELECTION_FADE_START) }

    LaunchedEffect(letters) {
        selectedLetters = emptyList()
        settledLetters = emptyList()
        selectionActive = false
    }

    LaunchedEffect(settledLetters) {
        if (settledLetters.isEmpty()) {
            selectionFade.snapTo(SELECTION_FADE_START)
            return@LaunchedEffect
        }
        selectionFade.snapTo(SELECTION_FADE_START)
        selectionFade.animateTo(
            targetValue = SELECTION_FADE_END,
            animationSpec = tween(
                durationMillis = SELECTION_FADE_DURATION_MS,
                easing = LinearEasing
            )
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val verticalPadding = WHEEL_CONTAINER_EXTRA_HEIGHT + WHEEL_VERTICAL_OFFSET
        val maxWheelWidth = maxWidth.coerceAtMost(WHEEL_MAX_SIZE)
        val heightBudget = maxHeight.coerceAtLeast(verticalPadding) - verticalPadding
        val wheelSize = minOf(maxWheelWidth * WHEEL_SIZE_RATIO, heightBudget)

        if (letters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(wheelSize + verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                if (showNewGameButton) {
                    NewGameWheelButton(
                        diameter = wheelSize,
                        onClick = onNewGame
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(wheelSize + verticalPadding)
            ) {
                SelectedLettersPreview(
                    activeLetters = selectedLetters,
                    settledLetters = settledLetters,
                    selectionActive = selectionActive,
                    fadeProgress = selectionFade.value,
                    onWordTapped = onWordTapped,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_VERTICAL_OFFSET)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(wheelSize + WHEEL_CONTAINER_EXTRA_HEIGHT)
                ) {
                    LetterWheel(
                        letters = letters,
                        showNewGameButton = showNewGameButton,
                        onShuffle = onShuffle,
                        onNewGame = onNewGame,
                        onSelectionStart = {
                            onSelectionStart()
                            selectionActive = true
                            settledLetters = emptyList()
                        },
                        onSelectionChanged = { selectedLetters = it },
                        onSelectionEnd = { finalLetters ->
                            selectionActive = false
                            settledLetters = finalLetters
                        },
                        onWordSelected = onWordSelected,
                        soundEffects = soundEffects,
                        modifier = Modifier
                            .size(wheelSize)
                            .align(Alignment.Center)
                    )
                    HammerButton(
                        armed = hammerActive,
                        onClick = onHammerTap,
                        onLongPress = onHammerLongPress,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = WHEEL_CORNER_BUTTON_PADDING, top = WHEEL_CORNER_BUTTON_PADDING)
                    )
                    if (hasMissingWords) {
                        MissingWordsIndicator(
                            remainingCount = missingWordsCount,
                            lastGuessedWord = lastMissingWord,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = WHEEL_CORNER_BUTTON_PADDING, top = WHEEL_CORNER_BUTTON_PADDING)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedLettersPreview(
    activeLetters: List<Char>,
    settledLetters: List<Char>,
    selectionActive: Boolean,
    fadeProgress: Float,
    onWordTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeText = activeLetters.joinToString(separator = "")
    val settledText = settledLetters.joinToString(separator = "")
    val showActive = selectionActive && activeText.isNotBlank()
    val selectionText = if (showActive) activeText else settledText
    val fadeStep = (fadeProgress * SELECTION_FADE_TARGET_RATIO).coerceIn(SELECTION_FADE_START, SELECTION_FADE_END)
    val textColor = if (showActive) {
        TileText
    } else {
        lerp(TileText, Color.Black, fadeStep)
    }
    val clickableModifier = if (!selectionActive && settledText.isNotBlank()) {
        modifier.clickable { onWordTapped(settledText) }
    } else {
        modifier
    }
    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = selectionText,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = WHEEL_SELECTION_TEXT_SIZE,
            letterSpacing = WHEEL_SELECTION_LETTER_SPACING,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LetterWheel(
    letters: List<Char>,
    showNewGameButton: Boolean,
    onShuffle: () -> Unit,
    onNewGame: () -> Unit,
    onSelectionStart: () -> Unit,
    onSelectionChanged: (List<Char>) -> Unit,
    onSelectionEnd: (List<Char>) -> Unit,
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val diameter = maxWidth.coerceAtMost(maxHeight)
        val density = LocalDensity.current
        val letterSize = diameter * WHEEL_LETTER_SIZE_RATIO
        val letterSizePx = with(density) { letterSize.toPx() }
        val letterTextSize = with(density) { (letterSize * WHEEL_LETTER_TEXT_RATIO).toSp() }
        val radiusPx = with(density) {
            (diameter / DIAMETER_TO_RADIUS_DIVISOR - letterSize * WHEEL_LETTER_RING_INSET_FACTOR).toPx()
        }
        val centerPx = with(density) { (diameter / DIAMETER_TO_RADIUS_DIVISOR).toPx() }
        val lineStrokePx = with(density) { 6.dp.toPx() }
        var selectedIndices by remember { mutableStateOf(listOf<Int>()) }
        var dragPosition by remember { mutableStateOf<Offset?>(null) }
        var lastToneFreq by remember { mutableStateOf<Double?>(null) }
        val onWordSelectedState by rememberUpdatedState(onWordSelected)
        val soundEffectsState by rememberUpdatedState(soundEffects)
        val onSelectionStartState by rememberUpdatedState(onSelectionStart)
        val onSelectionChangedState by rememberUpdatedState(onSelectionChanged)
        val onSelectionEndState by rememberUpdatedState(onSelectionEnd)
        val highlightColor = TileColor
        val hitRadius = letterSizePx * WHEEL_HIT_RADIUS_FACTOR * WHEEL_HIT_RADIUS_EXPANSION_FACTOR
        val hitRadiusSq = hitRadius * hitRadius
        val overlayDiameter = diameter - (letterSize * NEW_GAME_DIAMETER_REDUCTION_FACTOR)
        val centers = remember(letters, radiusPx, centerPx) {
            letters.mapIndexed { index, _ ->
                val angle = index.toDouble() / letters.size * 2.0 * PI - PI / 2
                Offset(
                    x = centerPx + (radiusPx * cos(angle)).toFloat(),
                    y = centerPx + (radiusPx * sin(angle)).toFloat()
                )
            }
        }

        fun setSelection(newSelection: List<Int>) {
            if (newSelection == selectedIndices) {
                return
            }
            selectedIndices = newSelection
            onSelectionChangedState(newSelection.map { index -> letters[index].uppercaseChar() })
        }

        LaunchedEffect(letters) {
            setSelection(emptyList())
            dragPosition = null
            lastToneFreq = null
        }

        fun findHitIndex(position: Offset): Int? {
            val index = centers.indexOfFirst { center ->
                val dx = center.x - position.x
                val dy = center.y - position.y
                dx * dx + dy * dy <= hitRadiusSq
            }
            return index.takeIf { it >= 0 }
        }

        fun toneStep(selectionSize: Int): Int {
            return (selectionSize - 1).coerceAtLeast(0)
        }

        fun updateSelection(nextIndex: Int) {
            val current = selectedIndices
            val last = current.lastOrNull()
            if (last == null) {
                val newSelection = listOf(nextIndex)
                setSelection(newSelection)
                val freq = soundEffectsState.letterBell(toneStep(newSelection.size))
                lastToneFreq = freq
                return
            }
            if (nextIndex == last) {
                return
            }
            if (current.size >= 2 && nextIndex == current[current.size - 2]) {
                val newSelection = current.dropLast(1)
                setSelection(newSelection)
                lastToneFreq = if (newSelection.isNotEmpty()) {
                    soundEffectsState.bellFrequency(toneStep(newSelection.size))
                } else {
                    null
                }
                return
            }
            if (!current.contains(nextIndex)) {
                val newSelection = current + nextIndex
                setSelection(newSelection)
                val freq = soundEffectsState.letterBell(toneStep(newSelection.size))
                lastToneFreq = freq
            }
        }

        fun finishSelection() {
            val selectionSize = selectedIndices.size
            if (selectionSize > 0) {
                onSelectionEndState(selectedIndices.map { index -> letters[index].uppercaseChar() })
            }
            if (selectionSize >= MIN_CROSSWORD_WORD_LENGTH) {
                val selectedWord = buildString {
                    selectedIndices.forEach { index ->
                        append(letters[index].uppercaseChar())
                    }
                }
                when (onWordSelectedState(selectedWord)) {
                    WordResult.Success -> soundEffectsState.successBell()
                    WordResult.AlreadySolved -> soundEffectsState.alreadySolvedConfirm()
                    WordResult.MissingWordFound -> soundEffectsState.sideWordFound()
                    WordResult.MissingWordAlreadyFound -> soundEffectsState.alreadySolvedConfirm()
                    WordResult.NotFound -> soundEffectsState.miss()
                }
            } else if (selectionSize > 0) {
                soundEffectsState.shortConfirm()
            }
            setSelection(emptyList())
            dragPosition = null
            lastToneFreq = null
        }

        Box(
            modifier = Modifier
                .size(diameter)
                .background(WheelBackground, CircleShape)
                .align(Alignment.Center)
                .pointerInput(letters, centers) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startIndex = findHitIndex(down.position) ?: return@awaitEachGesture
                        down.consume()
                        onSelectionStartState()
                        setSelection(emptyList())
                        updateSelection(startIndex)
                        dragPosition = centers[startIndex]
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            val hitIndex = findHitIndex(change.position)
                            if (hitIndex != null) {
                                updateSelection(hitIndex)
                                dragPosition = centers[hitIndex]
                            } else {
                                dragPosition = change.position
                            }
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                finishSelection()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                for (i in 0 until selectedIndices.size - 1) {
                    val start = centers[selectedIndices[i]]
                    val end = centers[selectedIndices[i + 1]]
                    drawLine(
                        color = highlightColor,
                        start = start,
                        end = end,
                        strokeWidth = lineStrokePx,
                        cap = StrokeCap.Round
                    )
                }
                val previewEnd = dragPosition
                if (previewEnd != null && selectedIndices.isNotEmpty()) {
                    val start = centers[selectedIndices.last()]
                    val dx = previewEnd.x - start.x
                    val dy = previewEnd.y - start.y
                    if (dx * dx + dy * dy > 1f) {
                        drawLine(
                            color = highlightColor,
                            start = start,
                            end = previewEnd,
                            strokeWidth = lineStrokePx,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            letters.forEachIndexed { index, letter ->
                val center = centers[index]
                val x = center.x - letterSizePx / 2
                val y = center.y - letterSizePx / 2
                val isSelected = selectedIndices.contains(index)
                Box(
                    modifier = Modifier
                        .size(letterSize)
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .background(
                            color = if (isSelected) highlightColor else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.uppercaseChar().toString(),
                        color = if (isSelected) Color.White else WheelLetter,
                        fontSize = letterTextSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (showNewGameButton) {
                NewGameWheelButton(
                    diameter = overlayDiameter,
                    onClick = onNewGame,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                IconButton(
                    onClick = onShuffle,
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.shuffle_letters),
                        tint = WheelLetter
                    )
                }
            }
        }
    }
}

@Composable
private fun NewGameWheelButton(
    diameter: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(diameter)
            .shadow(NEW_GAME_BUTTON_ELEVATION, CircleShape, clip = false)
            .clip(CircleShape)
            .drawWithCache {
                val circleRadius = size.minDimension * NEW_GAME_CIRCLE_RADIUS_RATIO
                val gradientRadius = size.minDimension * NEW_GAME_GRADIENT_RADIUS_RATIO
                val highlightCenter = Offset(
                    x = size.width * NEW_GAME_GRADIENT_CENTER_RATIO,
                    y = size.height * NEW_GAME_GRADIENT_CENTER_RATIO
                )
                val brush = Brush.radialGradient(
                    colors = listOf(GoldHighlight, GoldMid, GoldShadow),
                    center = highlightCenter,
                    radius = gradientRadius
                )
                onDrawBehind {
                    drawCircle(brush = brush, radius = circleRadius, center = center)
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.new_game),
            color = WheelLetter,
            fontWeight = FontWeight.Bold,
            fontSize = NEW_GAME_TEXT_SIZE,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HammerButton(
    armed: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (armed) AccentOrange else IconBase
    Surface(
        color = background,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(WHEEL_CORNER_BUTTON_SIZE)
            .hammerCombinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Gavel,
                contentDescription = stringResource(R.string.hammer),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun MissingWordsIndicator(
    remainingCount: Int,
    lastGuessedWord: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!lastGuessedWord.isNullOrBlank()) {
            Text(
                text = lastGuessedWord,
                color = TileText.copy(alpha = MISSING_WORD_LABEL_ALPHA),
                fontWeight = FontWeight.SemiBold,
                fontSize = MISSING_WORD_LABEL_TEXT_SIZE,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(MISSING_WORD_LABEL_SPACING))
        }
        Surface(
            color = IconBase,
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = Modifier.size(WHEEL_CORNER_BUTTON_SIZE)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = remainingCount.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = MISSING_WORD_COUNT_TEXT_SIZE
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.hammerCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier {
    return combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = IconBase,
        shape = CircleShape,
        shadowElevation = 6.dp,
        modifier = modifier.size(44.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun AbstractBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(DeepGreen, MidGreen, LightGreen)
            )
        )
        drawCircle(
            color = LightGreen.copy(alpha = 0.25f),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width * 0.2f, size.height * 0.15f)
        )
        drawCircle(
            color = MidGreen.copy(alpha = 0.25f),
            radius = size.minDimension * 0.5f,
            center = Offset(size.width * 0.85f, size.height * 0.3f)
        )
        drawCircle(
            color = DeepGreen.copy(alpha = 0.35f),
            radius = size.minDimension * 0.7f,
            center = Offset(size.width * 0.6f, size.height * 0.85f)
        )
    }
}

private data class NetSnapshotSettings(
    val selectionModeId: String,
    val maxLetterSetSize: Int
)

private data class NetSnapshot(
    val stateVersion: Int,
    val seedLetters: String,
    val wheelLetters: List<Char>,
    val gridRows: List<String>,
    val revealedPositions: List<GridPosition>,
    val words: List<CrosswordWord>,
    val solvedBy: Map<String, String>,
    val settings: NetSnapshotSettings
)

private sealed interface NetMessage {
    data class Snapshot(
        val role: NetPlayRole,
        val snapshot: NetSnapshot?,
        val players: List<NetPlayerInfo> = emptyList()
    ) : NetMessage
    data class StateUpdate(
        val snapshot: NetSnapshot,
        val players: List<NetPlayerInfo> = emptyList()
    ) : NetMessage
    data class Conflict(
        val snapshot: NetSnapshot,
        val players: List<NetPlayerInfo> = emptyList()
    ) : NetMessage
    data class Error(
        val message: String,
        val players: List<NetPlayerInfo> = emptyList()
    ) : NetMessage
}

private data class NetRebaseResult(
    val grid: List<List<CrosswordCell>>,
    val pendingWords: List<String>,
    val discardedWords: List<String>,
    val solvedBy: Map<String, String>
)

private fun rebasePendingWords(
    baseGrid: List<List<CrosswordCell>>,
    crosswordWords: Map<String, CrosswordWord>,
    pendingWords: List<String>,
    baseSolvedBy: Map<String, String>,
    playerId: String
): NetRebaseResult {
    if (pendingWords.isEmpty()) {
        return NetRebaseResult(baseGrid, emptyList(), emptyList(), baseSolvedBy)
    }
    var updatedGrid = baseGrid
    val remaining = mutableListOf<String>()
    val discarded = mutableListOf<String>()
    val updatedSolvedBy = baseSolvedBy.toMutableMap()
    for (word in pendingWords) {
        val match = crosswordWords[word]
        if (match == null) {
            discarded.add(word)
            continue
        }
        val isSolved = match.positions.all { pos -> updatedGrid[pos.row][pos.col].isRevealed }
        if (isSolved) {
            discarded.add(word)
            continue
        }
        val (nextGrid, result) = applySelectedWord(word, crosswordWords, updatedGrid)
        updatedGrid = nextGrid
        if (result == WordResult.Success && !remaining.contains(word)) {
            remaining.add(word)
            if (playerId.isNotBlank()) {
                updatedSolvedBy[word] = playerId
            }
        } else if (result != WordResult.Success) {
            discarded.add(word)
        }
    }
    return NetRebaseResult(updatedGrid, remaining, discarded, updatedSolvedBy.toMap())
}

private fun buildNetJoinMessage(settings: UserSettings): String {
    val root = JSONObject()
    root.put(NET_JSON_TYPE, NET_MESSAGE_TYPE_JOIN)
    root.put(NET_JSON_PLAYER_ID, settings.playerId)
    root.put(NET_JSON_PLAYER_NAME, settings.playerName)
    root.put(NET_JSON_PLAYER_COLOR, settings.playerColor.id)
    return root.toString()
}

private fun buildNetNewGameMessage(snapshot: NetSnapshot): String {
    val root = JSONObject()
    root.put(NET_JSON_TYPE, NET_MESSAGE_TYPE_NEW_GAME)
    root.put(NET_JSON_SNAPSHOT, buildNetSnapshotJson(snapshot))
    return root.toString()
}

private fun buildNetSubmitWordMessage(snapshot: NetSnapshot, baseVersion: Int): String {
    val root = JSONObject()
    root.put(NET_JSON_TYPE, NET_MESSAGE_TYPE_SUBMIT_WORD)
    root.put(NET_JSON_BASE_VERSION, baseVersion)
    root.put(NET_JSON_SNAPSHOT, buildNetSnapshotJson(snapshot))
    return root.toString()
}

private fun buildNetSnapshotFromState(
    seedLetters: String,
    wheelLetters: List<Char>,
    grid: List<List<CrosswordCell>>,
    crosswordWords: Map<String, CrosswordWord>,
    settings: UserSettings,
    solvedBy: Map<String, String>,
    stateVersion: Int
): NetSnapshot? {
    if (seedLetters.isBlank() || grid.isEmpty()) {
        return null
    }
    val gridRows = buildGridRows(grid)
    val revealedPositions = buildRevealedPositions(grid)
    val words = crosswordWords.values.toList()
    val resolvedWheelLetters = if (wheelLetters.isEmpty()) {
        generateLetterWheel(seedLetters)
    } else {
        wheelLetters
    }
    return NetSnapshot(
        stateVersion = stateVersion,
        seedLetters = seedLetters,
        wheelLetters = resolvedWheelLetters,
        gridRows = gridRows,
        revealedPositions = revealedPositions,
        words = words,
        solvedBy = solvedBy,
        settings = buildNetSnapshotSettings(settings)
    )
}

private fun buildNetSnapshotSettings(settings: UserSettings): NetSnapshotSettings {
    return NetSnapshotSettings(
        selectionModeId = settings.selectionMode.id,
        maxLetterSetSize = settings.maxLetterSetSize
    )
}

private fun buildNetSnapshotJson(snapshot: NetSnapshot): JSONObject {
    val root = JSONObject()
    root.put(NET_JSON_STATE_VERSION, snapshot.stateVersion)
    root.put(NET_JSON_SEED_LETTERS, snapshot.seedLetters)
    root.put(
        NET_JSON_WHEEL_LETTERS,
        JSONArray(snapshot.wheelLetters.map { it.toString() })
    )
    root.put(NET_JSON_GRID_ROWS, JSONArray(snapshot.gridRows))
    root.put(NET_JSON_REVEALED, buildPositionsJson(snapshot.revealedPositions))
    root.put(NET_JSON_WORDS, buildWordsJson(snapshot.words))
    root.put(NET_JSON_SOLVED_BY, buildNetSolvedByJson(snapshot.solvedBy))
    root.put(NET_JSON_SETTINGS, buildSettingsJson(snapshot.settings))
    return root
}

private fun buildGridRows(grid: List<List<CrosswordCell>>): List<String> {
    if (grid.isEmpty()) {
        return emptyList()
    }
    return grid.map { row ->
        buildString {
            row.forEach { cell ->
                val letter = cell.letter
                append(letter?.lowercaseChar() ?: CROSSWORD_EMPTY_CELL)
            }
        }
    }
}

private fun buildRevealedPositions(grid: List<List<CrosswordCell>>): List<GridPosition> {
    if (grid.isEmpty()) {
        return emptyList()
    }
    val positions = mutableListOf<GridPosition>()
    for ((rowIndex, row) in grid.withIndex()) {
        for ((colIndex, cell) in row.withIndex()) {
            if (cell.isRevealed) {
                positions.add(GridPosition(row = rowIndex, col = colIndex))
            }
        }
    }
    return positions
}

private fun buildPositionsJson(positions: List<GridPosition>): JSONArray {
    val array = JSONArray()
    for (position in positions) {
        val item = JSONObject()
        item.put(NET_JSON_ROW, position.row)
        item.put(NET_JSON_COL, position.col)
        array.put(item)
    }
    return array
}

private fun buildWordsJson(words: List<CrosswordWord>): JSONArray {
    val array = JSONArray()
    for (word in words) {
        val item = JSONObject()
        item.put(NET_JSON_WORD, word.word)
        item.put(NET_JSON_POSITIONS, buildPositionsJson(word.positions.toList()))
        array.put(item)
    }
    return array
}

private fun buildNetSolvedByJson(solvedBy: Map<String, String>): JSONObject {
    val root = JSONObject()
    for ((word, playerId) in solvedBy) {
        if (word.isNotBlank() && playerId.isNotBlank()) {
            root.put(word, playerId)
        }
    }
    return root
}

private fun buildSettingsJson(settings: NetSnapshotSettings): JSONObject {
    val root = JSONObject()
    root.put(NET_JSON_SELECTION_MODE, settings.selectionModeId)
    root.put(NET_JSON_MAX_LETTER_SET_SIZE, settings.maxLetterSetSize)
    return root
}

private fun parseNetMessage(raw: String): NetMessage? {
    val root = try {
        JSONObject(raw)
    } catch (error: JSONException) {
        return null
    }
    val players = parseNetPlayers(root.optJSONArray(NET_JSON_PLAYERS))
    return when (root.optString(NET_JSON_TYPE, "")) {
        NET_MESSAGE_TYPE_SNAPSHOT -> {
            val role = NetPlayRole.fromId(root.optString(NET_JSON_ROLE, ""))
            val snapshot = root.optJSONObject(NET_JSON_SNAPSHOT)?.let { parseNetSnapshot(it) }
            NetMessage.Snapshot(role = role, snapshot = snapshot, players = players)
        }
        NET_MESSAGE_TYPE_STATE_UPDATE -> {
            val snapshot = root.optJSONObject(NET_JSON_SNAPSHOT)?.let { parseNetSnapshot(it) } ?: return null
            NetMessage.StateUpdate(snapshot = snapshot, players = players)
        }
        NET_MESSAGE_TYPE_ERROR -> {
            val message = root.optString(NET_JSON_MESSAGE, "")
            val snapshot = root.optJSONObject(NET_JSON_SNAPSHOT)?.let { parseNetSnapshot(it) }
            if (message == NET_ERROR_CONFLICT && snapshot != null) {
                NetMessage.Conflict(snapshot = snapshot, players = players)
            } else {
                NetMessage.Error(message = message, players = players)
            }
        }
        else -> null
    }
}

private fun parseNetSnapshot(snapshot: JSONObject): NetSnapshot? {
    val seedLetters = snapshot.optString(NET_JSON_SEED_LETTERS, "")
    if (seedLetters.isBlank()) {
        return null
    }
    val gridRows = parseGridRows(snapshot.optJSONArray(NET_JSON_GRID_ROWS))
    if (gridRows.isEmpty()) {
        return null
    }
    val wheelLetters = parseWheelLetters(snapshot.optJSONArray(NET_JSON_WHEEL_LETTERS), seedLetters)
    val revealedPositions = parsePositions(snapshot.optJSONArray(NET_JSON_REVEALED))
    val words = parseWords(snapshot.optJSONArray(NET_JSON_WORDS))
    val solvedBy = parseSolvedBy(snapshot.optJSONObject(NET_JSON_SOLVED_BY))
    val settings = parseNetSnapshotSettings(snapshot.optJSONObject(NET_JSON_SETTINGS))
    val stateVersion = snapshot.optInt(NET_JSON_STATE_VERSION, NET_STATE_VERSION_INITIAL)
    return NetSnapshot(
        stateVersion = stateVersion,
        seedLetters = seedLetters,
        wheelLetters = wheelLetters,
        gridRows = gridRows,
        revealedPositions = revealedPositions,
        words = words,
        solvedBy = solvedBy,
        settings = settings
    )
}

private fun parseNetSnapshotSettings(settings: JSONObject?): NetSnapshotSettings {
    if (settings == null) {
        return NetSnapshotSettings(
            selectionModeId = DEFAULT_CROSSWORD_SELECTION_MODE.id,
            maxLetterSetSize = DEFAULT_MAX_LETTER_SET_SIZE
        )
    }
    val selectionModeId = settings.optString(
        NET_JSON_SELECTION_MODE,
        DEFAULT_CROSSWORD_SELECTION_MODE.id
    )
    val maxLetterSetSize = settings.optInt(
        NET_JSON_MAX_LETTER_SET_SIZE,
        DEFAULT_MAX_LETTER_SET_SIZE
    )
    return NetSnapshotSettings(
        selectionModeId = selectionModeId,
        maxLetterSetSize = maxLetterSetSize
    )
}

private fun parseNetPlayers(array: JSONArray?): List<NetPlayerInfo> {
    if (array == null) {
        return emptyList()
    }
    val players = mutableListOf<NetPlayerInfo>()
    val seen = mutableSetOf<String>()
    val count = array.length()
    repeat(count) { index ->
        val item = array.optJSONObject(index) ?: return@repeat
        val playerId = item.optString(NET_JSON_PLAYER_ID, "").trim()
        val colorId = item.optString(NET_JSON_PLAYER_COLOR, "").trim()
        if (playerId.isBlank() || colorId.isBlank()) {
            return@repeat
        }
        if (!seen.add(playerId)) {
            return@repeat
        }
        val playerName = item.optString(NET_JSON_PLAYER_NAME, "").trim()
        val playerColor = NetPlayerColor.fromId(colorId)
        players.add(
            NetPlayerInfo(
                playerId = playerId,
                playerName = playerName,
                playerColor = playerColor
            )
        )
    }
    return players
}

private fun parseGridRows(rows: JSONArray?): List<String> {
    if (rows == null) {
        return emptyList()
    }
    val result = mutableListOf<String>()
    val count = rows.length()
    repeat(count) { index ->
        result.add(rows.optString(index, ""))
    }
    return result
}

private fun parseWheelLetters(array: JSONArray?, seedLetters: String): List<Char> {
    if (array == null) {
        return generateLetterWheel(seedLetters)
    }
    val letters = mutableListOf<Char>()
    val count = array.length()
    repeat(count) { index ->
        val raw = array.optString(index, "")
        val letter = raw.firstOrNull()
        if (letter != null) {
            letters.add(letter)
        }
    }
    return if (letters.isEmpty()) generateLetterWheel(seedLetters) else letters
}

private fun parsePositions(array: JSONArray?): List<GridPosition> {
    if (array == null) {
        return emptyList()
    }
    val positions = mutableListOf<GridPosition>()
    val count = array.length()
    repeat(count) { index ->
        val item = array.optJSONObject(index) ?: return@repeat
        val row = item.optInt(NET_JSON_ROW, NET_INVALID_INDEX)
        val col = item.optInt(NET_JSON_COL, NET_INVALID_INDEX)
        if (row != NET_INVALID_INDEX && col != NET_INVALID_INDEX) {
            positions.add(GridPosition(row = row, col = col))
        }
    }
    return positions
}

private fun parseWords(array: JSONArray?): List<CrosswordWord> {
    if (array == null) {
        return emptyList()
    }
    val words = mutableListOf<CrosswordWord>()
    val count = array.length()
    repeat(count) { index ->
        val item = array.optJSONObject(index) ?: return@repeat
        val word = item.optString(NET_JSON_WORD, "").uppercase()
        if (word.isBlank()) {
            return@repeat
        }
        val positions = parsePositions(item.optJSONArray(NET_JSON_POSITIONS))
        if (positions.isEmpty()) {
            return@repeat
        }
        words.add(CrosswordWord(word = word, positions = positions.toSet()))
    }
    return words
}

private fun parseSolvedBy(data: JSONObject?): Map<String, String> {
    if (data == null) {
        return emptyMap()
    }
    val result = mutableMapOf<String, String>()
    val iterator = data.keys()
    while (iterator.hasNext()) {
        val word = iterator.next().trim().uppercase()
        if (word.isBlank()) {
            continue
        }
        val playerId = data.optString(word, "").trim()
        if (playerId.isNotBlank()) {
            result[word] = playerId
        }
    }
    return result.toMap()
}

private fun applyRevealedPositions(
    grid: List<List<CrosswordCell>>,
    positions: List<GridPosition>
): List<List<CrosswordCell>> {
    if (grid.isEmpty() || positions.isEmpty()) {
        return grid
    }
    val revealed = positions.toSet()
    return grid.mapIndexed { rowIndex, row ->
        row.mapIndexed { colIndex, cell ->
            if (revealed.contains(GridPosition(row = rowIndex, col = colIndex))) {
                cell.copy(isRevealed = true)
            } else {
                cell
            }
        }
    }
}

private class NetPlayConnection(
    private val client: OkHttpClient,
    private val onStatusChange: (NetConnectionStatus) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null

    fun connect(url: String) {
        webSocket?.cancel()
        val request = try {
            Request.Builder().url(url).build()
        } catch (exception: IllegalArgumentException) {
            postStatus(NetConnectionStatus.Disconnected)
            return
        }
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (isCurrentSocket(webSocket)) {
                        postStatus(NetConnectionStatus.Connected)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (isCurrentSocket(webSocket)) {
                        postStatus(NetConnectionStatus.Disconnected)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isCurrentSocket(webSocket)) {
                        postMessage(text)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    if (isCurrentSocket(webSocket)) {
                        postStatus(NetConnectionStatus.Disconnected)
                    }
                }
            }
        )
    }

    fun disconnect() {
        webSocket?.close(NET_PLAY_NORMAL_CLOSE_CODE, NET_PLAY_CLOSE_REASON)
        webSocket = null
    }

    fun shutdown() {
        disconnect()
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    private fun isCurrentSocket(socket: WebSocket): Boolean {
        return socket == webSocket
    }

    private fun postStatus(status: NetConnectionStatus) {
        mainHandler.post {
            onStatusChange(status)
        }
    }

    private fun postMessage(message: String) {
        mainHandler.post {
            onMessage(message)
        }
    }
}

private class TonePlayer {
    private val sampleRate = 44_100
    private val handlerThread = HandlerThread("tone-player", Process.THREAD_PRIORITY_URGENT_AUDIO).apply { start() }
    private val handler = Handler(handlerThread.looper)

    init {
        warmUpSilently()
    }


    private fun warmUpSilently() {
        // Preload audio pipeline to remove first-tap latency
        playTone(listOf(0.0), durationMs = 20, volume = 0f)
    }

    fun playTone(
        frequencies: List<Double>,
        durationMs: Int,
        volume: Float = 0.5f,
        startDelayMs: Long = 0,
        weights: List<Double>? = null
    ) {
        val safeVolume = volume.coerceIn(0f, 1f)
        val appliedWeights = when {
            weights == null || weights.size != frequencies.size -> List(frequencies.size) { 1.0 }
            else -> weights
        }
        val weightSum = appliedWeights.sum().coerceAtLeast(1e-6)
        val playTask = Runnable {
            val totalSamples = kotlin.math.max(1, (sampleRate * (durationMs / 1000.0)).toInt())
            val buffer = ShortArray(totalSamples)
            val twoPi = 2.0 * PI
            val fadeSamples = kotlin.math.max(1, kotlin.math.min(totalSamples / 5, (sampleRate * 0.0025).toInt()))
            for (i in buffer.indices) {
                val t = i / sampleRate.toDouble()
                var sample = 0.0
                frequencies.forEachIndexed { idx, freq ->
                    sample += kotlin.math.sin(twoPi * freq * t) * appliedWeights[idx]
                }
                sample /= weightSum
                val fadeIn = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
                val fadeOut = if (i >= totalSamples - fadeSamples) (totalSamples - 1 - i).toDouble() / fadeSamples else 1.0
                val envelope = kotlin.math.max(0.0, kotlin.math.min(1.0, fadeIn * fadeOut))
                val shaped = (sample * envelope).coerceIn(-1.0, 1.0)
                buffer[i] = (shaped * Short.MAX_VALUE * safeVolume).toInt().toShort()
            }
            val audioTrackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioTrackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val audioTrack = audioTrackBuilder.build()
            audioTrack.setVolume(safeVolume)
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            handler.postDelayed({ audioTrack.release() }, durationMs.toLong() + 20)
        }
        if (startDelayMs > 0) {
            handler.postDelayed(playTask, startDelayMs)
        } else {
            handler.postAtFrontOfQueue(playTask)
        }
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
}

private class SoundPoolSample(
    context: Context,
    private val rawResId: Int,
    private val volume: Float
) {
    private val soundPool: SoundPool
    private var soundId: Int = SOUND_POOL_INVALID_SOUND_ID
    @Volatile private var isLoaded: Boolean = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(SOUND_POOL_MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == SOUND_POOL_LOAD_SUCCESS && sampleId == soundId) {
                isLoaded = true
            }
        }
        soundId = soundPool.load(context, rawResId, SOUND_POOL_DEFAULT_PRIORITY)
    }

    fun play(playbackRate: Float = SOUND_POOL_BASE_RATE) {
        if (!isLoaded) {
            return
        }
        val rate = playbackRate.coerceIn(SOUND_POOL_MIN_RATE, SOUND_POOL_MAX_RATE)
        soundPool.play(
            soundId,
            volume,
            volume,
            SOUND_POOL_DEFAULT_PRIORITY,
            SOUND_POOL_NO_LOOP,
            rate
        )
    }

    fun release() {
        soundPool.release()
    }
}

private class SoundEffects(
    private val player: TonePlayer,
    private val letterTapSample: SoundPoolSample,
    private val bellSample: SoundPoolSample,
    private val sideWordSample: SoundPoolSample,
    private val completedSample: SoundPoolSample
) {
    var muted: Boolean = false
    private val bellBaseHz = 660.0
    private val bellStepRatio = 1.08
    private val initialTapHz = 220.0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialTap() {
        if (!muted) {
            player.playTone(listOf(initialTapHz), durationMs = 70, volume = 0.35f)
        }
    }

    fun letterBell(stepIndex: Int): Double {
        val freq = bellFrequency(stepIndex)
        if (!muted) {
            letterTapSample.play(bellPlaybackRate(stepIndex))
        }
        return freq
    }

    private fun bellPlaybackRate(stepIndex: Int): Float {
        val rate = SOUND_POOL_BASE_RATE * bellStepRatio.pow(stepIndex.toDouble()).toFloat()
        return rate.coerceIn(SOUND_POOL_MIN_RATE, SOUND_POOL_MAX_RATE)
    }

    fun bellFrequency(stepIndex: Int): Double {
        return bellBaseHz * bellStepRatio.pow(stepIndex.toDouble())
    }

    fun successBell() {
        if (muted) return
        bellSample.play()
    }

    fun sideWordFound() {
        if (muted) return
        sideWordSample.play()
    }

    fun crosswordCompleted() {
        if (muted) return
        completedSample.play()
    }

    fun miss() {
        if (muted) return
        player.playTone(listOf(200.0, 150.0), durationMs = 130, volume = 0.5f)
        player.playTone(listOf(170.0, 130.0), durationMs = 130, volume = 0.5f, startDelayMs = 100)
    }

    fun alreadySolvedConfirm() {
        if (muted) return
        letterTapSample.play(SOUND_POOL_BASE_RATE)
        mainHandler.postDelayed(
            {
                if (!muted) {
                    letterTapSample.play(SOUND_POOL_BASE_RATE)
                }
            },
            ALREADY_SOLVED_CONFIRM_REPEAT_DELAY_MS
        )
    }

    fun shortConfirm() {
        if (muted) return
        player.playTone(listOf(220.0), durationMs = 100, volume = 0.25f)
    }

    fun reviewWordConfirm() {
        if (muted) return
        player.playTone(
            listOf(REVIEW_WORD_CONFIRM_TONE_HZ),
            durationMs = REVIEW_WORD_CONFIRM_DURATION_MS,
            volume = REVIEW_WORD_CONFIRM_VOLUME
        )
    }
}

private fun buildNetServerUrl(serverIp: String, serverPort: Int): String {
    val trimmedIp = serverIp.trim().ifBlank { DEFAULT_NET_SERVER_IP }
    return "$NET_PLAY_WEBSOCKET_SCHEME$trimmedIp:$serverPort$NET_PLAY_WEBSOCKET_PATH"
}

private fun generatePlayerId(): String {
    return UUID.randomUUID().toString()
}

private fun loadSettings(context: Context): UserSettings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val storedMax = if (prefs.contains(KEY_MAX_LETTER_SET_SIZE)) {
        prefs.getInt(KEY_MAX_LETTER_SET_SIZE, DEFAULT_MAX_LETTER_SET_SIZE)
    } else {
        prefs.getInt(LEGACY_KEY_MAX_WORD_LENGTH, DEFAULT_MAX_LETTER_SET_SIZE)
    }
    val maxLetterSetSize = storedMax.coerceIn(
        MIN_SEED_LETTER_SET_SIZE,
        MAX_SEED_LETTER_SET_SIZE
    )
    val muted = prefs.getBoolean(KEY_MUTE, false)
    val selectionModeId = prefs.getString(KEY_CROSSWORD_SELECTION_MODE, DEFAULT_CROSSWORD_SELECTION_MODE_ID)
        ?: DEFAULT_CROSSWORD_SELECTION_MODE_ID
    val selectionMode = CrosswordSelectionMode.fromId(selectionModeId)
    val playerName = prefs.getString(KEY_NET_PLAYER_NAME, DEFAULT_NET_PLAYER_NAME) ?: DEFAULT_NET_PLAYER_NAME
    val playerColorId = prefs.getString(KEY_NET_PLAYER_COLOR, NET_PLAYER_COLOR_WHITE) ?: NET_PLAYER_COLOR_WHITE
    val playerColor = NetPlayerColor.fromId(playerColorId)
    val serverIp = prefs.getString(KEY_NET_SERVER_IP, DEFAULT_NET_SERVER_IP) ?: DEFAULT_NET_SERVER_IP
    val normalizedServerIp = serverIp.ifBlank { DEFAULT_NET_SERVER_IP }
    val serverPort = prefs.getInt(KEY_NET_SERVER_PORT, DEFAULT_NET_SERVER_PORT)
        .coerceIn(MIN_NET_SERVER_PORT, MAX_NET_SERVER_PORT)
    return UserSettings(
        muted = muted,
        maxLetterSetSize = maxLetterSetSize,
        selectionMode = selectionMode,
        playerId = generatePlayerId(),
        playerName = playerName,
        playerColor = playerColor,
        serverIp = normalizedServerIp,
        serverPort = serverPort
    )
}

private fun seedLetterLengthRange(maxLetterSetSize: Int): IntRange {
    val clampedMax = maxLetterSetSize.coerceIn(MIN_SEED_LETTER_SET_SIZE, MAX_SEED_LETTER_SET_SIZE)
    val minSize = maxOf(MIN_SEED_LETTER_SET_SIZE, clampedMax - COUNT_STEP)
    return minSize..clampedMax
}

private fun buildSeedLetterCandidates(
    eligibleWords: List<String>,
    previousSeedLetters: String,
    selectionMode: CrosswordSelectionMode,
    maxLetterSetSize: Int,
    random: Random = Random.Default
): List<String> {
    val seedLengthRange = seedLetterLengthRange(maxLetterSetSize)
    return when (selectionMode) {
        CrosswordSelectionMode.RandomWord -> eligibleWords
        CrosswordSelectionMode.LowOverlapWord -> {
            val filtered = eligibleWords.filter { hasLowLetterOverlap(it, previousSeedLetters) }
            if (filtered.isEmpty()) eligibleWords else filtered
        }
        CrosswordSelectionMode.VowelRichLetters -> {
            buildRandomSeedLetterCandidates(
                seedLengthRange = seedLengthRange,
                candidateCount = MAX_CROSSWORD_GENERATION_ATTEMPTS,
                previousSeedLetters = previousSeedLetters,
                random = random
            )
        }
    }
}

private fun hasLowLetterOverlap(candidate: String, previousSeedLetters: String): Boolean {
    if (previousSeedLetters.isBlank()) {
        return true
    }
    val normalizedCandidate = candidate.uppercase()
    val normalizedPrevious = previousSeedLetters.uppercase()
    val candidateCounts = countLetterMatches(normalizedCandidate) ?: return true
    val previousCounts = countLetterMatches(normalizedPrevious) ?: return true
    val sharedCount = candidateCounts.indices.sumOf { index ->
        minOf(candidateCounts[index], previousCounts[index])
    }
    val maxShared = floor(normalizedCandidate.length * LOW_OVERLAP_MAX_SHARED_RATIO).toInt()
    return sharedCount <= maxShared
}

private fun countLetterMatches(word: String): IntArray? {
    val counts = IntArray(CONSONANTS.length + VOWELS.length)
    for (char in word) {
        if (char !in 'A'..'Z') {
            return null
        }
        counts[char - 'A']++
    }
    return counts
}

private fun buildRandomSeedLetterCandidates(
    seedLengthRange: IntRange,
    candidateCount: Int,
    previousSeedLetters: String,
    random: Random = Random.Default
): List<String> {
    val consonantPool = buildAvailableConsonants(previousSeedLetters)
    val lengthRange = seedLengthRange.last - seedLengthRange.first + COUNT_STEP
    return List(candidateCount.coerceAtLeast(COUNT_STEP)) {
        val seedLength = seedLengthRange.first + random.nextInt(lengthRange)
        val vowelCount = pickRandomVowelCount(seedLength, random)
        buildRandomSeedLetters(
            seedLength = seedLength,
            vowelCount = vowelCount,
            consonantPool = consonantPool,
            random = random
        )
    }
}

private fun buildAvailableConsonants(previousSeedLetters: String): List<Char> {
    if (previousSeedLetters.isBlank()) {
        return CONSONANTS.toList()
    }
    val previousConsonants = previousSeedLetters.uppercase()
        .filter { it in CONSONANTS }
        .toSet()
    return CONSONANTS.filterNot { previousConsonants.contains(it) }.toList()
}

private fun pickRandomVowelCount(seedLength: Int, random: Random): Int {
    val minVowels = MIN_RANDOM_VOWEL_COUNT.coerceAtMost(seedLength)
    val maxVowels = MAX_RANDOM_VOWEL_COUNT.coerceAtMost(seedLength)
    return if (minVowels == maxVowels) {
        minVowels
    } else {
        if (random.nextBoolean()) minVowels else maxVowels
    }
}

private fun buildRandomSeedLetters(
    seedLength: Int,
    vowelCount: Int,
    consonantPool: List<Char>,
    random: Random
): String {
    val vowels = pickRandomVowels(vowelCount, random)
    val consonantCount = seedLength - vowels.size
    val consonants = pickRandomConsonants(consonantPool, consonantCount, random)
    return (vowels + consonants).shuffled(random).joinToString(separator = "")
}

private fun pickRandomVowels(vowelCount: Int, random: Random): List<Char> {
    if (vowelCount < COUNT_STEP) {
        return emptyList()
    }
    return List(vowelCount) { VOWELS[random.nextInt(VOWELS.length)] }
}

private fun pickRandomConsonants(
    consonantPool: List<Char>,
    consonantCount: Int,
    random: Random
): List<Char> {
    if (consonantCount < COUNT_STEP || consonantPool.isEmpty()) {
        return emptyList()
    }
    return if (consonantCount <= consonantPool.size) {
        consonantPool.shuffled(random).take(consonantCount)
    } else {
        List(consonantCount) { consonantPool[random.nextInt(consonantPool.size)] }
    }
}

private fun loadReviewWords(context: Context): List<String> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_REVIEW_WORDS, "") ?: ""
    if (stored.isBlank()) {
        return emptyList()
    }
    return stored.split(REVIEW_WORDS_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.lowercase(Locale.US) }
}

private fun logSmallWordListIfNeeded(context: Context, seedLetters: String, dictionary: List<String>) {
    if (seedLetters.isBlank()) {
        return
    }
    val matchingWordCount = buildMiniDictionary(seedLetters, dictionary)
        .count { it.length >= MIN_CROSSWORD_WORD_LENGTH }
    if (matchingWordCount < MIN_PROBLEM_LOG_WORD_COUNT) {
        val description = "$seedLetters: could only find $matchingWordCount words for crossword."
        appendProblemLogEntry(context, PROBLEM_LOG_TYPE_WORD, description)
    }
}

private fun logRejectedSeedLetters(context: Context, rejectedSeedLetters: List<String>) {
    for (seedLetters in rejectedSeedLetters.distinct()) {
        if (seedLetters.isBlank()) {
            continue
        }
        val description = buildRejectedSeedLettersDescription(seedLetters)
        appendProblemLogEntryIfMissing(context, PROBLEM_LOG_TYPE_WORD, description)
    }
}

private fun buildRejectedSeedLettersDescription(seedLetters: String): String {
    return "$seedLetters: crossword word count below minimum of $MIN_CROSSWORD_WORD_COUNT."
}

private fun saveSettings(context: Context, settings: UserSettings) {
    val normalizedServerIp = settings.serverIp.trim().ifBlank { DEFAULT_NET_SERVER_IP }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_MUTE, settings.muted)
        .putInt(
            KEY_MAX_LETTER_SET_SIZE,
            settings.maxLetterSetSize.coerceIn(MIN_SEED_LETTER_SET_SIZE, MAX_SEED_LETTER_SET_SIZE)
        )
        .putString(KEY_CROSSWORD_SELECTION_MODE, settings.selectionMode.id)
        .putString(KEY_NET_PLAYER_NAME, settings.playerName.trim())
        .putString(KEY_NET_PLAYER_COLOR, settings.playerColor.id)
        .putString(KEY_NET_SERVER_IP, normalizedServerIp)
        .putInt(
            KEY_NET_SERVER_PORT,
            settings.serverPort.coerceIn(MIN_NET_SERVER_PORT, MAX_NET_SERVER_PORT)
        )
        .apply()
}

private fun saveReviewWords(context: Context, words: List<String>) {
    val serialized = words.joinToString(REVIEW_WORDS_SEPARATOR)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_REVIEW_WORDS, serialized)
        .apply()
}

private fun shareWordsToReview(context: Context, words: List<String>) {
    val shareText = words.joinToString(System.lineSeparator())
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = REVIEW_WORDS_MIME_TYPE
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, buildReviewWordsSubject(Date()))
    }
    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.menu_send_words_review))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun buildReviewWordsSubject(date: Date): String {
    val formatter = SimpleDateFormat(REVIEW_WORDS_SUBJECT_DATE_PATTERN, Locale.US)
    return formatter.format(date) + REVIEW_WORDS_SUBJECT_SUFFIX
}
