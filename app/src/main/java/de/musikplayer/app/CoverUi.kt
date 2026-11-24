package de.musikplayer.app

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WebCover(val thumb: String, val full: String, val label: String)

private fun httpGet(url: String): ByteArray? = try {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 12000
    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Musikplayer")
    try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
} catch (e: Exception) { null }

/** Kostenlose Cover-Suche ohne Anmeldung: iTunes und Deezer. Liefert Vorschaubild und große Version. */
suspend fun searchCovers(query: String): List<WebCover> = withContext(Dispatchers.IO) {
    val q = URLEncoder.encode(query, "UTF-8")
    val out = ArrayList<WebCover>()
    httpGet("https://itunes.apple.com/search?term=$q&entity=album&limit=25")?.let {
        try {
            val arr = JSONObject(String(it)).optJSONArray("results")
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.optString("artworkUrl100")
                if (a.isNotEmpty()) out.add(WebCover(a, a.replace("100x100bb", "1000x1000bb"), o.optString("artistName") + " – " + o.optString("collectionName")))
            }
        } catch (e: Exception) { }
    }
    httpGet("https://api.deezer.com/search/album?q=$q&limit=25")?.let {
        try {
            val arr = JSONObject(String(it)).optJSONArray("data")
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val thumb = o.optString("cover_medium")
                val full = o.optString("cover_xl").ifEmpty { o.optString("cover_big") }
                val artist = o.optJSONObject("artist")?.optString("name").orEmpty()
                if (thumb.isNotEmpty() && full.isNotEmpty()) out.add(WebCover(thumb, full, artist + " – " + o.optString("title")))
            }
        } catch (e: Exception) { }
    }
    out
}

@Composable
private fun WebThumb(url: String, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            httpGet(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/**
 * „Cover ändern“: eigenes Bild aus der Galerie oder Websuche (Vorschlag aus Interpret + Titel/Album vorausgefüllt).
 * Genau eines von [onUri]/[onUrl] wird aufgerufen; [onReset] nur wenn ein eigenes Cover gesetzt ist.
 */
@Composable
fun CoverPickerDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onUri: (android.net.Uri) -> Unit,
    onUrl: (String) -> Unit,
    onReset: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf(initialQuery) }
    var submitted by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    val results by produceState<List<WebCover>>(emptyList(), submitted) {
        loading = true
        value = if (submitted.isBlank()) emptyList() else searchCovers(submitted)
        loading = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { onUri(uri); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cover ändern") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { picker.launch("image/*") }) {
                    Icon(Icons.Filled.Image, null, Modifier.size(18.dp)); Text("  Aus meinen Bildern wählen")
                }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Im Web suchen") },
                    trailingIcon = { IconButton(onClick = { submitted = query.trim() }) { Icon(Icons.Filled.Search, "Suchen") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() })
                )
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp)) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        results.isEmpty() -> Text(
                            "Nichts gefunden (Internet an?). Anderen Suchbegriff probieren.",
                            Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyVerticalGrid(
                            GridCells.Fixed(3), Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(results) { r ->
                                WebThumb(r.thumb, Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onUrl(r.full); onDismiss() })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        dismissButton = onReset?.let { r -> { TextButton(onClick = { r(); onDismiss() }) { Text("Zurücksetzen") } } }
    )
}
