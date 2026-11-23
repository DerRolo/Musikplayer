package de.musikplayer.app

import java.io.File

/** Ein Lied aus der Medien-Datenbank. [artist] ist der wirksame Interpret (nach Zuordnung). */
data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val tagArtist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    val modified: Long,
    /** true, wenn der Interpret von dir zugeordnet wurde (Ordner- oder Titel-Zuordnung). */
    val manual: Boolean = false,
    /** Zum Sortieren und Suchen: ohne Akzente, kleingeschrieben. */
    val sortKey: String = Names.fold(title),
    val artistKey: String = Names.fold(artist)
) {
    val folder: String get() = File(path).parent ?: ""
    val fileName: String get() = File(path).name
}

data class Playlist(
    val id: String,
    val name: String,
    val trackPaths: List<String>,
    val pinned: Boolean = false,
    val description: String = "",
    /** 0 = kein eigenes Bild, sonst Zeitstempel (damit sich das Bild neu lädt, wenn es ersetzt wird). */
    val coverStamp: Long = 0L,
    /** Wann zuletzt ein Lied hinzugefügt wurde (für die Reihenfolge in der Auswahl). */
    val lastAdded: Long = 0L,
    /** Hörbuch/Podcast-Liste statt Musik-Playlist. */
    val audio: Boolean = false
)

/** Gruppe von Titeln, die vermutlich dasselbe Lied sind. */
data class DupGroup(val tracks: List<Track>, val sameSize: Boolean, val reason: String = "")

/** Vorschlag: Ordner ohne eindeutigen Interpret im Tag. */
data class FolderInfo(
    val path: String,
    val name: String,
    val count: Int,
    val unknownCount: Int,
    val suggestion: String?,
    val assigned: String?
)

enum class SortBy(val label: String) { Title("Titel"), Artist("Interpret"), Added("Hinzugefügt"), Duration("Länge"), Plays("Meistgehört") }

enum class RepeatMode { Off, All, One }

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}

fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return String.format("%.1f %s", v, units[i])
}
