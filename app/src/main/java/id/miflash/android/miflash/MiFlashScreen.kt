package id.miflash.android.miflash

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import id.miflash.android.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.common.DeviceState
import id.miflash.android.common.resolve
import id.miflash.android.miflash.service.formatDuration
import id.miflash.android.miflash.ui.ConnectionStatus
import id.miflash.android.miflash.ui.DeviceCard
import id.miflash.android.miflash.ui.FlashOptionItem
import id.miflash.android.miflash.ui.FlashOptions
import id.miflash.android.miflash.ui.FlashProgressScreen
import id.miflash.android.miflash.ui.LogConsole
import id.miflash.android.miflash.ui.StatusPill
import id.miflash.android.ui.components.MiFlashCard
import id.miflash.android.ui.components.MiFlashHeader
import id.miflash.android.ui.components.MiFlashPrimaryButton
import id.miflash.android.ui.components.MiFlashSecondaryButton
import id.miflash.android.ui.components.MiFlashSectionCard
import id.miflash.android.ui.theme.MiFlashAmber
import id.miflash.android.ui.theme.MiFlashBorder
import id.miflash.android.ui.theme.MiFlashGreenText
import id.miflash.android.ui.theme.MiFlashStatusOkBg
import id.miflash.android.ui.theme.MiFlashStatusWarnBg
import id.miflash.android.ui.theme.MiFlashSurface
import id.miflash.android.ui.theme.MiFlashTextDim
import id.miflash.android.ui.theme.MiFlashTrack
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashTextMuted
import id.miflash.android.ui.theme.MiFlashTextSecondary

@Composable
fun MiFlashScreen(viewModel: MiFlashViewModel, onMenuClick: () -> Unit) {
    val devices by viewModel.devices.collectAsState()
    val selected by viewModel.selectedDevice.collectAsState()
    val firmwareLabel by viewModel.firmwareLabel.collectAsState()
    val flashing by viewModel.flashing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val status by viewModel.status.collectAsState()
    val logLines by viewModel.logger.lines.collectAsState()
    val fastbootInfo by viewModel.fastbootInfo.collectAsState()
    val fastbootScripts by viewModel.fastbootScripts.collectAsState()
    val availableOptions by viewModel.availableOptions.collectAsState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val pendingLock by viewModel.pendingLock.collectAsState()
    val partitions by viewModel.partitions.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val elapsedMs by viewModel.elapsedMs.collectAsState()
    val cancelling by viewModel.cancelling.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.setFirmwareTree(uri)
    }

    val isFastboot = viewModel.isFastbootDevice
    val canFlash = selected?.hasPermission == true &&
        firmwareLabel != null &&
        !flashing &&
        if (isFastboot) selectedOption in availableOptions else selected?.isEdl == true && viewModel.signerProvided

    val selectedDevice = selected
    val connectionStatus = when {
        selectedDevice == null -> ConnectionStatus.DISCONNECTED
        selectedDevice.isEdl -> ConnectionStatus.EDL_CONNECTED
        isFastboot -> ConnectionStatus.FASTBOOT_CONNECTED
        else -> ConnectionStatus.DISCONNECTED
    }

    if (flashing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            FlashProgressScreen(
                progress = progress,
                currentStep = if (currentStep.isEmpty) status else currentStep,
                partitions = partitions,
                elapsed = formatDuration(elapsedMs),
                remaining = remainingText(elapsedMs, progress),
                logLines = logLines,
                onCancel = { viewModel.cancelFlash() },
                cancelling = cancelling
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiFlashHeader(title = "MiFlash", icon = "⚡", onMenuClick = onMenuClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusPill(status = connectionStatus)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (!isFastboot) {
            MiFlashCard(
                containerColor = if (viewModel.signerProvided) MiFlashStatusOkBg else MiFlashStatusWarnBg
            ) {
                Text(
                    if (viewModel.signerProvided) stringResource(R.string.signer_available) else stringResource(R.string.signer_missing),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (viewModel.signerProvided) MiFlashGreenText else MiFlashAmber
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isFastboot && fastbootInfo != null) {
            MiFlashCard(containerColor = MiFlashStatusOkBg) {
                Column {
                    Text(
                        stringResource(R.string.product, fastbootInfo?.product ?: "?"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiFlashGreenText
                    )
                    Text(
                        stringResource(R.string.fastboot_info, fastbootInfo?.version ?: "?", fastbootInfo?.serialno ?: "?"),
                        fontSize = 13.sp,
                        color = MiFlashTextDim
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (pendingLock) {
            MiFlashCard(containerColor = MiFlashStatusWarnBg) {
                Column {
                    Text(
                        stringResource(R.string.pending_lock_text),
                        fontSize = 14.sp,
                        color = MiFlashAmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MiFlashPrimaryButton(
                        text = stringResource(R.string.lock_bootloader),
                        onClick = { viewModel.lockBootloader() },
                        enabled = isFastboot && selected?.hasPermission == true && !flashing
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        MiFlashSectionCard(
            title = stringResource(R.string.device_usb),
            action = {
                MiFlashSecondaryButton(
                    text = stringResource(R.string.action_refresh),
                    onClick = { viewModel.refreshDevices() },
                    enabled = !flashing
                )
            }
        ) {
            Column {
                selected?.let { device ->
                    DeviceCard(
                        deviceName = device.discovered.usbDevice.productName ?: device.discovered.label,
                        detail = "Mode: ${device.discovered.mode}",
                        serial = device.discovered.usbDevice.serialNumber,
                        hasPermission = device.hasPermission,
                        containerColor = MiFlashSurface,
                        borderColor = MiFlashBorder
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (devices.isEmpty()) {
                    Text(
                        stringResource(R.string.no_device),
                        fontSize = 14.sp,
                        color = MiFlashTextSecondary
                    )
                } else {
                    devices.filter { it != selected }.forEach { device ->
                        DeviceRow(
                            device = device,
                            onClick = { viewModel.selectDevice(device) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        MiFlashSectionCard(
            title = stringResource(R.string.firmware),
            action = {
                MiFlashSecondaryButton(
                    text = stringResource(R.string.action_change),
                    onClick = { folderPicker.launch(null) },
                    enabled = !flashing
                )
            }
        ) {
            Text(
                firmwareLabel ?: stringResource(R.string.not_selected),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selected != null) {
            if (isFastboot && fastbootScripts.isEmpty()) {
                Text(
                    stringResource(R.string.no_script),
                    fontSize = 14.sp,
                    color = MiFlashAmber
                )
            } else {
                FlashOptions(
                    options = availableOptions.map { option ->
                        val scriptName = fastbootScripts.firstOrNull { it.option == option }?.fileName
                        FlashOptionItem(
                            id = option.name,
                            title = stringResource(option.labelRes),
                            description = flashOptionDescription(option) +
                                if (scriptName != null) " • $scriptName" else ""
                        )
                    },
                    selectedId = selectedOption.name,
                    onSelect = { id -> viewModel.selectOption(FlashOption.valueOf(id)) },
                    enabled = !flashing
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!status.isEmpty) {
            Text(status.resolve(), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (flashing || progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                color = MiFlashPrimary,
                trackColor = MiFlashTrack,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        MiFlashPrimaryButton(
            text = "⚡  " + stringResource(R.string.start_flash),
            onClick = { viewModel.flash() },
            enabled = canFlash || (!flashing && selected != null)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LogConsole(lines = logLines)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.do_not_unplug),
            fontSize = 12.sp,
            color = MiFlashTextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        }
    }
}

private fun remainingText(elapsedMs: Long, progress: Float): String {
    if (progress <= 0.02f) return "--:--"
    val totalMs = (elapsedMs / progress).toLong()
    return formatDuration((totalMs - elapsedMs).coerceAtLeast(0))
}

@Composable
private fun flashOptionDescription(option: FlashOption): String {
    return when (option) {
        FlashOption.CLEAN_ALL -> stringResource(R.string.flash_option_clean_all_desc)
        FlashOption.SAVE_USERDATA -> stringResource(R.string.flash_option_save_desc)
        FlashOption.CLEAN_ALL_AND_LOCK -> stringResource(R.string.flash_option_lock_desc)
    }
}

@Composable
private fun DeviceRow(device: DeviceState, onClick: () -> Unit) {
    MiFlashCard(
        modifier = Modifier.padding(vertical = 4.dp),
        borderColor = MiFlashBorder,
        onClick = onClick
    ) {
        Column {
            Text(device.discovered.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.mode_permission,
                    device.discovered.mode,
                    if (device.hasPermission) stringResource(R.string.permission_granted) else stringResource(R.string.permission_missing)
                ),
                fontSize = 13.sp,
                color = MiFlashTextSecondary
            )
        }
    }
}
