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
import androidx.compose.material.icons.filled.LockOpen
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

// ---------- Sperrliste ----------

@Composable
internal fun BlockedPage(vm: MusicViewModel, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 Lieder, 1 Interpreten, 2 Ordner
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 A–Z, 1 Z–A
    val songs = remember(vm.blockTick, vm.tracks) {
        Library.blocked.filter { it.path in Store.blockedTracks }
    }
    val artists = remember(vm.blockTick, vm.tracks) { Store.blockedArtists.toList() }
    val folders = remember(vm.blockTick, vm.tracks) { Store.blockedFolders.toList() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Gesperrt", onBack)
        Text(
            "Gesperrte Einträge bleiben auf dem Handy, werden von der App aber ignoriert.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LetterChip("Lieder (${songs.size})", kind == 0, true) { kind = 0 }
            LetterChip("Interpreten (${artists.size})", kind == 1, true) { kind = 1 }
            LetterChip("Ordner (${folders.size})", kind == 2, true) { kind = 2 }
            LetterChip(if (sort == 0) "A–Z" else "Z–A", true, true) { sort = 1 - sort }
        }
        data class Entry(val key: String, val title: String, val sub: String, val unblock: () -> Unit)
        val entries: List<Entry> = when (kind) {
            0 -> songs.map { t -> Entry(t.path, t.title, t.artist + "  ·  " + t.fileName) { vm.unblockTracks(listOf(t.path)) } }
            1 -> artists.map { a -> Entry(a, a, "Interpret") { vm.unblockArtist(a) } }
            else -> folders.map { f -> Entry(f, File(f).name.ifEmpty { f }, f) { vm.unblockFolder(f) } }
        }.sortedBy { Names.fold(it.title) }.let { if (sort == 1) it.reversed() else it }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nichts gesperrt") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { kind.toString() + it.key }) { e ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (kind) { 0 -> Icons.Filled.MusicNote; 1 -> Icons.Filled.Person; else -> Icons.Filled.Folder },
                            null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(e.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = e.unblock) {
                            Icon(Icons.Filled.LockOpen, null, Modifier.size(18.dp))
                            Text("  Entsperren")
                        }
                    }
                }
            }
        }
    }
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
