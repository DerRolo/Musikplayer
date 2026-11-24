@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Neu gefundene Lieder, eins nach dem anderen als Karte. Nach links/rechts wischen blättert.
 * „Passt so“ nimmt das Lied auf (verschwindet aus dieser Liste), „Bearbeiten“ ändert Dateiname und Interpret.
 */
@Composable
fun NewSongsPage(vm: MusicViewModel, onBack: () -> Unit) {
    val fresh = remember(vm.tracks, vm.seenTick) { vm.newTracks() }
    val existing = remember(vm.tracks, vm.seenTick) { vm.existingArtists() }
    var edit by remember { mutableStateOf<Track?>(null) }
    var move by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Neue Lieder", onBack)
        if (fresh.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine neuen Lieder") }
        } else {
            val pager = rememberPagerState { fresh.size }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lied ${(pager.currentPage + 1).coerceAtMost(fresh.size)} von ${fresh.size}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { vm.markSeen(fresh) }) { Text("Alle: Passt so") }
            }
            Text(
                "Nach links/rechts wischen zum Blättern.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { fresh[it].path }) { page ->
                val t = fresh[page]
                val unknown = t.artist == UNKNOWN_ARTIST
                val guess = remember(t.path, t.artist) { if (unknown) Library.guessArtistFromName(t) else null }
                val folderHint = remember(t.path) { Library.folders().firstOrNull { it.path == t.folder }?.suggestion }
                val similar = if (unknown) emptyList() else Names.similarArtists(t.artist, existing)
                val quick = (listOfNotNull(guess, folderHint) + similar).distinct()
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(180.dp).clip(RoundedCornerShape(16.dp))) {
                        Cover(listOf(t), t.title, Modifier.fillMaxSize())
                        IconButton(onClick = { vm.play(listOf(t), 0, "q") }, Modifier.align(Alignment.Center).size(64.dp)) {
                            Icon(Icons.Filled.PlayArrow, "Anhören", Modifier.size(48.dp), tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                    Text(t.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (unknown) "Interpret: unbekannt" else "Interpret: ${t.artist}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (unknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (!unknown && t.artist !in existing) Text("Neuer Interpret in deiner Bibliothek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        t.fileName + "\nOrdner: " + t.folder.removePrefix("/storage/emulated/0/") + "  ·  " + formatDuration(t.durationMs),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                    if (quick.isNotEmpty()) {
                        Text("Interpret schnell zuordnen:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        Row(Modifier.horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (q in quick) LetterChip(q, q == t.artist, true) { vm.assignTrack(t.path, q) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.markSeen(listOf(t)) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Text("  Passt so")
                    }
                    OutlinedButton(onClick = { edit = t }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)); Text("  Bearbeiten (Name, Interpret)")
                    }
                    if (!unknown) OutlinedButton(onClick = { move = t }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = ORANGE)) {
                        Icon(Icons.Filled.DriveFileMove, null, Modifier.size(20.dp)); Text("  In Interpreten-Ordner verschieben")
                    }
                    OutlinedButton(onClick = { vm.blockTracks(listOf(t)) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Block, null, Modifier.size(20.dp)); Text("  Sperren (kein Lied)")
                    }
                }
            }
        }
    }

    edit?.let { t ->
        EditTrackDialog(
            t, remember(vm.tracks) { vm.existingArtists().sortedBy { Names.fold(it) } },
            onDismiss = { edit = null },
            onConfirm = { n, a -> vm.editTrack(t, n, a); edit = null }
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

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
