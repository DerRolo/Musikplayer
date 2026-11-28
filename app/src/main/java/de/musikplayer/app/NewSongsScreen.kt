@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val NEW_SORTS = listOf("Kürzlich hinzugefügt", "Titel", "Interpret", "Ordner")

/**
 * Neu gefundene Lieder. Ansichten: Einzeln (Karten zum Blättern), Liste, Kacheln – dazu Sortierung.
 * In Liste und Kacheln lassen sich mehrere Lieder markieren (gedrückt halten) und gemeinsam bearbeiten.
 */
@Composable
fun NewSongsPage(vm: MusicViewModel, actions: TrackActions, onBack: () -> Unit) {
    // Beim Öffnen sofort nachsehen, ob etwas Neues da ist
    LaunchedEffect(Unit) { vm.refresh() }
    var view by rememberSaveable { mutableIntStateOf(1) } // 0 Einzeln, 1 Liste, 2 Kacheln
    var sortIdx by rememberSaveable { mutableIntStateOf(Store.sortPref("newSongs", 0)) }
    val sel = remember { Selection() }
    val existing = remember(vm.tracks, vm.seenTick) { vm.existingArtists() }
    val fresh = remember(vm.tracks, vm.seenTick, sortIdx) {
        val l = vm.newTracks()
        when (sortIdx) {
            0 -> l.sortedByDescending { it.modified }
            1 -> l.sortedBy { it.sortKey }
            2 -> l.sortedWith(compareBy({ it.artistKey }, { it.sortKey }))
            else -> l.sortedWith(compareBy({ it.folder }, { it.sortKey }))
        }
    }
    var edit by remember { mutableStateOf<Track?>(null) }
    BackHandler(enabled = sel.active) { sel.clear() }
    val chosen = { fresh.filter { it.id in sel.ids } }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Neue Lieder", onBack) {
            ViewToggle(listOf(VIEW_SINGLE, VIEW_LIST, VIEW_GRID), view) { view = it; sel.clear() }
        }
        if (fresh.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine neuen Lieder") }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${fresh.size} neu", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SortMenu("Sortierung: " + NEW_SORTS[sortIdx], NEW_SORTS, sortIdx) { sortIdx = it; vm.saveSort("newSongs", it) }
            }
            if (sel.active && view == 2) {
                SelectionBar(
                    count = sel.ids.size,
                    onClose = { sel.clear() },
                    onSelectAll = { sel.setAll(fresh.map { it.id }) },
                    onPlaylist = { actions.onAddManyToPlaylist(chosen()); sel.clear() },
                    onQueue = { vm.addToQueue(chosen()); sel.clear() },
                    onPlayNext = { vm.playNext(chosen()); sel.clear() },
                    onDelete = { actions.onDeleteMany(chosen()); sel.clear() },
                    onBlock = { actions.onBlockMany(chosen()); sel.clear() },
                    onAssign = { actions.onAssignMany(chosen()); sel.clear() }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (view) {
                    0 -> SinglePager(vm, actions, fresh, existing, onEdit = { edit = it })
                    1 -> TrackListBody(vm, fresh, "q", actions, sel)
                    else -> {
                        val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
                        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                            items(fresh, key = { it.id }) { t ->
                                NewTile(t, actions, selected = t.id in sel.ids, playing = t.id == playingId, selectionMode = sel.active,
                                    onLong = { sel.toggle(t.id) }) {
                                    if (sel.active) sel.toggle(t.id) else vm.play(fresh, fresh.indexOf(t), "q")
                                }
                            }
                        }
                    }
                }
            }
            if (view != 0) {
                Button(
                    onClick = { vm.markSeen(if (sel.active) chosen() else fresh); sel.clear() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                    Text(if (sel.active) "  Passt so (${sel.ids.size} markierte)" else "  Alle: Passt so")
                }
            }
        }
    }

    edit?.let { t ->
        EditTrackDialog(
            t, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { edit = null },
            onConfirm = { n, a, mv -> vm.editTrack(t, n, a, mv); edit = null }
        )
    }
}

@Composable
private fun NewTile(
    t: Track, actions: TrackActions, selected: Boolean, playing: Boolean, selectionMode: Boolean,
    onLong: () -> Unit, onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(4.dp).combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
            Cover(listOf(t), t.title, Modifier.fillMaxSize())
            if (selected) Box(Modifier.fillMaxSize().androidxBg(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, Modifier.size(40.dp), tint = Color.White)
            }
            if (!selectionMode) Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, Modifier.size(34.dp)) { Icon(Icons.Filled.MoreVert, "Menü", tint = Color.White) }
                TrackMenu(menu, { menu = false }, t, actions)
            }
        }
        Text(
            t.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (t.artist == UNKNOWN_ARTIST) "Interpret unbekannt" else t.artist, style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (t.artist == UNKNOWN_ARTIST) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Modifier.androidxBg(c: Color): Modifier = this.then(Modifier.background(c))

@Composable
private fun SinglePager(
    vm: MusicViewModel, actions: TrackActions, fresh: List<Track>, existing: Set<String>,
    onEdit: (Track) -> Unit
) {
    val pager = rememberPagerState { fresh.size }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Lied ${(pager.currentPage + 1).coerceAtMost(fresh.size)} von ${fresh.size}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { fresh[it].path }) { page ->
            val t = fresh[page]
            val unknown = t.artist == UNKNOWN_ARTIST
            val guess = remember(t.path, t.artist) { if (unknown) Library.guessArtistFromName(t) else null }
            val folderHint = remember(t.path) { Library.folders().firstOrNull { it.path == t.folder }?.suggestion }
            val similar = if (unknown) emptyList() else Names.similarArtists(t.artist, existing)
            val quick = (listOfNotNull(guess, folderHint) + similar).distinct()
            var menu by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                        TrackMenu(menu, { menu = false }, t, actions)
                    }
                }
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(16.dp))) {
                    Cover(listOf(t), t.title, Modifier.fillMaxSize())
                    IconButton(onClick = { vm.play(listOf(t), 0, "q") }, Modifier.align(Alignment.Center).size(64.dp)) {
                        Icon(Icons.Filled.PlayArrow, "Anhören", Modifier.size(48.dp), tint = Color.White)
                    }
                }
                Text(t.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    if (unknown) "Interpret: unbekannt" else "Interpret: ${t.artist}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (unknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (!unknown && t.artist !in existing) Text("Neuer Interpret in deiner Bibliothek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(
                    t.fileName + "\nOrdner: " + t.folder.removePrefix("/storage/emulated/0/") + "  ·  " + formatDuration(t.durationMs),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
                if (quick.isNotEmpty()) {
                    Text("Interpret schnell zuordnen:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (q in quick) LetterChip(q, q == t.artist, true) { vm.assignTrack(t.path, q, Store.moveOnAssign) }
                    }
                }
                var mv by remember { mutableStateOf(Store.moveOnAssign) }
                val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
                if (quick.isNotEmpty()) MoveCheckRow(mv) { mv = it; Store.setMoveOnAssign(appCtx, it) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.markSeen(listOf(t)) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Text("  Passt so")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { actions.onAddToPlaylist(t) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(20.dp)); Text("  Playlist")
                    }
                    OutlinedButton(onClick = { actions.onCover(t) }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Image, null, Modifier.size(20.dp)); Text("  Cover")
                    }
                }
                OutlinedButton(onClick = { onEdit(t) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)); Text("  Bearbeiten (Name, Interpret)")
                }
                OutlinedButton(onClick = { vm.blockTracks(listOf(t)) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Filled.Block, null, Modifier.size(20.dp)); Text("  Sperren (kein Lied)")
                }
            }
        }
    }
}
