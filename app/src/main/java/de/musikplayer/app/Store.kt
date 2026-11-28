package de.musikplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Die feste Playlist „Lieblingssongs“ (Herz-Button). Kann nicht gelöscht oder umbenannt werden. */
const val FAV_ID = "fav"
const val FAV_NAME = "Lieblingssongs"

/**
 * Alles, was nur in der App lebt (keine Dateien werden kopiert):
 * Playlists, Interpret-Zuordnungen, Einstellungen. Gespeichert als JSON in der App.
 */
object Store {
    private const val FILE = "musikplayer.json"

    var playlists: List<Playlist> = emptyList()
        private set
    var folderArtists: Map<String, String> = emptyMap()
        private set
    var trackArtists: Map<String, String> = emptyMap()
        private set
    var sleepMinutes: Int = 30
        private set
    var fadeMinutes: Int = 30
        private set
    var albumGrid: Boolean = true
        private set
    var artistGrid: Boolean = true
        private set
    /** Wurde die Equalizer-Berechtigung schon einmal abgefragt? (Nicht bei jedem Öffnen erneut fragen.) */
    var eqAsked: Boolean = false
        private set
    var sortBy: SortBy = SortBy.Title
        private set
    /** 0 = Standardrichtung, 1 = umgekehrt, 2 = keine Sortierung (Reihenfolge der Bibliothek). */
    var sortLevel: Int = 0
        private set
    /** Lieder, die du schon gesehen hast (für „Neue Lieder“). */
    var knownPaths: Set<String> = emptySet()
        private set
    private var knownInit = false
    var playlistGrid: Boolean = false
        private set
    var audiobookGrid: Boolean = false
        private set
    var showAlbum: Boolean = false
        private set
    var blockedTracks: Set<String> = emptySet()
        private set
    var blockedArtists: Set<String> = emptySet()
        private set
    var blockedFolders: Set<String> = emptySet()
        private set
    // Soft block: exclude from library and library shuffle, but keep searchable and available in playlists.
    var softBlockedTracks: Set<String> = emptySet()
        private set
    var softBlockedArtists: Set<String> = emptySet()
        private set
    var softBlockedFolders: Set<String> = emptySet()
        private set
    /** Wie oft ein Lied gestartet wurde (für „Meistgehört“). */
    var playCounts: Map<String, Int> = emptyMap()
        private set
    /** Von dir vergebene Albumnamen (nur in der App), je Dateipfad. */
    var trackAlbums: Map<String, String> = emptyMap()
    /** Eigene Lied-Cover: Dateipfad -> Dateiname in filesDir/trackcovers. */
    var trackCovers: Map<String, String> = emptyMap()
        private set
    /** Eigene Interpreten-Cover, Interpretname -> Dateiname in filesDir/artistcovers. */
    var artistCovers: Map<String, String> = emptyMap()
        private set
    /** Dateien, deren Namen du selbst festgelegt hast: der Dateiname ist dann der Titel in der App. */
    var userNamed: Set<String> = emptySet()
        private set
    /** Stille am Anfang/Ende eines Liedes überspringen. */
    var skipSilence: Boolean = true
        private set
    /** Shuffle/Wiederholen pro Kontext: Schlüssel "all" (Bibliothek) oder "p:<Playlist-ID>", Wert "shuffle:repeat" (0/1 : 0/1/2). */
    var modes: Map<String, String> = emptyMap()
        private set
    /** Stille-Überspringen-Feineinstellung. */
    var silenceThr: Int = 250          // Amplitude (0–32767) unter der etwas als Stille gilt
        private set
    var silenceMinMs: Int = 1500       // erst Stille ab dieser Länge wird gekürzt
        private set
    var moveOnAssign: Boolean = true
    /** Schnell-Button „Dateinamen übernehmen“ im Player verschiebt auch in den Interpreten-Ordner. */
    var quickMove: Boolean = true
    var silenceKeepMs: Int = 500       // so viel Stille bleibt stehen (Hall)
        private set
    var silenceTailMs: Int = 1000      // so viel Stille bleibt nach dem letzten Ton
        private set
    /** Ergebnis der Ton-Suche je Lied: "Größe:Schwelle:ersterTonMs:letzterTonMs". */
    var trims: Map<String, String> = emptyMap()
        private set
    var silenceStartSec: Int = 3       // nur in den ersten Sekunden des Liedes
        private set
    var silenceEndSec: Int = 6         // nur in den letzten Sekunden des Liedes
        private set
    var stripStrings: List<String> = emptyList()
        private set
    var recentSearches: List<String> = emptyList()
    /** Favorisierte Interpreten (Stern). */
    var favArtists: List<String> = emptyList()
    /** Höhe/Größe der Listenzeilen. */
    var rowScale: Float = 1.0f
        private set
    /** Zuletzt gespieltes Bibliothekslied (für „Wiedergabe fortsetzen“ per Kopfhörer-/System-Taste). */
    var resumePath: String = ""
        private set
    var resumeContext: String = ""
        private set
    var resumePosMs: Long = 0L
        private set
    /** Eigene Geräte-Kennung für den Jam (zufällig, bleibt erhalten). */
    var deviceId: String = ""
        private set
    /** Lautstärke angeglichen: Pfad → „Methode:dB“ (Gesamtänderung gegenüber dem Original, für das Zurücknehmen). */
    var normInfo: Map<String, String> = emptyMap()
        private set
    /** Papierkorb: gelöschte Lieder (30 Tage wiederherstellbar). */
    var trash: List<TrashItem> = emptyList()
        private set
    /** Profil: eigener Name (leer = Gerätename) und Profilbild (0 = keins, sonst Zeitstempel). */
    var profileName: String = ""
        private set
    var profileStamp: Long = 0L
        private set
    /** Developer Option: Bearbeiten im Jam erlauben (Standard aus; wirkt nur, wenn alle im Jam es an haben). */
    var devJamEdit: Boolean = false
        private set
    /** Für andere im WLAN sichtbar (Jam-Einladungen). */
    var jamVisible: Boolean = true
        private set

    private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) { ensureFavorites(); return }
        try {
            val o = JSONObject(f.readText())
            playlists = o.optJSONArray("playlists").mapObjects { p ->
                Playlist(
                    p.getString("id"), p.getString("name"), p.optJSONArray("tracks").strings(),
                    p.optBoolean("pinned", false), p.optString("desc", ""), p.optLong("cover", 0L),
                    p.optLong("lastAdded", 0L), p.optBoolean("audio", false)
                )
            }
            folderArtists = o.optJSONObject("folderArtists").toMap()
            trackArtists = o.optJSONObject("trackArtists").toMap()
            sleepMinutes = o.optInt("sleepMinutes", 30)
            fadeMinutes = o.optInt("fadeMinutes", 30)
            artistGrid = o.optBoolean("artistGrid", true)
            albumGrid = o.optBoolean("albumGrid", true)
            eqAsked = o.optBoolean("eqAsked", false)
            sortBy = try { SortBy.valueOf(o.optString("sortBy", "Title")) } catch (e: Exception) { SortBy.Title }
            sortLevel = o.optInt("sortLevel", 0)
            knownInit = o.optBoolean("knownInit", false)
            playlistGrid = o.optBoolean("playlistGrid", false)
            audiobookGrid = o.optBoolean("audiobookGrid", false)
            showAlbum = o.optBoolean("showAlbum", false)
            blockedTracks = o.optJSONArray("blockedTracks").strings().toHashSet()
            blockedArtists = o.optJSONArray("blockedArtists").strings().toHashSet()
            blockedFolders = o.optJSONArray("blockedFolders").strings().toHashSet()
            softBlockedTracks = o.optJSONArray("softBlockedTracks").strings().toHashSet()
            softBlockedArtists = o.optJSONArray("softBlockedArtists").strings().toHashSet()
            softBlockedFolders = o.optJSONArray("softBlockedFolders").strings().toHashSet()
            playCounts = o.optJSONObject("playCounts").toMap().mapValues { it.value.toIntOrNull() ?: 0 }
            stripStrings = o.optJSONArray("stripStrings").strings()
            recentSearches = o.optJSONArray("recent").strings()
            favArtists = o.optJSONArray("favArtists").strings()
            rowScale = o.optDouble("rowScale2", 1.0).toFloat()
            resumePath = o.optString("resumePath", "")
            resumeContext = o.optString("resumeCtx", "")
            resumePosMs = o.optLong("resumePos", 0L)
            deviceId = o.optString("deviceId", "")
            jamVisible = o.optBoolean("jamVisible", true)
            profileName = o.optString("profileName", "")
            profileStamp = o.optLong("profileStamp", 0L)
            devJamEdit = o.optBoolean("devJamEdit", false)
            normInfo = o.optJSONObject("normInfo").toMap()
            trash = o.optJSONArray("trash")?.let { a -> List(a.length()) { TrashItem.from(a.getJSONObject(it)) } }.orEmpty()
            Names.extraStrip = stripStrings
            skipSilence = o.optBoolean("skipSilence", true)
            modes = o.optJSONObject("modes").toMap()
            silenceThr = o.optInt("silenceThr", 200)
            silenceMinMs = o.optInt("silenceMinMs", 1500)
            moveOnAssign = o.optBoolean("moveOnAssign", true)
            quickMove = o.optBoolean("quickMove", true)
            silenceKeepMs = o.optInt("silenceKeepMs", 300)
            silenceTailMs = o.optInt("silenceTailMs", 1000)
            trims = o.optJSONObject("trims").toMap()
            silenceStartSec = o.optInt("silenceStartSec", 3)
            silenceEndSec = o.optInt("silenceEndSec", 6)
            userNamed = o.optJSONArray("userNamed").strings().toHashSet()
            trackAlbums = o.optJSONObject("trackAlbums").toMap()
            trackCovers = o.optJSONObject("trackCovers").toMap()
            artistCovers = o.optJSONObject("artistCovers").toMap()
            playlistPlays = o.optJSONObject("playlistPlays").toMap().mapValues { it.value.toIntOrNull() ?: 0 }
            knownPaths = o.optJSONArray("known").strings().toHashSet()
        } catch (e: Exception) {
            // kaputte Datei ignorieren, mit leeren Daten weitermachen
        }
        ensureFavorites()
    }

    private fun ensureFavorites() {
        if (playlists.none { it.id == FAV_ID }) playlists = listOf(Playlist(FAV_ID, FAV_NAME, emptyList())) + playlists
    }

    /** Herz an/aus. Neue Favoriten kommen nach vorn. Gibt zurück, ob das Lied jetzt ein Favorit ist. */
    private val aliases = HashMap<String, String>()
    @Synchronized fun resolve(path: String): String = aliases[path] ?: path

    @Synchronized
    fun toggleFavorite(ctx: Context, rawPath: String): Boolean {
        val path = resolve(rawPath)
        val fav = playlists.firstOrNull { it.id == FAV_ID } ?: return false
        val now = path !in fav.trackPaths
        playlists = playlists.map { p ->
            if (p.id != FAV_ID) p
            else p.copy(trackPaths = if (now) listOf(path) + p.trackPaths else p.trackPaths.filter { it != path })
        }
        save(ctx)
        return now
    }

    @Synchronized
    private fun save(ctx: Context) {
        val o = JSONObject()
        o.put("playlists", JSONArray().also { arr ->
            for (p in playlists) arr.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("pinned", p.pinned).put("desc", p.description).put("cover", p.coverStamp)
                    .put("lastAdded", p.lastAdded).put("audio", p.audio)
                    .put("tracks", JSONArray().also { t -> p.trackPaths.forEach { t.put(it) } })
            )
        })
        o.put("folderArtists", JSONObject(folderArtists))
        o.put("trackArtists", JSONObject(trackArtists))
        o.put("sleepMinutes", sleepMinutes)
        o.put("fadeMinutes", fadeMinutes)
        o.put("artistGrid", artistGrid)
        o.put("albumGrid", albumGrid)
        o.put("eqAsked", eqAsked)
        o.put("sortBy", sortBy.name)
        o.put("sortLevel", sortLevel)
        o.put("knownInit", knownInit)
        o.put("playlistGrid", playlistGrid)
        o.put("audiobookGrid", audiobookGrid)
        o.put("showAlbum", showAlbum)
        o.put("blockedTracks", JSONArray(blockedTracks.toList()))
        o.put("blockedArtists", JSONArray(blockedArtists.toList()))
        o.put("blockedFolders", JSONArray(blockedFolders.toList()))
        o.put("softBlockedTracks", JSONArray(softBlockedTracks.toList()))
        o.put("softBlockedArtists", JSONArray(softBlockedArtists.toList()))
        o.put("softBlockedFolders", JSONArray(softBlockedFolders.toList()))
        o.put("playCounts", JSONObject(playCounts.mapValues { it.value.toString() }))
        o.put("stripStrings", JSONArray(stripStrings))
        o.put("skipSilence", skipSilence)
        o.put("modes", JSONObject(modes))
        o.put("silenceThr", silenceThr)
        o.put("silenceTailMs", silenceTailMs)
        o.put("trims", JSONObject(trims))
        o.put("silenceMinMs", silenceMinMs)
        o.put("moveOnAssign", moveOnAssign)
        o.put("quickMove", quickMove)
        o.put("silenceKeepMs", silenceKeepMs)
        o.put("silenceStartSec", silenceStartSec)
        o.put("silenceEndSec", silenceEndSec)
        o.put("userNamed", JSONArray(userNamed.toList()))
        o.put("trackAlbums", JSONObject(trackAlbums))
        o.put("trackCovers", JSONObject(trackCovers))
        o.put("artistCovers", JSONObject(artistCovers))
        o.put("playlistPlays", JSONObject(playlistPlays.mapValues { it.value.toString() }))
        o.put("recent", JSONArray(recentSearches))
        o.put("favArtists", JSONArray(favArtists))
        o.put("rowScale2", rowScale.toDouble())
        o.put("resumePath", resumePath)
        o.put("resumeCtx", resumeContext)
        o.put("resumePos", resumePosMs)
        o.put("deviceId", deviceId)
        o.put("jamVisible", jamVisible)
        o.put("profileName", profileName)
        o.put("profileStamp", profileStamp)
        o.put("devJamEdit", devJamEdit)
        o.put("normInfo", JSONObject(normInfo))
        o.put("trash", JSONArray().also { a -> trash.forEach { a.put(it.toJson()) } })
        o.put("known", JSONArray().also { a -> knownPaths.forEach { a.put(it) } })
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    // ---------- Playlists ----------

    /** Reihenfolge in Auswahl und Übersicht: Lieblingssongs, dann fixierte, dann nach zuletzt Hinzugefügtem. */
    fun ordered(list: List<Playlist>): List<Playlist> {
        val rest = list.filter { it.id != FAV_ID }
        return list.filter { it.id == FAV_ID } +
            rest.filter { it.pinned } +
            rest.filter { !it.pinned }.sortedByDescending { maxOf(it.lastAdded, it.id.toLongOrNull() ?: 0L) }
    }

    /** Wie oft ein Lied aus dieser Playlist gestartet wurde. */
    var playlistPlays: Map<String, Int> = emptyMap()
        private set

    fun incPlaylistPlay(ctx: Context, id: String) {
        playlistPlays = playlistPlays + (id to ((playlistPlays[id] ?: 0) + 1))
        save(ctx)
    }

    /** Für den Playlist-Reiter: Lieblingssongs, fixierte, dann nach Häufigkeit des Hörens. */
    fun orderedByUse(list: List<Playlist>): List<Playlist> {
        val rest = list.filter { it.id != FAV_ID }
        return list.filter { it.id == FAV_ID } +
            rest.filter { it.pinned } +
            rest.filter { !it.pinned }.sortedWith(
                compareByDescending<Playlist> { playlistPlays[it.id] ?: 0 }.thenByDescending { maxOf(it.lastAdded, it.id.toLongOrNull() ?: 0L) }
            )
    }

    fun setPinned(ctx: Context, id: String, pinned: Boolean) {
        playlists = playlists.map { if (it.id == id) it.copy(pinned = pinned) else it }
        save(ctx)
    }

    fun editPlaylist(ctx: Context, id: String, name: String, description: String) {
        playlists = playlists.map {
            if (it.id == id) it.copy(name = if (id == FAV_ID || name.isBlank()) it.name else name.trim(), description = description.trim()) else it
        }
        save(ctx)
    }

    fun setCoverStamp(ctx: Context, id: String, stamp: Long) {
        playlists = playlists.map { if (it.id == id) it.copy(coverStamp = stamp) else it }
        save(ctx)
    }

    fun setAudio(ctx: Context, id: String, audio: Boolean) {
        playlists = playlists.map { if (it.id == id) it.copy(audio = audio) else it }
        save(ctx)
    }

    // ---------- Sperrliste ----------

    fun isBlocked(t: Track): Boolean =
        t.path in blockedTracks || t.folder in blockedFolders || (t.artist != UNKNOWN_ARTIST && t.artist in blockedArtists)

    fun isSoftBlocked(t: Track): Boolean =
        t.path in softBlockedTracks || t.folder in softBlockedFolders || (t.artist != UNKNOWN_ARTIST && t.artist in softBlockedArtists)

    fun softBlockTracks(ctx: Context, paths: Collection<String>) { softBlockedTracks = softBlockedTracks + paths; save(ctx) }
    fun softUnblockTracks(ctx: Context, paths: Collection<String>) { softBlockedTracks = softBlockedTracks - paths.toSet(); save(ctx) }
    fun softBlockArtist(ctx: Context, name: String) { softBlockedArtists = softBlockedArtists + name; save(ctx) }
    fun softUnblockArtist(ctx: Context, name: String) { softBlockedArtists = softBlockedArtists - name; save(ctx) }
    fun softBlockFolder(ctx: Context, path: String) { softBlockedFolders = softBlockedFolders + path; save(ctx) }
    fun softUnblockFolder(ctx: Context, path: String) { softBlockedFolders = softBlockedFolders - path; save(ctx) }

    fun promoteTrackToHard(ctx: Context, path: String) { softBlockedTracks = softBlockedTracks - path; blockedTracks = blockedTracks + path; save(ctx) }
    fun promoteArtistToHard(ctx: Context, name: String) { softBlockedArtists = softBlockedArtists - name; blockedArtists = blockedArtists + name; save(ctx) }
    fun promoteFolderToHard(ctx: Context, path: String) { softBlockedFolders = softBlockedFolders - path; blockedFolders = blockedFolders + path; save(ctx) }

    fun blockTracks(ctx: Context, paths: Collection<String>) { blockedTracks = blockedTracks + paths; save(ctx) }
    fun unblockTracks(ctx: Context, paths: Collection<String>) { blockedTracks = blockedTracks - paths.toSet(); softBlockedTracks = softBlockedTracks - paths.toSet(); save(ctx) }
    fun blockArtist(ctx: Context, name: String) { blockedArtists = blockedArtists + name; save(ctx) }
    fun unblockArtist(ctx: Context, name: String) { blockedArtists = blockedArtists - name; softBlockedArtists = softBlockedArtists - name; save(ctx) }
    fun blockFolder(ctx: Context, path: String) { blockedFolders = blockedFolders + path; save(ctx) }
    fun unblockFolder(ctx: Context, path: String) { blockedFolders = blockedFolders - path; softBlockedFolders = softBlockedFolders - path; save(ctx) }

    // ---------- Zähler, Filter, Suchverlauf ----------

    fun setArtistCover(ctx: Context, artist: String, file: String?) {
        artistCovers = if (file.isNullOrBlank()) artistCovers - artist else artistCovers + (artist to file)
        save(ctx)
    }

    fun setTrackCover(ctx: Context, paths: List<String>, file: String?) {
        trackCovers = if (file == null) trackCovers - paths.toSet() else trackCovers + paths.associateWith { file }
        save(ctx)
    }

    fun setAlbum(ctx: Context, paths: List<String>, album: String?) {
        trackAlbums = if (album.isNullOrBlank()) trackAlbums - paths.toSet() else trackAlbums + paths.associateWith { album.trim() }
        save(ctx)
    }

    fun setQuickMove(ctx: Context, on: Boolean) { quickMove = on; save(ctx) }
    fun setMoveOnAssign(ctx: Context, on: Boolean) { moveOnAssign = on; save(ctx) }
    fun setSkipSilence(ctx: Context, on: Boolean) { skipSilence = on; save(ctx) }

    fun setSilence(ctx: Context, thr: Int, leadMs: Int, tailMs: Int) {
        silenceThr = thr; silenceKeepMs = leadMs; silenceTailMs = tailMs
        save(ctx)
    }

    /** Ohne sofortiges Speichern (für den Hintergrund-Abgleich); danach [flushTrims] aufrufen. */
    fun setTrimLazy(path: String, value: String) { trims = trims + (path to value) }
    fun flushTrims(ctx: Context) = save(ctx)

    /** Stille-Messung eines Liedes verwerfen (z. B. nach Lautstärkeänderung der Datei). */
    fun clearTrim(ctx: Context, path: String) { if (path in trims) { trims = trims - path; save(ctx) } }

    fun setNormInfo(ctx: Context, path: String, value: String?) {
        normInfo = if (value == null) normInfo - path else normInfo + (path to value)
        save(ctx)
    }

    fun setTrim(ctx: Context, path: String, value: String) {
        trims = trims + (path to value)
        save(ctx)
    }

    /** Gespeicherter Modus eines Kontexts: (Shuffle, Wiederholen 0=aus 1=alle 2=eins). */
    fun modeOf(key: String): Pair<Boolean, Int> {
        val p = modes[key]?.split(":") ?: return false to 0
        return (p.getOrNull(0) == "1") to (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    /** Geräte-Kennung für den Jam; wird beim ersten Mal erzeugt und gespeichert. */
    @Synchronized
    fun ensureDeviceId(ctx: Context): String {
        load(ctx)
        if (deviceId.isBlank()) { deviceId = java.util.UUID.randomUUID().toString().replace("-", "").take(16); save(ctx) }
        return deviceId
    }

    fun setJamVisible(ctx: Context, on: Boolean) { jamVisible = on; save(ctx) }
    fun setProfileName(ctx: Context, name: String) { profileName = name.trim().take(40); save(ctx) }
    fun setProfileStamp(ctx: Context, stamp: Long) { profileStamp = stamp; save(ctx) }
    fun setDevJamEdit(ctx: Context, on: Boolean) { devJamEdit = on; save(ctx) }

    @Synchronized
    fun addTrash(ctx: Context, items: List<TrashItem>) { trash = items + trash; save(ctx) }

    @Synchronized
    fun removeTrash(ctx: Context, ids: Set<String>) { trash = trash.filter { it.id !in ids }; save(ctx) }

    /** Merkt sich, wo die Wiedergabe zuletzt stand. Fremdtitel (nicht in der Bibliothek) werden nie gespeichert. */
    fun setResume(ctx: Context, path: String, context: String, posMs: Long) {
        if (path == resumePath && context == resumeContext && kotlin.math.abs(posMs - resumePosMs) < 1000) return
        resumePath = path
        resumeContext = context
        resumePosMs = posMs
        save(ctx)
    }

    fun setMode(ctx: Context, key: String, shuffle: Boolean, repeat: Int) {
        modes = modes + (key to "${if (shuffle) 1 else 0}:$repeat")
        save(ctx)
    }

    fun incPlay(ctx: Context, path: String) {
        playCounts = playCounts + (path to ((playCounts[path] ?: 0) + 1))
        save(ctx)
    }

    fun setStripStrings(ctx: Context, list: List<String>) {
        stripStrings = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        Names.extraStrip = stripStrings
        save(ctx)
    }

    fun addRecentSearch(ctx: Context, q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = (listOf(t) + recentSearches.filter { !it.equals(t, true) }).take(5)
        save(ctx)
    }

    fun removeRecentSearch(ctx: Context, q: String) {
        recentSearches = recentSearches.filter { it != q }
        save(ctx)
    }

    fun setPlaylistGrid(ctx: Context, grid: Boolean, audio: Boolean = false) {
        if (audio) audiobookGrid = grid else playlistGrid = grid
        save(ctx)
    }

    fun setShowAlbum(ctx: Context, enabled: Boolean) {
        showAlbum = enabled
        save(ctx)
    }

    fun createPlaylist(ctx: Context, name: String, audio: Boolean = false): Playlist {
        val p = Playlist(System.currentTimeMillis().toString(), name.trim(), emptyList(), audio = audio)
        playlists = playlists + p
        save(ctx)
        return p
    }

    fun renamePlaylist(ctx: Context, id: String, name: String) {
        if (id == FAV_ID) return
        playlists = playlists.map { if (it.id == id) it.copy(name = name.trim()) else it }
        save(ctx)
    }

    fun deletePlaylist(ctx: Context, id: String) {
        if (id == FAV_ID) return
        playlists = playlists.filter { it.id != id }
        save(ctx)
    }

    /** Fügt Pfade hinzu. Mit [skipDuplicates] werden Lieder übersprungen, die schon in der Playlist sind. */
    @Synchronized
    fun addToPlaylist(ctx: Context, id: String, rawPaths: List<String>, skipDuplicates: Boolean = true) {
        val paths = rawPaths.map { resolve(it) }
        playlists = playlists.map { p ->
            if (p.id != id) p
            else if (skipDuplicates) {
                val have = p.trackPaths.toHashSet()
                val add = paths.filter { have.add(it) }
                if (add.isEmpty()) p else p.copy(trackPaths = p.trackPaths + add, lastAdded = System.currentTimeMillis())
            } else p.copy(trackPaths = p.trackPaths + paths, lastAdded = System.currentTimeMillis())
        }
        save(ctx)
    }

    /** Entfernt den Eintrag an Position [index] (ein Lied kann mehrfach vorkommen). */
    fun removeFromPlaylistAt(ctx: Context, id: String, index: Int) {
        playlists = playlists.map { p ->
            if (p.id == id && index in p.trackPaths.indices) p.copy(trackPaths = p.trackPaths.filterIndexed { i, _ -> i != index }) else p
        }
        save(ctx)
    }

    fun movePlaylistTrack(ctx: Context, id: String, from: Int, to: Int) {
        playlists = playlists.map { p ->
            if (p.id == id && from in p.trackPaths.indices && to in p.trackPaths.indices) {
                val l = p.trackPaths.toMutableList()
                l.add(to, l.removeAt(from))
                p.copy(trackPaths = l)
            } else p
        }
        save(ctx)
    }

    /** Nach Verschieben/Umbenennen von Dateien: Pfade in Playlists und Zuordnungen nachziehen. */
    @Synchronized
    fun remapPaths(ctx: Context, map: Map<String, String>) {
        if (map.isEmpty()) return
        // alte Pfade merken, damit schnelle Aktionen (z. B. Herz direkt nach „Name übernehmen“) nicht ins Leere laufen
        for ((k, v) in map) { aliases[k] = v }
        for (k in aliases.keys.toList()) { aliases[k] = map[aliases[k]] ?: aliases[k]!! }
        playlists = playlists.map { p -> p.copy(trackPaths = p.trackPaths.map { map[it] ?: it }) }
        trackArtists = trackArtists.mapKeys { map[it.key] ?: it.key }
        knownPaths = knownPaths.map { map[it] ?: it }.toHashSet()
        blockedTracks = blockedTracks.map { map[it] ?: it }.toHashSet()
        softBlockedTracks = softBlockedTracks.map { map[it] ?: it }.toHashSet()
        trackAlbums = trackAlbums.mapKeys { map[it.key] ?: it.key }
        trackCovers = trackCovers.mapKeys { map[it.key] ?: it.key }
        trims = trims.mapKeys { map[it.key] ?: it.key }
        normInfo = normInfo.mapKeys { map[it.key] ?: it.key }
        userNamed = userNamed.map { map[it] ?: it }.toHashSet() + map.values
        playCounts = playCounts.mapKeys { map[it.key] ?: it.key }
        save(ctx)
    }

    // ---------- Interpret-Zuordnung ----------

    fun setFolderArtist(ctx: Context, folder: String, artist: String?) {
        folderArtists = if (artist.isNullOrBlank()) folderArtists - folder else folderArtists + (folder to artist.trim())
        save(ctx)
    }

    fun setTrackArtist(ctx: Context, path: String, artist: String?) {
        trackArtists = if (artist.isNullOrBlank()) trackArtists - path else trackArtists + (path to artist.trim())
        save(ctx)
    }

    fun setTrackArtists(ctx: Context, paths: List<String>, artist: String?) {
        trackArtists = if (artist.isNullOrBlank()) trackArtists - paths.toSet() else trackArtists + paths.associateWith { artist.trim() }
        save(ctx)
    }

    // ---------- Einstellungen ----------

    fun setSleepMinutes(ctx: Context, minutes: Int) {
        sleepMinutes = minutes
        save(ctx)
    }

    fun setFadeMinutes(ctx: Context, minutes: Int) {
        fadeMinutes = minutes
        save(ctx)
    }

    fun setEqAsked(ctx: Context) {
        eqAsked = true
        save(ctx)
    }

    fun setRowScale(ctx: Context, v: Float) { rowScale = v; save(ctx) }

    fun toggleFavArtist(ctx: Context, name: String): Boolean {
        val on = name !in favArtists
        favArtists = if (on) favArtists + name else favArtists - name
        save(ctx)
        return on
    }

    fun setAlbumGrid(ctx: Context, grid: Boolean) { albumGrid = grid; save(ctx) }

    fun setArtistGrid(ctx: Context, grid: Boolean) {
        artistGrid = grid
        save(ctx)
    }

    fun setSort(ctx: Context, s: SortBy, level: Int) {
        sortBy = s
        sortLevel = level
        save(ctx)
    }

    /** Beim ersten Start gelten alle vorhandenen Lieder als gesehen. */
    fun initKnown(ctx: Context, paths: Collection<String>) {
        if (knownInit) return
        knownInit = true
        knownPaths = paths.toHashSet()
        save(ctx)
    }

    fun markSeen(ctx: Context, paths: Collection<String>) {
        knownPaths = knownPaths + paths
        save(ctx)
    }
}

private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>()
    for (i in 0 until length()) out.add(f(getJSONObject(i)))
    return out
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>()
    for (i in 0 until length()) out.add(getString(i))
    return out
}

private fun JSONObject?.toMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = HashMap<String, String>()
    for (k in keys()) out[k] = getString(k)
    return out
}
