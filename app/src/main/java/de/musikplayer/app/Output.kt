package de.musikplayer.app

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer

/** Ein Ausgabegerät (Handy-Lautsprecher, Bluetooth-Box, Kopfhörer …). [id] = -1 bedeutet „Automatisch“. */
data class OutputDev(val id: Int, val name: String, val kind: String)

/**
 * Ausgabegerät für die Wiedergabe wählen. Gezeigt werden nur Geräte, die gerade verbunden sind.
 * „Automatisch“ = Android entscheidet (normalerweise das zuletzt verbundene Gerät, z. B. die Bluetooth-Box).
 * Im Jam gibt es nur eine Ausgabe: die des Hosts. Gäste wählen damit das Ausgabegerät am Host-Handy.
 */
object Output {
    const val AUTO = -1

    var devices by mutableStateOf<List<OutputDev>>(emptyList())
        private set
    /** Vom Nutzer gewählt; AUTO = Android entscheidet. */
    var selected by mutableStateOf(AUTO)
        private set
    /** Wohin Android gerade tatsächlich ausgibt (ab Android 13 abfragbar, sonst AUTO). */
    var active by mutableStateOf(AUTO)
        private set

    private var am: AudioManager? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            // Gewähltes Gerät getrennt → zurück auf Automatisch
            if (removed.any { it.id == selected }) select(AUTO)
            refresh()
        }
    }

    fun start(ctx: Context) {
        if (am != null) return
        val m = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am = m
        m.registerAudioDeviceCallback(callback, main)
        refresh()
    }

    fun refresh() {
        val m = am ?: return
        val list = m.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { d -> describe(d)?.let { OutputDev(d.id, it.first, it.second) } }
        // Manche Geräte melden dasselbe Ziel mehrfach (z. B. Lautsprecher in zwei Varianten)
        devices = list.distinctBy { it.kind + "|" + it.name }
        active = if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
                m.getAudioDevicesForAttributes(attrs).firstOrNull()?.id ?: AUTO
            } catch (e: Exception) { AUTO }
        } else AUTO
    }

    /** Name und Art für die Anzeige; null = für Musik ungeeignet (Hörmuschel, Telefonie usw.). */
    private fun describe(d: AudioDeviceInfo): Pair<String, String>? {
        val product = d.productName?.toString()?.trim().orEmpty()
        return when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Dieses Handy" to "speaker"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> product.ifBlank { "Bluetooth-Gerät" } to "bt"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kopfhörer (Kabel)" to "wired"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> product.ifBlank { "USB-Audio" } to "usb"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI" to "other"
            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> "Line-Ausgang" to "wired"
            else -> {
                // Bluetooth LE Audio (Android 12/13+): Kopfhörer und Lautsprecher
                if (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) product.ifBlank { "Bluetooth-Kopfhörer" } to "bt"
                else if (Build.VERSION.SDK_INT >= 33 && d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) product.ifBlank { "Bluetooth-Lautsprecher" } to "bt"
                else null
            }
        }
    }

    /** Ausgabe umschalten (am eigenen Player). AUTO = Android entscheidet. */
    fun select(id: Int) {
        val m = am
        val dev = if (id == AUTO || m == null) null else m.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
        (Jam.player as? ExoPlayer)?.setPreferredAudioDevice(dev)
        selected = if (dev == null) AUTO else id
        main.postDelayed({ refresh() }, 400)
    }

    /** Name der aktuellen Ausgabe für die Anzeige. */
    fun currentName(list: List<OutputDev>, selected: Int, active: Int): String {
        val id = if (selected != AUTO) selected else active
        return list.firstOrNull { it.id == id }?.name ?: "Automatisch"
    }
}
