@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package de.musikplayer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

/**
 * Optionen → Papierkorb. Wie in anderen Liedlisten: Tippen spielt das Lied (einzeln, wie eine Datei von außen),
 * lange drücken startet die Mehrfachauswahl. Wiederherstellen (orange = Änderung an Dateien), endgültig löschen (rot).
 */
@Composable
fun TrashPage(vm: MusicViewModel, onBack: () -> Unit) {
    val items = vm.trashItems()
    val sel = remember { mutableStateListOf<String>() }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<TrashItem>?>(null) }
    val selected = items.filter { it.id in sel }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Papierkorb", onBack) {
            if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Leeren", color = MaterialTheme.colorScheme.error) }
        }
        Text(
            "Gelöschte Lieder liegen hier ${Trash.DAYS} Tage und werden danach endgültig gelöscht. " +
                "Beim Wiederherstellen kommen sie an ihren alten Ort zurück – mit Playlists, Favoriten und Zuordnungen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (items.isEmpty()) {
            Text("Der Papierkorb ist leer.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            return@Column
        }
        if (selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selected.size} ausgewählt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.restoreTrash(selected); sel.clear() }) { Text("Wiederherstellen", color = ORANGE) }
                OutlinedButton(onClick = { confirmDelete = selected }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            }
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            TextButton(onClick = { vm.restoreTrash(items) }) { Text("Alle wiederherstellen", color = ORANGE) }
        }
        val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { i ->
                val isSel = i.id in sel
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .combinedClickable(
                            onClick = { if (sel.isNotEmpty()) { if (isSel) sel.remove(i.id) else sel.add(i.id) } else vm.playTrash(i) },
                            onLongClick = { if (isSel) sel.remove(i.id) else sel.add(i.id) }
                        )
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sel.isNotEmpty()) Checkbox(checked = isSel, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                    Cover(emptyList(), i.title, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), sizePx = 120)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(i.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${i.artist} · gelöscht am ${fmt.format(Date(i.deletedAt))} · noch ${i.daysLeft()} Tage",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { vm.restoreTrash(listOf(i)) }) { Icon(Icons.Filled.RestoreFromTrash, "Wiederherstellen", tint = ORANGE) }
                    IconButton(onClick = { confirmDelete = listOf(i) }) { Icon(Icons.Filled.DeleteForever, "Endgültig löschen", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmEmpty) ConfirmDialog(
        title = "Papierkorb leeren?",
        text = "${items.size} Dateien werden endgültig vom Handy gelöscht. Das lässt sich nicht rückgängig machen.",
        confirmText = "Endgültig löschen",
        onDismiss = { confirmEmpty = false },
        onConfirm = { vm.deleteTrashForever(items); sel.clear(); confirmEmpty = false }
    )
    confirmDelete?.let { list ->
        ConfirmDialog(
            title = if (list.size == 1) "Endgültig löschen?" else "${list.size} Dateien endgültig löschen?",
            text = "Die Dateien werden endgültig vom Handy gelöscht. Das lässt sich nicht rückgängig machen.",
            confirmText = "Endgültig löschen",
            onDismiss = { confirmDelete = null },
            onConfirm = { vm.deleteTrashForever(list); sel.removeAll(list.map { it.id }); confirmDelete = null }
        )
    }
}
