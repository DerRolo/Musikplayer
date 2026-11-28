package de.musikplayer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optionen → Profil: Name und Profilbild, die andere im Jam sehen (Pop-up, Mitgliederliste, „eingereiht von“).
 * Ohne eigenen Namen gilt der Gerätename aus den Android-Einstellungen.
 */
@Composable
fun ProfilePage(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Store.profileName) }
    var info by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext false
                    // quadratisch zuschneiden und auf 256 px verkleinern
                    val side = minOf(src.width, src.height)
                    val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    val out = Bitmap.createScaledBitmap(sq, 256, 256, true)
                    File(ctx.filesDir, "profile.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    true
                } catch (e: Exception) { false }
            }
            if (ok) { Store.setProfileStamp(ctx, System.currentTimeMillis()); Jam.profileChanged(); info = "Profilbild gespeichert" }
            else info = "Bild konnte nicht geladen werden"
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Profil", onBack)
        Text(
            "So sehen dich andere im Jam: im Pop-up, in der Mitgliederliste und bei „eingereiht von“.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            JamAvatar(Jam.myId, name.ifBlank { Jam.deviceName }, 72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Profilbild wählen") }
                // Bild steht schief/quer? Jeweils um 90° im Uhrzeigersinn drehen
                if (Store.profileStamp != 0L) TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                val f = File(ctx.filesDir, "profile.jpg")
                                val src = BitmapFactory.decodeFile(f.path) ?: return@withContext false
                                val m = android.graphics.Matrix().apply { postRotate(90f) }
                                val r = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                                f.outputStream().use { r.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                true
                            } catch (e: Exception) { false }
                        }
                        if (ok) { Store.setProfileStamp(ctx, System.currentTimeMillis()); Jam.profileChanged() }
                    }
                }) { Text("↻ Drehen") }
                if (Store.profileStamp != 0L) TextButton(onClick = {
                    File(ctx.filesDir, "profile.jpg").delete()
                    Store.setProfileStamp(ctx, 0L); Jam.profileChanged(); info = "Profilbild entfernt"
                }) { Text("Profilbild entfernen") }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40); Store.setProfileName(ctx, name) },
            singleLine = true,
            label = { Text("Name") },
            placeholder = { Text(Jam.deviceName) },
            supportingText = { Text("Leer lassen = Gerätename („${Jam.deviceName}“)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ORANGE, modifier = Modifier.padding(16.dp)) }
    }
}
