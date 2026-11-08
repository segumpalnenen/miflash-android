package id.miflash.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.R
import id.miflash.android.ui.AppScreen
import id.miflash.android.ui.theme.MiFlashBorder
import id.miflash.android.ui.theme.MiFlashDrawerSelected
import id.miflash.android.ui.theme.MiFlashOnSurface
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashSurface
import id.miflash.android.ui.theme.MiFlashSurfaceVariant

@Composable
fun AppSidebar(
    selected: AppScreen,
    onSelect: (AppScreen) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        drawerContainerColor = MiFlashSurface,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MiFlashPrimary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text("MiTool", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(MiFlashSurfaceVariant, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MiFlashOnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            NavigationDrawerItem(
                label = { Text("MiFlash", fontSize = 16.sp) },
                icon = { Text("⚡", fontSize = 18.sp) },
                selected = selected == AppScreen.MIFLASH,
                onClick = { onSelect(AppScreen.MIFLASH) },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MiFlashDrawerSelected
                )
            )
            NavigationDrawerItem(
                label = { Text("MiUnlock", fontSize = 16.sp) },
                icon = { Text("🔓", fontSize = 18.sp) },
                selected = selected == AppScreen.MIUNLOCK,
                onClick = { onSelect(AppScreen.MIUNLOCK) },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MiFlashDrawerSelected
                )
            )
            NavigationDrawerItem(
                label = { Text("Fastboot", fontSize = 16.sp) },
                icon = { Text("⌨️", fontSize = 18.sp) },
                selected = selected == AppScreen.FASTBOOT_TERMINAL,
                onClick = { onSelect(AppScreen.FASTBOOT_TERMINAL) },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MiFlashDrawerSelected
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(color = MiFlashBorder, modifier = Modifier.padding(horizontal = 12.dp))
        Column(modifier = Modifier.padding(12.dp)) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings_title), fontSize = 16.sp) },
                icon = { Text("⚙️", fontSize = 18.sp) },
                selected = selected == AppScreen.SETTINGS,
                onClick = { onSelect(AppScreen.SETTINGS) },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MiFlashDrawerSelected
                )
            )
        }
    }
}
