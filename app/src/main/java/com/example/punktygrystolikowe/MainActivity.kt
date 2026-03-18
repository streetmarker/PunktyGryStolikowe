package com.example.punktygrystolikowe

import android.content.Context
import com.google.gson.Gson
import java.io.File
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import android.media.MediaPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

// ── Historia rozgrywek — model danych ─────────────────────────────────────────
data class PlayerResult(val name: String, val score: Int)

data class GameRecord(
    val id: Long,
    val date: String,
    val players: List<PlayerResult>,
    val winner: String,       // jedno imię LUB "Remis: A, B" przy remisie
    val rounds: Int = 0,
    val isTie: Boolean = false
)

// ── Zapis / odczyt z SharedPreferences (JSON) ─────────────────────────────────
object GameHistoryManager {
    private const val PREFS = "triominos_history"
    private const val KEY   = "records"
    private const val MAX   = 100

    fun save(context: Context, record: GameRecord) {
        val all = load(context).toMutableList()
        all.add(0, record)
        val trimmed = all.take(MAX)
        val arr = JSONArray()
        trimmed.forEach { r ->
            val obj = JSONObject()
            obj.put("id",     r.id)
            obj.put("date",   r.date)
            obj.put("winner", r.winner)
            obj.put("rounds", r.rounds)
            obj.put("isTie",  r.isTie)
            val ps = JSONArray()
            r.players.forEach { p ->
                ps.put(JSONObject().apply {
                    put("name",  p.name)
                    put("score", p.score)
                })
            }
            obj.put("players", ps)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): List<GameRecord> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val ps  = obj.getJSONArray("players")
                GameRecord(
                    id      = obj.getLong("id"),
                    date    = obj.getString("date"),
                    winner  = obj.getString("winner"),
                    rounds  = obj.optInt("rounds", 0),
                    isTie   = obj.optBoolean("isTie", false),
                    players = (0 until ps.length()).map { j ->
                        val p = ps.getJSONObject(j)
                        PlayerResult(p.getString("name"), p.getInt("score"))
                    }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun formatNow(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
}

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
data class GameState(
    val playerNames: List<String>,
    val points: Map<String, Int>,
    val round: Int,
    val currentPlayerIndex: Int
)

object GameStateManager {

    private const val FILE_NAME = "currentGame.json"
    private val gson = Gson()

    fun save(context: Context, state: GameState) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = gson.toJson(state)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load(context: Context): GameState? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val json = file.readText()
            gson.fromJson(json, GameState::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.delete()
    }
}

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
    val context = LocalContext.current
    val activity = context as? Activity

    // ── Stan przeżywający rotację i przejście do tła ───────────────────────────
    // rememberSaveable zachowuje wartości przy rotacji ekranu oraz powrocie z tła.
    var currentScreen by rememberSaveable { mutableStateOf("start") }
    var playerNames   by rememberSaveable { mutableStateOf(listOf<String>()) }

    // Map<String,Int> nie jest bezpośrednio savowalny – przechowujemy jako dwie
    // równoległe listy i rekonstruujemy mapę przy każdym odczycie.
    var pointKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pointVals by rememberSaveable { mutableStateOf(listOf<Int>()) }
    val points: Map<String, Int> = remember(pointKeys, pointVals) {
        pointKeys.zip(pointVals).toMap()
    }
    fun setPoints(map: Map<String, Int>) {
        pointKeys = map.keys.toList()
        pointVals = map.values.toList()
    }
    val savedGame = GameStateManager.load(context)

    LaunchedEffect(Unit) {
        savedGame?.let {
            playerNames = it.playerNames
            setPoints(it.points)
            currentScreen = "game"
        }
    }
    // ── Globalny BackHandler — dialog wyjścia ─────────────────────────────────
    // Obsługuje przycisk systemowy „Wróć" na KAŻDYM ekranie aplikacji.
    // Home / Recent apps / przełączenie apki → Android sam wstrzymuje apkę
    // (stan jest zachowany); tu reagujemy tylko na świadome naciśnięcie Back.
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            backgroundColor = Color(0xFF1A1A4E),
            shape = RoundedCornerShape(20.dp),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("🚪", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Wyjść z gry?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = if (currentScreen == "game")
                        "Trwa rozgrywka — postęp nie zostanie zapisany.\nCzy na pewno chcesz wyjść?"
                    else
                        "Czy na pewno chcesz zamknąć aplikację?",
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { activity?.finishAffinity() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE63946)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tak, wyjdź", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showExitDialog = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x447B6FD8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Zostań w grze", color = Color.White)
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }

    when (currentScreen) {
        "start" -> StartScreen(
            onPlayersConfirmed = { names ->
                playerNames = names
                setPoints(names.associateWith { 0 })
                currentScreen = "game"
            }
        )
        "game" -> MainGameScreen(
            playerNames = playerNames,
            points = points,
            onShowWinner = { rounds ->
                val sorted   = points.entries.sortedByDescending { it.value }
                val topScore = sorted.first().value
                val tied     = sorted.filter { it.value == topScore }
                val isTie    = tied.size > 1
                val winnerLabel = if (isTie)
                    "Remis: ${tied.joinToString(", ") { it.key }}"
                else
                    sorted.first().key
                GameHistoryManager.save(context, GameRecord(
                    id      = System.currentTimeMillis(),
                    date    = GameHistoryManager.formatNow(),
                    players = sorted.map { PlayerResult(it.key, it.value) },
                    winner  = winnerLabel,
                    rounds  = rounds,
                    isTie   = isTie
                ))
                currentScreen = "winner_screen"
            },
            onUpdatePoints = { player, score ->
                setPoints(points.toMutableMap().apply {
                    this[player] = (this[player] ?: 0) + score
                })
            },
            onRestorePoints = { snapshot -> setPoints(snapshot) }
        )
        "winner_screen" -> WinnerScreen(
            playerScores = points,
            onGoBack = {
                // Reset stanu gry, wróć na ekran startowy
                playerNames = listOf()
                setPoints(mapOf())
                currentScreen = "start"
            }
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

// ── Dialog historii rozgrywek ──────────────────────────────────────────────────
@Composable
fun GameHistoryDialog(context: Context, onDismiss: () -> Unit) {
    var records by remember { mutableStateOf(GameHistoryManager.load(context)) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val gradientCard = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A4E), Color(0xFF2D1B69))
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D0D2B), Color(0xFF1A1A4E), Color(0xFF2D1B69))
                ))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Nagłówek dialogu ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Historia",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${records.size} rozgrywek",
                            fontSize = 13.sp,
                            color = Color(0xFF7B6FD8)
                        )
                    }
                    if (records.isNotEmpty()) {
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x33E63946)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp),
                            elevation = ButtonDefaults.elevation(0.dp)
                        ) {
                            Text("🗑 Wyczyść", fontSize = 12.sp, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Text("✕", color = Color(0xFF7B6FD8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f))

                // ── Lista lub pusty stan ───────────────────────────────────────
                if (records.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎲", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Brak zapisanych rozgrywek",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Zagraj i wróć tutaj!",
                                color = Color(0xFF7B6FD8),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(records, key = { it.id }) { record ->
                            GameRecordCard(record = record)
                        }
                    }
                }
            }

            // ── Potwierdzenie wyczyszczenia ────────────────────────────────────
            if (showClearConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearConfirm = false },
                    backgroundColor = Color(0xFF1A1A4E),
                    title = {
                        Text("Wyczyścić historię?", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            "Wszystkie ${records.size} rozgrywki zostaną trwale usunięte.",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                GameHistoryManager.clear(context)
                                records = emptyList()
                                showClearConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE63946)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Usuń", color = Color.White, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showClearConfirm = false },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x447B6FD8)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("Anuluj", color = Color.White) }
                    }
                )
            }
        }
    }
}

@Composable
fun GameRecordCard(record: GameRecord) {
    val topScore    = record.players.firstOrNull()?.score ?: 0
    val tiedPlayers = record.players.filter { it.score == topScore }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x33FFFFFF),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Data, liczba rund, badge remisu ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.date,
                    fontSize = 12.sp, color = Color(0xFF7B6FD8),
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                if (record.isTie) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x333A86FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🤝 REMIS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF7BC8FF),
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (record.rounds > 0) {
                    Text(text = "${record.rounds} rund", fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (record.isTie) {
                // ── Remis: pokaż wszystkich remisujących w niebieskim wierszu ──
                tiedPlayers.forEachIndexed { idx, player ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (idx == 0) Color(0x333A86FF) else Color(0x1A3A86FF))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (idx == 0) "🤝 " else "   ", fontSize = 16.sp)
                        Text(
                            text = player.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7BC8FF),
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${player.score} pkt",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF7BC8FF)
                        )
                    }
                    if (idx < tiedPlayers.lastIndex)
                        Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                // ── Zwykłe zwycięstwo ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFBE0B))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆 ", fontSize = 16.sp)
                    Text(
                        text = record.winner,
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFBE0B), modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${topScore} pkt",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFBE0B)
                    )
                }
            }

            // ── Pozostali gracze ───────────────────────────────────────────────
            val others = if (record.isTie) record.players.filter { it.score < topScore }
            else record.players.drop(1)
            if (others.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                others.forEachIndexed { index, player ->
                    val pos = if (record.isTie) tiedPlayers.size + index
                    else index + 1
                    val medal = when (pos) { 1 -> "🥈" 2 -> "🥉" else -> "  ${pos + 1}." }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = medal, fontSize = 14.sp, modifier = Modifier.width(30.dp))
                        Text(
                            text = player.name, color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${player.score} pkt",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StartScreen(onPlayersConfirmed: (List<String>) -> Unit) {
    var playerName by remember { mutableStateOf("") }
    val playerNames = remember { mutableStateListOf<String>() }
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current

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

                // ── Przycisk historii rozgrywek ────────────────────────────────
                Button(
                    onClick = { showHistory = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0x337B6FD8)),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Text(
                        text = "📋  Historia rozgrywek",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB4A8F0)
                    )
                }

                if (showHistory) {
                    GameHistoryDialog(
                        context = context,
                        onDismiss = { showHistory = false }
                    )
                }

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
    onShowWinner: (rounds: Int) -> Unit
) {
    // Back obsługiwany globalnie w GameApp — tu nie blokujemy
    GameScreen(
        playerNames = playerNames,
        points = points,
        onUpdatePoints = onUpdatePoints,
        onRestorePoints = onRestorePoints,
        onShowWinner = { rounds -> onShowWinner(rounds) }
    )
}
//@Preview(showBackground = true)
@Composable
fun GameScreen(
    playerNames: List<String>,
    points: Map<String, Int>,
    onUpdatePoints: (String, Int) -> Unit,
    onRestorePoints: (Map<String, Int>) -> Unit,
    onShowWinner: (rounds: Int) -> Unit
) {
    val context = LocalContext.current
    var currentPlayerIndex by rememberSaveable { mutableIntStateOf(0) }
    var score              by rememberSaveable { mutableStateOf("") }
    var round              by rememberSaveable { mutableIntStateOf(1) }
    var showInfoBox        by rememberSaveable { mutableStateOf(true) }
    var noMoveCounter      by rememberSaveable { mutableIntStateOf(4) }
    var minusPointsCounter by rememberSaveable { mutableIntStateOf(0) }
    var maxMinusPointsDialog by rememberSaveable { mutableStateOf(false) }
    var undoMessage        by rememberSaveable { mutableStateOf<String?>(null) }

    // Historia punktów — rememberSaveable z listSaver (przeżywa rotację)
    val pointHistory = rememberSaveable(
        saver = listSaver(
            save    = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    // Stos cofania — złożony typ, nie jest savowalny; akceptujemy reset przy rotacji
    val undoStack = remember { ArrayDeque<GameSnapshot>() }

    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    val coroutineScope = rememberCoroutineScope()
    val resetFlow    = remember { MutableSharedFlow<Unit>() }

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

    // Derived — wyliczane z mapy points przekazywanej z zewnątrz
    val maxPoints = points.values.maxOrNull() ?: 0
    val playerWithMaxPoints = points.filter { it.value == maxPoints }.keys.firstOrNull()

    LaunchedEffect(points, round, currentPlayerIndex) {
        if (playerNames.isNotEmpty()) {
            GameStateManager.save(
                context,
                GameState(playerNames, points, round, currentPlayerIndex)
            )
        }
    }
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
                        onClick = { onShowWinner(round) },
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
    val topScore      = sortedPlayers.first().value
    val tiedPlayers   = sortedPlayers.filter { it.value == topScore }
    val isTie         = tiedPlayers.size > 1

    // ── Tło ───────────────────────────────────────────────────────────────────
    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D0D2B), Color(0xFF1A1A4E), Color(0xFF2D1B69))
    )

    // ── Konfetti ───────────────────────────────────────────────────────────────
    val confettiCount = 120
    val confettiX     = remember { List(confettiCount) { Random.nextFloat() } }
    val confettiY     = remember { List(confettiCount) { Random.nextFloat() } }
    val confettiColors = remember {
        // Remis → tęczowe kolory; zwycięstwo → standardowe
        val palette = if (isTie)
            listOf(Color(0xFF3A86FF), Color(0xFF06D6A0), Color(0xFFFFBE0B),
                Color(0xFFF72585), Color(0xFFFF9F1C), Color(0xFF8338EC))
        else
            listOf(Color(0xFFF72585), Color(0xFF7209B7), Color(0xFF3A86FF),
                Color(0xFFFFBE0B), Color(0xFF06D6A0), Color(0xFFFF6B6B))
        List(confettiCount) { palette.random() }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val confettiFall by infiniteTransition.animateFloat(
        initialValue = -0.15f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "fall"
    )
    val confettiSway by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "sway"
    )

    // ── Animacje wejścia ───────────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    val cardScale   = remember { Animatable(0.7f) }
    val heroScale   = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        visible = true
        cardScale.animateTo(1f, tween(600, easing = EaseOutBack))
        heroScale.animateTo(1f, tween(500, easing = EaseOutBack))
    }

    // ── Pulsowanie ikony głównej ───────────────────────────────────────────────
    val heroPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    // ── Blask ─────────────────────────────────────────────────────────────────
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow"
    )
    // Kolor blasku: złoty dla zwycięzcy, tęczowy (cyan) dla remisu
    val glowColor = if (isTie) Color(0xFF3A86FF) else Color(0xFFFFBE0B)
    // Dodatkowy shift blasku przy remisie — kolory się przeplatają
    val glowShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowShift"
    )

    Box(modifier = Modifier.fillMaxSize().background(gradientBg)) {

        // ── Konfetti ─────────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until confettiCount) {
                val x = (confettiX[i] + confettiSway * 0.03f * (i % 3 - 1)) * size.width
                val y = ((confettiY[i] + confettiFall) % 1.25f) * size.height
                drawRect(color = confettiColors[i], topLeft = Offset(x, y),
                    size = Size(9f, 18f), alpha = 0.85f)
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { it / 3 }, animationSpec = tween(500, easing = EaseOutCubic)
            )
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Nagłówek ──────────────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "KONIEC GRY",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B6FD8), letterSpacing = 6.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTie) "Remis!" else "Wyniki końcowe",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = if (isTie) Color(0xFF3A86FF) else Color.White
                    )
                }

                // ── Karta główna: zwycięzca lub remis ─────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth().scale(cardScale.value),
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = if (isTie) Color(0x223A86FF) else Color(0x33FFFFFF),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Blask + ikona
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                drawCircle(
                                    color = glowColor.copy(alpha = glowAlpha),
                                    radius = size.minDimension / 2
                                )
                            }
                            Text(
                                text = if (isTie) "🤝" else "🏆",
                                fontSize = 64.sp,
                                modifier = Modifier.scale(heroPulse * heroScale.value)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isTie) {
                            // ── Remis: pokaż wszystkich remisujących ────────────
                            Text(
                                text = "REMIS",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF3A86FF),
                                letterSpacing = 4.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            tiedPlayers.forEach { player ->
                                Text(
                                    text = player.key,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$topScore punktów każdy",
                                fontSize = 16.sp,
                                color = Color(0xFF3A86FF).copy(alpha = 0.9f)
                            )
                        } else {
                            // ── Zwycięzca ────────────────────────────────────
                            Text(
                                text = sortedPlayers.first().key,
                                fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFBE0B), textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${sortedPlayers.first().value} punktów",
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
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
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B6FD8), letterSpacing = 3.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        sortedPlayers.forEachIndexed { index, player ->
                            val isTiedRow = player.value == topScore
                            // Przy remisie wszyscy liderzy dostają 🤝 i niebieski wiersz
                            // Przy zwycięstwie standardowe medale
                            val medal = when {
                                isTie && isTiedRow -> "🤝"
                                !isTie && index == 0 -> "🥇"
                                index == 1 || (isTie && index == tiedPlayers.size)     -> "🥈"
                                index == 2 || (isTie && index == tiedPlayers.size + 1) -> "🥉"
                                else -> "  ${index + 1}."
                            }
                            val rowBg = when {
                                isTie && isTiedRow -> Color(0x333A86FF)
                                !isTie && index == 0 -> Color(0x33FFBE0B)
                                else -> Color.Transparent
                            }
                            val nameColor = when {
                                isTie && isTiedRow -> Color(0xFF7BC8FF)
                                !isTie && index == 0 -> Color(0xFFFFBE0B)
                                else -> Color.White
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(rowBg)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = medal, fontSize = 20.sp, modifier = Modifier.width(40.dp))
                                Text(
                                    text = player.key, fontSize = 17.sp,
                                    fontWeight = if (isTiedRow || index == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = nameColor, modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${player.value} pkt", fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, color = nameColor.copy(alpha = 0.9f)
                                )
                            }
                            if (index < sortedPlayers.lastIndex)
                                Divider(color = Color.White.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }

                // ── Przycisk nowej gry ────────────────────────────────────────
                Button(
                    onClick = { onGoBack() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7209B7)),
                    elevation = ButtonDefaults.elevation(8.dp)
                ) {
                    Text(
                        text = "🎮  Nowa gra",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, letterSpacing = 1.sp
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