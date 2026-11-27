package de.musikplayer.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Duplikaterkennung (Vorlage: Dateimanager): ähnlicher Name + ähnliche Dateigröße + gleiche Länge.
 * Rein über Metadaten, es wird nichts gelesen oder gelöscht.
 */
object Dupes {

    private const val NAME_SIMILARITY = 0.85
    private const val SIZE_TOLERANCE = 0.10
    private const val DURATION_TOLERANCE_MS = 2000L

    private val SUFFIX = Regex("(\\s*\\(\\d+\\)|\\s*\\[\\d+\\]|[-_ ]\\d{1,2}|[-_ ]?(kopie|copy)(\\s*\\(\\d+\\))?)$", RegexOption.IGNORE_CASE)
    private val TRACK_NO = Regex("^\\d{1,3}\\s*[-_. ]+\\s*")
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^\\]]*\\]")

    /** Titel ohne Klammerzusätze, nur Buchstaben/Ziffern, ohne Akzente. */
    private fun coreTitle(title: String): String = Names.fold(title.replace(BRACKETS, " ")).filter { it.isLetterOrDigit() }

    /** Dateiname ohne Endung, ohne „(1)“, „- Kopie“, „_2“ und führende Titelnummer; nur Buchstaben/Ziffern. */
    fun normName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        var base = if (dot > 0) fileName.substring(0, dot) else fileName
        while (true) {
            val next = base.replace(SUFFIX, "").trimEnd()
            if (next == base || next.isEmpty()) break
            base = next
        }
        base = base.replace(TRACK_NO, "")
        return base.lowercase().filter { it.isLetterOrDigit() }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val m = max(a.length, b.length)
        if (m == 0) return 1.0
        if (abs(a.length - b.length).toDouble() / m > 0.2) return 0.0
        return 1.0 - levenshtein(a, b).toDouble() / m
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun sizeClose(a: Long, b: Long): Boolean {
        if (a == b) return true
        val m = max(a, b).toDouble()
        return m > 0 && abs(a - b) / m <= SIZE_TOLERANCE
    }

    fun find(tracks: List<Track>): List<DupGroup> {
        val list = tracks.sortedBy { it.durationMs }
        val names = list.map { normName(it.fileName) }
        val parent = IntArray(list.size) { it }
        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }
        for (i in list.indices) {
            if (names[i].length < 3) continue
            var j = i + 1
            while (j < list.size && list[j].durationMs - list[i].durationMs <= DURATION_TOLERANCE_MS) {
                if (list[i].path != list[j].path &&
                    sizeClose(list[i].size, list[j].size) &&
                    similarity(names[i], names[j]) >= NAME_SIMILARITY
                ) {
                    parent[root(j)] = root(i)
                }
                j++
            }
        }
        // Zweites Kriterium: gleicher Interpret + gleicher Titel (ohne Klammern wie „feat. …“), Länge ähnlich.
        // Fängt auch Lieder, die erst durch die Namens-Bereinigung gleich heißen oder leicht unterschiedlich lang sind.
        val byKey = HashMap<String, MutableList<Int>>()
        for (i in list.indices) {
            val t = list[i]
            if (t.artist == UNKNOWN_ARTIST) continue
            val core = coreTitle(t.title)
            if (core.length < 3) continue
            byKey.getOrPut(t.artistKey + "|" + core) { ArrayList() }.add(i)
        }
        for (idx in byKey.values) {
            for (a in 0 until idx.size - 1) {
                val x = list[idx[a]]
                val y = list[idx[a + 1]]
                val tol = max(6000L, (max(x.durationMs, y.durationMs) * 0.03).toLong())
                if (abs(x.durationMs - y.durationMs) <= tol) parent[root(idx[a + 1])] = root(idx[a])
            }
        }
        val groups = HashMap<Int, MutableList<Track>>()
        for (i in list.indices) groups.getOrPut(root(i)) { ArrayList() }.add(list[i])
        return groups.values.filter { it.size >= 2 }
            .map { g ->
                val o = order(g)
                val albums = o.map { it.albumName }
                val variant = albums.all { it.isNotBlank() } && albums.map { Names.fold(it) }.toSet().size == o.size
                val ident = o.all { sizeClose(it.size, o[0].size) && abs(it.durationMs - o[0].durationMs) <= DURATION_TOLERANCE_MS }
                DupGroup(
                    o, o.all { it.size == o[0].size },
                    if (variant) "Gleiches Lied auf verschiedenen Alben"
                    else if (ident) "Name, Größe und Länge passen" else "Gleicher Titel und Interpret, Länge ähnlich",
                    variant
                )
            }
            .sortedWith(compareBy<DupGroup> { it.albumVariant }.thenByDescending { it.sameSize }.thenByDescending { it.tracks[0].size * it.tracks.size })
    }

    /** Dieselben Kriterien wie [find], für ein einzelnes Paar (z. B. ein Lied aus einem Jam gegen die eigene Bibliothek). */
    fun same(a: Track, b: Track): Boolean {
        val d = abs(a.durationMs - b.durationMs)
        if (d <= DURATION_TOLERANCE_MS && sizeClose(a.size, b.size)) {
            val na = normName(a.fileName)
            if (na.length >= 3 && similarity(na, normName(b.fileName)) >= NAME_SIMILARITY) return true
        }
        if (a.artist != UNKNOWN_ARTIST && a.artistKey == b.artistKey) {
            val ca = coreTitle(a.title)
            if (ca.length >= 3 && ca == coreTitle(b.title)) {
                val tol = max(6000L, (max(a.durationMs, b.durationMs) * 0.03).toLong())
                if (d <= tol) return true
            }
        }
        return false
    }

    /** Schneller Abgleich vieler Lieder gegen eine Bibliothek: Vorfilter über die Länge, dann [same]. */
    class Matcher(pool: List<Track>) {
        private val sorted = pool.sortedBy { it.durationMs }
        private val durs = LongArray(sorted.size) { sorted[it].durationMs }

        fun find(t: Track): Track? {
            val tol = max(6000L, (t.durationMs * 0.03).toLong())
            var i = java.util.Arrays.binarySearch(durs, t.durationMs - tol).let { if (it < 0) -it - 1 else it }
            while (i > 0 && durs[i - 1] >= t.durationMs - tol) i--
            while (i < sorted.size && durs[i] <= t.durationMs + tol) {
                if (same(t, sorted[i])) return sorted[i]
                i++
            }
            return null
        }
    }

    /** Erster Eintrag = Vorschlag zum Behalten: größte Datei (beste Qualität), Original statt „(1)“-Kopie, ältere zuerst. */
    private fun order(g: List<Track>): List<Track> = g.sortedWith(
        compareByDescending<Track> { it.size }
            .thenBy { if (SUFFIX.containsMatchIn(it.fileName.substringBeforeLast('.'))) 1 else 0 }
            .thenBy { it.modified }
    )
}
