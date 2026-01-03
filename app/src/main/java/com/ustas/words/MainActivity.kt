package com.ustas.words

import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consumeAllChanges
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
import com.ustas.words.ui.theme.NewWordHighlightAura
import com.ustas.words.ui.theme.NewWordHighlightBackground
import com.ustas.words.ui.theme.NewWordHighlightText
import com.ustas.words.ui.theme.TileColor
import com.ustas.words.ui.theme.TileHiddenColor
import com.ustas.words.ui.theme.TileText
import com.ustas.words.ui.theme.WheelBackground
import com.ustas.words.ui.theme.WheelLetter
import com.ustas.words.ui.theme.WordsTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sin

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
private const val KEY_MAX_WORD_LENGTH = "max_word_length"
private const val DEFAULT_MAX_WORD_LENGTH = 9
private const val MIN_WORD_LENGTH = 5
private const val MAX_WORD_LENGTH = 9
private const val MIN_PROBLEM_LOG_WORD_COUNT = 5
private const val PROBLEM_LOG_TYPE_WORD = "word"
private const val PROBLEM_LOG_TYPE_LOGIC = "logic"
private const val PROBLEM_LOG_ATTEMPT_LIMIT_COMMENT = "Crossword generation attempt limit exceeded."
private const val WHEEL_SIZE_RATIO = 0.8f
private val WHEEL_LETTER_SIZE = 48.dp
private val WHEEL_MAX_SIZE = 320.dp
private val WHEEL_CONTAINER_EXTRA_HEIGHT = 24.dp
private val WHEEL_VERTICAL_OFFSET = WHEEL_LETTER_SIZE
private const val DIAMETER_TO_RADIUS_DIVISOR = 2f
private const val WHEEL_LETTER_RING_INSET_FACTOR = 0.7f
private const val NEW_GAME_DIAMETER_REDUCTION_FACTOR =
    WHEEL_LETTER_RING_INSET_FACTOR * DIAMETER_TO_RADIUS_DIVISOR
private const val NEW_GAME_GRADIENT_CENTER_RATIO = 0.3f
private const val NEW_GAME_GRADIENT_RADIUS_RATIO = 0.85f
private const val NEW_GAME_CIRCLE_RADIUS_RATIO = 0.5f
private val NEW_GAME_BUTTON_ELEVATION = 10.dp
private val NEW_GAME_TEXT_SIZE = 18.sp
private val CROSSWORD_MAX_SIZE = WHEEL_MAX_SIZE
private val WHEEL_SELECTION_TEXT_SIZE = 26.sp
private val WHEEL_SELECTION_LETTER_SPACING = 4.sp
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

private data class UserSettings(
    val muted: Boolean = false,
    val maxWordLength: Int = DEFAULT_MAX_WORD_LENGTH
)

private enum class HammerMode {
    Off,
    Single,
    Caps
}

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val tonePlayer = remember { TonePlayer() }
    val letterTapSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_letter_tap, SOUND_POOL_TAP_VOLUME) }
    val bellSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_bell, SOUND_POOL_BELL_VOLUME) }
    val sideWordSample = remember(appContext) { SoundPoolSample(appContext, R.raw.sfx_side_word, SOUND_POOL_SIDE_WORD_VOLUME) }
    val soundEffects = remember(tonePlayer, letterTapSample, bellSample, sideWordSample) {
        SoundEffects(tonePlayer, letterTapSample, bellSample, sideWordSample)
    }
    var settings by remember { mutableStateOf(loadSettings(appContext)) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    DisposableEffect(tonePlayer, letterTapSample, bellSample, sideWordSample) {
        onDispose {
            tonePlayer.dispose()
            letterTapSample.release()
            bellSample.release()
            sideWordSample.release()
        }
    }
    soundEffects.muted = settings.muted
    val dictionary = remember { loadWordList { context.assets.open("words.txt") } }
    val dictionarySet = remember(dictionary) { dictionary.toHashSet() }
    val eligibleWords = remember(dictionarySet, settings.maxWordLength) {
        dictionarySet.filter { it.length in MIN_WORD_LENGTH..settings.maxWordLength }
    }
    var baseWord by remember { mutableStateOf("") }
    var letters by remember { mutableStateOf(emptyList<Char>()) }
    var grid by remember { mutableStateOf(emptyList<List<CrosswordCell>>()) }
    var crosswordWords by remember { mutableStateOf(emptyMap<String, CrosswordWord>()) }
    var missingWordsState by remember { mutableStateOf(emptyMissingWordsState()) }
    var highlightedPositions by remember { mutableStateOf(emptySet<GridPosition>()) }
    var highlightTrigger by remember { mutableStateOf(0) }
    val highlightFade = remember { Animatable(NEW_WORD_HIGHLIGHT_NONE) }
    var hammerMode by remember { mutableStateOf(HammerMode.Off) }
    var generationError by remember { mutableStateOf(false) }

    fun startNewGame() {
        highlightedPositions = emptySet()
        val result = generateCrosswordWithQuality(
            baseWords = eligibleWords,
            dictionary = dictionary
        )
        val rejectedWords = when (result) {
            is CrosswordGenerationResult.Success -> result.rejectedWords
            is CrosswordGenerationResult.Failure -> result.rejectedWords
        }
        logRejectedWords(appContext, rejectedWords)
        when (result) {
            is CrosswordGenerationResult.Success -> {
                baseWord = result.baseWord
                letters = generateLetterWheel(result.baseWord).shuffled()
                grid = result.layout.grid
                crosswordWords = result.layout.words
                missingWordsState = buildMissingWordsState(result.baseWord, dictionary, result.layout.words)
                hammerMode = HammerMode.Off
                generationError = false
            }
            is CrosswordGenerationResult.Failure -> {
                generationError = true
                appendProblemLogEntry(
                    appContext,
                    PROBLEM_LOG_TYPE_LOGIC,
                    PROBLEM_LOG_ATTEMPT_LIMIT_COMMENT
                )
                if (grid.isEmpty()) {
                    baseWord = ""
                    letters = emptyList()
                    crosswordWords = emptyMap()
                    missingWordsState = emptyMissingWordsState()
                }
            }
        }
    }

    LaunchedEffect(baseWord) {
        logSmallWordListIfNeeded(appContext, baseWord, dictionary)
    }

    LaunchedEffect(eligibleWords) {
        if (grid.isEmpty() || !eligibleWords.contains(baseWord)) {
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
    val showNewGameButton = isSolved || generationError

    Box(modifier = Modifier.fillMaxSize()) {
        AbstractBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            TopBar(
                onSettings = { showSettings = true },
                onNewGame = {
                    startNewGame()
                },
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
                onWordSelected = { selectedWord ->
                    val normalizedWord = selectedWord.uppercase()
                    val match = crosswordWords[normalizedWord]
                    val (updatedGrid, result) = applySelectedWord(selectedWord, crosswordWords, grid)
                    grid = updatedGrid
                    if ((result == WordResult.Success || result == WordResult.AlreadySolved) && match != null) {
                        highlightedPositions = match.positions
                        highlightTrigger += HIGHLIGHT_TRIGGER_STEP
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
                        maxWordLength = updated.maxWordLength.coerceIn(MIN_WORD_LENGTH, MAX_WORD_LENGTH)
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
    onSettings: () -> Unit,
    onNewGame: () -> Unit,
    onShareProblems: () -> Unit,
    onResetProblems: () -> Unit,
    onExit: () -> Unit,
    onAbout: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        CircleIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings),
            onClick = {
                menuExpanded = false
                onSettings()
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
                    text = { Text(text = stringResource(R.string.menu_new)) },
                    onClick = {
                        menuExpanded = false
                        onNewGame()
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
private fun SettingsDialog(
    current: UserSettings,
    onSave: (UserSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var muted by remember(current.muted) { mutableStateOf(current.muted) }
    var maxLength by remember(current.maxWordLength) { mutableStateOf(current.maxWordLength.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(UserSettings(muted = muted, maxWordLength = maxLength.roundToInt())) }) {
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = muted, onCheckedChange = { muted = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Mute sounds")
                }
                Column {
                    Text(text = "Maximum word length: ${maxLength.roundToInt()}")
                    Slider(
                        value = maxLength,
                        onValueChange = { maxLength = it.roundToInt().toFloat() },
                        valueRange = MIN_WORD_LENGTH.toFloat()..MAX_WORD_LENGTH.toFloat(),
                        steps = (MAX_WORD_LENGTH - MIN_WORD_LENGTH - 1).coerceAtLeast(0)
                    )
                }
            }
        }
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
                Text(text = stringResource(R.string.about_version))
                Text(text = stringResource(R.string.about_author))
                Text(text = stringResource(R.string.about_platform))
                Text(text = stringResource(R.string.about_stack))
                Text(text = stringResource(R.string.about_year))
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
    missingWordsCount: Int,
    lastMissingWord: String?,
    onShuffle: () -> Unit,
    onNewGame: () -> Unit,
    onHammerTap: () -> Unit,
    onHammerLongPress: () -> Unit,
    onSelectionStart: () -> Unit,
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    var selectedLetters by remember { mutableStateOf(emptyList<Char>()) }
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
                    letters = selectedLetters,
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
                        onSelectionStart = onSelectionStart,
                        onSelectionChanged = { selectedLetters = it },
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

@Composable
private fun SelectedLettersPreview(
    letters: List<Char>,
    modifier: Modifier = Modifier
) {
    val selectionText = letters.joinToString(separator = "")
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = selectionText,
            color = TileText,
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
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val diameter = maxWidth.coerceAtMost(maxHeight)
        val letterSize = WHEEL_LETTER_SIZE
        val density = LocalDensity.current
        val letterSizePx = with(density) { letterSize.toPx() }
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
                        down.consumeAllChanges()
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
                                change.consumeAllChanges()
                                finishSelection()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consumeAllChanges()
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
                        fontSize = 40.sp,
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
    private val sideWordSample: SoundPoolSample
) {
    var muted: Boolean = false
    private val bellBaseHz = 660.0
    private val bellStepRatio = 1.08
    private val initialTapHz = 220.0

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

    fun miss() {
        if (muted) return
        player.playTone(listOf(200.0, 150.0), durationMs = 130, volume = 0.5f)
        player.playTone(listOf(170.0, 130.0), durationMs = 130, volume = 0.5f, startDelayMs = 100)
    }

    fun alreadySolvedConfirm() {
        if (muted) return
        letterTapSample.play(SOUND_POOL_BASE_RATE)
    }

    fun shortConfirm() {
        if (muted) return
        player.playTone(listOf(220.0), durationMs = 100, volume = 0.25f)
    }
}

private fun loadSettings(context: Context): UserSettings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val maxLength = prefs.getInt(KEY_MAX_WORD_LENGTH, DEFAULT_MAX_WORD_LENGTH)
        .coerceIn(MIN_WORD_LENGTH, MAX_WORD_LENGTH)
    val muted = prefs.getBoolean(KEY_MUTE, false)
    return UserSettings(muted = muted, maxWordLength = maxLength)
}

private fun logSmallWordListIfNeeded(context: Context, baseWord: String, dictionary: List<String>) {
    if (baseWord.isBlank()) {
        return
    }
    val matchingWordCount = buildMiniDictionary(baseWord, dictionary)
        .count { it.length >= MIN_CROSSWORD_WORD_LENGTH }
    if (matchingWordCount < MIN_PROBLEM_LOG_WORD_COUNT) {
        val description = "$baseWord: could only find $matchingWordCount words for crossword."
        appendProblemLogEntry(context, PROBLEM_LOG_TYPE_WORD, description)
    }
}

private fun logRejectedWords(context: Context, rejectedWords: List<String>) {
    for (word in rejectedWords.distinct()) {
        if (word.isBlank()) {
            continue
        }
        val description = buildRejectedWordDescription(word)
        appendProblemLogEntryIfMissing(context, PROBLEM_LOG_TYPE_WORD, description)
    }
}

private fun buildRejectedWordDescription(baseWord: String): String {
    return "$baseWord: crossword word count below minimum of $MIN_CROSSWORD_WORD_COUNT."
}

private fun saveSettings(context: Context, settings: UserSettings) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_MUTE, settings.muted)
        .putInt(KEY_MAX_WORD_LENGTH, settings.maxWordLength.coerceIn(MIN_WORD_LENGTH, MAX_WORD_LENGTH))
        .apply()
}
