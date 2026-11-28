package de.musikplayer.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Was der Host mit seinem Player machen kann (vom ViewModel umgesetzt, damit Shuffle/Warteschlange wie gewohnt laufen). */
interface JamHost {
    fun jamToggle()
    fun jamNext()
    fun jamPrevious()
    fun jamSeek(ms: Long)
    fun jamPlay(items: List<MediaItem>, index: Int)
    fun jamAdd(items: List<MediaItem>)
    fun jamShuffle()
    fun jamRepeat()
    /** Warteschlange übernehmen (Ausgabe wechselt auf dieses Gerät): ab [index] an Stelle [posMs], spielend oder pausiert. */
    fun jamLoad(items: List<MediaItem>, index: Int, posMs: Long, play: Boolean)
    val jamShuffleOn: Boolean
}

/**
 * Jam: Handys mit der App im selben WLAN finden sich, hören zusammen und können Musik voneinander übernehmen.
 *
 * - Teil A: Bibliothek der anderen ansehen und Lieder, Alben, Interpreten oder Playlists übernehmen
 *   (Dateien + Interpret/Album/Cover/Playlist; Duplikate werden vorher erkannt und nicht doppelt geladen).
 * - Teil B: Der Host spielt, alle Gäste hören synchron mit (auch Lieder, die sie nicht besitzen: Streaming im WLAN).
 *   Jeder kann starten, stoppen, springen und Lieder oder Playlists einreihen.
 *
 * Sicherheit: Alles außer „Wer bist du?“ braucht den Jam-Schlüssel, den nur angenommene Mitglieder bekommen.
 */
object Jam {

    enum class Role { None, Host, Guest }

    private const val SERVICE_TYPE = "_musikplayer._tcp"
    private const val ITEM_PREFIX = "ext:jam:"

    // ---------- Zustand für die Oberfläche ----------

    var role by mutableStateOf(Role.None)
        private set
    /** Gast: letzter Stand vom Host. Host: eigener Stand. */
    var state by mutableStateOf<JamState?>(null)
        private set
    var peers by mutableStateOf<List<JamPeer>>(emptyList())
        private set
    /** Host: Beitrittsanfragen von Leuten, die vorher entfernt wurden. */
    var requests by mutableStateOf<List<JamRequest>>(emptyList())
        private set
    /** Gast: Name des Hosts, solange die Verbindung aufgebaut wird. */
    var waitingFor by mutableStateOf<String?>(null)
        private set
    /** Pop-up: jemand in der Nähe. */
    var invite by mutableStateOf<JamPeer?>(null)
        private set
    var transfer by mutableStateOf<Transfer?>(null)
        private set
    /** Meldung für die Leiste unten (wird von der Oberfläche abgeholt). */
    var message by mutableStateOf<String?>(null)
    /** Zählt hoch, wenn eine übernommene Bibliothek geändert wurde (Anzeigen neu berechnen). */
    var libraryTick by mutableStateOf(0)
        private set

    data class Transfer(val total: Int, val done: Int, val skipped: Int, val failed: Int, val current: String, val finished: Boolean)

    // ---------- Verbindung zur App ----------

    /** Wird vom Wiedergabe-Dienst gesetzt. Nur auf dem Haupt-Thread benutzen. */
    @Volatile var player: Player? = null
    @Volatile var host: JamHost? = null
    var onLibraryChanged: (() -> Unit)? = null
    /**
     * Neuer Jam-Stand für die Player-Anzeige, wenn dieses Gerät gerade NICHT die Ausgabe ist (null = selbst Ausgabe
     * bzw. kein Jam: dann zeigt der Player wieder den eigenen Player).
     */
    var onRemoteState: ((JamState?) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context
    private var server: JamServer? = null

    val myId: String get() = Store.deviceId
    /** Name des Geräts (Android-Einstellungen) – Vorgabe, solange im Profil kein eigener Name steht. */
    var deviceName: String = "Handy"
        private set
    /** Angezeigter Name im Jam: Profilname, sonst Gerätename. */
    val myName: String get() = Store.profileName.ifBlank { deviceName }

    // Host
    private var jamId: String = ""
    @Volatile private var key: String? = null
    private val members = ConcurrentHashMap<String, JamMember>()
    /** Vom Host rausgeworfen: können diesem Jam nicht wieder beitreten (ein neuer Jam beginnt frisch). */
    private val kicked = ConcurrentHashMap.newKeySet<String>()
    /** Anfrage abgelehnt (wird dem Gast einmal gemeldet). */
    private val declined = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var snapshot: JamState? = null
    private var ticker: Runnable? = null
    /** Host: welches Mitglied die gemeinsame Ausgabe ist. */
    @Volatile private var speakerId: String = ""
    /** Host: zuletzt gemeldeter Wiedergabe-Stand jedes Mitglieds (Ausgabegeräte, und beim Abspielenden: was läuft). */
    private val engines = ConcurrentHashMap<String, EngineInfo>()
    private var enginePoll: Job? = null
    @Volatile private var switching = false

    // Gast
    @Volatile private var hostUrl: String = ""
    private var syncJob: Job? = null
    private var joinJob: Job? = null
    @Volatile private var clockOffset = 0L
    private var bestRtt = Long.MAX_VALUE

    /** Host: Lied eines Gasts nicht erreichbar (z. B. Gast hat den Jam verlassen) → zum nächsten springen statt hängen bleiben. */
    val errorSkipper = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val p = player ?: return
            if (!isJamItem(p.currentMediaItem)) return
            if (active && isSpeaker && p.hasNextMediaItem()) { p.seekToNextMediaItem(); p.prepare(); p.play() }
        }
    }

    fun isJamItem(item: MediaItem?): Boolean = item?.mediaId?.startsWith(ITEM_PREFIX) == true
    /** Jam-Lied von einem anderen Gerät oder eigenes Lied, das im Jam gestartet wurde. */
    private fun isJamContext(item: MediaItem?): Boolean = isJamItem(item) || item?.mediaId?.startsWith("t:jam:") == true
    val active: Boolean get() = role != Role.None
    /** Spielt dieses Gerät gerade für den Jam ab (ist es die gemeinsame Ausgabe)? */
    val isSpeaker: Boolean get() = when (role) {
        Role.Host -> speakerId == myId || speakerId.isEmpty()
        Role.Guest -> state?.speaker == myId
        Role.None -> false
    }

    // ---------- Start / Sichtbarkeit ----------

    private var started = false
    private var foreground = false

    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        Store.load(app)
        Store.ensureDeviceId(app)
        deviceName = (try { Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) } catch (e: Exception) { null })
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Handy"
        val s = JamServer(::route)
        try { s.start(); server = s } catch (e: Exception) { server = null }
        if (Store.jamVisible) register()
    }

    /** App sichtbar/unsichtbar: Suche nach anderen nur, solange die App offen ist. */
    fun setForeground(ctx: Context, on: Boolean) {
        start(ctx)
        foreground = on
        if (on && Store.jamVisible) { discover(); startPeerRefresh() } else { stopDiscovery(); peerJob?.cancel() }
    }

    fun setVisible(ctx: Context, on: Boolean) {
        start(ctx)
        Store.setJamVisible(app, on)
        if (on) { register(); if (foreground) { discover(); startPeerRefresh() } }
        else { unregister(); stopDiscovery(); peerJob?.cancel(); peers = emptyList(); invite = null }
    }

    /** App wird beendet (aus den letzten Apps gewischt). */
    fun shutdown() {
        if (!started) return
        if (role == Role.Host) endJam() else if (role == Role.Guest) leave(silent = true)
        stopDiscovery()
        unregister()
    }

    // ---------- Finden im WLAN (NSD) ----------

    private val nsd: NsdManager? get() = if (::app.isInitialized) app.getSystemService(Context.NSD_SERVICE) as? NsdManager else null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val serviceToPeer = ConcurrentHashMap<String, String>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var peerJob: Job? = null
    private val dismissed = HashSet<String>()

    private fun register() {
        val srvPort = server?.port ?: return
        if (regListener != null) return
        val info = NsdServiceInfo()
        info.serviceName = "MP-$myId"
        info.serviceType = SERVICE_TYPE
        info.port = srvPort
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { main.post { regListener = null } }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        regListener = l
        try { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { regListener = null }
    }

    private fun unregister() {
        val l = regListener ?: return
        regListener = null
        try { nsd?.unregisterService(l) } catch (e: Exception) { }
    }

    private fun discover() {
        if (discListener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { discListener = null } }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == "MP-$myId") return
                main.post { resolveQueue.addLast(info); nextResolve() }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val id = serviceToPeer.remove(info.serviceName) ?: return
                main.post { peers = peers.filter { it.id != id }; if (invite?.id == id) invite = null }
            }
        }
        discListener = l
        try { nsd?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { discListener = null }
    }

    private fun stopDiscovery() {
        val l = discListener ?: return
        discListener = null
        try { nsd?.stopServiceDiscovery(l) } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun nextResolve() {
        if (resolving) return
        val info = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd?.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) { main.post { resolving = false; nextResolve() } }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val addr = hostAddress(i)
                    val name = i.serviceName
                    main.post { resolving = false; nextResolve() }
                    if (addr != null) scope.launch { probe("http://$addr:${i.port}", name) }
                }
            })
        } catch (e: Exception) { resolving = false }
    }

    @Suppress("DEPRECATION")
    private fun hostAddress(i: NsdServiceInfo): String? {
        val list = if (Build.VERSION.SDK_INT >= 34) i.hostAddresses else listOfNotNull(i.host)
        val v4 = list.firstOrNull { it is Inet4Address } ?: return null
        return v4.hostAddress
    }

    /** „Wer bist du?“ – fragt ein gefundenes Gerät ab und nimmt es in die Liste auf. */
    private fun probe(url: String, serviceName: String?) {
        val o = try { JSONObject(http("$url/info", timeout = 3000)) } catch (e: Exception) { return }
        val id = o.optString("id")
        if (id.isBlank() || id == myId) return
        val peer = JamPeer(id, o.optString("name", "Handy"), url, o.optString("jam").takeIf { it.isNotBlank() })
        if (serviceName != null) serviceToPeer[serviceName] = id
        main.post { updatePeer(peer) }
    }

    private fun updatePeer(p: JamPeer) {
        peers = peers.filter { it.id != p.id } + p
        // Pop-up: nur wenn man selbst in keinem Jam ist und es für diese Lage nicht schon weggeklickt wurde
        if (role == Role.None && waitingFor == null && invite == null && inviteKey(p) !in dismissed) invite = p
        if (invite?.id == p.id && role == Role.None) invite = if (inviteKey(p) in dismissed) null else p
    }

    private fun inviteKey(p: JamPeer) = p.id + "|" + (p.hostingJam ?: "")

    fun dismissInvite() { invite?.let { dismissed += inviteKey(it) }; invite = null }

    /** Regelmäßig nachfragen, ob jemand einen Jam gestartet hat (für das Pop-up „Jam beitreten“). */
    private fun startPeerRefresh() {
        if (peerJob?.isActive == true) return
        peerJob = scope.launch {
            val fails = HashMap<String, Int>()
            while (isActive) {
                delay(4000)
                for (p in peers.toList()) {
                    val ok = try {
                        val o = JSONObject(http("${p.url}/info", timeout = 2500))
                        val np = p.copy(name = o.optString("name", p.name), hostingJam = o.optString("jam").takeIf { it.isNotBlank() })
                        if (np != p) main.post { updatePeer(np) }
                        true
                    } catch (e: Exception) { false }
                    if (ok) fails.remove(p.id) else {
                        val n = (fails[p.id] ?: 0) + 1
                        fails[p.id] = n
                        if (n >= 3) main.post { peers = peers.filter { it.id != p.id }; if (invite?.id == p.id) invite = null }
                    }
                }
            }
        }
    }

    // ---------- Host ----------

    fun createJam() {
        if (role != Role.None) return
        jamId = UUID.randomUUID().toString().take(8)
        key = UUID.randomUUID().toString().replace("-", "")
        members.clear(); kicked.clear(); declined.clear()
        requests = emptyList()
        role = Role.Host
        invite = null
        speakerId = myId
        engines.clear()
        startEnginePoll()
        val t = object : Runnable {
            override fun run() { takeSnapshot(); main.postDelayed(this, 500) }
        }
        ticker = t
        main.post(t)
        message = "Jam gestartet – andere im WLAN können jetzt beitreten"
    }

    /** Host: Anfrage einer vorher entfernten Person annehmen. */
    fun accept(r: JamRequest) {
        kicked.remove(r.id)
        members[r.id] = JamMember(r.id, r.name, r.url)
        requests = requests.filter { it.id != r.id }
        takeSnapshot()
        message = "${r.name} ist wieder im Jam"
    }

    fun decline(r: JamRequest) {
        declined += r.id
        requests = requests.filter { it.id != r.id }
    }

    /** Host: Mitglied aus dem Jam entfernen. */
    fun kick(id: String) {
        val m = members.remove(id) ?: return
        kicked += id
        memberGone(id)
        takeSnapshot()
        message = "${m.name} wurde aus dem Jam entfernt"
    }

    fun endJam() {
        if (role != Role.Host) return
        ticker?.let { main.removeCallbacks(it) }
        ticker = null
        enginePoll?.cancel()
        engines.clear()
        // War die Ausgabe woanders, läuft hier nichts – sonst spielt das eigene Handy einfach weiter
        val wasRemote = speakerId != myId
        speakerId = ""
        key = null
        members.clear()
        requests = emptyList()
        snapshot = null
        state = null
        role = Role.None
        libraries.clear()
        if (wasRemote) onRemoteState?.invoke(null)
        message = "Jam beendet"
    }

    private fun clipStart(item: MediaItem?): Long = item?.clippingConfiguration?.startPositionMs ?: 0L

    /** Was gerade läuft, als Eintrag für die anderen (Fremddateien ohne Bibliothek lassen sich nicht teilen). */
    private fun entryOf(item: MediaItem?): JamEntry? {
        if (item == null) return null
        val queued = item.mediaMetadata.extras?.getBoolean("queued", false) == true
        if (isJamItem(item)) {
            val raw = item.mediaMetadata.extras?.getString("jam") ?: item.requestMetadata.extras?.getString("jam")
            raw?.let { r -> try { return JamEntry.from(JSONObject(r)).copy(queued = queued) } catch (e: Exception) { } }
            // Ersatz: aus der Media-ID „ext:jam:<Gerät>:<ID>:x“
            val rest = item.mediaId.removePrefix(ITEM_PREFIX).removeSuffix(":x")
            val owner = rest.substringBeforeLast(':')
            val id = rest.substringAfterLast(':').toLongOrNull() ?: return null
            val md = item.mediaMetadata
            return JamEntry(owner, id, md.title?.toString().orEmpty(), md.artist?.toString().orEmpty(), 0L, queued = queued)
        }
        if (!item.mediaId.startsWith("t:")) return null
        val id = item.mediaId.substringAfterLast(':').toLongOrNull() ?: return null
        val t = Library.tracks.firstOrNull { it.id == id } ?: return null
        // Wer es eingereiht hat, steht in den Zusatzdaten; sonst hat das abspielende Handy es selbst gestartet
        val by = item.mediaMetadata.extras?.getString("jam")?.let { try { JSONObject(it).optString("by") } catch (e: Exception) { null } }
        return JamEntry.of(myId, t).copy(by = by?.takeIf { it.isNotBlank() } ?: myId, queued = queued)
    }

    /** Was der eigene Player gerade spielt, samt eigener Ausgabegeräte (nur Haupt-Thread). */
    private fun localEngine(maxNext: Int): EngineInfo {
        val p = player
        val cur = p?.currentMediaItem
        val upcoming = ArrayList<JamEntry>()
        if (p != null) {
            var i = p.currentMediaItemIndex + 1
            while (i < p.mediaItemCount && upcoming.size < maxNext) { entryOf(p.getMediaItemAt(i))?.let { upcoming += it }; i++ }
        }
        // Spielt wirklich (nicht nur „soll spielen“): nach einem Anruf oder Video in einer anderen App zeigen alle „Pause“
        val playing = p != null && (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING))
        return EngineInfo(
            Output.devices, Output.selected, Output.active, entryOf(cur), upcoming, playing,
            (p?.currentPosition ?: 0L) + clipStart(cur), host?.jamShuffleOn ?: false, p?.repeatMode ?: Player.REPEAT_MODE_OFF,
            System.currentTimeMillis(), Store.devJamEdit, myName,
            SleepTimer.sleepRemainingMs.value, SleepTimer.fadeRemainingMs.value
        )
    }

    /** Wiedergabe-Stand auf dem Haupt-Thread abfragen (für Anfragen aus dem Netz). */
    private fun localEngineBlocking(maxNext: Int): EngineInfo? {
        var out: EngineInfo? = null
        val done = CountDownLatch(1)
        main.post { try { out = localEngine(maxNext) } finally { done.countDown() } }
        done.await(1500, TimeUnit.MILLISECONDS)
        return out
    }

    /** Host: Stand aller Mitglieder abfragen (Ausgabegeräte; beim Abspielenden zusätzlich, was läuft). */
    private fun startEnginePoll() {
        enginePoll?.cancel()
        enginePoll = scope.launch {
            while (isActive && role == Role.Host) {
                for (m in members.values.toList()) {
                    try {
                        val t0 = System.currentTimeMillis()
                        val text = http("${m.url}/engine/state?k=${q(key ?: "")}&id=$myId", timeout = 2500)
                        val t1 = System.currentTimeMillis()
                        val e = EngineInfo.from(JSONObject(text), (t0 + t1) / 2)
                        engines[m.id] = e
                        // Profilname geändert? Mitgliederliste nachziehen
                        if (e.name.isNotBlank() && e.name != m.name) members[m.id] = m.copy(name = e.name)
                    } catch (e: Exception) {
                        // Nicht erreichbar: alter Stand bleibt kurz stehen
                    }
                }
                delay(600)
            }
        }
    }

    private fun takeSnapshot() {
        val now = System.currentTimeMillis()
        val all = listOf(JamMember(myId, myName, "")) + members.values.sortedBy { it.name }
        val sp = speakerId.ifEmpty { myId }
        val e = if (sp == myId) localEngine(30) else engines[sp]
        val outs = ArrayList<JamOutput>()
        Output.devices.forEach { outs += JamOutput.of(myId, myName, it) }
        for (m in members.values.sortedBy { it.name }) engines[m.id]?.outputs?.forEach { outs += JamOutput.of(m.id, m.name, it) }
        val s = JamState(
            jamId, myId, myName, all, e?.current, e?.upcoming.orEmpty(), e?.playing == true,
            e?.positionMs ?: 0L, e?.snapTime ?: now, now,
            e?.shuffle ?: false, e?.repeat ?: Player.REPEAT_MODE_OFF,
            sp, outs, "$sp/${e?.selected ?: Output.AUTO}", "$sp/${e?.active ?: Output.AUTO}",
            // Bearbeiten nur, wenn ALLE es in den Developer Options erlaubt haben
            Store.devJamEdit && members.keys.all { engines[it]?.editOk == true },
            e?.sleepLeft ?: 0L, e?.fadeLeft ?: 0L
        )
        snapshot = s
        if (s != state) state = s
        // Ist die Ausgabe bei jemand anderem, zeigt der eigene Player den gemeinsamen Stand an
        if (sp != myId) onRemoteState?.invoke(s)
    }

    /** Befehl auf dem EIGENEN Player ausführen (dieses Gerät ist die Ausgabe). */
    private fun runCommand(o: JSONObject) {
        val p = player
        val h = host
        when (o.optString("cmd")) {
            "toggle" -> if (p?.isPlaying == true) p.pause() else resume()
            "play" -> resume()
            "pause" -> p?.pause()
            "next" -> if (h != null) h.jamNext() else p?.seekToNextMediaItem()
            "prev" -> if (h != null) h.jamPrevious() else p?.seekToPreviousMediaItem()
            "seek" -> p?.let { it.seekTo((o.optLong("ms") - clipStart(it.currentMediaItem)).coerceAtLeast(0L)) }
            "shuffle" -> h?.jamShuffle()
            "repeat" -> if (h != null) h.jamRepeat() else p?.let { it.repeatMode = (it.repeatMode + 1) % 3 }
            // Warteschlange: Position relativ zum laufenden Lied, zur Sicherheit mit Lied-Schlüssel geprüft
            "move", "remove", "jump" -> if (p != null) {
                val cur = p.currentMediaItemIndex
                val from = cur + o.optInt("from")
                val k = o.optString("key")
                if (from in 0 until p.mediaItemCount && (k.isEmpty() || entryOf(p.getMediaItemAt(from))?.key == k)) {
                    when (o.optString("cmd")) {
                        "move" -> { val to = (cur + o.optInt("to")).coerceIn(cur + 1, p.mediaItemCount - 1); if (from != cur) p.moveMediaItem(from, to) }
                        "remove" -> if (from != cur) p.removeMediaItem(from)
                        "jump" -> { p.seekToDefaultPosition(from); resume() }
                    }
                }
            }
            "sleep" -> SleepTimer.startSleep(o.optInt("min", 30))
            "sleepOff" -> SleepTimer.cancelSleep()
            "fade" -> SleepTimer.startFade(o.optInt("min", 30))
            "fadeOff" -> SleepTimer.cancelFade()
            "output" -> Output.select(o.optInt("id", Output.AUTO))
            "stop" -> player?.let { it.pause(); it.clearMediaItems() }
            "playNow" -> {
                val items = JamEntry.list(o.optJSONArray("items")).mapNotNull { itemFor(it) }
                val i = o.optInt("index").coerceIn(0, maxOf(0, items.lastIndex))
                if (items.isNotEmpty()) { if (h != null) h.jamPlay(items, i) else p?.let { it.setMediaItems(items, i, 0L); it.prepare(); it.play() } }
            }
            "add" -> {
                val items = JamEntry.list(o.optJSONArray("items")).mapNotNull { itemFor(it) }
                if (items.isNotEmpty()) { if (h != null) h.jamAdd(items) else p?.let { it.addMediaItems(items); if (it.playbackState == Player.STATE_IDLE) it.prepare() } }
            }
            "load" -> {
                val items = JamEntry.list(o.optJSONArray("items")).mapNotNull { itemFor(it) }
                if (items.isNotEmpty()) {
                    val i = o.optInt("index").coerceIn(0, items.lastIndex)
                    val pos = (o.optLong("pos") - clipStart(items[i])).coerceAtLeast(0L)
                    if (h != null) h.jamLoad(items, i, pos, o.optBoolean("play"))
                    else p?.let { it.setMediaItems(items, i, pos); it.prepare(); it.playWhenReady = o.optBoolean("play") }
                }
            }
        }
        if (role == Role.Host) takeSnapshot()
    }

    /**
     * Weiterspielen – auch nach einer Unterbrechung (Anruf, Video in einer anderen App): Der Player wird neu
     * vorbereitet und fordert den Audio-Fokus erneut an. Während eines laufenden Anrufs gibt Android ihn nicht frei;
     * danach geht es mit dem nächsten „Play“ weiter.
     */
    private fun resume() {
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        if (p.playbackState == Player.STATE_ENDED) p.seekToDefaultPosition()
        if (p.playWhenReady && !p.isPlaying) p.pause()   // war „unterdrückt“: einmal neu anstoßen
        p.play()
    }

    /** Host: Befehl dorthin leiten, wo gespielt wird (eigener Player oder Handy eines Mitglieds). */
    private fun dispatch(o: JSONObject) {
        if (o.optString("cmd") == "output") { selectOutput(o.optString("member", myId), o.optInt("id", Output.AUTO)); return }
        val sp = speakerId.ifEmpty { myId }
        if (sp == myId) { runCommand(o); return }
        val url = members[sp]?.url ?: run { speakerId = myId; runCommand(o); return }
        scope.launch {
            try { http("$url/engine/cmd?k=${q(key ?: "")}&id=$myId", o.toString()) }
            catch (e: Exception) { withContext(Dispatchers.Main) { message = "${memberName(sp)} ist gerade nicht erreichbar" } }
        }
    }

    private fun engineCmd(member: String, o: JSONObject) {
        if (member == myId) { val done = CountDownLatch(1); main.post { try { runCommand(o) } finally { done.countDown() } }; done.await(3, TimeUnit.SECONDS); return }
        val url = members[member]?.url ?: return
        http("$url/engine/cmd?k=${q(key ?: "")}&id=$myId", o.toString(), timeout = 6000)
    }

    /**
     * Host: gemeinsame Ausgabe wählen. Liegt das Gerät bei einem anderen Mitglied, zieht die Wiedergabe dorthin um:
     * Warteschlange + Stelle + Play/Pause werden übergeben, beim bisherigen Gerät wird gestoppt.
     */
    private fun selectOutput(member: String, devId: Int) {
        val sp = speakerId.ifEmpty { myId }
        if (member == sp) {
            scope.launch { try { engineCmd(member, JSONObject().put("cmd", "output").put("id", devId)) } catch (e: Exception) { } }
            return
        }
        if (switching) return
        if (member != myId && !members.containsKey(member)) return
        switching = true
        scope.launch {
            try {
                // 1. Stand beim bisherigen Gerät holen (bis zu 300 Lieder Warteschlange)
                val e: EngineInfo? = if (sp == myId) localEngineBlocking(300) else {
                    val url = members[sp]?.url
                    if (url == null) null else try {
                        val t0 = System.currentTimeMillis()
                        val text = http("$url/engine/state?k=${q(key ?: "")}&id=$myId&full=1", timeout = 4000)
                        EngineInfo.from(JSONObject(text), (t0 + System.currentTimeMillis()) / 2)
                    } catch (x: Exception) { null }
                }
                // 2. dort anhalten
                try { engineCmd(sp, JSONObject().put("cmd", "stop")) } catch (x: Exception) { }
                // 3. beim neuen Gerät an derselben Stelle weitermachen
                val cur = e?.current
                if (e != null && cur != null) {
                    val elapsed = if (e.playing) System.currentTimeMillis() - e.snapTime else 0L
                    val load = JSONObject().put("cmd", "load")
                        .put("items", JamEntry.array(listOf(cur) + e.upcoming)).put("index", 0)
                        .put("pos", e.positionMs + elapsed).put("play", e.playing)
                    engineCmd(member, load)
                }
                engineCmd(member, JSONObject().put("cmd", "output").put("id", devId))
                withContext(Dispatchers.Main) {
                    speakerId = member
                    takeSnapshot()
                    if (member == myId) onRemoteState?.invoke(null)
                    message = "Ausgabe: " + (if (member == myId) "dieses Handy" else memberName(member))
                }
            } catch (x: Exception) {
                withContext(Dispatchers.Main) { message = "Ausgabe konnte nicht gewechselt werden" }
            } finally { switching = false }
        }
    }

    /** Mitglied weg (verlassen/rausgeworfen): War es die Ausgabe, wieder auf dem Host-Handy. */
    private fun memberGone(id: String) {
        engines.remove(id)
        if (speakerId == id) {
            speakerId = myId
            onRemoteState?.invoke(null)
            message = "Die Ausgabe war bei ${memberName(id)} – jetzt wieder auf diesem Handy"
        }
    }

    // ---------- Gast ----------

    /** Einem Jam beitreten – ohne Bestätigung, jeder im WLAN darf (der Host kann später rauswerfen). */
    fun join(peer: JamPeer) {
        if (role != Role.None || peer.hostingJam == null) return
        invite = null
        waitingFor = peer.name
        joinJob?.cancel()
        joinJob = scope.launch {
            try {
                val body = JSONObject().put("id", myId).put("name", myName).put("port", server?.port ?: 0)
                var o = JSONObject(http("${peer.url}/jam/join", body.toString()))
                // Vorher entfernt: warten, bis der Host die Anfrage annimmt (bis 2 Minuten)
                if (o.optString("status") == "pending") {
                    withContext(Dispatchers.Main) { waitingFor = peer.name; message = "Anfrage an ${peer.name} gesendet – ${peer.name} muss zustimmen" }
                    var tries = 0
                    while (isActive && tries++ < 120 && o.optString("status") == "pending") {
                        delay(1000)
                        o = JSONObject(http("${peer.url}/jam/status?id=$myId"))
                    }
                }
                when (o.optString("status")) {
                    "accepted" -> {
                        key = o.getString("key")
                        hostUrl = peer.url
                        bestRtt = Long.MAX_VALUE
                        withContext(Dispatchers.Main) {
                            waitingFor = null
                            role = Role.Guest
                            message = "Du bist im Jam von ${peer.name}"
                            // Es gibt nur eine Ausgabe (beim Host): die eigene Wiedergabe anhalten
                            player?.let { it.pause(); it.clearMediaItems() }
                        }
                        startSync()
                    }
                    "declined" -> withContext(Dispatchers.Main) { waitingFor = null; message = "${peer.name} hat die Anfrage abgelehnt" }
                    "pending" -> withContext(Dispatchers.Main) { waitingFor = null; message = "Keine Antwort von ${peer.name}" }
                    else -> withContext(Dispatchers.Main) { waitingFor = null; message = "Der Jam ist schon beendet" }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { waitingFor = null; message = "${peer.name} ist nicht erreichbar" }
            }
        }
    }

    fun cancelJoin() { joinJob?.cancel(); waitingFor = null }

    fun leave(silent: Boolean = false) {
        if (role != Role.Guest) return
        val url = hostUrl
        val k = key
        if (k != null) scope.launch { try { http("$url/jam/leave?k=$k&id=$myId", "{}") } catch (e: Exception) { } }
        syncJob?.cancel()
        key = null
        hostUrl = ""
        state = null
        role = Role.None
        libraries.clear()
        // War dieses Handy die Ausgabe, endet die gemeinsame Wiedergabe hier
        player?.let { p -> if (isJamContext(p.currentMediaItem)) p.pause() }
        onRemoteState?.invoke(null)
        if (!silent) message = "Jam verlassen"
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            var fails = 0
            while (isActive && role == Role.Guest) {
                try {
                    val t0 = System.currentTimeMillis()
                    val text = http("$hostUrl/jam/state?k=$key&id=$myId", timeout = 3000)
                    val t1 = System.currentTimeMillis()
                    val s = JamState.from(JSONObject(text))
                    val rtt = t1 - t0
                    // Uhren-Abgleich: die schnellste Antwort ist die genaueste
                    if (rtt <= bestRtt + 20) { bestRtt = minOf(bestRtt, rtt); clockOffset = s.hostTime - (t0 + t1) / 2 }
                    fails = 0
                    withContext(Dispatchers.Main) {
                        state = s
                        onRemoteState?.invoke(if (s.speaker == myId) null else s)
                    }
                } catch (e: HttpError) {
                    if (e.code == 403 || e.code == 410) { withContext(Dispatchers.Main) { leave(silent = true); message = "Der Jam ist für dich beendet" }; return@launch }
                    fails++
                } catch (e: Exception) { fails++ }
                if (fails >= 8) { withContext(Dispatchers.Main) { leave(silent = true); message = "Verbindung zum Jam verloren" }; return@launch }
                delay(700)
            }
        }
    }

    /** Gast: geschätzte Position beim Host (Uhren-Abgleich), in ms im ganzen Lied. */
    fun estimatedPosition(): Long {
        val s = state ?: return 0L
        if (!s.playing) return s.positionMs
        val pos = s.positionMs + (System.currentTimeMillis() + clockOffset - s.snapTime)
        val d = s.current?.durationMs ?: 0L
        return if (d > 0) pos.coerceIn(0L, d) else pos.coerceAtLeast(0L)
    }

    /** Anzeige-Eintrag für den Gast-Player (Lied läuft beim Host, nicht hier). */
    fun displayItem(e: JamEntry): MediaItem = itemFor(e) ?: MediaItem.Builder()
        .setMediaId("$ITEM_PREFIX${e.owner}:${e.trackId}:x")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(e.title).setArtist(e.artist)
            .setExtras(Bundle().apply { putString("jam", e.toJson().toString()) }).build())
        .build()

    /** Befehl an den Host schicken (Gast) bzw. direkt ausführen (Host). */
    fun command(o: JSONObject) {
        when (role) {
            Role.Host -> dispatch(o)
            Role.Guest -> {
                val url = hostUrl; val k = key ?: return
                scope.launch {
                    try { http("$url/jam/cmd?k=$k&id=$myId", o.toString()) }
                    catch (e: Exception) { withContext(Dispatchers.Main) { message = "Befehl kam beim Host nicht an" } }
                }
            }
            Role.None -> {}
        }
    }

    /** Im Jam darf nicht bearbeitet werden – außer ALLE haben es in den Developer Options erlaubt. */
    val editLocked: Boolean get() = active && state?.editAllowed != true

    /** Schlüssel „Gerät/Lied“ eines Player-Eintrags (für Warteschlangen-Befehle). */
    fun keyOf(item: MediaItem?): String? = entryOf(item)?.key

    /** Eintrag mit „Eingereiht von“ und Herkunft (für die Anzeige in der Warteschlange). */
    fun entryInfo(item: MediaItem?): JamEntry? = entryOf(item)

    /** Warteschlange bearbeiten: [from]/[to] relativ zum laufenden Lied. */
    fun queueCmd(cmd: String, from: Int, to: Int, item: MediaItem?) {
        command(JSONObject().put("cmd", cmd).put("from", from).put("to", to).put("key", keyOf(item) ?: ""))
    }

    fun cmd(name: String, ms: Long? = null) = command(JSONObject().put("cmd", name).also { if (ms != null) it.put("ms", ms) })

    /** Gast: Spul-Befehl mit Position im ganzen Lied (unabhängig vom Stille-Schnitt). */
    fun seekAbs(absMs: Long) = cmd("seek", absMs)

    fun playEntries(all: List<JamEntry>, index: Int) {
        if (all.isEmpty()) return
        val entries = all.map { it.copy(by = myId) }
        // Sehr lange Listen auf ein handliches Stück rund um das gewählte Lied kürzen
        val from = (index - 50).coerceAtLeast(0)
        val part = entries.subList(from, minOf(entries.size, from + 300))
        command(JSONObject().put("cmd", "playNow").put("items", JamEntry.array(part)).put("index", index - from))
        message = "Im Jam gestartet"
    }

    fun addEntries(all: List<JamEntry>) {
        if (all.isEmpty()) return
        val entries = all.map { it.copy(by = myId) }
        command(JSONObject().put("cmd", "add").put("items", JamEntry.array(entries.take(300))))
        message = "Zur Jam-Warteschlange hinzugefügt"
    }

    fun playOwn(list: List<Track>, index: Int) = playEntries(list.map { JamEntry.of(myId, it) }, index)
    fun addOwn(list: List<Track>) = addEntries(list.map { JamEntry.of(myId, it) })

    // ---------- Adressen und Player-Einträge ----------

    /** Basis-Adresse eines Mitglieds (null = man selbst). */
    private fun urlOf(owner: String): String? {
        if (owner == myId) return null
        if (role == Role.Guest) {
            val s = state
            if (owner == s?.hostId) return hostUrl
            return s?.members?.firstOrNull { it.id == owner }?.url?.takeIf { it.isNotBlank() }
        }
        return members[owner]?.url
    }

    fun memberName(owner: String): String =
        if (owner == myId) myName else state?.members?.firstOrNull { it.id == owner }?.name ?: members[owner]?.name ?: "Jam"

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    fun fileUrl(owner: String, id: Long): String? = urlOf(owner)?.let { "$it/file/$id?k=${q(key ?: "")}&id=$myId" }
    fun coverUrl(owner: String, id: Long, size: Int = 300): String? = urlOf(owner)?.let { "$it/cover/$id?k=${q(key ?: "")}&id=$myId&s=$size" }

    /** Player-Eintrag für ein Jam-Lied: eigenes Lied lokal, fremdes per Streaming vom Besitzer. */
    fun itemFor(e: JamEntry): MediaItem? {
        if (e.owner == myId) {
            val t = Library.tracks.firstOrNull { it.id == e.trackId } ?: return null
            val base = Library.mediaItem(t, "jam")
            // „Eingereiht von“ merken
            val ex = android.os.Bundle(base.mediaMetadata.extras ?: android.os.Bundle()).apply { putString("jam", e.toJson().toString()) }
            return base.buildUpon().setMediaMetadata(base.mediaMetadata.buildUpon().setExtras(ex).build()).build()
        }
        val url = fileUrl(e.owner, e.trackId) ?: return null
        val uri = Uri.parse(url)
        val extras = Bundle().apply { putString("jam", e.toJson().toString()) }
        val md = MediaMetadata.Builder()
            .setTitle(e.title).setArtist(e.artist)
            .setIsPlayable(true).setIsBrowsable(false)
            .setExtras(extras)
        coverUrl(e.owner, e.trackId, 512)?.let { md.setArtworkUri(Uri.parse(it)) }
        return MediaItem.Builder()
            .setMediaId("$ITEM_PREFIX${e.owner}:${e.trackId}:x")
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build())
            .setMediaMetadata(md.build())
            .build()
    }

    fun entryOfItem(item: MediaItem?): JamEntry? = if (isJamItem(item)) entryOf(item) else null

    // ---------- Bibliotheken der anderen (Teil A) ----------

    private val libraries = ConcurrentHashMap<String, RemoteLibrary>()

    /** Alle anderen im Jam (für „Bibliothek ansehen“). */
    fun others(): List<JamMember> {
        val s = state ?: return emptyList()
        return s.members.filter { it.id != myId }
    }

    suspend fun library(owner: String, refresh: Boolean = false): RemoteLibrary? = withContext(Dispatchers.IO) {
        if (!refresh) libraries[owner]?.let { return@withContext it }
        val base = urlOf(owner) ?: return@withContext null
        try {
            val lib = visible(RemoteLibrary.from(JSONObject(http("$base/library?k=${q(key ?: "")}&id=$myId", timeout = 20000))))
            libraries[owner] = lib
            lib
        } catch (e: Exception) { null }
    }

    private val coverCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val noCover = ConcurrentHashMap.newKeySet<String>()

    suspend fun cover(owner: String, id: Long, size: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
        val k = "$owner/$id/$size"
        coverCache.get(k)?.let { return@withContext it }
        if (k in noCover) return@withContext null
        val url = coverUrl(owner, id, size) ?: return@withContext null
        val b = try { httpBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (e: Exception) { null }
        if (b != null) coverCache.put(k, b) else noCover += k
        b
    }

    /**
     * Vollsperre gilt auch im Jam: Was ich selbst voll gesperrt habe (rot), sehe ich auch in der Bibliothek der anderen
     * nicht – gesperrte Interpreten und dieselben Lieder wie meine gesperrten (gleiche Regeln wie die Duplikat-Suche).
     * Umgekehrt teilt jedes Handy gesperrte Lieder (orange und rot) gar nicht erst.
     */
    suspend fun visible(lib: RemoteLibrary): RemoteLibrary = withContext(Dispatchers.Default) {
        val hard = Library.blocked.filter { Store.isBlocked(it) }
        val arts = Store.blockedArtists.map { Names.fold(it) }.toHashSet()
        if (hard.isEmpty() && arts.isEmpty()) return@withContext lib
        val m = Dupes.Matcher(hard)
        val keep = lib.tracks.filter { t -> Names.fold(t.artist) !in arts && m.find(t.asProbe()) == null }
        val ids = keep.map { it.id }.toHashSet()
        RemoteLibrary(lib.ownerId, lib.ownerName, keep, lib.playlists.map { it.copy(trackIds = it.trackIds.filter { id -> id in ids }) }.filter { it.trackIds.isNotEmpty() })
    }

    // ---------- Profilbilder ----------

    private val avatars = ConcurrentHashMap<String, Pair<Long, Bitmap?>>()
    /** Zählt hoch, wenn sich das eigene Profilbild ändert. */
    var avatarTick by mutableStateOf(0)
        private set
    fun profileChanged() { avatars.remove(myId); avatarTick++ }

    /** Profilbild eines Mitglieds oder Geräts in der Nähe (eine Minute zwischengespeichert). */
    suspend fun avatar(id: String): Bitmap? = withContext(Dispatchers.IO) {
        avatars[id]?.let { (at, b) -> if (System.currentTimeMillis() - at < 60_000) return@withContext b }
        val b: Bitmap? = if (id == myId) {
            val f = File(app.filesDir, "profile.jpg")
            if (Store.profileStamp != 0L && f.isFile) BitmapFactory.decodeFile(f.path) else null
        } else {
            val url = urlOf(id) ?: peers.firstOrNull { it.id == id }?.url
            if (url == null) null else try { httpBytes("$url/avatar")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (e: Exception) { null }
        }
        avatars[id] = System.currentTimeMillis() to b
        b
    }

    /** Welche Lieder des anderen schon in der eigenen Bibliothek sind (gleiche Regeln wie die Duplikat-Suche). */
    suspend fun owned(lib: RemoteLibrary): Map<Long, Track> = withContext(Dispatchers.Default) {
        val m = Dupes.Matcher(Library.allTracks)
        val out = HashMap<Long, Track>()
        for (t in lib.tracks) m.find(t.asProbe())?.let { out[t.id] = it }
        out
    }

    // ---------- Übernehmen ----------

    private class Job2(val owner: String, val ownerName: String, val tracks: List<RemoteTrack>, val playlist: RemotePlaylist?)
    /** Übernahmen laufen nacheinander (eine nach der anderen), nie gleichzeitig. */
    private val importLock = kotlinx.coroutines.sync.Mutex()

    /** Lieder (und optional eine Playlist) vom anderen Gerät aufs eigene Handy holen. */
    fun takeOver(owner: String, tracks: List<RemoteTrack>, playlist: RemotePlaylist? = null) {
        if (tracks.isEmpty() || owner == myId) return
        val job = Job2(owner, memberName(owner), tracks.distinctBy { it.id }, playlist)
        message = if (tracks.size == 1) "„${tracks[0].title}“ wird übernommen" else "${tracks.size} Lieder werden übernommen"
        scope.launch {
            importLock.lock()
            try { runImport(job) }
            catch (e: Exception) { withContext(Dispatchers.Main) { transfer = null; message = "Übernehmen abgebrochen" } }
            finally { importLock.unlock() }
        }
    }

    /** Ein einzelnes Jam-Lied übernehmen (z. B. aus dem Player). Holt die vollständigen Angaben vom Besitzer. */
    fun importEntry(e: JamEntry) {
        scope.launch {
            val rt = library(e.owner)?.byId?.get(e.trackId)
            withContext(Dispatchers.Main) {
                if (rt != null) takeOver(e.owner, listOf(rt))
                else message = "Lied nicht mehr verfügbar"
                Unit
            }
        }
    }

    private suspend fun setTransfer(t: Transfer?) { withContext(Dispatchers.Main) { transfer = t } }

    private suspend fun runImport(j: Job2) {
        val ctx = app
        Library.ensureLoaded(ctx)
        val matcher = Dupes.Matcher(Library.allTracks)
        val pathOf = HashMap<Long, String>()
        val fresh = ArrayList<Pair<String, RemoteTrack>>()
        var skipped = 0
        var failed = 0
        val total = j.tracks.size
        for ((n, rt) in j.tracks.withIndex()) {
            setTransfer(Transfer(total, n, skipped, failed, rt.title, false))
            val have = matcher.find(rt.asProbe())
            if (have != null) { pathOf[rt.id] = have.path; skipped++; continue }
            val url = fileUrl(j.owner, rt.id)
            val target = url?.let { download(it, rt) }
            if (target != null) { pathOf[rt.id] = target.path; fresh += target.path to rt } else failed++
        }
        if (fresh.isNotEmpty()) {
            setTransfer(Transfer(total, total, skipped, failed, "Einsortieren …", false))
            Library.rescan(ctx, fresh.map { it.first })
            Library.load(ctx)
            for ((path, rt) in fresh) {
                val t = Library.allTracks.firstOrNull { it.path == path }
                if (rt.artist != UNKNOWN_ARTIST && rt.artist.isNotBlank() && t?.artist != rt.artist) Store.setTrackArtist(ctx, path, rt.artist)
                if (rt.albumName.isNotBlank() && t?.albumName != rt.albumName) Store.setAlbum(ctx, listOf(path), rt.albumName)
                if (rt.customCover) saveCover(j.owner, rt.id, path)
            }
        }
        var listName: String? = null
        j.playlist?.let { pl ->
            val name = if (pl.favorites) "Lieblingssongs von ${j.ownerName}" else pl.name
            val paths = pl.trackIds.mapNotNull { pathOf[it] }
            if (paths.isNotEmpty()) {
                val existing = Store.playlists.firstOrNull { it.id != FAV_ID && it.audio == pl.audio && it.name.equals(name, ignoreCase = true) }
                val id = existing?.id ?: Store.createPlaylist(ctx, name, pl.audio).id
                Store.addToPlaylist(ctx, id, paths)
                listName = name
            }
        }
        Library.load(ctx)
        setTransfer(Transfer(total, total, skipped, failed, "", true))
        withContext(Dispatchers.Main) {
            libraryTick++
            onLibraryChanged?.invoke()
            val parts = ArrayList<String>()
            parts += "${fresh.size} übernommen"
            if (skipped > 0) parts += "$skipped schon vorhanden"
            if (failed > 0) parts += "$failed fehlgeschlagen"
            message = parts.joinToString(", ") + (listName?.let { " · Playlist „$it“" } ?: "")
        }
    }

    private fun safe(s: String): String {
        val t = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')
        return if (t.isEmpty()) "_" else t
    }

    /** Lädt die Datei nach „<Speicher>/Music/<Interpret>/“ (wie beim Einsortieren). */
    private fun download(url: String, rt: RemoteTrack): File? {
        val root = File(Environment.getExternalStorageDirectory(), "Music")
        // Gleich richtig einsortiert: Music/<Interpret>/<Album>/ (ohne Album: Music/<Interpret>/)
        val artistDir = if (rt.artist.isNotBlank() && rt.artist != UNKNOWN_ARTIST) File(root, safe(rt.artist)) else root
        val dir = if (rt.albumName.isNotBlank() && artistDir != root) File(artistDir, safe(rt.albumName)) else artistDir
        dir.mkdirs()
        val name = safe(rt.fileName.ifBlank { rt.title + ".mp3" })
        var target = File(dir, name)
        if (target.exists()) {
            val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
            var i = 2
            while (target.exists()) { target = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"); i++ }
        }
        val part = File(dir, target.name + ".part")
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 30000
            try {
                if (c.responseCode != 200) return null
                val expected = c.contentLengthLong
                c.inputStream.use { inp -> part.outputStream().use { inp.copyTo(it, 64 * 1024) } }
                if (expected > 0 && part.length() != expected) { part.delete(); return null }
            } finally { c.disconnect() }
            if (part.renameTo(target)) target else { part.delete(); null }
        } catch (e: Exception) { part.delete(); null }
    }

    private fun saveCover(owner: String, id: Long, path: String) {
        val url = coverUrl(owner, id, 900) ?: return
        val bytes = try { httpBytes(url) } catch (e: Exception) { null } ?: return
        try {
            val dir = File(app.filesDir, "trackcovers").also { it.mkdirs() }
            val n = UUID.randomUUID().toString() + ".jpg"
            File(dir, n).writeBytes(bytes)
            Store.setTrackCover(app, listOf(path), n)
            main.post { Art.invalidate() }
        } catch (e: Exception) { }
    }

    // ---------- Server (Antworten an die anderen) ----------

    private fun authed(r: JamServer.Request): Boolean { val k = key; return k != null && r.query["k"] == k }

    private fun route(r: JamServer.Request): JamServer.Response {
        val p = r.path
        return when {
            p == "/info" -> JamServer.Response.json(
                JSONObject().put("id", myId).put("name", myName).put("jam", if (role == Role.Host) jamId else "").toString()
            )
            p == "/jam/join" && r.method == "POST" -> {
                val k = key
                if (role != Role.Host || k == null) return JamServer.Response.json("{\"status\":\"closed\"}")
                val o = JSONObject(r.bodyText())
                val id = o.getString("id")
                val m = JamMember(id, o.optString("name", "Gast"), "http://${r.remote}:${o.optInt("port")}")
                if (id in kicked) {
                    // Wer entfernt wurde, braucht die Zustimmung des Hosts: Anfrage erscheint beim Host
                    declined.remove(id)
                    main.post { requests = requests.filter { it.id != id } + JamRequest(id, m.name, m.url) }
                    return JamServer.Response.json("{\"status\":\"pending\"}")
                }
                val isNew = members.put(id, m) == null
                if (isNew) main.post { message = "${m.name} ist dem Jam beigetreten"; takeSnapshot() }
                JamServer.Response.json(JSONObject().put("status", "accepted").put("key", k).put("jam", jamId).toString())
            }
            p == "/jam/status" -> {
                val id = r.query["id"].orEmpty()
                val k = key
                val st = when {
                    role != Role.Host || k == null -> JSONObject().put("status", "closed")
                    members.containsKey(id) -> JSONObject().put("status", "accepted").put("key", k).put("jam", jamId)
                    declined.remove(id) -> JSONObject().put("status", "declined")
                    else -> JSONObject().put("status", "pending")
                }
                JamServer.Response.json(st.toString())
            }
            // Profilbild (öffentlich wie der Name, damit es schon im Pop-up erscheint)
            p == "/avatar" -> {
                val f = File(app.filesDir, "profile.jpg")
                if (Store.profileStamp == 0L || !f.isFile) JamServer.Response.error(404) else JamServer.Response.image(f.readBytes())
            }
            !authed(r) -> JamServer.Response.error(403, "kein Zugriff")
            // Beim Host: nur aktuelle Mitglieder (Rausgeworfene kommen nicht mehr an die Musik)
            role == Role.Host && (p == "/library" || p.startsWith("/file/") || p.startsWith("/cover/") || p.startsWith("/engine/")) &&
                !members.containsKey(r.query["id"].orEmpty()) -> JamServer.Response.error(403, "kein Mitglied")
            // Beim Gast darf nur der Host die Wiedergabe steuern
            role == Role.Guest && p.startsWith("/engine/") && r.query["id"] != state?.hostId -> JamServer.Response.error(403, "nur Host")
            p == "/jam/state" -> {
                if (role != Role.Host) return JamServer.Response.error(410, "beendet")
                val id = r.query["id"].orEmpty()
                if (!members.containsKey(id)) return JamServer.Response.error(403, "kein Mitglied")
                val s = snapshot ?: return JamServer.Response.error(410, "beendet")
                JamServer.Response.json(s.copy(hostTime = System.currentTimeMillis()).toJson().toString())
            }
            p == "/jam/cmd" && r.method == "POST" -> {
                if (role != Role.Host) return JamServer.Response.error(410, "beendet")
                val o = JSONObject(r.bodyText())
                // Lied-Einträge brauchen die Bibliothek (Host-Thread), daher auf dem Haupt-Thread ausführen und kurz warten
                val done = CountDownLatch(1)
                main.post { try { dispatch(o) } finally { done.countDown() } }
                done.await(3, TimeUnit.SECONDS)
                JamServer.Response.json("{\"ok\":true}")
            }
            p == "/jam/leave" -> {
                val id = r.query["id"].orEmpty()
                val m = members.remove(id)
                if (m != null) main.post { memberGone(id); message = "${m.name} hat den Jam verlassen"; takeSnapshot() }
                JamServer.Response.json("{\"ok\":true}")
            }
            // Wiedergabe-Schnittstelle jedes Geräts (der Host steuert darüber die gemeinsame Ausgabe)
            p == "/engine/state" -> {
                val e = localEngineBlocking(if (r.query["full"] == "1") 300 else 30) ?: return JamServer.Response.error(500, "kein Player")
                JamServer.Response.json(e.toJson().toString())
            }
            p == "/engine/cmd" && r.method == "POST" -> {
                val o = JSONObject(r.bodyText())
                val done = CountDownLatch(1)
                main.post { try { runCommand(o) } finally { done.countDown() } }
                done.await(3, TimeUnit.SECONDS)
                JamServer.Response.json("{\"ok\":true}")
            }
            p == "/library" -> JamServer.Response.json(myLibrary().toJson().toString())
            p.startsWith("/file/") -> {
                val id = p.removePrefix("/file/").toLongOrNull() ?: return JamServer.Response.error(404)
                val t = Library.tracks.firstOrNull { it.id == id } ?: return JamServer.Response.error(404)
                JamServer.Response.file(File(t.path))
            }
            p.startsWith("/cover/") -> {
                val id = p.removePrefix("/cover/").toLongOrNull() ?: return JamServer.Response.error(404)
                val t = Library.tracks.firstOrNull { it.id == id } ?: return JamServer.Response.error(404)
                val size = r.query["s"]?.toIntOrNull()?.coerceIn(64, 1000) ?: 300
                val bmp = runBlocking { if (size > 400) Art.large(app, t) else Art.first(app, listOf(t), size) }
                    ?: return JamServer.Response.error(404)
                val out = ByteArrayOutputStream()
                val scaled = if (maxOf(bmp.width, bmp.height) > size) {
                    val f = size.toFloat() / maxOf(bmp.width, bmp.height)
                    Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * f).toInt()), maxOf(1, (bmp.height * f).toInt()), true)
                } else bmp
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                JamServer.Response.image(out.toByteArray())
            }
            else -> JamServer.Response.error(404)
        }
    }

    /** Eigene Bibliothek zum Teilen: ohne gesperrte Lieder; Playlists nur mit geteilten Liedern. */
    private fun myLibrary(): RemoteLibrary {
        Library.ensureLoaded(app)
        val tracks = Library.tracks
        val byPath = tracks.associateBy { it.path }
        val lists = Store.playlists.map { pl ->
            RemotePlaylist(pl.id, pl.name, pl.audio, pl.id == FAV_ID, pl.trackPaths.mapNotNull { byPath[Store.resolve(it)]?.id })
        }.filter { it.trackIds.isNotEmpty() }
        return RemoteLibrary(myId, myName, tracks.map { RemoteTrack.of(it) }, lists)
    }

    // ---------- HTTP-Client ----------

    class HttpError(val code: Int) : Exception("HTTP $code")

    private fun http(url: String, post: String? = null, timeout: Int = 5000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        try {
            if (post != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code)
            return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    private fun httpBytes(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 10000
        try {
            if (c.responseCode != 200) return null
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }
}
