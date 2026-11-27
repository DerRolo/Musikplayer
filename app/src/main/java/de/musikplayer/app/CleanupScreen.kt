@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val KEEP = 1
private const val KEEP_RENAME = 2
private const val DELETE = 3

/**
 * Duplikate und Dateinamen in einem Durchgang. Es wird nichts vorausgewählt: Du entscheidest bei jeder Version selbst.
 * Ausgeführt wird erst nach Bestätigung, zuerst Löschen, dann Umbenennen.
 * Ansicht „Einzeln“: eine Gruppe pro Seite, mit Wischen nach links/rechts blättern.
 */
@Composable
fun CleanupPage(vm: MusicViewModel, onBack: () -> Unit) {
    val choice = remember { mutableStateMapOf<String, Int>() }
    var confirm by remember { mutableStateOf(false) }
    var single by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (vm.dupes == null) vm.scanDupes() }
    val groups = vm.visibleDupes()
    var edit by remember { mutableStateOf<Track?>(null) }
    val byPath = vm.tracks.associateBy { it.path }

    val toDelete = choice.filterValues { it == DELETE }.keys.mapNotNull { byPath[it] }
    val toRename = choice.filterValues { it == KEEP_RENAME }.keys.mapNotNull { byPath[it] }
        .map { it to Library.proposedFileName(it).substringBeforeLast('.') }
    val allDeleted = groups.orEmpty().count { g -> g.tracks.all { choice[it.path] == DELETE } }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Aufräumen", onBack) {
            if (vm.dupBusy || vm.loading) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp))
            ViewToggle(listOf(VIEW_SINGLE, VIEW_LIST), if (single) 0 else 1) { single = it == 0 }
        }
        if (allDeleted > 0) {
            Text(
                "In $allDeleted Gruppe(n) ist alles zum Löschen gewählt – bitte mindestens eine Version behalten.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (vm.dupes?.any { it.albumVariant } == true) {
            Row(Modifier.clickable { vm.dupAlbums = !vm.dupAlbums }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.dupAlbums, onCheckedChange = { vm.dupAlbums = it })
                Text("Auch gleiche Lieder auf verschiedenen Alben zeigen (${vm.dupes?.count { it.albumVariant }})", style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
        if (groups != null && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Duplikate gefunden") }
        } else if (groups != null && single) {
            val pager = rememberPagerState { groups.size }
            Text(
                "Gruppe ${(pager.currentPage + 1).coerceAtMost(groups.size)} von ${groups.size}  ·  zum Blättern nach links/rechts wischen",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val g = groups[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (g.reason.isNotEmpty()) Text(g.reason, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        for (t in g.tracks) VersionCard(vm, t, choice, centered = true) { edit = it }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups?.forEachIndexed { gi, g ->
                    item(key = "h$gi") {
                        Text(
                            "Gruppe ${gi + 1} · ${g.tracks.size} Versionen" + if (g.reason.isNotEmpty()) " · " + g.reason else "",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
                        )
                    }
                    items(g.tracks.size, key = { "c${gi}_${g.tracks[it].path}" }) { idx -> VersionCard(vm, g.tracks[idx], choice, centered = false) { edit = it } }
                }
            }
        }
        }
        // „Ausführen“ erscheint unten über die ganze Breite, sobald etwas gewählt ist
        if ((toDelete.isNotEmpty() || toRename.isNotEmpty()) && allDeleted == 0) {
            Button(
                onClick = { confirm = true }, enabled = !vm.loading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Ausführen (${toDelete.size}× löschen, ${toRename.size}× umbenennen)") }
        }
    }

    edit?.let { t ->
        EditTrackDialog(
            t, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { edit = null },
            onConfirm = { n, a, mv -> vm.editTrack(t, n, a, mv); edit = null; choice.remove(t.path) }
        )
    }
    if (confirm) {
        // Gelöschtes kommt in den Papierkorb (30 Tage wiederherstellbar) – daher keine zusätzliche „endgültig“-Bestätigung
        val needSure = toDelete.isNotEmpty()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Aufräumen ausführen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (toDelete.isNotEmpty()) Text("${toDelete.size} Dateien kommen in den Papierkorb (Optionen → Papierkorb). Dort sind sie ${Trash.DAYS} Tage wiederherstellbar.")
                    if (toRename.isNotEmpty()) Text("${toRename.size} Dateien werden umbenannt. Playlists und Favoriten bleiben erhalten.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirm = false; vm.cleanup(toDelete, toRename); choice.clear() },
                    enabled = true
                ) { Text("Ausführen", color = if (needSure) MaterialTheme.colorScheme.error else ORANGE) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } }
        )
    }
}


/** Eine Version eines Liedes. Ohne Markierung bleibt sie unverändert; markiert wird nur eine Aktion (Löschen oder Name übernehmen). */
@Composable
private fun VersionCard(vm: MusicViewModel, t: Track, choice: MutableMap<String, Int>, centered: Boolean, onEdit: (Track) -> Unit) {
    val proposed = Library.proposedFileName(t)
    val hasProposal = proposed != t.fileName
    val c = choice[t.path]
    val guess = remember(t.path, t.artist) { if (t.artist == UNKNOWN_ARTIST) Library.guessArtistFromName(t) else null }
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val base = if (centered) {
        Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    } else Modifier.fillMaxWidth()
    Column(
        base.combinedClickable(onClick = { vm.play(listOf(t), 0, "q") }, onLongClick = { onEdit(t) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(t.fileName, style = MaterialTheme.typography.bodyMedium, textAlign = align)
        Text(formatSize(t.size) + " · " + formatDuration(t.durationMs), style = MaterialTheme.typography.bodySmall, color = dim, textAlign = align)
        Text(
            "Ordner: " + t.folder.removePrefix("/storage/emulated/0/").removePrefix("/sdcard/"),
            style = MaterialTheme.typography.bodySmall, color = dim, textAlign = align
        )
        if (t.artist == UNKNOWN_ARTIST) {
            Text("Interpret: unbekannt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = align)
            if (guess != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aus dem Namen: $guess", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                TextButton(onClick = { vm.assignTrack(t.path, guess) }) { Text("Übernehmen") }
            }
        } else {
            Text("Interpret: ${t.artist}", style = MaterialTheme.typography.bodySmall, color = dim, textAlign = align)
        }
        if (t.albumName.isNotBlank()) Text("Album: ${t.albumName}", style = MaterialTheme.typography.bodySmall, color = dim, textAlign = align)
        if (hasProposal) Text("Name in der App: $proposed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, textAlign = align)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Behalten", c == null || c == KEEP, MaterialTheme.colorScheme.primary) { choice.remove(t.path) }
            if (hasProposal) Choice("Name übernehmen", c == KEEP_RENAME, ORANGE) { if (c == KEEP_RENAME) choice.remove(t.path) else choice[t.path] = KEEP_RENAME }
            Choice("Löschen", c == DELETE, MaterialTheme.colorScheme.error) { if (c == DELETE) choice.remove(t.path) else choice[t.path] = DELETE }
        }
    }
}

@Composable
private fun Choice(text: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) color else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text, style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
