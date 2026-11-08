package id.miflash.android.miflash.terminal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import id.miflash.android.common.UiText
import id.miflash.android.common.resolve
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.ui.components.MiFlashHeader
import id.miflash.android.ui.components.MiFlashPrimaryButton
import id.miflash.android.ui.components.MiFlashSecondaryButton
import id.miflash.android.ui.theme.MiFlashGreenText
import id.miflash.android.ui.theme.MiFlashLogBackground
import id.miflash.android.ui.theme.MiFlashOnSurface
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashOrangeText
import id.miflash.android.ui.theme.MiFlashRedText
import id.miflash.android.ui.theme.MiFlashSurfaceVariant
import id.miflash.android.ui.theme.MiFlashTermCommand
import id.miflash.android.ui.theme.MiFlashTermOutput
import id.miflash.android.ui.theme.MiFlashTextSecondary

@Composable
fun FastbootTerminalScreen(viewModel: FastbootTerminalViewModel, onMenuClick: () -> Unit) {
    val lines by viewModel.lines.collectAsState()
    val attachedFiles by viewModel.attachedFiles.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val deviceLabel by viewModel.deviceLabel.collectAsState()
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachFile(uri)
    }

    LaunchedEffect(lines.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    fun send() {
        if (input.isBlank()) return
        viewModel.execute(input)
        if (!busy) input = ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiFlashHeader(title = "Fastboot", icon = "⌨️", onMenuClick = onMenuClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(deviceLabel.resolve(), fontSize = 14.sp, color = MiFlashTextSecondary)
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                stringResource(R.string.terminal_refresh),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiFlashOrangeText,
                modifier = Modifier.clickable { viewModel.refreshDevice() }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MiFlashLogBackground),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        line.text.resolve(),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (line.kind == TerminalKind.COMMAND) FontWeight.Bold else FontWeight.Normal,
                        color = when (line.kind) {
                            TerminalKind.COMMAND -> MiFlashTermCommand
                            TerminalKind.OUTPUT -> MiFlashTermOutput
                            TerminalKind.OK -> MiFlashGreenText
                            TerminalKind.ERROR -> MiFlashRedText
                            TerminalKind.INFO -> MiFlashTextSecondary
                        }
                    )
                }
            }
        }
        if (attachedFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                attachedFiles.forEach { file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MiFlashSurfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("📎 ${file.name}", fontSize = 13.sp, color = MiFlashOnSurface)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "✕",
                                fontSize = 13.sp,
                                color = MiFlashOnSurface,
                                modifier = Modifier.clickable { viewModel.detachFile(file.name) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(UiText.en(R.string.terminal_placeholder).resolve(), fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MiFlashOnSurface,
                    unfocusedTextColor = MiFlashOnSurface,
                    focusedContainerColor = MiFlashSurfaceVariant,
                    unfocusedContainerColor = MiFlashSurfaceVariant,
                    focusedPlaceholderColor = MiFlashTextSecondary,
                    unfocusedPlaceholderColor = MiFlashTextSecondary,
                    cursorColor = MiFlashPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            MiFlashSecondaryButton(text = stringResource(R.string.action_add_file), onClick = { filePicker.launch(arrayOf("*/*")) }, compact = true)
            Spacer(modifier = Modifier.width(8.dp))
            MiFlashPrimaryButton(text = stringResource(R.string.action_enter), onClick = { send() }, compact = true)
        }
        }
    }
}
