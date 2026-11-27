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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import java.io.File

/** Lädt eingebettete Albumcover (klein, zwischengespeichert). Ohne Cover bleibt es beim Farb-Platzhalter. */
object Art {
    private fun cacheKey(t: Track): String = t.path + "\u0000" + (Store.trackCovers[t.path] ?: "embedded")

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Zählt hoch, sobald sich ein Cover ändert; Bilder laden sich dann neu. */
    var version by mutableIntStateOf(0)
        private set

    @Volatile private var gen = 0

    fun invalidate() {
        gen++
        cache.evictAll(); bigCache.evictAll(); missing.clear(); version++
    }

    private fun custom(ctx: Context, path: String, size: Int): Bitmap? {
        val f = Store.trackCovers[path] ?: return null
        val file = java.io.File(java.io.File(ctx.filesDir, "trackcovers"), f)
        if (!file.exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, b)
        var sample = 1
        while (b.outWidth / (sample * 2) >= size && b.outHeight / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    suspend fun first(ctx: Context, tracks: List<Track>, size: Int): Bitmap? = withContext(Dispatchers.IO) {
        for (t in tracks.take(4)) {
            load(ctx, t, size)?.let { return@withContext it }
        }
        null
    }

    private val bigCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Sofort (ohne Laden) das große Cover, falls schon im Zwischenspeicher – verhindert Aufblitzen beim Liedwechsel. */
    fun peekLarge(t: Track): Bitmap? = bigCache.get(cacheKey(t))

    /** Großes Cover für den Player (nur wenn im Lied wirklich ein Bild steckt). */
    suspend fun large(ctx: Context, t: Track): Bitmap? = withContext(Dispatchers.IO) {
        bigCache.get(cacheKey(t))?.let { return@withContext it }
        val g = gen
        var bmp: Bitmap? = custom(ctx, t.path, 900)
        if (bmp != null) { if (g == gen) bigCache.put(cacheKey(t), bmp); return@withContext bmp }
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try { ctx.contentResolver.loadThumbnail(t.uri(), Size(900, 900), null) } catch (e: Exception) { null }
        }
        if (bmp == null) bmp = embedded(t.path, 900)
        if (bmp != null && g == gen) bigCache.put(cacheKey(t), bmp)
        bmp
    }

    private fun load(ctx: Context, t: Track, size: Int): Bitmap? {
        cache.get(cacheKey(t))?.let { return it }
        if (cacheKey(t) in missing) return null
        val g = gen
        var bmp: Bitmap? = custom(ctx, t.path, size)
        if (bmp != null) { if (g == gen) cache.put(cacheKey(t), bmp); return bmp }
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try {
                ctx.contentResolver.loadThumbnail(t.uri(), Size(size, size), null)
            } catch (e: Exception) {
                null
            }
        }
        if (bmp == null) bmp = embedded(t.path, size)
        if (g == gen) { if (bmp == null) missing.add(cacheKey(t)) else cache.put(cacheKey(t), bmp) }
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
    val ver = Art.version
    val bmp by produceState<ImageBitmap?>(null, key, ver) {
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

/** Interpreten-Cover mit persönlicher Auswahl; sonst fällt es auf Song-Cover zurück. */
@Composable
fun ArtistCover(artist: String, tracks: List<Track>, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val ctx = LocalContext.current
    val custom = Store.artistCovers[artist]
    val coverVersion = Art.version
    if (custom.isNullOrBlank()) {
        Cover(tracks, artist, modifier, sizePx)
    } else {
        val bmp by produceState<ImageBitmap?>(null, custom, coverVersion) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(File(File(ctx.filesDir, "artistcovers"), custom).path)?.asImageBitmap()
            }
        }
        Box(modifier.background(placeholderColor(artist)), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Text(artist.trim().take(1).uppercase().ifEmpty { "♪" }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}
