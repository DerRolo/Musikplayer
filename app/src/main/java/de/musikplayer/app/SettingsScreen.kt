@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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

private enum class Page { Menu, New, Folders, Sort, Names, Dupes, Cleanup, Strip, Blocked, Developer, DeveloperPreview, Jam, Trash, Profile, Update, Backup, Theme, Download }

@Composable
fun SettingsTab(vm: MusicViewModel, actions: TrackActions, sel: Selection, reset: Int) {
    var page by rememberSaveable { mutableStateOf(Page.Menu) }
    OnReset(reset) { page = Page.Menu }
    LaunchedEffect(vm.settingsPageRequest) {
        if (vm.settingsPageRequest == "blocked") { page = Page.Blocked; vm.settingsPageRequest = null }
        if (vm.settingsPageRequest == "jam") { page = Page.Jam; vm.settingsPageRequest = null }
        if (vm.settingsPageRequest == "trash") { page = Page.Trash; vm.settingsPageRequest = null }
    }
    BackHandler(enabled = page != Page.Menu) { page = if (page == Page.DeveloperPreview) Page.Developer else Page.Menu }
    when (page) {
        Page.Menu -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
            MenuEntry(Icons.Filled.SystemUpdate, "App-Update", "Version ${Updater.installedName(LocalContext.current)} · automatisch nach Updates suchen") { page = Page.Update }
            MenuEntry(Icons.Filled.Palette, "Design", "Hell, dunkel oder wie das System · Farbvorlagen") { page = Page.Theme }
            MenuEntry(Icons.Filled.AccountCircle, "Profil", "Name und Profilbild, die andere im Jam sehen") { page = Page.Profile }
            MenuEntry(Icons.Filled.Groups, "Jam", "Mit Handys im selben WLAN zusammen hören und Musik übernehmen") { page = Page.Jam }
            // Im Jam sind die Bearbeiten-Werkzeuge gesperrt (außer alle erlauben es in den Developer Options)
            val locked = Jam.editLocked
            if (locked) Text(
                "Im Jam sind die Werkzeuge zum Bearbeiten gesperrt, damit sich nichts ändert, während andere zuhören.",
                style = MaterialTheme.typography.bodySmall, color = ORANGE, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            MenuEntry(Icons.Filled.Download, "Musik herunterladen", "Geprüfte, legale Seiten im Browser öffnen · Torrent-App") { page = Page.Download }
            MenuEntry(Icons.Filled.FiberNew, "Neue Lieder", "Neu gefundene Lieder: Interpret zuordnen, umbenennen, einsortieren") { page = Page.New }
            MenuEntry(Icons.Filled.Person, "Interpreten & Ordner", "Ordner prüfen, Interpret zuordnen", enabled = !locked) { page = Page.Folders }
            MenuEntry(Icons.Filled.DriveFileMove, "Nach Interpret einsortieren", "Zugeordnete Lieder in Musik/<Interpret> verschieben", enabled = !locked) { page = Page.Sort }
            MenuEntry(Icons.Filled.Edit, "Dateinamen übernehmen", "Dateien so benennen, wie sie in der App angezeigt werden", enabled = !locked) { page = Page.Names }
            MenuEntry(Icons.Filled.ContentCopy, "Duplikate", "Ähnliche Lieder finden und aufräumen", enabled = !locked) { page = Page.Dupes }
            MenuEntry(Icons.Filled.Build, "Aufräumen", "Duplikate und Dateinamen zusammen: pro Version behalten, säubern oder löschen", enabled = !locked) { page = Page.Cleanup }
            MenuEntry(Icons.Filled.FilterAlt, "Namen-Filter", "Texte, die aus Titeln entfernt werden (z. B. „Official Video“)", enabled = !locked) { page = Page.Strip }
            MenuEntry(Icons.Filled.DeleteOutline, "Papierkorb", "Gelöschte Lieder ${Trash.DAYS} Tage lang wiederherstellen, anhören oder endgültig löschen") { page = Page.Trash }
            MenuEntry(Icons.Filled.Lock, "Gesperrt", "Gesperrte Lieder, Interpreten und Ordner ansehen und entsperren") { page = Page.Blocked }
            MenuEntry(Icons.Filled.Tune, "Developer Options", "Developer tools and UI settings") { page = Page.Developer }
            // Ganz unten: Sicherung für eine Neuinstallation
            val lastBackup = remember(Backup.tick) { Backup.list().firstOrNull()?.let { Backup.label(it) } }
            MenuEntry(
                Icons.Filled.SettingsBackupRestore, "Sicherung",
                "Für eine Neuinstallation: Playlists, Zuordnungen, Einstellungen usw. sichern · " + (lastBackup?.let { "zuletzt $it" } ?: "noch keine Sicherung")
            ) { page = Page.Backup }
            var silence by remember { mutableStateOf(Store.skipSilence) }
            var showSilence by remember { mutableStateOf(false) }
            if (showSilence) SilenceDialog { showSilence = false }
            Row(Modifier.fillMaxWidth().clickable { silence = !silence; vm.setSkipSilence(silence) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Stille überspringen", style = MaterialTheme.typography.titleMedium)
                    Text("Stille am Anfang und Ende eines Liedes wird übersprungen, Hall bleibt erhalten", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.IconButton(onClick = { showSilence = true }) { Icon(Icons.Filled.Tune, "Feineinstellung") }
                androidx.compose.material3.Switch(checked = silence, onCheckedChange = { silence = it; vm.setSkipSilence(it) })
            }
            var quickMove by remember { mutableStateOf(Store.quickMove) }
            Row(Modifier.fillMaxWidth().clickable { quickMove = !quickMove; Store.setQuickMove(appCtx, quickMove) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Schnell-Speichern verschiebt", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Der orange Speichern-Knopf im Player (Dateinamen übernehmen) schiebt das Lied zusätzlich in den Interpreten-Ordner. " +
                            "Lieder, die schon im Interpreten-Ordner oder einem Unterordner (Album) liegen, bleiben, wo sie sind.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Checkbox(checked = quickMove, onCheckedChange = { quickMove = it; Store.setQuickMove(appCtx, it) })
            }
            Text(
                "Playlists, Zuordnungen und Einstellungen liegen nur in der App. Deine Dateien werden nie kopiert.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        Page.Folders -> FoldersPage(vm, actions) { page = Page.Menu }
        Page.Sort -> SortPage(vm) { page = Page.Menu }
        Page.New -> NewSongsPage(vm, actions, onDownload = { page = Page.Download }) { page = Page.Menu }
        Page.Names -> NamesPage(vm) { page = Page.Menu }
        Page.Dupes -> DupesPage(vm) { page = Page.Menu }
        Page.Cleanup -> CleanupPage(vm) { page = Page.Menu }
        Page.Strip -> StripPage(vm) { page = Page.Menu }
        Page.Blocked -> BlockedPage(vm) { page = Page.Menu }
        Page.Developer -> DeveloperOptionsPage(vm, onOpenPreview = { page = Page.DeveloperPreview }) { page = Page.Menu }
        Page.DeveloperPreview -> DeveloperPreviewPage(vm) { page = Page.Developer }
        Page.Jam -> JamPage(vm) { page = Page.Menu }
        Page.Trash -> TrashPage(vm) { page = Page.Menu }
        Page.Profile -> ProfilePage { page = Page.Menu }
        Page.Update -> UpdatePage { page = Page.Menu }
        Page.Backup -> BackupPage { page = Page.Menu }
        Page.Theme -> ThemePage { page = Page.Menu }
        Page.Download -> DownloadPage(onBack = { page = Page.Menu }, onOpenNew = { page = Page.New })
    }
}

@Composable
private fun DeveloperOptionsPage(vm: MusicViewModel, onOpenPreview: () -> Unit, onBack: () -> Unit) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Developer Options", onBack)
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenPreview).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text("UI Preview", style = MaterialTheme.typography.titleMedium)
                Text("Preview the actual song-list row and quick actions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        var jamEdit by remember { mutableStateOf(Store.devJamEdit) }
        Row(Modifier.fillMaxWidth().clickable { jamEdit = !jamEdit; Store.setDevJamEdit(appContext, jamEdit) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Allow editing in Jam", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Edit files, tags, covers, block and delete while in a Jam. Only takes effect if everyone in the Jam has this enabled. Off by default.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(checked = jamEdit, onCheckedChange = { jamEdit = it; Store.setDevJamEdit(appContext, it) })
        }
        var showAlbum by remember { mutableStateOf(Store.showAlbum) }
        Row(Modifier.fillMaxWidth().clickable { showAlbum = !showAlbum; Store.setShowAlbum(appContext, showAlbum) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show Album", style = MaterialTheme.typography.titleMedium)
                Text("Show album name in song lists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = showAlbum, onCheckedChange = { showAlbum = it; Store.setShowAlbum(appContext, it) })
        }
        Column(Modifier.padding(16.dp)) {
            Text("UI Size", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small" to 0.85f, "Medium" to 1.0f, "Large" to 1.25f).forEach { (label, value) ->
                    LetterChip(label, kotlin.math.abs(vm.rowScale - value) < 0.05f, true) { vm.changeRowScale(value) }
                }
            }
        }
    }
}

@Composable
private fun DeveloperPreviewPage(vm: MusicViewModel, onBack: () -> Unit) {
    val sample = remember {
        Track(-999L, "/preview/sample.mp3", "A Very Long Song Title for UI Preview", "Sample Artist", "Sample Artist", "Sample Album", 0L, 272000L, 0L, 0L, albumName = "Sample Album")
    }
    val inert = remember {
        TrackActions(
            onPlayNext = {}, onAddToQueue = {}, onAddToPlaylist = {}, onAssignArtist = {}, onDelete = {},
            onAddManyToPlaylist = {}, onDeleteMany = {}, onToggleFavorite = {}, isFavorite = { false }, onBlock = {}
        )
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("UI Preview", onBack)
        Text("Actual song row · all UI sizes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        listOf("Small" to 0.85f, "Medium" to 1.0f, "Large" to 1.25f).forEach { (label, v) ->
            Text(label + if (kotlin.math.abs(vm.rowScale - v) < 0.05f) " (current)" else "", style = MaterialTheme.typography.labelMedium, color = ORANGE, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            androidx.compose.runtime.CompositionLocalProvider(LocalRowScale provides v) {
                TrackRow(sample, inert, compact = false, swipe = false, onClick = {})
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Text("Preview uses the same component as song lists. Actions are inert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, title: String, sub: String, enabled: Boolean = true, onClick: () -> Unit) {
    // Ausgegraut, wenn gerade nicht verfügbar (z. B. Bearbeiten im Jam)
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(16.dp).then(if (enabled) Modifier else Modifier.alpha(0.4f)), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PageHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Zurück") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
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
    var single by remember { mutableStateOf(false) }

    val cands = remember(vm.tracks) { vm.renameCandidates() }
    val groups = remember(cands) { cands.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val available = remember(groups) { groups.keys.map { Names.letterOf(it) }.toSet() }
    val active = letter?.takeIf { it in available }
    val shownGroups = remember(groups, active) { groups.filterKeys { active == null || Names.letterOf(it) == active } }
    val shownCount = shownGroups.values.sumOf { it.size }
    val existing = remember(vm.tracks) { vm.tracks.map { it.folder + "/" + it.fileName }.toHashSet() }

    fun proposals(list: List<Track>) = list.map { it to Library.proposedFileName(it).substringBeforeLast('.') }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Dateinamen übernehmen", onBack) { ViewToggle(listOf(VIEW_SINGLE, VIEW_LIST), if (single) 0 else 1) { single = it == 0 } }
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
            if (single) {
                val flat = shownGroups.values.flatten()
                val pager = rememberPagerState { flat.size }
                if (flat.isNotEmpty()) {
                    Text(
                        "Lied ${(pager.currentPage + 1).coerceAtMost(flat.size)} von ${flat.size}  ·  nach links/rechts wischen",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    HorizontalPager(pager, Modifier.fillMaxSize(), key = { flat[it].path }) { page ->
                        val t = flat[page]
                        val proposed = Library.proposedFileName(t)
                        val clash = (t.folder + "/" + proposed) in existing
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(16.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(t.artist, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                                Text("Jetzt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                                Text(t.fileName.substringBeforeLast('.'), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                                Text("Neu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                                Text(proposed.substringBeforeLast('.'), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                                if (clash) Text("Name gibt es schon – wird nummeriert", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                Button(
                                    onClick = { confirm = proposals(listOf(t)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ORANGE, contentColor = androidx.compose.ui.graphics.Color.Black),
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                ) { Text("Übernehmen") }
                                OutlinedButton(onClick = { manual = t }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)) { Text("Ändern …") }
                            }
                        }
                    }
                }
            } else
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
                            Text(t.fileName.substringBeforeLast('.'), style = MaterialTheme.typography.bodyMedium)
                            Text("Neu:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
                            Text(proposed.substringBeforeLast('.'), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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

/** Feineinstellung „Stille überspringen“: ab welcher Lautstärke etwas Stille ist, und wie viel Stille vor dem ersten / nach dem letzten Ton bleibt. */
@Composable
fun SilenceDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun toDb(thr: Int) = (20.0 * Math.log10(thr.coerceAtLeast(1) / 32768.0)).toFloat()
    fun fromDb(db: Float) = (32768.0 * Math.pow(10.0, db / 20.0)).toInt().coerceIn(10, 3000)
    var db by remember { mutableStateOf(toDb(Store.silenceThr)) }
    var lead by remember { mutableStateOf(Store.silenceKeepMs.toFloat()) }
    var tail by remember { mutableStateOf(Store.silenceTailMs.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stille überspringen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Beispiel: Stille am Anfang/Ende wird übersprungen", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1f).height(24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(0.35f).height(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(2f).height(24.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(0.55f).height(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(1f).height(24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Stille", style = MaterialTheme.typography.labelSmall)
                    Text("Ton", style = MaterialTheme.typography.labelSmall)
                    Text("Hall bleibt", style = MaterialTheme.typography.labelSmall)
                    Text("Stille", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "Die App schneidet intern: Alles vor dem ersten Ton und nach dem letzten Ton fällt weg. Pausen mitten im Lied bleiben. Deine Dateien werden nicht verändert.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Als Stille gilt alles leiser als ${db.toInt()} dB", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(db, { db = it }, valueRange = -70f..-30f)
                Text("Vor dem ersten Ton bleiben ${lead.toInt()} ms Stille", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(lead, { lead = it }, valueRange = 0f..2000f)
                Text("Nach dem letzten Ton bleiben ${tail.toInt()} ms (Hall)", style = MaterialTheme.typography.labelLarge)
                androidx.compose.material3.Slider(tail, { tail = it }, valueRange = 0f..4000f)
                Text(
                    "Gilt für Lieder, die ab jetzt gestartet werden. Wird die dB-Schwelle geändert, misst die App jedes Lied beim nächsten Abspielen neu.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { Store.setSilence(ctx, fromDb(db), lead.toInt(), tail.toInt()); onDismiss() }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { db = toDb(200); lead = 300f; tail = 1000f }) { Text("Standard") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}
