package de.musikplayer.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Ganz oben nochmal nach unten ziehen = neu laden (Bibliothek neu einlesen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        LaunchedEffect(Unit) {
            onRefresh()
            delay(800)
            state.endRefresh()
        }
    }
    Box(modifier.nestedScroll(state.nestedScrollConnection)) {
        content()
        PullToRefreshContainer(state, Modifier.align(Alignment.TopCenter))
    }
}

/** Ansichten, überall gleich: Einzeln (Karten zum Blättern), Liste, Kacheln. */
val VIEW_SINGLE: ImageVector get() = Icons.Filled.ViewCarousel
val VIEW_LIST: ImageVector get() = Icons.Filled.ViewList
val VIEW_GRID: ImageVector get() = Icons.Filled.GridView

/** Umschalter oben rechts: ein Symbol pro Ansicht, das aktive ist hervorgehoben. */
@Composable
fun ViewToggle(icons: List<ImageVector>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEachIndexed { i, icon ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(i) }.padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null, Modifier.size(20.dp),
                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Menü eines Liedes (für Kacheln und Karten): alles, was man mit einem Lied machen kann. */
@Composable
fun TrackMenu(expanded: Boolean, onDismiss: () -> Unit, t: Track, actions: TrackActions) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem("Zu Playlist / Hörbuch", Icons.Filled.PlaylistAdd) { onDismiss(); actions.onAddToPlaylist(t) }
        MenuItem("Als Nächstes spielen", Icons.Filled.PlaylistPlay) { onDismiss(); actions.onPlayNext(t) }
        MenuItem("Zur Warteschlange", Icons.Filled.QueueMusic) { onDismiss(); actions.onAddToQueue(t) }
        MenuItem("Interpret zuordnen", Icons.Filled.Person) { onDismiss(); actions.onAssignArtist(t) }
        MenuItem("Cover ändern …", Icons.Filled.Image) { onDismiss(); actions.onCover(t) }
        MenuItem("Datei bearbeiten …", Icons.Filled.Edit, warn = true) { onDismiss(); actions.onRename(t) }
        MenuItem("Datei im Ordner zeigen", Icons.Filled.FolderOpen) { onDismiss(); actions.onShowFile(t) }
        MenuItem("Sperren", Icons.Filled.Block, danger = true) { onDismiss(); actions.onBlock(t) }
        MenuItem("Löschen …", Icons.Filled.Delete, danger = true) { onDismiss(); actions.onDelete(t) }
    }
}
