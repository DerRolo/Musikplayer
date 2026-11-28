package de.musikplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var granted by mutableStateOf(false)
    private val vm: MusicViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasAccess()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasAccess()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (granted) {
                        MusicApp(vm)
                    } else {
                        // Eine von außen geöffnete Datei spielt auch ohne Dateizugriff; die Mini-Leiste zeigt sie an
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) { PermissionScreen(onGrant = { requestAccess() }) }
                            if (vm.current != null) MiniPlayer(vm, onAddToPlaylist = {}, onEdit = {}, onOpen = {})
                        }
                    }
                }
            }
        }
        // Nur beim echten Start auswerten, nicht nach Drehen oder beim Öffnen aus den letzten Apps
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) handleOpenIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** „Öffnen mit“ aus Dateimanager, Messenger usw.: genau dieses Lied abspielen, ohne es in die Bibliothek aufzunehmen. */
    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        // Dauerhafte Leseberechtigung, falls die liefernde App sie anbietet (sonst gilt die normale, befristete Freigabe)
        if ((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
        }
        vm.playExternal(uri)
    }

    override fun onStart() {
        super.onStart()
        // Jam: andere Handys im WLAN nur suchen, solange die App offen ist
        Jam.setForeground(this, true)
    }

    override fun onStop() {
        // Beim Verlassen offene Änderungen sichern (für eine Neuinstallation)
        Backup.flush(this)
        Jam.setForeground(this, false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        granted = hasAccess()
        // Aus dem Browser / der Torrent-App zurück: neue Downloads finden und unter „Neue Lieder“ zeigen
        if (granted) Downloads.check(this) { n ->
            vm.refresh()
            vm.message = if (n == 1) "1 neues Lied heruntergeladen – siehe Neue Lieder" else "$n neue Lieder heruntergeladen – siehe Neue Lieder"
        }
    }

    /** Zugriff auf alle Dateien (wie im Dateimanager) – nötig zum Einsortieren und Löschen. */
    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permLauncher.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            )
        }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Musikplayer", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Zum Finden, Einsortieren und Löschen deiner Lieder braucht die App Zugriff auf alle Dateien.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onGrant) { Text("Zugriff erlauben") }
    }
}

// Design (Hell/Dunkel, Farbvorlagen): siehe Theme.kt
