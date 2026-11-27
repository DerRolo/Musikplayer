package de.musikplayer.app

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lautstärke eines Liedes angleichen („Normalisieren“) auf den Streaming-Standard −14 LUFS.
 *
 * - MP3: verlustfrei in der Datei (wie mp3gain): nur der Lautstärkewert „global_gain“ jedes Frames wird geändert,
 *   in Schritten von 1,5 dB. Nichts wird neu kodiert, Tags bleiben, exakt umkehrbar.
 * - WAV: Samples werden in der Datei umgerechnet.
 * - Andere Formate (FLAC, M4A, OGG, OPUS …): Android kann sie nicht ohne Neukodieren ändern. Dort gleicht die App
 *   die Lautstärke beim Abspielen an; die Datei bleibt unverändert.
 * Immer mit Schutz vor Übersteuern: Lauter wird höchstens bis knapp unter die Vollaussteuerung.
 */
object Normalize {
    const val TARGET_LUFS = -14.0
    /** Höchster erlaubter Spitzenwert nach dem Anheben (−1 dBFS). */
    private const val PEAK_LIMIT = 0.891

    enum class Method { Mp3, Wav, Playback }

    /** Ergebnis der Messung mit Vorschlag. */
    data class Plan(val track: Track, val lufs: Double, val peak: Double, val method: Method, val gainDb: Double, val limited: Boolean)

    fun methodFor(path: String): Method = when (path.substringAfterLast('.', "").lowercase()) {
        "mp3" -> Method.Mp3
        "wav" -> Method.Wav
        else -> Method.Playback
    }

    /** Schon angeglichen? Dann Methode und Änderung in dB. */
    fun info(path: String): Pair<Method, Double>? {
        val v = Store.normInfo[Store.resolve(path)] ?: return null
        val m = try { Method.valueOf(v.substringBefore(':')) } catch (e: Exception) { return null }
        return m to (v.substringAfter(':').substringBefore(':').toDoubleOrNull() ?: 0.0)
    }

    /** Messen und Änderung berechnen (Hintergrund-Thread). Ist das Lied schon angeglichen, wird vom Original aus gerechnet. */
    fun plan(t: Track): Plan? {
        val r = Loudness.measure(t.path) ?: return null
        val method = methodFor(t.path)
        // Bei MP3/WAV wird die (evtl. schon angepasste) Datei gemessen, beim Abspiel-Angleichen das unveränderte Original
        var gain = TARGET_LUFS - r.lufs
        var limited = false
        // Schutz vor Übersteuern: Spitze darf nach dem Anheben höchstens −1 dBFS erreichen
        if (r.peak > 0) {
            val maxGain = 20.0 * log10(PEAK_LIMIT / r.peak)
            if (gain > maxGain) { gain = maxGain; limited = true }
        }
        if (method == Method.Mp3) gain = floor(gain / 1.5 + 0.5) * 1.5    // MP3 nur in 1,5-dB-Schritten
        return Plan(t, r.lufs, r.peak, method, gain, limited)
    }

    /** Änderung anwenden. Gibt true zurück, wenn es geklappt hat. */
    fun apply(ctx: Context, p: Plan): Boolean {
        val path = p.track.path
        val prev = info(path)
        val ok = when (p.method) {
            Method.Mp3 -> { val steps = (p.gainDb / 1.5).roundToInt(); steps == 0 || Mp3Gain.apply(File(path), steps) }
            Method.Wav -> p.gainDb == 0.0 || WavGain.apply(File(path), p.gainDb)
            Method.Playback -> true
        }
        if (!ok) return false
        // Gesamt-dB gegenüber dem Original merken (bei mehrfachem Angleichen addieren sich MP3/WAV-Änderungen)
        val total = when (p.method) {
            Method.Playback -> p.gainDb
            else -> (prev?.takeIf { it.first == p.method }?.second ?: 0.0) + p.gainDb
        }
        Store.setNormInfo(ctx, path, if (kotlin.math.abs(total) < 0.05) null else "${p.method.name}:${"%.2f".format(java.util.Locale.ROOT, total)}")
        if (p.method != Method.Playback) {
            // Stille-Erkennung neu (Schwelle hängt von der Lautstärke ab) und Medien-Datenbank aktualisieren
            Store.clearTrim(ctx, path)
            Library.rescan(ctx, listOf(path))
        }
        return true
    }

    /** Zurück zum Original: MP3 exakt, WAV rechnerisch, beim Abspielen einfach aus. */
    fun undo(ctx: Context, path: String): Boolean {
        val (m, db) = info(path) ?: return true
        val ok = when (m) {
            Method.Mp3 -> Mp3Gain.apply(File(path), -(db / 1.5).roundToInt())
            Method.Wav -> WavGain.apply(File(path), -db)
            Method.Playback -> true
        }
        if (!ok) return false
        Store.setNormInfo(ctx, path, null)
        if (m != Method.Playback) { Store.clearTrim(ctx, path); Library.rescan(ctx, listOf(path)) }
        return true
    }

    // ---------- Angleichen beim Abspielen (nur für Formate, die nicht in der Datei geändert werden können) ----------

    /** Faktor für die Player-Lautstärke (≤ 1, zum Leiser-Machen). Der Sleep-Timer multipliziert damit. */
    @Volatile var playFactor: Float = 1f
        private set
    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = 0

    /** Bei jedem Liedwechsel im Wiedergabe-Dienst aufrufen. */
    fun onTrack(p: Player, item: MediaItem?, sessionId: Int) {
        val t = item?.let { Library.trackOf(it) }
        val i = t?.let { info(it.path) }
        val db = if (i != null && i.first == Method.Playback) i.second else 0.0
        playFactor = if (db < 0) 10.0.pow(db / 20.0).toFloat() else 1f
        p.volume = SleepTimer.fadeFactor * playFactor
        // Lauter als 100 %: Androids LoudnessEnhancer (mit eingebautem Begrenzer)
        try {
            if (db > 0.05 && sessionId != 0) {
                if (enhancer == null || enhancerSession != sessionId) {
                    enhancer?.release()
                    enhancer = LoudnessEnhancer(sessionId); enhancerSession = sessionId
                }
                enhancer?.setTargetGain((db * 100).roundToInt())
                enhancer?.setEnabled(true)
            } else enhancer?.setEnabled(false)
        } catch (e: Exception) { enhancer = null }
    }

    fun release() { try { enhancer?.release() } catch (e: Exception) { }; enhancer = null; enhancerSession = 0 }
}

/**
 * Verlustfreie MP3-Lautstärkeänderung (Verfahren von mp3gain): ändert in jedem Frame das Feld „global_gain“
 * um [steps] × 1,5 dB. Erst in eine Kopie geschrieben, dann ersetzt – bei Fehlern bleibt das Original unberührt.
 */
object Mp3Gain {
    private val BR1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BR2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val SR = arrayOf(intArrayOf(11025, 12000, 8000), intArrayOf(0, 0, 0), intArrayOf(22050, 24000, 16000), intArrayOf(44100, 48000, 32000))

    private class Frame(val len: Int, val mpeg1: Boolean, val mono: Boolean, val crc: Boolean)

    private fun frameAt(b: ByteArray, p: Int, end: Int): Frame? {
        if (p + 4 > end) return null
        val b1 = b[p + 1].toInt() and 0xFF; val b2 = b[p + 2].toInt() and 0xFF; val b3 = b[p + 3].toInt() and 0xFF
        if ((b[p].toInt() and 0xFF) != 0xFF || (b1 and 0xE0) != 0xE0) return null
        val ver = (b1 shr 3) and 3
        if (ver == 1 || ((b1 shr 1) and 3) != 1) return null      // reserviert bzw. nicht Layer III
        val bri = (b2 shr 4) and 0xF
        val sri = (b2 shr 2) and 3
        if (bri == 0 || bri == 15 || sri == 3) return null
        val mpeg1 = ver == 3
        val br = (if (mpeg1) BR1 else BR2)[bri]
        val sr = SR[ver][sri]
        val pad = (b2 shr 1) and 1
        val len = (if (mpeg1) 144000 else 72000) * br / sr + pad
        if (len < 21) return null
        return Frame(len, mpeg1, ((b3 shr 6) and 3) == 3, (b1 and 1) == 0)
    }

    private fun getBits(b: ByteArray, bit: Int, n: Int): Int {
        var v = 0
        for (i in 0 until n) { val x = bit + i; v = (v shl 1) or ((b[x shr 3].toInt() shr (7 - (x and 7))) and 1) }
        return v
    }

    private fun setBits(b: ByteArray, bit: Int, n: Int, value: Int) {
        for (i in 0 until n) {
            val x = bit + i
            val one = (value shr (n - 1 - i)) and 1
            val mask = 1 shl (7 - (x and 7))
            b[x shr 3] = if (one == 1) (b[x shr 3].toInt() or mask).toByte() else (b[x shr 3].toInt() and mask.inv()).toByte()
        }
    }

    /** CRC-16 (0x8005) über Kopf-Bytes 2–3 und Seiteninformation – nur bei Frames mit Prüfsumme nötig. */
    private fun crc(b: ByteArray, p: Int, sideLen: Int): Int {
        var c = 0xFFFF
        fun feed(byte: Int) { for (i in 7 downTo 0) { val bit = ((byte shr i) and 1) xor ((c shr 15) and 1); c = (c shl 1) and 0xFFFF; if (bit == 1) c = c xor 0x8005 } }
        feed(b[p + 2].toInt() and 0xFF); feed(b[p + 3].toInt() and 0xFF)
        for (i in 0 until sideLen) feed(b[p + 6 + i].toInt() and 0xFF)
        return c
    }

    fun apply(file: File, steps: Int): Boolean {
        if (steps == 0) return true
        val b = try { file.readBytes() } catch (e: Exception) { return false }
        var pos = 0
        // ID3v2 am Anfang überspringen
        if (b.size > 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) {
            val size = ((b[6].toInt() and 0x7F) shl 21) or ((b[7].toInt() and 0x7F) shl 14) or ((b[8].toInt() and 0x7F) shl 7) or (b[9].toInt() and 0x7F)
            pos = 10 + size + if ((b[5].toInt() and 0x10) != 0) 10 else 0
        }
        // ID3v1 am Ende nicht anfassen
        var end = b.size
        if (end >= 128 && b[end - 128] == 'T'.code.toByte() && b[end - 127] == 'A'.code.toByte() && b[end - 126] == 'G'.code.toByte()) end -= 128
        var frames = 0
        while (pos < end) {
            val f = frameAt(b, pos, end)
            // Erster Frame: zur Sicherheit muss direkt danach noch ein gültiger Frame kommen
            if (f == null || pos + f.len > end || (frames == 0 && pos + f.len < end - 4 && frameAt(b, pos + f.len, end) == null)) {
                pos++; continue
            }
            val side = pos + 4 + if (f.crc) 2 else 0
            val nch = if (f.mono) 1 else 2
            val sideLen = if (f.mpeg1) (if (f.mono) 17 else 32) else (if (f.mono) 9 else 17)
            if (side + sideLen > end) break
            val base = side * 8
            if (f.mpeg1) {
                val start = 9 + (if (f.mono) 5 else 3) + 4 * nch
                for (gr in 0 until 2) for (c in 0 until nch) {
                    val at = base + start + (gr * nch + c) * 59 + 21
                    setBits(b, at, 8, (getBits(b, at, 8) + steps).coerceIn(0, 255))
                }
            } else {
                val start = 8 + (if (f.mono) 1 else 2)
                for (c in 0 until nch) {
                    val at = base + start + c * 63 + 21
                    setBits(b, at, 8, (getBits(b, at, 8) + steps).coerceIn(0, 255))
                }
            }
            if (f.crc) { val v = crc(b, pos, sideLen); b[pos + 4] = (v shr 8).toByte(); b[pos + 5] = v.toByte() }
            frames++
            pos += f.len
        }
        if (frames < 10) return false
        return replace(file) { it.writeBytes(b) }
    }
}

/** WAV: Samples direkt umrechnen (16/24/32 Bit Ganzzahl, 32 Bit Gleitkomma), mit Begrenzung gegen Übersteuern. */
object WavGain {
    fun apply(file: File, db: Double): Boolean {
        if (db == 0.0) return true
        val g = 10.0.pow(db / 20.0)
        return replace(file) { tmp ->
            file.copyTo(tmp, overwrite = true)
            RandomAccessFile(tmp, "rw").use { raf ->
                fun le32(): Long { val x = ByteArray(4); raf.readFully(x); return (x[0].toLong() and 0xFF) or ((x[1].toLong() and 0xFF) shl 8) or ((x[2].toLong() and 0xFF) shl 16) or ((x[3].toLong() and 0xFF) shl 24) }
                fun id(): String { val x = ByteArray(4); raf.readFully(x); return String(x, Charsets.US_ASCII) }
                if (id() != "RIFF") throw IllegalStateException("kein WAV"); le32(); if (id() != "WAVE") throw IllegalStateException("kein WAV")
                var format = 0; var bits = 0; var dataPos = -1L; var dataLen = 0L
                while (raf.filePointer + 8 <= raf.length()) {
                    val cid = id(); val len = le32()
                    val next = raf.filePointer + len + (len and 1)
                    if (cid == "fmt ") {
                        val x = ByteArray(minOf(len, 40L).toInt()); raf.readFully(x)
                        format = (x[0].toInt() and 0xFF) or ((x[1].toInt() and 0xFF) shl 8)
                        bits = (x[14].toInt() and 0xFF) or ((x[15].toInt() and 0xFF) shl 8)
                        if (format == 0xFFFE && x.size >= 26) format = (x[24].toInt() and 0xFF) or ((x[25].toInt() and 0xFF) shl 8)
                    } else if (cid == "data") { dataPos = raf.filePointer; dataLen = minOf(len, raf.length() - dataPos); break }
                    raf.seek(next)
                }
                val bps = bits / 8
                if (dataPos < 0 || bps == 0 || !(format == 1 && bps in 2..4 || format == 3 && bps == 4)) throw IllegalStateException("Format nicht unterstützt")
                val buf = ByteArray(64 * 1024 / bps * bps)
                var p = dataPos
                val endPos = dataPos + dataLen - dataLen % bps
                while (p < endPos) {
                    val n = minOf(buf.size.toLong(), endPos - p).toInt()
                    raf.seek(p); raf.readFully(buf, 0, n)
                    var i = 0
                    while (i + bps <= n) {
                        when {
                            format == 3 -> {
                                val v = java.lang.Float.intBitsToFloat((buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8) or ((buf[i + 2].toInt() and 0xFF) shl 16) or (buf[i + 3].toInt() shl 24))
                                val o = java.lang.Float.floatToIntBits((v * g).toFloat().coerceIn(-1f, 1f))
                                buf[i] = o.toByte(); buf[i + 1] = (o shr 8).toByte(); buf[i + 2] = (o shr 16).toByte(); buf[i + 3] = (o shr 24).toByte()
                            }
                            bps == 2 -> {
                                val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                                val o = (v * g).roundToInt().coerceIn(-32768, 32767)
                                buf[i] = o.toByte(); buf[i + 1] = (o shr 8).toByte()
                            }
                            bps == 3 -> {
                                val v = (buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8) or (buf[i + 2].toInt() shl 16)
                                val o = (v * g).roundToInt().coerceIn(-8388608, 8388607)
                                buf[i] = o.toByte(); buf[i + 1] = (o shr 8).toByte(); buf[i + 2] = (o shr 16).toByte()
                            }
                            else -> {
                                val v = (buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8) or ((buf[i + 2].toInt() and 0xFF) shl 16) or (buf[i + 3].toInt() shl 24)
                                val o = (v * g).coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).roundToInt()
                                buf[i] = o.toByte(); buf[i + 1] = (o shr 8).toByte(); buf[i + 2] = (o shr 16).toByte(); buf[i + 3] = (o shr 24).toByte()
                            }
                        }
                        i += bps
                    }
                    raf.seek(p); raf.write(buf, 0, n)
                    p += n
                }
            }
        }
    }
}

/** Sicher ersetzen: erst eine Kopie im selben Ordner schreiben, dann gegen das Original tauschen. */
private fun replace(file: File, write: (File) -> Unit): Boolean {
    val tmp = File(file.parentFile, ".${file.name}.norm")
    return try {
        write(tmp)
        val bak = File(file.parentFile, ".${file.name}.orig")
        if (!file.renameTo(bak)) { tmp.delete(); return false }
        if (!tmp.renameTo(file)) { bak.renameTo(file); tmp.delete(); return false }
        bak.delete()
        true
    } catch (e: Exception) { tmp.delete(); false }
}
