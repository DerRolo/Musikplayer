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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun formatMinutes(m: Int): String =
    if (m >= 60) "${m / 60} Std." + if (m % 60 != 0) " ${m % 60} Min." else "" else "$m Min."

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val item = vm.current ?: return
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.mediaMetadata.artist?.toString().orEmpty(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.previous() }) { Icon(Icons.Filled.SkipPrevious, "Zurück") }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Abspielen")
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Filled.SkipNext, "Weiter") }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showFade by remember { mutableStateOf(false) }
    val sleepLeft by SleepTimer.sleepRemainingMs.collectAsState()
    val fadeLeft by SleepTimer.fadeRemainingMs.collectAsState()
    val volume by SleepTimer.volumePercent.collectAsState()
    val item = vm.current
    val track = remember(item?.mediaId) {
        item?.mediaId?.substringAfterLast(':')?.toLongOrNull()?.let { id -> Library.tracks.firstOrNull { it.id == id } }
    }
    val title = item?.mediaMetadata?.title?.toString() ?: "Nichts ausgewählt"
    val artist = item?.mediaMetadata?.artist?.toString().orEmpty()

    // Beim Ziehen am Regler nur lokal anzeigen, erst beim Loslassen springen
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = vm.durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else vm.positionMs.toFloat().coerceIn(0f, dur.toFloat())

    // Wischen: links = nächstes Lied, rechts = voriges Lied
    val offset = remember { Animatable(0f) }
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
            TextButton(onClick = { showSleep = true }) {
                Icon(Icons.Filled.Bedtime, null, Modifier.size(18.dp))
                Text(
                    if (sleepLeft > 0) "  " + formatDuration(sleepLeft) else "  Sleep-Timer",
                    color = if (sleepLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = { showFade = true }) {
                Icon(Icons.Filled.VolumeDown, null, Modifier.size(18.dp))
                Text(
                    if (fadeLeft > 0) "  $volume % · " + formatDuration(fadeLeft) else "  Ausfaden",
                    color = if (fadeLeft > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showQueue) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            QueueList(vm, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { translationX = offset.value }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val w = size.width.toFloat()
                                val o = offset.value
                                scope.launch {
                                    if (o < -w * 0.22f) {
                                        offset.animateTo(-w, tween(140)); vm.next()
                                        offset.snapTo(w); offset.animateTo(0f, tween(200))
                                    } else if (o > w * 0.22f) {
                                        offset.animateTo(w, tween(140)); vm.previous()
                                        offset.snapTo(-w); offset.animateTo(0f, tween(200))
                                    } else offset.animateTo(0f, tween(160))
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, tween(160)) } },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + dx) }
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Vinyl(track, title, vm.isPlaying, Modifier.fillMaxWidth(0.72f))
                Spacer(Modifier.height(20.dp))
                Text(
                    title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            EqBars(
                vm.isPlaying, micOk,
                Modifier.fillMaxWidth().height(40.dp).clickable(enabled = !micOk) { showEqInfo = true }
            )
            Spacer(Modifier.weight(1f))
        }

        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..dur.toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown.toLong()), style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(vm.durationMs), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, "Zufall", tint = if (vm.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.previous() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Zurück", Modifier.size(36.dp)) }
            IconButton(onClick = { vm.togglePlay() }, Modifier.size(72.dp)) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Abspielen/Pause", Modifier.size(52.dp))
            }
            IconButton(onClick = { vm.next() }, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Weiter", Modifier.size(36.dp)) }
            IconButton(onClick = { vm.cycleRepeat() }) {
                Icon(
                    if (vm.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Wiederholen",
                    tint = if (vm.repeat == RepeatMode.Off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
        TextButton(onClick = { showQueue = !showQueue }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (showQueue) "Zurück zum Player" else "Warteschlange (${vm.queue.size})")
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

/** Drehende Schallplatte (dreht nur, solange Musik läuft) mit dem Cover als Etikett. */
@Composable
private fun Vinyl(track: Track?, label: String, spinning: Boolean, modifier: Modifier) {
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
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF111111), r)
                for (k in 0..9) {
                    drawCircle(Color.White.copy(alpha = 0.06f), r * (0.46f + 0.052f * k), style = Stroke(1.dp.toPx()))
                }
                drawCircle(Color(0xFF2B2B2B), r - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
            }
            Cover(
                if (track != null) listOf(track) else emptyList(), label,
                Modifier.fillMaxSize(0.38f).clip(CircleShape), sizePx = 300
            )
            Canvas(Modifier.size(10.dp)) { drawCircle(hole) }
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
 */
@Composable
private fun EqBars(playing: Boolean, live: Boolean, modifier: Modifier) {
    val n = Spectrum.BANDS
    var liveOk by remember { mutableStateOf(false) }
    DisposableEffect(playing, live) {
        liveOk = live && playing && Spectrum.start()
        onDispose { Spectrum.stop() }
    }
    val real by Spectrum.bands.collectAsState()

    val heights = remember { mutableStateListOf<Float>().apply { repeat(n) { add(0.04f) } } }
    val fakeActive = playing && !liveOk
    LaunchedEffect(fakeActive) {
        val rnd = java.util.Random()
        while (fakeActive || heights.any { it > 0.05f }) {
            for (i in 0 until n) {
                val envelope = 1f - 0.55f * (i.toFloat() / n) // tiefe Töne links höher
                val target = if (fakeActive) (0.15f + rnd.nextFloat() * 0.85f) * envelope else 0.04f
                heights[i] = heights[i] + (target - heights[i]) * (if (fakeActive) 0.55f else 0.25f)
            }
            delay(80)
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val slot = size.width / n
        val barW = slot * 0.62f
        for (i in 0 until n) {
            val level = if (liveOk) real.getOrElse(i) { 0f } else heights[i]
            val h = (size.height * level).coerceAtLeast(3.dp.toPx())
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
    LazyColumn(modifier) {
        itemsIndexed(vm.queue, key = { i, m -> "$i:${m.mediaId}" }) { i, m ->
            val active = i == vm.currentIndex
            Row(Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.mediaMetadata.title?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        m.mediaMetadata.artist?.toString().orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.moveInQueue(i, i - 1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, "Nach oben") }
                IconButton(onClick = { vm.moveInQueue(i, i + 1) }, enabled = i < vm.queue.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Nach unten") }
                IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Filled.Close, "Entfernen") }
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
