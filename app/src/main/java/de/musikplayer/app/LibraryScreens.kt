package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SongsTab(vm: MusicViewModel, actions: TrackActions) {
    var sortMenu by remember { mutableStateOf(false) }
    val list = vm.sortedTracks
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} Titel", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("Sortierung: ${vm.sortBy.label}") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    for (s in SortBy.values()) DropdownMenuItem(text = { Text(s.label) }, onClick = { sortMenu = false; vm.setSort(s) })
                }
            }
            IconButton(onClick = { vm.playShuffled(list, "all") }, enabled = list.isNotEmpty()) { Icon(Icons.Filled.Shuffle, "Alle zufällig") }
        }
        if (vm.loading && list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Musik gefunden") }
        } else {
            val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth()) {
                        vm.play(list, i, "all")
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsTab(vm: MusicViewModel, actions: TrackActions) {
    var open by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val groups = vm.tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val artist = open
    if (artist == null) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups.entries.toList(), key = { it.key }) { (name, list) ->
                Row(Modifier.fillMaxWidth().clickable { open = name }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${list.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        val list = (groups[artist] ?: emptyList()).sortedBy { it.title.lowercase() }
        val ctx = "a:$artist"
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open = null }) { Text("‹ Zurück") }
                Text(artist, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { vm.playShuffled(list, ctx) }) { Icon(Icons.Filled.Shuffle, "Zufällig") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, actions, modifier = Modifier.fillMaxWidth()) { vm.play(list, i, ctx) }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(vm: MusicViewModel, actions: TrackActions, onNew: () -> Unit, onRename: (Playlist) -> Unit, onDeleteList: (Playlist) -> Unit) {
    var openId by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = openId != null) { openId = null }
    val open = vm.playlists.firstOrNull { it.id == openId }
    if (open == null) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onNew, Modifier.padding(horizontal = 8.dp)) { Text("+ Neue Playlist") }
            if (vm.playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Playlists – füge Titel über das Menü hinzu")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.playlists, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().clickable { openId = p.id }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackPaths.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else {
        val list = vm.playlistTracks(open.id)
        val ctx = "p:${open.id}"
        val playActions = actions.copy(onRemoveFromPlaylist = { t -> vm.removeFromPlaylist(open.id, t.path) })
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
            // Gelöschte/verschobene Dateien fehlen in der Liste; Reihenfolge bezieht sich auf die gespeicherten Pfade.
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackRow(t, playActions, modifier = Modifier.weight(1f)) { vm.play(list, i, ctx) }
                        IconButton(
                            onClick = {
                                vm.movePlaylistTrack(open.id, open.trackPaths.indexOf(t.path), open.trackPaths.indexOf(list[i - 1].path))
                            },
                            enabled = i > 0
                        ) { Icon(Icons.Filled.ArrowUpward, "Nach oben") }
                        IconButton(
                            onClick = {
                                vm.movePlaylistTrack(open.id, open.trackPaths.indexOf(t.path), open.trackPaths.indexOf(list[i + 1].path))
                            },
                            enabled = i < list.lastIndex
                        ) { Icon(Icons.Filled.ArrowDownward, "Nach unten") }
                    }
                }
            }
        }
    }
}
