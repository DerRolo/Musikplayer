package de.musikplayer.app

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Farbvorlage: Akzentfarbe und Hintergründe für Hell und Dunkel. Die dunklen Hintergründe sind bewusst kein
 * reines Schwarz, sondern leicht in der Farbe der Vorlage getönt (freundlicher). „Mitternacht“ ist reines Schwarz.
 */
data class Palette(
    val id: String, val name: String,
    val darkPrimary: Color, val darkBg: Color, val darkSurface: Color, val darkVariant: Color,
    val lightPrimary: Color, val lightBg: Color, val lightSurface: Color, val lightVariant: Color
)

object AppThemes {
    const val SYSTEM_COLORS = "system"

    val palettes = listOf(
        Palette("musikplayer", "Musikplayer", Color(0xFFFFB45C), Color(0xFF1C1A1F), Color(0xFF242128), Color(0xFF353039),
            Color(0xFFB45309), Color(0xFFFFF8F1), Color(0xFFFFFFFF), Color(0xFFF2E6DA)),
        Palette("ozean", "Ozean", Color(0xFF6CCBF5), Color(0xFF111D25), Color(0xFF172833), Color(0xFF243D4C),
            Color(0xFF0369A1), Color(0xFFF2F8FC), Color(0xFFFFFFFF), Color(0xFFDDEBF3)),
        Palette("wald", "Wald", Color(0xFF8DD39A), Color(0xFF131C16), Color(0xFF1A271E), Color(0xFF293B2D),
            Color(0xFF2E7D32), Color(0xFFF3F8F2), Color(0xFFFFFFFF), Color(0xFFDFEBDD)),
        Palette("beere", "Beere", Color(0xFFF59AC0), Color(0xFF21141B), Color(0xFF2C1A24), Color(0xFF412935),
            Color(0xFFAD1457), Color(0xFFFDF2F6), Color(0xFFFFFFFF), Color(0xFFF3DDE7)),
        Palette("lavendel", "Lavendel", Color(0xFFBCA6F2), Color(0xFF17151F), Color(0xFF211E2D), Color(0xFF332D46),
            Color(0xFF5E35B1), Color(0xFFF7F4FD), Color(0xFFFFFFFF), Color(0xFFE6E0F5)),
        Palette("sonnenuntergang", "Sonnenuntergang", Color(0xFFFF8A65), Color(0xFF201715), Color(0xFF2B1F1C), Color(0xFF41302B),
            Color(0xFFC2410C), Color(0xFFFFF5F0), Color(0xFFFFFFFF), Color(0xFFF6E2D9)),
        Palette("graphit", "Graphit", Color(0xFFB9C6CF), Color(0xFF17191B), Color(0xFF202326), Color(0xFF30353A),
            Color(0xFF455A64), Color(0xFFF4F5F6), Color(0xFFFFFFFF), Color(0xFFE3E6E8)),
        Palette("mitternacht", "Mitternacht", Color(0xFFFFB45C), Color(0xFF000000), Color(0xFF0E0E10), Color(0xFF242228),
            Color(0xFFB45309), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFEEEEEE)),
    )

    fun byId(id: String) = palettes.firstOrNull { it.id == id } ?: palettes.first()

    fun scheme(p: Palette, dark: Boolean): ColorScheme = if (dark) {
        val bg = p.darkBg; val sv = p.darkVariant
        darkColorScheme(
            primary = p.darkPrimary, onPrimary = Color(0xFF1A1300),
            primaryContainer = lerp(bg, p.darkPrimary, 0.35f), onPrimaryContainer = Color.White,
            secondary = p.darkPrimary, onSecondary = Color(0xFF1A1300),
            secondaryContainer = lerp(bg, p.darkPrimary, 0.25f), onSecondaryContainer = Color.White,
            tertiary = p.darkPrimary, tertiaryContainer = lerp(bg, p.darkPrimary, 0.22f), onTertiaryContainer = Color.White,
            background = bg, onBackground = Color(0xFFECE7EE),
            surface = p.darkSurface, onSurface = Color(0xFFECE7EE),
            surfaceVariant = sv, onSurfaceVariant = Color(0xFFC9C2CC),
            surfaceTint = p.darkPrimary, outline = lerp(sv, Color.White, 0.25f), outlineVariant = sv,
            surfaceContainerLowest = lerp(bg, Color.Black, 0.3f), surfaceContainerLow = lerp(bg, p.darkSurface, 0.5f),
            surfaceContainer = p.darkSurface, surfaceContainerHigh = lerp(p.darkSurface, sv, 0.5f), surfaceContainerHighest = sv,
            surfaceBright = lerp(sv, Color.White, 0.08f), surfaceDim = bg
        )
    } else {
        val bg = p.lightBg; val sv = p.lightVariant
        lightColorScheme(
            primary = p.lightPrimary, onPrimary = Color.White,
            primaryContainer = lerp(bg, p.lightPrimary, 0.22f), onPrimaryContainer = Color(0xFF1C1B1F),
            secondary = p.lightPrimary, onSecondary = Color.White,
            secondaryContainer = lerp(bg, p.lightPrimary, 0.16f), onSecondaryContainer = Color(0xFF1C1B1F),
            tertiary = p.lightPrimary, tertiaryContainer = lerp(bg, p.lightPrimary, 0.18f), onTertiaryContainer = Color(0xFF1C1B1F),
            background = bg, onBackground = Color(0xFF1C1B1F),
            surface = p.lightSurface, onSurface = Color(0xFF1C1B1F),
            surfaceVariant = sv, onSurfaceVariant = Color(0xFF4F4A52),
            surfaceTint = p.lightPrimary, outline = lerp(sv, Color.Black, 0.35f), outlineVariant = sv,
            surfaceContainerLowest = Color.White, surfaceContainerLow = lerp(bg, Color.White, 0.5f),
            surfaceContainer = bg, surfaceContainerHigh = lerp(bg, sv, 0.5f), surfaceContainerHighest = sv,
            surfaceBright = Color.White, surfaceDim = lerp(bg, sv, 0.7f)
        )
    }
}

/** Gewähltes Design (Compose-Zustand, damit ein Wechsel sofort überall wirkt). */
object ThemePrefs {
    /** 0 = wie das System, 1 = hell, 2 = dunkel */
    var mode by mutableStateOf(0)
    var paletteId by mutableStateOf("musikplayer")
    private var loaded = false

    fun ensure(ctx: android.content.Context) {
        if (loaded) return
        loaded = true
        Store.load(ctx)
        mode = Store.themeMode
        paletteId = Store.themeId
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    ThemePrefs.ensure(ctx.applicationContext)
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePrefs.mode) { 1 -> false; 2 -> true; else -> systemDark }
    val scheme = if (ThemePrefs.paletteId == AppThemes.SYSTEM_COLORS && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else AppThemes.scheme(AppThemes.byId(ThemePrefs.paletteId), dark)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Optionen → Design: Hell/Dunkel/System und Farbvorlagen mit Vorschau (Antippen wirkt sofort). */
@Composable
fun ThemePage(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePrefs.mode) { 1 -> false; 2 -> true; else -> systemDark }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Design", onBack)
        Text("Helligkeit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wie System", "Hell", "Dunkel").forEachIndexed { i, l ->
                LetterChip(l, ThemePrefs.mode == i, true) { ThemePrefs.mode = i; Store.setTheme(ctx, i, ThemePrefs.paletteId) }
            }
        }
        Text("Farben", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        val choices = (if (Build.VERSION.SDK_INT >= 31) listOf<Palette?>(null) else emptyList()) + AppThemes.palettes
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            items(choices, key = { it?.id ?: AppThemes.SYSTEM_COLORS }) { p ->
                val id = p?.id ?: AppThemes.SYSTEM_COLORS
                val selected = ThemePrefs.paletteId == id
                val sc = if (p == null) MaterialTheme.colorScheme.let { if (selected) it else it } else AppThemes.scheme(p, dark)
                Column(
                    Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
                        .clickable { ThemePrefs.paletteId = id; Store.setTheme(ctx, ThemePrefs.mode, id) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mini-Vorschau: Hintergrund, eine „Liedzeile“ und die Akzentfarbe
                    Box(Modifier.fillMaxWidth().aspectRatio(1.1f).background(sc.background).padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(sc.primary))
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.height(8.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(sc.surfaceVariant))
                            }
                            Box(Modifier.height(8.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).background(sc.surfaceVariant))
                            Box(Modifier.height(16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(sc.surface))
                        }
                        Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(sc.primary))
                    }
                    Text(
                        p?.name ?: "Systemfarben", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
