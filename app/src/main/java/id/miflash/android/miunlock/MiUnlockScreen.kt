package id.miflash.android.miunlock

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import id.miflash.android.common.resolve
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.miunlock.auth.MiAccountCredentials
import id.miflash.android.ui.components.MiFlashCard
import id.miflash.android.ui.components.MiFlashHeader
import id.miflash.android.ui.components.MiFlashPrimaryButton
import id.miflash.android.ui.components.MiFlashSecondaryButton
import id.miflash.android.ui.theme.sectionLabelStyle

@Composable
fun MiUnlockScreen(viewModel: MiUnlockViewModel, onMenuClick: () -> Unit) {
    val loginState by viewModel.loginState.collectAsState()
    val loginStatus by viewModel.loginStatus.collectAsState()
    val unlockState by viewModel.unlockState.collectAsState()
    val unlockStatus by viewModel.unlockStatus.collectAsState()
    val unlockProgress by viewModel.unlockProgress.collectAsState()
    val unlockProduct by viewModel.unlockProduct.collectAsState()
    val unlockToken by viewModel.unlockToken.collectAsState()
    val unlockNotice by viewModel.unlockNotice.collectAsState()
    val unlockWipesData by viewModel.unlockWipesData.collectAsState()
    val loggedIn = loginState is MiLoginState.LoggedIn
    val context = LocalContext.current
    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val passToken = data?.getStringExtra(MiLoginActivity.EXTRA_PASS_TOKEN)
            val deviceId = data?.getStringExtra(MiLoginActivity.EXTRA_DEVICE_ID)
            val userId = data?.getStringExtra(MiLoginActivity.EXTRA_USER_ID)
            if (!passToken.isNullOrEmpty() && !deviceId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                viewModel.completeLogin(MiAccountCredentials(deviceId, passToken, userId))
            } else {
                viewModel.onLoginCancelled()
            }
        } else {
            viewModel.onLoginCancelled()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MiFlashHeader(title = "MiUnlock", icon = "🔓", onMenuClick = onMenuClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
        MiFlashCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mi_account), style = sectionLabelStyle())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(loginStatus.resolve(), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                if (loginState is MiLoginState.LoggedIn) {
                    MiFlashSecondaryButton(text = stringResource(R.string.logout), onClick = { viewModel.logout() })
                } else {
                    MiFlashSecondaryButton(
                        text = stringResource(R.string.login),
                        onClick = {
                            viewModel.beginLogin()
                            loginLauncher.launch(Intent(context, MiLoginActivity::class.java))
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.unlock_section), style = sectionLabelStyle())
        Spacer(modifier = Modifier.height(8.dp))
        MiFlashCard {
            Column {
                if (!loggedIn) {
                    Text(stringResource(R.string.login_first), fontSize = 14.sp)
                } else {
                    Text(stringResource(R.string.device_label, unlockProduct ?: stringResource(R.string.device_not_connected)), fontSize = 14.sp)
                    if (unlockToken != null) {
                        Text(stringResource(R.string.token_label, shortToken(unlockToken!!)), fontSize = 14.sp)
                    }
                    if (unlockNotice.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(unlockNotice, fontSize = 14.sp)
                        if (unlockWipesData) {
                            Text(stringResource(R.string.unlock_wipes_data), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(unlockStatus.resolve(), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (unlockState is MiUnlockState.CheckingDevice || unlockState is MiUnlockState.FetchingClear || unlockState is MiUnlockState.Unlocking) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = unlockProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    when (unlockState) {
                        is MiUnlockState.Idle, is MiUnlockState.Failed, is MiUnlockState.Done -> {
                            MiFlashSecondaryButton(text = stringResource(R.string.check_device), onClick = { viewModel.checkDevice() })
                        }
                        is MiUnlockState.DeviceReady -> {
                            MiFlashSecondaryButton(text = stringResource(R.string.continue_unlock), onClick = { viewModel.continueUnlock() })
                        }
                        is MiUnlockState.AwaitingConfirm -> {
                            MiFlashPrimaryButton(text = stringResource(R.string.unlock_btn), onClick = { viewModel.confirmUnlock() })
                        }
                        else -> {
                            MiFlashSecondaryButton(text = stringResource(R.string.processing), onClick = {}, enabled = false)
                        }
                    }
                }
            }
        }
    }
}
}

private fun shortToken(token: String): String {
    return if (token.length <= 12) token else token.take(8) + "…" + token.takeLast(4)
}
