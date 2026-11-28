@file:OptIn(ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.SegmentedButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalContentColor
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

/** Auswahl-Leiste für eine Liste von Liedern (überall gleich). */
@Composable
fun TrackSelectionBar(vm: MusicViewModel, list: List<Track>, actions: TrackActions, sel: Selection, removeLabel: String? = null, onRemove: () -> Unit = {}, onDone: () -> Unit = {}) {
    val done = { sel.clear(); onDone() }
    val chosen = { list.filter { it.id in sel.ids } }
    SelectionBar(
        count = sel.ids.size,
        onClose = { done() },
        onSelectAll = { sel.setAll(list.map { it.id }) },
        onPlaylist = { actions.onAddManyToPlaylist(chosen()); done() },
        onQueue = { vm.addToQueue(chosen()); done() },
        onPlayNext = { vm.playNext(chosen()); done() },
        onDelete = { actions.onDeleteMany(chosen()); done() },
        onBlock = { actions.onBlockMany(chosen()); done() },
        onAssign = { actions.onAssignMany(chosen()); done() },
        onCover = { actions.onCoverMany(chosen()); done() },
        onNormalize = actions.onNormalize?.let { nz -> { nz(chosen()); done() } },
        removeLabel = removeLabel,
        onRemove = { onRemove(); done() }
    )
}

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
        if (sel.active) TrackSelectionBar(vm, list, actions, sel)
        val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
        val swipeOk = rememberSwipeAllowed(listState)
        Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSwipeAllowed provides swipeOk) {
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
        }
        ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

// ---------- Bibliothek (Titel · Interpreten · Alben) ----------

/**
 * Umschalter oben in der Bibliothek (orange = aktiv): Bibliothek · Interpreten · Alben.
 * An derselben Stelle steht bei Playlists und Hörbüchern „+ Neue …“ – so sieht jeder Reiter gleich aus.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibrarySwitch(mode: Int, onMode: (Int) -> Unit) {
    val labels = listOf("Bibliothek", "Interpreten", "Alben")
    val icons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.Person, Icons.Filled.Album)
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = mode == i,
                onClick = { onMode(i) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, labels.size),
                colors = androidx.compose.material3.SegmentedButtonDefaults.colors(activeContainerColor = ORANGE, activeContentColor = Color.Black),
                icon = { Icon(icons[i], null, Modifier.size(18.dp)) }
            ) { Text(l, maxLines = 1) }
        }
    }
}

/** Oranger Knopf an der Stelle des Umschalters (Playlists/Hörbücher): „+ Neue Playlist“ usw. */
@Composable
fun NewListButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        androidx.compose.material3.Button(
            onClick = onClick, modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = ORANGE, contentColor = Color.Black)
        ) { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Text("  $label") }
    }
}

@Composable
fun LibraryTab(vm: MusicViewModel, actions: TrackActions, sel: Selection, openArtist: String?, onArtistConsumed: () -> Unit, reset: Int, originBack: (() -> Unit)? = null, onArtistClosed: () -> Unit = {}, onOpenPlaylist: (String) -> Unit = {}) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    val songsState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    OnReset(reset) { if (mode == 0) scope.launch { songsState.animateScrollToItem(0) } }
    var modeBefore by rememberSaveable { mutableIntStateOf(0) }
    var fromRequest by rememberSaveable { mutableStateOf(false) }
    // Interpret/Album aus der Suche oder aus „Alben“ öffnen; Zurück führt wieder dorthin (mit Scroll-Position)
    var innerRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val request = openArtist ?: innerRequest
    LaunchedEffect(request) { if (request != null) { if (mode != 1) modeBefore = mode; fromRequest = true; mode = 1 } }
    val openInside: (String) -> Unit = { innerRequest = it }
    val switch: @Composable () -> Unit = { LibrarySwitch(mode) { mode = it; fromRequest = false } }
    Column(Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(mode) {
            when (mode) {
                0 -> SongsTab(vm, actions, sel, songsState, switch, openInside, onOpenPlaylist)
                1 -> ArtistsTab(
                    vm, actions, sel, request, { innerRequest = null; onArtistConsumed() }, reset, switch, originBack,
                    onClosed = { onArtistClosed(); if (fromRequest) { fromRequest = false; mode = modeBefore } }
                )
                else -> AlbumsTab(vm, actions, reset, switch, openInside)
            }
        }
    }
}

// ---------- Titel ----------

@Composable
fun SongsTab(
    vm: MusicViewModel, actions: TrackActions, sel: Selection, listState: LazyListState, switch: @Composable () -> Unit,
    onOpenArtist: (String) -> Unit = {}, onOpenPlaylist: (String) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf("") }
    val ssel = remember { Selection() }
    val searchState = rememberLazyListState()
    val all = remember(vm.tracks, vm.sortBy, vm.sortLevel) { Library.sorted(vm.tracks, vm.sortBy, vm.sortLevel) }
    var lastSort by rememberSaveable { mutableIntStateOf(vm.sortBy.ordinal * 3 + vm.sortLevel) }
    LaunchedEffect(vm.sortBy, vm.sortLevel) {
        val now = vm.sortBy.ordinal * 3 + vm.sortLevel
        if (now != lastSort) { lastSort = now; listState.scrollToItem(0) }
    }
    Column(Modifier.fillMaxSize()) {
        // Gleiche Suche wie im Such-Reiter: Vorschläge, Interpreten, Alben, Playlists, Hörbücher, Titel
        SearchField(query, { query = it }, "Titel, Interpreten, Alben, Playlists", onSubmit = { picked = query; vm.addRecentSearch(query) })
        switch()
        if (query.isNotBlank()) {
            GlobalSearchBody(vm, actions, query, { query = it }, picked, ssel, onOpenArtist, onOpenPlaylist, searchState)
        } else if (vm.loading && all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val sortLabel = run {
                val naturalDown = vm.sortBy == SortBy.Added || vm.sortBy == SortBy.Duration || vm.sortBy == SortBy.Plays
                val down = if (vm.sortLevel == 1) !naturalDown else naturalDown
                vm.sortBy.label + if (vm.sortLevel != 2) (if (down) " ↓" else " ↑") else " (aus)"
            }
            // Herz = nur Lieblingssongs (wie der Stern bei Interpreten)
            val shownList = remember(all, vm.onlyFavSongs, vm.favoritePaths) { if (vm.onlyFavSongs) all.filter { vm.isFavorite(it.path) } else all }
            val ctx = if (vm.onlyFavSongs) "favsongs" else "all"
            // Feste Leiste: Anzahl · Herz · Abspielen · Zufällig · Kachel/Liste · Sortierung
            ListToolbar(
                if (vm.onlyFavSongs) "${shownList.size} Lieblingssongs" else "${all.size} Titel",
                sortLabel, SortBy.values().map { it.label }, vm.sortBy.ordinal, { vm.cycleSort(SortBy.values()[it]) }
            ) {
                IconButton(onClick = { vm.onlyFavSongs = !vm.onlyFavSongs }) {
                    Icon(if (vm.onlyFavSongs) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Nur Lieblingssongs", tint = if (vm.onlyFavSongs) HEART_PINK else LocalContentColor.current)
                }
                IconButton(onClick = { vm.playInOrder(shownList, ctx) }, enabled = shownList.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, "Der Reihe nach abspielen") }
                IconButton(onClick = { vm.playShuffled(shownList, ctx) }, enabled = shownList.isNotEmpty()) { Icon(Icons.Filled.Shuffle, "Zufällig abspielen") }
                IconButton(onClick = { vm.showSongsAsGrid(!vm.songGrid) }) {
                    Icon(if (vm.songGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.songGrid) "Listenansicht" else "Kachelansicht")
                }
            }
            if (shownList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (all.isEmpty()) "Keine Musik gefunden" else "Noch keine Lieblingssongs – tippe bei einem Lied auf das Herz")
                }
            } else if (vm.songGrid) {
                val gridState = rememberLazyGridState()
                val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), state = gridState, contentPadding = PaddingValues(8.dp)) {
                        itemsIndexed(shownList, key = { _, t -> t.id }) { i, t ->
                            TrackTile(t, t.id == playingId) { vm.play(shownList, i, ctx) }
                        }
                    }
                    GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                }
            } else {
                TrackListBody(vm, shownList, ctx, actions, sel, listState = listState)
            }
        }
    }
}

/** Lied als Kachel (Kachelansicht der Bibliothek): Cover, Titel, Interpret. */
@Composable
fun TrackTile(t: Track, playing: Boolean, onClick: () -> Unit) {
    Column(Modifier.padding(4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Cover(listOf(t), t.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), sizePx = 300)
        Text(
            t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- Alben ----------

/** Alle Alben der Bibliothek (Album + Interpret). Gleicher Aufbau wie Interpreten. Tippen öffnet das Album. */
@Composable
fun AlbumsTab(vm: MusicViewModel, actions: TrackActions, reset: Int, switch: @Composable () -> Unit, onOpen: (String) -> Unit) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    OnReset(reset) { scope.launch { if (vm.albumGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } }
    var query by rememberSaveable { mutableStateOf("") }
    var letter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortIdx by rememberSaveable { mutableIntStateOf(Store.sortPref("albums", 0)) }
    val sorts = listOf("Meistgehört", "Name A–Z", "Name Z–A", "Interpret A–Z", "Meiste Lieder", "Zuletzt hinzugefügt")
    var lastSort by rememberSaveable { mutableIntStateOf(sortIdx) }
    LaunchedEffect(sortIdx) { if (sortIdx != lastSort) { lastSort = sortIdx; gridState.scrollToItem(0); listState.scrollToItem(0) } }
    data class Alb(val artist: String, val name: String, val tracks: List<Track>)
    val albums = remember(vm.tracks) {
        vm.tracks.filter { it.albumName.isNotBlank() }.groupBy { it.artist to it.albumName }
            .map { (k, l) -> Alb(k.first, k.second, l.sortedBy { it.sortKey }) }
    }
    val available = remember(albums) { albums.map { Names.letterOf(it.name) }.toSet() }
    val active = letter?.takeIf { it in available }
    val shown = remember(albums, query, active, sortIdx) {
        val q = Names.fold(query.trim())
        val f = albums.filter { (q.isEmpty() || q in Names.fold(it.name) || q in Names.fold(it.artist)) && (active == null || Names.letterOf(it.name) == active) }
        when (sortIdx) {
            1 -> f.sortedBy { Names.fold(it.name) }
            2 -> f.sortedByDescending { Names.fold(it.name) }
            3 -> f.sortedWith(compareBy({ Names.fold(it.artist) }, { Names.fold(it.name) }))
            4 -> f.sortedWith(compareByDescending<Alb> { it.tracks.size }.thenBy { Names.fold(it.name) })
            5 -> f.sortedByDescending { a -> a.tracks.maxOf { it.modified } }
            else -> f.sortedWith(compareByDescending<Alb> { a -> a.tracks.sumOf { vm.plays(it.path) } }.thenBy { Names.fold(it.name) })
        }
    }
    // Feste Leiste über der Liste: Anzahl · Kachel/Liste · Sortierung
    val sortRow: @Composable () -> Unit = {
        ListToolbar("${shown.size} Alben", sorts[sortIdx], sorts, sortIdx, { sortIdx = it; vm.saveSort("albums", it) }) {
            IconButton(onClick = { vm.showAlbumsAsGrid(!vm.albumGrid) }) {
                Icon(if (vm.albumGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.albumGrid) "Listenansicht" else "Kachelansicht")
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it }, "Album oder Interpret suchen")
        switch()
        LetterBar(active, available) { letter = it }
        sortRow()
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (albums.isEmpty()) "Keine Alben – Alben kommen aus den Tags oder aus Album-Ordnern" else "Nichts gefunden", textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(24.dp)) }
        } else if (vm.albumGrid) {
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(GridCells.Adaptive(112.dp), Modifier.fillMaxSize(), state = gridState, contentPadding = PaddingValues(8.dp)) {
                    items(shown, key = { it.artist + "\u0000" + it.name }) { a ->
                        GroupTile(a.name, a.tracks, a.tracks.size, false, groupActions(vm, a.tracks, "album:${a.artist}:${a.name}", actions)) { onOpen(a.artist + "\u0000album:" + a.name) }
                    }
                }
                GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(shown, key = { it.artist + "\u0000" + it.name }) { a ->
                        GroupRow(a.name + "  ·  " + a.artist, a.tracks, a.tracks.size, false, groupActions(vm, a.tracks, "album:${a.artist}:${a.name}", actions)) { onOpen(a.artist + "\u0000album:" + a.name) }
                    }
                }
                ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
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
    switch: @Composable () -> Unit,
    originBack: (() -> Unit)? = null,
    onClosed: () -> Unit = {}
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open != null) wasOpen = true
        else if (wasOpen) { wasOpen = false; onClosed() }
    }
    var view by rememberSaveable { mutableStateOf("") } // "" Überblick, "*" alle Lieder, sonst Albumname
    val gridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ovState = rememberLazyListState()
    val albumRowState = rememberLazyListState()
    var lastOpen by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        if (open != lastOpen) { lastOpen = open; view = ""; ovState.scrollToItem(0); albumRowState.scrollToItem(0) }
    }
    OnReset(reset) {
        if (view != "" || open != null) { view = ""; open = null }
        else scope.launch { if (vm.artistGrid) gridState.animateScrollToItem(0) else artistsListState.animateScrollToItem(0) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var letter by rememberSaveable { mutableStateOf<String?>(null) }
    var aSort by rememberSaveable { mutableIntStateOf(Store.sortPref("artists", 3)) } // 0 A–Z, 1 Z–A, 2 meiste Lieder, 3 meistgehört (Standard), 4 keine Sortierung
    var lastSort by rememberSaveable { mutableIntStateOf(aSort) }
    LaunchedEffect(aSort) {
        if (aSort != lastSort) {
            lastSort = aSort
            gridState.scrollToItem(0); artistsListState.scrollToItem(0)
        }
    }
    // Direkt in ein Album geöffnet (aus Suche/Alben): Zurück schließt dann ganz und führt zum Ursprung zurück
    var directView by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            val a = openRequest.substringBefore('\u0000')
            val v = openRequest.substringAfter('\u0000', "")
            lastOpen = a      // verhindert, dass die Ansicht gleich wieder auf den Überblick springt
            open = a; view = v; directView = v.isNotEmpty()
            onConsumed()
        }
    }
    BackHandler(enabled = open != null) { open = null; view = ""; originBack?.invoke() }

    val groups = remember(vm.tracks) { vm.tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val artist = open
    if (artist == null) {
        val available = remember(groups) { groups.keys.map { Names.letterOf(it) }.toSet() }
        val activeLetter = letter?.takeIf { it in available }
        val names = remember(groups, query, activeLetter, aSort, vm.onlyFavArtists, vm.favArtists) {
            val q = Names.fold(query.trim())
            val f = groups.keys.filter { (q.isEmpty() || q in Names.fold(it)) && (activeLetter == null || Names.letterOf(it) == activeLetter) && (!vm.onlyFavArtists || it in vm.favArtists) }
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
        // Zeile über der Liste: Anzahl · Stern (nur Favoriten) · Liste/Kachel · Sortierung – gleich wie bei Alben, Playlists, Hörbüchern
        // Feste Leiste über der Liste: Anzahl · Stern (nur Favoriten) · Kachel/Liste · Sortierung
        val sortRow: @Composable () -> Unit = {
            ListToolbar("${names.size} Interpreten", sortOptions[aSort], sortOptions, aSort, { aSort = it; vm.saveSort("artists", it) }) {
                IconButton(onClick = { vm.onlyFavArtists = !vm.onlyFavArtists }) {
                    Icon(if (vm.onlyFavArtists) Icons.Filled.Star else Icons.Filled.StarBorder, "Nur Favoriten-Interpreten", tint = if (vm.onlyFavArtists) ORANGE else LocalContentColor.current)
                }
                IconButton(onClick = { vm.showArtistsAsGrid(!vm.artistGrid) }) {
                    Icon(if (vm.artistGrid) Icons.Filled.ViewList else Icons.Filled.GridView, if (vm.artistGrid) "Listenansicht" else "Kachelansicht")
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, "Interpret suchen")
            switch()
            LetterBar(activeLetter, available) { letter = it }
            sortRow()
            if (names.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (vm.onlyFavArtists) "Keine Favoriten-Interpreten" + (if (activeLetter != null) " mit „$activeLetter“" else "") else "Keine Interpreten") }
            } else if (vm.artistGrid) {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        GridCells.Adaptive(112.dp), Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(names, key = { it }) { name ->
                            val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                            GroupTile(name, list, list.size, true, groupActions(vm, list, "a:$name", actions) { vm.blockArtist(name) }.copy(onStar = { vm.toggleFavArtist(name) }, starred = name in vm.favArtists), artistCoverName = name) { view = ""; open = name }
                        }
                    }
                    GridScrollbar(gridState, Modifier.align(Alignment.CenterEnd))
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), state = artistsListState) {
                        items(names, key = { it }) { name ->
                            val list = remember(groups, name) { (groups[name] ?: emptyList()).sortedBy { it.sortKey } }
                            ArtistSwipe(vm, name) {
                                GroupRow(name, list, list.size, true, groupActions(vm, list, "a:$name", actions) { vm.blockArtist(name) }.copy(onStar = { vm.toggleFavArtist(name) }, starred = name in vm.favArtists), star = name in vm.favArtists, artistCoverName = name) { view = ""; open = name }
                            }
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
        BackHandler(enabled = v != "") {
            if (directView) { directView = false; view = ""; open = null; originBack?.invoke() } else view = ""
        }
        val albumDetail = v.startsWith("album:")
        val detailAlbumName = if (albumDetail) v.removePrefix("album:") else ""
        val shown = if (albumDetail) albums[detailAlbumName].orEmpty() else fullList
        val ctx = if (albumDetail) "album:$artist:$detailAlbumName" else "a:$artist"
        var menu by remember { mutableStateOf(false) }
        var editAlbumFor by remember { mutableStateOf<String?>(null) }
        var albumCover by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
        var artistCoverPicker by remember { mutableStateOf(false) }
        albumCover?.let { (an, tl) ->
            CoverPickerDialog(
                initialQuery = "$artist $an",
                onDismiss = { albumCover = null },
                onUri = { vm.setTrackCover(tl, uri = it) },
                onUrl = { vm.setTrackCover(tl, url = it) },
                onReset = if (vm.hasOwnCover(tl)) ({ vm.setTrackCover(tl) }) else null
            )
        }
        if (artistCoverPicker) CoverPickerDialog(
            initialQuery = artist,
            onDismiss = { artistCoverPicker = false },
            onUri = { vm.setArtistCover(artist, uri = it) },
            onUrl = { vm.setArtistCover(artist, url = it) },
            onReset = if (artist in Store.artistCovers) ({ vm.setArtistCover(artist, reset = true) }) else null
        )
        editAlbumFor?.let { an ->
            val tl = albums[an].orEmpty()
            if (tl.isNotEmpty()) AlbumEditDialog(
                artist, an, tl,
                onDismiss = { editAlbumFor = null },
                onRenameAlbum = { n -> vm.renameAlbum(tl, n); if (view == an) view = n },
                onRenameFiles = { vm.renameAlbum(tl, an); vm.renameFiles(it) },
                onCover = { albumCover = an to tl }
            )
        }
        if (v == "") BackHandler(enabled = sel.active) { sel.clear() }
        Column(Modifier.fillMaxSize()) {
            if (v == "" && sel.active) TrackSelectionBar(vm, fullList, actions, sel)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Zurück wie die System-Zurück-Taste: direkt geöffnetes Album schließt ganz (zurück zu Alben/Suche)
                TextButton(onClick = {
                    if (v != "" && !directView) view = ""
                    else { directView = false; view = ""; open = null; originBack?.invoke() }
                }) { Text("‹ Zurück") }
                if (v != "") Text(
                    if (albumDetail) detailAlbumName else "$artist · alle Lieder",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                ) else Spacer(Modifier.weight(1f))
                if (!albumDetail && v != "*") IconButton(onClick = { vm.toggleFavArtist(artist) }) {
                    val on = artist in vm.favArtists
                    Icon(if (on) Icons.Filled.Star else Icons.Filled.StarBorder, "Favoriten-Interpret", tint = if (on) ORANGE else LocalContentColor.current)
                }
                if (v != "" && !albumDetail) IconButton(onClick = { vm.playShuffled(shown, ctx) }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Alle Lieder …") }
                    GroupMenu(
                        menu, { menu = false },
                        (groupActions(vm, shown, ctx, actions) {
                            if (albumDetail) vm.blockTracks(shown) else vm.blockArtist(artist)
                            open = null
                        }).let { ga ->
                            if (albumDetail) ga.copy(onEdit = { editAlbumFor = detailAlbumName }, onDelete = { actions.onDeleteMany(shown) }) else ga
                        }
                    )
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Lieder") }
            } else if (v != "") {
                if (albumDetail) {
                    Column(Modifier.fillMaxSize()) {
                        AlbumDetailHeader(
                            artist = artist, album = detailAlbumName, tracks = shown,
                            onPlay = { if (shown.isNotEmpty()) vm.play(shown, 0, ctx) },
                            onShuffle = { if (shown.isNotEmpty()) vm.playShuffled(shown, ctx) }
                        )
                        TrackListBody(vm, shown, ctx, actions, sel)
                    }
                } else TrackListBody(vm, shown, ctx, actions, sel)
            } else {
                val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
                Box(Modifier.fillMaxSize()) {
                    val swipeOkOv = rememberSwipeAllowed(ovState)
                    androidx.compose.runtime.CompositionLocalProvider(LocalSwipeAllowed provides swipeOkOv) {
                    LazyColumn(Modifier.fillMaxSize(), state = ovState) {
                        item(key = "hdr") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        ArtistCover(artist, fullList, Modifier.size(104.dp).clip(CircleShape), sizePx = 256)
                                        Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(ORANGE).clickable { artistCoverPicker = true }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Edit, "Interpreten-Cover bearbeiten", tint = Color.Black, modifier = Modifier.size(17.dp)) }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                        Text(artist, style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        val totalPlays = fullList.sumOf { vm.plays(it.path) }
                                        Text(
                                            (if (albums.isNotEmpty()) "${albums.size} Alben · " else "") + "${fullList.size} Lieder" + if (totalPlays > 0) " · $totalPlays× gehört" else "",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Button(onClick = { vm.play(fullList, 0, ctx) }, Modifier.weight(1f)) {
                                        Icon(Icons.Filled.PlayArrow, null); Text("  Alle Songs abspielen")
                                    }
                                    androidx.compose.material3.FilledTonalIconButton(onClick = { vm.playShuffled(fullList, ctx) }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
                                }
                            }
                        }
                        if (top.isNotEmpty()) {
                            item { SectionTitle("Meistgehört") }
                            item {
                                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(top, key = { _, t -> "top" + t.id }) { i, t ->
                                        Column(Modifier.width(124.dp).clickable { vm.play(top, i, ctx) }) {
                                            Box {
                                                Cover(listOf(t), t.title, Modifier.size(124.dp).clip(RoundedCornerShape(10.dp)), sizePx = 256)
                                                Text(
                                                    "${i + 1}", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 1.dp)
                                                )
                                                if (t.id == playingId) MiniEq(ORANGE, LocalIsPlaying.current)
                                            }
                                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = if (t.id == playingId) ORANGE else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Headphones, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(" ${vm.plays(t.path)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (albums.isNotEmpty()) {
                            item { SectionTitle("Alben (${albums.size})") }
                            item {
                                androidx.compose.foundation.lazy.LazyRow(state = albumRowState, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(albums.entries.toList(), key = { it.key }) { (name, l) ->
                                        val albumCtx = "album:$artist:$name"
                                        AlbumTile(
                                            name, l,
                                            groupActions(vm, l, albumCtx, actions) { vm.blockTracks(l) }
                                                .copy(onEdit = { editAlbumFor = name }, onDelete = { actions.onDeleteMany(l) }),
                                            playingNow = vm.playingContext == albumCtx && vm.isPlaying,
                                            onShuffle = { vm.playShuffled(l, albumCtx) },
                                            onClick = { view = "album:$name" }
                                        )
                                    }
                                }
                            }
                        }
                        item { SectionTitle("Lieder (${fullList.size})") }
                        itemsIndexed(fullList.take(5), key = { _, t -> "all" + t.id }) { i, t ->
                            TrackRow(
                                t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth(),
                                selectionMode = sel.active, selected = t.id in sel.ids, onLongClick = { sel.toggle(t.id) }
                            ) { if (sel.active) sel.toggle(t.id) else vm.play(fullList, i, ctx) }
                        }
                        if (fullList.size > 5) item {
                            TextButton(onClick = { view = "*" }, Modifier.padding(horizontal = 8.dp)) { Text("Alle ${fullList.size} Lieder anzeigen ›") }
                        }
                    }
                    }
                    ListScrollbar(ovState, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailHeader(artist: String, album: String, tracks: List<Track>, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val total = tracks.sumOf { it.durationMs }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(tracks, album, Modifier.size(136.dp).clip(RoundedCornerShape(12.dp)), sizePx = 320)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("ALBUM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(album, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(artist, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} Songs · ${formatDuration(total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(onClick = onPlay, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, null); Text("  Play")
            }
            androidx.compose.material3.FilledTonalIconButton(onClick = onShuffle, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, "Shuffle")
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
    audio: Boolean = false,
    originBack: (() -> Unit)? = null
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var externalOpen by rememberSaveable { mutableStateOf(false) }
    val plListState = rememberLazyListState()
    val plGridState = rememberLazyGridState()
    val plScope = rememberCoroutineScope()
    OnReset(reset) { if (openId != null) { openId = null; externalOpen = false } else plScope.launch { plListState.animateScrollToItem(0) } }
    LaunchedEffect(openRequest) {
        if (openRequest != null) { openId = openRequest; externalOpen = true; onConsumed() }
    }
    val closePlaylist = {
        openId = null
        if (externalOpen) { externalOpen = false; originBack?.invoke() }
    }
    BackHandler(enabled = openId != null) { closePlaylist() }
    val open = vm.playlists.firstOrNull { it.id == openId }
    if (open == null) {
        val base = vm.playlistsByUse().filter { it.audio == audio }
        var query by rememberSaveable { mutableStateOf("") }
        var letter by rememberSaveable { mutableStateOf("") }
        val sortKey = if (audio) "audiobooks" else "playlists"
        var sortIdx by rememberSaveable { mutableIntStateOf(Store.sortPref(sortKey, 0)) }
        val sorts = listOf("Meistgehört", "Name A–Z", "Name Z–A", "Zuletzt hinzugefügt", "Anzahl Lieder")
        val available = remember(base) { base.map { Names.letterOf(it.name) }.toSet() }
        val fq = Names.fold(query.trim())
        val ordered = remember(base, query, letter, sortIdx) {
            var l = base
            if (fq.isNotEmpty()) l = l.filter { fq in Names.fold(it.name) }
            if (letter.isNotEmpty()) l = l.filter { Names.letterOf(it.name) == letter }
            when (sortIdx) {
                1 -> l.sortedBy { Names.fold(it.name) }
                2 -> l.sortedByDescending { Names.fold(it.name) }
                3 -> l.sortedByDescending { it.lastAdded }
                4 -> l.sortedByDescending { it.trackPaths.size }
                else -> l
            }
        }
        val grid = if (audio) vm.audiobookGrid else vm.playlistGrid
        Column(Modifier.fillMaxSize()) {
            // Gleicher Aufbau wie Bibliothek/Interpreten/Alben: Suche (nur in diesem Reiter), oranger Knopf, Buchstaben, Ansicht + Sortierung
            SearchField(query, { query = it }, if (audio) "Hörbücher & Podcasts suchen" else "Playlists suchen")
            NewListButton(if (audio) "Neues Hörbuch / Podcast" else "Neue Playlist", onNew)
            LetterBar(letter.ifEmpty { null }, available) { letter = it ?: "" }
            ListToolbar("${ordered.size} " + if (audio) "Listen" else "Playlists", sorts[sortIdx], sorts, sortIdx, { sortIdx = it; vm.saveSort(sortKey, it) }) {
                IconButton(onClick = { vm.showPlaylistsAsGrid(!grid, audio) }) {
                    Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (grid) "Listenansicht" else "Kachelansicht")
                }
            }
            if (ordered.isEmpty() && (fq.isNotEmpty() || letter.isNotEmpty())) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("Nichts gefunden") }
            } else if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (audio) "Noch keine Hörbuch- oder Podcast-Listen. Lege eine Liste an und füge Folgen oder Kapitel über „Zu Playlist hinzufügen“ hinzu (dort „Hörbuch-Listen anzeigen“)."
                        else "Noch keine Playlists. Lege oben mit „+ Neue Playlist“ eine an oder füge Lieder über „Zu Playlist hinzufügen“ hinzu.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (if (audio) vm.audiobookGrid else vm.playlistGrid) {
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
        var mode by rememberSaveable { mutableIntStateOf(Store.sortPref("playlistDetail", 0)) } // 0 eigene Reihenfolge, 1 zuletzt hinzugefügt, 2 Titel, 3 Interpret, 4 Länge
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
        var collapsed by remember(open.id) { mutableStateOf(false) }
        LaunchedEffect(open.id, shown.size) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex * 100000 + listState.firstVisibleItemScrollOffset }
                .collect { v ->
                    if (shown.size >= 14) {
                        if (!collapsed && v > 120) collapsed = true
                        else if (collapsed && v == 0) collapsed = false
                    } else collapsed = false
                }
        }
        Column(Modifier.fillMaxSize()) {
            PlaylistHeader(
                vm, open, list, compact = collapsed,
                onBack = { closePlaylist() },
                onStart = { vm.playContext(list, ctx) },
                onEdit = { onRename(open) },
                onDelete = { onDeleteList(open); openId = null }
            )
            val psel = remember(open.id) { Selection() }
            var pick by remember(open.id) { mutableStateOf(false) }
            val selecting = pick || psel.active
            val rmLabel = if (open.audio) "Aus Hörbuch entfernen" else "Aus Playlist entfernen"
            if (!collapsed || selecting) Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SortMenu("Sortierung: " + modes[mode], modes, mode) { mode = it; vm.saveSort("playlistDetail", it) }
                Spacer(Modifier.weight(1f))
                if (!selecting) TextButton(onClick = { pick = true }) { Text("Auswählen") }
            }
            ReorderEffects(rs)
            BackHandler(enabled = selecting) { psel.clear(); pick = false }
            if (selecting) TrackSelectionBar(vm, list, actions, psel, rmLabel, onRemove = {
                shown.filter { it.second.id in psel.ids }.map { it.first }.sortedDescending().forEach { vm.removeFromPlaylistAt(open.id, it) }
            }, onDone = { pick = false })
            val swipeOkPl = rememberSwipeAllowed(listState)
            Box(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalSwipeAllowed provides swipeOkPl) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                // Index in der gespeicherten Liste als Schlüssel: dasselbe Lied darf mehrfach vorkommen
                itemsIndexed(shown, key = { _, e -> e.first }) { i, (idx, t) ->
                    val rowActions = actions.copy(onRemoveFromPlaylist = { vm.removeFromPlaylistAt(open.id, idx) }, removeLabel = rmLabel)
                    if (mode == 0 && !selecting) {
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
                            TrackRow(t, rowActions, modifier = Modifier.fillMaxWidth(), compact = true) { vm.play(list, i, ctx) }
                        }
                    } else {
                        TrackRow(
                            t, rowActions, modifier = Modifier.fillMaxWidth(),
                            selectionMode = selecting, selected = t.id in psel.ids, onLongClick = { psel.toggle(t.id) }
                        ) { if (selecting) psel.toggle(t.id) else vm.play(list, i, ctx) }
                    }
                }
            }
            }
            ListScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}


/** Nach rechts wischen = Stern (Favoriten-Interpret), wie das Herz bei Liedern. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ArtistSwipe(vm: MusicViewModel, name: String, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        positionalThreshold = { total -> total * 0.25f },
        confirmValueChange = { v -> if (v == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) vm.toggleFavArtist(name); false }
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = LocalSwipeAllowed.current,
        backgroundContent = {
            if (state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) Box(
                Modifier.fillMaxSize().background(ORANGE).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart
            ) { Icon(Icons.Filled.Star, "Favoriten-Interpret", tint = Color.Black) }
        }
    ) { Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() } }
}
