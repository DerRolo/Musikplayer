package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MusicNote
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

@Composable
fun MusicApp(vm: MusicViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

    // Dialog-Zustand für Aktionen aus dem Titel-Menü
    var addToPlaylist by remember { mutableStateOf<List<Track>?>(null) }
    var newPlaylistFor by remember { mutableStateOf<List<Track>?>(null) }
    var renameList by remember { mutableStateOf<Playlist?>(null) }
    var deleteList by remember { mutableStateOf<Playlist?>(null) }
    var assign by remember { mutableStateOf<Track?>(null) }
    var delete by remember { mutableStateOf<List<Track>?>(null) }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    val actions = TrackActions(
        onPlayNext = { vm.playNext(listOf(it)) },
        onAddToQueue = { vm.addToQueue(listOf(it)) },
        onAddToPlaylist = { addToPlaylist = listOf(it) },
        onAssignArtist = { assign = it },
        onDelete = { delete = listOf(it) }
    )

    if (showPlayer) {
        NowPlayingScreen(vm) { showPlayer = false }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    MiniPlayer(vm) { showPlayer = true }
                    NavigationBar {
                        NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.MusicNote, null) }, label = { Text("Titel") })
                        NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Person, null) }, label = { Text("Interpreten") })
                        NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.PlaylistPlay, null) }, label = { Text("Playlists") })
                        NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Einstellungen") })
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                when (tab) {
                    0 -> SongsTab(vm, actions)
                    1 -> ArtistsTab(vm, actions)
                    2 -> PlaylistsTab(
                        vm, actions,
                        onNew = { newPlaylistFor = emptyList() },
                        onRename = { renameList = it },
                        onDeleteList = { deleteList = it }
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
            onPick = { vm.addToPlaylist(it.id, list); addToPlaylist = null },
            onNew = { newPlaylistFor = list; addToPlaylist = null }
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
    delete?.let { list ->
        DeleteDialog(list, onDismiss = { delete = null }, onConfirm = { vm.deleteTracks(list); delete = null })
    }
}
