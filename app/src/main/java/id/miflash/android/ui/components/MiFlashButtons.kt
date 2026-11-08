package id.miflash.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.miflash.android.ui.theme.MiFlashOnPrimary
import id.miflash.android.ui.theme.MiFlashOnSurface
import id.miflash.android.ui.theme.MiFlashOnSurfaceVariant
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashSurface

@Composable
fun MiFlashPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MiFlashPrimary,
    compact: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 20.dp else 16.dp),
        modifier = modifier.then(
            if (compact) Modifier.height(48.dp)
            else Modifier.fillMaxWidth().height(56.dp)
        )
    ) {
        Text(
            text,
            fontSize = if (compact) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiFlashOnPrimary
        )
    }
}

@Composable
fun MiFlashSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MiFlashOnSurfaceVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MiFlashSurface,
            contentColor = MiFlashOnSurface
        ),
        contentPadding = PaddingValues(
            horizontal = if (compact) 12.dp else 16.dp,
            vertical = 10.dp
        ),
        modifier = modifier.then(if (compact) Modifier.height(48.dp) else Modifier)
    ) {
        Text(
            text,
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
