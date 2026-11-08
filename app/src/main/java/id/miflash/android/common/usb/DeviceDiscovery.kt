package id.miflash.android.common.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

enum class DeviceMode {
    EDL_9008,
    FASTBOOT,
    UNKNOWN
}

data class DiscoveredDevice(
    val usbDevice: UsbDevice,
    val mode: DeviceMode
) {
    val label: String
        get() = "${usbDevice.productName ?: "Unknown"} (${usbDevice.vendorId.toString(16)}:${usbDevice.productId.toString(16)})"
}

object DeviceDiscovery {
    fun listDevices(usbManager: UsbManager): List<DiscoveredDevice> {
        return usbManager.deviceList.values.map { device ->
            DiscoveredDevice(device, classify(device))
        }
    }

    fun classify(device: UsbDevice): DeviceMode {
        return when {
            device.vendorId == 0x05C6 && device.productId == 0x9008 -> DeviceMode.EDL_9008
            device.vendorId == 0x18D1 -> DeviceMode.FASTBOOT
            device.vendorId == 0x2717 -> DeviceMode.FASTBOOT
            else -> DeviceMode.UNKNOWN
        }
    }
}
