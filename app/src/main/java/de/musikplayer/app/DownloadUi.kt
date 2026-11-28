package de.musikplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Hinweis zur Rechtslage – wird auf der Seite und vor dem Ausschalten von „Nur geprüfte Seiten“ gezeigt. */
private const val DL_OK =
    "• Musik unter Creative-Commons-Lizenz oder gemeinfreie Aufnahmen (z. B. Jamendo, Free Music Archive, Internet Archive)\n" +
        "• Gekaufte Downloads und kostenlose Angebote der Künstler selbst (z. B. Bandcamp)\n" +
        "• Torrents, die der Rechteinhaber selbst anbietet (z. B. beim Internet Archive)"
private const val DL_NOT_OK =
    "• Aktuelle Charts, Alben oder Hörbücher von Seiten, die sie ohne Erlaubnis anbieten\n" +
        "• Musik über Torrent-Tauschbörsen ohne Erlaubnis: Beim Herunterladen lädt die Torrent-App automatisch Teile an andere hoch – " +
        "das gilt als Verbreiten, deine IP-Adresse ist für alle sichtbar, in Deutschland drohen Abmahnungen\n" +
        "• Mitschneiden von Streaming-Diensten, wenn deren Nutzungsbedingungen das verbieten"

@Composable
fun DownloadPage(onBack: () -> Unit, onOpenNew: () -> Unit) {
    val ctx = LocalContext.current
    var verified by remember { mutableStateOf(Store.dlVerifiedOnly) }
    var askOff by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var torrent by remember { mutableStateOf(Downloads.torrentApp(ctx)) }
    // Nach dem Installieren von LibreTorrent zurück in der App: Knopf gleich umstellen
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) torrent = Downloads.torrentApp(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Musik herunterladen", onBack) {
            TextButton(onClick = onOpenNew) { Text("Neue Lieder") }
        }
        Text(
            "Die Seite öffnet sich in deinem Standard-Browser. Was du dort herunterlädst, landet im Download-Ordner – " +
                "die App findet es, sobald du zurückkommst, und zeigt es unter „Neue Lieder“. Dort kannst du es wie gewohnt einsortieren.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // ---------- Geprüfte Seiten ----------
        SectionTitle("Geprüfte Seiten (legal)")
        for (s in Downloads.SITES) {
            Row(
                Modifier.fillMaxWidth().clickable { if (!Downloads.open(ctx, s.url)) note = "Kein Browser gefunden" }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text(s.info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.OpenInBrowser, "Öffnen", tint = ORANGE)
            }
        }

        // ---------- Freie Websuche ----------
        SectionTitle("Im Web suchen")
        if (verified) {
            Text(
                "Gesperrt, weil „Nur geprüfte Seiten“ eingeschaltet ist. Du kannst sie unten bei den Optionen freischalten.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        } else {
            val go = { if (!Downloads.search(ctx, query)) note = "Bitte einen Suchbegriff eingeben" }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Künstler, Album …") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { go() })
                )
                Button(onClick = { go() }, modifier = Modifier.padding(start = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = ORANGE)) {
                    Icon(Icons.Filled.Search, "Suchen")
                }
            }
            Text(
                "Achte selbst darauf, dass das Angebot erlaubt ist (siehe Hinweis unten).",
                style = MaterialTheme.typography.bodySmall, color = ORANGE,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // ---------- Torrent ----------
        SectionTitle("Torrent-Dateien")
        Text(
            "Torrents öffnet eine Torrent-App. Legal sind sie z. B. beim Internet Archive: Auf der Seite eines Albums gibt es " +
                "unter „Download options“ den Eintrag „TORRENT“. Stell in der Torrent-App als Speicherort den Download-Ordner ein, " +
                "dann erscheint die Musik unter „Neue Lieder“.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val app = torrent
            if (app != null) {
                OutlinedButton(onClick = { if (!Downloads.launch(ctx, app)) note = "Torrent-App lässt sich nicht öffnen" }) {
                    Text("${Downloads.appName(ctx, app)} öffnen")
                }
            } else {
                Button(onClick = { Downloads.installLibreTorrent(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = ORANGE)) {
                    Text("LibreTorrent installieren")
                }
            }
        }
        if (torrent == null) Text(
            "Keine Torrent-App gefunden. Vorschlag: LibreTorrent – kostenlos, Open Source, ohne Werbung (Play Store oder F-Droid).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ---------- Optionen ----------
        SectionTitle("Optionen")
        Row(
            Modifier.fillMaxWidth().clickable { if (verified) askOff = true else { verified = true; Store.setDlVerifiedOnly(ctx, true) } }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Nur geprüfte Seiten", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (verified) "Die App öffnet nur die Seiten oben. Die freie Websuche ist gesperrt."
                    else "Die freie Websuche ist freigeschaltet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = verified, onCheckedChange = { on ->
                if (on) { verified = true; Store.setDlVerifiedOnly(ctx, true) } else askOff = true
            })
        }

        // ---------- Hinweis ----------
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text("Was ist okay – und was nicht?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
        }
        Disclaimer()
        note?.let { Text(it, color = ORANGE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
    }

    if (askOff) AlertDialog(
        onDismissRequest = { askOff = false },
        title = { Text("Freie Websuche freischalten?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Außerhalb der geprüften Seiten bist du selbst dafür verantwortlich, dass ein Download erlaubt ist.")
                Disclaimer()
            }
        },
        confirmButton = {
            TextButton(onClick = { askOff = false; verified = false; Store.setDlVerifiedOnly(ctx, false) }) { Text("Verstanden, freischalten") }
        },
        dismissButton = { TextButton(onClick = { askOff = false }) { Text("Abbrechen") } }
    )
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Disclaimer() {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Okay", style = MaterialTheme.typography.labelLarge, color = Color(0xFF22C55E))
        Text(DL_OK, style = MaterialTheme.typography.bodySmall)
        Text("Nicht okay", style = MaterialTheme.typography.labelLarge, color = Color(0xFFEF4444), modifier = Modifier.padding(top = 8.dp))
        Text(DL_NOT_OK, style = MaterialTheme.typography.bodySmall)
        Text(
            "Ein VPN macht einen unerlaubten Download nicht legal. Dies ist ein allgemeiner Hinweis, keine Rechtsberatung.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
        )
    }
}
