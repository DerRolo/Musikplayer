package de.musikplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp

/** Lieder, die seit deinem letzten „Gesehen“ neu gefunden wurden: direkt Interpret zuordnen, umbenennen, einsortieren. */
@Composable
fun NewSongsPage(vm: MusicViewModel, onBack: () -> Unit) {
    val fresh = remember(vm.tracks, vm.seenTick) { vm.newTracks() }
    val existing = remember(vm.tracks, vm.seenTick) { vm.existingArtists() }
    var assign by remember { mutableStateOf<Track?>(null) }
    var rename by remember { mutableStateOf<Track?>(null) }
    var move by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Neue Lieder", onBack)
        if (fresh.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine neuen Lieder") }
        } else {
            Text(
                "${fresh.size} neue Lieder. Tippe auf ein Lied, um es anzuhören. Mit „Gesehen“ verschwindet es aus dieser Liste.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Button(onClick = { vm.markSeen(fresh) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                Text("  Alle als gesehen markieren")
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(fresh, key = { it.path }) { t ->
                    val unknown = t.artist == UNKNOWN_ARTIST
                    val similar = if (unknown) emptyList() else Names.similarArtists(t.artist, existing)
                    Column(Modifier.fillMaxWidth().clickable { vm.play(listOf(t), 0, "q") }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                        Text(t.artist + "  ·  " + formatDuration(t.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t.fileName + "  (" + t.folder.substringAfterLast('/') + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (unknown) {
                            Text("Kein Interpret erkannt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        } else if (t.artist !in existing) {
                            Text("Neuer Interpret", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (similar.isNotEmpty()) {
                            Text(
                                "Ähnlich wie schon vorhanden: " + similar.joinToString(", "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { assign = t }) { Icon(Icons.Filled.Person, null, Modifier.size(18.dp)); Text("  Interpret") }
                            TextButton(onClick = { rename = t }, colors = ButtonDefaults.textButtonColors(contentColor = ORANGE)) { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Text("  Umbenennen") }
                            if (!unknown) TextButton(onClick = { move = t }, colors = ButtonDefaults.textButtonColors(contentColor = ORANGE)) { Icon(Icons.Filled.DriveFileMove, null, Modifier.size(18.dp)); Text("  Einsortieren") }
                            TextButton(onClick = { vm.markSeen(listOf(t)) }) { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Text("  Gesehen") }
                        }
                    }
                }
            }
        }
    }

    assign?.let { t ->
        val similar = if (t.artist == UNKNOWN_ARTIST) emptyList() else Names.similarArtists(t.artist, existing)
        val folderHint = listOfNotNull(Library.folders().firstOrNull { it.path == t.folder }?.suggestion)
        TextInputDialog(
            title = "Interpret zuordnen", label = "Interpret",
            initial = if (t.artist == UNKNOWN_ARTIST) "" else t.artist,
            suggestions = (similar + folderHint).distinct(),
            confirmText = "Zuordnen",
            onDismiss = { assign = null },
            onConfirm = { vm.assignTrack(t.path, it); assign = null }
        )
    }
    rename?.let { t ->
        TextInputDialog(
            title = "Datei umbenennen", label = "Name (ohne Endung)",
            initial = t.fileName.substringBeforeLast('.'),
            suggestions = listOf(Library.proposedFileName(t).substringBeforeLast('.')).filter { it != t.fileName.substringBeforeLast('.') },
            confirmText = "Umbenennen",
            onDismiss = { rename = null },
            onConfirm = { vm.renameFiles(listOf(t to it)); rename = null }
        )
    }
    move?.let { t ->
        ConfirmDialog(
            title = "Einsortieren?",
            text = "„${t.fileName}“ wird in den Ordner von „${t.artist}“ verschoben.",
            confirmText = "Verschieben",
            onDismiss = { move = null },
            onConfirm = { vm.sortIntoFolders(listOf(t)); move = null }
        )
    }
}
