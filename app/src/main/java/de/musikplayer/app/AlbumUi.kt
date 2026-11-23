package de.musikplayer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Gemeinsamer Anfang aller Dateinamen (bis einschließlich des letzten „ - “), z. B. „Ice Cube (1991) - Death Certificate - “. */
private fun commonPrefix(bases: List<String>, artist: String, album: String): String {
    if (bases.isEmpty()) return ""
    var p = bases[0]
    for (b in bases.drop(1)) {
        var i = 0
        while (i < p.length && i < b.length && p[i].lowercaseChar() == b[i].lowercaseChar()) i++
        p = p.substring(0, i)
    }
    val cut = p.lastIndexOf(" - ")
    val s = if (cut >= 0) p.substring(0, cut + 3) else ""
    return if (s.length >= 4) s else "$artist - $album - "
}

private fun stripText(base: String, text: String): String {
    if (text.isBlank()) return base
    val r = base.replace(text, "", ignoreCase = true).trim().trimStart('-', '–', '—', ' ').trim()
    return if (r.isEmpty()) base else r
}

/**
 * Album bearbeiten: Albumname (nur in der App) und Text, der aus allen Dateinamen des Albums entfernt wird
 * (Vorschlag: der gemeinsame Anfang). Dateien werden erst nach Bestätigung umbenannt.
 */
@Composable
fun AlbumEditDialog(
    artist: String,
    album: String,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onRenameAlbum: (String) -> Unit,
    onRenameFiles: (List<Pair<Track, String>>) -> Unit
) {
    var name by remember { mutableStateOf(album) }
    val bases = remember(tracks) { tracks.map { it.fileName.substringBeforeLast('.') } }
    var strip by remember { mutableStateOf(commonPrefix(bases, artist, album)) }
    var confirm by remember { mutableStateOf(false) }
    val changes = remember(strip, tracks) {
        tracks.map { it to stripText(it.fileName.substringBeforeLast('.'), strip) }
            .filter { it.second != it.first.fileName.substringBeforeLast('.') }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Album bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Albumname (nur in der App)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onRenameAlbum(name.trim()); onDismiss() }, enabled = name.isNotBlank() && name.trim() != album) { Text("Albumnamen speichern") }
                HorizontalDivider()
                Text("Aus allen ${tracks.size} Dateinamen entfernen:", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(strip, { strip = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Text") })
                if (changes.isEmpty()) {
                    Text("Dieser Text kommt in keinem Dateinamen vor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("${changes.size} Dateien würden sich ändern, z. B.:", style = MaterialTheme.typography.bodySmall)
                    for ((t, new) in changes.take(4)) {
                        Text(t.fileName.substringBeforeLast('.'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("→ $new", style = MaterialTheme.typography.bodySmall, color = ORANGE, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    TextButton(onClick = { confirm = true }) { Text("${changes.size} Dateien umbenennen …", color = ORANGE) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Dateien umbenennen?") },
            text = { Text("${changes.size} Dateien werden auf dem Handy umbenannt. Playlists und Favoriten bleiben erhalten.") },
            confirmButton = { TextButton(onClick = { confirm = false; onRenameFiles(changes); onDismiss() }) { Text("Umbenennen", color = ORANGE) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } }
        )
    }
}
