package id.miflash.android.miflash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.common.LogLine
import id.miflash.android.common.UiText
import id.miflash.android.common.resolve
import id.miflash.android.miflash.service.PartitionProgress
import id.miflash.android.miflash.service.PartitionState
import id.miflash.android.miflash.service.formatDuration
import id.miflash.android.ui.components.MiFlashPrimaryButton
import id.miflash.android.ui.theme.MiFlashAmber
import id.miflash.android.ui.theme.MiFlashGreenText
import id.miflash.android.ui.theme.MiFlashStatusWarnBg
import id.miflash.android.ui.theme.MiFlashTextDim
import id.miflash.android.ui.theme.MiFlashTrack
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashOrangeText
import id.miflash.android.ui.theme.MiFlashRed
import id.miflash.android.ui.theme.MiFlashTextMuted
import id.miflash.android.ui.theme.MiFlashTextSecondary
import id.miflash.android.ui.theme.sectionLabelStyle

@Composable
fun FlashProgressScreen(
    progress: Float,
    currentStep: UiText,
    partitions: List<PartitionProgress>,
    elapsed: String,
    remaining: String,
    logLines: List<LogLine>,
    onCancel: () -> Unit,
    cancelling: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiFlashStatusWarnBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.progress_warning),
                color = MiFlashAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(180.dp),
                color = MiFlashPrimary,
                strokeWidth = 10.dp,
                trackColor = MiFlashTrack
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.progress_flashing), fontSize = 14.sp, color = MiFlashTextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = currentStep.resolve(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (partitions.isNotEmpty()) {
        Text(stringResource(R.string.progress_partitions), style = sectionLabelStyle())
        Spacer(modifier = Modifier.height(8.dp))
        partitions.forEach { partition ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                when (partition.state) {
                    PartitionState.DONE -> Text(
                        "✓",
                        color = MiFlashGreenText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    PartitionState.IN_PROGRESS -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MiFlashPrimary,
                        strokeWidth = 2.dp
                    )
                    PartitionState.PENDING -> Text(
                        "○",
                        color = MiFlashTextMuted,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(partition.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(
                    text = when (partition.state) {
                        PartitionState.DONE -> stringResource(R.string.progress_completed) +
                            if (partition.durationMs != null) " • ${formatDuration(partition.durationMs)}" else ""
                        PartitionState.IN_PROGRESS -> stringResource(R.string.progress_in_progress, (progress * 100).toInt())
                        PartitionState.PENDING -> stringResource(R.string.progress_pending)
                    },
                    fontSize = 13.sp,
                    color = when (partition.state) {
                        PartitionState.DONE -> MiFlashGreenText
                        PartitionState.IN_PROGRESS -> MiFlashOrangeText
                        PartitionState.PENDING -> MiFlashTextSecondary
                    }
                )
            }
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.progress_elapsed, elapsed, remaining),
            fontSize = 13.sp,
            color = MiFlashTextDim
        )
        Spacer(modifier = Modifier.height(16.dp))
        LogConsole(lines = logLines)
        Spacer(modifier = Modifier.height(16.dp))
        MiFlashPrimaryButton(
            text = if (cancelling) stringResource(R.string.progress_cancelling) else "✕  " + stringResource(R.string.progress_cancel),
            onClick = onCancel,
            enabled = !cancelling,
            containerColor = MiFlashRed
        )
    }
}
