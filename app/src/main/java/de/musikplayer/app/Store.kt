package de.musikplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    var fadeSeconds: Int = 60
        private set
    var sortBy: SortBy = SortBy.Title
        private set

    private var loaded = false

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            playlists = o.optJSONArray("playlists").mapObjects { p ->
                Playlist(p.getString("id"), p.getString("name"), p.optJSONArray("tracks").strings())
            }
            folderArtists = o.optJSONObject("folderArtists").toMap()
            trackArtists = o.optJSONObject("trackArtists").toMap()
            sleepMinutes = o.optInt("sleepMinutes", 30)
            fadeSeconds = o.optInt("fadeSeconds", 60)
            sortBy = try { SortBy.valueOf(o.optString("sortBy", "Title")) } catch (e: Exception) { SortBy.Title }
        } catch (e: Exception) {
            // kaputte Datei ignorieren, mit leeren Daten weitermachen
        }
    }

    @Synchronized
    private fun save(ctx: Context) {
        val o = JSONObject()
        o.put("playlists", JSONArray().also { arr ->
            for (p in playlists) arr.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("tracks", JSONArray().also { t -> p.trackPaths.forEach { t.put(it) } })
            )
        })
        o.put("folderArtists", JSONObject(folderArtists))
        o.put("trackArtists", JSONObject(trackArtists))
        o.put("sleepMinutes", sleepMinutes)
        o.put("fadeSeconds", fadeSeconds)
        o.put("sortBy", sortBy.name)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    // ---------- Playlists ----------

    fun createPlaylist(ctx: Context, name: String): Playlist {
        val p = Playlist(System.currentTimeMillis().toString(), name.trim(), emptyList())
        playlists = playlists + p
        save(ctx)
        return p
    }

    fun renamePlaylist(ctx: Context, id: String, name: String) {
        playlists = playlists.map { if (it.id == id) it.copy(name = name.trim()) else it }
        save(ctx)
    }

    fun deletePlaylist(ctx: Context, id: String) {
        playlists = playlists.filter { it.id != id }
        save(ctx)
    }

    fun addToPlaylist(ctx: Context, id: String, paths: List<String>) {
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(trackPaths = p.trackPaths + paths.filter { it !in p.trackPaths }) else p
        }
        save(ctx)
    }

    fun removeFromPlaylist(ctx: Context, id: String, path: String) {
        playlists = playlists.map { p ->
            if (p.id == id) p.copy(trackPaths = p.trackPaths - path) else p
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

    // ---------- Einstellungen ----------

    fun setSleep(ctx: Context, minutes: Int, fade: Int) {
        sleepMinutes = minutes
        fadeSeconds = fade
        save(ctx)
    }

    fun setSort(ctx: Context, s: SortBy) {
        sortBy = s
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
