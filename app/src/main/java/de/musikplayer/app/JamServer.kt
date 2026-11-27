package de.musikplayer.app

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Kleiner HTTP-Server nur fürs lokale WLAN (Jam). Eine Anfrage pro Verbindung, Dateien mit Range-Unterstützung,
 * damit der Player beim Streamen springen kann. Keine Fremd-Bibliothek nötig.
 */
class JamServer(private val handle: (Request) -> Response) {

    class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: ByteArray,
        /** IP-Adresse des Anfragenden. */
        val remote: String
    ) {
        fun bodyText(): String = String(body, Charsets.UTF_8)
    }

    class Response(val status: Int, val type: String = "application/json", val bytes: ByteArray? = null, val file: File? = null) {
        companion object {
            fun json(s: String, status: Int = 200) = Response(status, "application/json; charset=utf-8", s.toByteArray(Charsets.UTF_8))
            fun error(status: Int, msg: String = "") = json("{\"error\":\"${msg.replace("\"", "'")}\"}", status)
            fun file(f: File) = Response(200, mimeOf(f.name), file = f)
            fun image(b: ByteArray) = Response(200, "image/jpeg", b)

            fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
                "mp3" -> "audio/mpeg"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "m4a", "mp4", "aac" -> "audio/mp4"
                "ogg", "oga" -> "audio/ogg"
                "opus" -> "audio/opus"
                else -> "application/octet-stream"
            }
        }
    }

    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile var port: Int = 0
        private set

    fun start(): Int {
        socket?.let { return port }
        val s = ServerSocket(0, 32, null as InetAddress?)
        socket = s
        port = s.localPort
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                pool.execute { serve(c) }
            }
        }, "JamServer").apply { isDaemon = true }.start()
        return port
    }

    fun stop() {
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        port = 0
    }

    private fun serve(c: Socket) {
        try {
            c.soTimeout = 15000
            val input = BufferedInputStream(c.getInputStream())
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val parts = lines.first().split(" ")
            if (parts.size < 2) return
            val headers = HashMap<String, String>()
            for (l in lines.drop(1)) {
                val i = l.indexOf(':')
                if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val len = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 8 * 1024 * 1024) ?: 0
            val body = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = input.read(body, read, len - read)
                if (n < 0) break
                read += n
            }
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))
            val req = Request(parts[0].uppercase(), path, query, headers, body, c.inetAddress?.hostAddress.orEmpty())
            val res = try { handle(req) } catch (e: Exception) { Response.error(500, e.message ?: "Fehler") }
            write(c.getOutputStream(), req, res)
        } catch (e: Exception) {
            // Verbindung abgebrochen – kein Problem
        } finally {
            try { c.close() } catch (e: Exception) { }
        }
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        var last4 = 0
        while (sb.length < 16 * 1024) {
            val b = input.read()
            if (b < 0) return null
            sb.append(b.toChar())
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) return sb.substring(0, sb.length - 4)
        }
        return null
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull {
            val k = it.substringBefore('=')
            if (k.isEmpty()) null else URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.toMap()
    }

    private fun write(out: OutputStream, req: Request, res: Response) {
        val f = res.file
        if (f == null) {
            val b = res.bytes ?: ByteArray(0)
            out.write(header(res.status, res.type, b.size.toLong(), null).toByteArray())
            if (req.method != "HEAD") out.write(b)
            out.flush()
            return
        }
        if (!f.isFile) {
            val b = "{\"error\":\"missing\"}".toByteArray()
            out.write(header(404, "application/json", b.size.toLong(), null).toByteArray()); out.write(b); out.flush()
            return
        }
        val total = f.length()
        var start = 0L
        var end = total - 1
        var status = 200
        val range = req.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").substringBefore(',')
            val a = spec.substringBefore('-').trim()
            val b = spec.substringAfter('-', "").trim()
            if (a.isEmpty() && b.isNotEmpty()) { start = (total - (b.toLongOrNull() ?: 0L)).coerceAtLeast(0L) }
            else {
                start = a.toLongOrNull() ?: 0L
                if (b.isNotEmpty()) end = (b.toLongOrNull() ?: end).coerceAtMost(total - 1)
            }
            if (start >= total || start > end) {
                out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                out.flush()
                return
            }
            status = 206
        }
        val len = end - start + 1
        out.write(header(status, res.type, len, if (status == 206) "bytes $start-$end/$total" else null).toByteArray())
        if (req.method == "HEAD") { out.flush(); return }
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = len
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
        out.flush()
    }

    private fun header(status: Int, type: String, len: Long, contentRange: String?): String {
        val text = when (status) { 200 -> "OK"; 206 -> "Partial Content"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; 410 -> "Gone"; else -> "Status" }
        val sb = StringBuilder("HTTP/1.1 $status $text\r\n")
        sb.append("Content-Type: $type\r\n")
        sb.append("Content-Length: $len\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        if (contentRange != null) sb.append("Content-Range: $contentRange\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("Connection: close\r\n\r\n")
        return sb.toString()
    }
}
