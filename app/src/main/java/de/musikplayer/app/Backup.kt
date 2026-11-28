package de.musikplayer.app

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sicherung der App-Daten AUSSERHALB der App, damit nach dem Deinstallieren/Neuinstallieren nichts verloren ist.
 *
 * - Ordner: „Musikplayer-Sicherung“ im Hauptspeicher (bleibt beim Deinstallieren erhalten). Darin je Sicherung eine
 *   ZIP-Datei mit allen App-Daten: Playlists, Hörbücher, Lieblingssongs, Wiedergabezahlen, Zuordnungen, Cover,
 *   Sperrliste, Sortierungen, Einstellungen, Profil. Die letzten [KEEP] Sicherungen bleiben erhalten.
 * - Automatisch: nach Änderungen gebündelt (frühestens alle [MIN_GAP_MS]) und beim Verlassen der App.
 * - Frische Installation: Beim ersten Start wird gefragt, ob die letzte Sicherung wiederhergestellt werden soll.
 *   Solange das nicht beantwortet ist, wird NICHT automatisch gesichert (sonst würden leere Stände alte verdrängen).
 * - Die Zuordnungen hängen an den Dateipfaden: Liegen die Lieder noch an denselben Stellen, ist alles wie vorher.
 */
object Backup {
    const val DIR = "Musikplayer-Sicherung"
    private const val KEEP = 5
    private const val MIN_GAP_MS = 10 * 60 * 1000L
    private const val DELAY_MS = 60 * 1000L

    /** Zählt hoch, wenn eine Sicherung geschrieben oder gelöscht wurde (für die Anzeige). */
    var tick by mutableStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val thread = HandlerThread("Backup").apply { start() }
    private val bg = Handler(thread.looper)
    @Volatile private var dirty = false
    @Volatile private var lastRun = 0L
    @Volatile private var scheduled = false

    fun dir(): File = File(Environment.getExternalStorageDirectory(), DIR)

    /** Vorhandene Sicherungen, neueste zuerst. */
    fun list(): List<File> = (dir().listFiles { f -> f.isFile && f.name.startsWith("sicherung-") && f.name.endsWith(".zip") } ?: emptyArray())
        .sortedByDescending { it.name }

    fun label(f: File): String {
        val stamp = f.name.removePrefix("sicherung-").removeSuffix(".zip")
        return try {
            val d = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).parse(stamp)
            SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY).format(d!!)
        } catch (e: Exception) { stamp }
    }

    /** Daten haben sich geändert: Sicherung bald im Hintergrund schreiben. */
    fun changed(ctx: Context) {
        if (!Store.autoBackup || !Store.restoreOffered) return
        dirty = true
        if (scheduled) return
        scheduled = true
        val app = ctx.applicationContext
        val wait = maxOf(DELAY_MS, lastRun + MIN_GAP_MS - System.currentTimeMillis())
        bg.postDelayed({ scheduled = false; if (dirty) write(app) }, wait)
    }

    /** App wird verlassen: offene Änderungen sofort sichern. */
    fun flush(ctx: Context) {
        if (!dirty || !Store.autoBackup || !Store.restoreOffered) return
        val app = ctx.applicationContext
        bg.post { if (dirty) write(app) }
    }

    /** Sicherung jetzt schreiben (Knopf „Jetzt sichern“). Gibt die Datei zurück oder null bei Fehler. */
    fun now(ctx: Context): File? {
        val app = ctx.applicationContext
        var out: File? = null
        val done = java.util.concurrent.CountDownLatch(1)
        bg.post { try { out = write(app) } finally { done.countDown() } }
        done.await(30, java.util.concurrent.TimeUnit.SECONDS)
        return out
    }

    /** Alle Dateien im App-Datenordner in eine ZIP-Datei packen (Zwischenstände und Caches ausgenommen). */
    private fun write(ctx: Context): File? {
        dirty = false
        lastRun = System.currentTimeMillis()
        return try {
            val d = dir(); d.mkdirs()
            val name = "sicherung-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date()) + ".zip"
            val tmp = File(d, "$name.part")
            val root = ctx.filesDir
            ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".part") }.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.relativeTo(root).path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            val target = File(d, name)
            if (!tmp.renameTo(target)) { tmp.delete(); throw IllegalStateException("Umbenennen fehlgeschlagen") }
            // nur die letzten Sicherungen behalten
            list().drop(KEEP).forEach { it.delete() }
            lastError = null
            tick++
            target
        } catch (e: Exception) {
            lastError = "Sicherung fehlgeschlagen – hat die App Zugriff auf alle Dateien?"
            null
        }
    }

    /**
     * Sicherung zurückspielen: App-Daten werden durch den Stand aus der Datei ersetzt, danach startet die App neu.
     * Die aktuellen Daten werden vorher selbst noch einmal gesichert (falls man es sich anders überlegt).
     */
    fun restore(ctx: Context, f: File): Boolean {
        val app = ctx.applicationContext
        if (Store.restoreOffered) now(app)
        return try {
            val root = app.filesDir
            val staging = File(app.cacheDir, "restore").also { it.deleteRecursively(); it.mkdirs() }
            ZipInputStream(f.inputStream().buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(staging, e.name)
                    // Schutz: nichts außerhalb des Zielordners schreiben
                    if (!out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
            if (!File(staging, "musikplayer.json").isFile) return false
            // erst jetzt die aktuellen Daten ersetzen
            root.listFiles()?.forEach { it.deleteRecursively() }
            staging.copyRecursively(root, overwrite = true)
            staging.deleteRecursively()
            Store.reload(app)
            Store.setRestoreOffered(app)
            true
        } catch (e: Exception) { false }
    }

    /** Neustart der App, damit alles den wiederhergestellten Stand benutzt. */
    fun restartApp(ctx: Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (i != null) ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    fun deleteAll() { list().forEach { it.delete() }; tick++ }
}
