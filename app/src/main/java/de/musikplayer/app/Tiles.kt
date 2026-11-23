@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Block
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
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
    val onAddToPlaylist: (() -> Unit)? = null,
    val onBlock: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null
)

@Composable
fun GroupMenu(expanded: Boolean, onDismiss: () -> Unit, a: GroupActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Alle abspielen", Icons.Filled.PlayArrow) { onDismiss(); a.onPlay() }
        MenuItem("Zufällig abspielen", Icons.Filled.Shuffle) { onDismiss(); a.onShuffle() }
        MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { onDismiss(); a.onPlayNext() }
        MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { onDismiss(); a.onAddToQueue() }
        a.onAddToPlaylist?.let { add ->
            MenuItem("Zu Playlist hinzufügen", Icons.Filled.PlaylistAdd) { onDismiss(); add() }
        }
        a.onEdit?.let { e -> MenuItem("Album bearbeiten …", Icons.Filled.Edit, warn = true) { onDismiss(); e() } }
        a.onBlock?.let { b -> MenuItem("Sperren", Icons.Filled.Block, danger = true) { onDismiss(); b() } }
        a.onDelete?.let { d -> MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { onDismiss(); d() } }
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
    heart: Boolean = false,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shape = if (circle) CircleShape else RoundedCornerShape(12.dp)
            if (heart) FavCover(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape))
            else Cover(tracks, name, Modifier.fillMaxWidth().aspectRatio(1f).clip(shape))
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
    heart: Boolean = false,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shape = if (circle) CircleShape else RoundedCornerShape(8.dp)
            if (heart) FavCover(Modifier.size(48.dp).clip(shape))
            else Cover(tracks, name, Modifier.size(48.dp).clip(shape), sizePx = 128)
            Text(name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GroupMenu(menu, { menu = false }, actions)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onSubmit: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke(); focus.clearFocus() }),
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
    onDelete: () -> Unit,
    onBlock: () -> Unit = {},
    onAssign: () -> Unit = {}
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
                    MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { more = false; onPlayNext() }
                    MenuItem("Interpret zuordnen …", Icons.Filled.Person) { more = false; onAssign() }
                    MenuItem("Sperren", Icons.Filled.Block) { more = false; onBlock() }
                    MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { more = false; onDelete() }
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

/** Cover der Lieblingssongs: rosa Verlauf mit Herz. */
@Composable
fun FavCover(modifier: Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFE5486B)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.5f))
    }
}

/** Buchstaben-Leiste: „Alle“, „#“, A–Z. Buchstaben ohne Einträge sind ausgegraut. */
@Composable
fun LetterBar(selected: String?, available: Set<String>, onSelect: (String?) -> Unit) {
    val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { LetterChip("Alle", selected == null, true) { onSelect(null) } }
        items(letters) { l -> LetterChip(l, selected == l, l in available) { onSelect(if (selected == l) null else l) } }
    }
}

@Composable
fun LetterChip(text: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.4f), style = MaterialTheme.typography.labelLarge)
    }
}
