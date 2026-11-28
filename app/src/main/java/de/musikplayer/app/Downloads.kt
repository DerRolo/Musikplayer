package de.musikplayer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File

/** Eine geprüfte Seite, auf der man Musik legal (kostenlos oder gekauft) herunterladen kann. */
data class DlSite(val name: String, val url: String, val info: String)

/**
 * Download-Funktion: Die App lädt selbst nichts herunter. Sie öffnet geprüfte Seiten im Standard-Browser
 * (bzw. Torrent-Dateien in einer Torrent-App). Was dort im Download-Ordner landet, findet die App beim
 * Zurückkehren und zeigt es unter „Neue Lieder“.
 */
object Downloads {
    val SITES = listOf(
        DlSite("Jamendo", "https://www.jamendo.com", "Unabhängige Künstler, Creative-Commons-Lizenz – kostenlos für privates Hören"),
        DlSite("Free Music Archive", "https://freemusicarchive.org", "Große Sammlung freier Musik mit Lizenzangabe je Lied"),
        DlSite("Internet Archive – Audio", "https://archive.org/details/audio", "Gemeinfreie Aufnahmen, Live-Mitschnitte mit Erlaubnis der Bands; viele auch als legaler Torrent"),
        DlSite("Bandcamp", "https://bandcamp.com", "Alben direkt von Künstlern kaufen – oft „Name your price“ oder kostenlos"),
        DlSite("ccMixter", "https://ccmixter.org", "Remixe und Stücke unter Creative Commons"),
        DlSite("Musopen", "https://musopen.org", "Klassische Musik, gemeinfrei aufgenommen")
    )

    const val LIBRETORRENT = "org.proninyaroslav.libretorrent"

    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mka")

    fun open(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false }

    /** Freie Websuche im Standard-Browser (nur wenn „Nur geprüfte Seiten“ aus ist). */
    fun search(ctx: Context, query: String): Boolean {
        if (Store.dlVerifiedOnly || query.isBlank()) return false
        return open(ctx, "https://www.google.com/search?q=" + Uri.encode(query.trim()))
    }

    /** Paketname einer installierten Torrent-App (oder null). */
    fun torrentApp(ctx: Context): String? {
        val pm = ctx.packageManager
        val probes = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("magnet:?xt=urn:btih:0000000000000000000000000000000000000000")),
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://x/a.torrent"), "application/x-bittorrent")
        )
        for (i in probes) {
            val hit = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull { it.activityInfo.packageName != ctx.packageName }
            if (hit != null) return hit.activityInfo.packageName
        }
        return if (installed(ctx, LIBRETORRENT)) LIBRETORRENT else null
    }

    private fun installed(ctx: Context, pkg: String) = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun appName(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    /** LibreTorrent (kostenlos, Open Source, werbefrei) im Play Store bzw. F-Droid öffnen. */
    fun installLibreTorrent(ctx: Context) {
        if (!open(ctx, "market://details?id=$LIBRETORRENT"))
            open(ctx, "https://play.google.com/store/apps/details?id=$LIBRETORRENT")
    }

    fun downloadDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * Sucht im Download-Ordner (mit Unterordnern, z. B. von Torrent-Apps) nach Audiodateien, die seit der
     * letzten Prüfung dazugekommen sind, meldet sie Android (MediaStore) und ruft danach [done] mit der Anzahl auf.
     * Beim allerersten Mal wird nur der Zeitpunkt gemerkt – vorhandene Dateien kennt die Bibliothek schon.
     */
    fun check(ctx: Context, done: (Int) -> Unit) {
        val app = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val since = Store.dlLastScan
            val now = System.currentTimeMillis()
            if (since == 0L) { main.post { Store.setDlLastScan(app, now) }; return@Thread }
            val found = ArrayList<File>()
            collect(downloadDir(), since, found, 0)
            if (found.isEmpty()) return@Thread
            val newest = found.maxOf { it.lastModified() }
            var left = found.size
            MediaScannerConnection.scanFile(app, found.map { it.absolutePath }.toTypedArray(), null) { _, _ ->
                val rest = synchronized(found) { --left }
                if (rest == 0) main.post {
                    Store.setDlLastScan(app, maxOf(newest, since))
                    done(found.size)
                }
            }
        }.start()
    }

    private fun collect(dir: File, since: Long, out: MutableList<File>, depth: Int) {
        if (depth > 4) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) collect(f, since, out, depth + 1)
            else if (f.extension.lowercase() in AUDIO && f.lastModified() > since && f.length() > 0) out.add(f)
        }
    }
}
