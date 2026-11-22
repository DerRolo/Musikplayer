@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Aktionen für eine ganze Gruppe von Liedern (Interpret, Playlist). */
data class GroupActions(
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onAddToPlaylist: (() -> Unit)? = null
)

@Composable
fun GroupMenu(expanded: Boolean, onDismiss: () -> Unit, a: GroupActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Alle abspielen") }, onClick = { onDismiss(); a.onPlay() })
        DropdownMenuItem(text = { Text("Zufällig abspielen") }, onClick = { onDismiss(); a.onShuffle() })
        DropdownMenuItem(text = { Text("Als Nächstes spielen") }, onClick = { onDismiss(); a.onPlayNext() })
        DropdownMenuItem(text = { Text("Zur Warteschlange") }, onClick = { onDismiss(); a.onAddToQueue() })
        a.onAddToPlaylist?.let { add ->
            DropdownMenuItem(text = { Text("Zu Playlist hinzufügen") }, onClick = { onDismiss(); add() })
        }
    }
}

/** Kachel wie bei Spotify: Cover, Name, Anzahl. Gedrückt halten öffnet das Menü für alle Lieder der Gruppe. */
@Composable
fun GroupTile(
    name: String,
    tracks: List<Track>,
    count: Int,
    circle: Boolean,
    actions: GroupActions,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Cover(
                tracks, name,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(if (circle) CircleShape else RoundedCornerShape(12.dp))
            )
            Text(
                name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                if (count == 1) "1 Titel" else "$count Titel",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        GroupMenu(menu, { menu = false }, actions)
    }
}

@Composable
fun GroupRow(
    name: String,
    tracks: List<Track>,
    count: Int,
    circle: Boolean,
    actions: GroupActions,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(tracks, name, Modifier.size(48.dp).clip(if (circle) CircleShape else RoundedCornerShape(8.dp)), sizePx = 128)
            Text(name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GroupMenu(menu, { menu = false }, actions)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Leeren") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

/** Leiste oben, solange Lieder ausgewählt sind. */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlaylist: () -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onDelete: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Auswahl beenden") }
            Text("$count ausgewählt", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onSelectAll) { Text("Alle") }
            IconButton(onClick = onPlaylist, enabled = count > 0) { Icon(Icons.Filled.PlaylistAdd, "Zu Playlist") }
            IconButton(onClick = onQueue, enabled = count > 0) { Icon(Icons.Filled.QueueMusic, "Zur Warteschlange") }
            Box {
                IconButton(onClick = { more = true }, enabled = count > 0) { Icon(Icons.Filled.MoreVert, "Mehr") }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("Als Nächstes spielen") }, onClick = { more = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Dateien löschen …") }, onClick = { more = false; onDelete() })
                }
            }
        }
    }
}

/** Warnung, wenn beim Hinzufügen Lieder schon in der Playlist sind. */
@Composable
fun DuplicateAddDialog(
    playlistName: String,
    dupCount: Int,
    total: Int,
    onSkip: () -> Unit,
    onAddAll: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Schon in der Playlist") },
        text = {
            Text(
                if (dupCount == total) "Alle $total Lieder sind in „$playlistName“ schon vorhanden."
                else "$dupCount von $total Liedern sind in „$playlistName“ schon vorhanden."
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onSkip) { Text(if (dupCount == total) "Nichts hinzufügen" else "Doppelte überspringen") }
                TextButton(onClick = onAddAll) { Text("Trotzdem alle hinzufügen") }
                TextButton(onClick = onCancel) { Text("Abbrechen") }
            }
        }
    )
}
