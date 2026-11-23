package de.musikplayer.app

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val UNKNOWN_ARTIST = "Unbekannt"

fun Track.uri(): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/** Die Mediathek: liest Musik wie der Dateimanager-Reiter „Musik“ (Medien-Datenbank ohne Aufnahmen/Töne). */
object Library {

    @Volatile
    var tracks: List<Track> = emptyList()
        private set

    private val GENERIC_FOLDERS = setOf(
        "music", "musik", "download", "downloads", "audio", "sounds", "songs", "media", "documents",
        "dokumente", "dcim", "podcasts", "audiobooks", "hörbücher", "0", "sdcard", "storage", "emulated", "neu", "new"
    )

    private val RECORDING_KEYS = listOf(
        "whatsapp", "telegram", "/recordings/", "/recorder", "/voice recorder", "/voice_recorder", "/voicerecorder",
        "/voice memos", "/voicememos", "/voice notes", "/sprach", "/aufnahme", "/call recordings", "/callrecord",
        "/sound_recorder", "/soundrecorder", "/ringtones/", "/notifications/", "/alarms/"
    )

    /** Sprachnachrichten, Aufnahmen und Töne – gehören nicht zur Musik (gleiche Regeln wie im Dateimanager). */
    fun isRecordingPath(p: String): Boolean {
        val low = p.lowercase()
        for (k in RECORDING_KEYS) if (low.contains(k)) return true
        val n = low.substringAfterLast('/')
        return n.startsWith("ptt-") || n.startsWith("aud-") || n.startsWith("voice ") || n.startsWith("recording") ||
            n.startsWith("aufnahme") || n.startsWith("sprachnachricht")
    }

    fun isGenericFolder(name: String) = name.trim().lowercase() in GENERIC_FOLDERS

    private fun knownArtist(a: String?): String? {
        val t = a?.trim().orEmpty()
        return if (t.isEmpty() || t == "<unknown>" || t.equals("unknown", true) || t.equals("unbekannt", true)) null else t
    }

    // ---------- Laden ----------

    /** Gesperrte Lieder (Sperrliste): tauchen nirgends in der App auf, bleiben aber auf dem Handy. */
    @Volatile
    var blocked: List<Track> = emptyList()
        private set

    @Synchronized
    fun load(ctx: Context): List<Track> {
        Store.load(ctx)
        val all = scan(ctx)
        tracks = all.filter { !Store.isBlocked(it) }
        blocked = all.filter { Store.isBlocked(it) }
        return tracks
    }

    fun ensureLoaded(ctx: Context): List<Track> = if (tracks.isEmpty()) load(ctx) else tracks

    private fun scan(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val raws = ArrayList<Raw>()
        val seen = HashSet<String>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_MODIFIED
        )
        val cursor = try {
            ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)
        } catch (e: Exception) {
            null
        }
        cursor?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val p = c.getString(iData) ?: continue
                if (p.contains("/.") || p.contains("/Android/data/")) continue
                if (isRecordingPath(p)) continue
                if (!seen.add(p)) continue
                val f = File(p)
                if (!f.isFile) continue
                val dur = c.getLong(iDur)
                if (dur <= 0L) continue
                val tag = knownArtist(c.getString(iArtist))?.let { Names.cleanArtist(it) }
                val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension
                raws += Raw(
                    c.getLong(iId), p, title, tag, c.getString(iAlbum).orEmpty(),
                    c.getLong(iAlbumId), dur, c.getLong(iSize), c.getLong(iMod) * 1000
                )
            }
        }
        // Zweiter Durchgang: Namen bereinigen (Tracknummern, Unterstriche); Ordner-Mehrheit für „1 Song“-Muster
        val numbered = raws.groupBy { File(it.path).parent.orEmpty() }
            .mapValues { (_, l) -> Names.folderIsNumbered(l.map { File(it.path).nameWithoutExtension }) }
        val artistsPerFolder = HashMap<String, MutableSet<String>>()
        for (r in raws) artistsPerFolder.getOrPut(File(r.path).parent.orEmpty()) { HashSet() }.add(effectiveArtist(r.path, r.tag).first)
        for (r in raws) {
            val (artist, manual) = effectiveArtist(r.path, r.tag)
            val artistForCheck = r.tag ?: artist.takeIf { it != UNKNOWN_ARTIST }
            val fromName = albumPattern(r.path, artist)
            val title = if (fromName != null) Names.clean(fromName.second, artistForCheck, false)
            else Names.clean(r.title, artistForCheck, numbered[File(r.path).parent.orEmpty()] == true)
            val folder = File(r.path).parent.orEmpty()
            out += Track(
                r.id, r.path, title, artist, r.tag ?: "", r.album,
                r.albumId, r.durationMs, r.size, r.modified, manual,
                albumName = Store.trackAlbums[r.path] ?: albumOf(r.path, r.album, artist, (artistsPerFolder[folder]?.size ?: 1))
            )
        }
        return out
    }

    private val ARTIST_DASH = Regex("^(?:\\d{1,3}\\s*[.\\-_ ]+\\s*)?(.{2,40}?)\\s+-\\s+\\S.*$")

    /** „Colin Hay - Overkill“ → „Colin Hay“ (nur ein Vorschlag, wenn kein Interpret bekannt ist). */
    fun guessArtistFromName(t: Track): String? {
        val base = Names.tidy(File(t.path).nameWithoutExtension).replace('_', ' ')
        val a = ARTIST_DASH.find(base)?.groupValues?.get(1)?.trim() ?: return null
        return a.takeIf { it.any { c -> c.isLetter() } }
    }

    private val ALBUM_IN_NAME = Regex("^(?:\\d{1,3}\\s*[.\\-_ ]+\\s*)?(.+?)\\s*\\((\\d{4})\\)\\s*-\\s*(.+?)\\s*-\\s*(.+)$")
    private val ALBUM_GENERIC = setOf("musik", "music", "download", "downloads", "unknown", "<unknown>", "audio", "songs", "lieder", "sdcard", "hiphop", "hip hop")

    /** Album eines Liedes: Dateiname „Interpret (Jahr) - Album - Titel“, sonst Tag/Ordner (wenn er nicht nur der Interpretenordner ist). */
    private val ALBUM_NUMBERED = Regex("^(?:\\d{1,3}\\s*[.\\-_ ]+\\s*)?(.+?)\\s+-\\s+(.+?)\\s+-\\s+\\d{1,2}\\s*[.\\-]?\\s*-?\\s*(.+)$")

    /** „Interpret (1993) - Album - Titel“ oder „Interpret - Album - 01 - Titel“ → (Album, Titel); nur wenn der Interpret stimmt. */
    fun albumPattern(path: String, artist: String): Pair<String, String>? {
        val base = Names.tidy(File(path).nameWithoutExtension).replace('_', ' ')
        val ar = Names.fold(artist).filter { it.isLetterOrDigit() }
        if (ar.isEmpty()) return null
        fun same(x: String): Boolean {
            val a = Names.fold(x).filter { it.isLetterOrDigit() }
            return a.isNotEmpty() && (a == ar || a.contains(ar) || ar.contains(a))
        }
        ALBUM_IN_NAME.find(base)?.let { m ->
            if (same(m.groupValues[1])) return (m.groupValues[3].trim() + " (" + m.groupValues[2] + ")") to m.groupValues[4].trim()
        }
        ALBUM_NUMBERED.find(base)?.let { m ->
            if (same(m.groupValues[1])) return m.groupValues[2].trim() to m.groupValues[3].trim()
        }
        return null
    }

    fun albumOf(path: String, tagAlbum: String, artist: String, artistsInFolder: Int): String {
        val f = File(path)
        albumPattern(path, artist)?.let { return it.first }
        val a = tagAlbum.trim()
        if (a.isEmpty()) return ""
        val fa = Names.fold(a)
        if (fa in ALBUM_GENERIC || fa == Names.fold(artist)) return ""
        val folderName = Names.fold(f.parentFile?.name.orEmpty())
        // Album = bloß der Ordnername und im Ordner liegen viele verschiedene Interpreten → kein Album, nur eine Sammelablage
        if (fa == folderName && artistsInFolder >= 4) return ""
        return a
    }

    private class Raw(
        val id: Long, val path: String, val title: String, val tag: String?, val album: String,
        val albumId: Long, val durationMs: Long, val size: Long, val modified: Long
    )

    /** Titel-Zuordnung > Interpret im Tag > Ordner-Zuordnung > „Unbekannt“. */
    private fun effectiveArtist(path: String, tag: String?): Pair<String, Boolean> {
        Store.trackArtists[path]?.let { return it to true }
        if (tag != null) return tag to false
        val folder = File(path).parent.orEmpty()
        Store.folderArtists[folder]?.let { return it to true }
        return UNKNOWN_ARTIST to false
    }

    // ---------- Ansichten ----------

    fun byArtist(): Map<String, List<Track>> =
        tracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    fun byAlbum(): Map<String, List<Track>> =
        tracks.filter { it.album.isNotBlank() }.groupBy { it.album }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    /** [level]: 0 = Standardrichtung, 1 = umgekehrt, 2 = keine Sortierung. */
    fun sorted(list: List<Track>, by: SortBy, level: Int = 0): List<Track> {
        if (level >= 2) return list
        val asc = when (by) {
            SortBy.Title -> list.sortedBy { it.sortKey }
            SortBy.Artist -> list.sortedWith(compareBy({ it.artistKey }, { it.sortKey }))
            SortBy.Added -> list.sortedByDescending { it.modified }
            SortBy.Duration -> list.sortedByDescending { it.durationMs }
            SortBy.Plays -> list.sortedByDescending { Store.playCounts[it.path] ?: 0 }
        }
        return if (level == 1) asc.asReversed() else asc
    }

    /** Ordner mit Liedern ohne Interpret im Tag (oder mit deiner Zuordnung) – für die Einstellungen. */
    fun folders(): List<FolderInfo> {
        val out = ArrayList<FolderInfo>()
        for ((folder, list) in tracks.groupBy { it.folder }) {
            val unknown = list.count { it.tagArtist.isEmpty() && Store.trackArtists[it.path] == null }
            val assigned = Store.folderArtists[folder]
            if (unknown == 0 && assigned == null) continue
            val name = File(folder).name
            val suggestion = if (!isGenericFolder(name) && name.isNotBlank()) Names.cleanArtist(name) else null
            out += FolderInfo(folder, name, list.size, unknown, suggestion, assigned)
        }
        return out.sortedWith(compareByDescending<FolderInfo> { it.assigned == null }.thenByDescending { it.unknownCount })
    }

    // ---------- MediaItems (App und Android Auto) ----------

    fun mediaItem(t: Track, ctx: String): MediaItem = MediaItem.Builder()
        .setMediaId("t:$ctx:${t.id}")
        .setUri(t.uri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(Uri.parse("content://media/external/audio/albumart/${t.albumId}"))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()

    /** Liste für einen Kontext: "all", "a:<Interpret>", "p:<Playlist-ID>". */
    fun tracksFor(context: String): List<Track> = when {
        context == "all" -> sorted(tracks, SortBy.Title)
        context.startsWith("a:") -> tracks.filter { it.artist == context.substring(2) }.sortedBy { it.sortKey }
        context.startsWith("p:") -> {
            val byPath = tracks.associateBy { it.path }
            Store.playlists.firstOrNull { it.id == context.substring(2) }?.trackPaths?.mapNotNull { byPath[it] }.orEmpty()
        }
        else -> emptyList()
    }

    /** Einträge einer Playlist mit ihrer Position in der gespeicherten Liste (fehlende Dateien entfallen). */
    fun playlistEntries(id: String): List<Pair<Int, Track>> {
        val byPath = tracks.associateBy { it.path }
        val p = Store.playlists.firstOrNull { it.id == id } ?: return emptyList()
        return p.trackPaths.mapIndexedNotNull { i, path -> byPath[path]?.let { i to it } }
    }

    // ---------- Dateien ----------

    private fun safeName(s: String): String {
        val t = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')
        return if (t.isEmpty()) "_" else t
    }

    private fun roots(ctx: Context): List<File> {
        val primary = Environment.getExternalStorageDirectory()
        val list = mutableListOf(primary)
        ctx.getExternalFilesDirs(null).filterNotNull().forEach { f ->
            val p = f.absolutePath
            val idx = p.indexOf("/Android/")
            if (idx > 0) {
                val base = File(p.substring(0, idx))
                if (list.none { it.absolutePath == base.absolutePath }) list += base
            }
        }
        return list
    }

    /** Wurzel des Speichers (intern oder SD-Karte), auf dem die Datei liegt. */
    fun volumeRoot(ctx: Context, path: String): File =
        roots(ctx).firstOrNull { path.startsWith(it.path + "/") } ?: Environment.getExternalStorageDirectory()

    private fun uniqueTarget(dir: File, name: String): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        val dot = name.lastIndexOf('.')
        val base = if (dot <= 0) name else name.substring(0, dot)
        val ext = if (dot <= 0) "" else name.substring(dot)
        var n = 1
        while (t.exists()) {
            t = File(dir, "$base ($n)$ext")
            n++
        }
        return t
    }

    /** Medien-Datenbank für die Pfade aktualisieren (auch gelöschte/verschobene) und kurz warten. */
    fun rescan(ctx: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(20, TimeUnit.SECONDS)
    }

    /** Titel, die per Zuordnung einen Interpreten haben, aber noch nicht in „<Speicher>/Music/<Interpret>“ liegen. */
    fun sortCandidates(): List<Track> = tracks.filter {
        it.manual && it.artist != UNKNOWN_ARTIST &&
            !File(it.folder).name.equals(safeName(it.artist), ignoreCase = true)
    }

    data class MoveResult(val moved: Int, val failed: Int, val map: Map<String, String>)

    /**
     * Verschiebt Dateien in „<Speicher>/Music/<Interpret>/“ – auf demselben Speicher (SD-Karte bleibt SD-Karte).
     * Danach werden Playlists und Zuordnungen auf die neuen Pfade umgestellt.
     */
    fun sortIntoArtistFolders(ctx: Context, list: List<Track>): MoveResult {
        val map = LinkedHashMap<String, String>()
        var failed = 0
        for (t in list) {
            if (t.artist == UNKNOWN_ARTIST) continue
            val src = File(t.path)
            if (!src.isFile) continue
            val dir = File(File(volumeRoot(ctx, t.path), "Music"), safeName(t.artist))
            if (src.parentFile?.absolutePath == dir.absolutePath) continue
            dir.mkdirs()
            val target = uniqueTarget(dir, src.name)
            val ok = src.renameTo(target) || try {
                src.copyTo(target)
                src.delete()
            } catch (e: Exception) {
                target.delete()
                false
            }
            if (ok) map[t.path] = target.path else failed++
        }
        Store.remapPaths(ctx, map)
        rescan(ctx, map.keys.toList() + map.values.toList())
        return MoveResult(map.size, failed, map)
    }

    // ---------- Dateinamen übernehmen ----------

    /** Dateiname so, wie das Lied in der App heißt (bereinigter Titel + alte Endung). */
    fun proposedFileName(t: Track): String {
        val ext = File(t.path).extension
        return Names.fileBaseName(t.title) + if (ext.isEmpty()) "" else ".$ext"
    }

    /** Lieder, deren Dateiname vom Namen in der App abweicht. */
    fun renameCandidates(): List<Track> = tracks.filter { File(it.path).name != proposedFileName(it) }

    data class RenameResult(val renamed: Int, val failed: Int, val skipped: Int)

    /** Benennt Dateien um (Titel, neuer Name ohne Endung). Playlists und Zuordnungen ziehen mit. */
    fun renameFiles(ctx: Context, items: List<Pair<Track, String>>): RenameResult {
        val map = LinkedHashMap<String, String>()
        var failed = 0
        var skipped = 0
        for ((t, base) in items) {
            val src = File(t.path)
            val dir = src.parentFile
            if (!src.isFile || dir == null) { failed++; continue }
            val ext = src.extension
            val name = Names.fileBaseName(base) + if (ext.isEmpty()) "" else ".$ext"
            if (name == src.name) { skipped++; continue }
            var target: File
            val ok: Boolean
            if (name.equals(src.name, ignoreCase = true)) {
                // nur Groß-/Kleinschreibung: auf FAT/exFAT (SD-Karte) über einen Zwischennamen
                target = File(dir, name)
                val tmp = File(dir, "." + System.nanoTime() + ".tmp")
                ok = src.renameTo(tmp) && (tmp.renameTo(target) || run { tmp.renameTo(src); false })
            } else {
                target = uniqueTarget(dir, name)
                ok = src.renameTo(target)
            }
            if (ok) map[t.path] = target.path else failed++
        }
        Store.remapPaths(ctx, map)
        rescan(ctx, map.keys.toList() + map.values.toList())
        return RenameResult(map.size, failed, skipped)
    }

    /** Löscht Dateien endgültig. Gibt die Anzahl der gelöschten Dateien zurück. */
    fun deleteFiles(ctx: Context, list: List<Track>): Int {
        val done = ArrayList<String>()
        for (t in list) if (File(t.path).delete()) done += t.path
        rescan(ctx, done)
        return done.size
    }
}
