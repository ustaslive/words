package com.ustas.words

import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ustas.words.ui.theme.IconBase
import com.ustas.words.ui.theme.LightGreen
import com.ustas.words.ui.theme.MidGreen
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

private data class UserSettings(
    val muted: Boolean = false,
    val maxWordLength: Int = DEFAULT_MAX_WORD_LENGTH
)

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val tonePlayer = remember { TonePlayer() }
    val soundEffects = remember { SoundEffects(tonePlayer) }
    var settings by remember { mutableStateOf(loadSettings(appContext)) }
    var showSettings by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { tonePlayer.dispose() }
    }
    soundEffects.muted = settings.muted
    val dictionary = remember { loadWordList { context.assets.open("words.txt") } }
    val dictionarySet = remember(dictionary) { dictionary.toHashSet() }
    val eligibleWords = remember(dictionarySet, settings.maxWordLength) {
        dictionarySet.filter { it.length in MIN_WORD_LENGTH..settings.maxWordLength }
    }
    var baseWord by remember { mutableStateOf(pickRandomBaseWord(eligibleWords)) }
    var letters by remember { mutableStateOf(generateLetterWheel(baseWord).shuffled()) }
    var grid by remember { mutableStateOf(generateCrosswordGrid(baseWord)) }
    var hammerArmed by remember { mutableStateOf(false) }
    LaunchedEffect(eligibleWords) {
        if (!eligibleWords.contains(baseWord) || baseWord.length > settings.maxWordLength) {
            val newWord = pickRandomBaseWord(eligibleWords)
            baseWord = newWord
            letters = generateLetterWheel(newWord).shuffled()
            grid = generateCrosswordGrid(newWord)
            hammerArmed = false
        }
    }
    val crosswordWords = remember(baseWord) { generateCrosswordWords(baseWord).associateBy { it.word } }
    val isSolved = grid.all { row -> row.all { cell -> !cell.isActive || cell.isRevealed } }

    Box(modifier = Modifier.fillMaxSize()) {
        AbstractBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            TopBar(
                onSettings = { showSettings = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            CrosswordSection(
                grid = grid,
                hammerArmed = hammerArmed,
                isSolved = isSolved,
                onNewGame = {
                    val newWord = pickRandomBaseWord(eligibleWords)
                    baseWord = newWord
                    grid = generateCrosswordGrid(newWord)
                    letters = generateLetterWheel(newWord).shuffled()
                    hammerArmed = false
                },
                onCellTap = { rowIndex, colIndex ->
                    val cell = grid[rowIndex][colIndex]
                    if (hammerArmed && cell.isActive && !cell.isRevealed) {
                        grid = grid.mapIndexed { row, rowCells ->
                            rowCells.mapIndexed { col, item ->
                                if (row == rowIndex && col == colIndex) {
                                    item.copy(isRevealed = true)
                                } else {
                                    item
                                }
                            }
                        }
                        hammerArmed = false
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LetterWheelSection(
                letters = letters,
                hammerArmed = hammerArmed,
                onShuffle = { letters = letters.shuffled() },
                onHammer = { hammerArmed = !hammerArmed },
                onWordSelected = { selectedWord ->
                    val (updatedGrid, result) = applySelectedWord(selectedWord, crosswordWords, grid)
                    grid = updatedGrid
                    result
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
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        CircleIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings),
            onClick = onSettings
        )
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
private fun CrosswordSection(
    grid: List<List<CrosswordCell>>,
    hammerArmed: Boolean,
    isSolved: Boolean,
    onNewGame: () -> Unit,
    onCellTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CrosswordGrid(
            grid = grid,
            onCellTap = onCellTap,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSolved) {
            CompletionBanner(
                onNewGame = onNewGame,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (hammerArmed && !isSolved) {
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
private fun CompletionBanner(
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WheelBackground,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "excellent",
                color = WheelLetter,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Button(onClick = onNewGame) {
                Text(text = "New game")
            }
        }
    }
}

@Composable
private fun CrosswordGrid(
    grid: List<List<CrosswordCell>>,
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
        val gridWidth = maxWidth.coerceAtMost(320.dp)
        val cellSpacing = 4.dp
        val cellSize = (gridWidth - cellSpacing * (columns - 1)) / columns

        Column(modifier = Modifier.width(gridWidth)) {
            for (rowIndex in 0 until rows) {
                Row {
                    for (colIndex in 0 until columns) {
                        val cell = grid[rowIndex][colIndex]
                        if (cell.isActive) {
                            CrosswordCellItem(
                                cell = cell,
                                size = cellSize,
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
    onClick: () -> Unit
) {
    val background = if (cell.isRevealed) TileColor else TileHiddenColor
    val letterSize = with(LocalDensity.current) { (size * 0.55f).toSp() }
    Box(
        modifier = Modifier
            .size(size)
            .background(background, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (cell.isRevealed && cell.letter != null) {
            Text(
                text = cell.letter.toString(),
                color = TileText,
                fontWeight = FontWeight.Bold,
                fontSize = letterSize
            )
        }
    }
}

@Composable
private fun LetterWheelSection(
    letters: List<Char>,
    hammerArmed: Boolean,
    onShuffle: () -> Unit,
    onHammer: () -> Unit,
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wheelSize = maxWidth.coerceAtMost(320.dp) * 0.8f

        @Suppress("DEPRECATION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelSize + 24.dp)
        ) {
            LetterWheel(
                letters = letters,
                onShuffle = onShuffle,
                onWordSelected = onWordSelected,
                soundEffects = soundEffects,
                modifier = Modifier
                    .size(wheelSize)
                    .align(Alignment.Center)
            )
            HammerButton(
                armed = hammerArmed,
                onClick = onHammer,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun LetterWheel(
    letters: List<Char>,
    onShuffle: () -> Unit,
    onWordSelected: (String) -> WordResult,
    soundEffects: SoundEffects,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val diameter = maxWidth.coerceAtMost(maxHeight)
        val letterSize = 48.dp
        val density = LocalDensity.current
        val letterSizePx = with(density) { letterSize.toPx() }
        val radiusPx = with(density) { (diameter / 2 - letterSize * 0.7f).toPx() }
        val centerPx = with(density) { (diameter / 2).toPx() }
        val lineStrokePx = with(density) { 6.dp.toPx() }
        var selectedIndices by remember { mutableStateOf(listOf<Int>()) }
        var dragPosition by remember { mutableStateOf<Offset?>(null) }
        var lastToneFreq by remember { mutableStateOf<Double?>(null) }
        val onWordSelectedState by rememberUpdatedState(onWordSelected)
        val soundEffectsState by rememberUpdatedState(soundEffects)
        val highlightColor = TileColor
        val hitRadius = letterSizePx * 0.55f
        val hitRadiusSq = hitRadius * hitRadius
        val centers = remember(letters, radiusPx, centerPx) {
            letters.mapIndexed { index, _ ->
                val angle = index.toDouble() / letters.size * 2.0 * PI - PI / 2
                Offset(
                    x = centerPx + (radiusPx * cos(angle)).toFloat(),
                    y = centerPx + (radiusPx * sin(angle)).toFloat()
                )
            }
        }

        LaunchedEffect(letters) {
            selectedIndices = emptyList()
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
                selectedIndices = newSelection
                val freq = soundEffectsState.letterBell(toneStep(newSelection.size))
                lastToneFreq = freq
                return
            }
            if (nextIndex == last) {
                return
            }
            if (current.size >= 2 && nextIndex == current[current.size - 2]) {
                val newSelection = current.dropLast(1)
                selectedIndices = newSelection
                lastToneFreq = if (newSelection.isNotEmpty()) {
                    soundEffectsState.bellFrequency(toneStep(newSelection.size))
                } else {
                    null
                }
                return
            }
            if (!current.contains(nextIndex)) {
                val newSelection = current + nextIndex
                selectedIndices = newSelection
                val freq = soundEffectsState.letterBell(toneStep(newSelection.size))
                lastToneFreq = freq
            }
        }

        fun finishSelection() {
            val selectionSize = selectedIndices.size
            if (selectionSize >= 4) {
                val selectedWord = buildString {
                    selectedIndices.forEach { index ->
                        append(letters[index].uppercaseChar())
                    }
                }
                when (onWordSelectedState(selectedWord)) {
                    WordResult.Success -> soundEffectsState.successChord(lastToneFreq)
                    WordResult.AlreadySolved -> soundEffectsState.shortConfirm()
                    WordResult.NotFound -> soundEffectsState.miss()
                }
            } else if (selectionSize > 0) {
                soundEffectsState.shortConfirm()
            }
            selectedIndices = emptyList()
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
                        selectedIndices = emptyList()
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

@Composable
private fun HammerButton(
    armed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (armed) AccentOrange else IconBase
    Surface(
        color = background,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier.size(56.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.Gavel,
                contentDescription = stringResource(R.string.hammer),
                tint = Color.White
            )
        }
    }
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

private class SoundEffects(private val player: TonePlayer) {
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
            player.playTone(listOf(freq), durationMs = 80, volume = 0.6f)
        }
        return freq
    }

    fun bellFrequency(stepIndex: Int): Double {
        return bellBaseHz * bellStepRatio.pow(stepIndex.toDouble())
    }

    fun successChord(rootFreq: Double?) {
        if (muted) return
        val root = (rootFreq ?: bellBaseHz) * 0.6
        val partials = listOf(
            root,
            root * 2.0,
            root * 3.0,
            root * 4.0,
            root * 5.0,
            root * 6.0
        )
        val weights = listOf(1.0, 0.55, 0.45, 0.3, 0.22, 0.18)
        player.playTone(partials, durationMs = 240, volume = 0.6f, weights = weights)
        player.playTone(partials, durationMs = 420, volume = 0.64f, startDelayMs = 120, weights = weights)
    }

    fun miss() {
        if (muted) return
        player.playTone(listOf(200.0, 150.0), durationMs = 130, volume = 0.5f)
        player.playTone(listOf(170.0, 130.0), durationMs = 130, volume = 0.5f, startDelayMs = 100)
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

private fun saveSettings(context: Context, settings: UserSettings) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_MUTE, settings.muted)
        .putInt(KEY_MAX_WORD_LENGTH, settings.maxWordLength.coerceIn(MIN_WORD_LENGTH, MAX_WORD_LENGTH))
        .apply()
}
