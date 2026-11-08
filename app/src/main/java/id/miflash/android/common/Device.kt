package id.miflash.android.common

import id.miflash.android.common.usb.DeviceMode
import id.miflash.android.common.usb.DiscoveredDevice

data class DeviceState(
    val discovered: DiscoveredDevice,
    val hasPermission: Boolean = false
) {
    val isEdl: Boolean
        get() = discovered.mode == DeviceMode.EDL_9008
}
