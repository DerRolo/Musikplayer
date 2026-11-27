package de.musikplayer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun db(v: Double) = String.format(Locale.GERMANY, "%+.1f dB", v)
private fun lufs(v: Double) = String.format(Locale.GERMANY, "%.1f LUFS", v)

private fun methodText(m: Normalize.Method) = when (m) {
    Normalize.Method.Mp3 -> "Die MP3-Datei wird verlustfrei angepasst (wie mp3gain): nichts wird neu kodiert, Tags bleiben, jederzeit exakt umkehrbar. " +
        "Auch andere Player spielen das Lied danach angeglichen."
    Normalize.Method.Wav -> "Die WAV-Datei wird umgerechnet. Zurücknehmen ist jederzeit möglich."
    Normalize.Method.Playback -> "Dieses Format lässt sich auf dem Handy nicht ohne Qualitätsverlust ändern. " +
        "Die App gleicht die Lautstärke deshalb beim Abspielen an – die Datei bleibt unverändert."
}

/**
 * „Lautstärke angleichen“: Ein Lied wird zuerst gemessen und die Änderung angezeigt; bei mehreren Liedern läuft
 * alles im Hintergrund. Ziel: −14 LUFS (wie Spotify und YouTube), mit Schutz vor Übersteuern.
 */
@Composable
fun NormalizeDialog(vm: MusicViewModel, tracks: List<Track>, onDismiss: () -> Unit) {
    if (tracks.size > 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${tracks.size} Lieder angleichen?") },
            text = {
                Text(
                    "Jedes Lied wird gemessen und auf die übliche Streaming-Lautstärke (−14 LUFS) gebracht – gleichmäßig über das ganze Lied. " +
                        "MP3 und WAV werden in der Datei angepasst (MP3 verlustfrei), andere Formate beim Abspielen. " +
                        "Das dauert pro Lied einige Sekunden und lässt sich bei jedem Lied wieder zurücknehmen."
                )
            },
            confirmButton = { TextButton(onClick = { vm.normalizeMany(tracks); onDismiss() }) { Text("Angleichen", color = ORANGE) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
        )
        return
    }
    val t = tracks.firstOrNull() ?: return
    val prev = vm.normalizedInfo(t)
    // 0 = Original, 1 = angeglichen, -1 = kein Vorhören; beim Schließen wieder normale Lautstärke
    var listening by remember { mutableStateOf(-1) }
    DisposableEffect(t.path) { onDispose { vm.previewNormalize(t, null) } }
    // first = Messung fertig, second = Ergebnis (null = nicht lesbar)
    val plan by produceState<Pair<Boolean, Normalize.Plan?>>(false to null, t.path) { value = true to vm.planNormalize(t) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lautstärke angleichen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleSmall)
                if (prev != null) Text("Schon angeglichen: ${db(prev.second)} gegenüber dem Original.", style = MaterialTheme.typography.bodySmall, color = ORANGE)
                val p = plan.second
                when {
                    !plan.first -> {
                        Text("Lied wird gemessen …", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    p == null -> Text("Das Lied konnte nicht gelesen werden.", color = MaterialTheme.colorScheme.error)
                    else -> {
                        val diff = p.lufs - Normalize.TARGET_LUFS
                        Text(
                            "Gemessen: ${lufs(p.lufs)} – " + when {
                                kotlin.math.abs(diff) < 1.0 -> "passt schon gut."
                                diff > 0 -> "zu laut."
                                else -> "zu leise."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("Ziel: ${lufs(Normalize.TARGET_LUFS)} (Standard bei Spotify und YouTube)", style = MaterialTheme.typography.bodySmall)
                        Text("Änderung: ${db(p.gainDb)}" + if (p.limited) " (begrenzt, damit nichts übersteuert)" else "", style = MaterialTheme.typography.bodyMedium)
                        Text(methodText(p.method), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Vorhören: gleiche Stelle, nur die Lautstärke wechselt
                        val inFile = prev != null && prev.first != Normalize.Method.Playback
                        val origDb = if (inFile) -prev!!.second else 0.0
                        Text("Vorhören", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val sel = listening
                            if (sel == 0) Button(onClick = {}) { Text("Original") } else OutlinedButton(onClick = { listening = 0; vm.previewNormalize(t, origDb) }) { Text("Original") }
                            if (sel == 1) Button(onClick = {}) { Text("Angeglichen") } else OutlinedButton(onClick = { listening = 1; vm.previewNormalize(t, p.gainDb) }) { Text("Angeglichen") }
                        }
                        Text(
                            "Das Lied läuft dabei weiter, nur die Lautstärke wechselt. Die Datei wird erst mit „Angleichen“ geändert." +
                                if (p.gainDb > 0.05) " (Beim Vorhören wird das Anheben über Android simuliert, das Ergebnis in der Datei ist sehr ähnlich.)" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val p = plan.second
            TextButton(onClick = { if (p != null) vm.applyNormalize(p); onDismiss() }, enabled = p != null && kotlin.math.abs(p.gainDb) >= 0.05) {
                Text("Angleichen", color = ORANGE)
            }
        },
        dismissButton = {
            Column {
                if (prev != null) TextButton(onClick = { vm.undoNormalize(t); onDismiss() }) { Text("Original wiederherstellen", color = ORANGE) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}
