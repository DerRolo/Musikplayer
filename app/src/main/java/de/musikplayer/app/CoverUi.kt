@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.musikplayer.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Treffer der Cover-Suche. [kind]: 0 Album/Lied-Cover, 1 Interpreten-Foto. [mayFail]: Bild existiert evtl. nicht (wird dann ausgeblendet). */
data class WebCover(val thumb: String, val full: String, val label: String, val kind: Int = 0, val mayFail: Boolean = false)

private fun httpGet(url: String): ByteArray? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 12000
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "Musikplayer/1.0 (Android; https://github.com/DerRolo/Musikplayer)")
    try { if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
} catch (e: Exception) { null }

private fun jsonArr(bytes: ByteArray?, key: String) = try { bytes?.let { JSONObject(String(it)).optJSONArray(key) } } catch (_: Exception) { null }

/**
 * Kostenlose Cover-Suche ohne Anmeldung, aus mehreren Quellen gleichzeitig:
 * iTunes (Alben + Lieder), Deezer (Alben, Lieder, Interpreten-Fotos), MusicBrainz/Cover Art Archive (auch seltene Veröffentlichungen).
 * Doppelte Bilder fallen weg; am besten passende Treffer stehen vorn.
 */
suspend fun searchCovers(query: String): List<WebCover> = withContext(Dispatchers.IO) {
    val q = URLEncoder.encode(query, "UTF-8")
    val jobs = listOf(
        async { // iTunes Alben
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://itunes.apple.com/search?term=$q&entity=album&limit=200&country=DE"), "results")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val a = o.optString("artworkUrl100")
                    if (a.isNotEmpty()) out.add(WebCover(a, a.replace("100x100bb", "1000x1000bb"), o.optString("artistName") + " – " + o.optString("collectionName"))) }
            }
            out
        },
        async { // Deezer Alben
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://api.deezer.com/search/album?q=$q&limit=100"), "data")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i)
                    val thumb = o.optString("cover_medium"); val full = o.optString("cover_xl").ifEmpty { o.optString("cover_big") }
                    if (thumb.isNotEmpty() && full.isNotEmpty()) out.add(WebCover(thumb, full, o.optJSONObject("artist")?.optString("name").orEmpty() + " – " + o.optString("title"))) }
            }
            out
        },
        async { // iTunes Lieder (findet Alben auch über den Liedtitel)
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://itunes.apple.com/search?term=$q&entity=song&limit=200&country=DE"), "results")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val a = o.optString("artworkUrl100")
                    if (a.isNotEmpty()) out.add(WebCover(a, a.replace("100x100bb", "1000x1000bb"), o.optString("artistName") + " – " + o.optString("trackName"))) }
            }
            out
        },
        async { // Deezer Lieder
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://api.deezer.com/search?q=$q&limit=100"), "data")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val al = o.optJSONObject("album") ?: continue
                    val thumb = al.optString("cover_medium"); val full = al.optString("cover_xl").ifEmpty { al.optString("cover_big") }
                    if (thumb.isNotEmpty() && full.isNotEmpty()) out.add(WebCover(thumb, full, o.optJSONObject("artist")?.optString("name").orEmpty() + " – " + o.optString("title"))) }
            }
            out
        },
        async { // MusicBrainz + Cover Art Archive
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://musicbrainz.org/ws/2/release-group/?query=$q&fmt=json&limit=60"), "release-groups")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val id = o.optString("id"); if (id.isEmpty()) continue
                    val artist = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
                    out.add(WebCover("https://coverartarchive.org/release-group/$id/front-250", "https://coverartarchive.org/release-group/$id/front-1200",
                        "$artist – " + o.optString("title"), mayFail = true)) }
            }
            out
        },
        async { // Deezer Interpreten-Fotos
            val out = ArrayList<WebCover>()
            jsonArr(httpGet("https://api.deezer.com/search/artist?q=$q&limit=40"), "data")?.let { arr ->
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i)
                    val thumb = o.optString("picture_medium"); val full = o.optString("picture_xl").ifEmpty { o.optString("picture_big") }
                    // Interpreten ohne Foto haben eine leere Bild-Kennung („/artist//“)
                    if (thumb.isNotEmpty() && full.isNotEmpty() && "/artist//" !in full) out.add(WebCover(thumb, full, o.optString("name"), kind = 1)) }
            }
            out
        }
    )
    val all = jobs.awaitAll().flatten()
    val seen = HashSet<String>()
    val unique = all.filter { seen.add(it.full.substringBefore('?')) }
    // Treffer, deren Beschriftung mehr Suchwörter enthält, nach vorn (Reihenfolge der Quellen bleibt sonst erhalten)
    val words = Names.fold(query).split(' ', '-', '–').filter { it.length > 1 }
    if (words.isEmpty()) unique
    else unique.sortedByDescending { c -> val l = Names.fold(c.label); words.count { it in l } }
}

private val thumbCache = object : android.util.LruCache<String, ImageBitmap>(160) {}

/** Vorschaubild aus dem Web. [onFail] wird gerufen, wenn es das Bild nicht gibt. */
@Composable
private fun WebThumb(url: String, modifier: Modifier, onFail: (() -> Unit)? = null) {
    val bmp by produceState<ImageBitmap?>(thumbCache.get(url), url) {
        if (value != null) return@produceState
        val b = withContext(Dispatchers.IO) {
            httpGet(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
        if (b != null) { thumbCache.put(url, b); value = b } else onFail?.invoke()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}


@Composable
fun EditCoverPreview(track: Track, uri: android.net.Uri? = null, url: String? = null, resetPending: Boolean = false, modifier: Modifier = Modifier) {
    if (resetPending) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("♪", style = MaterialTheme.typography.titleLarge) }
    } else if (url != null) {
        WebThumb(url, modifier)
    } else if (uri != null) {
        val ctx = LocalContext.current
        val bmp by produceState<ImageBitmap?>(null, uri) {
            value = withContext(Dispatchers.IO) { try { ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (_: Exception) { null } }
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
    } else {
        Cover(listOf(track), track.title, modifier)
    }
}

private const val COVER_PAGE = 9 // 3 × 3 pro Seite

/**
 * „Cover ändern“: eigenes Bild aus der Galerie oder Websuche (Vorschlag aus Interpret + Titel/Album vorausgefüllt).
 * Treffer aus mehreren Quellen, seitenweise (wischen oder ‹ ›), mit Filter und Vorschlägen für andere Suchbegriffe.
 * Genau eines von [onUri]/[onUrl] wird aufgerufen; [onReset] nur wenn ein eigenes Cover gesetzt ist.
 */
@Composable
fun CoverPickerDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onUri: (android.net.Uri) -> Unit,
    onUrl: (String) -> Unit,
    onReset: (() -> Unit)? = null,
    suggestions: List<String> = emptyList()
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableIntStateOf(0) } // 0 Alle, 1 Cover, 2 Interpreten-Fotos
    val results = remember { mutableStateListOf<WebCover>() }
    // Sucht schon beim Tippen (kurze Pause nach der letzten Eingabe)
    LaunchedEffect(query) {
        loading = true
        kotlinx.coroutines.delay(450)
        val q = query.trim()
        val r = if (q.isBlank()) emptyList() else searchCovers(q)
        results.clear(); results.addAll(r)
        loading = false
    }
    val shown = results.filter { filter == 0 || (filter == 1 && it.kind == 0) || (filter == 2 && it.kind == 1) }
    val pages = maxOf(1, (shown.size + COVER_PAGE - 1) / COVER_PAGE)
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, filter) { if (pager.currentPage != 0) pager.scrollToPage(0) }
    val chips = remember(initialQuery, suggestions) { (listOf(initialQuery) + suggestions).map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { onUri(uri); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        title = { Text("Cover ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Filled.Image, null, Modifier.size(18.dp)); Text("  Aus meinen Bildern")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        Downloads.open(ctx, "https://www.google.com/search?tbm=isch&q=" + android.net.Uri.encode(query.trim() + " cover"))
                    }) { Text("Im Browser suchen") }
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Im Web suchen") },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Leeren") }
                        else Icon(Icons.Filled.Search, null)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )
                // Andere Suchbegriffe mit einem Tipp (z. B. nur Interpret oder Interpret + Album)
                if (chips.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in chips) FilterChip(selected = query.trim() == c, onClick = { query = c }, label = { Text(c, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Alle", "Cover", "Interpreten-Fotos").forEachIndexed { i, l ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(390.dp)) {
                    when {
                        loading && results.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        shown.isEmpty() -> Text(
                            "Nichts gefunden (Internet an?). Anderen Suchbegriff probieren – oft hilft nur der Interpret oder nur das Album.",
                            Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> HorizontalPager(pager, Modifier.fillMaxSize(), beyondBoundsPageCount = 1) { page ->
                            val items = shown.drop(page * COVER_PAGE).take(COVER_PAGE)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (row in items.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (r in row) Column(Modifier.weight(1f)) {
                                        WebThumb(
                                            r.thumb,
                                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onUrl(r.full); onDismiss() },
                                            onFail = if (r.mayFail) ({ results.remove(r) }) else null
                                        )
                                        Text(r.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                if (shown.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                        Icon(Icons.Filled.ChevronLeft, "Vorherige Seite")
                    }
                    Text(
                        "Seite ${pager.currentPage + 1} von $pages · ${shown.size} Treffer" + if (loading) " …" else "",
                        Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < pages - 1) {
                        Icon(Icons.Filled.ChevronRight, "Nächste Seite")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        dismissButton = onReset?.let { r -> { TextButton(onClick = { r(); onDismiss() }) { Text("Zurücksetzen") } } }
    )
}
