package de.musikplayer.app

import java.text.Normalizer

/**
 * Bereinigt Liednamen nur für Anzeige und Sortierung – Dateien werden nie umbenannt.
 *
 * Führende Tracknummern werden entfernt, wenn sie eindeutig als solche erkennbar sind:
 *  - mit Trennzeichen:   "01 - Song", "1. Song", "01_Song", "01) Song", "(01) Song"
 *  - mit führender Null: "01 Song", "007 Song"
 *  - ohne Null und ohne Trennzeichen ("1 Song") nur, wenn die Mehrheit der Dateien im selben Ordner so beginnt
 * Namen wie "2Pac", "50 Cent", "10cc", "3 Doors Down", "1979" bleiben unangetastet.
 */
object Names {

    private val BRACKET = Regex("^\\s*[\\[(]\\d{1,3}[\\])]\\s*[.\\-_–—]*\\s*")
    private val WITH_SEP = Regex("^\\s*\\d{1,3}\\s*[.\\-_–—)]+\\s*")
    private val ZERO_PADDED = Regex("^\\s*0\\d{1,2}\\s+")
    private val PLAIN = Regex("^\\s*\\d{1,3}\\s+(?=\\S)")
    private val SPACES = Regex("\\s+")
    private val MARKS = Regex("\\p{Mn}+")

    /** Beginnt der Dateiname mit „<Zahl> <Text>“? Dient der Ordner-Mehrheitsprüfung. */
    fun looksNumbered(fileNameNoExt: String): Boolean = PLAIN.containsMatchIn(fileNameNoExt)

    /** Sind in diesem Ordner die meisten Dateien „<Zahl> <Text>“? (mind. 3 Dateien, mind. 60 %) */
    fun folderIsNumbered(fileNames: List<String>): Boolean {
        if (fileNames.size < 3) return false
        val n = fileNames.count { looksNumbered(it) }
        return n * 10 >= fileNames.size * 6
    }

    fun clean(raw: String, artist: String? = null, folderNumbered: Boolean = false): String {
        var s = tidy(raw).trim()
        while (EXT.containsMatchIn(s)) s = s.replace(EXT, "").trim()
        val artistStartsWithDigit = !artist.isNullOrBlank() && artist.first().isDigit()
        val protectedByArtist = artistStartsWithDigit && s.startsWith(artist!!, ignoreCase = true)
        if (!protectedByArtist) s = stripNumber(s, folderNumbered)
        s = s.replace('_', ' ').replace(SPACES, " ").trim().trim('-', '–', '—', ' ')
        s = collapseRepeat(stripArtistPrefix(s, artist))
        return if (s.isEmpty()) raw.trim() else s
    }

    /** Dateiendungen gehören nie in den Namen („Song.mp3“ → „Song“). */
    private val EXT = Regex("\\.(mp3|wav|flac|m4a|aac|ogg|oga|opus|wma|mp4|webm|mka|aiff?|amr|3gp)$", RegexOption.IGNORE_CASE)

    private val ARTIST_SEP = Regex("^\\s*[-–—:]\\s*")

    /** „Ice Cube - Titel“ bei Interpret „Ice Cube“ → „Titel“ (nur mit Trennstrich, und es muss etwas übrig bleiben). */
    private fun stripArtistPrefix(s: String, artist: String?): String {
        if (artist.isNullOrBlank() || artist == UNKNOWN_ARTIST) return s
        if (!s.startsWith(artist, ignoreCase = true)) return s
        val rest = s.substring(artist.length)
        val m = ARTIST_SEP.find(rest) ?: return s
        val r2 = rest.substring(m.value.length).trim()
        return if (r2.isBlank()) s else r2
    }

    private fun key(x: String) = fold(x).filter { it.isLetterOrDigit() }

    /** „Titel - Titel“ (die letzten beiden Teile sind bis auf Leerzeichen/Satzzeichen gleich) → „Titel“. */
    private fun collapseRepeat(s: String): String {
        val parts = s.split(" - ").map { it.trim() }
        if (parts.size < 2) return s
        val a = parts[parts.size - 2]
        val b = parts.last()
        return if (key(a).isNotEmpty() && key(a) == key(b)) (parts.dropLast(2) + b).joinToString(" - ") else s
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Interpreten aus [pool], die [name] ähnlich sind (andere Schreibweise, Tippfehler, „X feat. Y“). */
    fun similarArtists(name: String, pool: Collection<String>): List<String> {
        val k = key(name)
        if (k.length < 3) return emptyList()
        return pool.filter { o ->
            if (o == name || o == UNKNOWN_ARTIST) return@filter false
            val ok = key(o)
            if (ok.isEmpty()) return@filter false
            ok == k ||
                (minOf(k.length, ok.length) >= 4 && (ok.contains(k) || k.contains(ok))) ||
                editDistance(k, ok) <= (if (maxOf(k.length, ok.length) >= 8) 2 else 1)
        }.distinct().take(3)
    }

    private fun stripNumber(s: String, folderNumbered: Boolean): String {
        if (s.all { it.isDigit() || it.isWhitespace() }) return s // "1979", "22": das ist der Titel
        val rx = when {
            BRACKET.containsMatchIn(s) -> BRACKET
            WITH_SEP.containsMatchIn(s) -> WITH_SEP
            ZERO_PADDED.containsMatchIn(s) -> ZERO_PADDED
            folderNumbered && PLAIN.containsMatchIn(s) -> PLAIN
            else -> return s
        }
        val rest = s.replaceFirst(rx, "")
        return if (rest.isBlank()) s else rest
    }

    fun cleanArtist(raw: String): String = tidy(raw).replace('_', ' ').replace(SPACES, " ").trim()

    private val INVISIBLE = Regex("[\\u200B-\\u200D\\u2060\\uFEFF]")
    private val UNI_SPACE = Regex("[\\p{Zs}\\u0085]")
    private val BAD_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")

    /** Unsichtbare Zeichen entfernen, geschützte/Sonderleerzeichen zu normalen Leerzeichen machen (sonst greifen die Regeln nicht). */
    fun tidy(s: String): String = s.replace(INVISIBLE, "").replace(UNI_SPACE, " ")

    /** Anfangsbuchstabe für die Buchstabenleiste: A–Z, alles andere (Zahlen, Zeichen) unter „#“. */
    fun letterOf(name: String): String {
        val c = fold(name).firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
    }

    /** Dateiname (ohne Endung) aus einem Titel: Zeichen, die Android in Dateinamen nicht erlaubt, werden zu „-“. */
    fun fileBaseName(title: String): String =
        title.replace(BAD_FILE_CHARS, "-").replace(SPACES, " ").trim().trimEnd('.').ifEmpty { "_" }

    /** Kleinschreibung ohne Akzente – für Sortierung und Suche. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(MARKS, "")
}
