package de.musikplayer.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * App-Update direkt aus den GitHub-Releases des Projekts (wie sie der Workflow baut: Tag „v1.0.<Nummer>“ + APK).
 * Beim Start (abschaltbar in Optionen → App-Update) wird im Hintergrund nachgesehen; gibt es eine neuere Nummer,
 * erscheint „Update bereit“ mit „Installieren“. Android zeigt danach seine eigene Bestätigung.
 * Voraussetzung: Das Repository ist öffentlich (sonst liefert GitHub ohne Anmeldung keine Releases).
 */
object Updater {

    data class Release(val tag: String, val number: Int, val notes: String, val apkUrl: String, val size: Long)

    /** Gefundenes neueres Update (null = keins bzw. noch nicht gesucht). */
    var available by mutableStateOf<Release?>(null)
        private set
    /** Status für die Anzeige (z. B. „Suche …“, Fehlermeldung). */
    var status by mutableStateOf<String?>(null)
    /** Download-Fortschritt 0..1, -1 = kein Download. */
    var progress by mutableStateOf(-1f)
        private set
    /** „Später“ gewählt: für diese Version in dieser Sitzung nicht mehr nachfragen. */
    var dismissedTag by mutableStateOf<String?>(null)
    private var checkedThisStart = false

    fun installedNumber(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (e: Exception) { 0 }

    fun installedName(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    fun repo(ctx: Context): String = try { ctx.getString(R.string.update_repo) } catch (e: Exception) { "" }

    /** Einmal pro App-Start automatisch (wenn eingeschaltet). */
    suspend fun checkOnStart(ctx: Context) {
        if (checkedThisStart || !Store.autoUpdate) return
        checkedThisStart = true
        check(ctx, manual = false)
    }

    /** Neueste Version auf GitHub abfragen. [manual] = vom Nutzer angestoßen (dann auch „Kein Update“ melden). */
    suspend fun check(ctx: Context, manual: Boolean) {
        val repo = repo(ctx)
        if (repo.isBlank()) { if (manual) status = "Diese Version wurde ohne GitHub-Angabe gebaut – Update-Suche nicht möglich."; return }
        if (manual) status = "Suche nach Updates …"
        val r: Result<Release?> = withContext<Result<Release?>>(Dispatchers.IO) {
            try {
                val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Musikplayer-App")
                c.connectTimeout = 8000; c.readTimeout = 10000
                try {
                    when (c.responseCode) {
                        200 -> {}
                        404 -> return@withContext Result.failure<Release?>(Exception("Keine Releases gefunden – ist das Repository öffentlich?"))
                        else -> return@withContext Result.failure<Release?>(Exception("GitHub antwortet nicht (Code ${c.responseCode})"))
                    }
                    val o = JSONObject(c.inputStream.use { String(it.readBytes()) })
                    val tag = o.optString("tag_name")
                    val num = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
                    val assets = o.optJSONArray("assets")
                    var url = ""; var size = 0L
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
                    }
                    if (url.isEmpty()) Result.success(null) else Result.success(Release(tag, num, o.optString("body").trim(), url, size))
                } finally { c.disconnect() }
            } catch (e: Exception) { Result.failure(Exception("Keine Verbindung zu GitHub")) }
        }
        val rel = r.getOrNull()
        val err = r.exceptionOrNull()
        when {
            err != null -> { if (manual) status = err.message }
            rel != null && rel.number > installedNumber(ctx) -> { available = rel; status = null }
            else -> { available = null; if (manual) status = "Du hast die neueste Version (${installedName(ctx)})." }
        }
    }

    /** Herunterladen und an Android zur Installation übergeben. */
    suspend fun install(ctx: Context, rel: Release) {
        val app = ctx.applicationContext
        // Android verlangt einmalig die Erlaubnis „Unbekannte Apps installieren“ für diese App
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            status = "Bitte erlaube „Apps aus dieser Quelle installieren“ und tippe dann nochmal auf „Installieren“."
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { app.startActivity(i) } catch (e: Exception) { }
            return
        }
        status = "Lade ${rel.tag} …"
        progress = 0f
        val file = File(app.cacheDir, "update.apk")
        val ok = withContext(Dispatchers.IO) {
            try {
                val c = URL(rel.apkUrl).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Musikplayer-App")
                c.connectTimeout = 10000; c.readTimeout = 30000
                try {
                    if (c.responseCode != 200) return@withContext false
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: rel.size
                    c.inputStream.use { inp ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = inp.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                if (total > 0) withContext(Dispatchers.Main) { progress = (done.toFloat() / total).coerceIn(0f, 1f) }
                            }
                        }
                    }
                    total <= 0 || file.length() == total
                } finally { c.disconnect() }
            } catch (e: Exception) { false }
        }
        progress = -1f
        if (!ok) { status = "Download fehlgeschlagen – bitte nochmal versuchen."; return }
        status = "Installation wird gestartet …"
        val started = withContext(Dispatchers.IO) {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(app.packageName)
                // Ab Android 12: Hat die App ihre aktuelle Version selbst installiert, darf sie sich ohne Rückfrage
                // aktualisieren. Beim allerersten Update (installiert über Browser/Dateimanager) fragt Android noch.
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = installer.createSession(params)
                installer.openSession(id).use { s ->
                    s.openWrite("base.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(app, 7, Intent(app, UpdateReceiver::class.java), flags)
                    s.commit(pi.intentSender)
                }
                true
            } catch (e: Exception) { false }
        }
        if (!started) status = "Installation konnte nicht gestartet werden."
    }
}

/** Rückmeldung von Android: Bestätigung anzeigen, Erfolg oder Fehler. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(confirm) } catch (e: Exception) { Updater.status = "Bestätigung konnte nicht geöffnet werden." }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.status = "Update installiert."
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.status = "Installation abgebrochen."
            else -> Updater.status = "Installation fehlgeschlagen: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unbekannter Fehler")
        }
    }
}
