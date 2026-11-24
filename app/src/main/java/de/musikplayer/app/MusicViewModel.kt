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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(private val app: Application) : AndroidViewModel(app) {

    // ---------- Mediathek ----------
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var folders by mutableStateOf<List<FolderInfo>>(emptyList())
        private set
    var playlistGrid by mutableStateOf(false)
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
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var currentIndex by mutableStateOf(0)
        private set
    /** Das Lied, das als Nächstes bzw. davor käme (für die Vorschau beim Wischen); null = keins. */
    var nextItem by mutableStateOf<MediaItem?>(null)
        private set
    var prevItem by mutableStateOf<MediaItem?>(null)
        private set

    private var lastCounted = -1L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Hörzähler: jedes gestartete Lied zählt einmal
            val id = mediaItem?.mediaId?.substringAfterLast(':')?.toLongOrNull() ?: return
            if (id == lastCounted && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
            lastCounted = id
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
        playlistGrid = Store.playlistGrid
        syncPlaylists()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            try {
                controller = f.get().also { it.addListener(listener) }
                syncFromPlayer()
            } catch (e: Exception) {
                message = "Wiedergabe-Dienst nicht erreichbar"
            }
        }, MoreExecutors.directExecutor())
        viewModelScope.launch {
            while (true) {
                controller?.let {
                    if (it.isPlaying) positionMs = it.currentPosition
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private fun syncFromPlayer() {
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
            withContext(Dispatchers.IO) { Library.load(app) }
            Store.initKnown(app, Library.tracks.map { it.path })
            refreshFromLibrary()
            loading = false
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

    private fun isQueued(m: MediaItem) = m.mediaMetadata.extras?.getBoolean("queued", false) == true

    private fun asQueued(m: MediaItem): MediaItem {
        val b = Bundle().apply { putBoolean("queued", true) }
        return m.buildUpon().setMediaMetadata(m.mediaMetadata.buildUpon().setExtras(b).build()).build()
    }

    /**
     * Spielt [list] ab Position [index]. Zufall wird hier selbst gemacht (nicht vom Player): bei jedem Start und
     * jedem Ein-/Ausschalten wird neu gemischt, und eingereihte Lieder bleiben immer direkt als Nächstes dran.
     */
    fun play(list: List<Track>, index: Int, context: String) {
        if (list.isEmpty()) return
        startItems(list.map { Library.mediaItem(it, context) }, index.coerceIn(0, list.lastIndex))
    }

    private fun startItems(items: List<MediaItem>, index: Int) {
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
        shuffle = true
        c.shuffleModeEnabled = false
        val items = list.map { Library.mediaItem(it, context) }
        baseList = items
        c.setMediaItems(items.shuffled(), 0, 0L)
        c.prepare()
        c.play()
    }

    /** Aktuelle Abspielposition, direkt vom Player (für ruckelfreie Anzeige, jedes Bild neu abgefragt). */
    fun livePositionMs(): Long = controller?.currentPosition ?: positionMs

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() {
        val c = controller ?: return
        shuffle = !shuffle
        c.shuffleModeEnabled = false
        message = if (shuffle) "Zufall an" else "Zufall aus"
        val from = c.currentMediaItemIndex + 1 + queuedCount
        val count = c.mediaItemCount
        if (from >= count) return
        val tail = (from until count).map { c.getMediaItemAt(it) }
        val newTail = if (shuffle) tail.shuffled() else {
            val curId = c.currentMediaItem?.mediaId
            val pos = baseList.indexOfFirst { it.mediaId == curId }
            if (pos >= 0) baseList.drop(pos + 1).filter { b -> tail.any { it.mediaId == b.mediaId } } else tail
        }
        if (newTail.size == tail.size) c.replaceMediaItems(from, count, newTail)
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------- Warteschlange ----------

    private fun itemsOf(list: List<Track>) = list.map { asQueued(Library.mediaItem(it, "q")) }

    /** Direkt nach dem aktuellen Lied (vor schon eingereihten). */
    fun playNext(list: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, itemsOf(list))
        message = "Als Nächstes eingereiht"
    }

    /** Hinten an die Warteschlange: nach dem aktuellen Lied und nach allem, was schon eingereiht ist. */
    fun addToQueue(list: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(c.currentMediaItemIndex + 1 + queuedCount, itemsOf(list))
        message = "Zur Warteschlange hinzugefügt"
    }

    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun clearQueue() { controller?.let { it.stop(); it.clearMediaItems() } }

    // ---------- Playlists (nur in der App, keine Dateikopien) ----------

    private fun syncPlaylists() {
        playlists = Store.playlists
        favoritePaths = Store.playlists.firstOrNull { it.id == FAV_ID }?.trackPaths?.toHashSet() ?: emptySet()
    }

    fun isFavorite(path: String) = path in favoritePaths

    /** Herz an/aus für ein Lied (Lieblingssongs). */
    fun toggleFavorite(path: String) {
        val now = Store.toggleFavorite(app, path)
        syncPlaylists()
        message = if (now) "Zu Lieblingssongs hinzugefügt" else "Aus Lieblingssongs entfernt"
    }

    // ---------- Dateinamen übernehmen / umbenennen (immer erst nach Bestätigung in der Oberfläche) ----------

    fun renameCandidates(): List<Track> = Library.renameCandidates()

    fun renameFiles(items: List<Pair<Track, String>>) {
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
    fun quickRename(t: Track, newBase: String) {
        val oldBase = t.fileName.substringBeforeLast('.')
        val ext = t.fileName.substringAfterLast('.', "")
        val expected = java.io.File(java.io.File(t.path).parentFile, Names.fileBaseName(newBase) + if (ext.isEmpty()) "" else ".$ext").path
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val res = Library.renameFiles(app, listOf(t to newBase))
                Library.load(app)
                res
            }
            refreshFromLibrary()
            if (dupes != null) dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            if (r.renamed == 0) { message = "Umbenennen fehlgeschlagen"; return@launch }
            val nu = Library.tracks.firstOrNull { it.path == expected }
            message = "Dateiname geändert"
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

    fun showPlaylistsAsGrid(grid: Boolean) {
        Store.setPlaylistGrid(app, grid)
        playlistGrid = grid
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
    fun setCover(id: String, uri: android.net.Uri? = null, url: String? = null) {
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

    /** Eigenes Cover für Lieder (auch ganze Alben): Galerie-Bild, Web-Bild oder (beides null) zurücksetzen. */
    fun setTrackCover(tracks: List<Track>, uri: android.net.Uri? = null, url: String? = null) {
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

    fun removeCover(id: String) {
        java.io.File(java.io.File(app.filesDir, "covers"), "$id.jpg").delete()
        Store.setCoverStamp(app, id, 0L)
        syncPlaylists()
    }

    /** Kontext der laufenden Wiedergabe, z. B. „p:<Playlist-ID>“ (für die Markierung „diese Playlist läuft“). */
    val playingContext: String? get() = current?.mediaId?.removePrefix("t:")?.substringBeforeLast(':')

    fun playPlaylist(p: Playlist) {
        val list = playlistTracks(p.id)
        if (list.isEmpty()) { message = "Die Playlist ist leer"; return }
        if (shuffle) playShuffled(list, "p:${p.id}") else play(list, 0, "p:${p.id}")
    }

    /** Spielt die Playlist, oder Pause/Weiter, wenn sie gerade läuft. */
    fun togglePlaylistPlay(p: Playlist) {
        if (playingContext == "p:${p.id}") togglePlay() else playPlaylist(p)
    }

    /** Fügt Lieder zu mehreren Playlists hinzu (Doppelte werden übersprungen) und meldet das Ergebnis. */
    fun addToPlaylists(ids: List<String>, list: List<Track>) {
        var added = 0
        for (id in ids) {
            val before = Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0
            Store.addToPlaylist(app, id, list.map { it.path }, true)
            added += (Store.playlists.firstOrNull { it.id == id }?.trackPaths?.size ?: 0) - before
        }
        syncPlaylists()
        val names = ids.mapNotNull { id -> Store.playlists.firstOrNull { it.id == id }?.name }
        message = when {
            added == 0 -> "Schon in der Playlist"
            names.size == 1 -> "Zu „${names[0]}“ hinzugefügt"
            else -> "Zu ${names.size} Playlists hinzugefügt"
        }
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
    fun assignFolder(folder: String, artist: String?) {
        Store.setFolderArtist(app, folder, artist)
        reindex()
    }

    fun assignTrack(path: String, artist: String?) {
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

    fun sortIntoFolders(list: List<Track>) {
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

    /** Name (Datei) und/oder Interpret in einem Schritt ändern. */
    fun editTrack(t: Track, newBaseName: String, artist: String?) {
        val nameChanged = newBaseName.isNotBlank() && newBaseName != t.fileName.substringBeforeLast('.')
        val artistChanged = (artist ?: "") != (if (t.artist == UNKNOWN_ARTIST) "" else t.artist)
        if (!nameChanged && !artistChanged) return
        loading = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                // erst Interpret (nach Pfad), dann Umbenennen (Store zieht den Pfad nach)
                if (artistChanged) Store.setTrackArtist(app, t.path, artist?.takeIf { it.isNotBlank() })
                val res = if (nameChanged) Library.renameFiles(app, listOf(t to newBaseName)) else null
                Library.load(app)
                res
            }
            refreshFromLibrary()
            if (dupes != null) dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            loading = false
            message = if (r != null && r.failed > 0) "Umbenennen fehlgeschlagen" else "Gespeichert"
        }
    }

    /** Interpret für viele Lieder auf einmal. */
    fun assignTracks(list: List<Track>, artist: String?) {
        if (list.isEmpty()) return
        Store.setTrackArtists(app, list.map { it.path }, artist?.takeIf { it.isNotBlank() })
        reindex()
        message = "Interpret für ${list.size} Lieder gesetzt"
    }

    /** Albumname für die Lieder ändern (nur in der App, Dateien bleiben unberührt). */
    fun renameAlbum(list: List<Track>, name: String) {
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

    private fun afterBlock(label: String, affected: List<Track>, undoAction: () -> Unit) {
        val ids = affected.map { it.id }.toHashSet()
        dropFromPlayer { it in ids }
        blockTick++
        reindex()
        message = label
        undo = undoAction
    }

    fun blockTracks(list: List<Track>) {
        if (list.isEmpty()) return
        val paths = list.map { it.path }
        Store.blockTracks(app, paths)
        afterBlock(if (list.size == 1) "Gesperrt: ${list[0].title}" else "${list.size} Lieder gesperrt", list) {
            Store.unblockTracks(app, paths); blockTick++; reindex()
        }
    }

    fun blockArtist(name: String) {
        val affected = tracks.filter { it.artist == name }
        Store.blockArtist(app, name)
        afterBlock("Gesperrt: $name", affected) { Store.unblockArtist(app, name); blockTick++; reindex() }
    }

    fun blockFolder(path: String) {
        val affected = tracks.filter { it.folder == path }
        Store.blockFolder(app, path)
        afterBlock("Ordner gesperrt: ${path.substringAfterLast('/')}", affected) { Store.unblockFolder(app, path); blockTick++; reindex() }
    }

    fun unblockTracks(paths: List<String>) { Store.unblockTracks(app, paths); blockTick++; reindex(); message = "Entsperrt" }
    fun unblockArtist(name: String) { Store.unblockArtist(app, name); blockTick++; reindex(); message = "Entsperrt: $name" }
    fun unblockFolder(path: String) { Store.unblockFolder(app, path); blockTick++; reindex(); message = "Ordner entsperrt" }

    // ---------- Filterliste für Namen, Suchverlauf ----------

    fun setStripStrings(list: List<String>) {
        Store.setStripStrings(app, list)
        blockTick++
        reindex()
    }

    fun addRecentSearch(q: String) { Store.addRecentSearch(app, q); blockTick++ }
    fun removeRecentSearch(q: String) { Store.removeRecentSearch(app, q); blockTick++ }

    fun setSkipSilence(on: Boolean) { Store.setSkipSilence(app, on) }

    /** Öffnet den Ordner der Datei im Dateimanager; wenn das nicht geht, wird der Pfad kopiert. */
    fun showInFolder(t: Track) {
        val path = t.folder
        val (vol, rel) = when {
            path.startsWith("/storage/emulated/0") -> "primary" to path.removePrefix("/storage/emulated/0").trim('/')
            path.startsWith("/storage/") -> path.removePrefix("/storage/").let { it.substringBefore('/') to it.substringAfter('/', "") }
            else -> "primary" to path.trim('/')
        }
        val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "$vol:$rel")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            val cm = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Pfad", path))
            message = "Kein Dateimanager gefunden – Pfad kopiert: $path"
        }
    }

    /** Wie oft ein Lied gespielt wurde. */
    fun plays(path: String): Int = Store.playCounts[path] ?: 0

    // ---------- Löschen (immer nach Bestätigung in der Oberfläche) ----------

    fun deleteTracks(list: List<Track>) {
        if (list.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val c = Library.deleteFiles(app, list)
                Library.load(app)
                c
            }
            refreshFromLibrary()
            dupes = dupes?.map { g -> g.copy(tracks = g.tracks.filter { t -> tracks.any { it.path == t.path } }) }
                ?.filter { it.tracks.size >= 2 }
            loading = false
            message = "$n Dateien gelöscht"
        }
    }

    /** Aufräumen (erweitert): erst löschen, dann umbenennen (vermeidet Namenskonflikte). Nur nach Bestätigung aufrufen. */
    fun cleanup(delete: List<Track>, rename: List<Pair<Track, String>>) {
        if (delete.isEmpty() && rename.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val (d, r) = withContext(Dispatchers.IO) {
                val d = if (delete.isNotEmpty()) Library.deleteFiles(app, delete) else 0
                val r = if (rename.isNotEmpty()) Library.renameFiles(app, rename).renamed else 0
                Library.load(app)
                d to r
            }
            refreshFromLibrary()
            dupes = withContext(Dispatchers.Default) { Dupes.find(Library.tracks) }
            loading = false
            message = "$d gelöscht, $r umbenannt"
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
        SleepTimer.startSleep(minutes)
    }

    fun cancelSleep() = SleepTimer.cancelSleep()

    // ---------- Ausfaden (Lautstärke sinkt linear über die eingestellte Zeit) ----------

    fun startFade(minutes: Int) {
        Store.setFadeMinutes(app, minutes)
        SleepTimer.startFade(minutes)
    }

    fun cancelFade() = SleepTimer.cancelFade()

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
