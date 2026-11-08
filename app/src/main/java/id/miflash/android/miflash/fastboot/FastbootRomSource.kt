package id.miflash.android.miflash.fastboot

import android.content.Context
import android.net.Uri
import id.miflash.android.common.FlashLogger
import id.miflash.android.miflash.FlashOption
import id.miflash.android.miflash.edl.FirmwareSource
import id.miflash.android.miflash.edl.RomFile

data class FastbootScriptOption(
    val option: FlashOption,
    val fileName: String
) {
    val labelRes: Int get() = option.labelRes
}

class FastbootRomSource(
    val firmware: FirmwareSource
) {

    companion object {
        fun open(context: Context, treeUri: Uri, logger: FlashLogger): FastbootRomSource {
            return FastbootRomSource(FirmwareSource.open(context, treeUri, logger))
        }

        private fun preferSh(names: List<String>, stems: List<String>): String? {
            for (stem in stems) {
                names.firstOrNull { it.equals("$stem.sh", ignoreCase = true) }?.let { return it }
                names.firstOrNull { it.equals("$stem.bat", ignoreCase = true) }?.let { return it }
            }
            return null
        }
    }

    fun availableScripts(): List<FastbootScriptOption> {
        val names = firmware.listNames()
        val options = mutableListOf<FastbootScriptOption>()
        preferSh(names, listOf("flash_all"))?.let {
            options.add(FastbootScriptOption(FlashOption.CLEAN_ALL, it))
        }
        preferSh(
            names,
            listOf("flash_all_except_storage", "flash_all_except_data_storage")
        )?.let {
            options.add(FastbootScriptOption(FlashOption.SAVE_USERDATA, it))
        } ?: run {
            val other = names.firstOrNull {
                it.lowercase().startsWith("flash_all_except_") &&
                    (it.lowercase().endsWith(".sh") || it.lowercase().endsWith(".bat"))
            }
            if (other != null) {
                options.add(FastbootScriptOption(FlashOption.SAVE_USERDATA, other))
            }
        }
        preferSh(names, listOf("flash_all_lock"))?.let {
            options.add(FastbootScriptOption(FlashOption.CLEAN_ALL_AND_LOCK, it))
        }
        return options
    }

    fun scriptFor(option: FlashOption): FastbootScriptOption? {
        return availableScripts().firstOrNull { it.option == option }
    }

    fun readScript(fileName: String): String {
        return firmware.readText(fileName)
    }

    fun openImage(name: String): RomFile {
        return firmware.open(name)
    }

    fun contains(name: String): Boolean = firmware.contains(name)
}
