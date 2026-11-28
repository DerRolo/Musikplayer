package de.musikplayer.app

import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Schnelle Scrollleiste: erscheint beim Scrollen am rechten Rand, am Griff ziehen springt durch die ganze Liste. */
@Composable
private fun ScrollbarCore(total: Int, first: Int, visible: Int, scrollTo: (Int) -> Unit, modifier: Modifier) {
    if (total <= visible + 1 || total < 12) return
    var dragging by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(first, dragging) {
        if (first == 0 && !dragging) return@LaunchedEffect
        show = true
        if (!dragging) { delay(1400); show = false }
    }
    if (!show) return
    val totalNow by rememberUpdatedState(total)
    val visibleNow by rememberUpdatedState(visible)
    val firstNow by rememberUpdatedState(first)
    val scrollNow by rememberUpdatedState(scrollTo)
    BoxWithConstraints(modifier.fillMaxHeight().width(30.dp)) {
        val thumbH = 64.dp
        val trackPx = with(androidx.compose.ui.platform.LocalDensity.current) { (maxHeight - thumbH).toPx() }
        val range = (total - visible).coerceAtLeast(1)
        val frac = if (dragging) dragFrac else (first.toFloat() / range).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (frac * trackPx).roundToInt()) }
                .width(30.dp).height(thumbH)
                .pointerInput(trackPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFrac = (firstNow.toFloat() / (totalNow - visibleNow).coerceAtLeast(1)).coerceIn(0f, 1f)
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, dy ->
                        change.consume()
                        if (trackPx > 0f) dragFrac = (dragFrac + dy / trackPx).coerceIn(0f, 1f)
                        scrollNow((dragFrac * (totalNow - 1)).roundToInt())
                    }
                }
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 3.dp)
                    .width(if (dragging) 10.dp else 6.dp).height(thumbH - 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.95f else 0.7f))
            )
        }
    }
}

@Composable
fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ScrollbarCore(
        state.layoutInfo.totalItemsCount, state.firstVisibleItemIndex, state.layoutInfo.visibleItemsInfo.size,
        { i -> scope.launch { state.scrollToItem(i) } }, modifier
    )
}

@Composable
fun GridScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    ScrollbarCore(
        state.layoutInfo.totalItemsCount, state.firstVisibleItemIndex, state.layoutInfo.visibleItemsInfo.size,
        { i -> scope.launch { state.scrollToItem(i) } }, modifier
    )
}

/** Eine Sortier-Möglichkeit mit Symbol. */
data class SortOpt(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Passendes Symbol zu einer Sortier-Beschriftung (für ältere Aufrufe, die nur Texte liefern). */
fun sortIcon(label: String): androidx.compose.ui.graphics.vector.ImageVector {
    val l = label.lowercase()
    return when {
        "meistgehört" in l || "wiedergabe" in l -> Icons.Filled.Headphones
        "hinzugefügt" in l || "datum" in l || "neueste" in l || "älteste" in l -> Icons.Filled.CalendarMonth
        "länge" in l || "dauer" in l -> Icons.Filled.Timer
        "interpret" in l -> Icons.Filled.Person
        "album" in l -> Icons.Filled.Album
        "titel" in l || "name" in l || "a–z" in l || "z–a" in l -> Icons.Filled.SortByAlpha
        "anzahl" in l || "meiste" in l || "größe" in l -> Icons.Filled.FormatListNumbered
        "ordner" in l -> Icons.Filled.Folder
        "eigene" in l || "reihenfolge" in l -> Icons.Filled.DragHandle
        else -> Icons.Filled.SwapVert
    }
}

/**
 * Einheitlicher Sortier-Knopf: nur ⇅ mit Aufklapp-Pfeil (spart Platz). Aufgeklappt: jede Möglichkeit mit Symbol,
 * die gewählte mit Richtung (↑/↓). Antippen wählt bzw. dreht die Richtung um – das Menü BLEIBT offen, damit man schnell
 * wechseln kann, und schließt sich erst beim Tippen daneben.
 * [direction]: Richtung der gewählten Sortierung (true = absteigend ↓, false = aufsteigend ↑, null = keine Richtung).
 */
@Composable
fun SortMenu(options: List<SortOpt>, selected: Int, direction: Boolean?, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SwapVert, "Sortierung: " + (options.getOrNull(selected)?.label ?: ""), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(o.label, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (on && direction != null) Text(if (direction) "  ↓" else "  ↑", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    leadingIcon = { Icon(o.icon, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) else Box(Modifier.size(24.dp)) },
                    onClick = { onPick(i) }
                )
            }
        }
    }
}

/** Ältere Form (nur Texte, ohne Richtung): bekommt automatisch Symbole und dasselbe Verhalten. */
@Composable
fun SortMenu(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    SortMenu(options.map { SortOpt(it, sortIcon(it)) }, selected, null, onPick)
}

/** Album-Kachel: Cover (falls Bilder in den Liedern stecken), Name und Anzahl. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun AlbumTile(
    name: String,
    tracks: List<Track>,
    actions: GroupActions? = null,
    playingNow: Boolean = false,
    onShuffle: () -> Unit,
    onClick: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.width(132.dp).combinedClickable(onClick = onClick, onLongClick = { if (actions != null) menu = true }).padding(4.dp)) {
        Box {
            Cover(tracks, name, Modifier.size(124.dp).clip(RoundedCornerShape(10.dp)))
            PlayOverlay(playingNow, Modifier.align(Alignment.BottomEnd).padding(7.dp), 34.dp, onShuffle)
            if (actions != null) GroupMenu(menu, { menu = false }, actions)
        }
        Text(name, Modifier.padding(top = 4.dp).clickable(onClick = onClick), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text("${tracks.size} Lieder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Feste Werkzeugzeile über jeder Liste (Bibliothek, Interpreten, Alben, Playlists, Hörbücher, Jam):
 * links die Anzahl, rechts Filter/Abspielen/Ansicht und ganz rechts die Sortierung. Liegt NICHT in der Liste,
 * damit sie beim Scrollen, Filtern und Wechsel Kachel/Liste immer an derselben Stelle bleibt.
 */
@Composable
fun ListToolbar(count: String, sort: @Composable () -> Unit, icons: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(count, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        icons()
        sort()
    }
}

/**
 * Gewählte Sortierung mit Richtung, wird dauerhaft gemerkt. Antippen der gewählten Möglichkeit dreht die Richtung um,
 * eine andere Möglichkeit startet mit ihrer natürlichen Richtung ([defaults]: true = absteigend, z. B. Meistgehört).
 */
class SortChoice(private val save: (Int, Boolean) -> Unit, private val defaults: List<Boolean>, idx: Int, desc: Boolean) {
    var index by androidx.compose.runtime.mutableIntStateOf(idx)
        private set
    var desc by mutableStateOf(desc)
        private set
    fun pick(i: Int) {
        if (i == index) desc = !desc else { index = i; desc = defaults.getOrElse(i) { false } }
        save(index, desc)
    }
    /** Liste in der gewählten Richtung: [ascending] ist die aufsteigende Reihenfolge. */
    fun <T> apply(ascending: List<T>): List<T> = if (desc) ascending.asReversed() else ascending
}

/** Gespeicherte Sortierung laden (ohne Compose, z. B. für mehrere Reiter auf einmal). */
fun sortChoiceFor(vm: MusicViewModel, key: String, defaults: List<Boolean>, defaultIndex: Int = 0): SortChoice {
    val i = Store.sortPref(key, defaultIndex).coerceIn(0, defaults.lastIndex)
    val d = Store.sortPref("$key.d", if (defaults[i]) 1 else 0) == 1
    return SortChoice({ idx, desc -> vm.saveSort(key, idx); vm.saveSort("$key.d", if (desc) 1 else 0) }, defaults, i, d)
}

@Composable
fun rememberSortChoice(vm: MusicViewModel, key: String, defaults: List<Boolean>, defaultIndex: Int = 0): SortChoice =
    remember(key) { sortChoiceFor(vm, key, defaults, defaultIndex) }

/** Sortier-Knopf für eine [SortChoice]. */
@Composable
fun SortMenu(options: List<SortOpt>, choice: SortChoice) = SortMenu(options, choice.index, choice.desc) { choice.pick(it) }
