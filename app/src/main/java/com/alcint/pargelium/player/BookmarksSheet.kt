@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alcint.pargelium.AudioTrack
import com.alcint.pargelium.PrefsManager
import com.alcint.pargelium.R
import com.alcint.pargelium.TrackBookmark

@Composable
fun BookmarksSheet(
    track: AudioTrack,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val trackKey = remember(track.artist, track.title) {
        "${track.artist.trim().lowercase()}_${track.title.trim().lowercase()}".hashCode().toString()
    }
    val legacyKey = remember(track.artist, track.title) {
        "${track.artist}_${track.title}".hashCode().toString()
    }

    var bookmarks by remember(trackKey) {
        val primary = PrefsManager.getTrackBookmarks(trackKey)
        val initial = if (primary.isNotEmpty()) primary else PrefsManager.getTrackBookmarks(legacyKey)
        mutableStateOf(initial)
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var bookmarkToEdit by remember { mutableStateOf<TrackBookmark?>(null) }

    var bookmarkNameInput by remember { mutableStateOf("") }
    var bookmarkTimeInput by remember { mutableStateOf("") }

    val newBookmarkStr = stringResource(id = R.string.new_bookmark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.track_bookmarks),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Button(
                onClick = {
                    bookmarkToEdit = null
                    bookmarkNameInput = "$newBookmarkStr (${bookmarks.size + 1})"
                    bookmarkTimeInput = formatDuration(currentPosition)
                    showAddDialog = true
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(id = R.string.create_bookmark), fontWeight = FontWeight.Bold)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .padding(top = 10.dp)
            ) {
                if (bookmarks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.no_bookmarks_yet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(bookmarks, key = { it.id }) { b ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        headlineContent = {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                text = formatDuration(b.timeMs),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSeek(b.timeMs)
                                onDismiss()
                            },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    bookmarkToEdit = b
                                    bookmarkNameInput = b.name
                                    bookmarkTimeInput = formatDuration(b.timeMs)
                                    showAddDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(id = R.string.action_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    val updated = bookmarks.filter { it.id != b.id }
                                    bookmarks = updated
                                    PrefsManager.saveTrackBookmarks(trackKey, updated)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(id = R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (bookmarkToEdit == null) stringResource(id = R.string.create_bookmark)
                    else stringResource(id = R.string.edit_bookmark)
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = bookmarkNameInput,
                        onValueChange = { bookmarkNameInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = bookmarkTimeInput,
                        onValueChange = { bookmarkTimeInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_time_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-5000L to "-5s", -1000L to "-1s", 1000L to "+1s", 5000L to "+5s").forEach { (delta, label) ->
                            FilledTonalButton(
                                onClick = {
                                    val cur = parseDurationToMs(bookmarkTimeInput).takeIf { it >= 0L } ?: currentPosition
                                    bookmarkTimeInput = formatDuration((cur + delta).coerceAtLeast(0L))
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    var parsedTimeMs = parseDurationToMs(bookmarkTimeInput)
                    if (parsedTimeMs < 0) {
                        parsedTimeMs = currentPosition
                    }

                    val updated = if (bookmarkToEdit == null) {
                        bookmarks + TrackBookmark(name = bookmarkNameInput, timeMs = parsedTimeMs)
                    } else {
                        bookmarks.map {
                            if (it.id == bookmarkToEdit!!.id) it.copy(name = bookmarkNameInput, timeMs = parsedTimeMs)
                            else it
                        }
                    }
                    bookmarks = updated.sortedBy { it.timeMs }
                    PrefsManager.saveTrackBookmarks(trackKey, bookmarks)
                    showAddDialog = false
                }) {
                    Text(stringResource(id = R.string.action_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            }
        )
    }
}