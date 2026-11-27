@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.musikplayer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue

// ---------- Sperrliste ----------

@Composable
internal fun BlockedPage(vm: MusicViewModel, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val songs = remember(vm.blockTick, vm.tracks) { Library.blocked.filter { it.path in Store.blockedTracks || it.path in Store.softBlockedTracks } }
    val artists = remember(vm.blockTick, vm.tracks) { (Store.blockedArtists + Store.softBlockedArtists).toList() }
    val folders = remember(vm.blockTick, vm.tracks) { (Store.blockedFolders + Store.softBlockedFolders).toList() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Gesperrt", onBack)
        Text("Orange = nur aus Mediathek/Shuffle (Stufe 1), rot = auch aus Suche (Stufe 2). Wischen: → Vollsperre, ← entsperren. Tippen spielt ab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LetterChip("Titel (${songs.size})", kind == 0, true) { kind = 0 }
            LetterChip("Interpreten (${artists.size})", kind == 1, true) { kind = 1 }
            LetterChip("Ordner (${folders.size})", kind == 2, true) { kind = 2 }
            LetterChip(if (sort == 0) "A–Z" else "Z–A", true, true) { sort = 1 - sort }
        }
        data class Entry(val key: String, val title: String, val sub: String, val soft: Boolean, val unblock: () -> Unit, val harden: (() -> Unit)?)
        val entries: List<Entry> = when (kind) {
            0 -> songs.map { t -> val soft = t.path in Store.softBlockedTracks; Entry(t.path, t.title, t.artist + " · " + t.fileName, soft, { vm.unblockTracks(listOf(t.path)) }, if (soft) ({ vm.hardBlockTrack(t.path) }) else null) }
            1 -> artists.map { a -> val soft = a in Store.softBlockedArtists; Entry(a, a, if (soft) "Interpret" else "Interpret", soft, { vm.unblockArtist(a) }, if (soft) ({ vm.hardBlockArtist(a) }) else null) }
            else -> folders.map { f -> val soft = f in Store.softBlockedFolders; Entry(f, File(f).name.ifEmpty { f }, f, soft, { vm.unblockFolder(f) }, if (soft) ({ vm.hardBlockFolder(f) }) else null) }
        }.sortedBy { Names.fold(it.title) }.let { if (sort == 1) it.reversed() else it }
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nichts gesperrt") }
        else LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { kind.toString() + it.key }) { e ->
                val eNow by androidx.compose.runtime.rememberUpdatedState(e)
                val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
                    positionalThreshold = { total -> total * 0.3f },
                    confirmValueChange = { v ->
                        when (v) {
                            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> eNow.harden?.invoke()      // nach rechts: Vollsperre
                            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> eNow.unblock()             // nach links: entsperren
                            else -> {}
                        }
                        false
                    }
                )
                androidx.compose.material3.SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = e.harden != null,
                    backgroundContent = {
                        val right = state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
                        Box(
                            Modifier.fillMaxSize().background(if (right) MaterialTheme.colorScheme.error else ORANGE).padding(horizontal = 20.dp),
                            contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Icon(Icons.Filled.Lock, null, tint = Color.Black)
                        }
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                            .clickable { playBlocked(vm, kind, e.key) }
                            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (kind) { 0 -> Icons.Filled.MusicNote; 1 -> Icons.Filled.Person; else -> Icons.Filled.Folder }, null,
                            tint = if (e.soft) ORANGE else MaterialTheme.colorScheme.error
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            MarqueeText(e.title, MaterialTheme.typography.bodyLarge, Modifier.fillMaxWidth(), align = TextAlign.Start)
                            MarqueeText(e.sub, MaterialTheme.typography.bodySmall, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, align = TextAlign.Start)
                        }
                        Icon(Icons.Filled.Lock, if (e.soft) "Mediathek-Sperre" else "Vollsperre", Modifier.size(18.dp), tint = if (e.soft) ORANGE else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun playBlocked(vm: MusicViewModel, kind: Int, key: String) {
    val all = Library.blocked
    val list = when (kind) {
        0 -> all.filter { it.path == key }
        1 -> all.filter { it.artist == key }
        else -> all.filter { it.folder == key }
    }
    if (list.isNotEmpty()) vm.play(list, 0, "q")
}

// ---------- Namen-Filter ----------

private val PRESETS = listOf(
    "(Official Video)", "[Official Video]", "(Official Music Video)", "[Official Music Video]",
    "(Official Audio)", "[Official Audio]", "(Lyrics)", "[Lyrics]", "(Lyric Video)", "(Audio)",
    "(HD)", "[HD]", "(HQ)", "[HQ]", "(Video)", "(Music Video)", "(Visualizer)", "(Remastered)"
)

@Composable
internal fun StripPage(vm: MusicViewModel, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val list = remember(vm.blockTick) { Store.stripStrings }
    // Roh-Dateinamen, damit auch bereits entfernte Texte noch als Treffer zählen
    val raw = remember(vm.tracks) { vm.tracks.map { it.fileName.substringBeforeLast('.') + " " + it.title } }

    fun hits(s: String) = raw.count { it.contains(s, ignoreCase = true) }
    fun examples(s: String) = vm.tracks.filter { (it.fileName + " " + it.title).contains(s, ignoreCase = true) }.take(3).map { it.fileName }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Namen-Filter", onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Texte, die aus Titeln entfernt werden (z. B. Markierungen von YouTube-Downloads). " +
                        "Gilt nur für die Anzeige. Mit „Dateinamen übernehmen“ kannst du die Dateien danach auch so umbenennen.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, label = { Text("Text entfernen") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.setStripStrings(list + input.trim()); input = "" }, enabled = input.isNotBlank()) {
                        Icon(Icons.Filled.Add, "Hinzufügen")
                    }
                }
            }
            val found = PRESETS.filter { p -> p !in list && hits(p) > 0 }
            if (found.isNotEmpty()) {
                item {
                    Text("Gefunden in deiner Bibliothek", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                }
                items(found, key = { "p$it" }) { p ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p, style = MaterialTheme.typography.bodyLarge)
                            Text("${hits(p)} Treffer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.setStripStrings(list + p) }) { Text("Hinzufügen") }
                    }
                }
            }
            item {
                Text("Aktive Filter", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
                if (list.isEmpty()) Text("Noch keine.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            items(list, key = { "s$it" }) { s ->
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { vm.setStripStrings(list - s) }) {
                            Icon(Icons.Filled.Delete, "Entfernen", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    val h = hits(s)
                    Text(
                        if (h == 0) "keine Treffer" else "$h Treffer, z. B.: " + examples(s).joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3
                    )
                }
            }
        }
    }
}
