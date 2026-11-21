package de.musikplayer.app

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sleep-Timer mit langsamem Ausblenden: In den letzten [fadeSeconds] Sekunden vor Ablauf
 * wird die Lautstärke gleichmäßig auf 0 gefahren, dann pausiert die Wiedergabe.
 * Läuft im Wiedergabe-Dienst, funktioniert also auch bei geschlossener App.
 */
object SleepTimer {

    private val handler = Handler(Looper.getMainLooper())

    /** Wird vom Wiedergabe-Dienst gesetzt. */
    @Volatile
    var player: Player? = null

    private val _remainingMs = MutableStateFlow(0L)
    /** Restzeit in ms, 0 = Timer aus. */
    val remainingMs: StateFlow<Long> = _remainingMs

    private var endAt = 0L
    private var fadeMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            val p = player
            val left = endAt - System.currentTimeMillis()
            if (p == null) {
                reset()
                return
            }
            if (left <= 0L) {
                p.pause()
                p.volume = 1f
                reset()
                return
            }
            _remainingMs.value = left
            p.volume = if (fadeMs > 0L && left < fadeMs) {
                val x = left.toFloat() / fadeMs.toFloat()
                x * x // quadratisch klingt gleichmäßiger als linear
            } else 1f
            handler.postDelayed(this, 250L)
        }
    }

    fun start(minutes: Int, fadeSeconds: Int) {
        handler.removeCallbacks(tick)
        val total = minutes.coerceAtLeast(1) * 60_000L
        fadeMs = minOf(fadeSeconds.coerceAtLeast(0) * 1000L, total)
        endAt = System.currentTimeMillis() + total
        _remainingMs.value = total
        handler.post(tick)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        player?.volume = 1f
        reset()
    }

    private fun reset() {
        endAt = 0L
        _remainingMs.value = 0L
    }
}
