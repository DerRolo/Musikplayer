@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

private val FOLDER_SORTS = listOf("Meiste ohne Interpret", "Name", "Anzahl Lieder")

/**
 * Interpreten & Ordner. Liste (oder Einzeln-Karten) aller Ordner mit Liedern ohne Interpret.
 * Ein Ordner lässt sich öffnen: dort sieht man alle Lieder, markiert einzelne, ordnet sie zu, öffnet den Ordner im Dateimanager.
 */
@Composable
fun FoldersPage(vm: MusicViewModel, actions: TrackActions, onBack: () -> Unit) {
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableIntStateOf(1) } // 0 Einzeln, 1 Liste
    var sortIdx by rememberSaveable { mutableIntStateOf(0) }
    var input by remember { mutableStateOf<FolderInfo?>(null) }
    var sortConfirm by remember { mutableStateOf<List<Track>?>(null) }
    val sel = remember { Selection() }
    val artists = remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } }
    BackHandler(enabled = openPath != null) { sel.clear(); openPath = null }

    val all = vm.folders
    val folders = remember(all, sortIdx) {
        when (sortIdx) {
            0 -> all
            1 -> all.sortedBy { Names.fold(it.name) }
            else -> all.sortedByDescending { it.count }
        }
    }
    val open = folders.firstOrNull { it.path == openPath }

    Column(Modifier.fillMaxSize()) {
        if (open != null) {
            val inFolder = remember(vm.tracks, open.path) { vm.tracks.filter { it.folder == open.path }.sortedBy { it.sortKey } }
            PageHeader(open.name.ifBlank { open.path }, { sel.clear(); openPath = null })
            TrackListBody(vm, inFolder, "q", actions, sel, Modifier.weight(1f)) {
                FolderHeader(vm, open, inFolder, onInput = { input = open }, onSort = { sortConfirm = it })
            }
        } else {
            PageHeader("Interpreten & Ordner", onBack) {
                ViewToggle(listOf(VIEW_SINGLE, VIEW_LIST), view) { view = it }
            }
            if (folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Alle Lieder haben einen Interpreten") }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${folders.size} Ordner", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    SortMenu("Sortierung: " + FOLDER_SORTS[sortIdx], FOLDER_SORTS, sortIdx) { sortIdx = it }
                }
                if (view == 1) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(folders, key = { it.path }) { f -> FolderRow(f) { openPath = f.path } }
                    }
                } else {
                    FolderCards(vm, folders, onInput = { input = it }, onOpen = { openPath = it.path })
                }
            }
        }
    }
    input?.let { f ->
        AssignArtistDialog(
            f.unknownCount.coerceAtLeast(1), artists,
            onDismiss = { input = null },
            onConfirm = { vm.assignFolder(f.path, it); input = null }
        )
    }
    sortConfirm?.let { list ->
        ConfirmDialog(
            title = "Einsortieren?",
            text = "${list.size} Dateien werden in Ordner „Music/<Interpret>“ verschoben (neue Ordner werden angelegt). Playlists bleiben erhalten.",
            confirmText = "Verschieben",
            onDismiss = { sortConfirm = null },
            onConfirm = { vm.sortIntoFolders(list); sortConfirm = null }
        )
    }
}

@Composable
private fun FolderRow(f: FolderInfo, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(f.name.ifBlank { f.path }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${f.count} Lieder" + if (f.unknownCount > 0) " · ${f.unknownCount} ohne Interpret" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (f.unknownCount > 0 && f.assigned == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                f.path.removePrefix("/storage/emulated/0/"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (f.assigned != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(" " + f.assigned, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

/** Kopf eines geöffneten Ordners: Zuordnung und Aktionen. Die Lieder stehen darunter. */
@Composable
private fun FolderHeader(vm: MusicViewModel, f: FolderInfo, inFolder: List<Track>, onInput: () -> Unit, onSort: (List<Track>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(f.path.removePrefix("/storage/emulated/0/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${f.count} Lieder, davon ${f.unknownCount} ohne Interpret", style = MaterialTheme.typography.bodyMedium)
        if (f.assigned != null) {
            Text("Interpret für Lieder ohne Interpret: ${f.assigned}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        } else if (f.unknownCount > 0) {
            Text(
                "Lieder ohne Interpret bekommen den Namen, den du wählst. Alternativ unten einzelne Lieder markieren und zuordnen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (f.assigned == null && f.suggestion != null && f.unknownCount > 0) {
            Button(onClick = { vm.assignFolder(f.path, f.suggestion) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Text("  Alle ohne Interpret → „${f.suggestion}“")
            }
        }
        OutlinedButton(onClick = onInput, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Person, null, Modifier.size(20.dp)); Text("  Interpret für alle ohne Interpret wählen …")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { inFolder.firstOrNull()?.let { vm.showInFolder(it) } }, Modifier.weight(1f), enabled = inFolder.isNotEmpty()) {
                Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Text("  Dateimanager", maxLines = 1)
            }
            OutlinedButton(onClick = { vm.play(inFolder, 0, "q") }, Modifier.weight(1f), enabled = inFolder.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Text("  Anhören", maxLines = 1)
            }
        }
        val known = inFolder.filter { it.artist != UNKNOWN_ARTIST }
        OutlinedButton(
            onClick = { onSort(known) }, Modifier.fillMaxWidth(), enabled = known.isNotEmpty(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)
        ) { Icon(Icons.Filled.DriveFileMove, null, Modifier.size(18.dp)); Text("  ${known.size} Lieder mit Interpret in Interpreten-Ordner verschieben …") }
        if (f.assigned != null) OutlinedButton(
            onClick = { vm.assignFolder(f.path, null) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)
        ) { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)); Text("  Zuordnung entfernen") }
        OutlinedButton(
            onClick = { vm.blockFolder(f.path) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Text("  Ordner sperren (keine Musik)") }
        Text("Lieder in diesem Ordner:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Ansicht „Einzeln“: ein Ordner pro Karte, mit „Lieder ansehen“. */
@Composable
private fun FolderCards(vm: MusicViewModel, folders: List<FolderInfo>, onInput: (FolderInfo) -> Unit, onOpen: (FolderInfo) -> Unit) {
    val pager = rememberPagerState { folders.size }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Ordner ${(pager.currentPage + 1).coerceAtMost(folders.size)} von ${folders.size}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { folders[it].path }) { page ->
            val f = folders[page]
            val inFolder = remember(vm.tracks, f.path) { vm.tracks.filter { it.folder == f.path } }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(f.name.ifBlank { f.path }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(f.path.removePrefix("/storage/emulated/0/"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("${f.count} Lieder, davon ${f.unknownCount} ohne Interpret", style = MaterialTheme.typography.bodyMedium)
                if (f.assigned != null) Text("Interpret für diesen Ordner: ${f.assigned}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                else Text("Noch kein Interpret zugeordnet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onOpen(f) }, Modifier.fillMaxWidth()) { Text("Lieder ansehen und einzeln bearbeiten") }
                if (f.assigned == null && f.suggestion != null) {
                    OutlinedButton(onClick = { vm.assignFolder(f.path, f.suggestion) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Text("  Alle als „${f.suggestion}“")
                    }
                }
                OutlinedButton(onClick = { onInput(f) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null, Modifier.size(20.dp)); Text("  Interpret wählen …")
                }
                OutlinedButton(onClick = { inFolder.firstOrNull()?.let { vm.showInFolder(it) } }, Modifier.fillMaxWidth(), enabled = inFolder.isNotEmpty()) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp)); Text("  Im Dateimanager öffnen")
                }
            }
        }
    }
}
