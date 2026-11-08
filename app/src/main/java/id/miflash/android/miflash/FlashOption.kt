package id.miflash.android.miflash

import androidx.annotation.StringRes
import id.miflash.android.R

enum class FlashOption(@StringRes val labelRes: Int) {
    CLEAN_ALL(R.string.option_clean_all),
    SAVE_USERDATA(R.string.option_save_userdata),
    CLEAN_ALL_AND_LOCK(R.string.option_clean_all_lock)
}
