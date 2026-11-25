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
        onCover = { coverFor = it }
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(snack) { data ->
                androidx.compose.material3.Snackbar(
                    data, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 68.dp)
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
                NowPlayingScreen(vm, actions, onAddToPlaylist = { addToPlaylist = listOf(it) }) { showPlayer = false }
            } else holder.SaveableStateProvider(tab) { when (tab) {
                0 -> LibraryTab(vm, actions, sel, artistToOpen, { artistToOpen = null }, resets[0])
                1 -> SearchTab(
                    vm, actions, resets[1],
                    onOpenArtist = { artistToOpen = it; tab = 0 },
                    onOpenPlaylist = { playlistToOpen = it; tab = 2 }
                )
                2 -> PlaylistsTab(
                    vm, actions,
                    onNew = { newPlaylistFor = emptyList() },
                    onRename = { renameList = it },
                    onDeleteList = { deleteList = it },
                    openRequest = playlistToOpen,
                    onConsumed = { playlistToOpen = null },
                    reset = resets[2]
                )
                3 -> PlaylistsTab(
                    vm, actions,
                    onNew = { newAudioList = true },
                    onRename = { renameList = it },
                    onDeleteList = { deleteList = it },
                    openRequest = null,
                    onConsumed = {},
                    reset = resets[3],
                    audio = true
                )
                else -> SettingsTab(vm, actions, sel, resets[4])
            } }
        }
        }
    }

    // ---------- Dialoge ----------
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
    renameTrack?.let { t ->
        EditTrackDialog(
            t, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { renameTrack = null; stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false },
            onConfirm = { n, a, mv ->
                vm.editTrack(t, n, a, mv, stagedCoverUri, stagedCoverUrl, coverSelected = stagedCoverReset || stagedCoverUri != null || stagedCoverUrl != null, coverReset = stagedCoverReset)
                renameTrack = null; stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = false
            },
            onCover = { coverFor = t }, coverUri = stagedCoverUri, coverUrl = stagedCoverUrl, coverResetPending = stagedCoverReset
        )
    }
    coverFor?.let { t ->
        CoverPickerDialog(
            initialQuery = listOf(t.artist, t.title).filter { it.isNotBlank() }.joinToString(" "),
            onDismiss = { coverFor = null },
            onUri = { if (renameTrack != null) { stagedCoverUri = it; stagedCoverUrl = null; stagedCoverReset = false } else vm.setTrackCover(listOf(t), uri = it) },
            onUrl = { if (renameTrack != null) { stagedCoverUrl = it; stagedCoverUri = null; stagedCoverReset = false } else vm.setTrackCover(listOf(t), url = it) },
            onReset = if (vm.hasOwnCover(listOf(t)) || stagedCoverUri != null || stagedCoverUrl != null) ({ if (renameTrack != null) { stagedCoverUri = null; stagedCoverUrl = null; stagedCoverReset = true } else vm.setTrackCover(listOf(t)) }) else null
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
