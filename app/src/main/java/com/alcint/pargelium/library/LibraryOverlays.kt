@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.library

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import kotlinx.coroutines.launch

@Composable
fun FullPlayerDraggableWrapper(
    playerOffsetY: Animatable<Float, AnimationVector1D>,
    containerHeightPx: Float,
    hiddenOffsetPx: Float,
    snapPlayer: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val currentY = playerOffsetY.value
                translationY = currentY
                val progress = if (containerHeightPx > 0f) {
                    (currentY / containerHeightPx).coerceIn(0f, 1f)
                } else 0f
                val scale = 1f - (progress * 0.04f)
                scaleX = scale
                scaleY = scale
            }
            .shadow(20.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .zIndex(2f)
            .pointerInput(containerHeightPx, hiddenOffsetPx) {
                val velocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        val shouldExpand = when {
                            velocityY > 900f -> false
                            velocityY < -900f -> true
                            else -> playerOffsetY.value < (containerHeightPx * 0.25f)
                        }
                        snapPlayer(shouldExpand)
                    },
                    onDragCancel = {
                        snapPlayer(playerOffsetY.value < (containerHeightPx * 0.25f))
                    }
                ) { change, dragAmount ->
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    val newOffset = (playerOffsetY.value + dragAmount).coerceIn(0f, hiddenOffsetPx)
                    scope.launch { playerOffsetY.snapTo(newOffset) }
                }
            }
    ) {
        content()
    }
}

@Composable
fun AddToPlaylistSheet(
    trackAddingToPlaylist: AudioTrack,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playlists = remember { PrefsManager.getPlaylists() }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.zIndex(4f)) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(id = R.string.add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.no_playlists),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            LazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    id = R.plurals.tracks_count,
                                    count = playlist.trackIds.size,
                                    playlist.trackIds.size
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                        modifier = Modifier.clickable {
                            PrefsManager.addTrackToPlaylist(playlist.id, trackAddingToPlaylist.id)
                            Toast.makeText(
                                context,
                                context.getString(R.string.added_to_playlist_msg, playlist.name),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}