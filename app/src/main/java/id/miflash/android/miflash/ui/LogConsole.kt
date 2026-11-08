package id.miflash.android.miflash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.common.LogKind
import id.miflash.android.common.LogLine
import id.miflash.android.common.resolve
import id.miflash.android.ui.theme.MiFlashAmber
import id.miflash.android.ui.theme.MiFlashGreenText
import id.miflash.android.ui.theme.MiFlashLogBackground
import id.miflash.android.ui.theme.MiFlashLogStage
import id.miflash.android.ui.theme.MiFlashLogText
import id.miflash.android.ui.theme.MiFlashRedText
import id.miflash.android.ui.theme.MiFlashTermCommand
import id.miflash.android.ui.theme.sectionLabelStyle

@Composable
fun LogConsole(
    lines: List<LogLine>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.log_console), style = sectionLabelStyle())
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MiFlashLogBackground),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(12.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = "[${line.time}] ${line.text.resolve()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when (line.kind) {
                            LogKind.INFO -> MiFlashLogText
                            LogKind.STAGE -> MiFlashLogStage
                            LogKind.SUCCESS -> MiFlashGreenText
                            LogKind.WARNING -> MiFlashAmber
                            LogKind.ERROR -> MiFlashRedText
                            LogKind.COMMAND -> MiFlashTermCommand
                        },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
