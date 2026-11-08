package id.miflash.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.ui.components.MiFlashCard
import id.miflash.android.ui.components.MiFlashHeader
import id.miflash.android.ui.theme.MiFlashTextSecondary

@Composable
fun HomeScreen(onSelect: (AppScreen) -> Unit, onMenuClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        MiFlashHeader(title = "MiTool", icon = "⚡", onMenuClick = onMenuClick)
        Column(modifier = Modifier.padding(16.dp)) {
            HomeCard(
                icon = "⚡",
                title = "MiFlash",
                subtitle = stringResource(R.string.home_miflash_subtitle),
                onClick = { onSelect(AppScreen.MIFLASH) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeCard(
                icon = "🔓",
                title = "MiUnlock",
                subtitle = stringResource(R.string.home_miunlock_subtitle),
                onClick = { onSelect(AppScreen.MIUNLOCK) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeCard(
                icon = "⌨️",
                title = "Fastboot",
                subtitle = stringResource(R.string.home_fastboot_subtitle),
                onClick = { onSelect(AppScreen.FASTBOOT_TERMINAL) }
            )
        }
    }
}

@Composable
private fun HomeCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    MiFlashCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.size(16.dp))
            Column {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = MiFlashTextSecondary)
            }
        }
    }
}
