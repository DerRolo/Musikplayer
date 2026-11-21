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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class Page { Menu, Folders, Sort, Dupes }

@Composable
fun SettingsTab(vm: MusicViewModel) {
    var page by remember { mutableStateOf(Page.Menu) }
    BackHandler(enabled = page != Page.Menu) { page = Page.Menu }
    when (page) {
        Page.Menu -> Column(Modifier.fillMaxSize()) {
            MenuEntry("Interpreten & Ordner", "Ordner prüfen, Interpret zuordnen") { page = Page.Folders }
            MenuEntry("Nach Interpret einsortieren", "Zugeordnete Lieder in Musik/<Interpret> verschieben") { page = Page.Sort }
            MenuEntry("Duplikate", "Ähnliche Lieder finden und aufräumen") { page = Page.Dupes }
            Text(
                "Playlists, Zuordnungen und Einstellungen liegen nur in der App. Deine Dateien werden nie kopiert.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        Page.Folders -> FoldersPage(vm) { page = Page.Menu }
        Page.Sort -> SortPage(vm) { page = Page.Menu }
        Page.Dupes -> DupesPage(vm) { page = Page.Menu }
    }
}

@Composable
private fun MenuEntry(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Zurück") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------- Interpreten & Ordner ----------

@Composable
private fun FoldersPage(vm: MusicViewModel, onBack: () -> Unit) {
    var input by remember { mutableStateOf<FolderInfo?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Interpreten & Ordner", onBack)
        Text(
            "Ordner mit Liedern ohne Interpret im Tag. Der Ordnername kann als Interpret übernommen werden.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (vm.folders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Alle Lieder haben einen Interpreten") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.folders, key = { it.path }) { f ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(f.name.ifBlank { f.path }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${f.count} Lieder, davon ${f.unknownCount} ohne Interpret",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(f.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (f.assigned != null) {
                        Text("Zugeordnet: ${f.assigned}", color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (f.assigned == null && f.suggestion != null) {
                            TextButton(onClick = { vm.assignFolder(f.path, f.suggestion) }) { Text("Als „${f.suggestion}“ übernehmen") }
                        }
                        TextButton(onClick = { input = f }) { Text(if (f.assigned == null) "Eingeben …" else "Ändern …") }
                        if (f.assigned != null) TextButton(onClick = { vm.assignFolder(f.path, null) }) { Text("Entfernen") }
                    }
                }
            }
        }
    }
    input?.let { f ->
        TextInputDialog(
            title = "Interpret für Ordner",
            label = "Interpret",
            initial = f.assigned ?: f.suggestion.orEmpty(),
            onDismiss = { input = null },
            onConfirm = { vm.assignFolder(f.path, it); input = null }
        )
    }
}

// ---------- Einsortieren ----------

@Composable
private fun SortPage(vm: MusicViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val cands = vm.sortCandidates()
    Column(Modifier.fillMaxSize()) {
        PageHeader("Nach Interpret einsortieren", onBack)
        Text(
            "Verschiebt Lieder mit zugeordnetem Interpret nach „<Speicher>/Music/<Interpret>“. " +
                "Liegt ein Lied auf der SD-Karte, bleibt es auf der SD-Karte. Playlists bleiben erhalten.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (vm.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Button(onClick = { confirm = true }, enabled = cands.isNotEmpty(), modifier = Modifier.padding(16.dp)) {
                Text(if (cands.isEmpty()) "Nichts einzusortieren" else "${cands.size} Lieder einsortieren")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(cands, key = { it.path }) { t ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(t.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ ${t.artist}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Dateien verschieben?",
            text = "${cands.size} Dateien werden in Interpretenordner verschoben.",
            confirmText = "Verschieben",
            onDismiss = { confirm = false },
            onConfirm = { confirm = false; vm.sortIntoFolders(cands) }
        )
    }
}

// ---------- Duplikate ----------

@Composable
private fun DupesPage(vm: MusicViewModel, onBack: () -> Unit) {
    val marked: SnapshotStateList<String> = remember { mutableStateListOf() }
    var showDelete by remember { mutableStateOf(false) }
    val groups = vm.dupes
    val byPath = vm.tracks.associateBy { it.path }
    val toDelete = marked.mapNotNull { byPath[it] }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Duplikate", onBack)
        Text(
            "Erkennt Lieder mit ähnlichem Dateinamen, ähnlicher Dateigröße und gleicher Länge. " +
                "Das erste Lied jeder Gruppe wird zum Behalten vorgeschlagen.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { marked.clear(); vm.scanDupes() }, enabled = !vm.dupBusy) { Text(if (groups == null) "Suchen" else "Neu suchen") }
            if (groups != null && groups.isNotEmpty()) {
                TextButton(onClick = {
                    marked.clear()
                    groups.forEach { g -> marked.addAll(g.tracks.drop(1).map { it.path }) }
                }) { Text("Alle Reste markieren") }
            }
            if (vm.dupBusy) CircularProgressIndicator(Modifier.padding(start = 4.dp))
        }
        if (toDelete.isNotEmpty()) {
            Button(
                onClick = { showDelete = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text("${toDelete.size} markierte löschen …") }
        }
        if (groups != null && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Duplikate gefunden") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            groups?.forEachIndexed { gi, g ->
                item(key = "h$gi") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${g.tracks.size} ähnliche Dateien" + if (g.sameSize) " · gleiche Größe" else "",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            for (t in g.tracks.drop(1)) if (t.path !in marked) marked.add(t.path)
                            marked.remove(g.tracks[0].path)
                        }) { Text("Rest markieren") }
                    }
                }
                items(g.tracks, key = { "d${gi}_${it.path}" }) { t ->
                    val checked = t.path in marked
                    Row(
                        Modifier.fillMaxWidth().clickable { if (checked) marked.remove(t.path) else marked.add(t.path) }.padding(start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { if (it) marked.add(t.path) else marked.remove(t.path) })
                        Column(Modifier.weight(1f)) {
                            Text(t.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                formatSize(t.size) + " · " + formatDuration(t.durationMs) + " · " + t.folder.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (t == g.tracks[0]) Text("behalten?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showDelete) {
        DeleteDialog(
            tracks = toDelete,
            onDismiss = { showDelete = false },
            onConfirm = { showDelete = false; vm.deleteTracks(toDelete); marked.clear() }
        )
    }
}
