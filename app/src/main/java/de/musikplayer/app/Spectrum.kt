package de.musikplayer.app

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Echter Equalizer: Der Android-[Visualizer] hängt an der Audio-Sitzung des eigenen Players und liefert
 * das Frequenzspektrum dessen, was die App selbst abspielt. Das Mikrofon wird NICHT abgehört und es wird
 * nichts aufgenommen oder gespeichert. Android verlangt dafür trotzdem die Berechtigung „Audio aufnehmen“.
 */
object Spectrum {

    const val BANDS = 28
    private const val MIN_HZ = 40f
    private const val MAX_HZ = 14_000f

    /** Wird vom Wiedergabe-Dienst gesetzt: liefert die Audio-Sitzungs-ID des Players (0 = noch keine). */
    @Volatile
    var sessionIdProvider: (() -> Int)? = null

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    /** Pegel je Band, 0..1, tiefe Töne links. */
    val bands: StateFlow<FloatArray> = _bands

    private var visualizer: Visualizer? = null

    /** Startet die Analyse. Gibt false zurück, wenn das Gerät sie nicht liefert (dann zeigt die App eine Animation). */
    @Synchronized
    fun start(): Boolean {
        stop()
        val session = sessionIdProvider?.invoke() ?: return false
        if (session == 0) return false
        return try {
            val v = Visualizer(session)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) process(fft, samplingRate / 1000)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun stop() {
        visualizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                // schon freigegeben
            }
        }
        visualizer = null
        _bands.value = FloatArray(BANDS)
    }

    /** FFT-Bytes (Re/Im-Paare) in [BANDS] logarithmisch verteilte Bänder umrechnen. */
    private fun process(fft: ByteArray, sampleRateHz: Int) {
        val n = fft.size
        if (n < 16 || sampleRateHz <= 0) return
        val hi = minOf(MAX_HZ, sampleRateHz / 2f)
        val ratio = hi / MIN_HZ
        val out = FloatArray(BANDS)
        for (i in 0 until BANDS) {
            val f0 = MIN_HZ * ratio.pow(i.toFloat() / BANDS)
            val f1 = MIN_HZ * ratio.pow((i + 1).toFloat() / BANDS)
            val k0 = max(1, (f0 * n / sampleRateHz).roundToInt())
            val k1 = max(k0, (f1 * n / sampleRateHz).roundToInt()).coerceAtMost(n / 2 - 1)
            var peak = 0f
            for (k in k0..k1) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                peak = max(peak, sqrt((re * re + im * im).toFloat()))
            }
            // Höhen sind von Natur aus leiser: sanft anheben; dann komprimieren
            val tilt = 1f + 1.4f * i / BANDS
            val level = ((peak * tilt) / 110f).coerceIn(0f, 1f).pow(0.8f)
            // Glättung passiert in der Anzeige (jedes Bild), hier nur die Rohwerte
            out[i] = level
        }
        _bands.value = out
    }
}
