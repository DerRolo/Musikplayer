package de.musikplayer.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun formatMinutes(m: Int): String =
    if (m >= 60) "${m / 60} Std." + if (m % 60 != 0) " ${m % 60} Min." else "" else "$m Min."

private val HEART = Color(0xFFE5486B)

private fun trackOf(item: MediaItem?): Track? =
    item?.mediaId?.substringAfterLast(':')?.toLongOrNull()?.let { id -> Library.tracks.firstOrNull { it.id == id } }

@Composable
fun MiniPlayer(vm: MusicViewModel, onAddToPlaylist: (Track) -> Unit, onOpen: () -> Unit) {
    val item = vm.current ?: return
    val track = remember(item.mediaId) { trackOf(item) }
    val fav = track != null && vm.isFavorite(track.path)
    val title = item.mediaMetadata.title?.toString().orEmpty()
    val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(listOfNotNull(track), title, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), sizePx = 120)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        item.mediaMetadata.artist?.toString().orEmpty(),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
                if (track != null) {
                    IconButton(onClick = { vm.toggleFavorite(track.path) }, Modifier.size(36.dp)) {
                        Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Lieblingssong", tint = if (fav) HEART else LocalContentColor.current)
                    }
                    IconButton(onClick = { onAddToPlaylist(track) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Add, "Zu Playlist") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }, Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Shuffle, "Zufall", tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.6f))
                }
                IconButton(onClick = { vm.previous() }, Modifier.size(44.dp)) { Icon(Icons.Filled.SkipPrevious, "Zurück", Modifier.size(30.dp)) }
                IconButton(onClick = { vm.togglePlay() }, Modifier.size(48.dp)) {
                    Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Abspielen", Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.next() }, Modifier.size(44.dp)) { Icon(Icons.Filled.SkipNext, "Weiter", Modifier.size(30.dp)) }
                IconButton(onClick = { vm.cycleRepeat() }, Modifier.size(40.dp)) {
                    Icon(
                        if (vm.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Wiederholen",
                        tint = if (vm.repeat == RepeatMode.Off) LocalContentColor.current.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary
                    )
                }
            }
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}

/** Eine „Seite“ des Players: Platte, Titel, Interpret. Beim Wischen sind drei davon nebeneinander zu sehen. */
@Composable
private fun PlayerPage(item: MediaItem?, spinning: Boolean, modifier: Modifier) {
    val title = item?.mediaMetadata?.title?.toString() ?: "Nichts ausgewählt"
    val artist = item?.mediaMetadata?.artist?.toString().orEmpty()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Vinyl(spinning, Modifier.fillMaxWidth(0.72f))
        Spacer(Modifier.height(20.dp))
        Text(
            title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
        )
        Text(
            artist.ifBlank { " " }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
fun NowPlayingScreen(vm: MusicViewModel, actions: TrackActions, onAddToPlaylist: (Track) -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showFade by remember { mutableStateOf(false) }
    val sleepLeft by SleepTimer.sleepRemainingMs.collectAsState()
    val fadeLeft by SleepTimer.fadeRemainingMs.collectAsState()
    val volume by SleepTimer.volumePercent.collectAsState()
    val item = vm.current
    val track = remember(item?.mediaId) { trackOf(item) }
    val title = item?.mediaMetadata?.title?.toString() ?: "Nichts ausgewählt"
    val artist = item?.mediaMetadata?.artist?.toString().orEmpty()
    val fav = track != null && vm.isFavorite(track.path)

    // Regler: beim Ziehen nur lokal anzeigen, erst beim Loslassen springen; sonst in jedem Bild nachgeführt (ruckelfrei)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var smooth by remember { mutableFloatStateOf(0f) }
    val dur = vm.durationMs.coerceAtLeast(1L)
    LaunchedEffect(vm.isPlaying, dragging) {
        if (dragging) return@LaunchedEffect
        smooth = vm.livePositionMs().toFloat()
        if (vm.isPlaying) while (true) {
            withFrameNanos { }
            smooth = vm.livePositionMs().toFloat()
        }
    }
    LaunchedEffect(vm.positionMs) { if (!vm.isPlaying && !dragging) smooth = vm.positionMs.toFloat() }
    val shown = if (dragging) dragValue else smooth.coerceIn(0f, dur.toFloat())

    // Wischen wie in einem Pager: links = nächstes Lied, rechts = voriges Lied, Nachbar schiebt sich live herein
    val offset = remember { Animatable(0f) }
    var pageW by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    // Equalizer: Berechtigung „Audio aufnehmen“ nur für die Visualizer-Schnittstelle, einmalig mit Erklärung
    val ctx = LocalContext.current
    var micOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micOk = it }
    var showEqInfo by remember { mutableStateOf(!micOk && !Store.eqAsked) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Schließen") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSleep = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Icon(Icons.Filled.Bedtime, null, Modifier.size(18.dp))
                Text(
                    if (sleepLeft > 0) "  " + formatDuration(sleepLeft) else "  Sleep-Timer",
                    color = if (sleepLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { showFade = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Icon(Icons.Filled.VolumeDown, null, Modifier.size(18.dp))
                Text(
                    if (fadeLeft > 0) "  $volume % · " + formatDuration(fadeLeft) else "  Ausfaden",
                    color = if (fadeLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = track != null) { Icon(Icons.Filled.MoreVert, "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (track != null) {
                        MenuItem("Zu Playlist hinzufügen", Icons.Filled.PlaylistAdd) { menu = false; onAddToPlaylist(track) }
                        MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { menu = false; actions.onAddToQueue(track) }
                        MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { menu = false; actions.onPlayNext(track) }
                        MenuItem(
                            if (fav) "Aus Lieblingssongs entfernen" else "Zu Lieblingssongs",
                            if (fav) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite
                        ) { menu = false; actions.onToggleFavorite(track) }
                        MenuItem("Interpret zuordnen", Icons.Filled.Person) { menu = false; actions.onAssignArtist(track) }
                        MenuItem("Datei bearbeiten …", Icons.Filled.Edit, warn = true) { menu = false; actions.onRename(track) }
                        MenuItem("Sperren", Icons.Filled.Block) { menu = false; actions.onBlock(track) }
                        MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { menu = false; actions.onDelete(track) }
                    }
                }
            }
        }

        if (showQueue) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            QueueList(vm, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth()
                    .onSizeChanged { pageW = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val w = size.width.toFloat()
                                val o = offset.value
                                scope.launch {
                                    val goNext = o < -w * 0.22f && vm.nextItem != null
                                    val goPrev = o > w * 0.22f && vm.prevItem != null
                                    if (goNext || goPrev) {
                                        val before = vm.currentIndex to vm.current?.mediaId
                                        offset.animateTo(if (goNext) -w else w, tween(160))
                                        if (goNext) vm.next() else vm.previous()
                                        // warten, bis der Player wirklich weiter ist, dann nahtlos auf Mitte springen
                                        withTimeoutOrNull(700) { snapshotFlow { vm.currentIndex to vm.current?.mediaId }.first { it != before } }
                                        offset.snapTo(0f)
                                    } else offset.animateTo(0f, tween(180))
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                val o = offset.value
                                // am Ende der Liste (kein Nachbar) nur zäh mitziehen
                                val blocked = (dx + o < 0 && vm.nextItem == null) || (dx + o > 0 && vm.prevItem == null)
                                scope.launch { offset.snapTo(o + if (blocked) dx * 0.3f else dx) }
                            }
                        )
                    }
            ) {
                PlayerPage(item, vm.isPlaying, Modifier.fillMaxWidth().graphicsLayer { translationX = offset.value })
                if (offset.value > 0f) vm.prevItem?.let {
                    PlayerPage(it, false, Modifier.fillMaxWidth().graphicsLayer { translationX = offset.value - pageW })
                }
                if (offset.value < 0f) vm.nextItem?.let {
                    PlayerPage(it, false, Modifier.fillMaxWidth().graphicsLayer { translationX = offset.value + pageW })
                }
            }
            if (track != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.toggleFavorite(track.path) }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Lieblingssong",
                            tint = if (fav) HEART else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.size(24.dp))
                    IconButton(onClick = { onAddToPlaylist(track) }) {
                        Icon(Icons.Filled.Add, "Zu Playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.size(64.dp))
                    IconButton(onClick = { actions.onBlock(track) }) {
                        Icon(Icons.Filled.Block, "Sperren", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            EqBars(
                vm.isPlaying, micOk,
                Modifier.fillMaxWidth().height(40.dp).clickable(enabled = !micOk) { showEqInfo = true }
            )
            Spacer(Modifier.weight(1f))
        }

        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { val v = dragValue; vm.seekTo(v.toLong()); smooth = v; dragging = false },
            valueRange = 0f..dur.toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(vm.durationMs), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle() }, Modifier.size(44.dp)) {
                Icon(Icons.Filled.Shuffle, "Zufall", tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.previous() }, Modifier.size(52.dp)) { Icon(Icons.Filled.SkipPrevious, "Zurück", Modifier.size(34.dp)) }
            IconButton(onClick = { vm.togglePlay() }, Modifier.size(68.dp)) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Abspielen/Pause", Modifier.size(48.dp))
            }
            IconButton(onClick = { vm.next() }, Modifier.size(52.dp)) { Icon(Icons.Filled.SkipNext, "Weiter", Modifier.size(34.dp)) }
            IconButton(onClick = { vm.cycleRepeat() }, Modifier.size(44.dp)) {
                Icon(
                    if (vm.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Wiederholen",
                    tint = if (vm.repeat == RepeatMode.Off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { showQueue = !showQueue }, Modifier.size(44.dp)) {
                Icon(Icons.Filled.QueueMusic, "Warteschlange", tint = if (showQueue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showEqInfo) {
        AlertDialog(
            onDismissRequest = { Store.setEqAsked(ctx); showEqInfo = false },
            title = { Text("Echter Equalizer") },
            text = {
                Text(
                    "Android verlangt dafür die Berechtigung „Audio aufnehmen“. Der Equalizer hört aber nur auf die Musik, " +
                        "die diese App selbst abspielt – nicht auf das Mikrofon und nicht auf andere Apps. " +
                        "Es wird nichts aufgenommen oder gespeichert, die App hat nicht einmal Internetzugriff."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.setEqAsked(ctx); showEqInfo = false
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Erlauben") }
            },
            dismissButton = {
                TextButton(onClick = { Store.setEqAsked(ctx); showEqInfo = false }) { Text("Nein, nur Animation") }
            }
        )
    }
    if (showSleep) SleepDialog(vm, sleepLeft > 0) { showSleep = false }
    if (showFade) FadeDialog(vm, fadeLeft > 0) { showFade = false }
}

// ---------- Schallplatte ----------

/**
 * Drehende Schallplatte wie auf dem Foto: schwarzes Vinyl mit Rillen, ockerfarbenes Etikett, kleines Loch,
 * weiße Handschrift auf dem schwarzen Ring. Dreht nur, solange Musik läuft.
 */
@Composable
private fun Vinyl(spinning: Boolean, modifier: Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1_000_000_000f * 36f) % 360f // 36 Grad pro Sekunde
                last = now
            }
        }
    }
    val hole = MaterialTheme.colorScheme.background
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(235, 255, 255, 255)
            typeface = android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
        }
    }
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Platte
            drawCircle(Color(0xFF0B0C10), r, c)
            // Rillen: außen dicht, innen ein paar feine Ringe um das Etikett
            for (k in 0..26) drawCircle(Color.White.copy(alpha = if (k % 3 == 0) 0.075f else 0.04f), r * (0.72f + 0.0105f * k), c, style = Stroke(0.8.dp.toPx()))
            for (k in 0..5) drawCircle(Color.White.copy(alpha = 0.05f), r * (0.385f + 0.012f * k), c, style = Stroke(0.8.dp.toPx()))
            // Glanz (dreht mit, wirkt wie feine Struktur)
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                    c
                ), r, c
            )
            drawCircle(Color(0xFF2A2B30), r - 1.5.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            // Etikett
            drawCircle(Color(0xFFB97A3E), r * 0.36f, c)
            drawCircle(Color(0xFF9A6230), r * 0.36f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color(0xFFC98A4B), r * 0.27f, c, style = Stroke(1.dp.toPx()))
            drawCircle(hole, r * 0.03f, c)
            // Handschrift auf dem schwarzen Ring
            val textR = r * 0.56f
            val size1 = r * 0.15f
            paint.textSize = size1
            val rect = android.graphics.RectF(c.x - textR, c.y - textR, c.x + textR, c.y + textR)
            val arc = (Math.PI * textR).toFloat()
            drawIntoCanvas { cv ->
                val n = cv.nativeCanvas
                // oben: im Uhrzeigersinn, Buchstaben stehen nach außen
                val top = "If it's nice"
                val pTop = android.graphics.Path().apply { addArc(rect, 180f, 180f) }
                n.drawTextOnPath(top, pTop, (arc - paint.measureText(top)) / 2f, -size1 * 0.35f, paint)
                // unten: gegen den Uhrzeigersinn, damit die Schrift unten lesbar ist
                val bottom = "PLAY it twice!"
                val pBot = android.graphics.Path().apply { addArc(rect, 180f, -180f) }
                n.drawTextOnPath(bottom, pBot, (arc - paint.measureText(bottom)) / 2f, size1 * 0.9f, paint)
            }
        }
        // Lichtreflex steht still, während sich die Platte dreht
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val tl = Offset(size.width / 2f - r, size.height / 2f - r)
            val sz = Size(r * 2f, r * 2f)
            drawArc(Color.White.copy(alpha = 0.07f), -50f, 30f, true, tl, sz)
            drawArc(Color.White.copy(alpha = 0.07f), 130f, 30f, true, tl, sz)
        }
    }
}

// ---------- Equalizer-Balken ----------

/**
 * Mit Berechtigung ([live]) zeigen die Balken das echte Spektrum der abgespielten Musik (siehe [Spectrum]).
 * Ohne Berechtigung oder wenn das Gerät keine Daten liefert, läuft stattdessen eine Zier-Animation.
 * Die Balken werden in jedem Bild nachgeführt: schnell hoch, sanft runter – dadurch kein Ruckeln.
 */
@Composable
private fun EqBars(playing: Boolean, live: Boolean, modifier: Modifier) {
    val n = Spectrum.BANDS
    var liveOk by remember { mutableStateOf(false) }
    DisposableEffect(playing, live) {
        liveOk = live && playing && Spectrum.start()
        onDispose { Spectrum.stop() }
    }
    val levels = remember { FloatArray(n) { 0.04f } }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, liveOk) {
        val rnd = java.util.Random()
        val fakeTarget = FloatArray(n) { 0.04f }
        var last = withFrameNanos { it }
        var nextFake = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            if (playing && !liveOk && now >= nextFake) {
                for (i in 0 until n) {
                    val envelope = 1f - 0.55f * (i.toFloat() / n) // tiefe Töne links höher
                    fakeTarget[i] = (0.15f + rnd.nextFloat() * 0.85f) * envelope
                }
                nextFake = now + 110_000_000L
            }
            val real = Spectrum.bands.value
            val up = 1f - exp(-dt / 0.045f)
            val down = 1f - exp(-dt / 0.22f)
            var any = false
            for (i in 0 until n) {
                val target = when {
                    !playing -> 0.04f
                    liveOk -> real.getOrElse(i) { 0f }
                    else -> fakeTarget[i]
                }
                levels[i] += (target - levels[i]) * (if (target > levels[i]) up else down)
                if (levels[i] > 0.05f) any = true
            }
            frame++
            if (!playing && !any) break
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = frame // Neuzeichnen bei jedem Bild
        val slot = size.width / n
        val barW = slot * 0.62f
        for (i in 0 until n) {
            val h = (size.height * levels[i]).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color.copy(alpha = 0.85f),
                topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

// ---------- Warteschlange ----------

@Composable
private fun QueueList(vm: MusicViewModel, modifier: Modifier) {
    val start = (vm.currentIndex + 1).coerceAtMost(vm.queue.size)
    val queued = vm.queuedCount
    LazyColumn(modifier) {
        if (queued > 0) item(key = "hq") {
            Text("Deine Warteschlange", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp))
        }
        for (i in start until vm.queue.size) {
            val m = vm.queue[i]
            if (i == start + queued) item(key = "hn") {
                Text(
                    if (vm.shuffle) "Danach (zufällige Reihenfolge)" else "Danach", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                )
            }
            item(key = "$i:${m.mediaId}") {
                Row(Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            m.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.moveInQueue(i, i - 1) }, enabled = i > start) { Icon(Icons.Filled.ArrowUpward, "Nach oben") }
                    IconButton(onClick = { vm.moveInQueue(i, i + 1) }, enabled = i < vm.queue.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Nach unten") }
                    IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Filled.Close, "Entfernen") }
                }
            }
        }
    }
}

// ---------- Timer-Dialoge (getrennt) ----------

@Composable
private fun SleepDialog(vm: MusicViewModel, running: Boolean, onDismiss: () -> Unit) {
    var minutes by remember { mutableFloatStateOf(Store.sleepMinutes.toFloat().coerceIn(5f, 240f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep-Timer") },
        text = {
            Column {
                Text("Die Musik stoppt nach ${formatMinutes(minutes.toInt())}.", style = MaterialTheme.typography.titleSmall)
                Slider(minutes, { minutes = it }, valueRange = 5f..240f, steps = 46)
                Text(
                    "Die letzten ${SleepTimer.END_FADE_MS / 1000} Sekunden blendet sie automatisch aus, damit sie nicht abrupt endet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.startSleep(minutes.toInt()); onDismiss() }) { Text("Starten") } },
        dismissButton = {
            if (running) TextButton(onClick = { vm.cancelSleep(); onDismiss() }) { Text("Timer aus") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun FadeDialog(vm: MusicViewModel, running: Boolean, onDismiss: () -> Unit) {
    var minutes by remember { mutableFloatStateOf(Store.fadeMinutes.toFloat().coerceIn(5f, 480f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ausfaden") },
        text = {
            Column {
                Text("Lautstärke sinkt über ${formatMinutes(minutes.toInt())} gleichmäßig auf 0 %.", style = MaterialTheme.typography.titleSmall)
                Slider(minutes, { minutes = it }, valueRange = 5f..480f, steps = 94)
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    for (m in listOf(30, 60, 120, 240)) {
                        TextButton(onClick = { minutes = m.toFloat() }) { Text(formatMinutes(m)) }
                    }
                }
                Text(
                    "Gilt für alle Lieder nacheinander, nicht nur für das aktuelle. Bei 0 % stoppt die Musik. Die Zeit läuft nur, solange Musik spielt.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.startFade(minutes.toInt()); onDismiss() }) { Text("Starten") } },
        dismissButton = {
            if (running) TextButton(onClick = { vm.cancelFade(); onDismiss() }) { Text("Ausfaden aus") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
