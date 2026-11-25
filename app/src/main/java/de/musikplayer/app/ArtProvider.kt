package de.musikplayer.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Gibt Cover an andere Apps weiter (Android Auto, Sperrbildschirm, Benachrichtigung).
 * Adresse: content://de.musikplayer.app.art/<Titel-ID>?c=<Cover-Kennung>
 * Das Cover kommt aus deinem gesetzten Bild oder aus der Datei; ohne Cover gibt es „nicht gefunden“.
 */
class ArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException()
        val tag = (uri.getQueryParameter("c") ?: "0").filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(60)
        val dir = File(ctx.cacheDir, "autoart").apply { mkdirs() }
        val f = File(dir, "$id-$tag.jpg")
        if (!f.exists()) {
            Store.load(ctx)
            val t = Library.ensureLoaded(ctx).firstOrNull { it.id == id } ?: throw FileNotFoundException()
            val bmp = runBlocking { Art.first(ctx, listOf(t), 512) } ?: throw FileNotFoundException()
            val tmp = File(dir, "$id-$tag.tmp")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(f)
            // alte Fassungen desselben Liedes aufräumen
            dir.listFiles()?.filter { it != f && it.name.startsWith("$id-") }?.forEach { it.delete() }
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
