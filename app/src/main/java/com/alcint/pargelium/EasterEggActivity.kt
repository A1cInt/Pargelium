package com.alcint.pargelium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

class EasterEggActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasterEggScreen()
        }
    }
}

@Composable
fun EasterEggScreen() {
    val context = LocalContext.current
    var allAlbums by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(Unit) {
        val tracks = AudioRepository.getAudioTracks(context)
        val uniqueAlbums = tracks.map { it.albumId }.distinct()
        if (uniqueAlbums.isNotEmpty()) {
            allAlbums = uniqueAlbums
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (allAlbums.isNotEmpty()) {
            val itemsCount = minOf(15, allAlbums.size)
            for (i in 0 until itemsCount) {
                FloatingAlbumArt(allAlbums)
            }
        }
    }
}

@Composable
fun FloatingAlbumArt(allAlbums: List<Long>) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var currentAlbumId by remember { mutableStateOf(allAlbums.random()) }

    val sizeDp = remember { Random.nextInt(60, 120).dp }
    val sizePx = with(density) { sizeDp.toPx() }

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val maxX = screenWidthPx - sizePx
    val maxY = screenHeightPx - sizePx

    var offsetX by remember { mutableFloatStateOf(Random.nextFloat() * maxX) }
    var offsetY by remember { mutableFloatStateOf(Random.nextFloat() * maxY) }
    var velocityX by remember { mutableFloatStateOf((Random.nextFloat() - 0.5f) * 10f) }
    var velocityY by remember { mutableFloatStateOf((Random.nextFloat() - 0.5f) * 10f) }

    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameTime ->
                val deltaMs = (frameTime - lastFrameTime) / 1_000_000f
                lastFrameTime = frameTime
                val timeScale = deltaMs / 16f

                if (!isDragging) {
                    offsetX += velocityX * timeScale
                    offsetY += velocityY * timeScale

                    if (offsetX <= 0f) {
                        offsetX = 0f
                        velocityX = kotlin.math.abs(velocityX) * 0.8f
                    } else if (offsetX >= maxX) {
                        offsetX = maxX
                        velocityX = -kotlin.math.abs(velocityX) * 0.8f
                    }

                    if (offsetY <= 0f) {
                        offsetY = 0f
                        velocityY = kotlin.math.abs(velocityY) * 0.8f
                    } else if (offsetY >= maxY) {
                        offsetY = maxY
                        velocityY = -kotlin.math.abs(velocityY) * 0.8f
                    }

                    velocityX *= 0.99f
                    velocityY *= 0.99f

                    if (kotlin.math.abs(velocityX) < 0.5f) velocityX += if (velocityX > 0) 0.05f else -0.05f
                    if (kotlin.math.abs(velocityY) < 0.5f) velocityY += if (velocityY > 0) 0.05f else -0.05f
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(15000, 30000))
            currentAlbumId = allAlbums.random()
        }
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(AudioRepository.getAlbumArtUri(currentAlbumId))
            .crossfade(true)
            .build()
    )

    val shape = RoundedCornerShape(12.dp)
    val velocityTracker = remember { VelocityTracker() }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
            .size(sizeDp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        isDragging = false
                        val velocity = velocityTracker.calculateVelocity()
                        velocityX = (velocity.x / 60f).coerceIn(-60f, 60f)
                        velocityY = (velocity.y / 60f).coerceIn(-60f, 60f)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                }
            }
    ) {
        if (painter.state is AsyncImagePainter.State.Error) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(sizeDp / 2)
            )
            LaunchedEffect(currentAlbumId) {
                delay(2000)
                currentAlbumId = allAlbums.random()
            }
        } else {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}