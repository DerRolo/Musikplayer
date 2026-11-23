package de.musikplayer.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Globale Suche wie bei Spotify: Interpreten, Playlists und Titel in einer Liste. */
@Composable
fun SearchTab(
    vm: MusicViewModel,
    actions: TrackActions,
    reset: Int,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    OnReset(reset) { query = "" }
    val q = Names.fold(query.trim())

    val artistHits = remember(vm.tracks, q) {
        if (q.isEmpty()) emptyList()
        else vm.tracks.groupBy { it.artist }
            .filterKeys { q in Names.fold(it) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, list) -> name to list.sortedBy { it.sortKey } }
            .take(20)
    }
    val playlistHits = remember(vm.playlists, q) {
        if (q.isEmpty()) emptyList() else vm.playlists.filter { q in Names.fold(it.name) }
    }
    val trackHits = remember(vm.tracks, query) {
        if (q.isEmpty()) emptyList() else Library.sorted(filterTracks(vm.tracks, query), SortBy.Title).take(150)
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it }, "Titel, Interpreten, Playlists")
        if (q.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Suche nach Titeln, Interpreten oder Playlists", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (artistHits.isEmpty() && playlistHits.isEmpty() && trackHits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nichts gefunden") }
        } else {
            val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
            LazyColumn(Modifier.fillMaxSize()) {
                if (artistHits.isNotEmpty()) {
                    item(key = "h_a") { Header("Interpreten") }
                    itemsIndexed(artistHits, key = { _, h -> "a_" + h.first }) { _, (name, list) ->
                        GroupRow(name, list, list.size, true, groupActions(vm, list, "a:$name", actions)) { onOpenArtist(name) }
                    }
                }
                if (playlistHits.isNotEmpty()) {
                    item(key = "h_p") { Header("Playlists") }
                    itemsIndexed(playlistHits, key = { _, p -> "p_" + p.id }) { _, p ->
                        val list = vm.playlistEntries(p.id).map { it.second }
                        GroupRow(p.name, list, p.trackPaths.size, false, groupActions(vm, list, "p:${p.id}", null)) { onOpenPlaylist(p.id) }
                    }
                }
                if (trackHits.isNotEmpty()) {
                    item(key = "h_t") { Header("Titel") }
                    itemsIndexed(trackHits, key = { _, t -> "t_" + t.id }) { i, t ->
                        TrackRow(t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth()) {
                            vm.play(trackHits, i, "s")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}
