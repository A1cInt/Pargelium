package com.alcint.pargelium

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun FloatingActionRow(
    onRefresh: () -> Unit,
    onShuffleAll: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.padding(end = 8.dp)) {
            SmallActionBtn(Icons.Default.Shuffle, onClick = onShuffleAll)
        }

        Box(modifier = Modifier.padding(end = 16.dp)) {
            SmallActionBtn(Icons.Default.MoreVert) { showMenu = true }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Обновить медиатеку") },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = {
                        showMenu = false
                        onRefresh()
                    }
                )
            }
        }
    }
}

@Composable
fun SmallActionBtn(icon: ImageVector, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MiniPlayer(
    track: AudioTrack,
    isPlaying: Boolean,
    progress: Float,
    albumArt: Uri?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit
) {
    val saavnTrack = SaavnApi.trackCache[track.id]

    val actualArt: Uri? = if (saavnTrack != null && saavnTrack.coverUrl.isNotEmpty()) {
        Uri.parse(saavnTrack.coverUrl)
    } else {
        albumArt
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(72.dp)
    ) {
        // Фон плеера
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
        ) {
            if (actualArt != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(actualArt)
                        .size(200)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(radius = 25.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (actualArt != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(actualArt).size(100).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .shadow(4.dp, RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Текст
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Кнопки
                IconButton(onClick = onPlayPause) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                }
                IconButton(onClick = onSkipNext) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White)
                }
            }
            // Прогресс
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPillIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(56.dp),
        indicatorColor = color,
        containerColor = trackColor
    )
}