package com.example.punktygrystolikowe

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

// ── Snapshot do cofania ruchów ─────────────────────────────────────────────────
data class GameSnapshot(
    val playerIndex: Int,
    val round: Int,
    val noMoveCounter: Int,
    val minusPointsCounter: Int,
    val pointsSnapshot: Map<String, Int>,
    val historySize: Int,
    val actionDescription: String   // np. "Jan: +15 pkt" lub "Brak ruchu Ania: −5 pkt"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameApp()
        }
    }
}

@Composable
fun GameApp() {
    var playerNames by remember { mutableStateOf(listOf<String>()) }
    var currentScreen by remember { mutableStateOf("start") }
    var points by remember { mutableStateOf(playerNames.associateWith { 0 }) }

    when (currentScreen) {
        "start" -> StartScreen(
            onPlayersConfirmed = { names ->
                playerNames = names
                points = names.associateWith { 0 }.toMutableMap()
                currentScreen = "game"
            }
        )
        "game" -> MainGameScreen(
            playerNames = playerNames,
            points = points,
            onShowWinner = { currentScreen = "winner_screen" },
            onUpdatePoints = { player, score ->
                points = points.toMutableMap().apply {
                    this[player] = (this[player] ?: 0) + score
                }
            },
            onRestorePoints = { snapshot ->
                points = snapshot.toMutableMap()
            }
        )
        "winner_screen" -> WinnerScreen(
            playerScores = points,
            onGoBack = { currentScreen = "start" }
        )
    }
}

// ── Logo Triominos ─────────────────────────────────────────────────────────────
// Cztery trójkątne kafle ułożone w romb, jak w prawdziwym Triominos.
// Każdy kafel: wypełnienie kremowe, złota ramka, liczby przy rogach.
@Composable
fun TriominosLogo(modifier: Modifier = Modifier, scale: Float = 1f) {
    val textMeasurer = rememberTextMeasurer()

    // Kolory kafli
    val tile1 = Color(0xFFFFF3CD)   // ciepły krem  – lewy-górny
    val tile2 = Color(0xFFFFE4C4)   // biszkopt     – prawy-górny
    val tile3 = Color(0xFFFFD6A5)   // morelowy     – lewy-dolny
    val tile4 = Color(0xFFFFF8E7)   // ivory        – prawy-dolny
    val border = Color(0xFFB8860B)  // ciemne złoto
    val numColor = Color(0xFF2D1B69) // ciemnofioletowy (pasuje do tła)
    val dotColor = Color(0xFFE63946) // czerwona kropka w centrum każdego kafla

    Canvas(modifier = modifier.size((160 * scale).dp)) {
        val S = size.width * 0.46f          // długość boku trójkąta
        val H = S * sqrt(3f) / 2f          // wysokość trójkąta równobocznego

        // Centrum canvasu
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Offset: romb z 4 kafli.
        //   Kafel A (↑) — lewy-górny
        //   Kafel B (↑) — prawy-górny
        //   Kafel C (↓) — lewy-dolny
        //   Kafel D (↓) — prawy-dolny
        //
        //     A  B
        //     C  D

        // Pomocnicze: rysuj trójkąt skierowany w górę
        fun upTriangle(ox: Float, oy: Float): Path = Path().apply {
            moveTo(ox + S / 2f, oy)          // szczyt
            lineTo(ox + S, oy + H)           // prawy-dół
            lineTo(ox, oy + H)               // lewy-dół
            close()
        }

        // Trójkąt skierowany w dół
        fun downTriangle(ox: Float, oy: Float): Path = Path().apply {
            moveTo(ox, oy)                   // lewy-góra
            lineTo(ox + S, oy)              // prawy-góra
            lineTo(ox + S / 2f, oy + H)    // środek-dół
            close()
        }

        val strokeW = (3f * scale).coerceAtLeast(2f)

        // ── Pozycje kafli ──────────────────────────────────────────────────
        // Kafle przylegają do siebie bokiem:
        //   A(↑) + B(↑) w górnym rzędzie, C(↓) + D(↓) pod nimi
        //   Między ↑ a ↓ kafle dzielą dolną krawędź / górną krawędź

        val row1Y = cy - H           // górna krawędź górnych kafli
        val row2Y = cy               // górna krawędź dolnych kafli (= dół górnych)

        // Kafle górne (↑)
        val axA = cx - S             // Kafel A: lewY offset x
        val axB = cx                 // Kafel B: prawy offset x

        // Kafle dolne (↓) – dopasowane do dołu górnych
        val axC = cx - S
        val axD = cx

        val pathA = upTriangle(axA, row1Y)
        val pathB = upTriangle(axB, row1Y)
        val pathC = downTriangle(axC, row2Y)
        val pathD = downTriangle(axD, row2Y)

        // ── Rysowanie kafli ────────────────────────────────────────────────
        listOf(pathA to tile1, pathB to tile2, pathC to tile3, pathD to tile4)
            .forEach { (path, fill) ->
                drawPath(path, fill)
                drawPath(path, border, style = Stroke(width = strokeW))
            }

        // ── Czerwone kropki centrum każdego kafla ──────────────────────────
        val dotR = S * 0.06f
        // Centroidy trójkątów
        fun upCentroid(ox: Float, oy: Float) = Offset(ox + S / 2f, oy + H * 2f / 3f)
        fun downCentroid(ox: Float, oy: Float) = Offset(ox + S / 2f, oy + H / 3f)

        listOf(
            upCentroid(axA, row1Y),
            upCentroid(axB, row1Y),
            downCentroid(axC, row2Y),
            downCentroid(axD, row2Y)
        ).forEach { drawCircle(dotColor, dotR, it) }

        // ── Liczby przy rogach kafli ───────────────────────────────────────
        val numSp = (13f * scale).sp
        val numStyle = TextStyle(
            color = numColor,
            fontSize = numSp,
            fontWeight = FontWeight.ExtraBold
        )
        val pad = S * 0.08f

        fun drawNum(text: String, x: Float, y: Float) {
            val measured = textMeasurer.measure(text, numStyle)
            drawText(
                measured,
                topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f)
            )
        }

        // Kafel A (↑): 3 rogi → szczyt, lewy-dół, prawy-dół
        drawNum("4", axA + S / 2f, row1Y + pad * 1.2f)
        drawNum("2", axA + pad, row1Y + H - pad)
        drawNum("3", axA + S - pad, row1Y + H - pad)

        // Kafel B (↑)
        drawNum("3", axB + S / 2f, row1Y + pad * 1.2f)
        drawNum("3", axB + pad, row1Y + H - pad)
        drawNum("5", axB + S - pad, row1Y + H - pad)

        // Kafel C (↓): lewy-góra, prawy-góra, środek-dół
        drawNum("2", axC + pad, row2Y + pad * 1.2f)
        drawNum("3", axC + S - pad, row2Y + pad * 1.2f)
        drawNum("1", axC + S / 2f, row2Y + H - pad)

        // Kafel D (↓)
        drawNum("3", axD + pad, row2Y + pad * 1.2f)
        drawNum("5", axD + S - pad, row2Y + pad * 1.2f)
        drawNum("4", axD + S / 2f, row2Y + H - pad)
    }
}

@Composable
fun StartScreen(onPlayersConfirmed: (List<String>) -> Unit) {
    var playerName by remember { mutableStateOf("") }
    val playerNames = remember { mutableStateListOf<String>() }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D0D2B), Color(0xFF1A1A4E), Color(0xFF2D1B69))
    )

    // Animacja wejścia
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Subtelna pulsująca ikona
    val infiniteTransition = rememberInfiniteTransition(label = "start")
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "icon"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(500, easing = EaseOutCubic)
            )
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Nagłówek ──────────────────────────────────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                TriominosLogo(
                    modifier = Modifier.scale(iconPulse),
                    scale = 1f
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Triominos",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFBE0B),
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Dodaj graczy i rozpocznij rozgrywkę",
                    fontSize = 14.sp,
                    color = Color(0xFF7B6FD8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Karta dodawania gracza ─────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x33FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Nowy gracz",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B6FD8),
                            letterSpacing = 3.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            keyboardOptions = KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text
                            ),
                            label = { Text("Imię gracza", color = Color(0xFF7B6FD8)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.textFieldColors(
                                textColor = Color.White,
                                backgroundColor = Color(0x22FFFFFF),
                                cursorColor = Color(0xFF7209B7),
                                focusedIndicatorColor = Color(0xFF7209B7),
                                unfocusedIndicatorColor = Color(0x557B6FD8),
                                focusedLabelColor = Color(0xFF7209B7),
                                unfocusedLabelColor = Color(0xFF7B6FD8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (playerName.isNotEmpty()) {
                                    playerNames.add(playerName)
                                    playerName = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF3A86FF),
                                disabledBackgroundColor = Color(0x443A86FF)
                            ),
                            enabled = playerName.isNotEmpty()
                        ) {
                            Text(
                                text = "+ Dodaj gracza",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ── Lista graczy ───────────────────────────────────────────────
                if (playerNames.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = Color(0x22FFFFFF),
                        elevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Gracze (${playerNames.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7B6FD8),
                                letterSpacing = 3.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            playerNames.forEachIndexed { index, name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x1AFFFFFF))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        color = Color(0xFF7B6FD8),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (index < playerNames.lastIndex)
                                    Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onPlayersConfirmed(playerNames) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7209B7)),
                        elevation = ButtonDefaults.elevation(8.dp)
                    ) {
                        Text(
                            text = "🎮  Rozpocznij grę",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainGameScreen(
    playerNames: List<String>,
    points: Map<String, Int>,
    onUpdatePoints: (String, Int) -> Unit,
    onRestorePoints: (Map<String, Int>) -> Unit,
    onShowWinner: () -> Unit
) {
    AppWithBackBlocked()
    GameScreen(
        playerNames = playerNames,
        points = points,
        onUpdatePoints = onUpdatePoints,
        onRestorePoints = onRestorePoints,
        onShowWinner = { onShowWinner() }
    )
}
//@Preview(showBackground = true)
@Composable
fun GameScreen(
    playerNames: List<String>,
    points: Map<String, Int>,
    onUpdatePoints: (String, Int) -> Unit,
    onRestorePoints: (Map<String, Int>) -> Unit,
    onShowWinner: () -> Unit
) {
    var currentPlayerIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf("") }
    var round by remember { mutableIntStateOf(1) }
    val pointHistory = remember { mutableStateListOf<String>() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val maxPoints = points.values.maxOrNull() ?: 0
    val playerWithMaxPoints = points.filter { it.value == maxPoints }.keys.firstOrNull()
    var showInfoBox by remember { mutableStateOf(true) }
    var noMoveCounter by remember { mutableIntStateOf(4) }
    var minusPointsCounter by remember { mutableIntStateOf(0) }
    var maxMinusPointsDialog by remember { mutableStateOf(false) }
    val resetFlow = remember { MutableSharedFlow<Unit>() }
    val coroutineScope = rememberCoroutineScope()

    // ── Stos cofania ───────────────────────────────────────────────────────────
    val undoStack = remember { ArrayDeque<GameSnapshot>() }
    var undoMessage by remember { mutableStateOf<String?>(null) }

    // Auto-ukryj komunikat cofnięcia po 2.5s
    LaunchedEffect(undoMessage) {
        if (undoMessage != null) {
            delay(2500)
            undoMessage = null
        }
    }

    // Snapshot stanu PRZED wykonaniem akcji
    fun snapshot(actionDesc: String): GameSnapshot = GameSnapshot(
        playerIndex       = currentPlayerIndex,
        round             = round,
        noMoveCounter     = noMoveCounter,
        minusPointsCounter = minusPointsCounter,
        pointsSnapshot    = points.toMap(),
        historySize       = pointHistory.size,
        actionDescription = actionDesc
    )

    // Przywróć stan ze snapshotu
    fun restoreSnapshot(s: GameSnapshot) {
        currentPlayerIndex  = s.playerIndex
        round               = s.round
        noMoveCounter       = s.noMoveCounter
        minusPointsCounter  = s.minusPointsCounter
        // Przytnij historię do rozmiaru sprzed akcji
        while (pointHistory.size > s.historySize) pointHistory.removeLast()
        // Przywróć punkty w rodzicu
        onRestorePoints(s.pointsSnapshot)
        undoMessage = "↩  Cofnięto: ${s.actionDescription}"
        score = ""
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D0D2B), Color(0xFF1A1A4E), Color(0xFF2D1B69))
    )

    fun nextRound() {
        currentPlayerIndex = (currentPlayerIndex + 1) % playerNames.size
        if (currentPlayerIndex == 0) round += 1
        minusPointsCounter = 0
        noMoveCounter = 4
        score = ""
    }

    ModalDrawer(
        drawerState = drawerState,
        drawerBackgroundColor = Color(0xFF1A1A4E),
        drawerContent = {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Historia",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF7B6FD8), letterSpacing = 4.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (pointHistory.isEmpty()) {
                    Text(
                        text = "Brak ruchów do wyświetlenia",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
                pointHistory.asReversed().forEachIndexed { index, record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (index == 0) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = record, fontSize = 15.sp, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x447B6FD8))
                ) {
                    Text("Zamknij", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(gradientBg)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Header: runda + timer + cofnij ─────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x33FFFFFF),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Runda
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🎯", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Runda $round",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                        // Timer
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("⏱ ", fontSize = 13.sp)
                            TimerWithReset(resetFlow)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // ── Przycisk COFNIJ ────────────────────────────────────
                        Button(
                            onClick = {
                                val s = undoStack.removeLastOrNull()
                                if (s != null) restoreSnapshot(s)
                            },
                            enabled = undoStack.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFFFF6B6B),
                                disabledBackgroundColor = Color(0x33FFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            elevation = ButtonDefaults.elevation(0.dp)
                        ) {
                            Text(
                                text = "↩ Cofnij",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (undoStack.isNotEmpty()) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }
                }

                // ── Baner sukcesu cofnięcia ────────────────────────────────────
                AnimatedVisibility(
                    visible = undoMessage != null,
                    enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { -it }),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = Color(0xFF06D6A0),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = undoMessage ?: "",
                                color = Color(0xFF0D0D2B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // ── Aktywny gracz ──────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x44FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Teraz gra",
                            fontSize = 12.sp, color = Color(0xFF7B6FD8),
                            fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playerNames[currentPlayerIndex],
                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFBE0B)
                        )
                        if (minusPointsCounter > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Brak ruchu: ${"✕ ".repeat(minusPointsCounter).trim()}",
                                fontSize = 13.sp, color = Color(0xFFFF6B6B),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Wprowadzanie punktów ───────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x22FFFFFF),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Brak ruchu
                        Button(
                            onClick = {
                                if (noMoveCounter > 0) {
                                    val scoreValue = if (noMoveCounter > 1) -5 else -10
                                    val currentPlayer = playerNames[currentPlayerIndex]
                                    val penaltyLabel = if (scoreValue == -5) "−5" else "−10"
                                    val advancesPlayer = (scoreValue == -10)

                                    // Snapshot PRZED akcją
                                    undoStack.addLast(
                                        snapshot("Brak ruchu $currentPlayer: $penaltyLabel pkt")
                                    )

                                    onUpdatePoints(currentPlayer, scoreValue)
                                    pointHistory.add(
                                        "${pointHistory.size + 1}. $currentPlayer: $scoreValue pkt (brak ruchu)"
                                    )
                                    score = ""
                                    minusPointsCounter++
                                    noMoveCounter--
                                    if (advancesPlayer) nextRound()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (noMoveCounter > 0) Color(0xFFE63946) else Color(0x44FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "Brak\nruchu",
                                color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            )
                        }
                        // Input
                        TextField(
                            value = score,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("\\d*"))) score = newValue
                            },
                            label = { Text("Punkty", color = Color(0xFF7B6FD8)) },
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.textFieldColors(
                                textColor = Color.White,
                                backgroundColor = Color(0x22FFFFFF),
                                cursorColor = Color(0xFF7209B7),
                                focusedIndicatorColor = Color(0xFF7209B7),
                                unfocusedIndicatorColor = Color(0x55FFFFFF),
                                focusedLabelColor = Color(0xFF7209B7),
                                unfocusedLabelColor = Color(0xFF7B6FD8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        // Zatwierdź
                        Button(
                            onClick = {
                                if (score.isNotEmpty()) {
                                    val scoreValue = score.toIntOrNull() ?: 0
                                    val currentPlayer = playerNames[currentPlayerIndex]

                                    // Snapshot PRZED akcją
                                    undoStack.addLast(
                                        snapshot("$currentPlayer: +$scoreValue pkt")
                                    )

                                    onUpdatePoints(currentPlayer, scoreValue)
                                    pointHistory.add(
                                        "${pointHistory.size + 1}. $currentPlayer: +$scoreValue pkt"
                                    )
                                    nextRound()
                                    coroutineScope.launch { resetFlow.emit(Unit) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (score.isNotEmpty()) Color(0xFF06D6A0) else Color(0x44FFFFFF),
                                disabledBackgroundColor = Color(0x44FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp),
                            enabled = score.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "OK\n✓", color = Color.White, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ── Podpowiedź ─────────────────────────────────────────────────
                if (showInfoBox) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = Color(0x22FFFFFF),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡  Wpisz punkty i zatwierdź OK. " +
                                        "\"Brak ruchu\" nalicza karę (−5 lub −10 pkt). " +
                                        "\"↩ Cofnij\" w nagłówku cofa ostatni ruch.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showInfoBox = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Zamknij",
                                    tint = Color(0xFF7B6FD8)
                                )
                            }
                        }
                    }
                }

                // ── Tabela aktualnych punktów ──────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color(0x22FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Aktualne punkty",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B6FD8), letterSpacing = 3.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val sortedByPoints = playerNames.sortedByDescending { points[it] ?: 0 }
                        sortedByPoints.forEachIndexed { index, player ->
                            val isLeader = player == playerWithMaxPoints
                            val isActive = player == playerNames[currentPlayerIndex]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isLeader -> Color(0x33FFBE0B)
                                            isActive -> Color(0x1AFFFFFF)
                                            else     -> Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isLeader) "👑" else "${index + 1}.",
                                    fontSize = 16.sp, modifier = Modifier.width(30.dp)
                                )
                                Text(
                                    text = player + if (isActive) " ←" else "",
                                    color = when {
                                        isLeader -> Color(0xFFFFBE0B)
                                        isActive -> Color.White
                                        else     -> Color.White.copy(alpha = 0.8f)
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = if (isLeader || isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${points[player] ?: 0} pkt",
                                    color = if (isLeader) Color(0xFFFFBE0B) else Color.White.copy(alpha = 0.8f),
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < sortedByPoints.lastIndex)
                                Divider(
                                    color = Color.White.copy(alpha = 0.06f),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Przyciski dolne ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x447B6FD8))
                    ) {
                        Text("📜 Historia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onShowWinner() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7209B7)),
                        elevation = ButtonDefaults.elevation(6.dp)
                    ) {
                        Text("🏁 Zakończ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // ── Dialog max minus ───────────────────────────────────────────────
            if (maxMinusPointsDialog) {
                AlertDialog(
                    onDismissRequest = { maxMinusPointsDialog = false },
                    backgroundColor = Color(0xFF1A1A4E),
                    title = { Text("Uwaga", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Można maksymalnie zatwierdzić trzykrotnie −5 pkt, a potem raz −10 pkt.",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { maxMinusPointsDialog = false },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7209B7)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("OK", color = Color.White) }
                    },
                    dismissButton = {
                        Button(
                            onClick = { maxMinusPointsDialog = false },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x447B6FD8)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Zamknij", color = Color.White) }
                    },
                    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                )
            }
        }
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun WinnerScreen(playerScores: Map<String, Int>, onGoBack: () -> Unit) {
    val sortedPlayers = playerScores.entries.sortedByDescending { it.value }
    val topPlayer = sortedPlayers.first()

    // ── Tło: gradient ──────────────────────────────────────────────────────────
    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D2B),
            Color(0xFF1A1A4E),
            Color(0xFF2D1B69)
        )
    )

    // ── Konfetti ───────────────────────────────────────────────────────────────
    val confettiCount = 120
    val confettiX = remember { List(confettiCount) { Random.nextFloat() } }
    val confettiY = remember { List(confettiCount) { Random.nextFloat() } }
    val confettiRotations = remember { List(confettiCount) { Random.nextFloat() * 360f } }
    val confettiColors = remember {
        List(confettiCount) {
            listOf(
                Color(0xFFF72585), Color(0xFF7209B7), Color(0xFF3A86FF),
                Color(0xFFFFBE0B), Color(0xFF06D6A0), Color(0xFFFF6B6B)
            ).random()
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val confettiFall by infiniteTransition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "fall"
    )
    val confettiSway by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ), label = "sway"
    )

    // ── Animacja wejścia ───────────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    val cardScale = remember { Animatable(0.7f) }
    val trophyScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        visible = true
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = EaseOutBack)
        )
        trophyScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = EaseOutBack)
        )
    }

    // ── Pulsowanie trofeum ─────────────────────────────────────────────────────
    val trophyPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // ── Blask za trofeum ───────────────────────────────────────────────────────
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
    ) {
        // ── Konfetti canvas ───────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until confettiCount) {
                val x = (confettiX[i] + confettiSway * 0.03f * (i % 3 - 1)) * size.width
                val y = ((confettiY[i] + confettiFall) % 1.25f) * size.height
                val rot = confettiRotations[i] + confettiFall * 360f
                drawRect(
                    color = confettiColors[i],
                    topLeft = Offset(x, y),
                    size = Size(9f, 18f),
                    alpha = 0.85f
                )
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(500, easing = EaseOutCubic)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ── Nagłówek ──────────────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "KONIEC GRY",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B6FD8),
                        letterSpacing = 6.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wyniki końcowe",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // ── Karta zwycięzcy ───────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(cardScale.value),
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = Color(0x33FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Blask + trofeum
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                drawCircle(
                                    color = Color(0xFFFFBE0B).copy(alpha = glowAlpha),
                                    radius = size.minDimension / 2
                                )
                            }
                            Text(
                                text = "🏆",
                                fontSize = 64.sp,
                                modifier = Modifier.scale(trophyPulse * trophyScale.value)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = topPlayer.key,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFBE0B),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${topPlayer.value} punktów",
                            fontSize = 18.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // ── Tabela wyników ────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    backgroundColor = Color(0x22FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tabela wyników",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B6FD8),
                            letterSpacing = 3.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        sortedPlayers.forEachIndexed { index, player ->
                            val medal = when (index) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "  ${index + 1}."
                            }
                            val rowBg = if (index == 0)
                                Color(0x33FFBE0B)
                            else
                                Color.Transparent
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(rowBg)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = medal,
                                    fontSize = 20.sp,
                                    modifier = Modifier.width(40.dp)
                                )
                                Text(
                                    text = player.key,
                                    fontSize = 17.sp,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == 0) Color(0xFFFFBE0B) else Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${player.value} pkt",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == 0) Color(0xFFFFBE0B) else Color.White.copy(alpha = 0.75f)
                                )
                            }
                            if (index < sortedPlayers.lastIndex) {
                                Divider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // ── Przycisk nowej gry ────────────────────────────────────────
                Button(
                    onClick = { onGoBack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF7209B7)
                    ),
                    elevation = ButtonDefaults.elevation(8.dp)
                ) {
                    Text(
                        text = "🎮  Nowa gra",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GameApp()
}

@Composable
fun AppWithBackBlocked() {
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {},
            backgroundColor = Color(0xFF1A1A4E),
            title = { Text(text = "Uwaga", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Nie ma wyjścia z apki :)", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = { showExitDialog = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7209B7)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("OK", color = Color.White) }
            },
        )
    }
}

@Composable
fun TimerWithReset(resetFlow: MutableSharedFlow<Unit>) {
    val context = LocalContext.current
    var elapsedTime by remember { mutableStateOf(0L) } // Przechowuje czas w sekundach
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var lastStartTime = System.currentTimeMillis()
        coroutineScope.launch {
            resetFlow.collectLatest {
                // Resetuj timer po otrzymaniu sygnału
                elapsedTime = 0
                lastStartTime = System.currentTimeMillis()
            }
        }

        while (true) {
            delay(1000L) // Odświeżanie co sekundę
            elapsedTime = (System.currentTimeMillis() - lastStartTime) / 1000
            if (elapsedTime > 0 && elapsedTime % 60 == 0L) { // Co minutę
                playSound(context)
            }
        }
    }

    Text(
        text = formatTime(elapsedTime),
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
}

fun playSound(context: Context) {
    val mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound)
    mediaPlayer.setVolume(1.0f, 1.0f)
    mediaPlayer.start()
    mediaPlayer.setOnCompletionListener { mp ->
        mp.release()
    }
}

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}