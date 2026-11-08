package id.miflash.android.miflash.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import id.miflash.android.ui.theme.MiFlashPrimary
import id.miflash.android.ui.theme.MiFlashSurfaceVariant
import id.miflash.android.ui.theme.MiFlashTextDim
import id.miflash.android.ui.theme.MiFlashTextMuted
import id.miflash.android.ui.theme.sectionLabelStyle

data class FlashOptionItem(
    val id: String,
    val title: String,
    val description: String
)

@Composable
fun FlashOptions(
    options: List<FlashOptionItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.flash_options), style = sectionLabelStyle())
        Spacer(modifier = Modifier.height(8.dp))
        options.forEach { option ->
            val selected = option.id == selectedId
            Card(
                colors = CardDefaults.cardColors(containerColor = MiFlashSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MiFlashPrimary else Color.Transparent,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = enabled) { onSelect(option.id) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MiFlashPrimary,
                            unselectedColor = MiFlashTextMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(option.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            option.description,
                            fontSize = 13.sp,
                            color = MiFlashTextDim
                        )
                    }
                }
            }
        }
    }
}

val defaultFlashOptions = listOf(
    FlashOptionItem(
        id = "clean_all",
        title = "Clean all",
        description = "Hapus semua data, flash seluruh ROM. Disarankan untuk instal bersih."
    ),
    FlashOptionItem(
        id = "save_userdata",
        title = "Save userdata",
        description = "Data dan aplikasi tetap. Hanya sistem yang di-flash."
    ),
    FlashOptionItem(
        id = "clean_all_and_lock",
        title = "Clean all and lock",
        description = "Hapus semua data, kunci bootloader setelah flash."
    )
)
