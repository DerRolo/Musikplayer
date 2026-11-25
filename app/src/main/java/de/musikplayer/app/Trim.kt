package de.musikplayer.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.C
import java.nio.ByteOrder

/**
 * Internes „Schnittprogramm“: findet den ersten und den letzten Ton eines Liedes (alles lauter als die Schwelle).
 * Abgespielt wird dann nur von [Start − Vorlauf] bis [Ende + Nachlauf]. Pausen mitten im Lied bleiben unberührt.
 * Die Dateien selbst werden nicht verändert; das Ergebnis wird je Lied gespeichert.
 */
object Trim {
    private fun key(t: Track) = "${t.size}:${Store.silenceThr}"

    private fun tones(t: Track): Pair<Long, Long>? {
        val p = Store.trims[t.path]?.split(":") ?: return null
        if (p.size < 4 || p[0] + ":" + p[1] != key(t)) return null
        return (p[2].toLongOrNull() ?: -1L) to (p[3].toLongOrNull() ?: -1L)
    }

    /** true, wenn dieses Lied schon untersucht wurde (mit der aktuellen Schwelle). */
    fun known(t: Track): Boolean = tones(t).let { it != null && it.second != -2L }

    /** Start und Ende in ms (Ende = C.TIME_END_OF_SOURCE, wenn hinten nichts zu schneiden ist); null = nicht schneiden. */
    fun clip(t: Track): Pair<Long, Long>? {
        if (!Store.skipSilence) return null
        val (first, last) = tones(t) ?: return null
        if (first < 0) return null
        val start = (first - Store.silenceKeepMs).coerceAtLeast(0L)
        val end = last + Store.silenceTailMs
        val cutEnd = t.durationMs > 0 && end < t.durationMs - 300 && end > start + 1000
        if (start < 150 && !cutEnd) return null
        return start to (if (cutEnd) end else C.TIME_END_OF_SOURCE)
    }

    /** Lied untersuchen (blockiert, im Hintergrund-Thread aufrufen). */
    fun ensure(ctx: Context, t: Track, startOnly: Boolean = false) {
        if (known(t) || (startOnly && tones(t) != null)) return
        val (a, b) = analyze(t.path, Store.silenceThr, t.durationMs, startOnly)
        Store.setTrim(ctx, t.path, "${key(t)}:$a:$b")
    }

    private fun analyze(path: String, thr: Int, durMs: Long, startOnly: Boolean): Pair<Long, Long> {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(path)
            var ti = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { ti = i; break }
            }
            if (ti < 0) return -1L to -1L
            ex.selectTrack(ti)
            val fmt = ex.getTrackFormat(ti)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return -1L to -1L
            val dur = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) / 1000 else durMs
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()
            val first = scan(ex, c, fmt, 0L, thr, true).first
            if (first < 0) return -1L to -1L
            if (startOnly) return first / 1000 to -2L   // Ende folgt später im Hintergrund
            var win = 8_000L
            var last = -1L
            while (true) {
                val from = (dur - win).coerceAtLeast(0L)
                val r = scan(ex, c, fmt, from * 1000, thr, false).second
                if (r >= 0) { last = r; break }
                if (from == 0L) break
                win *= 3
            }
            return first / 1000 to (if (last >= 0) last / 1000 else dur)
        } catch (e: Exception) {
            return -1L to -1L
        } finally {
            try { codec?.stop() } catch (e: Exception) { }
            try { codec?.release() } catch (e: Exception) { }
            try { ex.release() } catch (e: Exception) { }
        }
    }

    /** Liefert (Zeit des ersten lauten Blocks, Ende des letzten lauten Blocks) in µs ab [startUs]; -1 = nichts gefunden. */
    private fun scan(ex: MediaExtractor, codec: MediaCodec, inFmt: MediaFormat, startUs: Long, thr: Int, stopAtFirst: Boolean): Pair<Long, Long> {
        codec.flush()
        ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var sr = if (inFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        var ch = if (inFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        var isFloat = false
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var firstLoud = -1L
        var lastLoud = -1L
        val deadline = System.nanoTime() + 25_000_000_000L
        while (System.nanoTime() < deadline) {
            if (!inputDone) {
                val ii = codec.dequeueInputBuffer(5000)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii) ?: return firstLoud to lastLoud
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 5000)
            if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)
                if (ob != null && info.size > 0) {
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    ob.order(ByteOrder.nativeOrder())
                    val blockFrames = (sr / 100).coerceAtLeast(1)
                    val total: Int
                    val sh = if (isFloat) null else ob.asShortBuffer()
                    val fl = if (isFloat) ob.asFloatBuffer() else null
                    total = (if (sh != null) sh.remaining() else fl!!.remaining()) / ch.coerceAtLeast(1)
                    var f = 0
                    while (f + blockFrames <= total) {
                        var sum = 0.0
                        for (k in 0 until blockFrames * ch) {
                            val v = if (sh != null) sh.get((f * ch) + k).toDouble() else fl!!.get((f * ch) + k) * 32768.0
                            sum += v * v
                        }
                        val rms = Math.sqrt(sum / (blockFrames * ch))
                        if (rms > thr) {
                            val tUs = info.presentationTimeUs + f * 1_000_000L / sr
                            if (firstLoud < 0) firstLoud = tUs
                            lastLoud = tUs + blockFrames * 1_000_000L / sr
                        }
                        f += blockFrames
                    }
                }
                codec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                if (stopAtFirst && firstLoud >= 0) break
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val of = codec.outputFormat
                if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4
            }
        }
        return firstLoud to lastLoud
    }
}
