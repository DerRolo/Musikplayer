package de.musikplayer.app

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

/**
 * Einheitliche Sortier-Auswahl: ein Knopf mit der aktuellen Sortierung (ohne das Wort „Sortierung“ – das Symbol
 * zeigt es), öffnet eine Liste und schließt sich nach der Wahl. Feste Breite, damit sich daneben nichts verschiebt.
 */
@Composable
fun SortMenu(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shown = label.removePrefix("Sortierung: ")
    Box {
        Row(
            Modifier.width(148.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SwapVert, "Sortierung", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            )
            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = { if (i == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) else Box(Modifier.size(24.dp)) },
                    onClick = { open = false; onPick(i) }
                )
            }
        }
    }
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
fun ListToolbar(count: String, sortLabel: String, sortOptions: List<String>, sortSelected: Int, onSort: (Int) -> Unit, icons: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(count, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        icons()
        SortMenu(sortLabel, sortOptions, sortSelected, onSort)
    }
}
