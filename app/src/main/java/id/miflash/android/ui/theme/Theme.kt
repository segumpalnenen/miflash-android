package id.miflash.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.miflash.android.R

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class ThemePalette(
    val key: String,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color
)

data class MiFlashColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val onSurface: Color,
    val onBackground: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onSurfaceVariant: Color,
    val logBackground: Color,
    val logText: Color,
    val logStage: Color,
    val termCommand: Color,
    val termOutput: Color,
    val textDim: Color,
    val statusOkBg: Color,
    val statusWarnBg: Color,
    val drawerSelected: Color,
    val track: Color,
    val headerBackground: Color,
    val greenText: Color,
    val orangeText: Color,
    val redText: Color,
    val warningText: Color,
    val pillEdlBg: Color,
    val pillEdlText: Color,
    val pillFastbootBg: Color,
    val pillFastbootText: Color,
    val pillNoneBg: Color,
    val pillNoneText: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color
) {
    companion object {
        val DEFAULT = MiFlashColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            surfaceVariant = Color(0xFF262626),
            border = Color(0xFF2E2E2E),
            onSurface = Color(0xFFF5F5F5),
            onBackground = Color(0xFFF5F5F5),
            textSecondary = Color(0xFFC9C9C9),
            textMuted = Color(0xFFA8A8A8),
            onSurfaceVariant = Color(0xFFC7C7C7),
            logBackground = Color(0xFF0A0A0A),
            logText = Color(0xFFE0E0E0),
            logStage = Color(0xFFFFA040),
            termCommand = Color(0xFFFFB74D),
            termOutput = Color(0xFFEDEDED),
            textDim = Color(0xFFBDBDBD),
            statusOkBg = Color(0xFF1B2B1B),
            statusWarnBg = Color(0xFF4A3400),
            drawerSelected = Color(0xFF2E2E2E),
            track = Color(0xFF2E2E2E),
            headerBackground = Color(0xFF232323),
            greenText = Color(0xFF81C784),
            orangeText = Color(0xFFFFA040),
            redText = Color(0xFFFF8A80),
            warningText = Color(0xFFFFB300),
            pillEdlBg = Color(0xFF81C784),
            pillEdlText = Color(0xFF1B5E20),
            pillFastbootBg = Color(0xFF7BA7FF),
            pillFastbootText = Color(0xFF0D1B3E),
            pillNoneBg = Color(0xFF3A3A3A),
            pillNoneText = Color(0xFFBDBDBD),
            primary = Color(0xFFF9A825),
            onPrimary = Color(0xFF1A1A1A),
            secondary = Color(0xFFFFE082),
            onSecondary = Color(0xFF1A1A1A)
        )
    }
}

val LocalMiFlashColors = staticCompositionLocalOf { MiFlashColors.DEFAULT }

@Composable
fun themePalettes(): List<ThemePalette> = listOf(
    ThemePalette(
        "blue",
        colorResource(R.color.palette_blue_primary),
        Color.White,
        colorResource(R.color.palette_blue_secondary),
        Color.White
    ),
    ThemePalette(
        "red",
        colorResource(R.color.palette_red_primary),
        Color.White,
        colorResource(R.color.palette_red_secondary),
        Color.White
    ),
    ThemePalette(
        "green",
        colorResource(R.color.palette_green_primary),
        Color.White,
        colorResource(R.color.palette_green_secondary),
        Color.White
    ),
    ThemePalette(
        "yellow",
        colorResource(R.color.palette_yellow_primary),
        colorResource(R.color.palette_yellow_on_primary),
        colorResource(R.color.palette_yellow_secondary),
        colorResource(R.color.palette_yellow_on_secondary)
    ),
    ThemePalette(
        "purple",
        colorResource(R.color.palette_purple_primary),
        Color.White,
        colorResource(R.color.palette_purple_secondary),
        Color.White
    ),
    ThemePalette(
        "orange",
        colorResource(R.color.palette_orange_primary),
        Color.White,
        colorResource(R.color.palette_orange_secondary),
        Color.White
    ),
    ThemePalette(
        "teal",
        colorResource(R.color.palette_teal_primary),
        Color.White,
        colorResource(R.color.palette_teal_secondary),
        Color.White
    ),
    ThemePalette(
        "pink",
        colorResource(R.color.palette_pink_primary),
        Color.White,
        colorResource(R.color.palette_pink_secondary),
        Color.White
    )
)

fun paletteFor(palettes: List<ThemePalette>, key: String): ThemePalette =
    palettes.firstOrNull { it.key == key } ?: palettes.first { it.key == "yellow" }

@Composable
private fun darkColors(palette: ThemePalette) = MiFlashColors(
    background = colorResource(R.color.mi_dark_background),
    surface = colorResource(R.color.mi_dark_surface),
    surfaceVariant = colorResource(R.color.mi_dark_surface_variant),
    border = colorResource(R.color.mi_dark_border),
    onSurface = colorResource(R.color.mi_dark_on_surface),
    onBackground = colorResource(R.color.mi_dark_on_background),
    textSecondary = colorResource(R.color.mi_dark_text_secondary),
    textMuted = colorResource(R.color.mi_dark_text_muted),
    onSurfaceVariant = colorResource(R.color.mi_dark_on_surface_variant),
    logBackground = colorResource(R.color.mi_dark_log_background),
    logText = colorResource(R.color.mi_dark_log_text),
    logStage = colorResource(R.color.mi_dark_log_stage),
    termCommand = colorResource(R.color.mi_dark_term_command),
    termOutput = colorResource(R.color.mi_dark_term_output),
    textDim = colorResource(R.color.mi_dark_text_dim),
    statusOkBg = colorResource(R.color.mi_dark_status_ok_bg),
    statusWarnBg = colorResource(R.color.mi_dark_status_warn_bg),
    drawerSelected = colorResource(R.color.mi_dark_drawer_selected),
    track = colorResource(R.color.mi_dark_track),
    headerBackground = colorResource(R.color.mi_dark_header_background),
    greenText = colorResource(R.color.mi_dark_green_text),
    orangeText = colorResource(R.color.mi_dark_orange_text),
    redText = colorResource(R.color.mi_dark_red_text),
    warningText = colorResource(R.color.mi_dark_warning_text),
    pillEdlBg = colorResource(R.color.mi_dark_pill_edl_bg),
    pillEdlText = colorResource(R.color.mi_dark_pill_edl_text),
    pillFastbootBg = colorResource(R.color.mi_dark_pill_fastboot_bg),
    pillFastbootText = colorResource(R.color.mi_dark_pill_fastboot_text),
    pillNoneBg = colorResource(R.color.mi_dark_pill_none_bg),
    pillNoneText = colorResource(R.color.mi_dark_pill_none_text),
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    secondary = palette.secondary,
    onSecondary = palette.onSecondary
)

@Composable
private fun lightColors(palette: ThemePalette) = MiFlashColors(
    background = colorResource(R.color.mi_light_background),
    surface = colorResource(R.color.mi_light_surface),
    surfaceVariant = colorResource(R.color.mi_light_surface_variant),
    border = colorResource(R.color.mi_light_border),
    onSurface = colorResource(R.color.mi_light_on_surface),
    onBackground = colorResource(R.color.mi_light_on_background),
    textSecondary = colorResource(R.color.mi_light_text_secondary),
    textMuted = colorResource(R.color.mi_light_text_muted),
    onSurfaceVariant = colorResource(R.color.mi_light_on_surface_variant),
    logBackground = colorResource(R.color.mi_light_log_background),
    logText = colorResource(R.color.mi_light_log_text),
    logStage = colorResource(R.color.mi_light_log_stage),
    termCommand = colorResource(R.color.mi_light_term_command),
    termOutput = colorResource(R.color.mi_light_term_output),
    textDim = colorResource(R.color.mi_light_text_dim),
    statusOkBg = colorResource(R.color.mi_light_status_ok_bg),
    statusWarnBg = colorResource(R.color.mi_light_status_warn_bg),
    drawerSelected = colorResource(R.color.mi_light_drawer_selected),
    track = colorResource(R.color.mi_light_track),
    headerBackground = colorResource(R.color.mi_light_header_background),
    greenText = colorResource(R.color.mi_light_green_text),
    orangeText = colorResource(R.color.mi_light_orange_text),
    redText = colorResource(R.color.mi_light_red_text),
    warningText = colorResource(R.color.mi_light_warning_text),
    pillEdlBg = colorResource(R.color.mi_light_pill_edl_bg),
    pillEdlText = colorResource(R.color.mi_light_pill_edl_text),
    pillFastbootBg = colorResource(R.color.mi_light_pill_fastboot_bg),
    pillFastbootText = colorResource(R.color.mi_light_pill_fastboot_text),
    pillNoneBg = colorResource(R.color.mi_light_pill_none_bg),
    pillNoneText = colorResource(R.color.mi_light_pill_none_text),
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    secondary = palette.secondary,
    onSecondary = palette.onSecondary
)

@Composable
fun MiFlashTheme(
    dark: Boolean,
    palette: ThemePalette,
    content: @Composable () -> Unit
) {
    val colors = if (dark) darkColors(palette) else lightColors(palette)
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            error = colorResource(R.color.mi_red)
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            error = colorResource(R.color.mi_red)
        )
    }
    CompositionLocalProvider(LocalMiFlashColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

val MiFlashPrimary: Color
    @Composable get() = LocalMiFlashColors.current.primary
val MiFlashOnPrimary: Color
    @Composable get() = LocalMiFlashColors.current.onPrimary
val MiFlashSecondary: Color
    @Composable get() = LocalMiFlashColors.current.secondary
val MiFlashOnSurface: Color
    @Composable get() = LocalMiFlashColors.current.onSurface
val MiFlashBackground: Color
    @Composable get() = LocalMiFlashColors.current.background
val MiFlashSurface: Color
    @Composable get() = LocalMiFlashColors.current.surface
val MiFlashSurfaceVariant: Color
    @Composable get() = LocalMiFlashColors.current.surfaceVariant
val MiFlashBorder: Color
    @Composable get() = LocalMiFlashColors.current.border
val MiFlashOnSurfaceVariant: Color
    @Composable get() = LocalMiFlashColors.current.onSurfaceVariant
val MiFlashLogBackground: Color
    @Composable get() = LocalMiFlashColors.current.logBackground
val MiFlashLogText: Color
    @Composable get() = LocalMiFlashColors.current.logText
val MiFlashLogStage: Color
    @Composable get() = LocalMiFlashColors.current.logStage
val MiFlashTextSecondary: Color
    @Composable get() = LocalMiFlashColors.current.textSecondary
val MiFlashTextMuted: Color
    @Composable get() = LocalMiFlashColors.current.textMuted
val MiFlashTextDim: Color
    @Composable get() = LocalMiFlashColors.current.textDim
val MiFlashGreenText: Color
    @Composable get() = LocalMiFlashColors.current.greenText
val MiFlashOrangeText: Color
    @Composable get() = LocalMiFlashColors.current.orangeText
val MiFlashRedText: Color
    @Composable get() = LocalMiFlashColors.current.redText
val MiFlashAmber: Color
    @Composable get() = LocalMiFlashColors.current.warningText
val MiFlashStatusOkBg: Color
    @Composable get() = LocalMiFlashColors.current.statusOkBg
val MiFlashStatusWarnBg: Color
    @Composable get() = LocalMiFlashColors.current.statusWarnBg
val MiFlashHeaderBackground: Color
    @Composable get() = LocalMiFlashColors.current.headerBackground
val MiFlashDrawerSelected: Color
    @Composable get() = LocalMiFlashColors.current.drawerSelected
val MiFlashTrack: Color
    @Composable get() = LocalMiFlashColors.current.track
val MiFlashPillEdlBg: Color
    @Composable get() = LocalMiFlashColors.current.pillEdlBg
val MiFlashPillEdlText: Color
    @Composable get() = LocalMiFlashColors.current.pillEdlText
val MiFlashPillFastbootBg: Color
    @Composable get() = LocalMiFlashColors.current.pillFastbootBg
val MiFlashPillFastbootText: Color
    @Composable get() = LocalMiFlashColors.current.pillFastbootText
val MiFlashPillNoneBg: Color
    @Composable get() = LocalMiFlashColors.current.pillNoneBg
val MiFlashPillNoneText: Color
    @Composable get() = LocalMiFlashColors.current.pillNoneText
val MiFlashBlue: Color
    @Composable get() = colorResource(R.color.mi_blue)
val MiFlashRed: Color
    @Composable get() = colorResource(R.color.mi_red)
val MiFlashTermCommand: Color
    @Composable get() = LocalMiFlashColors.current.termCommand
val MiFlashTermOutput: Color
    @Composable get() = LocalMiFlashColors.current.termOutput

@Composable
fun sectionLabelStyle(): TextStyle = TextStyle(
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.5.sp,
    color = MiFlashTextSecondary
)
