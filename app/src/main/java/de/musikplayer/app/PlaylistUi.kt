@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Cover einer Playlist: eigenes Bild, Herz (Lieblingssongs) oder Cover der Lieder. */
@Composable
fun PlaylistCover(p: Playlist, tracks: List<Track>, modifier: Modifier, sizePx: Int = 256) {
    val ctx = LocalContext.current
    if (p.id == FAV_ID) {
        FavCover(modifier)
    } else if (p.coverStamp > 0L) {
        val bmp by produceState<ImageBitmap?>(null, p.id, p.coverStamp) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(File(File(ctx.filesDir, "covers"), "${p.id}.jpg").path)?.asImageBitmap()
            }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    } else {
        Cover(tracks, p.name, modifier, sizePx)
    }
}

/** Drei springende Balken: zeigt „läuft gerade“. */
@Composable
fun MiniEq(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(430, easing = LinearEasing), AnimRepeat.Reverse), label = "a")
    val b by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(570, easing = LinearEasing), AnimRepeat.Reverse), label = "b")
    val c by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(350, easing = LinearEasing), AnimRepeat.Reverse), label = "c")
    androidx.compose.foundation.Canvas(modifier.size(20.dp)) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, v ->
            val h = size.height * v
            drawRoundRect(
                color, topLeft = androidx.compose.ui.geometry.Offset(w * (i * 2 + 0.0f) + w * 0.0f, size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f)
            )
        }
    }
}

/** Runder Knopf auf dem Cover: Play, oder Equalizer, solange diese Playlist läuft. */
@Composable
fun PlayOverlay(playingNow: Boolean, modifier: Modifier, size: Dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (playingNow) MiniEq(Color.White, Modifier.size(size * 0.5f))
        else Icon(Icons.Filled.PlayArrow, "Abspielen", tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}

@Composable
fun PlaylistMenu(expanded: Boolean, onDismiss: () -> Unit, vm: MusicViewModel, p: Playlist, onEdit: () -> Unit, onDelete: () -> Unit) {
    val thisPlaying = vm.playingContext == "p:${p.id}"
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(if (thisPlaying && vm.isPlaying) "Pause" else "Abspielen", if (thisPlaying && vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow) { onDismiss(); vm.togglePlaylistPlay(p) }
        MenuItem("Zufällig abspielen", Icons.Filled.Shuffle) { onDismiss(); vm.playShuffled(vm.playlistTracks(p.id), "p:${p.id}") }
        MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { onDismiss(); vm.playNext(vm.playlistTracks(p.id)) }
        MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { onDismiss(); vm.addToQueue(vm.playlistTracks(p.id)) }
        MenuItem(if (p.audio) "Zu Playlist / Hörbuch …" else "Zu Playlist / Hörbuch …", Icons.Filled.PlaylistAdd) { onDismiss(); vm.pickerRequest = vm.playlistTracks(p.id) }
        if (p.id != FAV_ID) {
            MenuItem(if (p.pinned) "Fixierung lösen" else "Fixieren", Icons.Filled.PushPin) { onDismiss(); vm.setPinned(p.id, !p.pinned) }
            MenuItem("Bearbeiten (Name, Beschreibung, Bild)", Icons.Filled.Edit) { onDismiss(); onEdit() }
            MenuItem(if (p.audio) "Zu Playlist umwandeln" else "Zu Hörbuch/Podcast umwandeln", if (p.audio) Icons.Filled.PlaylistPlay else Icons.Filled.Headphones) { onDismiss(); vm.setAudio(p.id, !p.audio) }
            MenuItem("Playlist löschen …", Icons.Filled.Delete, danger = true) { onDismiss(); onDelete() }
        } else {
            MenuItem("Beschreibung bearbeiten", Icons.Filled.Edit) { onDismiss(); onEdit() }
        }
    }
}

private fun subtitle(p: Playlist) =
    "${p.trackPaths.size} Lieder" + if (p.description.isNotBlank()) "  ·  " + p.description.lines().first() else ""

@Composable
fun PlaylistRow(vm: MusicViewModel, p: Playlist, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val tracks = remember(vm.tracks, p) { vm.playlistTracks(p.id) }
    val thisPlaying = vm.playingContext == "p:${p.id}"
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(64.dp)) {
                PlaylistCover(p, tracks, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), 160)
                PlayOverlay(thisPlaying && vm.isPlaying, Modifier.align(Alignment.Center), 34.dp) { vm.togglePlaylistPlay(p) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1000, velocity = 40.dp),
                    color = if (thisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(subtitle(p), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.pinned) Icon(Icons.Filled.PushPin, "Fixiert", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
        }
        PlaylistMenu(menu, { menu = false }, vm, p, onEdit, onDelete)
    }
}

@Composable
fun PlaylistTile(vm: MusicViewModel, p: Playlist, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val tracks = remember(vm.tracks, p) { vm.playlistTracks(p.id) }
    val thisPlaying = vm.playingContext == "p:${p.id}"
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.padding(6.dp)) {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                PlaylistCover(p, tracks, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), 320)
                PlayOverlay(thisPlaying && vm.isPlaying, Modifier.align(Alignment.BottomEnd).padding(8.dp), 40.dp) { vm.togglePlaylistPlay(p) }
                if (p.pinned) Icon(Icons.Filled.PushPin, "Fixiert", Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp), tint = Color.White)
            }
            Text(
                p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).basicMarquee(iterations = 1, initialDelayMillis = 1000, velocity = 40.dp),
                color = if (thisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text("${p.trackPaths.size} ${if (p.audio) "Kapitel" else "Songs"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PlaylistMenu(menu, { menu = false }, vm, p, onEdit, onDelete)
    }
}

/** Kopf der geöffneten Playlist: Titel, Menü (3 Punkte), Bild + Beschreibung, Steuerung. */
@Composable
fun PlaylistHeader(
    vm: MusicViewModel, p: Playlist, tracks: List<Track>,
    onBack: () -> Unit, onStart: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    compact: Boolean = false
) {
    var menu by remember { mutableStateOf(false) }
    val thisPlaying = vm.playingContext == "p:${p.id}"
    val total = tracks.sumOf { it.durationMs }
    if (compact) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹") }
            PlaylistCover(p, tracks, Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)), 96)
            Text(p.name, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { if (thisPlaying) vm.togglePlay() else onStart() },
                contentAlignment = Alignment.Center
            ) {
                if (thisPlaying && vm.isPlaying) MiniEq(MaterialTheme.colorScheme.onPrimary, Modifier.size(20.dp))
                else Icon(Icons.Filled.PlayArrow, "Abspielen", Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                PlaylistMenu(menu, { menu = false }, vm, p, onEdit, onDelete)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Zurück") }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                PlaylistMenu(menu, { menu = false }, vm, p, onEdit, onDelete)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                PlaylistCover(p, tracks, Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)), 240)
                if (p.id != FAV_ID) Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(ORANGE).clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Image, "Cover ändern", Modifier.size(17.dp), tint = Color.Black) }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${tracks.size} ${if (p.audio) "Kapitel" else "Songs"} · " + formatDuration(total), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle("p:${p.id}") }, Modifier.size(44.dp)) {
                Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (vm.shuffleFor("p:${p.id}")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.previous() }, Modifier.size(48.dp), enabled = thisPlaying) { Icon(Icons.Filled.SkipPrevious, "Zurück", Modifier.size(30.dp)) }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable { if (thisPlaying) vm.togglePlay() else onStart() },
                contentAlignment = Alignment.Center
            ) {
                if (thisPlaying && vm.isPlaying) MiniEq(MaterialTheme.colorScheme.onPrimary, Modifier.size(26.dp))
                else Icon(Icons.Filled.PlayArrow, "Abspielen", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            IconButton(onClick = { vm.next() }, Modifier.size(48.dp), enabled = thisPlaying) { Icon(Icons.Filled.SkipNext, "Weiter", Modifier.size(30.dp)) }
            IconButton(onClick = { vm.cycleRepeat("p:${p.id}") }, Modifier.size(44.dp)) {
                RepeatIcon(vm.repeatFor("p:${p.id}"))
            }
        }
    }
}

/** Name, Beschreibung und Bild einer Playlist ändern. */
@Composable
fun EditPlaylistDialog(vm: MusicViewModel, playlistId: String, onDismiss: () -> Unit) {
    val p = vm.playlists.firstOrNull { it.id == playlistId } ?: run { onDismiss(); return }
    var name by remember { mutableStateOf(p.name) }
    var desc by remember { mutableStateOf(p.description) }
    var webCover by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlaylistCover(p, vm.playlistTracks(p.id), Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)), 200)
                    Column(Modifier.padding(start = 12.dp)) {
                        if (p.id != FAV_ID) {
                            TextButton(onClick = { webCover = true }) { Icon(Icons.Filled.Image, null, Modifier.size(18.dp)); Text("  Bild wählen / suchen") }
                            if (p.coverStamp > 0L) TextButton(onClick = { vm.removeCover(p.id) }) { Text("Bild entfernen") }
                        }
                    }
                }
                if (p.id != FAV_ID) OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { vm.editPlaylist(p.id, name, desc); onDismiss() }, enabled = p.id == FAV_ID || name.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
    if (webCover) CoverPickerDialog(
        initialQuery = p.name,
        onDismiss = { webCover = false },
        onUri = { vm.setCover(p.id, uri = it) },
        onUrl = { vm.setCover(p.id, url = it) }
    )
}

/** „Zu Playlist hinzufügen“: ein Umschalter Playlist ⇄ Hörbuch, Häkchen setzen/entfernen, unten „Fertig“. */
@Composable
fun AddToPlaylistDialog(vm: MusicViewModel, tracks: List<Track>, onDismiss: () -> Unit) {
    val paths = remember(tracks) { tracks.map { Store.resolve(it.path) }.toHashSet() }
    val initial = remember(tracks) {
        vm.playlists.filter { paths.isNotEmpty() && it.trackPaths.toHashSet().containsAll(paths) }.map { it.id }.toHashSet()
    }
    val checked = remember { mutableStateListOf<String>().also { it.addAll(initial) } }
    var creating by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val fq = Names.fold(q.trim())
    // Wie viele der Lieder schon drin sind (Stand beim Öffnen, damit beim Antippen nichts herumspringt)
    val already = remember(tracks) {
        vm.playlists.associate { p -> val have = p.trackPaths.toHashSet(); p.id to paths.count { it in have } }
    }
    // Erst Playlists, die die Lieder schon ganz enthalten, dann teilweise, dann die übrige gewohnte Reihenfolge
    val ordered = vm.orderedPlaylists().filter { it.audio == showAudio && (fq.isEmpty() || fq in Names.fold(it.name)) }
        .sortedByDescending { p -> val n = already[p.id] ?: 0; if (n == 0) 0 else if (n >= paths.size) 2 else 1 }
    val toAdd = checked.filter { it !in initial }
    val toRemove = initial.filter { it !in checked }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "Zu Playlist hinzufügen" else "${tracks.size} Lieder hinzufügen") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showAudio = !showAudio },
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE, contentColor = Color.Black)
                    ) {
                        Icon(if (showAudio) Icons.Filled.Headphones else Icons.Filled.QueueMusic, null, Modifier.size(20.dp))
                        Text(if (showAudio) "  Hörbücher &\n  Podcasts" else "  Playlists", maxLines = 2, softWrap = false)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Text(" Neu", maxLines = 1, softWrap = false) }
                }
                SearchField(q, { q = it }, if (showAudio) "Hörbuch suchen" else "Playlist suchen")
                LazyColumn(Modifier.height(320.dp)) {
                    items(ordered, key = { it.id }) { p ->
                        val on = p.id in checked
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) checked.remove(p.id) else checked.add(p.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            PlaylistCover(p, remember(p) { vm.playlistTracks(p.id) }, Modifier.padding(start = 10.dp).size(52.dp).clip(RoundedCornerShape(8.dp)), 120)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = already[p.id] ?: 0
                                Text(
                                    "${p.trackPaths.size} Lieder" + when {
                                        n == 0 -> ""
                                        n >= paths.size -> if (paths.size == 1) " · schon drin" else " · alle schon drin"
                                        else -> " · $n von ${paths.size} schon drin"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (n > 0) ORANGE else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (p.pinned) Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.addToPlaylists(toAdd, tracks, toRemove); vm.selectionDone(); onDismiss() },
                enabled = toAdd.isNotEmpty() || toRemove.isNotEmpty()
            ) { Text("Fertig") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
    if (creating) {
        TextInputDialog(
            title = "Neue Playlist", label = "Name", confirmText = "Erstellen",
            onDismiss = { creating = false },
            onConfirm = { n -> vm.createPlaylist(n, audio = showAudio)?.let { checked.add(it) }; creating = false }
        )
    }
}
