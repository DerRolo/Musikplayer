package de.musikplayer.app

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Wiedergabe-Dienst: Hintergrundwiedergabe, Benachrichtigung, Sperrbildschirm
 * und Browsing für Android Auto (Alle Titel / Interpreten / Playlists).
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var silenceTick: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Store.load(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        SleepTimer.player = player
        player.addListener(resumeSaver)
        Jam.player = player
        player.addListener(Jam.errorSkipper)
        Output.start(this)
        // Lautstärke-Angleichung beim Abspielen (für Formate, die nicht in der Datei geändert werden können)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = Normalize.onTrack(player, mediaItem, player.audioSessionId)
            override fun onAudioSessionIdChanged(audioSessionId: Int) = Normalize.onTrack(player, player.currentMediaItem, audioSessionId)
        })
        Spectrum.sessionIdProvider = { player.audioSessionId }
        // Tippen auf die Benachrichtigung / Mediensteuerung öffnet die App
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App aus der Übersicht weggewischt = Programm beenden, auch die Wiedergabe (und ein laufender Jam)
        Jam.shutdown()
        session?.player?.let { it.pause(); it.stop(); it.clearMediaItems() }
        stopSelf()
    }

    override fun onDestroy() {
        silenceTick?.let { handler.removeCallbacks(it) }
        session?.player?.removeListener(resumeSaver)
        session?.player?.removeListener(Jam.errorSkipper)
        if (Jam.player === session?.player) Jam.player = null
        SleepTimer.cancel()
        Normalize.release()
        SleepTimer.player = null
        Spectrum.stop()
        Spectrum.sessionIdProvider = null
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        exec.shutdown()
        super.onDestroy()
    }

    // ---------- Hilfen ----------

    private fun folderItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    /** "t:<Kontext>:<Titel-ID>" -> (Kontext, Titel-ID). Der Kontext darf selbst Doppelpunkte enthalten. */
    private fun parseId(id: String): Pair<String, Long>? {
        if (!id.startsWith("t:")) return null
        val last = id.lastIndexOf(':')
        if (last <= 1) return null
        val tid = id.substring(last + 1).toLongOrNull() ?: return null
        return id.substring(2, last) to tid
    }

    private fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        // Fremdtitel („Öffnen mit“): Abspiel-URI steckt in den Anfrage-Daten
        if (External.isExternal(item)) {
            val uri = item.requestMetadata.mediaUri ?: return item
            return item.buildUpon().setUri(uri).build()
        }
        val (ctx, tid) = parseId(item.mediaId) ?: return item
        val t = Library.tracks.firstOrNull { it.id == tid } ?: return item
        return Library.mediaItem(t, ctx)
    }

    // ---------- Wiedergabe fortsetzen (Kopfhörer-/Bluetooth-Taste, System-Mediensteuerung) ----------

    /** Speichert Lied, Kontext und Position bei jedem Liedwechsel und jeder Pause. Fremdtitel werden übersprungen. */
    private val resumeSaver = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = save(0L)
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) save(session?.player?.currentPosition ?: 0L)
        }
        private fun save(pos: Long) {
            val item = session?.player?.currentMediaItem ?: return
            val (ctx, tid) = parseId(item.mediaId) ?: return
            val t = Library.tracks.firstOrNull { it.id == tid } ?: return
            Store.setResume(this@PlaybackService, t.path, ctx, pos.coerceAtLeast(0L))
        }
    }

    /** Zuletzt gespieltes Lied samt seiner Liste; ohne gespeicherten Stand: alle Titel ab dem ersten. */
    private fun resumeItems(): MediaSession.MediaItemsWithStartPosition? {
        Library.ensureLoaded(applicationContext)
        val path = Store.resolve(Store.resumePath)
        val ctx = Store.resumeContext
        val track = Library.tracks.firstOrNull { it.path == path }
        if (track != null) {
            val list = Library.tracksFor(ctx).takeIf { l -> l.any { it.id == track.id } } ?: listOf(track)
            val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val useCtx = ctx.ifEmpty { "all" }
            return MediaSession.MediaItemsWithStartPosition(list.map { Library.mediaItem(it, useCtx) }, idx, Store.resumePosMs)
        }
        val all = Library.tracksFor("all")
        if (all.isEmpty()) return null
        return MediaSession.MediaItemsWithStartPosition(all.map { Library.mediaItem(it, "all") }, 0, 0L)
    }

    // ---------- Browsing (Android Auto) ----------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            // System-Mediensteuerung fragt beim Fortsetzen nach dem „zuletzt gespielt“-Stamm
            if (params?.isRecent == true) Futures.immediateFuture(LibraryResult.ofItem(folderItem("recent", "Zuletzt gespielt"), params))
            else Futures.immediateFuture(LibraryResult.ofItem(folderItem("root", "Musik"), params))

        /** Play-Taste am Kopfhörer/Auto oder in der System-Mediensteuerung, obwohl nichts geladen ist. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            exec.submit(Callable<MediaSession.MediaItemsWithStartPosition> {
                resumeItems() ?: throw UnsupportedOperationException("Keine Lieder zum Fortsetzen")
            })

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            exec.submit(Callable<LibraryResult<ImmutableList<MediaItem>>> {
                val items: List<MediaItem> = try {
                Library.ensureLoaded(applicationContext)
                when {
                    parentId == "root" -> listOf(
                        folderItem("songs", "Alle Titel"),
                        folderItem("artists", "Interpreten"),
                        folderItem("playlists", "Playlists")
                    )
                    parentId == "songs" -> Library.tracksFor("all").map { Library.mediaItem(it, "all") }
                    parentId == "recent" -> resumeItems()?.let { r -> listOfNotNull(r.mediaItems.getOrNull(r.startIndex)) }.orEmpty()
                    parentId == "artists" -> Library.byArtist().keys.map { folderItem("a:$it", it) }
                    parentId == "playlists" -> Store.playlists.map { folderItem("p:${it.id}", it.name) }
                    parentId.startsWith("a:") || parentId.startsWith("p:") ->
                        Library.tracksFor(parentId).map { Library.mediaItem(it, parentId) }
                    else -> emptyList()
                }
                } catch (e: Exception) { emptyList() }
                val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
                LibraryResult.ofItemList(ImmutableList.copyOf(items.drop(from).take(pageSize)), params)
            })

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            exec.submit(Callable<LibraryResult<MediaItem>> {
                Library.ensureLoaded(applicationContext)
                val parsed = parseId(mediaId)
                val track = parsed?.let { (_, tid) -> Library.tracks.firstOrNull { it.id == tid } }
                when {
                    parsed != null && track != null -> LibraryResult.ofItem(Library.mediaItem(track, parsed.first), null)
                    mediaId == "root" -> LibraryResult.ofItem(folderItem("root", "Musik"), null)
                    mediaId == "recent" -> LibraryResult.ofItem(folderItem("recent", "Zuletzt gespielt"), null)
                    mediaId == "songs" -> LibraryResult.ofItem(folderItem("songs", "Alle Titel"), null)
                    mediaId == "artists" -> LibraryResult.ofItem(folderItem("artists", "Interpreten"), null)
                    mediaId == "playlists" -> LibraryResult.ofItem(folderItem("playlists", "Playlists"), null)
                    mediaId.startsWith("a:") -> LibraryResult.ofItem(folderItem(mediaId, mediaId.substring(2)), null)
                    mediaId.startsWith("p:") -> LibraryResult.ofItem(
                        folderItem(mediaId, Store.playlists.firstOrNull { it.id == mediaId.substring(2) }?.name ?: "Playlist"),
                        null
                    )
                    else -> LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            })

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            exec.submit(Callable<MutableList<MediaItem>> {
                Library.ensureLoaded(applicationContext)
                mediaItems.map { resolve(it) }.toMutableList()
            })

        /** Tippt man im Auto auf ein einzelnes Lied, wird die ganze Liste (Interpret, Playlist, Alle) in die Warteschlange gelegt. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            exec.submit(Callable<MediaSession.MediaItemsWithStartPosition> {
                Library.ensureLoaded(applicationContext)
                if (mediaItems.size == 1) {
                    val parsed = parseId(mediaItems[0].mediaId)
                    if (parsed != null) {
                        val list = Library.tracksFor(parsed.first)
                        val idx = list.indexOfFirst { it.id == parsed.second }
                        if (idx >= 0) {
                            return@Callable MediaSession.MediaItemsWithStartPosition(
                                list.map { Library.mediaItem(it, parsed.first) }, idx, startPositionMs
                            )
                        }
                    }
                }
                MediaSession.MediaItemsWithStartPosition(mediaItems.map { resolve(it) }, startIndex, startPositionMs)
            })
    }
}
