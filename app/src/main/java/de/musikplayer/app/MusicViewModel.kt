package de.musikplayer.app

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
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
    var sortBy by mutableStateOf(SortBy.Title)
        private set
    var message by mutableStateOf<String?>(null)
    var artistGrid by mutableStateOf(true)
        private set
    var favoritePaths by mutableStateOf<Set<String>>(emptySet())
        private set

    // ---------- Duplikate ----------
    var dupes by mutableStateOf<List<DupGroup>?>(null)
        private set
    var dupBusy by mutableStateOf(false)
        private set

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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
    }

    init {
        Store.load(app)
        sortBy = Store.sortBy
        artistGrid = Store.artistGrid
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
        shuffle = c.shuffleModeEnabled
        repeat = when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.All
            Player.REPEAT_MODE_ONE -> RepeatMode.One
            else -> RepeatMode.Off
        }
        currentIndex = c.currentMediaItemIndex
        nextItem = c.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { c.getMediaItemAt(it) }
        prevItem = c.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { c.getMediaItemAt(it) }
        queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
    }

    // ---------- Laden ----------

    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Library.load(app) }
            refreshFromLibrary()
            loading = false
        }
    }

    private fun refreshFromLibrary() {
        tracks = Library.tracks
        syncPlaylists()
        folders = Library.folders()
        sortBy = Store.sortBy
    }

    val sortedTracks: List<Track> get() = Library.sorted(tracks, sortBy)

    fun setSort(s: SortBy) {
        Store.setSort(app, s)
        sortBy = s
    }

    // ---------- Wiedergabe-Steuerung ----------

    /** Spielt [list] ab Position [index]; der Kontext bestimmt die Warteschlange (alles aus derselben Liste). */
    fun play(list: List<Track>, index: Int, context: String) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { Library.mediaItem(it, context) }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>, context: String) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        c.setMediaItems(list.map { Library.mediaItem(it, context) }, (list.indices).random(), 0L)
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

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------- Warteschlange ----------

    private fun itemsOf(list: List<Track>) = list.map { Library.mediaItem(it, "q") }

    fun playNext(list: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(c.currentMediaItemIndex + 1, itemsOf(list))
        message = "Als Nächstes eingereiht"
    }

    fun addToQueue(list: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(list, 0, "q"); return }
        c.addMediaItems(itemsOf(list))
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
            message = "${r.renamed} Dateien umbenannt" + if (r.failed > 0) ", ${r.failed} fehlgeschlagen" else ""
        }
    }

    fun createPlaylist(name: String, firstTracks: List<Track> = emptyList()) {
        if (name.isBlank()) return
        val p = Store.createPlaylist(app, name)
        if (firstTracks.isNotEmpty()) Store.addToPlaylist(app, p.id, firstTracks.map { it.path })
        syncPlaylists()
    }

    fun renamePlaylist(id: String, name: String) {
        if (name.isBlank()) return
        Store.renamePlaylist(app, id, name)
        syncPlaylists()
    }

    fun deletePlaylist(id: String) {
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
