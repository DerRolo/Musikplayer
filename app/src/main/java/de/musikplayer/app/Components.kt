@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.musikplayer.app

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Aktionen, die ein Titel-Menü auslösen kann. */
data class TrackActions(
    val onPlayNext: (Track) -> Unit,
    val onAddToQueue: (Track) -> Unit,
    val onAddToPlaylist: (Track) -> Unit,
    val onAssignArtist: (Track) -> Unit,
    val onDelete: (Track) -> Unit,
    /** Mehrere Lieder auf einmal (Auswahl, ganzer Interpret). */
    val onAddManyToPlaylist: (List<Track>) -> Unit,
    val onDeleteMany: (List<Track>) -> Unit,
    /** Nur in Playlists: Titel aus der Playlist entfernen (Datei bleibt). */
    val onRemoveFromPlaylist: ((Track) -> Unit)? = null,
    /** Herz (Lieblingssongs) und Datei umbenennen. */
    val onToggleFavorite: (Track) -> Unit = {},
    val isFavorite: (Track) -> Boolean = { false },
    val onRename: (Track) -> Unit = {}
)

/** Menüeintrag mit Symbol. [danger] = rot (Löschen). */
@Composable
fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val red = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        text = { Text(text, color = if (danger) red else Color.Unspecified) },
        leadingIcon = { Icon(icon, null, tint = if (danger) red else LocalContentColor.current) },
        onClick = onClick
    )
}

private val HEART_PINK = Color(0xFFE5486B)

/** Nach rechts wischen = Herz, nach links wischen = Warteschlange. Die Zeile springt danach zurück. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(
    track: Track,
    actions: TrackActions,
    playing: Boolean = false,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    /** Ohne Herz/„+“ in der Zeile (steht dann im Menü), z. B. in Playlists mit Pfeilen. */
    compact: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> actions.onAddToQueue(track)
            SwipeToDismissBoxValue.StartToEnd -> actions.onToggleFavorite(track)
            else -> {}
        }
        false
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = !selectionMode,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Box(Modifier.fillMaxSize().background(HEART_PINK).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    Icon(Icons.Filled.Favorite, "Lieblingssong", tint = Color.White)
                }
                SwipeToDismissBoxValue.EndToStart -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Filled.QueueMusic, "Zur Warteschlange", tint = MaterialTheme.colorScheme.onPrimary)
                }
                else -> {}
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    track.artist + (if (track.manual) " ·  zugeordnet" else "") + "  ·  " + formatDuration(track.durationMs),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode && !compact) {
                val fav = actions.isFavorite(track)
                IconButton(onClick = { actions.onToggleFavorite(track) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Lieblingssong",
                        tint = if (fav) HEART_PINK else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { actions.onAddToPlaylist(track) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Zu Playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!selectionMode) Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, contentDescription = "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { menu = false; actions.onPlayNext(track) }
                    MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { menu = false; actions.onAddToQueue(track) }
                    MenuItem("Zu Playlist hinzufügen", Icons.Filled.PlaylistAdd) { menu = false; actions.onAddToPlaylist(track) }
                    MenuItem(
                        if (actions.isFavorite(track)) "Aus Lieblingssongs entfernen" else "Zu Lieblingssongs",
                        if (actions.isFavorite(track)) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite
                    ) { menu = false; actions.onToggleFavorite(track) }
                    MenuItem("Interpret zuordnen", Icons.Filled.Person) { menu = false; actions.onAssignArtist(track) }
                    actions.onRemoveFromPlaylist?.let { rm ->
                        MenuItem("Aus Playlist entfernen", Icons.Filled.RemoveCircleOutline) { menu = false; rm(track) }
                    }
                    MenuItem("Datei umbenennen …", Icons.Filled.Edit) { menu = false; actions.onRename(track) }
                    MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { menu = false; actions.onDelete(track) }
                }
            }
        }
    }
}

// ---------- Dialoge ----------

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    suggestions: List<String> = emptyList(),
    confirmText: String = "OK",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true)
                for (s in suggestions.distinct().take(4)) {
                    TextButton(onClick = { text = s }) { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/**
 * Löschen mit extra Bestätigung: Erst Liste der Dateien anzeigen, dann Haken „Ich weiß, dass das endgültig ist“
 * setzen – erst dann wird der Löschen-Knopf aktiv. Es gibt keinen Papierkorb.
 */
@Composable
fun DeleteDialog(tracks: List<Track>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Datei löschen?" else "${tracks.size} Dateien löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Die Dateien werden endgültig vom Handy gelöscht – nicht nur aus der App.")
                LazyColumn(Modifier.padding(vertical = 4.dp)) {
                    items(tracks.take(8)) { t ->
                        Text(
                            t.fileName + "  (" + formatSize(t.size) + ")",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (tracks.size > 8) item { Text("… und ${tracks.size - 8} weitere", style = MaterialTheme.typography.bodySmall) }
                }
                Row(Modifier.clickable { sure = !sure }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sure, onCheckedChange = { sure = it })
                    Text("Ja, endgültig löschen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = sure) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Playlist hinzufügen") },
        text = {
            LazyColumn {
                item { TextButton(onClick = onNew) { Text("+ Neue Playlist") } }
                items(playlists) { p ->
                    Text(
                        p.name + "  (" + p.trackPaths.size + ")",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
