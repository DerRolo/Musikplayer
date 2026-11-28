package de.musikplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Kurzfassung der Release-Notizen (GitHub-Markdown grob bereinigt). */
private fun shortNotes(s: String): String =
    s.lines().map { it.trim().removePrefix("#").removePrefix("#").removePrefix("#").trim().removePrefix("* ").removePrefix("- ") }
        .filter { it.isNotBlank() && !it.startsWith("**Full Changelog") }.take(8).joinToString("\n").take(600)

/** Beim Start: „Update bereit“ mit Installieren / Später. */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Updater.checkOnStart(ctx) }
    val rel = Updater.available ?: return
    if (Updater.dismissedTag == rel.tag) return
    val busy = Updater.progress >= 0f
    AlertDialog(
        onDismissRequest = { if (!busy) Updater.dismissedTag = rel.tag },
        title = { Text("Update bereit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Neue Version ${rel.name} (installiert: ${Updater.installedName(ctx)})", style = MaterialTheme.typography.titleSmall)
                if (rel.notes.isNotBlank()) Text(
                    shortNotes(rel.notes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())
                )
                if (busy) LinearProgressIndicator(progress = { Updater.progress }, modifier = Modifier.fillMaxWidth())
                Updater.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ORANGE) }
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { Updater.install(ctx, rel) } }, enabled = !busy) { Text("Installieren") } },
        dismissButton = { TextButton(onClick = { Updater.dismissedTag = rel.tag }, enabled = !busy) { Text("Später") } }
    )
}

/** Optionen → App-Update: Version, automatische Suche an/aus, jetzt suchen, installieren. */
@Composable
fun UpdatePage(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Store.autoUpdate) }
    val rel = Updater.available
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("App-Update", onBack)
        Text("Installiert: Version ${Updater.installedName(ctx)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().clickable { auto = !auto; Store.setAutoUpdate(ctx, auto) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatisch nach Updates suchen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bei jedem Start schaut die App im Hintergrund in den GitHub-Releases nach. Gibt es eine neue Version, erscheint „Update bereit“.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = auto, onCheckedChange = { auto = it; Store.setAutoUpdate(ctx, it) })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { Updater.check(ctx, manual = true) } }, enabled = Updater.progress < 0f) { Text("Jetzt suchen") }
            if (rel != null) Button(onClick = { scope.launch { Updater.install(ctx, rel) } }, enabled = Updater.progress < 0f) { Text("Version ${rel.name} installieren") }
        }
        if (Updater.progress >= 0f) LinearProgressIndicator(progress = { Updater.progress }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        Updater.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ORANGE, modifier = Modifier.padding(16.dp)) }
        if (rel != null && rel.notes.isNotBlank()) {
            Text("Neu in Version ${rel.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Text(shortNotes(rel.notes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Text(
            "Beim ersten Mal fragt Android, ob die App Updates installieren darf („Apps aus dieser Quelle zulassen“). " +
                "Danach zeigt Android vor jedem Update eine Bestätigung. Deine Daten bleiben erhalten.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
