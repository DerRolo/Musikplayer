package de.musikplayer.app

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Lieder, die von außen geöffnet werden (Dateimanager, Messenger, Downloads …).
 *
 * Regeln:
 * - Es wird immer nur dieses eine Lied gespielt (keine Ordner-Warteschlange).
 * - Liegt die Datei in der Bibliothek, wird der Bibliothekstitel gespielt (Herz, Playlist, Sperren usw. bleiben verfügbar).
 * - Sonst wird die Datei nur abgespielt und NICHT in die Bibliothek, Playlists oder Statistiken aufgenommen.
 *
 * Fremde Titel haben die Media-ID „ext:<uri>:x“. Sie endet bewusst nicht auf eine Zahl, damit alle Stellen,
 * die eine Bibliotheks-ID per `substringAfterLast(':')` lesen, für fremde Titel sauber `null` liefern.
 */
object External {
    const val PREFIX = "ext:"
    /** Wiedergabe-Kontext für von außen geöffnete Lieder (eigene Shuffle-/Wiederholen-Einstellung). */
    const val CONTEXT = "ext"

    fun isExternal(item: MediaItem?): Boolean = item?.mediaId?.startsWith(PREFIX) == true

    /** Fertiges MediaItem: Bibliothekstitel, falls vorhanden, sonst ein reiner Fremdtitel. Läuft auf einem Hintergrund-Thread. */
    fun itemFor(ctx: Context, uri: Uri): MediaItem {
        val t = libraryTrack(ctx, uri)
        return if (t != null) Library.mediaItem(t, CONTEXT) else foreignItem(ctx, uri)
    }

    /** Sucht die Datei in der Bibliothek (über Medien-ID oder Dateipfad). */
    fun libraryTrack(ctx: Context, uri: Uri): Track? {
        val tracks = try { Library.ensureLoaded(ctx) } catch (e: Exception) { emptyList() }
        if (tracks.isEmpty()) return null
        if (uri.authority == MediaStore.AUTHORITY) {
            val id = try { ContentUris.parseId(uri) } catch (e: Exception) { -1L }
            if (id >= 0) tracks.firstOrNull { it.id == id }?.let { return it }
        }
        val path = pathOf(ctx, uri) ?: return null
        val canon = try { File(path).canonicalPath } catch (e: Exception) { path }
        return tracks.firstOrNull { it.path == path || it.path == canon }
    }

    /** Best-Effort: echten Dateipfad zu einer URI finden (nicht jede App gibt ihn preis). */
    private fun pathOf(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null
        // Interner Speicher/SD-Karte über den Android-Dateiauswahl-Dienst: "primary:Music/x.mp3" bzw. "1234-ABCD:Music/x.mp3"
        if (uri.authority == "com.android.externalstorage.documents") {
            try {
                val doc = DocumentsContract.getDocumentId(uri)
                val vol = doc.substringBefore(':')
                val rel = doc.substringAfter(':', "")
                return if (vol.equals("primary", true)) "/storage/emulated/0/$rel" else "/storage/$vol/$rel"
            } catch (e: Exception) { /* weiter unten versuchen */ }
        }
        // Viele Dateimanager und die Medien-Datenbank liefern die Spalte _data mit
        try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (i >= 0) c.getString(i)?.takeIf { it.startsWith("/") }?.let { return it }
                }
            }
        } catch (e: Exception) { /* Spalte nicht vorhanden */ }
        // Manche FileProvider haben den Pfad direkt in der URI (…/storage/emulated/0/Music/x.mp3)
        val p = uri.path ?: return null
        val i = p.indexOf("/storage/")
        if (i >= 0) return Uri.decode(p.substring(i)).takeIf { File(it).exists() }
        return null
    }

    /** Fremdtitel: Titel/Interpret/Cover aus den Tags, sonst aus dem Dateinamen. */
    private fun foreignItem(ctx: Context, uri: Uri): MediaItem {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var art: ByteArray? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
            art = r.embeddedPicture?.let { shrink(it) }
        } catch (e: Exception) {
            // Tags nicht lesbar – Wiedergabe kann trotzdem klappen
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
        if (title == null) title = displayName(ctx, uri)?.let { Names.clean(it, artist) }?.takeIf { it.isNotBlank() } ?: "Unbekannter Titel"
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: "")
            .setAlbumTitle(album)
            .setIsPlayable(true)
            .setIsBrowsable(false)
        art?.let { md.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        return MediaItem.Builder()
            .setMediaId("$PREFIX$uri:x")
            .setUri(uri)
            // Die URI auch in den Anfrage-Daten mitgeben: sie überlebt den Weg vom Controller zum Wiedergabe-Dienst sicher
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(md.build())
            .build()
    }

    private fun displayName(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        } catch (e: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    /** Eingebettetes Cover auf max. 512 px verkleinern (Benachrichtigung und Player brauchen nicht mehr). */
    private fun shrink(bytes: ByteArray): ByteArray? = try {
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (b == null) null
        else if (b.width <= 512 && b.height <= 512 && bytes.size < 300_000) bytes
        else {
            val f = 512f / maxOf(b.width, b.height)
            val s = Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f).toInt()), maxOf(1, (b.height * f).toInt()), true)
            ByteArrayOutputStream().use { o -> s.compress(Bitmap.CompressFormat.JPEG, 88, o); o.toByteArray() }
        }
    } catch (e: Exception) { null }

    /** Cover eines Fremdtitels als Bild (für Mini-Leiste und Player). */
    fun artwork(item: MediaItem?): Bitmap? {
        val d = item?.mediaMetadata?.artworkData ?: return null
        return try { BitmapFactory.decodeByteArray(d, 0, d.size) } catch (e: Exception) { null }
    }
}
