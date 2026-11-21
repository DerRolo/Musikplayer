package de.musikplayer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val sleepLeft by SleepTimer.remainingMs.collectAsState()
    val item = vm.current

    // Beim Ziehen am Regler nur lokal anzeigen, erst beim Loslassen springen
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = vm.durationMs.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else vm.positionMs.toFloat().coerceIn(0f, dur.toFloat())

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
        }
        Spacer(Modifier.weight(1f))
        Text(
            item?.mediaMetadata?.title?.toString() ?: "Nichts ausgewählt",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            item?.mediaMetadata?.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showQueue = !showQueue }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (showQueue) "Warteschlange ausblenden" else "Warteschlange (${vm.queue.size})")
        }
        if (showQueue) QueueList(vm, Modifier.weight(2f))
    }

    if (showSleep) SleepDialog(vm, sleepLeft > 0) { showSleep = false }
}

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

@Composable
private fun SleepDialog(vm: MusicViewModel, running: Boolean, onDismiss: () -> Unit) {
    var minutes by remember { mutableFloatStateOf(Store.sleepMinutes.toFloat().coerceIn(5f, 120f)) }
    var fade by remember { mutableFloatStateOf(Store.fadeSeconds.toFloat().coerceIn(0f, 300f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep-Timer") },
        text = {
            Column {
                Text("Stoppt nach ${minutes.toInt()} Min.")
                Slider(minutes, { minutes = it }, valueRange = 5f..120f, steps = 22)
                Spacer(Modifier.height(8.dp))
                Text(if (fade.toInt() == 0) "Kein Ausblenden" else "Langsam ausblenden in den letzten ${fade.toInt()} Sek.")
                Slider(fade, { fade = it }, valueRange = 0f..300f, steps = 29)
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.startSleep(minutes.toInt(), fade.toInt()); onDismiss() }) { Text("Starten") }
        },
        dismissButton = {
            if (running) TextButton(onClick = { vm.cancelSleep(); onDismiss() }) { Text("Timer aus") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
