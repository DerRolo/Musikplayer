package de.musikplayer.app

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.tan

/**
 * Lautheit eines ganzen Liedes messen, nach EBU R128 / ITU-R BS.1770 (wie Spotify, YouTube und Apple Music):
 * K-Filter (Gehörkurve), 400-ms-Blöcke mit 75 % Überlappung, absolute (−70 LUFS) und relative (−10 LU) Schwelle.
 * Ergebnis in LUFS plus Spitzenwert (für den Schutz vor Übersteuern).
 */
object Loudness {

    data class Result(val lufs: Double, val peak: Double)

    /** Biquad-Filter (Direktform I) für einen Kanal. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /** K-Filter (Stufe 1: Höhenanhebung, Stufe 2: Hochpass) für jede Abtastrate berechnet. */
    private fun kFilter(fs: Double): Pair<Biquad, Biquad> {
        var f0 = 1681.974450955533; val g = 3.999843853973347; var q = 0.7071752369554196
        var k = tan(Math.PI * f0 / fs)
        val vh = Math.pow(10.0, g / 20.0)
        val vb = Math.pow(vh, 0.4996667741545416)
        var a0 = 1.0 + k / q + k * k
        val s1 = Biquad(
            (vh + vb * k / q + k * k) / a0, 2.0 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0,
            2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0
        )
        f0 = 38.13547087602444; q = 0.5003270373238773
        k = tan(Math.PI * f0 / fs)
        a0 = 1.0 + k / q + k * k
        val s2 = Biquad(1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / q + k * k) / a0)
        return s1 to s2
    }

    /** Misst die Datei. null = nicht lesbar. Läuft auf einem Hintergrund-Thread (dauert je nach Länge einige Sekunden). */
    fun measure(path: String): Result? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            var filters = List(ch) { kFilter(sr.toDouble()) }
            var sub = max(1, sr / 10)               // 100 ms
            val subEnergy = ArrayList<Double>()     // mittlere Energie je 100-ms-Abschnitt (Summe über Kanäle)
            var acc = 0.0
            var accN = 0
            var peak = 0.0

            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = c.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { c.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10_000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = c.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = Build.VERSION.SDK_INT >= 24 && of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    filters = List(ch) { kFilter(sr.toDouble()) }
                    sub = max(1, sr / 10)
                } else if (oi >= 0) {
                    val buf = c.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    if (float) {
                        val fb = buf.asFloatBuffer()
                        val frames = fb.remaining() / ch
                        for (f in 0 until frames) {
                            var e = 0.0
                            for (k in 0 until ch) {
                                val x = fb.get().toDouble()
                                val a = abs(x); if (a > peak) peak = a
                                val y = filters[k].second.run(filters[k].first.run(x))
                                e += y * y
                            }
                            acc += e; accN++
                            if (accN >= sub) { subEnergy += acc / accN; acc = 0.0; accN = 0 }
                        }
                    } else {
                        val sb = buf.asShortBuffer()
                        val frames = sb.remaining() / ch
                        for (f in 0 until frames) {
                            var e = 0.0
                            for (k in 0 until ch) {
                                val x = sb.get() / 32768.0
                                val a = abs(x); if (a > peak) peak = a
                                val y = filters[k].second.run(filters[k].first.run(x))
                                e += y * y
                            }
                            acc += e; accN++
                            if (accN >= sub) { subEnergy += acc / accN; acc = 0.0; accN = 0 }
                        }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true
                }
            }
            // 400-ms-Blöcke (4 Abschnitte), alle 100 ms ein neuer Block
            val blocks = ArrayList<Double>()
            for (i in 0..subEnergy.size - 4) blocks += (subEnergy[i] + subEnergy[i + 1] + subEnergy[i + 2] + subEnergy[i + 3]) / 4.0
            if (blocks.isEmpty() && subEnergy.isNotEmpty()) blocks += subEnergy.average()
            fun lufs(e: Double) = -0.691 + 10.0 * log10(maxOf(e, 1e-12))
            val loud = blocks.filter { lufs(it) > -70.0 }
            if (loud.isEmpty()) return Result(-70.0, peak)
            val rel = lufs(loud.average()) - 10.0
            val gated = loud.filter { lufs(it) > rel }
            return Result(lufs((if (gated.isEmpty()) loud else gated).average()), peak)
        } catch (e: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
        }
    }
}
