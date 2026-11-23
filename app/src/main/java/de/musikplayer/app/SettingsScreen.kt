package de.musikplayer.app

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
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

private enum class Page { Menu, New, Folders, Sort, Names, Dupes, Cleanup, Strip, Blocked }

@Composable
fun SettingsTab(vm: MusicViewModel, reset: Int) {
    var page by rememberSaveable { mutableStateOf(Page.Menu) }
    OnReset(reset) { page = Page.Menu }
    BackHandler(enabled = page != Page.Menu) { page = Page.Menu }
    when (page) {
        Page.Menu -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MenuEntry(Icons.Filled.FiberNew, "Neue Lieder", "Neu gefundene Lieder: Interpret zuordnen, umbenennen, einsortieren") { page = Page.New }
            MenuEntry(Icons.Filled.Person, "Interpreten & Ordner", "Ordner prüfen, Interpret zuordnen") { page = Page.Folders }
            MenuEntry(Icons.Filled.DriveFileMove, "Nach Interpret einsortieren", "Zugeordnete Lieder in Musik/<Interpret> verschieben") { page = Page.Sort }
            MenuEntry(Icons.Filled.Edit, "Dateinamen übernehmen", "Dateien so benennen, wie sie in der App angezeigt werden") { page = Page.Names }
            MenuEntry(Icons.Filled.ContentCopy, "Duplikate", "Ähnliche Lieder finden und aufräumen") { page = Page.Dupes }
            MenuEntry(Icons.Filled.Build, "Aufräumen (erweitert)", "Duplikate und Dateinamen zusammen: pro Version behalten, säubern oder löschen") { page = Page.Cleanup }
            MenuEntry(Icons.Filled.FilterAlt, "Namen-Filter", "Texte, die aus Titeln entfernt werden (z. B. „Official Video“)") { page = Page.Strip }
            MenuEntry(Icons.Filled.Block, "Gesperrt", "Gesperrte Lieder, Interpreten und Ordner ansehen und entsperren") { page = Page.Blocked }
            Text(
                "Playlists, Zuordnungen und Einstellungen liegen nur in der App. Deine Dateien werden nie kopiert.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        Page.Folders -> FoldersPage(vm) { page = Page.Menu }
        Page.Sort -> SortPage(vm) { page = Page.Menu }
        Page.New -> NewSongsPage(vm) { page = Page.Menu }
        Page.Names -> NamesPage(vm) { page = Page.Menu }
        Page.Dupes -> DupesPage(vm) { page = Page.Menu }
        Page.Cleanup -> CleanupPage(vm) { page = Page.Menu }
        Page.Strip -> StripPage(vm) { page = Page.Menu }
        Page.Blocked -> BlockedPage(vm) { page = Page.Menu }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit) {
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
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.blockFolder(f.path) }) { Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Text("  Sperren") }
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
    var renameConfirm by remember { mutableStateOf<List<Pair<Track, String>>?>(null) }
    val groups = vm.visibleDupes()
    val byPath = vm.tracks.associateBy { it.path }
    val toDelete = marked.mapNotNull { byPath[it] }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Duplikate", onBack)
        Text(
            "Erkennt Lieder mit ähnlichem Dateinamen, ähnlicher Dateigröße und gleicher Länge. " +
                "Das erste Lied jeder Gruppe wird zum Behalten vorgeschlagen. Auf ein Lied tippen = anhören, Kästchen = zum Löschen markieren.",
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
        if (vm.dupes?.any { it.albumVariant } == true) {
            Row(Modifier.clickable { vm.dupAlbums = !vm.dupAlbums }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.dupAlbums, onCheckedChange = { vm.dupAlbums = it })
                Text("Auch gleiche Lieder auf verschiedenen Alben zeigen (${vm.dupes?.count { it.albumVariant }})", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (toDelete.isNotEmpty()) {
            Button(
                onClick = { showDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                Text("  ${toDelete.size} markierte löschen …")
            }
        }
        if (groups != null && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Duplikate gefunden") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            groups?.forEachIndexed { gi, g ->
                item(key = "h$gi") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${g.tracks.size} ähnliche Dateien" + (if (g.sameSize) " · gleiche Größe" else "") +
                                (if (g.reason.isNotEmpty()) "\n" + g.reason else ""),
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
                        Modifier.fillMaxWidth().clickable { vm.play(listOf(t), 0, "q") }.padding(start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { if (it) marked.add(t.path) else marked.remove(t.path) })
                        Column(Modifier.weight(1f)) {
                            Text(t.fileName)
                            Text(
                                formatSize(t.size) + " · " + formatDuration(t.durationMs) + " · " + t.folder.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            val proposed = Library.proposedFileName(t)
                            if (proposed != t.fileName) {
                                Text(
                                    "In der App: $proposed",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { renameConfirm = listOf(t to proposed.substringBeforeLast('.')) }) { Text("Dateinamen übernehmen", color = ORANGE) }
                            }
                        }
                        if (t == g.tracks[0]) Text("behalten?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    renameConfirm?.let { list ->
        RenameConfirmDialog(list, onDismiss = { renameConfirm = null }, onConfirm = { renameConfirm = null; vm.renameFiles(list) })
    }
    if (showDelete) {
        DeleteDialog(
            tracks = toDelete,
            onDismiss = { showDelete = false },
            onConfirm = { showDelete = false; vm.deleteTracks(toDelete); marked.clear() }
        )
    }
}

// ---------- Dateinamen übernehmen ----------

/** Zeigt vor dem Umbenennen alt → neu (die ersten Einträge) und verlangt eine Bestätigung. */
@Composable
private fun RenameConfirmDialog(entries: List<Pair<Track, String>>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entries.size == 1) "Datei umbenennen?" else "${entries.size} Dateien umbenennen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Die Dateien werden auf dem Handy umbenannt. Playlists und Favoriten bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.padding(vertical = 4.dp)) {
                    items(entries.take(6)) { (t, base) ->
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Text(t.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "→ " + Names.fileBaseName(base) + t.fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (entries.size > 6) item { Text("… und ${entries.size - 6} weitere", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Umbenennen", color = ORANGE) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun NamesPage(vm: MusicViewModel, onBack: () -> Unit) {
    var letter by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<List<Pair<Track, String>>?>(null) }
    var manual by remember { mutableStateOf<Track?>(null) }

    val cands = remember(vm.tracks) { vm.renameCandidates() }
    val groups = remember(cands) { cands.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val available = remember(groups) { groups.keys.map { Names.letterOf(it) }.toSet() }
    val active = letter?.takeIf { it in available }
    val shownGroups = remember(groups, active) { groups.filterKeys { active == null || Names.letterOf(it) == active } }
    val shownCount = shownGroups.values.sumOf { it.size }
    val existing = remember(vm.tracks) { vm.tracks.map { it.folder + "/" + it.fileName }.toHashSet() }

    fun proposals(list: List<Track>) = list.map { it to Library.proposedFileName(it).substringBeforeLast('.') }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Dateinamen übernehmen", onBack)
        Text(
            "Links der aktuelle Dateiname, darunter der Name, wie er in der App steht (ohne Nummern, Unterstriche usw.). " +
                "Geändert wird erst nach deiner Bestätigung.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (vm.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (cands.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Alle Dateinamen passen schon") }
        } else {
            LetterBar(active, available) { letter = it }
            Button(
                onClick = { confirm = proposals(shownGroups.values.flatten()) },
                enabled = shownCount > 0 && !vm.loading,
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE, contentColor = androidx.compose.ui.graphics.Color.Black),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) { Text(if (active == null) "Alle $shownCount übernehmen …" else "Alle mit $active ($shownCount) übernehmen …") }
            LazyColumn(Modifier.fillMaxSize()) {
                shownGroups.forEach { (artist, list) ->
                    item(key = "h$artist") {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$artist  (${list.size})", Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { confirm = proposals(list) }) { Text("Alle übernehmen", color = ORANGE) }
                        }
                    }
                    items(list, key = { "n${it.path}" }) { t ->
                        val proposed = Library.proposedFileName(t)
                        val clash = (t.folder + "/" + proposed) in existing
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp)) {
                            Text("Jetzt:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(t.fileName, style = MaterialTheme.typography.bodyMedium)
                            Text("Neu:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                            Text(proposed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            if (clash) Text("Name gibt es schon – wird nummeriert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { manual = t }) { Text("Ändern …", color = ORANGE) }
                                TextButton(onClick = { confirm = proposals(listOf(t)) }) { Text("Übernehmen", color = ORANGE) }
                            }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { list ->
        RenameConfirmDialog(list, onDismiss = { confirm = null }, onConfirm = { confirm = null; vm.renameFiles(list) })
    }
    manual?.let { t ->
        TextInputDialog(
            title = "Datei umbenennen", label = "Name (ohne Endung)",
            initial = Library.proposedFileName(t).substringBeforeLast('.'),
            confirmText = "Umbenennen",
            onDismiss = { manual = null },
            onConfirm = { vm.renameFiles(listOf(t to it)); manual = null }
        )
    }
}
