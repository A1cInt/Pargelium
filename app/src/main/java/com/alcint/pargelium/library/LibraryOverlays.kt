@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.library

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
            .graphicsLayer { translationY = playerOffsetY.value }
            .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .zIndex(2f)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val shouldExpand = playerOffsetY.value < (containerHeightPx * 0.20f)
                        snapPlayer(shouldExpand)
                    }
                ) { change, dragAmount ->
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
            Text(stringResource(id = R.string.add_to_playlist), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(id = R.string.no_playlists), color = MaterialTheme.colorScheme.error)
                }
            }

            LazyColumn {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text(pluralStringResource(id = R.plurals.tracks_count, count = playlist.trackIds.size, playlist.trackIds.size)) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        modifier = Modifier.clickable {
                            PrefsManager.addTrackToPlaylist(playlist.id, trackAddingToPlaylist.id)
                            Toast.makeText(context, context.getString(R.string.added_to_playlist_msg, playlist.name), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}