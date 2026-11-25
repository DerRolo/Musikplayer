@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var picked by rememberSaveable { mutableStateOf("") }
    OnReset(reset) { query = ""; picked = ""; ssel.clear() }
    val q = Names.fold(query.trim())

    val artistHits = remember(vm.searchableTracks, vm.blockTick, q) {
        if (q.isEmpty()) emptyList()
        else vm.searchableTracks.groupBy { it.artist }
            .filterKeys { q in Names.fold(it) }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, list) -> name to list.sortedBy { it.sortKey } }
            .take(20)
    }
    val albumHits = remember(vm.searchableTracks, vm.blockTick, q) {
        if (q.isEmpty()) emptyList()
        else vm.searchableTracks.filter { it.albumName.isNotBlank() && q in Names.fold(it.albumName) }
            .groupBy { it.artist to it.albumName }
            .map { (k, l) -> Triple(k.first, k.second, l.sortedBy { it.sortKey }) }
            .sortedBy { Names.fold(it.second) }.take(20)
    }
    val playlistHits = remember(vm.playlists, q) {
        if (q.isEmpty()) emptyList() else vm.playlists.filter { q in Names.fold(it.name) }
    }
    val trackHits = remember(vm.searchableTracks, vm.blockTick, query) {
        if (q.isEmpty()) emptyList() else Library.sorted(filterTracks(vm.searchableTracks, query), SortBy.Title).take(150)
    }

    val ssel = remember { Selection() }
    LaunchedEffect(vm.blockTick) { ssel.clear() }
    Column(Modifier.fillMaxSize(), verticalArrangement = if (q.isEmpty()) Arrangement.Center else Arrangement.Top) {
        SearchField(query, { query = it }, "Titel, Interpreten, Playlists", onSubmit = { picked = query; vm.addRecentSearch(query) })
        val sugs = remember(vm.searchableTracks, vm.blockTick, albumHits, q) {
            if (q.isEmpty()) emptyList()
            else {
                val out = ArrayList<Sug>()
                for ((n, l) in vm.searchableTracks.groupBy { it.artist }) if (n != UNKNOWN_ARTIST && q in Names.fold(n)) out += Sug(0, n, "", l.sortedBy { it.sortKey }.take(4))
                for ((ar, al, l) in albumHits) out += Sug(1, al, ar, l)
                for (t in vm.searchableTracks) if (q in t.sortKey) out += Sug(2, t.title, t.artist, listOf(t))
                out.sortedWith(compareBy({ if (Names.fold(it.name).startsWith(q)) 0 else 1 }, { it.kind })).take(5)
            }
        }
        val showSug = query != picked
        if (q.isEmpty()) {
            val recent = vm.blockTick.let { Store.recentSearches }
            if (recent.isEmpty()) {
                Text(
                    "Suche nach Titeln, Interpreten, Alben oder Playlists", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Header("Zuletzt gesucht")
                for (r in recent) {
                    Row(Modifier.fillMaxWidth().clickable { query = r }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(r, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1)
                        IconButton(onClick = { vm.removeRecentSearch(r) }) { Icon(Icons.Filled.Close, "Entfernen") }
                    }
                }
            }
        } else if (artistHits.isEmpty() && albumHits.isEmpty() && playlistHits.isEmpty() && trackHits.isEmpty() && sugs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nichts gefunden") }
        } else {
            val playingId = vm.current?.mediaId?.substringAfterLast(':')?.toLongOrNull()
            LazyColumn(Modifier.fillMaxSize()) {
                if (showSug && sugs.isNotEmpty()) {
                    item(key = "h_s") { Header("Vorschläge") }
                    items(sugs, key = { "s_" + it.kind + "_" + it.name + "_" + it.artist }) { sg ->
                        SuggestRow(sg) {
                            vm.addRecentSearch(sg.name)
                            when (sg.kind) {
                                0 -> onOpenArtist(sg.name)
                                1 -> onOpenArtist(sg.artist)
                                else -> vm.play(sg.tracks, 0, "s")
                            }
                        }
                    }
                }
                if (artistHits.isNotEmpty()) {
                    item(key = "h_a") { Header("Interpreten") }
                    itemsIndexed(artistHits, key = { _, h -> "a_" + h.first }) { _, (name, list) ->
                        GroupRow(name, list, list.size, true, groupActions(vm, list, "a:$name", actions)) { onOpenArtist(name) }
                    }
                }
                if (albumHits.isNotEmpty()) {
                    item(key = "h_al") { Header("Alben") }
                    itemsIndexed(albumHits, key = { _, h -> "al_" + h.first + "|" + h.second }) { _, (artistName, albumName, list) ->
                        GroupRow(albumName + "  ·  " + artistName, list, list.size, false, groupActions(vm, list, "a:$artistName", actions)) { onOpenArtist(artistName) }
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
                    if (ssel.active) item(key = "selection_t") { TrackSelectionBar(vm, trackHits, actions, ssel) }
                    item(key = "h_t") { Header("Titel") }
                    itemsIndexed(trackHits, key = { _, t -> "t_" + t.id }) { i, t ->
                        TrackRow(
                            t, actions, playing = t.id == playingId, modifier = Modifier.fillMaxWidth(),
                            selectionMode = ssel.active, selected = t.id in ssel.ids, onLongClick = { ssel.toggle(t.id) }
                        ) {
                            if (ssel.active) ssel.toggle(t.id) else {
                                vm.addRecentSearch(query)
                                vm.play(trackHits, i, "s")
                            }
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

/** Vorschlag: kind 0 = Interpret, 1 = Album, 2 = Song. */
private data class Sug(val kind: Int, val name: String, val artist: String, val tracks: List<Track>)

@Composable
private fun SuggestRow(s: Sug, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(s.tracks, s.name, Modifier.size(48.dp).clip(RoundedCornerShape(if (s.kind == 0) 24.dp else 8.dp)), 128)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1000, velocity = 40.dp))
            Text(
                when (s.kind) { 0 -> "Interpret"; 1 -> "Album · " + s.artist; else -> "Song · " + s.artist },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
