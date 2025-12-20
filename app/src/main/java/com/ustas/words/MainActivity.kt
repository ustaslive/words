package com.ustas.words

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private data class CrosswordCell(
    val letter: Char?,
    val isActive: Boolean,
    val isRevealed: Boolean
)

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    var letters by remember { mutableStateOf(generateLetterWheel().shuffled()) }
    var grid by remember { mutableStateOf(generateCrosswordGrid()) }
    var hammerArmed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AbstractBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            TopBar(
                onSettings = {
                    Toast.makeText(context, "Settings coming soon", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            CrosswordSection(
                grid = grid,
                hammerArmed = hammerArmed,
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
                modifier = Modifier.weight(0.9f)
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
private fun CrosswordSection(
    grid: List<List<CrosswordCell>>,
    hammerArmed: Boolean,
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
        if (hammerArmed) {
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
                fontSize = 16.sp
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wheelSize = maxWidth.coerceAtMost(320.dp) * 0.8f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelSize + 24.dp)
        ) {
            LetterWheel(
                letters = letters,
                onShuffle = onShuffle,
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
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val diameter = maxWidth.coerceAtMost(maxHeight)
        val letterSize = 48.dp
        val density = LocalDensity.current
        val letterSizePx = with(density) { letterSize.toPx() }
        val radiusPx = with(density) { (diameter / 2 - letterSize * 0.7f).toPx() }
        val centerPx = with(density) { (diameter / 2).toPx() }

        Box(
            modifier = Modifier
                .size(diameter)
                .background(WheelBackground, CircleShape)
                .align(Alignment.Center)
        ) {
            letters.forEachIndexed { index, letter ->
                val angle = index.toDouble() / letters.size * 2.0 * PI - PI / 2
                val x = centerPx + radiusPx * cos(angle) - letterSizePx / 2
                val y = centerPx + radiusPx * sin(angle) - letterSizePx / 2
                Box(
                    modifier = Modifier
                        .size(letterSize)
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.uppercaseChar().toString(),
                        color = WheelLetter,
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

// Dummy generators; replace with real logic later.
private fun generateLetterWheel(): List<Char> {
    return "culture".uppercase().toList()
}

private fun generateCrosswordGrid(): List<List<CrosswordCell>> {
    val rows = listOf(
        ".........",
        "...CUTE..",
        "..CUE.C..",
        "..C...U..",
        ".CULTURE.",
        ".LT...E..",
        ".UCLUE...",
        ".E.TRUE..",
        "........."
    )

    return rows.map { row ->
        row.map { symbol ->
            when {
                symbol == '.' -> CrosswordCell(null, isActive = false, isRevealed = false)
                else -> CrosswordCell(symbol, isActive = true, isRevealed = false)
            }
        }
    }
}
