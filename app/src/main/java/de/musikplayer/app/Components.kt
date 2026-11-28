@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.musikplayer.app

import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val removeLabel: String = "Aus Playlist entfernen",
    /** Herz (Lieblingssongs) und Datei umbenennen. */
    val onToggleFavorite: (Track) -> Unit = {},
    val isFavorite: (Track) -> Boolean = { false },
    val onRename: (Track) -> Unit = {},
    /** Sperrliste: bleibt auf dem Handy, wird aber von der App ignoriert. */
    val onBlock: (Track) -> Unit = {},
    val onShowFile: (Track) -> Unit = {},
    val onBlockMany: (List<Track>) -> Unit = {},
    val onAssignMany: (List<Track>) -> Unit = {},
    val onCover: (Track) -> Unit = {},
    /** Tippen auf den Interpreten-Namen in einer Zeile: zur Interpreten-Seite springen. */
    val onOpenArtist: ((String) -> Unit)? = null,
    /** Ein gemeinsames Cover für alle ausgewählten Lieder. */
    val onCoverMany: (List<Track>) -> Unit = {},
    /** Lautstärke angleichen (ein oder mehrere Lieder); null = in dieser Ansicht nicht angeboten. */
    val onNormalize: ((List<Track>) -> Unit)? = null
)

/** Orange = ändert Dateien auf dem Handy (Umbenennen, Verschieben); Rot ist nur für Löschen. */
val ORANGE = Color(0xFFF59E0B)

/** Id des gerade laufenden Titels – damit jede Liste (Bibliothek, Playlist, Hörbuch …) ihn gleich markiert. */
val LocalRowScale = androidx.compose.runtime.compositionLocalOf { 1.0f }

val LocalPlayingId = androidx.compose.runtime.compositionLocalOf<Long?> { null }

/** Menüeintrag mit Symbol. [danger] = rot (Löschen). */
@Composable
fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, warn: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else if (warn) ORANGE else Color.Unspecified
    DropdownMenuItem(
        text = { Text(text, color = c) },
        leadingIcon = { Icon(icon, null, tint = if (c == Color.Unspecified) LocalContentColor.current else c) },
        onClick = onClick
    )
}

/** Herz-Farbe für Lieblingssongs (überall gleich). */
val HEART_PINK = Color(0xFFE5486B)

/** Läuft gerade Musik? (für die kleinen Equalizer-Balken in Listenzeilen) */
val LocalIsPlaying = androidx.compose.runtime.compositionLocalOf { false }

/** Drei kleine Equalizer-Balken, in der Farbe des Titels; stehen still, solange pausiert ist. */
@Composable
fun MiniEq(color: Color, active: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    val specs = listOf(420, 560, 480)
    val hs = specs.mapIndexed { i, ms ->
        t.animateFloat(
            0.25f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
                androidx.compose.animation.core.StartOffset(i * 130)
            ), label = "b$i"
        )
    }
    Row(Modifier.size(width = 16.dp, height = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        hs.forEachIndexed { i, h ->
            val f = if (active) h.value else listOf(0.5f, 0.8f, 0.35f)[i]
            Box(Modifier.width(4.dp).fillMaxHeight(f).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

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
    swipe: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val playing = playing || LocalPlayingId.current == track.id
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { total -> total * 0.25f }, confirmValueChange = { v ->
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
        enableDismissFromStartToEnd = !selectionMode && swipe && LocalSwipeAllowed.current,
        enableDismissFromEndToStart = !selectionMode && swipe && LocalSwipeAllowed.current,
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
        val sc = LocalRowScale.current
        val lv = if (sc < 0.95f) 0 else if (sc < 1.12f) 1 else 2 // 0 Klein, 1 Mittel, 2 Groß
        val coverDp = listOf(40, 48, 58)[lv].dp
        val vPad = listOf(4, 5, 8)[lv].dp
        val titleSp = listOf(14, 16, 18)[lv].sp
        val subSp = listOf(11, 13, 14)[lv].sp
        val btnW = listOf(32, 34, 40)[lv].dp
        val btnH = listOf(32, 32, 38)[lv].dp
        val iconDp = listOf(18, 19, 22)[lv].dp
        val moreDp = listOf(32, 34, 40)[lv].dp
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val titleColor = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        val openArtist = actions.onOpenArtist
        // Beim Markieren führt ein Tipp auf Interpret/Album nicht weg, sondern (de)markiert die Zeile
        val canOpen = openArtist != null && track.artist != UNKNOWN_ARTIST && !selectionMode
        val fav = actions.isFavorite(track)
        val jamLocked = Jam.editLocked
        val plays = Store.playCounts[track.path] ?: 0
        val showAlbum = Store.showAlbum && track.albumName.isNotBlank()
        val subStyle = MaterialTheme.typography.bodySmall.copy(fontSize = subSp)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(start = 12.dp, top = vPad, bottom = vPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
            Cover(listOf(track), track.title, Modifier.size(coverDp).clip(RoundedCornerShape(6.dp)), sizePx = 128)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                // Zeile 1: Titel (Klein: mit Dauer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playing) { MiniEq(titleColor, LocalIsPlaying.current); Spacer(Modifier.width(8.dp)) }
                    Text(
                        track.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).basicMarquee(iterations = 1, initialDelayMillis = 1000, velocity = 40.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = titleSp),
                        color = titleColor
                    )
                    if (lv == 0) Text(formatDuration(track.durationMs), maxLines = 1, softWrap = false, style = subStyle, color = muted, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
                }
                if (lv == 0) {
                    // Klein: zwei Ebenen – Interpret · Album
                    Row {
                        Text(
                            track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subStyle,
                            color = if (canOpen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else muted,
                            textDecoration = if (canOpen) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                            modifier = Modifier.weight(1f, fill = false).then(if (canOpen) Modifier.clickable { openArtist?.invoke(track.artist) } else Modifier)
                        )
                        if (showAlbum) Text(
                            "  ·  " + track.albumName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subStyle, color = muted,
                            modifier = Modifier.weight(1f, fill = false).then(if (canOpen) Modifier.clickable { openArtist?.invoke(track.artist + "\u0000" + track.albumName) } else Modifier)
                        )
                    }
                } else {
                    // Zeile 2: Album (nur wenn „Show Album“ an)
                    if (showAlbum) Text(
                        track.albumName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subStyle, color = muted,
                        modifier = Modifier.then(if (canOpen) Modifier.clickable { openArtist?.invoke(track.artist + "\u0000" + track.albumName) } else Modifier)
                    )
                    // Zeile 3: Interpret · Dauer links (Dauer wird nie abgeschnitten), Schnellsymbole rechts
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = subStyle,
                                color = if (canOpen) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else muted,
                                textDecoration = if (canOpen) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                modifier = Modifier.weight(1f, fill = false).then(if (canOpen) Modifier.clickable { openArtist?.invoke(track.artist) } else Modifier)
                            )
                            Text("  ·  " + formatDuration(track.durationMs), maxLines = 1, softWrap = false, style = subStyle, color = muted)
                        }
                        if (!selectionMode) {
                            Row(Modifier.height(btnH), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.width(btnW), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Filled.Headphones, "Wiedergaben", Modifier.size(subSp.value.dp), tint = muted)
                                    Text(plays.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = (subSp.value - 2).sp), color = muted, maxLines = 1)
                                }
                                IconButton(onClick = { actions.onToggleFavorite(track) }, modifier = Modifier.size(btnW, btnH)) {
                                    Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Lieblingssong", Modifier.size(iconDp), tint = if (fav) HEART_PINK else muted)
                                }
                                if (!compact) IconButton(onClick = { actions.onAddToPlaylist(track) }, modifier = Modifier.size(btnW, btnH)) {
                                    Icon(Icons.Filled.Add, "Zu Playlist", Modifier.size(iconDp), tint = muted)
                                }
                                IconButton(onClick = { actions.onBlock(track) }, enabled = !jamLocked, modifier = Modifier.size(btnW, btnH)) {
                                    Icon(Icons.Filled.Lock, "Sperren", Modifier.size(iconDp), tint = if (jamLocked) muted.copy(alpha = 0.35f) else ORANGE)
                                }
                            }
                        }
                    }
                }
            }
            if (!selectionMode) {
                if (lv == 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Headphones, "Wiedergaben", Modifier.size(11.dp), tint = muted)
                        Text(plays.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = muted, maxLines = 1)
                    }
                    IconButton(onClick = { actions.onToggleFavorite(track) }, modifier = Modifier.size(btnW, btnH)) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Lieblingssong", Modifier.size(iconDp), tint = if (fav) HEART_PINK else muted)
                    }
                    IconButton(onClick = { actions.onBlock(track) }, enabled = !jamLocked, modifier = Modifier.size(btnW, btnH)) {
                        Icon(Icons.Filled.Lock, "Sperren", Modifier.size(iconDp), tint = if (jamLocked) muted.copy(alpha = 0.35f) else ORANGE)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(moreDp)) { Icon(Icons.Filled.MoreVert, "Aktionsmenü") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { menu = false; actions.onPlayNext(track) }
                        MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { menu = false; actions.onAddToQueue(track) }
                        MenuItem("Zu Playlist hinzufügen", Icons.Filled.PlaylistAdd) { menu = false; actions.onAddToPlaylist(track) }
                        MenuItem(
                            if (fav) "Aus Lieblingssongs entfernen" else "Zu Lieblingssongs",
                            if (fav) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite
                        ) { menu = false; actions.onToggleFavorite(track) }
                        // Im Jam sind alle Bearbeiten-Funktionen ausgeblendet (außer alle erlauben es in den Developer Options)
                        if (!jamLocked) MenuItem("Interpret zuordnen", Icons.Filled.Person) { menu = false; actions.onAssignArtist(track) }
                        actions.onRemoveFromPlaylist?.let { rm ->
                            MenuItem(actions.removeLabel, Icons.Filled.RemoveCircleOutline) { menu = false; rm(track) }
                        }
                        if (!jamLocked) {
                            MenuItem("Datei bearbeiten …", Icons.Filled.Edit, warn = true) { menu = false; actions.onRename(track) }
                            actions.onNormalize?.let { nz -> MenuItem("Lautstärke angleichen …", Icons.Filled.GraphicEq, warn = true) { menu = false; nz(listOf(track)) } }
                            MenuItem("Cover ändern …", Icons.Filled.Image) { menu = false; actions.onCover(track) }
                        }
                        MenuItem("Datei im Ordner zeigen", Icons.Filled.FolderOpen) { menu = false; actions.onShowFile(track) }
                        if (!jamLocked) {
                            MenuItem("Sperren", Icons.Filled.Lock, warn = true) { menu = false; actions.onBlock(track) }
                            MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { menu = false; actions.onDelete(track) }
                        } else Text(
                            "Bearbeiten ist im Jam aus", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
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
 * Löschen mit Bestätigung: Liste der Dateien anzeigen. Gelöschtes kommt in den Papierkorb
 * (Optionen → Papierkorb) und ist dort 30 Tage wiederherstellbar.
 */
@Composable
fun DeleteDialog(tracks: List<Track>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Datei löschen?" else "${tracks.size} Dateien löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Die Dateien kommen in den Papierkorb (Optionen → Papierkorb). Dort kannst du sie ${Trash.DAYS} Tage lang wiederherstellen, danach werden sie endgültig gelöscht.")
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
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
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

/** Eingabefeld für einen Interpreten mit Vorschlägen aus den vorhandenen Interpreten (filtert beim Tippen). */
@Composable
fun ArtistField(value: String, onChange: (String) -> Unit, artists: List<String>, label: String = "Interpret") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val q = Names.fold(value.trim())
        val hits = if (q.isEmpty()) emptyList() else artists.filter { Names.fold(it).contains(q) && it != value }.take(4)
        for (a in hits) {
            Row(
                Modifier.fillMaxWidth().clickable { onChange(a) }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(a, Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** „Datei bearbeiten“: zeigt aktuellen Namen, Vorschlag der App und Interpret – alles änderbar. Deine Eingabe gilt immer. */
@Composable
fun EditTrackDialog(track: Track, artists: List<String>, onDismiss: () -> Unit, onConfirm: (name: String, artist: String, move: Boolean) -> Unit, onCover: ((String) -> Unit)? = null, coverUri: android.net.Uri? = null, coverUrl: String? = null, coverResetPending: Boolean = false) {
    val current = track.fileName.substringBeforeLast('.')
    var name by remember(track.path) { mutableStateOf(current) }
    var artist by remember(track.path) { mutableStateOf(if (track.artist == UNKNOWN_ARTIST) "" else track.artist) }
    val proposal = Library.proposedFileName(track).substringBeforeLast('.')
    var move by remember { mutableStateOf(Store.moveOnAssign) }
    val appCtx = LocalContext.current.applicationContext
    val guess = remember(track.path) { if (track.artist == UNKNOWN_ARTIST) Library.guessArtistFromName(track) else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datei bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Cover groß, mit Bearbeiten-Symbol mittendrin (wie der Play-Knopf auf Playlist-Covern)
                Box(Modifier.align(Alignment.CenterHorizontally).size(132.dp).clip(RoundedCornerShape(12.dp))) {
                    EditCoverPreview(track, coverUri, coverUrl, coverResetPending, Modifier.fillMaxSize())
                    if (onCover != null) Box(
                        Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onCover(listOf(artist.trim(), name.trim()).filter { it.isNotEmpty() }.joinToString(" ")) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Edit, "Cover ändern", tint = Color.White) }
                }
                Text("Aktueller Dateiname", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current, style = MaterialTheme.typography.bodySmall)
                if (proposal != current) {
                    Text("Vorschlag der App", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(proposal, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { name = proposal }) { Text("Übernehmen") }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Neuer Dateiname (ohne Endung)") }, modifier = Modifier.fillMaxWidth())
                if (guess != null && guess != artist) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interpret im Namen: $guess", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        TextButton(onClick = { artist = guess }) { Text("Übernehmen") }
                    }
                }
                ArtistField(artist, { artist = it }, artists)
                MoveCheckRow(move) { move = it }
            }
        },
        confirmButton = { TextButton(onClick = { Store.setMoveOnAssign(appCtx, move); onConfirm(name.trim(), artist.trim(), move) }, enabled = name.isNotBlank()) { Text("Speichern", color = ORANGE) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Kleine Checkbox „In Interpreten-Ordner verschieben“ – merkt sich den letzten Zustand (Store.moveOnAssign). */
@Composable
fun MoveCheckRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("In Interpreten-Ordner verschieben", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** Interpret für mehrere Lieder auf einmal. */
@Composable
fun AssignArtistDialog(count: Int, artists: List<String>, onDismiss: () -> Unit, showMove: Boolean = true, onConfirm: (String, Boolean) -> Unit) {
    var artist by remember { mutableStateOf("") }
    var move by remember { mutableStateOf(Store.moveOnAssign) }
    val appCtx = LocalContext.current.applicationContext
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Interpret zuordnen" else "Interpret für $count Lieder") },
        text = {
            Column {
                ArtistField(artist, { artist = it }, artists)
                if (showMove) MoveCheckRow(move) { move = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (showMove) Store.setMoveOnAssign(appCtx, move)
                onConfirm(artist.trim(), showMove && move)
            }, enabled = artist.isNotBlank()) { Text("Zuordnen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Beim Scrollen (und kurz danach) sind die Wisch-Aktionen der Zeilen gesperrt, damit nichts versehentlich ausgelöst wird. */
val LocalSwipeAllowed = androidx.compose.runtime.compositionLocalOf { true }

@Composable
fun rememberSwipeAllowed(state: androidx.compose.foundation.lazy.LazyListState): Boolean {
    var locked by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) locked = true else { kotlinx.coroutines.delay(450); locked = false }
    }
    return !locked && !scrolling
}


/**
 * Wiederholen-Knopf überall gleich: ⟳ Liste, ⟳1 Lied, →| nur dieses Lied (danach Stopp), grau = aus.
 */
@Composable
fun RepeatIcon(mode: RepeatMode, modifier: Modifier = Modifier, offTint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val (icon, label) = when (mode) {
        RepeatMode.One -> Icons.Filled.RepeatOne to "Lied wiederholen"
        RepeatMode.Once -> Icons.Filled.KeyboardTab to "Nur dieses Lied, danach Stopp"
        RepeatMode.All -> Icons.Filled.Repeat to "Liste wiederholen"
        RepeatMode.Off -> Icons.Filled.Repeat to "Wiederholen aus"
    }
    Icon(icon, label, modifier, tint = if (mode == RepeatMode.Off) offTint else MaterialTheme.colorScheme.primary)
}
