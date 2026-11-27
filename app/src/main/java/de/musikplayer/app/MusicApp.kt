package de.musikplayer.app

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val TAB_TITLES = listOf("Bibliothek", "Suche", "Playlists", "Hörbücher", "Optionen")

@Composable
fun MusicApp(vm: MusicViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    val sel = remember { Selection() }

    // Sprünge aus der Suche in die Interpreten-/Playlist-Reiter
    var artistToOpen by remember { mutableStateOf<String?>(null) }
    var playlistToOpen by remember { mutableStateOf<String?>(null) }
    var backTo by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    // Dialog-Zustand
    var addToPlaylist by remember { mutableStateOf<List<Track>?>(null) }
    var newPlaylistFor by remember { mutableStateOf<List<Track>?>(null) }
    var dupAdd by remember { mutableStateOf<Pair<Playlist, List<Track>>?>(null) }
    var renameList by remember { mutableStateOf<Playlist?>(null) }
    var deleteList by remember { mutableStateOf<Playlist?>(null) }
    var assign by remember { mutableStateOf<Track?>(null) }
    var delete by remember { mutableStateOf<List<Track>?>(null) }
    var renameTrack by remember { mutableStateOf<Track?>(null) }
    var assignMany by remember { mutableStateOf<List<Track>?>(null) }
    var newAudioList by remember { mutableStateOf(false) }
    var coverFor by remember { mutableStateOf<Track?>(null) }
    var coverMany by remember { mutableStateOf<List<Track>?>(null) }
    var coverQuery by remember { mutableStateOf<String?>(null) }
    var stagedCoverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var stagedCoverUrl by remember { mutableStateOf<String?>(null) }
    var stagedCoverReset by remember { mutableStateOf(false) }

    val snack = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val resets = remember { mutableStateListOf(0, 0, 0, 0, 0) }
    LaunchedEffect(Unit) { vm.load() }
    // Zurück in der App (z. B. nach einem Download): Bibliothek neu einlesen
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { m ->
            val u = vm.undo
            if (u != null) {
                val r = snack.showSnackbar(m, actionLabel = "Rückgängig", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) u()
            } else snack.showSnackbar(m)
            if (vm.message == m) { vm.message = null; vm.undo = null }
        }
    }
    LaunchedEffect(tab) { sel.clear() }
    BackHandler(enabled = showPlayer) { showPlayer = false }
    // Lied über „Öffnen mit“ gestartet: Vollbild-Player zeigen, Zurück führt in den bisherigen Reiter
    LaunchedEffect(vm.openPlayerRequest) {
        if (vm.openPlayerRequest) { vm.openPlayerRequest = false; showPlayer = true }
    }

    val actions = TrackActions(
        onPlayNext = { vm.playNext(listOf(it)) },
        onAddToQueue = { vm.addToQueue(listOf(it)) },
        onAddToPlaylist = { addToPlaylist = listOf(it) },
        onAssignArtist = { assign = it },
        onDelete = { delete = listOf(it) },
        onAddManyToPlaylist = { addToPlaylist = it },
        onDeleteMany = { delete = it },
        onToggleFavorite = { vm.toggleFavorite(it.path) },
        isFavorite = { vm.isFavorite(it.path) },
        onRename = { stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false; renameTrack = it },
        onBlock = { vm.blockTracks(listOf(it)) },
        onShowFile = { vm.showInFolder(it) },
        onBlockMany = { vm.blockTracks(it) },
        onAssignMany = { assignMany = it },
        onCover = { coverFor = it },
        onOpenArtist = { backTo = tab to showPlayer; artistToOpen = it; tab = 0; showPlayer = false },
        onCoverMany = { coverMany = it }
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalIsPlaying provides vm.isPlaying, LocalRowScale provides vm.rowScale, LocalPlayingId provides vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(snack) { data ->
                val canPromote = vm.promoteLabel != null && data.visuals.message == vm.promoteLabel
                val isLink = vm.linkLabel != null && data.visuals.message == vm.linkLabel
                androidx.compose.material3.Snackbar(
                    action = data.visuals.actionLabel?.let { al ->
                        { androidx.compose.material3.TextButton(onClick = { data.performAction() }) { Text(al) } }
                    },
                    content = {
                        Text(
                            data.visuals.message,
                            color = if (isLink) ORANGE else androidx.compose.material3.SnackbarDefaults.contentColor,
                            textDecoration = if (isLink) androidx.compose.ui.text.style.TextDecoration.Underline else null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 68.dp).then(
                        if (isLink) Modifier.clickable {
                            val tg = vm.linkTarget
                            if (tg == "blocked") { vm.settingsPageRequest = "blocked"; tab = 4; showPlayer = false; data.dismiss() }
                            else if (tg != null && tg.startsWith("pl:")) {
                                val id = tg.removePrefix("pl:")
                                playlistToOpen = id
                                tab = if (vm.playlists.firstOrNull { it.id == id }?.audio == true) 3 else 2
                                showPlayer = false
                                data.dismiss()
                            }
                        } else Modifier
                    ).then(
                        if (!canPromote) Modifier else Modifier.pointerInput(data) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > 70.dp.toPx()) { vm.promoteAction?.invoke(); vm.promoteAction = null; vm.promoteLabel = null } },
                                onHorizontalDrag = { change, dx -> change.consume(); total += dx }
                            )
                        }
                    )
                )
            }
        },
        bottomBar = {
            Column {
                if (!showPlayer) MiniPlayer(vm, onAddToPlaylist = { addToPlaylist = listOf(it) }, onEdit = { stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false; renameTrack = it }) { showPlayer = true }
                NavigationBar {
                    val icons = listOf(Icons.Filled.LibraryMusic, Icons.Filled.Search, Icons.Filled.PlaylistPlay, Icons.Filled.Headphones, Icons.Filled.Settings)
                    TAB_TITLES.forEachIndexed { i, title ->
                        NavigationBarItem(
                            selected = !showPlayer && tab == i,
                            onClick = {
                                // Nochmal auf den aktuellen Reiter: zurück auf die oberste Ebene
                                if (!showPlayer && tab == i) resets[i] = resets[i] + 1
                                backTo = null
                                tab = i; showPlayer = false
                            },
                            icon = { Icon(icons[i], null) },
                            label = { Text(title, fontSize = 10.sp, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        PullRefresh(onRefresh = { vm.refresh() }, modifier = Modifier.padding(pad)) {
        Column(Modifier.fillMaxSize()) {
            if (showPlayer) {
                NowPlayingScreen(
                    vm, actions, onAddToPlaylist = { addToPlaylist = listOf(it) },
                    nav = PlayerNav(
                        onArtist = { backTo = tab to showPlayer; artistToOpen = it; tab = 0; showPlayer = false },
                        onLocate = { t -> backTo = tab to showPlayer; artistToOpen = t.artist; tab = 0; showPlayer = false },
                        onPlaylist = { id -> backTo = tab to showPlayer; playlistToOpen = id; tab = if (vm.playlists.firstOrNull { it.id == id }?.audio == true) 3 else 2; showPlayer = false }
                    )
                ) { showPlayer = false }
            } else holder.SaveableStateProvider(tab) { when (tab) {
                0 -> LibraryTab(vm, actions, sel, artistToOpen, { artistToOpen = null }, resets[0],
                    originBack = backTo?.let { b -> { backTo = null; tab = b.first; showPlayer = b.second } },
                    onArtistClosed = { backTo = null })
                1 -> SearchTab(
                    vm, actions, resets[1],
                    onOpenArtist = { backTo = tab to showPlayer; artistToOpen = it; tab = 0; showPlayer = false },
                    onOpenPlaylist = { id -> backTo = tab to showPlayer; playlistToOpen = id; tab = if (vm.playlists.firstOrNull { it.id == id }?.audio == true) 3 else 2; showPlayer = false }
                )
                2 -> PlaylistsTab(
                    vm, actions,
                    onNew = { newPlaylistFor = emptyList() },
                    onRename = { renameList = it },
                    onDeleteList = { deleteList = it },
                    openRequest = playlistToOpen,
                    onConsumed = { playlistToOpen = null },
                    reset = resets[2],
                    originBack = backTo?.let { b -> { backTo = null; tab = b.first; showPlayer = b.second } }
                )
                3 -> PlaylistsTab(
                    vm, actions,
                    onNew = { newAudioList = true },
                    onRename = { renameList = it },
                    onDeleteList = { deleteList = it },
                    openRequest = playlistToOpen,
                    onConsumed = { playlistToOpen = null },
                    reset = resets[3],
                    audio = true,
                    originBack = backTo?.let { b -> { backTo = null; tab = b.first; showPlayer = b.second } }
                )
                else -> SettingsTab(vm, actions, sel, resets[4])
            } }
        }
        }
    }
    }

    // ---------- Dialoge ----------
    vm.pickerRequest?.let { list -> AddToPlaylistDialog(vm, list, onDismiss = { vm.pickerRequest = null }) }
    addToPlaylist?.let { list ->
        AddToPlaylistDialog(vm, list, onDismiss = { addToPlaylist = null })
    }
    newPlaylistFor?.let { first ->
        TextInputDialog(
            title = "Neue Playlist", label = "Name", confirmText = "Erstellen",
            onDismiss = { newPlaylistFor = null },
            onConfirm = { vm.createPlaylist(it, first); newPlaylistFor = null }
        )
    }
    renameList?.let { p ->
        EditPlaylistDialog(vm, p.id, onDismiss = { renameList = null })
    }
    deleteList?.let { p ->
        ConfirmDialog(
            title = "Playlist löschen?",
            text = "„${p.name}“ wird gelöscht. Die Lieder bleiben auf dem Handy.",
            confirmText = "Löschen",
            onDismiss = { deleteList = null },
            onConfirm = { vm.deletePlaylist(p.id); deleteList = null }
        )
    }
    assign?.let { t ->
        AssignArtistDialog(
            1, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { assign = null },
            onConfirm = { a, mv -> vm.assignTrack(t.path, a, mv); assign = null }
        )
    }
    renameTrack?.let { t0 ->
        // immer den aktuellen Stand nehmen (nach Speichern ändern sich Name, Pfad und Interpret)
        val t = vm.tracks.firstOrNull { it.id == t0.id } ?: vm.tracks.firstOrNull { it.path == Store.resolve(t0.path) } ?: t0
        EditTrackDialog(
            t, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { renameTrack = null; stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false },
            onConfirm = { n, a, mv ->
                vm.editTrack(t, n, a, mv, stagedCoverUri, stagedCoverUrl, coverSelected = stagedCoverReset || stagedCoverUri != null || stagedCoverUrl != null, coverReset = stagedCoverReset)
                renameTrack = null; stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false
            },
            onCover = { q -> coverQuery = q; coverFor = t }, coverUri = stagedCoverUri, coverUrl = stagedCoverUrl, coverResetPending = stagedCoverReset
        )
    }
    coverFor?.let { t ->
        CoverPickerDialog(
            initialQuery = coverQuery ?: listOf(t.artist, t.title).filter { it.isNotBlank() }.joinToString(" "),
            onDismiss = { coverFor = null; coverQuery = null },
            onUri = { if (renameTrack != null) { stagedCoverUri = it; stagedCoverUrl = null; stagedCoverReset = false } else vm.setTrackCover(listOf(t), uri = it) },
            onUrl = { if (renameTrack != null) { stagedCoverUrl = it; stagedCoverUri = null; stagedCoverReset = false } else vm.setTrackCover(listOf(t), url = it) },
            onReset = if (vm.hasOwnCover(listOf(t)) || stagedCoverUri != null || stagedCoverUrl != null) ({ if (renameTrack != null) { stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = true } else vm.setTrackCover(listOf(t)) }) else null
        )
    }
    coverMany?.let { list ->
        CoverPickerDialog(
            initialQuery = list.firstOrNull()?.let { listOf(it.artist.takeIf { a -> a != UNKNOWN_ARTIST } ?: "", it.albumName).filter { s -> s.isNotBlank() }.joinToString(" ") }.orEmpty(),
            onDismiss = { coverMany = null },
            onUri = { vm.setTrackCover(list, uri = it) },
            onUrl = { vm.setTrackCover(list, url = it) },
            onReset = if (vm.hasOwnCover(list)) ({ vm.setTrackCover(list) }) else null
        )
    }
    assignMany?.let { list ->
        AssignArtistDialog(
            list.size, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { assignMany = null },
            onConfirm = { a, mv -> vm.assignTracks(list, a, mv); assignMany = null }
        )
    }
    if (newAudioList) {
        TextInputDialog(
            title = "Neue Hörbuch-/Podcast-Liste", label = "Name", confirmText = "Erstellen",
            onDismiss = { newAudioList = false },
            onConfirm = { vm.createPlaylist(it, audio = true); newAudioList = false }
        )
    }
    delete?.let { list ->
        DeleteDialog(list, onDismiss = { delete = null }, onConfirm = { vm.deleteTracks(list); delete = null })
    }
}
