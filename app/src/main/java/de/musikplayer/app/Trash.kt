package de.musikplayer.app

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Ein Lied im Papierkorb: liegt in [file], kam von [origPath]. */
data class TrashItem(
    val id: String,
    val file: String,
    val origPath: String,
    val title: String,
    val artist: String,
    val size: Long,
    val durationMs: Long,
    val deletedAt: Long
) {
    fun daysLeft(now: Long = System.currentTimeMillis()): Int =
        maxOf(0, Trash.DAYS - ((now - deletedAt) / DAY_MS).toInt())

    fun toJson(): JSONObject = JSONObject().put("id", id).put("f", file).put("o", origPath).put("t", title).put("a", artist)
        .put("s", size).put("d", durationMs).put("at", deletedAt)

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        fun from(o: JSONObject) = TrashItem(
            o.getString("id"), o.getString("f"), o.getString("o"), o.optString("t"), o.optString("a"),
            o.optLong("s"), o.optLong("d"), o.optLong("at")
        )
    }
}

/**
 * Papierkorb: Alles, was die App löscht, wird zuerst hierher verschoben und erst nach [DAYS] Tagen endgültig gelöscht.
 * Die Dateien liegen im App-Ordner auf demselben Speicher (SD-Karte bleibt SD-Karte) und tauchen nicht in der
 * Medien-Datenbank auf. Playlists, Favoriten, Zuordnungen usw. bleiben gespeichert: Beim Wiederherstellen an den
 * alten Ort ist alles wieder wie vorher.
 */
object Trash {
    const val DAYS = 30

    /** Papierkorb-Ordner auf demselben Speicher wie [path]. */
    private fun dirFor(ctx: Context, path: String): File {
        val root = Library.volumeRoot(ctx, path).path
        val dirs = ctx.getExternalFilesDirs("Papierkorb").filterNotNull()
        val d = dirs.firstOrNull { it.path.startsWith("$root/") } ?: dirs.firstOrNull() ?: File(ctx.filesDir, "Papierkorb")
        d.mkdirs()
        val nomedia = File(d, ".nomedia")
        if (!nomedia.exists()) try { nomedia.createNewFile() } catch (e: Exception) { }
        return d
    }

    fun isInTrash(path: String?): Boolean = path != null && Store.trash.any { it.file == path }

    private fun move(src: File, dst: File): Boolean {
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) return true
        return try {
            src.copyTo(dst, overwrite = false)
            if (!src.delete()) { dst.delete(); false } else true
        } catch (e: Exception) { dst.delete(); false }
    }

    /** Lieder in den Papierkorb verschieben. Gibt die Anzahl und die neuen Einträge zurück. */
    fun moveToTrash(ctx: Context, list: List<Track>): List<TrashItem> {
        val out = ArrayList<TrashItem>()
        val gone = ArrayList<String>()
        for (t in list) {
            val src = File(t.path)
            if (!src.isFile) continue
            val id = UUID.randomUUID().toString().replace("-", "").take(12)
            val dst = File(dirFor(ctx, t.path), id + "_" + src.name)
            if (move(src, dst)) {
                out += TrashItem(id, dst.path, t.path, t.title, t.artist, t.size, t.durationMs, System.currentTimeMillis())
                gone += t.path
            }
        }
        if (out.isNotEmpty()) Store.addTrash(ctx, out)
        Library.rescan(ctx, gone)
        return out
    }

    /** Zurück an den alten Ort (ist der belegt, mit „(2)“ im Namen). Gibt die Zahl der wiederhergestellten zurück. */
    fun restore(ctx: Context, items: List<TrashItem>): Int {
        val back = ArrayList<String>()
        val remap = LinkedHashMap<String, String>()
        val done = ArrayList<String>()
        for (i in items) {
            val src = File(i.file)
            if (!src.isFile) { done += i.id; continue }
            var dst = File(i.origPath)
            if (dst.exists()) {
                val name = dst.name
                val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
                var n = 2
                while (dst.exists()) { dst = File(dst.parentFile, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"); n++ }
                remap[i.origPath] = dst.path
            }
            if (move(src, dst)) { back += dst.path; done += i.id }
        }
        // Lag am alten Ort inzwischen etwas anderes: Playlists usw. auf den neuen Namen umstellen
        if (remap.isNotEmpty()) Store.remapPaths(ctx, remap)
        Store.removeTrash(ctx, done.toSet())
        Library.rescan(ctx, back)
        return back.size
    }

    /** Endgültig löschen (ohne Rückweg). */
    fun deleteForever(ctx: Context, items: List<TrashItem>): Int {
        var n = 0
        for (i in items) { val f = File(i.file); if (!f.exists() || f.delete()) n++ }
        Store.removeTrash(ctx, items.map { it.id }.toSet())
        return n
    }

    /** Alles, was länger als [DAYS] Tage im Papierkorb liegt, endgültig löschen. */
    fun purgeOld(ctx: Context): Int {
        val limit = System.currentTimeMillis() - DAYS * TrashItem.DAY_MS
        val old = Store.trash.filter { it.deletedAt < limit }
        return if (old.isEmpty()) 0 else deleteForever(ctx, old)
    }
}
