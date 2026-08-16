package com.alcint.pargelium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class EasterEggActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EasterEggScreen()
            }
        }
    }
}

class EggItem(
    val id: Int,
    initialAlbum: Long,
    startX: Float,
    startY: Float,
    val radiusPx: Float,
    val sizeDp: Dp
) {
    var albumId by mutableLongStateOf(initialAlbum)

    var x by mutableFloatStateOf(startX)
    var y by mutableFloatStateOf(startY)
    var rotation by mutableFloatStateOf(Random.nextFloat() * 360f)
    var isDragging by mutableStateOf(false)

    var vx = (Random.nextFloat() - 0.5f) * 30f
    var vy = (Random.nextFloat() - 0.5f) * 30f
    var angularVelocity = (Random.nextFloat() - 0.5f) * 5f

    var lastChangeTime = System.currentTimeMillis()
    var changeInterval = Random.nextLong(15000, 30000)
}

@Composable
fun EasterEggScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current
    var allAlbums by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(Unit) {
        val tracks = AudioRepository.getAudioTracks(context)
        val uniqueAlbums = tracks.map { it.albumId }.distinct()
        if (uniqueAlbums.isNotEmpty()) {
            allAlbums = uniqueAlbums
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF121212), Color(0xFF1E1E2E))
                )
            )
    ) {
        if (allAlbums.isNotEmpty()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            val items = remember {
                val count = minOf(20, allAlbums.size)
                List(count) { i ->
                    val sizeDp = Random.nextInt(70, 130).dp
                    val radiusPx = with(density) { sizeDp.toPx() / 2f }
                    EggItem(
                        id = i,
                        initialAlbum = allAlbums.random(),
                        startX = widthPx / 2f + (Random.nextFloat() - 0.5f) * 100f,
                        startY = heightPx / 2f + (Random.nextFloat() - 0.5f) * 100f,
                        radiusPx = radiusPx,
                        sizeDp = sizeDp
                    )
                }
            }

            LaunchedEffect(Unit) {
                var lastTime = withFrameNanos { it }
                while (isActive) {
                    withFrameNanos { time ->
                        val dt = ((time - lastTime) / 1_000_000f) / 16f
                        lastTime = time
                        val currentTime = System.currentTimeMillis()

                        for (item in items) {
                            if (currentTime - item.lastChangeTime > item.changeInterval) {
                                item.albumId = allAlbums.random()
                                item.lastChangeTime = currentTime
                            }

                            if (!item.isDragging) {
                                item.x += item.vx * dt
                                item.y += item.vy * dt
                                item.rotation += item.angularVelocity * dt

                                item.vx *= 0.995f
                                item.vy *= 0.995f
                                item.angularVelocity *= 0.99f

                                if (item.x < item.radiusPx) {
                                    item.x = item.radiusPx
                                    item.vx = abs(item.vx) * 0.8f
                                    item.angularVelocity += item.vy * 0.15f
                                } else if (item.x > widthPx - item.radiusPx) {
                                    item.x = widthPx - item.radiusPx
                                    item.vx = -abs(item.vx) * 0.8f
                                    item.angularVelocity -= item.vy * 0.15f
                                }

                                if (item.y < item.radiusPx) {
                                    item.y = item.radiusPx
                                    item.vy = abs(item.vy) * 0.8f
                                    item.angularVelocity -= item.vx * 0.15f
                                } else if (item.y > heightPx - item.radiusPx) {
                                    item.y = heightPx - item.radiusPx
                                    item.vy = -abs(item.vy) * 0.8f
                                    item.angularVelocity += item.vx * 0.15f
                                }
                            }
                        }

                        for (i in items.indices) {
                            for (j in i + 1 until items.size) {
                                val a = items[i]
                                val b = items[j]

                                val dx = b.x - a.x
                                val dy = b.y - a.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val minDist = a.radiusPx + b.radiusPx

                                if (dist < minDist && dist > 0.1f) {
                                    val overlap = minDist - dist
                                    val nx = dx / dist
                                    val ny = dy / dist

                                    val moveX = nx * overlap * 0.5f
                                    val moveY = ny * overlap * 0.5f

                                    if (!a.isDragging) { a.x -= moveX; a.y -= moveY }
                                    if (!b.isDragging) { b.x += moveX; b.y += moveY }

                                    val kx = a.vx - b.vx
                                    val ky = a.vy - b.vy
                                    val p = 2f * (nx * kx + ny * ky) / 2f

                                    val bounceDamping = 0.85f

                                    if (!a.isDragging) {
                                        a.vx -= p * nx * bounceDamping
                                        a.vy -= p * ny * bounceDamping
                                        a.angularVelocity -= p * 0.05f
                                    }
                                    if (!b.isDragging) {
                                        b.vx += p * nx * bounceDamping
                                        b.vy += p * ny * bounceDamping
                                        b.angularVelocity += p * 0.05f
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Отрисовка
            items.forEach { item ->
                PhysicsAlbumArt(
                    item = item,
                    allAlbums = allAlbums,
                    maxX = widthPx,
                    maxY = heightPx
                )
            }
        }
    }
}

@Composable
fun PhysicsAlbumArt(
    item: EggItem,
    allAlbums: List<Long>,
    maxX: Float,
    maxY: Float
) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(AudioRepository.getAlbumArtUri(item.albumId))
            .crossfade(true)
            .build()
    )

    val velocityTracker = remember { VelocityTracker() }

    val scale by animateFloatAsState(
        targetValue = if (item.isDragging) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "dragScale"
    )

    val shadow by animateFloatAsState(
        targetValue = if (item.isDragging) 24f else 8f,
        label = "dragShadow"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = item.x - item.radiusPx
                translationY = item.y - item.radiusPx
                rotationZ = item.rotation
                scaleX = scale
                scaleY = scale
            }
            .size(item.sizeDp)
            .shadow(shadow.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        item.albumId = allAlbums.random()
                        item.vx += (Random.nextFloat() - 0.5f) * 60f
                        item.vy += (Random.nextFloat() - 0.5f) * 60f
                        item.angularVelocity += (Random.nextFloat() - 0.5f) * 30f
                        item.lastChangeTime = System.currentTimeMillis()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        item.isDragging = true
                        velocityTracker.resetTracking()
                    },
                    onDragEnd = {
                        item.isDragging = false
                        val velocity = velocityTracker.calculateVelocity()
                        item.vx = (velocity.x / 40f).coerceIn(-100f, 100f)
                        item.vy = (velocity.y / 40f).coerceIn(-100f, 100f)
                        item.angularVelocity = (item.vx + item.vy) * 0.1f
                    },
                    onDragCancel = {
                        item.isDragging = false
                    }
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)

                    item.x = (item.x + dragAmount.x).coerceIn(item.radiusPx, maxX - item.radiusPx)
                    item.y = (item.y + dragAmount.y).coerceIn(item.radiusPx, maxY - item.radiusPx)
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
                    .size(item.sizeDp / 2)
            )
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