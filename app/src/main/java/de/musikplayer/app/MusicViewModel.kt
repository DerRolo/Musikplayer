package de.musikplayer.app

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(private val app: Application) : AndroidViewModel(app) {

    // ---------- Mediathek ----------
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    /** Suche zeigt Mediathek-Sperren weiterhin, aber vollständige Sperren bleiben ausgeblendet. */
    val searchableTracks: List<Track> get() = Library.allTracks.filter { !Store.isBlocked(it) }
    var loading by mutableStateOf(false)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var folders by mutableStateOf<List<FolderInfo>>(emptyList())
        private set
    var playlistGrid by mutableStateOf(false)
        private set
    var audiobookGrid by mutableStateOf(false)
        private set
    var sortLevel by mutableIntStateOf(0)
        private set
    var seenTick by mutableIntStateOf(0)
        private set
    var queuedCount by mutableIntStateOf(0)
        private set
    var sortBy by mutableStateOf(SortBy.Title)
        private set
    var message by mutableStateOf<String?>(null)
    /** Wenn gesetzt, zeigt die Meldung einen „Rückgängig“-Knopf. */
    var undo by mutableStateOf<(() -> Unit)?>(null)
    /** Zählt hoch, wenn sich Sperrliste/Filter/Verlauf ändern (damit Seiten neu lesen). */
    var blockTick by mutableIntStateOf(0)
        private set
    var artistGrid by mutableStateOf(true)
    var albumGrid by mutableStateOf(true)
        private set
    var favArtists by mutableStateOf<Set<String>>(emptySet())
    var onlyFavArtists by mutableStateOf(false)
    var rowScale by mutableStateOf(1.0f)
        private set
    var favoritePaths by mutableStateOf<Set<String>>(emptySet())
        private set

    // ---------- Duplikate ----------
    var dupes by mutableStateOf<List<DupGroup>?>(null)
        private set
    var dupBusy by mutableStateOf(false)
        private set
    /** false = nur echte Duplikate; true = auch „gleiches Lied auf verschiedenen Alben“. */
    var dupAlbums by mutableStateOf(false)
    fun visibleDupes(): List<DupGroup>? = dupes?.filter { dupAlbums || !it.albumVariant }

    // ---------- Wiedergabe ----------
    private var controller: MediaController? = null
    var current by mutableStateOf<MediaItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatMode.Off)
        private set
    /** Zählt hoch, wenn sich gespeicherte Shuffle-/Wiederholen-Modi ändern (damit Anzeigen neu lesen). */
    var modeVersion by mutableStateOf(0)
        private set
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var currentIndex by mutableStateOf(0)
        private set
    /** Das Lied, das als Nächstes bzw. davor käme (für die Vorschau beim Wischen); null = keins. */
    var nextItem by mutableStateOf<MediaItem?>(null)
        private set
    var prevItem by mutableStateOf<MediaItem?>(null)
        private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            prefetchNext()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prefetchNext()
            // Jede tatsächliche Media-Transition zählt als neuer Hörvorgang, auch Repeat 1.
            val id = mediaItem?.mediaId?.substringAfterLast(':')?.toLongOrNull() ?: return
            val ctxId = mediaItem?.mediaId.orEmpty().removePrefix("t:").substringBeforeLast(':')
            if (ctxId.startsWith("p:")) Store.incPlaylistPlay(app, ctxId.removePrefix("p:"))
            tracks.firstOrNull { it.id == id }?.let { Store.incPlay(app, it.path) }
        }
    }

    init {
        Store.load(app)
        sortBy = Store.sortBy
        sortLevel = Store.sortLevel
        artistGrid = Store.artistGrid
        albumGrid = Store.albumGrid
        favArtists = Store.favArtists.toSet()
        rowScale = Store.rowScale
        playlistGrid = Store.playlistGrid
        audiobookGrid = Store.audiobookGrid
        syncPlaylists()
        Jam.onLibraryChanged = { refresh() }
        Jam.onRemoteState = { s -> if (s != null) syncFromJam(s) else syncFromPlayer() }
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            try {
                controller = f.get().also { it.addListener(listener) }
                // erst hier: alle Felder des ViewModels sind jetzt sicher angelegt
                Jam.host = jamHost
                syncFromPlayer()
                applyMode(keyOf(playingContext))
                // Wurde die App über „Öffnen mit“ gestartet, bevor der Dienst verbunden war: jetzt abspielen
                pendingExternal?.let { pendingExternal = null; playExternal(it) }
            } catch (e: Exception) {
                message = "Wiedergabe-Dienst nicht erreichbar"
            }
        }, MoreExecutors.directExecutor())
        viewModelScope.launch {
            while (true) {
                if (jamRemote) { if (isPlaying) positionMs = Jam.estimatedPosition() }
                else controller?.let {
                    if (it.isPlaying) positionMs = it.currentPosition
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private fun syncFromPlayer() {
        // Als Gast im Jam zeigt der Player, was beim Host läuft (die einzige Ausgabe ist das Host-Handy)
        if (jamRemote) { syncFromJam(Jam.state); return }
        val c = controller ?: return
        current = c.currentMediaItem
        isPlaying = c.isPlaying
        positionMs = c.currentPosition
        durationMs = if (c.duration > 0) c.duration else 0L
        repeat = when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            else -> RepeatMode.Off
        }
        currentIndex = c.currentMediaItemIndex
        nextItem = c.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { c.getMediaItemAt(it) }
        prevItem = c.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { c.getMediaItemAt(it) }
        queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        var n = 0
        var i = currentIndex + 1
        while (i < queue.size && isQueued(queue[i])) { n++; i++ }
        queuedCount = n
    }

    // ---------- Laden ----------

    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Papierkorb: was älter als 30 Tage ist, endgültig löschen
                if (Trash.purgeOld(app) > 0) trashTick++
                Library.load(app)
            }
            Store.initKnown(app, Library.tracks.map { it.path })
            refreshFromLibrary()
            loading = false
            startTrimSync()
        }
    }

    private var trimSyncRunning = false

    /** Misst im Hintergrund alle noch nicht untersuchten Lieder aus, damit der Schnitt beim Abspielen sofort greift. */
    fun startTrimSync() {
        if (trimSyncRunning || !Store.skipSilence) return
        trimSyncRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var n = 0
                for (t in Library.tracks.toList()) {
                    if (!Store.skipSilence) break
                    if (Trim.known(t)) continue
                    Trim.ensure(app, t, lazy = true)
                    if (++n % 25 == 0) Store.flushTrims(app)
                    kotlinx.coroutines.yield()
                }
                if (n > 0) Store.flushTrims(app)
            } finally { trimSyncRunning = false }
        }
    }

    private fun refreshFromLibrary() {
        tracks = Library.tracks
        syncPlaylists()
        folders = Library.folders()
        sortBy = Store.sortBy
        sortLevel = Store.sortLevel
        syncPlayerMetadata()
    }

    /** Geänderte Titel/Interpreten (z. B. nach Umbenennen) in die laufende Warteschlange übernehmen; Wiedergabe läuft weiter. */
    private fun syncPlayerMetadata() {
        val c = controller ?: return
        val byId = Library.tracks.associateBy { it.id }
        val cur = c.currentMediaItemIndex
        val pos = c.currentPosition
        var replacedCurrent = false
        for (i in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
            val t = item.mediaId.substringAfterLast(':').toLongOrNull()?.let { byId[it] } ?: continue
            val md = item.mediaMetadata
            if (md.title?.toString() == t.title && md.artist?.toString() == t.artist) continue
            val nu = item.buildUpon()
                .setMediaMetadata(md.buildUpon().setTitle(t.title).setArtist(t.artist).build())
                .build()
            c.replaceMediaItem(i, nu)
            if (i == cur) replacedCurrent = true
        }
        if (replacedCurrent && c.currentMediaItemIndex == cur && c.currentPosition < pos - 2000) c.seekTo(pos)
        syncFromPlayer()
    }

    val sortedTracks: List<Track> get() = Library.sorted(tracks, sortBy, sortLevel)

    /** Mehrmals auf dieselbe Sortierung tippen: Standard → umgekehrt → keine Sortierung → wieder Standard. */
    fun cycleSort(s: SortBy) {
        val level = if (s == sortBy) (sortLevel + 1) % 3 else 0
        Store.setSort(app, s, level)
        sortBy = s
        sortLevel = level
    }

    // ---------- Neue Lieder ----------

    fun newTracks(): List<Track> = tracks.filter { it.path !in Store.knownPaths }

    fun existingArtists(): Set<String> = tracks.filter { it.path in Store.knownPaths && it.artist != UNKNOWN_ARTIST }.map { it.artist }.toSet()

    fun markSeen(list: List<Track>) {
        Store.markSeen(app, list.map { it.path })
        seenTick++
    }

    // ---------- Wiedergabe-Steuerung ----------

    /** Ursprüngliche Reihenfolge der zuletzt gestarteten Liste (um „Zufall aus“ wiederherzustellen). */
    private var baseList: List<MediaItem> = emptyList()

    /** Kontext des laufenden Titels: „p:<id>“ für Playlists/Hörbücher, sonst z. B. „all“. */
    val playContextKey: String get() = current?.mediaId?.removePrefix("t:")?.substringBeforeLast(':') ?: ""

    private fun keyOf(context: String?) = when {
        context == null -> "all"
        context.startsWith("p:") -> context
        // Von außen geöffnete Lieder haben eigene Shuffle-/Wiederholen-Einstellungen (Fremdtitel und Bibliothekstitel gleich)
        context == External.CONTEXT || context.startsWith(External.PREFIX) -> External.CONTEXT
        else -> "all"
    }

    fun shuffleFor(key: String): Boolean { modeVersion; return Store.modeOf(key).first }
    fun repeatFor(key: String): RepeatMode {
        modeVersion
        return when (Store.modeOf(key).second) { 1 -> RepeatMode.All; 2 -> RepeatMode.One; else -> RepeatMode.Off }
    }

    /** Gespeicherten Modus (Shuffle + Wiederholen) eines Kontexts auf den Player anwenden. */
    private fun applyMode(key: String) {
        val (s, r) = Store.modeOf(key)
        shuffle = s
        controller?.repeatMode = when (r) { 1 -> Player.REPEAT_MODE_ALL; 2 -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }

    /** Liste starten mit dem gespeicherten Modus ihres Kontexts (Bibliothek bzw. Playlist). */
    fun playContext(list: List<Track>, context: String) {
        applyMode(keyOf(context))
        if (shuffle) playShuffled(list, context) else play(list, 0, context)
    }

    private fun isQueued(m: MediaItem) = m.mediaMetadata.extras?.getBoolean("queued", false) == true

    private fun asQueued(m: MediaItem): MediaItem {
        // vorhandene Zusatzdaten (z. B. Jam-Herkunft) behalten
        val b = Bundle(m.mediaMetadata.extras ?: Bundle()).apply { putBoolean("queued", true) }
        return m.buildUpon().setMediaMetadata(m.mediaMetadata.buildUpon().setExtras(b).build()).build()
    }

    /**
     * Spielt [list] ab Position [index]. Zufall wird hier selbst gemacht (nicht vom Player): bei jedem Start und
     * jedem Ein-/Ausschalten wird neu gemischt, und eingereihte Lieder bleiben immer direkt als Nächstes dran.
     */
    fun play(list: List<Track>, index: Int, context: String) {
        if (list.isEmpty()) return
        if (jamRemote) { Jam.playOwn(list, index.coerceIn(0, list.lastIndex)); return }
        applyMode(keyOf(context))
        startItems(list.map { Library.mediaItem(it, context) }, index.coerceIn(0, list.lastIndex))
    }

    private fun startItems(items: List<MediaItem>, index: Int) {
        val t = Library.trackOf(items[index])
        if (Store.skipSilence && t != null && !Trim.known(t)) {
            // Nur den Anfang schnell ausmessen (max. ~0,7 s), das Ende folgt im Hintergrund
            viewModelScope.launch {
                quickTrim(t)
                val fixed = items.toMutableList()
                fixed[index] = Library.reclip(items[index])
                doStart(fixed, index)
            }
        } else doStart(items, index)
    }

    /** Anfang schnell (mit Zeitlimit) bestimmen; vollständige Analyse (Ende) läuft danach im Hintergrund weiter. */
    private suspend fun quickTrim(t: Track) {
        val job = viewModelScope.async(Dispatchers.IO) { Trim.ensure(app, t, startOnly = true) }
        kotlinx.coroutines.withTimeoutOrNull(700) { job.await() }
        viewModelScope.launch(Dispatchers.IO) { job.join(); Trim.ensure(app, t) }
    }

    /** Das nächste Lied in der Warteschlange vorab ausmessen und mit Schnitt versehen. */
    private fun prefetchNext() {
        val c = controller ?: return
        if (!Store.skipSilence) return
        val i = c.nextMediaItemIndex
        if (i == C.INDEX_UNSET || i >= c.mediaItemCount) return
        val item = c.getMediaItemAt(i)
        val t = Library.trackOf(item) ?: return
        val want = Trim.clip(t)
        val have = item.clippingConfiguration
        val same = if (want == null) have.startPositionMs == 0L && have.endPositionMs == C.TIME_END_OF_SOURCE
                   else have.startPositionMs == want.first && have.endPositionMs == want.second
        if (Trim.known(t)) {
            if (!same) c.replaceMediaItem(i, Library.reclip(item))
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Trim.ensure(app, t) }
            val cc = controller ?: return@launch
            val j = cc.nextMediaItemIndex
            if (j != C.INDEX_UNSET && j < cc.mediaItemCount && cc.getMediaItemAt(j).mediaId == item.mediaId) {
                cc.replaceMediaItem(j, Library.reclip(cc.getMediaItemAt(j)))
            }
        }
    }

    private fun doStart(items: List<MediaItem>, index: Int) {
        val c = controller ?: return
        baseList = items
        c.shuffleModeEnabled = false
        if (shuffle) {
            val rest = items.filterIndexed { i, _ -> i != index }.shuffled()
            c.setMediaItems(listOf(items[index]) + rest, 0, 0L)
        } else {
            c.setMediaItems(items, index, 0L)
        }
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>, context: String) {
        val c = controller ?: return
        if (list.isEmpty()) return
        if (jamRemote) { Jam.playOwn(list.shuffled(), 0); return }
        shuffle = true
        Store.setMode(app, keyOf(context), true, Store.modeOf(keyOf(context)).second); modeVersion++
        c.shuffleModeEnabled = false
        val items = list.map { Library.mediaItem(it, context) }
        baseList = items
        val sh = items.shuffled()
        val t0 = Library.trackOf(sh[0])
        if (Store.skipSilence && t0 != null && !Trim.known(t0)) {
            viewModelScope.launch {
                quickTrim(t0)
                val fixed = sh.toMutableList()
                fixed[0] = Library.reclip(sh[0])
                c.setMediaItems(fixed, 0, 0L); c.prepare(); c.play()
            }
        } else {
            c.setMediaItems(sh, 0, 0L); c.prepare(); c.play()
        }
    }

    /** Aktuelle Abspielposition, direkt vom Player (für ruckelfreie Anzeige, jedes Bild neu abgefragt). */
    fun livePositionMs(): Long = if (jamRemote) Jam.estimatedPosition() else controller?.currentPosition ?: positionMs

    fun togglePlay() {
        val c = controller ?: return
        if (jamRemote) { Jam.cmd(if (isPlaying) "pause" else "play"); isPlaying = !isPlaying; return }
        if (c.isPlaying) { c.pause(); return }
        // Nach einer Unterbrechung (Anruf, Video in einer anderen App) neu anstoßen, damit es sicher weitergeht
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playWhenReady) c.pause()
        c.play()
    }

    fun next() {
        if (jamRemote) { Jam.cmd("next"); return }
        val c = controller ?: return
        if (c.mediaItemCount == 0 || c.currentMediaItemIndex < 0) return
        when (repeatFor(keyOf(playingContext))) {
            RepeatMode.One -> {
                c.seekTo(c.currentMediaItemIndex, 0L)
                c.play()
            }
            else -> {
                if (c.hasNextMediaItem()) c.seekToNextMediaItem()
                else {
                    c.pause()
                    if (c.currentMediaItemIndex >= 0) c.seekTo(c.currentMediaItemIndex, 0L)
                    positionMs = 0L
                }
            }
        }
    }
    /** Swipe ist ein echter Trackwechsel und darf nicht wie der Zurück-Button zum Songanfang springen. */
    fun previousFromSwipe() { if (jamRemote) Jam.cmd("prev") else controller?.seekToPreviousMediaItem() }
    fun previous() {
        if (jamRemote) { Jam.cmd("prev"); return }
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        if (jamRemote) { Jam.seekAbs(ms); positionMs = ms; return }
        controller?.seekTo(ms)
        positionMs = ms
    }

    private fun isLive(key: String): Boolean {
        val c = controller
        return c == null || c.mediaItemCount == 0 || keyOf(playingContext) == key
    }

    /** Shuffle umschalten. [key] = Kontext einer Playlist-Seite; ohne Angabe gilt das, was gerade läuft. */
    fun toggleShuffle(key: String? = null) {
        if (jamRemote && key == null) { Jam.cmd("shuffle"); return }
        val c = controller ?: return
        val k = key ?: keyOf(playingContext)
        val on = !Store.modeOf(k).first
        Store.setMode(app, k, on, Store.modeOf(k).second)
        modeVersion++
        message = if (on) "Shuffle an" else "Shuffle aus"
        if (!isLive(k)) return
        shuffle = on
        c.shuffleModeEnabled = false
        val cur = c.currentMediaItemIndex
        if (cur < 0 || c.mediaItemCount == 0) return
        // Das laufende Lied und direkt Eingereihtes („Als Nächstes“) bleiben immer, wo sie sind
        val count = c.mediaItemCount
        val from = (cur + 1 + queuedCount).coerceAtMost(count)
        val head = (0 until cur).map { c.getMediaItemAt(it) }
        val tail = (from until count).map { c.getMediaItemAt(it) }
        if (on) {
            // Alles andere neu mischen – auch Lieder, die in der Liste vor dem laufenden standen
            val pool = (head + tail).shuffled()
            if (pool.isEmpty()) return
            c.replaceMediaItems(from, count, pool)
            if (head.isNotEmpty()) c.removeMediaItems(0, cur)
        } else {
            // Zurück zur Reihenfolge der Liste, ausgehend vom laufenden Lied: davor, was davor stand, danach, was danach kommt
            val curId = c.currentMediaItem?.mediaId
            val pos = baseList.indexOfFirst { it.mediaId == curId }
            if (pos < 0) return
            // vorhandene Einträge nehmen (mit ihrem Stille-Schnitt); Lieder können mehrfach vorkommen
            val left = LinkedHashMap<String, ArrayDeque<MediaItem>>()
            for (m in head + tail) left.getOrPut(m.mediaId) { ArrayDeque() }.addLast(m)
            fun take(b: MediaItem): MediaItem? = left[b.mediaId]?.removeFirstOrNull()
            val before = baseList.take(pos).mapNotNull { take(it) }
            val after = baseList.drop(pos + 1).mapNotNull { take(it) }
            // Was nicht aus der Liste stammt (z. B. später eingereiht und schon gespielt), kommt hinten dran
            val rest = left.values.flatten()
            // erst hinten, dann vorne ersetzen (so verschieben sich die Positionen nicht dazwischen)
            c.replaceMediaItems(from, count, after + rest)
            c.replaceMediaItems(0, cur, before)
        }
    }

    fun cycleRepeat(key: String? = null) {
        if (jamRemote && key == null) { Jam.cmd("repeat"); return }
        val c = controller ?: return
        val k = key ?: keyOf(playingContext)
        val (s, r) = Store.modeOf(k)
        val next = (r + 1) % 3
        Store.setMode(app, k, s, next)
        modeVersion++
        if (isLive(k)) {
            c.repeatMode = when (next) {
                1 -> Player.REPEAT_MODE_ALL
                2 -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            repeat = when (next) { 1 -> RepeatMode.All; 2 -> RepeatMode.One; else -> RepeatMode.Off }
        }
    }

    /** Bibliothek neu einlesen (z. B. wenn die App wieder in den Vordergrund kommt oder man nach unten zieht). */
    fun refresh() { if (!loading) { blockTick++; reindex() } }

    // ---------- Warteschlange ----------

    private fun itemsOf(list: List<Track>) = list.map { asQueued(Library.mediaItem(it, "q")) }

    /** Direkt nach dem aktuellen Lied (vor schon eingereihten). */
    fun playNext(list: List<Track>) {
        if (jamRemote) { Jam.addOwn(list); return }
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, itemsOf(list))
        message = "Als Nächstes eingereiht"
    }

    /** Hinten an die Warteschlange: nach dem aktuellen Lied und nach allem, was schon eingereiht ist. */
    fun addToQueue(list: List<Track>) {
        if (jamRemote) { Jam.addOwn(list); return }
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(c.currentMediaItemIndex + 1 + queuedCount, itemsOf(list))
        message = "Zur Warteschlange hinzugefügt"
    }

    fun removeFromQueue(index: Int) {
        if (jamRemote) { Jam.queueCmd("remove", index - currentIndex, 0, queue.getOrNull(index)); return }
        controller?.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        if (jamRemote) { Jam.queueCmd("move", from - currentIndex, to - currentIndex, queue.getOrNull(from)); return }
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun jumpTo(index: Int) {
        if (jamRemote) { Jam.queueCmd("jump", index - currentIndex, 0, queue.getOrNull(index)); return }
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun clearQueue() {
        // Im Jam als Gast: Player wegwischen = Jam verlassen
        if (jamRemote) { if (Jam.role == Jam.Role.Guest) Jam.leave() else Jam.cmd("pause"); return }
        controller?.let { it.stop(); it.clearMediaItems() }
    }

    // ---------- Jam ----------

    /**
     * Im Jam ist Bearbeiten gesperrt (Dateien, Tags, Cover, Interpreten, Sperren, Löschen, Angleichen, Papierkorb),
     * damit sich nichts unvorhergesehen ändert, während andere zuhören. Ausnahme: Developer Option, die ALLE an haben.
     */
    val jamEditLocked: Boolean get() = Jam.editLocked

    private fun editLocked(): Boolean {
        if (!Jam.editLocked) return false
        message = "Im Jam ist Bearbeiten gesperrt"
        return true
    }

    /** Gast-Anzeige: Einträge je Lied zwischenspeichern, damit sich Cover und Anzeige nicht ständig neu aufbauen. */
    private val jamItems = HashMap<String, MediaItem>()
    private fun jamItem(e: JamEntry): MediaItem = jamItems.getOrPut(e.key) { Jam.displayItem(e) }

    /** Player-Anzeige aus dem Stand des Hosts (Titel, Position, Pause, Warteschlange, Shuffle/Wiederholen). */
    private fun syncFromJam(s: JamState?) {
        if (jamItems.size > 300) jamItems.clear()
        val cur = s?.current?.let { jamItem(it) }
        current = cur
        isPlaying = s?.playing == true && cur != null
        positionMs = Jam.estimatedPosition()
        durationMs = s?.current?.durationMs ?: 0L
        shuffle = s?.shuffle == true
        repeat = when (s?.repeat) { Player.REPEAT_MODE_ALL -> RepeatMode.All; Player.REPEAT_MODE_ONE -> RepeatMode.One; else -> RepeatMode.Off }
        val next = s?.upcoming.orEmpty().map { jamItem(it) }
        queue = listOfNotNull(cur) + next
        currentIndex = 0
        nextItem = next.firstOrNull()
        prevItem = null
        queuedCount = 0
    }

    /**
     * Im Jam, aber dieses Handy ist nicht die gemeinsame Ausgabe: Der Player zeigt, was dort läuft,
     * und alle Bedienung geht dorthin. Ist dieses Handy die Ausgabe, läuft alles wie gewohnt hier.
     */
    private val jamRemote: Boolean get() = Jam.active && !Jam.isSpeaker

    /** Als Host: Befehle der Gäste laufen über dieselben Funktionen wie die eigene Bedienung. */
    private val jamHost = object : JamHost {
        override fun jamToggle() = togglePlay()
        override fun jamNext() = next()
        override fun jamPrevious() = previous()
        override fun jamSeek(ms: Long) = seekTo(ms)
        override fun jamPlay(items: List<MediaItem>, index: Int) {
            applyMode("all")
            startItems(items, index.coerceIn(0, items.lastIndex))
        }
        override fun jamAdd(items: List<MediaItem>) {
            val c = controller ?: return
            if (c.mediaItemCount == 0) { doStart(items, 0); return }
            c.addMediaItems(c.currentMediaItemIndex + 1 + queuedCount, items.map { asQueued(it) })
        }
        override fun jamLoad(items: List<MediaItem>, index: Int, posMs: Long, play: Boolean) {
            val c = controller ?: return
            applyMode("all")
            baseList = items
            c.shuffleModeEnabled = false
            c.setMediaItems(items, index.coerceIn(0, items.lastIndex), posMs)
            c.prepare()
            c.playWhenReady = play
        }
        override fun jamShuffle() = toggleShuffle()
        override fun jamRepeat() = cycleRepeat()
        override val jamShuffleOn: Boolean get() = shuffle
    }

    // ---------- Von außen geöffnete Dateien („Öffnen mit“) ----------

    private var pendingExternal: android.net.Uri? = null
    /** true = Vollbild-Player öffnen (wird von der Oberfläche zurückgesetzt). */
    var openPlayerRequest by mutableStateOf(false)

    /**
     * Spielt genau diese eine Datei. Liegt sie in der Bibliothek, läuft der Bibliothekstitel (mit Herz, Playlist usw.),
     * sonst ein Fremdtitel, der nirgends gespeichert wird. Die bisherige Warteschlange wird ersetzt.
     */
    fun playExternal(uri: android.net.Uri) {
        if (controller == null) { pendingExternal = uri; return }
        // Eine Datei von außen lässt sich im Jam nicht mit den anderen teilen
        if (Jam.active) { message = "Während eines Jams lassen sich Dateien von außen nicht abspielen"; return }
        viewModelScope.launch {
            val item = try { withContext(Dispatchers.IO) { External.itemFor(app, uri) } } catch (e: Exception) { null }
            if (item == null) { message = "Datei kann nicht geöffnet werden"; return@launch }
            applyMode(External.CONTEXT)
            if (Library.trackOf(item) != null) startItems(listOf(item), 0) else doStart(listOf(item), 0)
            openPlayerRequest = true
        }
    }

    // ---------- Playlists (nur in der App, keine Dateikopien) ----------

    private fun syncPlaylists() {
        playlists = Store.playlists
        favoritePaths = Store.playlists.firstOrNull { it.id == FAV_ID }?.trackPaths?.toHashSet() ?: emptySet()
    }

    fun isFavorite(path: String) = Store.resolve(path) in favoritePaths

    /** Herz an/aus für ein Lied (Lieblingssongs). */
    fun toggleFavorite(path: String) {
        val now = Store.toggleFavorite(app, path)
        syncPlaylists()
        linkLabel = null; linkTarget = null
        if (now) linked("Zu Lieblingssongs hinzugefügt", "pl:$FAV_ID") else message = "Aus Lieblingssongs entfernt"
    }

    // ---------- Dateinamen übernehmen / umbenennen (immer erst nach Bestätigung in der Oberfläche) ----------

    fun renameCandidates(): List<Track> = Library.renameCandidates()

    fun renameFiles(items: List<Pair<Track, String>>) { if (editLocked()) return;
        undo = null
        if (items.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val res = Library.renameFiles(app, items)
                Library.load(app)
                res
            }
            refreshFromLibrary()
            if (dupes != null) dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            loading = false
            message = (if (r.renamed == 1 && r.failed == 0) "Dateiname geändert" else "${r.renamed} Dateien umbenannt") + if (r.failed > 0) ", ${r.failed} fehlgeschlagen" else ""
        }
    }

    /** Schnell-Umbenennen aus dem Player: mit Meldung und „Rückgängig“. */
    fun quickRename(t: Track, newBase: String) { if (editLocked()) return;
        val oldBase = t.fileName.substringBeforeLast('.')
        val ext = t.fileName.substringAfterLast('.', "")
        val newName = Names.fileBaseName(newBase) + if (ext.isEmpty()) "" else ".$ext"
        val expected = java.io.File(java.io.File(t.path).parentFile, newName).path
        val wantMove = Store.quickMove && t.artist != UNKNOWN_ARTIST && !Library.inArtistTree(t)
        viewModelScope.launch {
            var movedTo: String? = null
            val r = withContext(Dispatchers.IO) {
                val res = Library.renameFiles(app, listOf(t to newBase))
                if (res.renamed > 0 && wantMove) {
                    val cur = t.copy(path = expected)
                    Library.sortIntoArtistFolders(app, listOf(cur)).map[expected]?.let { movedTo = it }
                }
                Library.load(app)
                res
            }
            refreshFromLibrary()
            if (dupes != null) dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            if (r.renamed == 0) { message = "Umbenennen fehlgeschlagen"; return@launch }
            val nu = Library.tracks.firstOrNull { it.path == (movedTo ?: expected) }
            message = if (movedTo != null) "Dateiname geändert, in Interpreten-Ordner verschoben" else "Dateiname geändert"
            // Rückgängig stellt nur den Namen wieder her (Verschieben bleibt)
            if (nu != null) undo = { renameFiles(listOf(nu to oldBase)) }
        }
    }

    fun createPlaylist(name: String, firstTracks: List<Track> = emptyList(), audio: Boolean = false): String? {
        if (name.isBlank()) return null
        val p = Store.createPlaylist(app, name, audio)
        if (firstTracks.isNotEmpty()) Store.addToPlaylist(app, p.id, firstTracks.map { it.path })
        syncPlaylists()
        return p.id
    }

    /** Reihenfolge: Lieblingssongs, fixierte, dann zuletzt Hinzugefügtes. */
    fun orderedPlaylists(): List<Playlist> = Store.ordered(playlists)

    /** Playlist-Reiter: Lieblingssongs, fixierte, dann die am häufigsten gehörten. */
    fun playlistsByUse(): List<Playlist> = Store.orderedByUse(playlists)

    /** Playlist ⇄ Hörbuch/Podcast umschalten. */
    fun setAudio(id: String, audio: Boolean) {
        Store.setAudio(app, id, audio)
        syncPlaylists()
        message = if (audio) "Zu Hörbuch/Podcast umgewandelt" else "Zu Playlist umgewandelt"
    }

    fun showPlaylistsAsGrid(grid: Boolean, audio: Boolean = false) {
        Store.setPlaylistGrid(app, grid, audio)
        if (audio) audiobookGrid = grid else playlistGrid = grid
    }

    fun setPinned(id: String, pinned: Boolean) {
        Store.setPinned(app, id, pinned)
        syncPlaylists()
        message = if (pinned) "Playlist fixiert" else "Fixierung gelöst"
    }

    fun editPlaylist(id: String, name: String, description: String) {
        Store.editPlaylist(app, id, name, description)
        syncPlaylists()
    }

    /** Eigenes Bild für eine Playlist: wird verkleinert, quadratisch zugeschnitten und in der App gespeichert. */
    private fun fetchBytes(uri: android.net.Uri?, url: String?): ByteArray? = try {
        if (uri != null) app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        else if (url != null) {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Musikplayer")
            try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
        } else null
    } catch (e: Exception) { null }

    /** Bild mittig quadratisch zuschneiden, auf höchstens 600 px verkleinern. */
    private fun squareBitmap(bytes: ByteArray): android.graphics.Bitmap? = try {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 800 && o.outHeight / (sample * 2) >= 800) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) null else {
            val side = minOf(bmp.width, bmp.height)
            val sq = android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            android.graphics.Bitmap.createScaledBitmap(sq, minOf(side, 600), minOf(side, 600), true)
        }
    } catch (e: Exception) { null }

    /** Playlist-Cover aus Galerie-Bild (uri) oder Web-Bild (url). */
    fun setCover(id: String, uri: android.net.Uri? = null, url: String? = null) { if (editLocked()) return;
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val out = fetchBytes(uri, url)?.let { squareBitmap(it) } ?: return@withContext false
                try {
                    val dir = java.io.File(app.filesDir, "covers").also { it.mkdirs() }
                    java.io.File(dir, "$id.jpg").outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    true
                } catch (e: Exception) { false }
            }
            if (ok) { Store.setCoverStamp(app, id, System.currentTimeMillis()); syncPlaylists() } else message = "Bild konnte nicht geladen werden"
        }
    }

    /** Eigenes, unabhängiges Interpret-Cover speichern oder zurücksetzen. */
    fun setArtistCover(artist: String, uri: android.net.Uri? = null, url: String? = null, reset: Boolean = false) { if (editLocked()) return;
        viewModelScope.launch {
            if (reset) {
                val old = Store.artistCovers[artist]
                old?.let { java.io.File(java.io.File(app.filesDir, "artistcovers"), it).delete() }
                Store.setArtistCover(app, artist, null)
                Art.invalidate()
                message = "Interpreten-Cover zurückgesetzt"
                return@launch
            }
            val filename = withContext(Dispatchers.IO) {
                val bmp = fetchBytes(uri, url)?.let { squareBitmap(it) } ?: return@withContext null
                try {
                    val dir = java.io.File(app.filesDir, "artistcovers").also { it.mkdirs() }
                    val n = java.util.UUID.randomUUID().toString() + ".jpg"
                    java.io.File(dir, n).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    n
                } catch (e: Exception) { null }
            }
            if (filename != null) {
                Store.artistCovers[artist]?.let { java.io.File(java.io.File(app.filesDir, "artistcovers"), it).delete() }
                Store.setArtistCover(app, artist, filename)
                Art.invalidate()
                message = "Interpreten-Cover gesetzt"
            } else message = "Bild konnte nicht geladen werden"
        }
    }

    /** Eigenes Cover für Lieder (auch ganze Alben): Galerie-Bild, Web-Bild oder (beides null) zurücksetzen. */
    fun setTrackCover(tracks: List<Track>, uri: android.net.Uri? = null, url: String? = null) { if (editLocked()) return;
        val paths = tracks.map { it.path }
        viewModelScope.launch {
            if (uri == null && url == null) {
                Store.setTrackCover(app, paths, null)
                Art.invalidate()
                message = "Cover zurückgesetzt"
                return@launch
            }
            val name = withContext(Dispatchers.IO) {
                val out = fetchBytes(uri, url)?.let { squareBitmap(it) } ?: return@withContext null
                try {
                    val dir = java.io.File(app.filesDir, "trackcovers").also { it.mkdirs() }
                    val n = java.util.UUID.randomUUID().toString() + ".jpg"
                    java.io.File(dir, n).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    n
                } catch (e: Exception) { null }
            }
            if (name != null) {
                Store.setTrackCover(app, paths, name)
                Art.invalidate()
                message = "Cover gesetzt"
            } else message = "Bild konnte nicht geladen werden"
        }
    }

    fun hasOwnCover(tracks: List<Track>) = tracks.any { it.path in Store.trackCovers }

    fun removeCover(id: String) { if (editLocked()) return;
        java.io.File(java.io.File(app.filesDir, "covers"), "$id.jpg").delete()
        Store.setCoverStamp(app, id, 0L)
        syncPlaylists()
    }

    /** Kontext der laufenden Wiedergabe, z. B. „p:<Playlist-ID>“ (für die Markierung „diese Playlist läuft“). */
    val playingContext: String? get() = current?.mediaId?.removePrefix("t:")?.substringBeforeLast(':')

    fun playPlaylist(p: Playlist) {
        val list = playlistTracks(p.id)
        if (list.isEmpty()) { message = "Die Playlist ist leer"; return }
        playContext(list, "p:${p.id}")
    }

    /** Spielt die Playlist, oder Pause/Weiter, wenn sie gerade läuft. */
    fun togglePlaylistPlay(p: Playlist) {
        if (playingContext == "p:${p.id}") togglePlay() else playPlaylist(p)
    }

    /** Fügt Lieder zu mehreren Playlists hinzu (Doppelte werden übersprungen) und meldet das Ergebnis. */
    /** Auswahl „Zu Playlist“ von außen öffnen (z. B. alle Lieder einer Playlist in eine andere). */
    var pickerRequest by mutableStateOf<List<Track>?>(null)

    /** Tippen auf die „hinzugefügt“-Meldung öffnet diese Playlist. */
    var linkLabel: String? = null
    /** "pl:<id>" = Playlist öffnen, "blocked" = Sperrliste in den Einstellungen. */
    var linkTarget: String? = null
    var navRequest by mutableStateOf<String?>(null)
    var settingsPageRequest by mutableStateOf<String?>(null)
    private fun linked(msg: String, target: String) { linkLabel = msg; linkTarget = target; message = msg }

    fun addToPlaylists(ids: List<String>, list: List<Track>, removeIds: List<String> = emptyList()) {
        val pathSet = list.map { Store.resolve(it.path) }.toHashSet()
        var removed = 0
        for (id in removeIds) {
            val p = Store.playlists.firstOrNull { it.id == id } ?: continue
            p.trackPaths.withIndex().filter { it.value in pathSet }.map { it.index }.sortedDescending().forEach {
                Store.removeFromPlaylistAt(app, id, it); removed++
            }
        }
        var added = 0
        for (id in ids) {
            val before = Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0
            Store.addToPlaylist(app, id, list.map { it.path }, true)
            added += (Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0) - before
        }
        syncPlaylists()
        val names = ids.mapNotNull { id -> Store.playlists.firstOrNull { it.id == id }?.name }
        linkLabel = null; linkTarget = null
        val msg = when {
            added == 0 && removed > 0 -> if (removeIds.size == 1) "Aus „${Store.playlists.firstOrNull { it.id == removeIds[0] }?.name.orEmpty()}“ entfernt" else "Aus ${removeIds.size} Playlists entfernt"
            added == 0 -> "Schon in der Playlist"
            names.size == 1 -> "Zu „${names[0]}“ hinzugefügt" + if (removed > 0) " (und woanders entfernt)" else ""
            else -> "Zu ${names.size} Playlists hinzugefügt" + if (removed > 0) " (und woanders entfernt)" else ""
        }
        val tid = ids.firstOrNull { id -> Store.playlists.any { it.id == id } }
        if (added > 0 && names.size == 1 && tid != null) linked(msg, "pl:$tid") else message = msg
    }

    fun renamePlaylist(id: String, name: String) {
        if (name.isBlank()) return
        Store.renamePlaylist(app, id, name)
        syncPlaylists()
    }

    fun deletePlaylist(id: String) {
        java.io.File(java.io.File(app.filesDir, "covers"), "$id.jpg").delete()
        Store.deletePlaylist(app, id)
        syncPlaylists()
    }

    /** Lieder aus [list], die schon in der Playlist [id] sind (für die Warnung vor Doppelten). */
    fun alreadyIn(id: String, list: List<Track>): List<Track> {
        val have = Store.playlists.firstOrNull { it.id == id }?.trackPaths?.toHashSet() ?: return emptyList()
        return list.filter { it.path in have }
    }

    fun addToPlaylist(id: String, list: List<Track>, skipDuplicates: Boolean = true) {
        val before = Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0
        Store.addToPlaylist(app, id, list.map { it.path }, skipDuplicates)
        syncPlaylists()
        val added = (Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0) - before
        message = if (added == 1) "1 Lied hinzugefügt" else "$added Lieder hinzugefügt"
    }

    fun removeFromPlaylistAt(id: String, index: Int) {
        Store.removeFromPlaylistAt(app, id, index)
        syncPlaylists()
    }

    fun playlistEntries(id: String): List<Pair<Int, Track>> = Library.playlistEntries(id)

    // ---------- Ansicht ----------

    fun changeRowScale(v: Float) { Store.setRowScale(app, v); rowScale = v }

    fun toggleFavArtist(name: String) {
        val on = Store.toggleFavArtist(app, name)
        favArtists = Store.favArtists.toSet()
        message = if (on) "„$name“ zu Favoriten-Interpreten hinzugefügt" else "„$name“ aus Favoriten-Interpreten entfernt"
    }

    fun showAlbumsAsGrid(grid: Boolean) {
        Store.setAlbumGrid(app, grid)
        albumGrid = grid
    }

    fun showArtistsAsGrid(grid: Boolean) {
        Store.setArtistGrid(app, grid)
        artistGrid = grid
    }

    fun movePlaylistTrack(id: String, from: Int, to: Int) {
        Store.movePlaylistTrack(app, id, from, to)
        syncPlaylists()
    }

    fun playlistTracks(id: String): List<Track> = Library.tracksFor("p:$id")

    // ---------- Interpret-Zuordnung & Einsortieren ----------

    /** Interpret für einen ganzen Ordner festlegen (gilt für alle Lieder ohne Interpret im Tag). */
    fun assignFolder(folder: String, artist: String?) { if (editLocked()) return;
        Store.setFolderArtist(app, folder, artist)
        reindex()
    }

    /** Interpret für ein Lied; mit [move] wandert die Datei danach in den Interpreten-Ordner. */
    fun assignTrack(path: String, artist: String?, move: Boolean = false) { if (editLocked()) return;
        val t = tracks.firstOrNull { it.path == path }
        if (move && t != null && !artist.isNullOrBlank()) { assignTracks(listOf(t), artist, true); return }
        Store.setTrackArtist(app, path, artist)
        reindex()
    }

    private fun reindex() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Library.load(app) }
            refreshFromLibrary()
        }
    }

    fun sortCandidates(): List<Track> = Library.sortCandidates()

    fun sortIntoFolders(list: List<Track>) { if (editLocked()) return;
        if (list.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val res = Library.sortIntoArtistFolders(app, list)
                Library.load(app)
                res
            }
            refreshFromLibrary()
            loading = false
            message = "${r.moved} Dateien einsortiert" + if (r.failed > 0) ", ${r.failed} fehlgeschlagen" else ""
        }
    }

    // ---------- Datei bearbeiten ----------

    /** Name (Datei), Interpret und Verschieben in den Interpreten-Ordner in einem Schritt. */
    fun editTrack(t: Track, newBaseName: String, artist: String?, move: Boolean = false, coverUri: android.net.Uri? = null, coverUrl: String? = null, coverSelected: Boolean = false, coverReset: Boolean = false) { if (editLocked()) return;
        val art = artist?.takeIf { it.isNotBlank() }
        val nameChanged = newBaseName.isNotBlank() && newBaseName != t.fileName.substringBeforeLast('.')
        val artistChanged = (art ?: "") != (if (t.artist == UNKNOWN_ARTIST) "" else t.artist)
        val moveWanted = move && art != null
        if (!nameChanged && !artistChanged && !moveWanted && !coverSelected) return
        loading = true
        viewModelScope.launch {
            var failed = 0
            withContext(Dispatchers.IO) {
                var cur = t
                // erst Interpret (nach Pfad), dann Verschieben, dann Umbenennen (Store zieht den Pfad jeweils nach)
                if (artistChanged) Store.setTrackArtist(app, cur.path, art)
                if (moveWanted) {
                    val r = Library.sortIntoArtistFolders(app, listOf(cur.copy(artist = art!!)))
                    failed += r.failed
                    r.map[cur.path]?.let { cur = cur.copy(path = it) }
                }
                if (nameChanged) failed += Library.renameFiles(app, listOf(cur to newBaseName)).failed
                Library.load(app)
                val coverTarget = Library.allTracks.firstOrNull { candidate ->
                    candidate.title == t.title && candidate.artist == (art ?: t.artist) &&
                        (candidate.fileName.substringBeforeLast('.') == newBaseName || candidate.folder == cur.folder)
                }?.path ?: cur.path
                if (coverSelected) {
                    if (coverReset) Store.setTrackCover(app, listOf(coverTarget), null)
                    else {
                        val savedName = fetchBytes(coverUri, coverUrl)?.let { squareBitmap(it) }?.let { bitmap ->
                            try {
                                val dir = java.io.File(app.filesDir, "trackcovers").also { it.mkdirs() }
                                val n = java.util.UUID.randomUUID().toString() + ".jpg"
                                java.io.File(dir, n).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                                n
                            } catch (_: Exception) { null }
                        }
                        if (savedName != null) Store.setTrackCover(app, listOf(coverTarget), savedName) else failed++
                    }
                }
            }
            if (coverSelected) Art.invalidate()
            refreshFromLibrary()
            if (dupes != null) dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            loading = false
            message = if (failed > 0) "Teilweise fehlgeschlagen" else "Gespeichert"
        }
    }

    /** Interpret für viele Lieder auf einmal; optional gleich in den Interpreten-Ordner verschieben. */
    fun assignTracks(list: List<Track>, artist: String?, move: Boolean = false) { if (editLocked()) return;
        if (list.isEmpty()) return
        val art = artist?.takeIf { it.isNotBlank() }
        Store.setTrackArtists(app, list.map { it.path }, art)
        if (!move || art == null) {
            reindex()
            message = "Interpret für ${list.size} Lieder gesetzt"
            return
        }
        loading = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val res = Library.sortIntoArtistFolders(app, list.map { it.copy(artist = art) })
                Library.load(app)
                res
            }
            refreshFromLibrary()
            loading = false
            message = "Interpret gesetzt, ${r.moved} verschoben" + if (r.failed > 0) ", ${r.failed} fehlgeschlagen" else ""
        }
    }

    /** Albumname für die Lieder ändern (nur in der App, Dateien bleiben unberührt). */
    fun renameAlbum(list: List<Track>, name: String) { if (editLocked()) return;
        if (list.isEmpty() || name.isBlank()) return
        Store.setAlbum(app, list.map { it.path }, name)
        reindex()
        message = "Album umbenannt"
    }

    // ---------- Sperrliste ----------

    private fun dropFromPlayer(pred: (Long) -> Boolean) {
        val c = controller ?: return
        fun idAt(i: Int) = c.getMediaItemAt(i).mediaId.substringAfterLast(':').toLongOrNull() ?: -1L
        if (c.mediaItemCount == 0) return
        if (pred(idAt(c.currentMediaItemIndex))) {
            if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.pause()
        }
        for (i in c.mediaItemCount - 1 downTo 0) if (i != c.currentMediaItemIndex && pred(idAt(i))) c.removeMediaItem(i)
        // war das aktuelle Lied gesperrt und ist es noch da (kein Nachfolger), entfernen
        if (c.mediaItemCount > 0 && pred(idAt(c.currentMediaItemIndex))) c.removeMediaItem(c.currentMediaItemIndex)
    }

    /** Nach rechts wischen auf der „gesperrt“-Meldung = sofort Vollsperre. */
    var promoteLabel: String? = null
    var promoteAction: (() -> Unit)? = null

    private fun afterBlock(label: String, affected: List<Track>, promote: (() -> Unit)? = null, undoAction: () -> Unit) {
        linkLabel = label; linkTarget = "blocked"
        promoteLabel = if (promote != null) label else null
        promoteAction = promote
        val ids = affected.map { it.id }.toHashSet()
        dropFromPlayer { it in ids }
        blockTick++
        reindex()
        message = label
        undo = undoAction
    }

    fun blockTracks(list: List<Track>) { if (editLocked()) return;
        if (list.isEmpty()) return
        val paths = list.map { it.path }
        Store.softBlockTracks(app, paths)
        afterBlock(
            if (list.size == 1) "Aus Mediathek gesperrt: ${list[0].title}" else "${list.size} Titel aus Mediathek gesperrt", list,
            promote = { paths.forEach { Store.promoteTrackToHard(app, it) }; blockTick++; reindex(); message = "Vollständig gesperrt" }
        ) {
            Store.softUnblockTracks(app, paths); blockTick++; reindex()
        }
    }

    fun blockArtist(name: String) { if (editLocked()) return;
        val affected = tracks.filter { it.artist == name }
        Store.softBlockArtist(app, name)
        afterBlock("Aus Mediathek gesperrt: $name", affected, promote = { Store.promoteArtistToHard(app, name); blockTick++; reindex(); message = "Interpret vollständig gesperrt" }) { Store.softUnblockArtist(app, name); blockTick++; reindex() }
    }

    fun blockFolder(path: String) { if (editLocked()) return;
        val affected = tracks.filter { it.folder == path }
        Store.softBlockFolder(app, path)
        afterBlock("Ordner aus Mediathek gesperrt: ${path.substringAfterLast('/')}", affected, promote = { Store.promoteFolderToHard(app, path); blockTick++; reindex(); message = "Ordner vollständig gesperrt" }) { Store.softUnblockFolder(app, path); blockTick++; reindex() }
    }


    fun hardBlockTrack(path: String) { if (editLocked()) return; Store.promoteTrackToHard(app, path); blockTick++; reindex(); message = "Vollständig gesperrt" }
    fun hardBlockArtist(name: String) { if (editLocked()) return; Store.promoteArtistToHard(app, name); blockTick++; reindex(); message = "Interpret vollständig gesperrt" }
    fun hardBlockFolder(path: String) { if (editLocked()) return; Store.promoteFolderToHard(app, path); blockTick++; reindex(); message = "Ordner vollständig gesperrt" }

    fun unblockTracks(paths: List<String>) { if (editLocked()) return; Store.unblockTracks(app, paths); blockTick++; reindex(); message = "Entsperrt" }
    fun unblockArtist(name: String) { if (editLocked()) return; Store.unblockArtist(app, name); blockTick++; reindex(); message = "Entsperrt: $name" }
    fun unblockFolder(path: String) { if (editLocked()) return; Store.unblockFolder(app, path); blockTick++; reindex(); message = "Ordner entsperrt" }

    // ---------- Filterliste für Namen, Suchverlauf ----------

    fun setStripStrings(list: List<String>) { if (editLocked()) return;
        Store.setStripStrings(app, list)
        blockTick++
        reindex()
    }

    fun addRecentSearch(q: String) { Store.addRecentSearch(app, q); blockTick++ }
    fun removeRecentSearch(q: String) { Store.removeRecentSearch(app, q); blockTick++ }

    fun setSkipSilence(on: Boolean) { Store.setSkipSilence(app, on); if (on) startTrimSync() }

    /** Öffnet den Ordner der Datei im Dateimanager (mehrere Wege nacheinander); zuletzt Ordnerauswahl am Ordner, sonst Pfad kopieren. */
    fun showInFolder(t: Track) {
        val path = t.folder
        val (vol, rel) = when {
            path.startsWith("/storage/emulated/0") -> "primary" to path.removePrefix("/storage/emulated/0").trim('/')
            path.startsWith("/storage/") -> path.removePrefix("/storage/").let { it.substringBefore('/') to it.substringAfter('/', "") }
            else -> "primary" to path.trim('/')
        }
        val auth = "com.android.externalstorage.documents"
        val docId = "$vol:$rel"
        val docUri = android.provider.DocumentsContract.buildDocumentUri(auth, docId)
        val treeDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            android.provider.DocumentsContract.buildTreeDocumentUri(auth, docId), docId)
        val newTask = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        val grant = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        val view = android.content.Intent.ACTION_VIEW
        val dir = android.provider.DocumentsContract.Document.MIME_TYPE_DIR
        val attempts = listOf(
            // Samsung „Eigene Dateien“
            android.content.Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                .setClassName("com.sec.android.app.myfiles", "com.sec.android.app.myfiles.external.ui.MainActivity")
                .putExtra("samsung.myfiles.intent.extra.START_PATH", path).addFlags(newTask),
            // Standard-Dateimanager des Systems / Files
            android.content.Intent(view).setDataAndType(docUri, dir).addFlags(newTask or grant),
            android.content.Intent(view).setDataAndType(treeDocUri, dir).addFlags(newTask or grant),
            android.content.Intent(view).setDataAndType(android.net.Uri.parse("content://$auth/document/" + android.net.Uri.encode(docId)), "vnd.android.document/directory").addFlags(newTask or grant),
            // Systemauswahl, geöffnet am Ordner
            android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, docUri).addFlags(newTask)
        )
        for (i in attempts) {
            try { app.startActivity(i); return } catch (_: Exception) { }
        }
        val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Pfad", path))
        message = "Kein Dateimanager gefunden – Pfad kopiert: $path"
    }

    /** Wie oft ein Lied gespielt wurde. */
    fun plays(path: String): Int = Store.playCounts[path] ?: 0

    // ---------- Löschen (immer nach Bestätigung in der Oberfläche) ----------

    fun deleteTracks(list: List<Track>) { if (editLocked()) return;
        if (list.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) {
                val c = Trash.moveToTrash(app, list)
                Library.load(app)
                c
            }
            refreshFromLibrary()
            dupes = dupes?.map { g -> g.copy(tracks = g.tracks.filter { t -> tracks.any { it.path == t.path } }) }
                ?.filter { it.tracks.size >= 2 }
            loading = false
            trashTick++
            message = if (moved.size == 1) "In den Papierkorb verschoben" else "${moved.size} Dateien in den Papierkorb verschoben"
            undo = { restoreTrash(moved) }
        }
    }

    // ---------- Lautstärke angleichen (Normalisieren) ----------

    /** Fortschritt beim Angleichen mehrerer Lieder (null = nichts läuft). */
    var normProgress by mutableStateOf<String?>(null)
        private set
    var normTick by mutableStateOf(0)
        private set

    fun normalizedInfo(t: Track): Pair<Normalize.Method, Double>? { normTick; return Normalize.info(t.path) }

    /** Ein Lied messen (für die Vorschau im Dialog). */
    suspend fun planNormalize(t: Track): Normalize.Plan? = withContext(Dispatchers.IO) { Normalize.plan(t) }

    private fun refreshPlayGain() {
        (Jam.player as? androidx.media3.exoplayer.ExoPlayer)?.let { Normalize.onTrack(it, it.currentMediaItem, it.audioSessionId) }
    }

    /**
     * Vorhören: [t] mit [db] Änderung spielen (null = Vorhören beenden). Läuft das Lied noch nicht, wird es direkt
     * als Nächstes eingeschoben und gestartet (ab einem Drittel, damit man gleich Musik hört) – die Warteschlange bleibt.
     * Beim Umschalten läuft es an derselben Stelle weiter, nur die Lautstärke wechselt.
     */
    fun previewNormalize(t: Track, db: Double?) {
        if (db == null) {
            val was = Normalize.previewDb != null
            Normalize.previewDb = null; Normalize.previewPath = null
            if (was) refreshPlayGain()
            return
        }
        if (jamRemote) { message = "Im Jam nur auf dem Handy möglich, das gerade abspielt"; return }
        Normalize.previewPath = t.path
        Normalize.previewDb = db
        val c = controller ?: return
        val playingThis = Library.trackOf(c.currentMediaItem ?: MediaItem.EMPTY)?.path == t.path
        if (!playingThis) {
            val item = Library.mediaItem(t, "q")
            if (c.mediaItemCount == 0) c.setMediaItem(item) else c.addMediaItem(c.currentMediaItemIndex + 1, item)
            if (c.mediaItemCount > 1 && c.currentMediaItem?.mediaId != item.mediaId) c.seekToNextMediaItem()
            c.prepare()
            c.seekTo(t.durationMs / 3)
        }
        c.play()
        refreshPlayGain()
    }

    /** Gemessene Änderung anwenden (ein Lied). */
    fun applyNormalize(p: Normalize.Plan) { if (editLocked()) return;
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Normalize.apply(app, p) }
            normTick++
            refreshPlayGain()
            message = if (!ok) "Datei konnte nicht geändert werden" else if (kotlin.math.abs(p.gainDb) < 0.05) "Lautstärke passt schon" else "Lautstärke angeglichen (" + "%+.1f dB".format(p.gainDb) + ")"
        }
    }

    /** Mehrere Lieder nacheinander messen und angleichen (im Hintergrund, mit Fortschritt). */
    fun normalizeMany(list: List<Track>) { if (editLocked()) return;
        if (list.isEmpty() || normProgress != null) return
        viewModelScope.launch {
            var done = 0; var failed = 0
            for ((i, t) in list.withIndex()) {
                normProgress = "Lautstärke angleichen ${i + 1}/${list.size}: ${t.title}"
                val ok = withContext(Dispatchers.IO) { Normalize.plan(t)?.let { Normalize.apply(app, it) } ?: false }
                if (ok) done++ else failed++
            }
            normProgress = null
            normTick++
            refreshPlayGain()
            message = "$done Lieder angeglichen" + if (failed > 0) ", $failed nicht möglich" else ""
        }
    }

    /** Zurück zur Original-Lautstärke. */
    fun undoNormalize(t: Track) { if (editLocked()) return;
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Normalize.undo(app, t.path) }
            normTick++
            refreshPlayGain()
            message = if (ok) "Original-Lautstärke wiederhergestellt" else "Datei konnte nicht geändert werden"
        }
    }

    // ---------- Papierkorb ----------

    var trashTick by mutableStateOf(0)
        private set

    /** Inhalt des Papierkorbs, neueste zuerst. */
    fun trashItems(): List<TrashItem> { trashTick; return Store.trash.sortedByDescending { it.deletedAt } }

    fun restoreTrash(items: List<TrashItem>) { if (editLocked()) return;
        if (items.isEmpty()) return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { val r = Trash.restore(app, items); Library.load(app); r }
            refreshFromLibrary()
            trashTick++
            message = if (n == 1) "Wiederhergestellt" else "$n Dateien wiederhergestellt"
        }
    }

    fun deleteTrashForever(items: List<TrashItem>) { if (editLocked()) return;
        if (items.isEmpty()) return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { Trash.deleteForever(app, items) }
            trashTick++
            message = if (n == 1) "Endgültig gelöscht" else "$n Dateien endgültig gelöscht"
        }
    }

    /** Lied aus dem Papierkorb anhören (wie eine Datei von außen: einzeln, ohne Bibliothek). */
    fun playTrash(item: TrashItem) = playExternal(android.net.Uri.fromFile(java.io.File(item.file)))

    /** Aufräumen (erweitert): erst löschen, dann umbenennen (vermeidet Namenskonflikte). Nur nach Bestätigung aufrufen. */
    fun cleanup(delete: List<Track>, rename: List<Pair<Track, String>>) { if (editLocked()) return;
        if (delete.isEmpty() && rename.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val (d, r) = withContext(Dispatchers.IO) {
                val d = if (delete.isNotEmpty()) Library.deleteFiles(app, delete).also { if (it > 0) trashTick++ } else 0
                val r = if (rename.isNotEmpty()) Library.renameFiles(app, rename).renamed else 0
                Library.load(app)
                d to r
            }
            refreshFromLibrary()
            dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            loading = false
            message = "$d in den Papierkorb, $r umbenannt"
        }
    }

    // ---------- Duplikate ----------

    fun scanDupes() {
        if (dupBusy) return
        dupBusy = true
        viewModelScope.launch {
            dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            dupBusy = false
        }
    }

    // ---------- Sleep-Timer ----------

    fun startSleep(minutes: Int) {
        Store.setSleepMinutes(app, minutes)
        // Im Jam gilt der Timer für die gemeinsame Ausgabe (dort, wo gespielt wird)
        if (jamRemote) { Jam.command(org.json.JSONObject().put("cmd", "sleep").put("min", minutes)); message = "Sleep-Timer gilt für den ganzen Jam"; return }
        SleepTimer.startSleep(minutes)
    }

    fun cancelSleep() { if (jamRemote) Jam.cmd("sleepOff") else SleepTimer.cancelSleep() }

    // ---------- Ausfaden (Lautstärke sinkt linear über die eingestellte Zeit) ----------

    fun startFade(minutes: Int) {
        Store.setFadeMinutes(app, minutes)
        if (jamRemote) { Jam.command(org.json.JSONObject().put("cmd", "fade").put("min", minutes)); message = "Ausfaden gilt für den ganzen Jam"; return }
        SleepTimer.startFade(minutes)
    }

    fun cancelFade() { if (jamRemote) Jam.cmd("fadeOff") else SleepTimer.cancelFade() }

    override fun onCleared() {
        if (Jam.host === jamHost) Jam.host = null
        Jam.onLibraryChanged = null
        Jam.onRemoteState = null
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
