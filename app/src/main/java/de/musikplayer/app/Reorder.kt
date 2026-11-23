package de.musikplayer.app

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Umsortieren per Finger in einer LazyColumn: gedrückt halten, hoch/runter schieben.
 * Die Liste darf keine anderen Einträge (Kopfzeilen) vor den Liedern haben; Schlüssel müssen stabil sein.
 */
class ReorderState(val listState: LazyListState, private val onSwap: (from: Int, to: Int) -> Unit) {
    /** Aktuelle Position des gezogenen Eintrags, -1 = es wird nichts gezogen. */
    var dragging by mutableIntStateOf(-1)
        private set
    /** Versatz des gezogenen Eintrags gegenüber seinem Platz in der Liste (Pixel). */
    var delta by mutableFloatStateOf(0f)
    private var startIndex = -1
    private var locked = false

    fun unlock() { locked = false }

    fun start(index: Int) {
        dragging = index
        startIndex = index
        delta = 0f
        locked = false
    }

    fun drag(dy: Float) {
        if (dragging < 0) return
        delta += dy
        if (locked) return // erst nach dem nächsten Layout wieder tauschen
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.index == dragging } ?: return
        val center = cur.offset + delta + cur.size / 2f
        val target = infos.firstOrNull { it.index != dragging && center >= it.offset && center < it.offset + it.size } ?: return
        onSwap(dragging, target.index)
        val newTop = if (target.index > dragging) cur.offset + target.size else target.offset
        delta += (cur.offset - newTop) // sichtbare Position bleibt unter dem Finger
        dragging = target.index
        locked = true
    }

    /** Loslassen: sanft einrasten, dann [onDrop] (Start- und Endposition) aufrufen. */
    fun end(scope: CoroutineScope, onDrop: (from: Int, to: Int) -> Unit) {
        val from = startIndex
        val to = dragging
        if (to < 0) return
        scope.launch {
            animate(delta, 0f, animationSpec = tween(140)) { v, _ -> delta = v }
            dragging = -1
            if (from != to) onDrop(from, to)
        }
    }
}

/** Entsperrt das Tauschen nach jedem Layout und scrollt automatisch, wenn der gezogene Eintrag an den Rand kommt. */
@Composable
fun ReorderEffects(state: ReorderState) {
    LaunchedEffect(state) {
        snapshotFlow { state.listState.layoutInfo.visibleItemsInfo.map { it.key } }.collect { state.unlock() }
    }
    LaunchedEffect(state.dragging >= 0) {
        while (state.dragging >= 0) {
            val info = state.listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.index == state.dragging }
            if (cur != null) {
                val top = cur.offset + state.delta
                val bottom = top + cur.size
                val edge = 140f
                val amount = when {
                    top < info.viewportStartOffset + edge -> -(((info.viewportStartOffset + edge - top) / edge) * 26f).coerceAtMost(26f)
                    bottom > info.viewportEndOffset - edge -> (((bottom - (info.viewportEndOffset - edge)) / edge) * 26f).coerceAtMost(26f)
                    else -> 0f
                }
                if (amount != 0f) {
                    val used = state.listState.scrollBy(amount)
                    state.delta += used
                }
            }
            withFrameNanos { }
        }
    }
}
