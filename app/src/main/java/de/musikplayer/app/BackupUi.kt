package de.musikplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Text, was gesichert wird – gleich auf Seite und im Dialog. */
private const val WHAT = "Playlists, Hörbücher, Lieblingssongs, Wiedergabezahlen, Interpreten- und Album-Zuordnungen, eigene Cover, " +
    "Sperrliste, Sortierungen, Einstellungen und Profil"

/** Frische Installation: Gibt es eine Sicherung, wird einmal gefragt, ob sie zurückgespielt werden soll. */
@Composable
fun RestoreOnFirstStart() {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf(Store.restoreOffered) }
    if (asked) return
    val newest = remember { Backup.list().firstOrNull() }
    if (newest == null) {
        // nichts zu fragen: ab jetzt normal automatisch sichern
        androidx.compose.runtime.LaunchedEffect(Unit) { Store.setRestoreOffered(ctx); asked = true }
        return
    }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Sicherung gefunden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Es gibt eine Sicherung vom ${Backup.label(newest)}. Soll alles wie vorher eingerichtet werden?")
                Text("Enthält: $WHAT. Lieder werden über ihren Speicherort wieder zugeordnet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed) Text("Wiederherstellen hat nicht geklappt.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Backup.restore(ctx, newest) }
                    if (ok) Backup.restartApp(ctx) else { busy = false; failed = true }
                }
            }) { Text(if (busy) "Wird wiederhergestellt …" else "Wiederherstellen") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { Store.setRestoreOffered(ctx); asked = true }) { Text("Neu anfangen") } }
    )
}

/** Optionen → Sicherung (ganz unten): automatische Sicherung, „Jetzt sichern“, Sicherungen zurückspielen oder löschen. */
@Composable
fun BackupPage(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Store.autoBackup) }
    var info by remember { mutableStateOf<String?>(null) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val files = remember(Backup.tick) { Backup.list() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Sicherung", onBack)
        Text(
            "Für eine Neuinstallation: Die App sichert ihre Daten in den Ordner „${Backup.DIR}“ im Handyspeicher. Der bleibt beim Deinstallieren erhalten. " +
                "Nach dem Neuinstallieren fragt die App beim ersten Start, ob alles wiederhergestellt werden soll.\n\nGesichert werden: $WHAT. " +
                "Die Musikdateien selbst bleiben, wo sie sind – liegen sie nach der Neuinstallation an denselben Stellen, ist alles wie vorher. " +
                "Auch der Papierkorb liegt in diesem Ordner.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth().clickable { auto = !auto; Store.setAutoBackup(ctx, auto) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatisch sichern", style = MaterialTheme.typography.titleMedium)
                Text("Nach Änderungen im Hintergrund (höchstens alle 10 Minuten) und beim Verlassen der App. Die letzten 5 Sicherungen bleiben.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = auto, onCheckedChange = { auto = it; Store.setAutoBackup(ctx, it) })
        }
        Button(onClick = {
            info = "Sichere …"
            scope.launch {
                val f = withContext(Dispatchers.IO) { Backup.now(ctx) }
                info = if (f != null) "Gesichert: ${Backup.label(f)}" else (Backup.lastError ?: "Sicherung fehlgeschlagen")
            }
        }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Text("Jetzt sichern") }
        info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ORANGE, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        Text("Vorhandene Sicherungen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        if (files.isEmpty()) Text("Noch keine Sicherung.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        for (f in files) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Backup.label(f), style = MaterialTheme.typography.bodyLarge)
                    Text(formatSize(f.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { restoreFile = f }) { Text("Wiederherstellen", color = ORANGE) }
            }
        }
        if (files.isNotEmpty()) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Alle Sicherungen löschen", color = MaterialTheme.colorScheme.error)
        }
    }
    restoreFile?.let { f ->
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) restoreFile = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = { Text("Die aktuellen App-Daten werden durch den Stand vom ${Backup.label(f)} ersetzt. Der jetzige Stand wird vorher selbst noch gesichert. Danach startet die App neu.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Backup.restore(ctx, f) }
                        if (ok) Backup.restartApp(ctx) else { busy = false; restoreFile = null; info = "Wiederherstellen hat nicht geklappt." }
                    }
                }) { Text(if (busy) "Wird wiederhergestellt …" else "Wiederherstellen", color = ORANGE) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { restoreFile = null }) { Text("Abbrechen") } }
        )
    }
    if (confirmDelete) ConfirmDialog(
        title = "Alle Sicherungen löschen?",
        text = "Danach lässt sich nach einer Neuinstallation nichts mehr wiederherstellen (bis zur nächsten Sicherung). Der Papierkorb bleibt.",
        confirmText = "Löschen",
        onDismiss = { confirmDelete = false },
        onConfirm = { Backup.deleteAll(); confirmDelete = false; info = "Sicherungen gelöscht" }
    )
}
