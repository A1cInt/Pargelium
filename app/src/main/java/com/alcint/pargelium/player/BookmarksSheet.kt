@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.alcint.pargelium.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val trackKey = "${track.artist}_${track.title}".hashCode().toString()
    var bookmarks by remember { mutableStateOf(PrefsManager.getTrackBookmarks(trackKey)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var bookmarkToEdit by remember { mutableStateOf<TrackBookmark?>(null) }

    var bookmarkNameInput by remember { mutableStateOf("") }
    var bookmarkTimeInput by remember { mutableStateOf("") }

    val newBookmarkStr = stringResource(id = R.string.new_bookmark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()) {
            Text(
                stringResource(id = R.string.track_bookmarks),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {
                    bookmarkToEdit = null
                    bookmarkNameInput = newBookmarkStr
                    bookmarkTimeInput = formatDuration(currentPosition)
                    showAddDialog = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(id = R.string.create_bookmark))
            }

            LazyColumn(modifier = Modifier.fillMaxHeight(0.5f).padding(top = 8.dp)) {
                if (bookmarks.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(id = R.string.no_bookmarks_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(bookmarks) { b ->
                    ListItem(
                        headlineContent = { Text(b.name) },
                        supportingContent = { Text(formatDuration(b.timeMs)) },
                        modifier = Modifier.clickable {
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
                                    Icon(Icons.Default.Edit, stringResource(id = R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    val updated = bookmarks.filter { it.id != b.id }
                                    bookmarks = updated
                                    PrefsManager.saveTrackBookmarks(trackKey, updated)
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(id = R.string.action_delete), tint = MaterialTheme.colorScheme.error)
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
            title = { Text(if (bookmarkToEdit == null) stringResource(id = R.string.create_bookmark) else stringResource(id = R.string.edit_bookmark)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = bookmarkNameInput,
                        onValueChange = { bookmarkNameInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = bookmarkTimeInput,
                        onValueChange = { bookmarkTimeInput = it },
                        label = { Text(stringResource(id = R.string.bookmark_time_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        bookmarks.map { if (it.id == bookmarkToEdit!!.id) it.copy(name = bookmarkNameInput, timeMs = parsedTimeMs) else it }
                    }
                    bookmarks = updated.sortedBy { it.timeMs }
                    PrefsManager.saveTrackBookmarks(trackKey, bookmarks)
                    showAddDialog = false
                }) { Text(stringResource(id = R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(id = R.string.action_cancel)) }
            }
        )
    }
}