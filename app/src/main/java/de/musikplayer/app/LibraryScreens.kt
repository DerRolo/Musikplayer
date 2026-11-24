@file:OptIn(ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reagiert einmal, wenn der Reiter ein zweites Mal angetippt wurde (zurück auf die oberste Ebene). */
@Composable
fun OnReset(reset: Int, block: () -> Unit) {
    var handled by rememberSaveable { mutableIntStateOf(reset) }
    LaunchedEffect(reset) { if (reset != handled) { handled = reset; block() } }
}

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

fun groupActions(vm: MusicViewModel, list: List<Track>, context: String, actions: TrackActions?, onBlock: (() -> Unit)? = null): GroupActions =
    GroupActions(
        onPlay = { vm.play(list, 0, context) },
        onShuffle = { vm.playShuffled(list, context) },
        onPlayNext = { vm.playNext(list) },
        onAddToQueue = { vm.addToQueue(list) },
        onAddToPlaylist = actions?.let { a -> { a.onAddManyToPlaylist(list) } },
        onBlock = onBlock
    )

/** Liste von Liedern mit Auswahlmodus. Tippen spielt ab, Gedrückt-halten wählt aus. */
@Composable
fun TrackListBody(
    vm: MusicViewModel,
    list: List<Track>,
    context: String,
    actions: TrackActions,
    sel: Selection,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null
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
                onDelete = { actions.onDeleteMany(chosen()); sel.clear() },
                onBlock = { actions.onBlockMany(chosen()); sel.clear() },
                onAssign = { actions.onAssignMany(chosen()); sel.clear() }
            )
        }
        val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
        Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (header != null) item(key = "header") { header() }
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
        ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

// ---------- Bibliothek (Titel + Interpreten) ----------

@Composable
fun LibraryTab(vm: MusicViewModel, actions: TrackActions, sel: Selection, openArtist: String?, onArtistConsumed: () -> Unit, reset: Int) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val songsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    OnReset(reset) { if (mode == 0) scope.launch { songsState.animateScrollToItem(0) } }
    LaunchedEffect(openArtist) { if (openArtist != null) mode = 1 }
    // Die beiden Umschalter stehen unter dem Suchfeld
    val switch: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LetterChip("Bibliothek", mode == 0, true) { mode = 0 }
            LetterChip("Interpreten", mode == 1, true) { mode = 1 }
        }
    }
    Column(Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(mode) {
            if (mode == 0) SongsTab(vm, actions, sel, songsState, switch)
            else ArtistsTab(vm, actions, sel, openArtist, onArtistConsumed, reset, switch)
        }
    }
}

// ---------- Titel ----------

@Composable
fun SongsTab(vm: MusicViewModel, actions: TrackActions, sel: Selection, listState: LazyListState, switch: @Composable () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(vm.tracks, vm.sortBy, vm.sortLevel) { Library.sorted(vm.tracks, vm.sortBy, vm.sortLevel) }
    val list = remember(all, query) { filterTracks(all, query) }
    var lastSort by rememberSaveable { mutableIntStateOf(vm.sortBy.ordinal * 3 + vm.sortLevel) }
    LaunchedEffect(vm.sortBy, vm.sortLevel) {
        val now = vm.sortBy.ordinal * 3 + vm.sortLevel
        if (now != lastSort) { lastSort = now; listState.scrollToItem(0) }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it }, "Titel oder Interpret suchen")
        switch()
        if (vm.loading && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (all.isEmpty()) "Keine Musik gefunden" else "Nichts gefunden")
            }
        } else {
            val sortLabel = run {
                val naturalDown = vm.sortBy == SortBy.Added || vm.sortBy == SortBy.Duration || vm.sortBy == SortBy.Plays
                val down = if (vm.sortLevel == 1) !naturalDown else naturalDown
                "Sortierung: " + vm.sortBy.label + if (vm.sortLevel != 2) (if (down) " ↓" else " ↑") else " (aus)"
            }
            // Beim Suchen nur die Treffer abspielen; ohne Suche die ganze Bibliothek
            TrackListBody(
                vm, list, if (query.isBlank()) "all" else "s", actions, sel, listState = listState,
                header = {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (query.isBlank()) "${all.size} Titel" else "${list.size} von ${all.size}",
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
                        )
                        SortMenu(sortLabel, SortBy.values().map { it.label }, vm.sortBy.ordinal) { vm.cycleSort(SortBy.values()[it]) }
                        IconButton(onClick = { vm.playShuffled(list, "all") }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
                    }
                }
            )
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
    onConsumed: () -> Unit,
    reset: Int,
    switch: @Composable () -> Unit
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf("") } // "" Überblick, "*" alle Lieder, sonst Albumname
    val gridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ovState = rememberLazyListState()
    val albumRowState = rememberLazyListState()
    var lastOpen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        if (open != lastOpen) { lastOpen = open; ovState.scrollToItem(0); albumRowState.scrollToItem(0) }
    }
    OnReset(reset) {
        if (view != "" || open != null) { view = ""; open = null }
        else scope.launch { if (vm.artistGrid) gridState.animateScrollToItem(0) else artistsListState.animateScrollToItem(0) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var letter by rememberSaveable { mutableStateOf<String?>(null) }
    var aSort by rememberSaveable { mutableIntStateOf(0) } // 0 A–Z, 1 Z–A, 2 meiste Lieder, 3 meistgehört, 4 keine Sortierung
    var lastSort by rememberSaveable { mutableIntStateOf(aSort) }
    LaunchedEffect(aSort) {
        if (aSort != lastSort) {
            lastSort = aSort
            gridState.scrollToItem(0); artistsListState.scrollToItem(0)
        }
    }
    LaunchedEffect(openRequest) {
        if (openRequest != null) { open = openRequest; view = ""; onConsumed() }
    }
    BackHandler(enabled = open != null) { open = null }

    val groups = remember(vm.tracks) { vm.tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val artist = open
    if (artist == null) {
        val available = remember(groups) { groups.keys.map { Names.letterOf(it) }.toSet() }
        val activeLetter = letter?.takeIf { it in available }
        val names = remember(groups, query, activeLetter, aSort) {
            val q = Names.fold(query.trim())
            val f = groups.keys.filter { (q.isEmpty() || q in Names.fold(it)) && (activeLetter == null || Names.letterOf(it) == activeLetter) }
            when (aSort) {
                0 -> f
                1 -> f.reversed()
                2 -> f.sortedWith(compareByDescending<String> { groups[it]?.size ?: 0 }.thenBy { Names.fold(it) })
                3 -> {
                    val plays = HashMap<String, Int>()
                    vm.tracks.forEach { t -> plays[t.artist] = (plays[t.artist] ?: 0) + vm.plays(t.path) }
                    f.sortedWith(compareByDescending<String> { plays[it] ?: 0 }.thenBy { Names.fold(it) })
                }
                else -> { val first = HashMap<String, Int>(); vm.tracks.forEachIndexed { i, t -> first.putIfAbsent(t.artist, i) }; f.sortedBy { first[it] ?: Int.MAX_VALUE } }
            }
        }
        val sortOptions = listOf("Name A–Z", "Name Z–A", "Meiste Lieder", "Meistgehört", "Ohne Sortierung")
        val sortRow: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${names.size} Interpreten", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                SortMenu("Sortierung: " + sortOptions[aSort], sortOptions, aSort) { aSort = it }
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, "Interpret suchen", Modifier.weight(1f))
                IconButton(onClick = { vm.showArtistsAsGrid(!vm.artistGrid) }) {
                    Icon(if (vm.artistGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.artistGrid) "Listenansicht" else "Kachelansicht")
                }
            }
            switch()
            LetterBar(activeLetter, available) { letter = it }
            if (names.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Interpreten") }
            } else if (vm.artistGrid) {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(112.dp), Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { sortRow() }
                        items(names, key = { it }) { name ->
                            val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                            GroupTile(name, list, list.size, true, groupActions(vm, list, "a:$name", actions) { vm.blockArtist(name) }) { view = ""; open = name }
                        }
                    }
                    GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = artistsListState) {
                        item { sortRow() }
                        items(names, key = { it }) { name ->
                            val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                            GroupRow(name, list, list.size, true, groupActions(vm, list, "a:$name", actions) { vm.blockArtist(name) }) { view = ""; open = name }
                        }
                    }
                    ListScrollbar(artistsListState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    } else {
        val fullList = remember(groups, artist) { (groups[artist] ?: emptyList()).sortedBy { it.sortKey } }
        val albums = remember(fullList) { fullList.groupBy { it.albumName }.filterKeys { it.isNotBlank() }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
        val top = remember(fullList, vm.current) { fullList.filter { vm.plays(it.path) > 0 }.sortedByDescending { vm.plays(it.path) }.take(5) }
        val v = view
        BackHandler(enabled = v != "") { view = "" }
        val shown = when {
            v == "" || v == "*" -> fullList
            else -> albums[v].orEmpty()
        }
        val ctx = "a:$artist"
        var menu by remember { mutableStateOf(false) }
        var editAlbum by remember { mutableStateOf(false) }
        val isAlbum = v != "" && v != "*"
        LaunchedEffect(albums.keys, v) { if (isAlbum && !albums.containsKey(v)) view = "" }
        if (editAlbum && isAlbum) {
            AlbumEditDialog(
                artist, v, shown,
                onDismiss = { editAlbum = false },
                onRenameAlbum = { n -> vm.renameAlbum(shown, n); view = n },
                onRenameFiles = { vm.renameAlbum(shown, v); vm.renameFiles(it) }
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (v != "") view = "" else open = null }) { Text("‹ Zurück") }
                Text(
                    if (v == "" ) artist else if (v == "*") "$artist · alle Lieder" else v,
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { vm.playShuffled(shown, ctx) }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Alle Lieder …") }
                    GroupMenu(
                        menu, { menu = false },
                        if (isAlbum) groupActions(vm, shown, ctx, actions) { vm.blockTracks(shown); view = "" }
                            .copy(onEdit = { editAlbum = true }, onDelete = { actions.onDeleteMany(shown) })
                        else groupActions(vm, shown, ctx, actions) { vm.blockArtist(artist); open = null }
                    )
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Lieder") }
            } else if (v != "") {
                TrackListBody(vm, shown, ctx, actions, sel)
            } else {
                val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = ovState) {
                        if (top.isNotEmpty()) {
                            item { SectionTitle("Meistgehört") }
                            itemsIndexed(top, key = { _, t -> "top" + t.id }) { i, t ->
                                TrackRow(t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth()) { vm.play(top, i, ctx) }
                            }
                        }
                        if (albums.isNotEmpty()) {
                            item { SectionTitle("Alben (${albums.size})") }
                            item {
                                androidx.compose.foundation.lazy.LazyRow(state = albumRowState, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(albums.entries.toList(), key = { it.key }) { (name, l) -> AlbumTile(name, l) { view = name } }
                                }
                            }
                        }
                        item { SectionTitle("Lieder (${fullList.size})") }
                        itemsIndexed(fullList.take(5), key = { _, t -> "all" + t.id }) { i, t ->
                            TrackRow(t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth()) { vm.play(fullList, i, ctx) }
                        }
                        if (fullList.size > 5) item {
                            TextButton(onClick = { view = "*" }, Modifier.padding(horizontal = 8.dp)) { Text("Alle ${fullList.size} Lieder anzeigen ›") }
                        }
                    }
                    ListScrollbar(ovState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
    )
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
    onConsumed: () -> Unit,
    reset: Int,
    /** true = Reiter „Hörbücher & Podcasts“ (eigene Listen, getrennt von den Musik-Playlists). */
    audio: Boolean = false
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val plListState = rememberLazyListState()
    val plGridState = rememberLazyGridState()
    val plScope = rememberCoroutineScope()
    OnReset(reset) { if (openId != null) openId = null else plScope.launch { plListState.animateScrollToItem(0) } }
    LaunchedEffect(openRequest) {
        if (openRequest != null) { openId = openRequest; onConsumed() }
    }
    BackHandler(enabled = openId != null) { openId = null }
    val open = vm.playlists.firstOrNull { it.id == openId }
    if (open == null) {
        val ordered = vm.playlistsByUse().filter { it.audio == audio }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNew) { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Text(if (audio) "  Neue Liste" else "  Neue Playlist") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.showPlaylistsAsGrid(!vm.playlistGrid) }) {
                    Icon(if (vm.playlistGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.playlistGrid) "Listenansicht" else "Kachelansicht")
                }
            }
            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Noch keine Hörbuch- oder Podcast-Listen. Lege eine Liste an und füge Folgen oder Kapitel über „Zu Playlist hinzufügen“ hinzu (dort „Hörbuch-Listen anzeigen“).",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (vm.playlistGrid) {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), state = plGridState, contentPadding = PaddingValues(6.dp)) {
                        items(ordered, key = { it.id }) { p ->
                            PlaylistTile(vm, p, onOpen = { openId = p.id }, onEdit = { onRename(p) }, onDelete = { onDeleteList(p) })
                        }
                    }
                    GridScrollbar(plGridState, Modifier.align(Alignment.CenterEnd))
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = plListState) {
                        items(ordered, key = { it.id }) { p ->
                            PlaylistRow(vm, p, onOpen = { openId = p.id }, onEdit = { onRename(p) }, onDelete = { onDeleteList(p) })
                        }
                    }
                    ListScrollbar(plListState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    } else {
        val entries = vm.playlistEntries(open.id)
        val ctx = "p:${open.id}"
        var mode by rememberSaveable { mutableIntStateOf(0) } // 0 eigene Reihenfolge, 1 zuletzt hinzugefügt, 2 Titel, 3 Interpret, 4 Länge
        val modes = listOf("Eigene Reihenfolge", "Zuletzt hinzugefügt", "Titel", "Interpret", "Länge")
        val order = remember(entries) { entries.toMutableStateList() }
        val shown: List<Pair<Int, Track>> = when (mode) {
            0 -> order
            1 -> if (open.id == FAV_ID) entries else entries.reversed()
            2 -> entries.sortedBy { it.second.sortKey }
            3 -> entries.sortedWith(compareBy({ it.second.artistKey }, { it.second.sortKey }))
            else -> entries.sortedByDescending { it.second.durationMs }
        }
        val list = shown.map { it.second }
        val listState = rememberLazyListState()
        val orderRef by rememberUpdatedState(order)
        val rs = remember(open.id) { ReorderState(listState) { from, to -> orderRef.add(to, orderRef.removeAt(from)) } }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        Column(Modifier.fillMaxSize()) {
            PlaylistHeader(
                vm, open, list,
                onBack = { openId = null },
                onStart = { if (vm.shuffle) vm.playShuffled(list, ctx) else vm.play(list, 0, ctx) },
                onEdit = { onRename(open) },
                onDelete = { onDeleteList(open); openId = null }
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SortMenu("Sortierung: " + modes[mode], modes, mode) { mode = it }
            }
            ReorderEffects(rs)
            Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                // Index in der gespeicherten Liste als Schlüssel: dasselbe Lied darf mehrfach vorkommen
                itemsIndexed(shown, key = { _, e -> e.first }) { i, (idx, t) ->
                    val rowActions = actions.copy(onRemoveFromPlaylist = { vm.removeFromPlaylistAt(open.id, idx) })
                    if (mode == 0) {
                        val pos by rememberUpdatedState(i)
                        val isDrag = rs.dragging == i
                        Box(
                            Modifier.fillMaxWidth()
                                .then(
                                    if (isDrag) Modifier.zIndex(1f).graphicsLayer { translationY = rs.delta; shadowElevation = 16f }
                                    else Modifier.animateItemPlacement()
                                )
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); rs.start(pos) },
                                        onDrag = { change, offset -> change.consume(); rs.drag(offset.y) },
                                        onDragEnd = { rs.end(scope) { from, to -> vm.movePlaylistTrack(open.id, entries[from].first, entries[to].first) } },
                                        onDragCancel = { rs.end(scope) { from, to -> vm.movePlaylistTrack(open.id, entries[from].first, entries[to].first) } }
                                    )
                                }
                        ) {
                            TrackRow(t, rowActions, modifier = Modifier.fillMaxWidth(), compact = true, swipe = false) { vm.play(list, i, ctx) }
                        }
                    } else {
                        TrackRow(t, rowActions, modifier = Modifier.fillMaxWidth()) { vm.play(list, i, ctx) }
                    }
                }
            }
            ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}
