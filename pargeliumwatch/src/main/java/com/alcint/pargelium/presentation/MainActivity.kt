package com.alcint.pargelium

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val client = WearClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            PargeliumWatchTheme {
                WatchApp(client)
            }
        }
    }
}

@Composable
fun WatchApp(client: WearClient) {
    val context = LocalContext.current
    val state by client.playerState.collectAsState()
    val library by client.library.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        client.connect(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            client.connect(context)
        }
    }

    var showPlayer by remember { mutableStateOf(true) }

    val seedColor = Color(state.primaryColor)
    val auroraColor = if (state.primaryColor == android.graphics.Color.DKGRAY || state.primaryColor == android.graphics.Color.BLACK)
        Color(0xFFD0BCFF) else seedColor

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AuroraWatchBackground(seedColor = auroraColor, isPlaying = state.isPlaying)

        if (showPlayer) {
            PlayerScreen(
                state = state,
                onPlayPause = { client.sendCommand(if (state.isPlaying) "PAUSE" else "PLAY") },
                onNext = { client.sendCommand("NEXT") },
                onPrev = { client.sendCommand("PREV") },
                onLibraryClick = { showPlayer = false }
            )
        } else {
            LibraryScreen(
                albums = library,
                onBack = { showPlayer = true },
                onAlbumClick = { id ->
                    client.sendCommand("PLAY_ALBUM:$id")
                    showPlayer = true
                }
            )
        }
    }
}

@Composable
fun AuroraWatchBackground(seedColor: Color, isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_anim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "angle"
    )
    val pulse by animateFloatAsState(
        targetValue = if (isPlaying) 1.2f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val rad = w * 0.5f * pulse
        val x1 = cx + cos(Math.toRadians(angle.toDouble())).toFloat() * (w * 0.2f)
        val y1 = cy + sin(Math.toRadians(angle.toDouble())).toFloat() * (h * 0.2f)

        drawCircle(brush = Brush.radialGradient(colors = listOf(seedColor.copy(alpha = 0.6f), Color.Transparent), center = Offset(x1, y1), radius = rad), center = Offset(x1, y1), radius = rad)
        val x2 = cx + cos(Math.toRadians(angle.toDouble() + 180)).toFloat() * (w * 0.2f)
        val y2 = cy + sin(Math.toRadians(angle.toDouble() + 180)).toFloat() * (h * 0.2f)
        drawCircle(brush = Brush.radialGradient(colors = listOf(seedColor.copy(alpha = 0.4f), Color.Transparent), center = Offset(x2, y2), radius = rad * 0.8f), center = Offset(x2, y2), radius = rad * 0.8f)
    }
}

@Composable
fun PlayerScreen(state: PlayerState, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onLibraryClick: () -> Unit) {
    val progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f
    CircularProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxSize(), startAngle = 290f, endAngle = 250f, strokeWidth = 4.dp, indicatorColor = Color.White.copy(alpha = 0.8f), trackColor = Color.White.copy(alpha = 0.1f))
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.height(12.dp))
        Text(text = state.title, style = MaterialTheme.typography.title3, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        Text(text = state.artist, style = MaterialTheme.typography.body2, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPrev, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(40.dp)) { Text("<", fontWeight = FontWeight.Bold) }
            Button(onClick = onPlayPause, modifier = Modifier.size(56.dp), colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color.White)) { Text(if (state.isPlaying) "||" else "▶", color = Color.Black, fontSize = 20.sp) }
            Button(onClick = onNext, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(40.dp)) { Text(">", fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CompactChip(onClick = onLibraryClick, label = { Text("Библиотека", fontSize = 12.sp) }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.width(100.dp))
    }
}

@Composable
fun LibraryScreen(albums: List<LibraryItem>, onBack: () -> Unit, onAlbumClick: (Long) -> Unit) {
    ScalingLazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)), anchorType = androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType.ItemStart) {
        item { CompactChip(onClick = onBack, label = { Text("Назад к плееру") }, colors = ChipDefaults.primaryChipColors(backgroundColor = MaterialTheme.colors.primary), modifier = Modifier.padding(bottom = 4.dp)) }
        if (albums.isEmpty()) { item { Text("Список пуст.\nПроверь соединение.", textAlign = TextAlign.Center, style = MaterialTheme.typography.body2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) } }
        else { items(albums) { album -> TitleCard(onClick = { onAlbumClick(album.id) }, title = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, content = { Text("${album.count} треков", color = Color.Gray) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) } }
    }
}

@Composable
fun PargeliumWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = Colors(primary = Color(0xFFD0BCFF), background = Color.Black, surface = Color(0xFF202020), onPrimary = Color.Black, onSurface = Color.White), content = content)
}