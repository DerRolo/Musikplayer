package de.musikplayer.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch

private val OWNED = Color(0xFF22C55E)

// ---------- Cover von anderen Geräten / Fremddateien ----------

/** Cover eines Liedes auf einem anderen Gerät (wird im WLAN geladen und zwischengespeichert). */
@Composable
fun RemoteCover(owner: String, id: Long, label: String, modifier: Modifier, size: Int = 200) {
    val bmp by produceState<ImageBitmap?>(null, owner, id) { value = Jam.cover(owner, id, size)?.asImageBitmap() }
    Box(modifier.background(placeholderColor(label)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(label.trim().take(1).uppercase().ifEmpty { "♪" }, color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Cover für Lieder, die nicht in der eigenen Bibliothek liegen: Fremddatei (aus der Datei) oder Jam-Lied (vom Besitzer). */
@Composable
fun rememberForeignArt(item: MediaItem?, size: Int = 512): ImageBitmap? {
    val initial = remember(item?.mediaId) { External.artwork(item)?.asImageBitmap() }
    val bmp by produceState(initial, item?.mediaId) {
        val e = Jam.entryOfItem(item)
        value = if (e != null) Jam.cover(e.owner, e.trackId, size)?.asImageBitmap() else External.artwork(item)?.asImageBitmap()
    }
    return bmp
}

// ---------- Leiste über der Mini-Leiste ----------

@Composable
fun JamBar(onOpen: () -> Unit) {
    val t = Jam.transfer
    val showTransfer = t != null && !t.finished
    if (!Jam.active && !showTransfer) return
    val s = Jam.state
    val text = when (Jam.role) {
        Jam.Role.Host -> "Dein Jam · " + personen(s?.members?.size ?: 1)
        Jam.Role.Guest -> "Im Jam von ${s?.hostName ?: "…"} · " + personen(s?.members?.size ?: 2)
        Jam.Role.None -> "Übernehmen"
    }
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, Modifier.size(18.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showTransfer && t != null) Text("⬇ ${t.done}/${t.total}", style = MaterialTheme.typography.labelMedium)
            }
            if (showTransfer && t != null) LinearProgressIndicator(progress = { if (t.total > 0) t.done.toFloat() / t.total else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

private fun personen(n: Int) = if (n == 1) "1 Person" else "$n Personen"

// ---------- Pop-ups (überall in der App) ----------

/** Jemand in der Nähe / Beitrittsanfrage an den Host. [onOpenJam] öffnet die Jam-Seite. */
@Composable
fun JamDialogs(onOpenJam: () -> Unit) {
    val req = Jam.requests.firstOrNull()
    if (req != null && Jam.role == Jam.Role.Host) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Groups, null) },
            title = { Text("Beitrittsanfrage") },
            text = { Text("${req.name} möchte deinem Jam beitreten. Danach hört ${req.name} mit, kann Lieder starten und einreihen und deine Bibliothek ansehen.") },
            confirmButton = { TextButton(onClick = { Jam.accept(req) }) { Text("Annehmen") } },
            dismissButton = { TextButton(onClick = { Jam.decline(req) }) { Text("Ablehnen") } }
        )
        return
    }
    val p = Jam.invite ?: return
    if (Jam.role != Jam.Role.None) return
    val hosting = p.hostingJam != null
    AlertDialog(
        onDismissRequest = { Jam.dismissInvite() },
        icon = { Icon(Icons.Filled.Wifi, null) },
        title = { Text(if (hosting) "Jam in der Nähe" else "Jemand in der Nähe") },
        text = {
            Text(
                if (hosting) "${p.name} hat einen Jam gestartet. Beitreten und zusammen hören?"
                else "${p.name} ist im selben WLAN und hat auch den Musikplayer. Jam erstellen? Dann kann ${p.name} beitreten."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (hosting) Jam.join(p) else Jam.createJam()
                onOpenJam()
            }) { Text(if (hosting) "Jam beitreten" else "Jam erstellen") }
        },
        dismissButton = { TextButton(onClick = { Jam.dismissInvite() }) { Text("Später") } }
    )
}

// ---------- Jam-Seite ----------

@Composable
fun JamPage(vm: MusicViewModel, onBack: () -> Unit) {
    var browse by rememberSaveable { mutableStateOf<String?>(null) }
    val owner = browse
    if (owner != null) {
        BackHandler { browse = null }
        JamLibraryPage(vm, owner) { browse = null }
        return
    }
    val ctx = LocalContext.current.applicationContext
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Jam", onBack)
        Text(
            "Zusammen Musik hören mit Handys im selben WLAN – wie bei Spotify. Alle hören, was der Host spielt, " +
                "jeder kann Lieder starten und einreihen. Außerdem könnt ihr Lieder, Alben, Interpreten und Playlists voneinander übernehmen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        var visible by remember { mutableStateOf(Store.jamVisible) }
        Row(Modifier.fillMaxWidth().clickable { visible = !visible; Jam.setVisible(ctx, visible) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text("Im WLAN sichtbar", style = MaterialTheme.typography.titleMedium)
                Text("Andere mit der App sehen dich und bekommen ein Pop-up zum Jam. Ohne Bestätigung kommt niemand an deine Musik.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = visible, onCheckedChange = { visible = it; Jam.setVisible(ctx, it) })
        }

        TransferCard()

        when (Jam.role) {
            Jam.Role.None -> NotInJam()
            else -> InJam(vm) { browse = it }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TransferCard() {
    val t = Jam.transfer ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(if (t.finished) "Übernehmen fertig" else "Übernehmen läuft", style = MaterialTheme.typography.titleSmall)
            if (!t.finished) {
                LinearProgressIndicator(progress = { if (t.total > 0) t.done.toFloat() / t.total else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                Text("${t.done} von ${t.total} · ${t.current}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(
                    "${t.total - t.skipped - t.failed} neu" + (if (t.skipped > 0) ", ${t.skipped} schon vorhanden" else "") + (if (t.failed > 0) ", ${t.failed} fehlgeschlagen" else "") +
                        ". Neue Lieder findest du unter Optionen → Neue Lieder.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NotInJam() {
    val waiting = Jam.waitingFor
    if (waiting != null) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Warte auf $waiting …", style = MaterialTheme.typography.titleMedium)
                Text("$waiting muss die Anfrage auf dem eigenen Handy annehmen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { Jam.cancelJoin() }) { Text("Abbrechen") }
        }
        return
    }
    Button(onClick = { Jam.createJam() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Icon(Icons.Filled.Groups, null); Spacer(Modifier.width(8.dp)); Text("Jam erstellen")
    }
    SectionTitle("In der Nähe")
    val peers = Jam.peers.sortedWith(compareByDescending<JamPeer> { it.hostingJam != null }.thenBy { it.name })
    if (peers.isEmpty()) {
        Text(
            if (Store.jamVisible) "Gerade niemand gefunden. Beide Handys müssen im selben WLAN sein und die App geöffnet haben."
            else "„Im WLAN sichtbar“ ist aus – dann wird auch nicht nach anderen gesucht.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    for (p in peers) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(if (p.hostingJam != null) "hat einen Jam gestartet" else "in der Nähe, kein Jam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.hostingJam != null) Button(onClick = { Jam.join(p) }) { Text("Beitreten") }
        }
    }
}

@Composable
private fun InJam(vm: MusicViewModel, onBrowse: (String) -> Unit) {
    val s = Jam.state
    val host = Jam.role == Jam.Role.Host
    // Läuft gerade
    SectionTitle(if (host) "Du bist der Host" else "Jam von ${s?.hostName ?: "…"}")
    val cur = s?.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cur != null) {
            EntryCover(cur, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(cur.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${cur.artist} · von ${Jam.memberName(cur.owner)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val playing = s?.playing == true
            IconButton(onClick = { vm.togglePlay() }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Abspielen") }
        } else Text(
            "Gerade läuft nichts. Starte ein Lied – aus deiner Bibliothek oder der eines anderen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val next = s?.upcoming.orEmpty()
    if (next.isNotEmpty()) {
        SectionTitle("Als Nächstes")
        for (e in next.take(10)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                EntryCover(e, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.artist} · von ${Jam.memberName(e.owner)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (next.size > 10) Text("… und ${next.size - 10} weitere", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
    // Mitglieder und ihre Bibliotheken
    SectionTitle("Im Jam")
    for (m in s?.members.orEmpty()) {
        val me = m.id == Jam.myId
        Row(Modifier.fillMaxWidth().then(if (me) Modifier else Modifier.clickable { onBrowse(m.id) }).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name + if (me) " (du)" else "", style = MaterialTheme.typography.titleMedium)
                Text(if (m.id == s?.hostId) "Host" else "Gast", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!me) OutlinedButton(onClick = { onBrowse(m.id) }) {
                Icon(Icons.Filled.LibraryMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Bibliothek")
            }
        }
    }
    if (host && (s?.members?.size ?: 1) <= 1) Text(
        "Noch niemand dabei. Andere im WLAN bekommen jetzt ein Pop-up „Jam beitreten“.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    OutlinedButton(
        onClick = { if (host) Jam.endJam() else Jam.leave() },
        modifier = Modifier.padding(16.dp)
    ) { Text(if (host) "Jam beenden" else "Jam verlassen", color = ORANGE) }
}

@Composable
private fun EntryCover(e: JamEntry, modifier: Modifier) {
    if (e.owner == Jam.myId) {
        val t = remember(e.trackId) { Library.tracks.firstOrNull { it.id == e.trackId } }
        Cover(listOfNotNull(t), e.title, modifier, sizePx = 120)
    } else RemoteCover(e.owner, e.trackId, e.title, modifier)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

// ---------- Bibliothek eines anderen ----------

private enum class LibTab(val label: String) { Songs("Lieder"), Albums("Alben"), Artists("Interpreten"), Playlists("Playlists") }

/** Gruppe in der fremden Bibliothek (Album, Interpret oder Playlist). */
private data class RemoteGroup(val key: String, val name: String, val sub: String, val tracks: List<RemoteTrack>, val playlist: RemotePlaylist? = null)

@Composable
private fun JamLibraryPage(vm: MusicViewModel, owner: String, onBack: () -> Unit) {
    val name = Jam.memberName(owner)
    var reload by remember { mutableStateOf(0) }
    val lib by produceState<RemoteLibrary?>(null, owner, reload) { value = Jam.library(owner, refresh = reload > 0) }
    val owned by produceState<Map<Long, Track>>(emptyMap(), lib, Jam.libraryTick, vm.tracks) { lib?.let { value = Jam.owned(it) } }
    var tab by rememberSaveable { mutableStateOf(LibTab.Songs) }
    var query by rememberSaveable { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<RemoteGroup?>(null) }
    val g = openGroup
    if (g != null) {
        BackHandler { openGroup = null }
        GroupPage(owner, g, owned) { openGroup = null }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Bibliothek von $name", onBack) { TextButton(onClick = { reload++ }) { Text("Neu laden") } }
        val l = lib
        if (l == null) {
            Text("Lade Bibliothek von $name …", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibTab.values().forEach { t -> LetterChip(t.label, tab == t, true) { tab = t } }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Suchen") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        val f = Names.fold(query.trim())
        fun hit(vararg s: String) = f.isEmpty() || s.any { Names.fold(it).contains(f) }
        val haveCount = owned.size
        Text(
            "${l.tracks.size} Lieder · $haveCount davon hast du schon",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        when (tab) {
            LibTab.Songs -> {
                val list = remember(l, f) { l.tracks.filter { hit(it.title, it.artist, it.albumName) }.sortedBy { Names.fold(it.title) } }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { t -> RemoteTrackRow(owner, t, owned[t.id] != null, onPlay = { Jam.playEntries(list.map { JamEntry.of(owner, it) }, list.indexOf(t)) }) }
                }
            }
            else -> {
                val groups = remember(l, tab, f, owned) {
                    when (tab) {
                        LibTab.Albums -> l.tracks.filter { it.albumName.isNotBlank() }.groupBy { it.artist + "\u0000" + it.albumName }.map { (k, ts) ->
                            RemoteGroup("al:$k", ts[0].albumName, "${ts[0].artist} · ${ts.size} Lieder · ${ts.count { owned[it.id] != null }} vorhanden", ts)
                        }
                        LibTab.Artists -> l.tracks.groupBy { it.artist }.map { (a, ts) ->
                            RemoteGroup("a:$a", a, "${ts.size} Lieder · ${ts.count { owned[it.id] != null }} vorhanden", ts.sortedBy { Names.fold(it.title) })
                        }
                        else -> l.playlists.map { p ->
                            val ts = p.trackIds.mapNotNull { l.byId[it] }
                            RemotePlaylist(p.id, if (p.favorites) "Lieblingssongs" else p.name, p.audio, p.favorites, p.trackIds).let { pl ->
                                RemoteGroup("p:${p.id}", pl.name, (if (p.audio) "Hörbuch/Podcast · " else "") + "${ts.size} Lieder · ${ts.count { owned[it.id] != null }} vorhanden", ts, p)
                            }
                        }
                    }.filter { gr -> hit(gr.name, gr.sub) }.sortedBy { Names.fold(it.name) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.key }) { gr ->
                        Row(Modifier.fillMaxWidth().clickable { openGroup = gr }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val first = gr.tracks.firstOrNull()
                            if (first != null) RemoteCover(owner, first.id, gr.name, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(gr.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(gr.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            val all = gr.tracks.isNotEmpty() && gr.tracks.all { owned[it.id] != null }
                            if (all) Icon(Icons.Filled.CheckCircle, "Alles vorhanden", tint = OWNED)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupPage(owner: String, g: RemoteGroup, owned: Map<Long, Track>, onBack: () -> Unit) {
    val missing = g.tracks.filter { owned[it.id] == null }
    val isList = g.playlist != null
    Column(Modifier.fillMaxSize()) {
        PageHeader(g.name, onBack)
        Text(g.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Jam.playEntries(g.tracks.map { JamEntry.of(owner, it) }, 0) }, enabled = g.tracks.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Im Jam abspielen")
            }
            OutlinedButton(onClick = { Jam.addEntries(g.tracks.map { JamEntry.of(owner, it) }) }, enabled = g.tracks.isNotEmpty()) { Icon(Icons.Filled.Add, "Einreihen") }
        }
        // Übernehmen: Playlist wird mit angelegt (vorhandene Lieder werden verknüpft, nicht doppelt geladen)
        OutlinedButton(
            onClick = { Jam.takeOver(owner, g.tracks, g.playlist) },
            enabled = missing.isNotEmpty() || isList,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp))
            Text(
                when {
                    isList && missing.isEmpty() -> "Playlist übernehmen (alle Lieder schon da)"
                    isList -> "Playlist übernehmen · ${missing.size} Lieder laden"
                    missing.isEmpty() -> "Alles schon vorhanden"
                    else -> "Übernehmen · ${missing.size} Lieder laden"
                }
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            // Index als Schlüssel: in Playlists kann ein Lied mehrfach vorkommen
            itemsIndexed(g.tracks, key = { i, _ -> i }) { i, t ->
                RemoteTrackRow(owner, t, owned[t.id] != null, onPlay = { Jam.playEntries(g.tracks.map { JamEntry.of(owner, it) }, i) })
            }
        }
    }
}

/** Zeile für ein Lied eines anderen Geräts: Abspielen im Jam, Einreihen, Übernehmen. Grüner Haken = hast du schon. */
@Composable
private fun RemoteTrackRow(owner: String, t: RemoteTrack, have: Boolean, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteCover(owner, t.id, t.title, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t.artist + (if (t.albumName.isNotBlank()) " · ${t.albumName}" else "") + " · " + formatDuration(t.durationMs),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { Jam.addEntries(listOf(JamEntry.of(owner, t))) }) { Icon(Icons.Filled.Add, "Zur Jam-Warteschlange") }
        if (have) Icon(Icons.Filled.CheckCircle, "Hast du schon", tint = OWNED, modifier = Modifier.padding(12.dp))
        else IconButton(onClick = { Jam.takeOver(owner, listOf(t)) }) { Icon(Icons.Filled.Download, "Übernehmen") }
    }
}
