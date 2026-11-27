package de.musikplayer.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Zwei getrennte Funktionen, die beide auf die Lautstärke des Players wirken:
 *
 * 1. Sleep-Timer: Nach X Minuten stoppt die Musik. In den letzten [END_FADE_MS] blendet sie
 *    immer automatisch aus, damit sie nicht abrupt endet (fest eingebaut).
 * 2. Ausfaden: Die Gesamtlautstärke sinkt linear von 100 % auf 0 % über eine frei einstellbare Zeit
 *    (Minuten bis Stunden), auch über Liedwechsel hinweg. Bei 0 % wird die Wiedergabe gestoppt.
 *    Die Zeit läuft nur, solange Musik wirklich spielt.
 *
 * Sind beide aktiv, gilt jeweils die leisere Lautstärke. Läuft im Wiedergabe-Dienst,
 * funktioniert also auch bei geschlossener App.
 */
object SleepTimer {

    /** Fester Ausklang am Ende des Sleep-Timers. */
    const val END_FADE_MS = 20_000L

    private val handler = Handler(Looper.getMainLooper())

    /** Wird vom Wiedergabe-Dienst gesetzt. */
    @Volatile
    var player: Player? = null

    private val _sleepRemainingMs = MutableStateFlow(0L)
    /** Restzeit des Sleep-Timers in ms, 0 = aus. */
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs

    private val _fadeRemainingMs = MutableStateFlow(0L)
    /** Restzeit des Ausfadens in ms, 0 = aus. */
    val fadeRemainingMs: StateFlow<Long> = _fadeRemainingMs

    private val _volumePercent = MutableStateFlow(100)
    /** Aktuelle Player-Lautstärke in Prozent (nur sinnvoll, solange eine Funktion aktiv ist). */
    val volumePercent: StateFlow<Int> = _volumePercent
    /** Aktueller Ausblend-Faktor (1 = kein Ausblenden). */
    val fadeFactor: Float get() = _volumePercent.value / 100f

    private var sleepEndAt = 0L          // Systemzeit, 0 = aus
    private var fadeTotalMs = 0L         // 0 = aus
    private var fadeElapsedMs = 0L
    private var lastTick = 0L

    private val active: Boolean get() = sleepEndAt > 0L || fadeTotalMs > 0L

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p == null) {
                clearAll()
                return
            }
            val now = SystemClock.elapsedRealtime()
            val dt = (now - lastTick).coerceIn(0L, 2_000L)
            lastTick = now

            var factor = 1f

            if (fadeTotalMs > 0L) {
                if (p.isPlaying) fadeElapsedMs += dt
                val left = fadeTotalMs - fadeElapsedMs
                if (left <= 0L) {
                    finish(p)
                    return
                }
                _fadeRemainingMs.value = left
                factor = minOf(factor, left.toFloat() / fadeTotalMs.toFloat()) // linear
            }

            if (sleepEndAt > 0L) {
                val left = sleepEndAt - System.currentTimeMillis()
                if (left <= 0L) {
                    finish(p)
                    return
                }
                _sleepRemainingMs.value = left
                if (left < END_FADE_MS) factor = minOf(factor, left.toFloat() / END_FADE_MS.toFloat())
            }

            factor = factor.coerceIn(0f, 1f)
            // Lautstärke-Angleichung des Liedes (beim Abspielen) bleibt beim Ausblenden erhalten
            p.volume = factor * Normalize.playFactor
            _volumePercent.value = (factor * 100f).toInt()
            handler.postDelayed(this, 250L)
        }
    }

    private fun ensureTicking() {
        handler.removeCallbacks(tick)
        lastTick = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    /** Zeit abgelaufen: Musik stoppen, Lautstärke zurücksetzen, damit sie beim nächsten Start wieder normal ist. */
    private fun finish(p: Player) {
        p.pause()
        clearAll()
    }

    private fun clearAll() {
        handler.removeCallbacks(tick)
        sleepEndAt = 0L
        fadeTotalMs = 0L
        fadeElapsedMs = 0L
        _sleepRemainingMs.value = 0L
        _fadeRemainingMs.value = 0L
        _volumePercent.value = 100
        player?.volume = Normalize.playFactor
    }

    // ---------- Sleep-Timer ----------

    fun startSleep(minutes: Int) {
        val total = minutes.coerceAtLeast(1) * 60_000L
        sleepEndAt = System.currentTimeMillis() + total
        _sleepRemainingMs.value = total
        ensureTicking()
    }

    fun cancelSleep() {
        sleepEndAt = 0L
        _sleepRemainingMs.value = 0L
        if (!active) clearAll() else ensureTicking()
    }

    // ---------- Ausfaden ----------

    fun startFade(minutes: Int) {
        fadeTotalMs = minutes.coerceAtLeast(1) * 60_000L
        fadeElapsedMs = 0L
        _fadeRemainingMs.value = fadeTotalMs
        ensureTicking()
    }

    fun cancelFade() {
        fadeTotalMs = 0L
        fadeElapsedMs = 0L
        _fadeRemainingMs.value = 0L
        if (!active) clearAll() else ensureTicking()
    }

    /** Alles beenden (Dienst wird zerstört). */
    fun cancel() = clearAll()
}
