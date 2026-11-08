package id.miflash.android.miflash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.ui.components.MiFlashCard
import id.miflash.android.ui.theme.MiFlashBorder
import id.miflash.android.ui.theme.MiFlashGreenText
import id.miflash.android.ui.theme.MiFlashOnPrimary
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashRedText
import id.miflash.android.ui.theme.MiFlashSurfaceVariant
import id.miflash.android.ui.theme.MiFlashTextDim
import id.miflash.android.ui.theme.MiFlashTextSecondary
import id.miflash.android.ui.theme.sectionLabelStyle

@Composable
fun DeviceCard(
    deviceName: String,
    detail: String,
    serial: String?,
    hasPermission: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MiFlashSurfaceVariant,
    borderColor: Color? = null
) {
    MiFlashCard(modifier = modifier, containerColor = containerColor, borderColor = borderColor) {
        Column {
            Text(stringResource(R.string.connected_device), style = sectionLabelStyle())
            Spacer(modifier = Modifier.height(10.dp))
            Text(deviceName, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail, fontSize = 14.sp, color = MiFlashGreenText, fontWeight = FontWeight.Medium)
            if (serial != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(stringResource(R.string.serial, serial), fontSize = 13.sp, color = MiFlashTextDim)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasPermission) "✓  " + stringResource(R.string.usb_permission_granted) else stringResource(R.string.usb_permission_missing),
                fontSize = 13.sp,
                color = if (hasPermission) MiFlashGreenText else MiFlashRedText
            )
        }
    }
}

@Composable
fun ModeSwitch(
    isEdlSelected: Boolean,
    onSelectEdl: () -> Unit,
    onSelectFastboot: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.mode_label), style = sectionLabelStyle())
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ModeButton(
                label = "⚡  EDL",
                selected = isEdlSelected,
                enabled = enabled,
                onClick = onSelectEdl,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                label = "Fastboot",
                selected = !isEdlSelected,
                enabled = enabled,
                onClick = onSelectFastboot,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) MiFlashPrimary else MiFlashSurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) MiFlashPrimary else MiFlashBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MiFlashOnPrimary else MiFlashTextSecondary
        )
    }
}
