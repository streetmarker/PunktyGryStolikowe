package com.example.punktygrystolikowe

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.DialogProperties

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

    // Funkcja do aktualizacji punktów
    fun updatePoints(player: String, score: Int) {
        points = points.toMutableMap().apply {
            this[player] = (this[player] ?: 0) + score
        }
    }
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
            onShowWinner = {
                currentScreen = "winner_screen"
            },
            onUpdatePoints = { player, score ->
                points = points.toMutableMap().apply {
                    this[player] = (this[player] ?: 0) + score
                }
            }
        )
        "winner_screen" -> WinnerScreen(
            playerScores = points,
            onGoBack = {
                currentScreen = "start"
            }
        )
    }
}

@Composable
fun StartScreen(onPlayersConfirmed: (List<String>) -> Unit) {
    var playerName by remember { mutableStateOf("") }
    val playerNames = remember { mutableStateListOf<String>() }

    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dodaj graczy", fontSize = 24.sp)

        TextField(
                value = playerName,
                onValueChange = { playerName = it },
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences, // Ustawia wielką literę na początku zdania
                    keyboardType = KeyboardType.Text
                ),
                label = { Text("Imię gracza") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
        )
        Button(
                onClick = {
                    if (playerName.isNotEmpty()) {
                        playerNames.add(playerName)
                        playerName = ""
                    }
                },
                modifier = Modifier.padding(8.dp)
        ) {
            Text("Dodaj gracza")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playerNames.isNotEmpty()) {
            Column {
                Text("Lista graczy:", fontSize = 18.sp)
                playerNames.forEach { Text(it) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                    onClick = { onPlayersConfirmed(playerNames) },
                    modifier = Modifier.padding(8.dp)
            ) {
                Text("Rozpocznij grę")
            }
        }
    }
}

@Composable
fun MainGameScreen(
    playerNames: List<String>,
    points: Map<String, Int>,
    onUpdatePoints: (String, Int) -> Unit,
    onShowWinner: () -> Unit // Callback do pokazania ekranu zwycięzcy
) {
    fun winner() {
        onShowWinner();
    }
    // Wywołanie ekranu gry
    GameScreen(
        playerNames = playerNames,
        points = points,
        onUpdatePoints = onUpdatePoints,
        onShowWinner = { winner() }// Callback do pokazania ekranu zwycięzcy
    )
}
//@Preview(showBackground = true)
@Composable
fun GameScreen(
    playerNames: List<String>,
    points: Map<String, Int>,
    onUpdatePoints: (String, Int) -> Unit,
    onShowWinner: () -> Unit // Callback do pokazania ekranu zwycięzcy
) {
    var currentPlayerIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf("") }
    var round by remember { mutableIntStateOf(1) }
    val pointHistory = remember { mutableListOf<String>() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val maxPoints = points.values.maxOrNull() ?: 0
    val playerWithMaxPoints = points.filter { it.value == maxPoints }.keys.firstOrNull()
    var showInfoBox by remember { mutableStateOf(true) } // Flaga do kontrolowania widoczności info boxa
    var isNegative by remember { mutableStateOf(false) } // Flaga do kontroli znaku liczby
    var minusPointsCounter by remember { mutableIntStateOf(0) }
    var nextRoundDialog by remember { mutableStateOf(false) }
    var maxMinusPointsDialog by remember { mutableStateOf(false) }

    ModalDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                Button(
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp)
                ) {
                    Text("Zamknij")
                }

//                Spacer(modifier = Modifier.weight(1f))

                Text(text = "Historia punktów od ostatniego", fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                pointHistory.asReversed().forEach { record ->
                    Text(text = record, fontSize = 16.sp, modifier = Modifier.padding(4.dp))
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Runda: $round",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
//                modifier = Modifier.padding(bottom = 16.dp)
            )
/////////////////////////////////////////////////////////////////
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
//                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly // Ustawienie rozmieszczenia elementów
            ) {
                    Button(
                        onClick = {
                            val scoreValue = if (isNegative) -(score.toIntOrNull() ?: 0) else score.toIntOrNull() ?: 0 // TODO do funkcji
//                            val currentPlayer = playerNames[currentPlayerIndex]
//                            onUpdatePoints(currentPlayer, scoreValue)
//                            pointHistory.add("${pointHistory.size+1}. $currentPlayer: $scoreValue pkt")
//                            score = "";
                            if (isNegative) {
                                minusPointsCounter++
                            } else {
                                minusPointsCounter = 0
                            }

                            if(minusPointsCounter >= 4 && scoreValue != -10){
                                maxMinusPointsDialog = true
                            } else if (minusPointsCounter > 4) {
                                maxMinusPointsDialog = true
                            } else {
                                val currentPlayer = playerNames[currentPlayerIndex]
                                onUpdatePoints(currentPlayer, scoreValue)
                                pointHistory.add("${pointHistory.size+1}. $currentPlayer: $scoreValue pkt")
                                score = "";
                            }

                        },
                        modifier = Modifier.weight(1f).padding(8.dp).fillMaxWidth()
                    ) {
                        Text("Zatwierdź punkt")
                    }
                Button(
                    onClick = {
                        if(score.isNotEmpty()) {
                            nextRoundDialog = true
                        } else {
                            currentPlayerIndex = (currentPlayerIndex + 1) % playerNames.size
                            if (currentPlayerIndex == 0) {
                                round += 1
                            }
                            minusPointsCounter = 0
                        }
                    },
                    modifier = Modifier.weight(1f).padding(8.dp).fillMaxWidth()
                ) {
                    Text("Następny gracz")
                }

            }

//            Spacer(modifier = Modifier.height(16.dp))
//////////////////////////////////////////////////////////////
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Przycisk do ustawienia znaku minus
                Button(
                    onClick = { isNegative = !isNegative },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isNegative) Color.Red else Color.Gray // Kolor przycisku zależny od wybranego znaku
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("-")
                }
                // Pole tekstowe dla liczby
                TextField(
                    value = score,
                    onValueChange = { newValue ->
                        // Walidacja, by wpisywać tylko cyfry
                        if (newValue.isEmpty() || newValue.matches(Regex("\\d*"))) {
                            score = newValue
                        }
                    },
                    label = { Text("Punkty") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f) // Sprawia, że TextField zajmuje resztę dostępnej szerokości
                )    // Pole tekstowe dla liczby
            }
            if(minusPointsCounter != 0){
                Text(
                    text = buildString {
                        if (minusPointsCounter > 0) {
                            append("Minusowe punkty: ${" X ".repeat(minusPointsCounter)} \n")
                        }
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
//                modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                text = " ".repeat(minusPointsCounter) +"Gracz: ${playerNames[currentPlayerIndex]}",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
//                modifier = Modifier.padding(8.dp)
            )
//////////////////////////////////////////////////////////////
            if (nextRoundDialog) {
                AlertDialog(
                    onDismissRequest = {
                        // Zamknij dialog po kliknięciu poza nim
                        nextRoundDialog = false
                    },
                    title = {
                        Text(text = "Uwaga")
                    },
                    text = {
                        Text("Zatwierdź punkt przed przejściem do ruchu kolejnego gracza" +
                                "lub naciśnij Zatwierdź by zapisać punkt i przejść do kolejnego" +
                                "gracza)\n" +
                                "Punkt ${playerNames[currentPlayerIndex]}: ${score}")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                nextRoundDialog = false

                                val scoreValue = if (isNegative) -(score.toIntOrNull() ?: 0) else score.toIntOrNull() ?: 0 // TODO do funkcji

                                if (isNegative) {
                                    minusPointsCounter++
                                } else {
                                    minusPointsCounter = 0
                                }

                                if(minusPointsCounter >= 4 && scoreValue != -10){
                                    maxMinusPointsDialog = true
                                } else if (minusPointsCounter > 4) {
                                    maxMinusPointsDialog = true
                                } else {
                                    val currentPlayer = playerNames[currentPlayerIndex]
                                    onUpdatePoints(currentPlayer, scoreValue)
                                    pointHistory.add("${pointHistory.size+1}. $currentPlayer: $scoreValue pkt")
                                    score = "";

                                    // Kontynuuj działanie po zatwierdzeniu alertu
                                    currentPlayerIndex = (currentPlayerIndex + 1) % playerNames.size
                                    if (currentPlayerIndex == 0) {
                                        round += 1
                                    }
                                }
                            }
                        ) {
                            Text("Zatwierdź")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                // Zamknij dialog bez zmian
                                nextRoundDialog = false
                            }
                        ) {
                            Text("Zamknij")
                        }
                    },
                    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                )
            }
            ////////////////////////////////////////////////////////////////////////////
            if (maxMinusPointsDialog) {
                AlertDialog(
                    onDismissRequest = {
                        // Zamknij dialog po kliknięciu poza nim
                        maxMinusPointsDialog = false
                    },
                    title = {
                        Text(text = "Uwaga")
                    },
                    text = {
                        Text("Można maksymalnie zatwierdzić jedynie trzykrotnie -5 pkt." +
                                "a potem raz -10 pkt.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                maxMinusPointsDialog = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                // Zamknij dialog bez zmian
                                maxMinusPointsDialog = false
                            }
                        ) {
                            Text("Zamknij")
                        }
                    },
                    properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                )
            }
            /////////////////////////////////////////////////////////////////////////////
            if (showInfoBox) {
                Card(
                    backgroundColor = Color.LightGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1. Wpisz wynik ruchu gracza w polu Punkty\n" +
                                    "2. Wciśnij dla liczb ujemnych lub odciśnij znak minusa po lewej stronie\n" +
                                    "3. Zatwierdź punkt\n" +
                                    "4. Przejdź do ruchu następnego gracza gracza",
                            modifier = Modifier.weight(1f) // Zapewnia, że Text zajmuje resztę dostępnej szerokości
                        )
                        IconButton(
                            onClick = { showInfoBox = false },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(24.dp) // Ustaw rozmiar ikony
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close), // Ikona zamknięcia (dodaj odpowiedni plik do zasobów)
                                contentDescription = "Zamknij"
                            )
                        }
                    }
                }
            }
            Text(
                text = "Aktualne punkty:",
                fontSize = 18.sp,
                modifier = Modifier.padding(8.dp)
            )

            // Wyświetlanie punktów graczy w dropdown menu
            playerNames.forEach { player ->
                val backgroundColor =
                    if (player == playerWithMaxPoints) Color.Yellow else Color.Transparent
                 Text(
                        text = "$player: ${points[player] ?: 0} punktów",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .background(color = backgroundColor)
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }

            Spacer(modifier = Modifier.height(16.dp))

            // Przycisk otwierający panel wysuwany
            Button(onClick = { scope.launch { drawerState.open() } }) {
                Text("Pokaż historię punktów")
            }
            // Dodaj przycisk, który kończy grę i przechodzi do ekranu zwycięzcy
            Button(
                onClick = {
                    onShowWinner() // Wywołanie callbacku przejścia do ekranu zwycięzcy
                },
                modifier = Modifier.padding(8.dp)
            ) {
                Text("Zakończ grę i pokaż zwycięzcę")
            }
        }
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun WinnerScreen(playerScores: Map<String, Int>, onGoBack: () -> Unit) {
    // Sortowanie graczy według punktów malejąco
    val sortedPlayers = playerScores.entries.sortedByDescending { it.value }

    // Najlepszy gracz (pierwszy w posortowanej liście)
    val topPlayer = sortedPlayers.first()

    // Dodajmy animację fajerwerków (z prostą symulacją)
    val infiniteTransition = rememberInfiniteTransition()
    val fireworksSize by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    // Konfetti - opadające elementy
    val confettiYPositions = remember { List(100) { Random.nextFloat() * 1000f } }
    val confettiXPositions = remember { List(100) { Random.nextFloat() * 800f } }
    val confettiColors = remember {
        List(100) {
            Color(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat(),
                alpha = 0.8f
            )
        }
    }

    val confettiFall by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
        // Animacja konfetti
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Fajerwerki
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color.Yellow, radius = fireworksSize)
                drawCircle(Color.Red, radius = fireworksSize / 2, center = Offset(200f, 200f))
                drawCircle(Color.Blue, radius = fireworksSize / 3, center = Offset(500f, 500f))
            }
            // Animacja konfetti
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (i in confettiYPositions.indices) {
                    drawRect(
                        color = confettiColors[i],
                        topLeft = Offset(
                            confettiXPositions[i],
                            (confettiYPositions[i] + confettiFall) % size.height
                        ),
                        size = Size(10f, 20f)
                    )
                }
            }

//        Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Najlepszy gracz z animacją
                Text(
                    text = "Zwycięzca: ${topPlayer.key} z ${topPlayer.value} punktami!",
                    fontSize = 30.sp,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Lista pozostałych graczy
                Text(text = "Wyniki:", fontSize = 24.sp, color = Color.White)
                sortedPlayers.forEach { player ->
                    Text(
                        text = "${sortedPlayers.indexOf(player) + 1}. ${player.key}: ${player.value} pkt",
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Przycisk powrotu
                Button(onClick = { onGoBack() }) {
                    Text("Nowa Gra")
                }
            }
        }

}
@Composable
fun ShowAlertDialog() {
    // Tworzenie stanu dla widoczności dialogu
    var openDialog by remember { mutableStateOf(false) }

    // Przycisk, który otworzy dialog
    Button(onClick = { openDialog = true }) {
        Text("Pokaż Alert")
    }

    // Definicja samego AlertDialog
    if (openDialog) {
        AlertDialog(
            onDismissRequest = {
                // Zamknięcie dialogu po kliknięciu w tło
                openDialog = false
            },
            title = {
                Text(text = "Tytuł Alertu")
            },
            text = {
                Text("To jest treść alertu. Czy chcesz kontynuować?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Akcja po kliknięciu "Tak"
                        openDialog = false
                    }
                ) {
                    Text("Tak")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        // Akcja po kliknięciu "Nie"
                        openDialog = false
                    }
                ) {
                    Text("Nie")
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }
}
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    GameApp()
}

