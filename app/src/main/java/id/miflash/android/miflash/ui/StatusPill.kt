package id.miflash.android.miflash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.ui.theme.MiFlashPillEdlBg
import id.miflash.android.ui.theme.MiFlashPillEdlText
import id.miflash.android.ui.theme.MiFlashPillFastbootBg
import id.miflash.android.ui.theme.MiFlashPillFastbootText
import id.miflash.android.ui.theme.MiFlashPillNoneBg
import id.miflash.android.ui.theme.MiFlashPillNoneText

enum class ConnectionStatus {
    EDL_CONNECTED,
    FASTBOOT_CONNECTED,
    DISCONNECTED
}

@Composable
fun StatusPill(status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (background, text, label) = when (status) {
        ConnectionStatus.EDL_CONNECTED ->
            Triple(MiFlashPillEdlBg, MiFlashPillEdlText, "●  " + stringResource(R.string.pill_edl))
        ConnectionStatus.FASTBOOT_CONNECTED ->
            Triple(MiFlashPillFastbootBg, MiFlashPillFastbootText, "●  " + stringResource(R.string.pill_fastboot))
        ConnectionStatus.DISCONNECTED ->
            Triple(MiFlashPillNoneBg, MiFlashPillNoneText, "○  " + stringResource(R.string.pill_none))
    }
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
