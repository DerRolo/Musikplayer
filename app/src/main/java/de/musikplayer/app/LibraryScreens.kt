package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Ausgewählte Lieder (Gedrückt-halten startet die Auswahl). */
class Selection {
    val ids = mutableStateListOf<Long>()
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: Long) { if (!ids.remove(id)) ids.add(id) }
    fun setAll(all: List<Long>) { ids.clear(); ids.addAll(all) }
    fun clear() = ids.clear()
}

/** Alle Wörter der Suche müssen in Titel oder Interpret vorkommen (ohne Akzente, Groß-/Kleinschreibung egal). */
fun filterTracks(all: List<Track>, query: String): List<Track> {
    val words = Names.fold(query.trim()).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return all
    return all.filter { t ->
        val hay = t.sortKey + " " + t.artistKey
        words.all { it in hay }
    }
}

fun groupActions(vm: MusicViewModel, list: List<Track>, context: String, actions: TrackActions?): GroupActions =
    GroupActions(
        onPlay = { vm.play(list, 0, context) },
        onShuffle = { vm.playShuffled(list, context) },
        onPlayNext = { vm.playNext(list) },
        onAddToQueue = { vm.addToQueue(list) },
        onAddToPlaylist = actions?.let { a -> { a.onAddManyToPlaylist(list) } }
    )

/** Liste von Liedern mit Auswahlmodus. Tippen spielt ab, Gedrückt-halten wählt aus. */
@Composable
fun TrackListBody(
    vm: MusicViewModel,
    list: List<Track>,
    context: String,
    actions: TrackActions,
    sel: Selection,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = sel.active) { sel.clear() }
    Column(modifier) {
        if (sel.active) {
            val chosen = { vm.tracks.filter { it.id in sel.ids } }
            SelectionBar(
                count = sel.ids.size,
                onClose = { sel.clear() },
                onSelectAll = { sel.setAll(list.map { it.id }) },
                onPlaylist = { actions.onAddManyToPlaylist(chosen()); sel.clear() },
                onQueue = { vm.addToQueue(chosen()); sel.clear() },
                onPlayNext = { vm.playNext(chosen()); sel.clear() },
                onDelete = { actions.onDeleteMany(chosen()); sel.clear() }
            )
        }
        val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                TrackRow(
                    t, actions,
                    playing = t.id == playingId,
                    modifier = Modifier.fillMaxWidth(),
                    selectionMode = sel.active,
                    selected = t.id in sel.ids,
                    onLongClick = { sel.toggle(t.id) }
                ) {
                    if (sel.active) sel.toggle(t.id) else vm.play(list, i, context)
                }
            }
        }
    }
}

// ---------- Titel ----------

@Composable
fun SongsTab(vm: MusicViewModel, actions: TrackActions, sel: Selection) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    val all = remember(vm.tracks, vm.sortBy) { Library.sorted(vm.tracks, vm.sortBy) }
    val list = remember(all, query) { filterTracks(all, query) }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it }, "Titel oder Interpret suchen")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (query.isBlank()) "${all.size} Titel" else "${list.size} von ${all.size}",
                Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall
            )
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("Sortierung: ${vm.sortBy.label}") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (s in SortBy.values()) DropdownMenuItem(text = { Text(s.label) }, onClick = { sortMenu = false; vm.setSort(s) })
                }
            }
            IconButton(onClick = { vm.playShuffled(list, "all") }, enabled = list.isNotEmpty()) { Icon(Icons.Filled.Shuffle, "Zufällig") }
        }
        if (vm.loading && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (all.isEmpty()) "Keine Musik gefunden" else "Nichts gefunden")
            }
        } else {
            // Beim Suchen nur die Treffer abspielen; ohne Suche die ganze Bibliothek
            TrackListBody(vm, list, if (query.isBlank()) "all" else "s", actions, sel)
        }
    }
}

// ---------- Interpreten ----------

@Composable
fun ArtistsTab(
    vm: MusicViewModel,
    actions: TrackActions,
    sel: Selection,
    openRequest: String?,
    onConsumed: () -> Unit
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(openRequest) {
        if (openRequest != null) { open = openRequest; onConsumed() }
    }
    BackHandler(enabled = open != null) { open = null }

    val groups = remember(vm.tracks) { vm.tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val artist = open
    if (artist == null) {
        val names = remember(groups, query) {
            val q = Names.fold(query.trim())
            groups.keys.filter { q.isEmpty() || q in Names.fold(it) }
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, "Interpret suchen", Modifier.weight(1f))
                IconButton(onClick = { vm.setArtistGrid(!vm.artistGrid) }) {
                    Icon(if (vm.artistGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.artistGrid) "Listenansicht" else "Kachelansicht")
                }
            }
            if (names.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Interpreten") }
            } else if (vm.artistGrid) {
                LazyVerticalGrid(
                    GridCells.Adaptive(112.dp), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(names, key = { it }) { name ->
                        val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                        GroupTile(name, list, list.size, true, groupActions(vm, list, "a:$name", actions)) { open = name }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(names, key = { it }) { name ->
                        val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                        GroupRow(name, list, list.size, true, groupActions(vm, list, "a:$name", actions)) { open = name }
                    }
                }
            }
        }
    } else {
        val list = remember(groups, artist) { (groups[artist] ?: emptyList()).sortedBy { it.sortKey } }
        val ctx = "a:$artist"
        var menu by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = null }) { Text("‹ Zurück") }
                Text(artist, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { vm.playShuffled(list, ctx) }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Alle Lieder …") }
                    GroupMenu(menu, { menu = false }, groupActions(vm, list, ctx, actions))
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Lieder") }
            } else {
                TrackListBody(vm, list, ctx, actions, sel)
            }
        }
    }
}

// ---------- Playlists ----------

@Composable
fun PlaylistsTab(
    vm: MusicViewModel,
    actions: TrackActions,
    onNew: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDeleteList: (Playlist) -> Unit,
    openRequest: String?,
    onConsumed: () -> Unit
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openRequest) {
        if (openRequest != null) { openId = openRequest; onConsumed() }
    }
    BackHandler(enabled = openId != null) { openId = null }
    val open = vm.playlists.firstOrNull { it.id == openId }
    if (open == null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onNew, Modifier.padding(horizontal = 8.dp)) { Text("+ Neue Playlist") }
            if (vm.playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Playlists – wähle Lieder oder einen Interpreten aus und füge sie hinzu")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.playlists, key = { it.id }) { p ->
                    val list = vm.playlistEntries(p.id).map { it.second }
                    GroupRow(p.name, list, p.trackPaths.size, false, groupActions(vm, list, "p:${p.id}", null)) { openId = p.id }
                }
            }
        }
    } else {
        val entries = vm.playlistEntries(open.id)
        val list = entries.map { it.second }
        val ctx = "p:${open.id}"
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openId = null }) { Text("‹ Zurück") }
                Text(open.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { vm.playShuffled(list, ctx) }, enabled = list.isNotEmpty()) { Icon(Icons.Filled.Shuffle, "Zufällig") }
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onRename(open) }) { Text("Umbenennen") }
                TextButton(onClick = { onDeleteList(open); openId = null }) { Text("Playlist löschen", color = MaterialTheme.colorScheme.error) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                // Index in der gespeicherten Liste als Schlüssel: dasselbe Lied darf mehrfach vorkommen
                itemsIndexed(entries, key = { _, e -> e.first }) { i, (idx, t) ->
                    val rowActions = actions.copy(onRemoveFromPlaylist = { vm.removeFromPlaylistAt(open.id, idx) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackRow(t, rowActions, modifier = Modifier.weight(1f)) { vm.play(list, i, ctx) }
                        IconButton(onClick = { vm.movePlaylistTrack(open.id, idx, entries[i - 1].first) }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, "Nach oben")
                        }
                        IconButton(onClick = { vm.movePlaylistTrack(open.id, idx, entries[i + 1].first) }, enabled = i < entries.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, "Nach unten")
                        }
                    }
                }
            }
        }
    }
}
