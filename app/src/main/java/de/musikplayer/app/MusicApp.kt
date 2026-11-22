package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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

private val TAB_TITLES = listOf("Titel", "Interpreten", "Playlists", "Suche", "Einstellungen")

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

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
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
        onRename = { renameTrack = it }
    )

    if (showPlayer) {
        NowPlayingScreen(vm, onAddToPlaylist = { addToPlaylist = listOf(it) }) { showPlayer = false }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    MiniPlayer(vm, onAddToPlaylist = { addToPlaylist = listOf(it) }) { showPlayer = true }
                    NavigationBar {
                        val icons = listOf(Icons.Filled.MusicNote, Icons.Filled.Person, Icons.Filled.PlaylistPlay, Icons.Filled.Search, Icons.Filled.Settings)
                        TAB_TITLES.forEachIndexed { i, title ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(icons[i], null) },
                                label = { Text(title, fontSize = 10.sp, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                when (tab) {
                    0 -> SongsTab(vm, actions, sel)
                    1 -> ArtistsTab(vm, actions, sel, artistToOpen) { artistToOpen = null }
                    2 -> PlaylistsTab(
                        vm, actions,
                        onNew = { newPlaylistFor = emptyList() },
                        onRename = { renameList = it },
                        onDeleteList = { deleteList = it },
                        openRequest = playlistToOpen,
                        onConsumed = { playlistToOpen = null }
                    )
                    3 -> SearchTab(
                        vm, actions,
                        onOpenArtist = { artistToOpen = it; tab = 1 },
                        onOpenPlaylist = { playlistToOpen = it; tab = 2 }
                    )
                    else -> SettingsTab(vm)
                }
            }
        }
    }

    // ---------- Dialoge ----------
    addToPlaylist?.let { list ->
        PlaylistPickerDialog(
            playlists = vm.playlists,
            onDismiss = { addToPlaylist = null },
            onPick = { p ->
                addToPlaylist = null
                // Warnung, wenn Lieder schon in der Playlist sind
                if (vm.alreadyIn(p.id, list).isEmpty()) vm.addToPlaylist(p.id, list) else dupAdd = p to list
            },
            onNew = { newPlaylistFor = list; addToPlaylist = null }
        )
    }
    dupAdd?.let { (p, list) ->
        DuplicateAddDialog(
            playlistName = p.name,
            dupCount = vm.alreadyIn(p.id, list).size,
            total = list.size,
            onSkip = { vm.addToPlaylist(p.id, list, skipDuplicates = true); dupAdd = null },
            onAddAll = { vm.addToPlaylist(p.id, list, skipDuplicates = false); dupAdd = null },
            onCancel = { dupAdd = null }
        )
    }
    newPlaylistFor?.let { first ->
        TextInputDialog(
            title = "Neue Playlist", label = "Name", confirmText = "Erstellen",
            onDismiss = { newPlaylistFor = null },
            onConfirm = { vm.createPlaylist(it, first); newPlaylistFor = null }
        )
    }
    renameList?.let { p ->
        TextInputDialog(
            title = "Playlist umbenennen", label = "Name", initial = p.name,
            onDismiss = { renameList = null },
            onConfirm = { vm.renamePlaylist(p.id, it); renameList = null }
        )
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
        TextInputDialog(
            title = "Interpret zuordnen", label = "Interpret",
            initial = if (t.artist == UNKNOWN_ARTIST) "" else t.artist,
            onDismiss = { assign = null },
            onConfirm = { vm.assignTrack(t.path, it); assign = null }
        )
    }
    renameTrack?.let { t ->
        TextInputDialog(
            title = "Datei umbenennen", label = "Name (ohne Endung)",
            initial = t.fileName.substringBeforeLast('.'),
            suggestions = listOf(Library.proposedFileName(t).substringBeforeLast('.')),
            confirmText = "Umbenennen",
            onDismiss = { renameTrack = null },
            onConfirm = { vm.renameFiles(listOf(t to it)); renameTrack = null }
        )
    }
    delete?.let { list ->
        DeleteDialog(list, onDismiss = { delete = null }, onConfirm = { vm.deleteTracks(list); delete = null })
    }
}
