package de.musikplayer.app

import org.json.JSONArray
import org.json.JSONObject

/*
 * Datenformate für den Jam (Austausch zwischen Handys im selben WLAN, als JSON über HTTP).
 * Lieder werden über „Besitzer-Gerät + Lied-ID des Besitzers“ angesprochen. URLs baut jedes Gerät selbst
 * aus der Mitgliederliste, damit niemand fremde Adressen weiterreichen muss.
 */

/** Ein Lied aus der Bibliothek eines anderen Geräts. */
data class RemoteTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val albumName: String,
    val durationMs: Long,
    val size: Long,
    val fileName: String,
    /** true = Interpret wurde dort von Hand zugeordnet (nicht aus dem Tag). */
    val manual: Boolean,
    /** true = dort ist ein eigenes Cover gesetzt (wird beim Übernehmen mitgeladen). */
    val customCover: Boolean
) {
    /** Als Track-Objekt nur für den Duplikat-Abgleich (Pfad ist ein Platzhalter). */
    fun asProbe(): Track = Track(
        id = -id - 1, path = "/jam/$id/$fileName", title = title, artist = artist, tagArtist = artist,
        album = albumName, albumId = 0L, durationMs = durationMs, size = size, modified = 0L, albumName = albumName
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("t", title).put("a", artist).put("al", albumName).put("d", durationMs)
        .put("s", size).put("f", fileName).put("m", manual).put("c", customCover)

    companion object {
        fun from(o: JSONObject) = RemoteTrack(
            o.getLong("id"), o.optString("t"), o.optString("a", UNKNOWN_ARTIST), o.optString("al"), o.optLong("d"),
            o.optLong("s"), o.optString("f"), o.optBoolean("m"), o.optBoolean("c")
        )

        fun of(t: Track) = RemoteTrack(
            t.id, t.title, t.artist, t.albumName, t.durationMs, t.size, t.fileName, t.manual, t.path in Store.trackCovers
        )
    }
}

data class RemotePlaylist(val id: String, val name: String, val audio: Boolean, val favorites: Boolean, val trackIds: List<Long>) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("au", audio).put("fav", favorites)
        .put("t", JSONArray().also { a -> trackIds.forEach { a.put(it) } })

    companion object {
        fun from(o: JSONObject): RemotePlaylist {
            val arr = o.optJSONArray("t") ?: JSONArray()
            return RemotePlaylist(o.getString("id"), o.optString("n"), o.optBoolean("au"), o.optBoolean("fav"), List(arr.length()) { arr.getLong(it) })
        }
    }
}

data class RemoteLibrary(val ownerId: String, val ownerName: String, val tracks: List<RemoteTrack>, val playlists: List<RemotePlaylist>) {
    val byId: Map<Long, RemoteTrack> by lazy { tracks.associateBy { it.id } }

    fun toJson(): JSONObject = JSONObject().put("owner", ownerId).put("name", ownerName)
        .put("tracks", JSONArray().also { a -> tracks.forEach { a.put(it.toJson()) } })
        .put("playlists", JSONArray().also { a -> playlists.forEach { a.put(it.toJson()) } })

    companion object {
        fun from(o: JSONObject): RemoteLibrary {
            val t = o.optJSONArray("tracks") ?: JSONArray()
            val p = o.optJSONArray("playlists") ?: JSONArray()
            return RemoteLibrary(
                o.optString("owner"), o.optString("name"),
                List(t.length()) { RemoteTrack.from(t.getJSONObject(it)) },
                List(p.length()) { RemotePlaylist.from(p.getJSONObject(it)) }
            )
        }
    }
}

/** Ein Eintrag in der gemeinsamen Warteschlange: welches Lied von welchem Gerät. */
data class JamEntry(val owner: String, val trackId: Long, val title: String, val artist: String, val durationMs: Long) {
    val key: String get() = "$owner/$trackId"

    fun toJson(): JSONObject = JSONObject().put("o", owner).put("id", trackId).put("t", title).put("a", artist).put("d", durationMs)

    companion object {
        fun from(o: JSONObject) = JamEntry(o.getString("o"), o.getLong("id"), o.optString("t"), o.optString("a"), o.optLong("d"))
        fun of(owner: String, t: Track) = JamEntry(owner, t.id, t.title, t.artist, t.durationMs)
        fun of(owner: String, t: RemoteTrack) = JamEntry(owner, t.id, t.title, t.artist, t.durationMs)
        fun list(arr: JSONArray?): List<JamEntry> = if (arr == null) emptyList() else List(arr.length()) { from(arr.getJSONObject(it)) }
        fun array(list: List<JamEntry>) = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
    }
}

/** Ein Mitglied des Jams. [url] ist leer für den Host (Gäste kennen seine Adresse schon). */
data class JamMember(val id: String, val name: String, val url: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", name).put("u", url)
    companion object { fun from(o: JSONObject) = JamMember(o.getString("id"), o.optString("n"), o.optString("u")) }
}

/** Was der Host gerade spielt – Gäste spielen danach synchron mit. */
data class JamState(
    val jamId: String,
    val hostId: String,
    val hostName: String,
    val members: List<JamMember>,
    val current: JamEntry?,
    val upcoming: List<JamEntry>,
    val playing: Boolean,
    val positionMs: Long,
    /** Host-Uhrzeit, zu der [positionMs] gemessen wurde. */
    val snapTime: Long,
    /** Host-Uhrzeit beim Beantworten der Anfrage (für den Uhren-Abgleich). */
    val hostTime: Long,
    val shuffle: Boolean,
    val repeat: Int,
    /** Welches Mitglied gerade abspielt (die eine gemeinsame Ausgabe). */
    val speaker: String = "",
    /** Alle verbundenen Ausgabegeräte aller Mitglieder – eines davon ist die gemeinsame Ausgabe. */
    val outputs: List<JamOutput> = emptyList(),
    /** „<Mitglied>/<Geräte-ID>“ der gewählten bzw. tatsächlich genutzten Ausgabe. */
    val outSelected: String = "",
    val outActive: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("jam", jamId).put("host", hostId).put("hostName", hostName)
        .put("members", JSONArray().also { a -> members.forEach { a.put(it.toJson()) } })
        .put("cur", current?.toJson() ?: JSONObject.NULL)
        .put("next", JamEntry.array(upcoming))
        .put("playing", playing).put("pos", positionMs).put("snap", snapTime).put("now", hostTime)
        .put("shuffle", shuffle).put("repeat", repeat)
        .put("speaker", speaker)
        .put("outs", JSONArray().also { a -> outputs.forEach { a.put(it.toJson()) } })
        .put("outSel", outSelected).put("outAct", outActive)

    companion object {
        fun from(o: JSONObject): JamState {
            val m = o.optJSONArray("members") ?: JSONArray()
            val outs = o.optJSONArray("outs") ?: JSONArray()
            return JamState(
                o.optString("jam"), o.optString("host"), o.optString("hostName"),
                List(m.length()) { JamMember.from(m.getJSONObject(it)) },
                o.optJSONObject("cur")?.let { JamEntry.from(it) },
                JamEntry.list(o.optJSONArray("next")),
                o.optBoolean("playing"), o.optLong("pos"), o.optLong("snap"), o.optLong("now"),
                o.optBoolean("shuffle"), o.optInt("repeat"),
                o.optString("speaker"),
                List(outs.length()) { i -> JamOutput.from(outs.getJSONObject(i)) },
                o.optString("outSel"), o.optString("outAct")
            )
        }
    }
}

/** Ein anderes Gerät mit der App im selben WLAN. */
data class JamPeer(val id: String, val name: String, val url: String, val hostingJam: String?)


/** Ein Ausgabegerät irgendeines Mitglieds (Handy-Lautsprecher, Bluetooth-Box …). */
data class JamOutput(val member: String, val memberName: String, val id: Int, val name: String, val kind: String) {
    val key: String get() = "$member/$id"
    fun toJson(): JSONObject = JSONObject().put("m", member).put("mn", memberName).put("id", id).put("n", name).put("k", kind)
    companion object {
        fun from(o: JSONObject) = JamOutput(o.getString("m"), o.optString("mn"), o.getInt("id"), o.optString("n"), o.optString("k"))
        fun of(member: String, memberName: String, d: OutputDev) = JamOutput(member, memberName, d.id, d.name, d.kind)
    }
}

/** Wiedergabe-Stand eines Geräts („Engine“): was es spielt und welche Ausgabegeräte es hat. */
data class EngineInfo(
    val outputs: List<OutputDev>,
    val selected: Int,
    val active: Int,
    val current: JamEntry?,
    val upcoming: List<JamEntry>,
    val playing: Boolean,
    /** Position im ganzen Lied (ohne Stille-Schnitt). */
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: Int,
    /** Host-Uhrzeit, zu der [positionMs] galt (wird vom Host beim Empfang gesetzt). */
    val snapTime: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("outs", JSONArray().also { a -> outputs.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("k", it.kind)) } })
        .put("sel", selected).put("act", active)
        .put("cur", current?.toJson() ?: JSONObject.NULL).put("next", JamEntry.array(upcoming))
        .put("playing", playing).put("pos", positionMs).put("shuffle", shuffle).put("repeat", repeat)

    companion object {
        fun from(o: JSONObject, snapTime: Long): EngineInfo {
            val outs = o.optJSONArray("outs") ?: JSONArray()
            return EngineInfo(
                List(outs.length()) { i -> outs.getJSONObject(i).let { OutputDev(it.getInt("id"), it.optString("n"), it.optString("k")) } },
                o.optInt("sel", Output.AUTO), o.optInt("act", Output.AUTO),
                o.optJSONObject("cur")?.let { JamEntry.from(it) }, JamEntry.list(o.optJSONArray("next")),
                o.optBoolean("playing"), o.optLong("pos"), o.optBoolean("shuffle"), o.optInt("repeat"), snapTime
            )
        }
    }
}
