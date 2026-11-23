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
    var blockedTracks: Set<String> = emptySet()
        private set
    var blockedArtists: Set<String> = emptySet()
        private set
    var blockedFolders: Set<String> = emptySet()
        private set
    /** Wie oft ein Lied gestartet wurde (für „Meistgehört“). */
    var playCounts: Map<String, Int> = emptyMap()
        private set
    var stripStrings: List<String> = emptyList()
        private set
    var recentSearches: List<String> = emptyList()
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
            eqAsked = o.optBoolean("eqAsked", false)
            sortBy = try { SortBy.valueOf(o.optString("sortBy", "Title")) } catch (e: Exception) { SortBy.Title }
            sortLevel = o.optInt("sortLevel", 0)
            knownInit = o.optBoolean("knownInit", false)
            playlistGrid = o.optBoolean("playlistGrid", false)
            blockedTracks = o.optJSONArray("blockedTracks").strings().toHashSet()
            blockedArtists = o.optJSONArray("blockedArtists").strings().toHashSet()
            blockedFolders = o.optJSONArray("blockedFolders").strings().toHashSet()
            playCounts = o.optJSONObject("playCounts").toMap().mapValues { it.value.toIntOrNull() ?: 0 }
            stripStrings = o.optJSONArray("stripStrings").strings()
            recentSearches = o.optJSONArray("recent").strings()
            Names.extraStrip = stripStrings
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
    fun toggleFavorite(ctx: Context, path: String): Boolean {
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
        o.put("eqAsked", eqAsked)
        o.put("sortBy", sortBy.name)
        o.put("sortLevel", sortLevel)
        o.put("knownInit", knownInit)
        o.put("playlistGrid", playlistGrid)
        o.put("blockedTracks", JSONArray(blockedTracks.toList()))
        o.put("blockedArtists", JSONArray(blockedArtists.toList()))
        o.put("blockedFolders", JSONArray(blockedFolders.toList()))
        o.put("playCounts", JSONObject(playCounts.mapValues { it.value.toString() }))
        o.put("stripStrings", JSONArray(stripStrings))
        o.put("playlistPlays", JSONObject(playlistPlays.mapValues { it.value.toString() }))
        o.put("recent", JSONArray(recentSearches))
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

    fun blockTracks(ctx: Context, paths: Collection<String>) { blockedTracks = blockedTracks + paths; save(ctx) }
    fun unblockTracks(ctx: Context, paths: Collection<String>) { blockedTracks = blockedTracks - paths.toSet(); save(ctx) }
    fun blockArtist(ctx: Context, name: String) { blockedArtists = blockedArtists + name; save(ctx) }
    fun unblockArtist(ctx: Context, name: String) { blockedArtists = blockedArtists - name; save(ctx) }
    fun blockFolder(ctx: Context, path: String) { blockedFolders = blockedFolders + path; save(ctx) }
    fun unblockFolder(ctx: Context, path: String) { blockedFolders = blockedFolders - path; save(ctx) }

    // ---------- Zähler, Filter, Suchverlauf ----------

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

    fun setPlaylistGrid(ctx: Context, grid: Boolean) {
        playlistGrid = grid
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
    fun addToPlaylist(ctx: Context, id: String, paths: List<String>, skipDuplicates: Boolean = true) {
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
    fun remapPaths(ctx: Context, map: Map<String, String>) {
        if (map.isEmpty()) return
        playlists = playlists.map { p -> p.copy(trackPaths = p.trackPaths.map { map[it] ?: it }) }
        trackArtists = trackArtists.mapKeys { map[it.key] ?: it.key }
        knownPaths = knownPaths.map { map[it] ?: it }.toHashSet()
        blockedTracks = blockedTracks.map { map[it] ?: it }.toHashSet()
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
