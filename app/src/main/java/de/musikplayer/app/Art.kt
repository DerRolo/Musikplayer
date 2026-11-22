package de.musikplayer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/** Lädt eingebettete Albumcover (klein, zwischengespeichert). Ohne Cover bleibt es beim Farb-Platzhalter. */
object Art {
    private val cache = object : LruCache<Long, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<Long> = Collections.synchronizedSet(HashSet())

    suspend fun first(ctx: Context, tracks: List<Track>, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        for (t in tracks.take(4)) {
            load(ctx, t, size)?.let { return@withContext it }
        }
        null
    }

    private fun load(ctx: Context, t: Track, size: Int): Bitmap? {
        cache.get(t.id)?.let { return it }
        if (t.id in missing) return null
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                ctx.contentResolver.loadThumbnail(t.uri(), Size(size, size), null)
            } catch (e: Exception) {
                null
            }
        }
        if (bmp == null) bmp = embedded(t.path, size)
        if (bmp == null) missing.add(t.id) else cache.put(t.id, bmp)
        return bmp
    }

    private fun embedded(path: String, size: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val data = r.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }
}

fun placeholderColor(label: String): Color =
    Color.hsv((Math.abs(label.hashCode()) % 360).toFloat(), 0.45f, 0.42f)

/** Quadratisches Cover: Albumbild des ersten Titels mit Bild, sonst Farbfläche mit Anfangsbuchstabe. */
@Composable
fun Cover(tracks: List<Track>, label: String, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val ctx = LocalContext.current
    val key = tracks.take(4).map { it.id }
    val bmp by produceState<ImageBitmap?>(null, key) {
        value = Art.first(ctx, tracks, sizePx)?.asImageBitmap()
    }
    Box(modifier.background(placeholderColor(label)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                label.trim().take(1).uppercase().ifEmpty { "♪" },
                color = Color.White.copy(alpha = 0.85f), fontSize = 28.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
