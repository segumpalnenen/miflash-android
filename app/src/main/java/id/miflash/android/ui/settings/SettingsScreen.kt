package id.miflash.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.R
import id.miflash.android.ui.components.MiFlashHeader
import id.miflash.android.ui.components.MiFlashSectionCard
import id.miflash.android.ui.theme.MiFlashBorder
import id.miflash.android.ui.theme.MiFlashOnPrimary
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashTextMuted
import id.miflash.android.ui.theme.MiFlashTextSecondary
import id.miflash.android.ui.theme.ThemeMode
import id.miflash.android.ui.theme.ThemePalette

@Composable
fun SettingsScreen(
    settings: AppSettings,
    palettes: List<ThemePalette>,
    onThemeMode: (ThemeMode) -> Unit,
    onPalette: (String) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        MiFlashHeader(
            title = stringResource(R.string.settings_title),
            icon = "⚙️",
            onMenuClick = onMenuClick
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MiFlashSectionCard(title = stringResource(R.string.settings_theme)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeModeOption(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onThemeMode(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeOption(
                        label = stringResource(R.string.settings_theme_light),
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeModeOption(
                        label = stringResource(R.string.settings_theme_system),
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeMode(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            MiFlashSectionCard(title = stringResource(R.string.settings_color)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(palettes) { palette ->
                        ColorSwatch(
                            palette = palette,
                            selected = palette.key == settings.paletteKey,
                            onClick = { onPalette(palette.key) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            MiFlashSectionCard(title = stringResource(R.string.settings_language)) {
                Column {
                    AppLanguage.entries.forEachIndexed { index, language ->
                        if (index > 0) HorizontalDivider(color = MiFlashBorder)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguage(language) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                language.nativeName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                language.code.uppercase(),
                                fontSize = 13.sp,
                                color = MiFlashTextMuted
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            if (language == settings.language) {
                                Text(
                                    "✓",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiFlashPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MiFlashPrimary else MiFlashBorder.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MiFlashOnPrimary else MiFlashTextSecondary
        )
    }
}

@Composable
private fun ColorSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(palette.primary)
            .then(
                if (selected) Modifier.border(2.dp, MiFlashTextSecondary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Text(
                "✓",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onPrimary
            )
        }
    }
}
