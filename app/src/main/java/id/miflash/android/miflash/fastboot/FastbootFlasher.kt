package id.miflash.android.miflash.fastboot

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import id.miflash.android.R
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.UiText
import id.miflash.android.common.LogKind
import id.miflash.android.miflash.service.FlashProgress
import id.miflash.android.common.usb.DeviceDiscovery
import id.miflash.android.common.usb.DeviceMode

class FastbootFlasher(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val logger: FlashLogger? = null
) {

    companion object {
        const val RECONNECT_TIMEOUT_MS = 90_000L
        const val RECONNECT_POLL_MS = 1000L
    }

    fun interface ProgressListener {
        fun onProgress(fraction: Float, message: UiText)
    }

    data class DeviceInfo(
        val product: String?,
        val version: String?,
        val serialno: String?,
        val maxDownloadSize: Long?
    )

    fun queryInfo(): DeviceInfo {
        val transport = FastbootTransport(usbManager, device)
        if (!transport.open()) throw FastbootException("Tidak bisa membuka koneksi USB ke device")
        val session = FastbootSession(transport, logger)
        try {
            val product = session.getvar("product")
            val version = session.getvar("version")
            val serialno = session.getvar("serialno")
            val maxDownloadSize = runCatching { session.maxDownloadSize() }.getOrNull()
            return DeviceInfo(product, version, serialno, maxDownloadSize)
        } finally {
            transport.close()
        }
    }

    fun flash(rom: FastbootRomSource, steps: List<FastbootStep>, listener: ProgressListener? = null) {
        var transport = FastbootTransport(usbManager, device)
        if (!transport.open()) throw FastbootException("Tidak bisa membuka koneksi USB ke device")
        var session = FastbootSession(transport, logger)
        try {
            val product = session.getvar("product")
            logger?.log(R.string.log_fastboot_device, product ?: "?")
            val flashSteps = steps.filterIsInstance<FastbootStep.Flash>()
            FlashProgress.setPartitions(flashSteps.map { it.partition })
            var flashIndex = 0
            val totalBytes = totalFlashBytes(rom, steps)
            var sentBytes = 0L
            for (step in steps) {
                FlashProgress.checkCancelled()
                when (step) {
                    is FastbootStep.Flash -> {
                        val myIndex = flashIndex++
                        FlashProgress.markPartitionStarted(myIndex)
                        listener?.onProgress(fraction(sentBytes, totalBytes), UiText.en(R.string.progress_flash_partition, step.partition))
                        logger?.logRaw("Flash ${step.partition} <- ${step.filename}", LogKind.COMMAND)
                        val sizeBefore = sentBytes
                        rom.openImage(step.filename).use { image ->
                            session.flash(step.partition, image) { sent, total ->
                                FlashProgress.checkCancelled()
                                listener?.onProgress(fraction(sizeBefore + sent, totalBytes), UiText.en(R.string.progress_flash_partition, step.partition))
                            }
                            sentBytes += image.size
                        }
                        listener?.onProgress(fraction(sentBytes, totalBytes), UiText.en(R.string.progress_flash_partition_done, step.partition))
                        FlashProgress.markPartitionDone(myIndex)
                    }
                    is FastbootStep.Erase -> {
                        logger?.logRaw("Erase ${step.partition}", LogKind.COMMAND)
                        session.erase(step.partition)
                    }
                    is FastbootStep.GetVar -> {
                        val value = session.getvar(step.name)
                            ?: throw FastbootException("Device tidak menjawab getvar:${step.name}")
                        logger?.logRaw("getvar ${step.name}: $value", LogKind.COMMAND)
                    }
                    is FastbootStep.Oem -> {
                        logger?.logRaw("oem ${step.args}", LogKind.COMMAND)
                        session.oem(step.args)
                    }
                    is FastbootStep.FlashingLock -> {
                        if (step.lock) {
                            logger?.logRaw("flashing lock", LogKind.COMMAND)
                            session.flashingLock()
                        } else {
                            logger?.logRaw("flashing unlock", LogKind.COMMAND)
                            session.flashingUnlock()
                        }
                    }
                    is FastbootStep.SetActive -> {
                        logger?.logRaw("set_active ${step.slot}", LogKind.COMMAND)
                        session.setActive(step.slot)
                    }
                    is FastbootStep.Sleep -> {
                        logger?.log(R.string.log_wait_seconds, step.seconds)
                        Thread.sleep(step.seconds * 1000L)
                    }
                    is FastbootStep.Reboot -> {
                        logger?.logRaw("Reboot ${step.target ?: ""}", LogKind.COMMAND)
                        session.reboot(step.target)
                        if (step.target == "bootloader" || step.target == "fastboot") {
                            val reopened = reconnect(session, transport)
                            session = reopened.first
                            transport = reopened.second
                        } else {
                            break
                        }
                    }
                }
            }
            listener?.onProgress(1f, UiText.en(R.string.flash_done))
        } finally {
            transport.close()
        }
    }

    private fun fraction(sent: Long, total: Long): Float {
        if (total <= 0) return 0f
        return (sent.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    private fun totalFlashBytes(rom: FastbootRomSource, steps: List<FastbootStep>): Long {
        var total = 0L
        for (step in steps) {
            if (step !is FastbootStep.Flash) continue
            total += runCatching {
                rom.openImage(step.filename).use { it.size }
            }.getOrDefault(0L)
        }
        return total
    }

    private fun reconnect(
        oldSession: FastbootSession,
        oldTransport: FastbootTransport
    ): Pair<FastbootSession, FastbootTransport> {
        oldSession.close()
        oldTransport.close()
        logger?.log(R.string.log_wait_reboot)
        val deadline = System.currentTimeMillis() + RECONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            FlashProgress.checkCancelled()
            Thread.sleep(RECONNECT_POLL_MS)
            val found = usbManager.deviceList.values.firstOrNull {
                DeviceDiscovery.classify(it) == DeviceMode.FASTBOOT
            } ?: continue
            if (!usbManager.hasPermission(found)) {
                throw FastbootException(
                    "Device kembali setelah reboot tapi izin USB belum diberikan — " +
                        "beri izin USB lalu ulangi flash dari awal"
                )
            }
            val transport = FastbootTransport(usbManager, found)
            if (!transport.open()) continue
            val session = FastbootSession(transport, logger)
            val version = runCatching { session.getvar("version") }.getOrNull()
            if (version == null) {
                transport.close()
                continue
            }
            logger?.log(R.string.log_device_back, version, kind = LogKind.SUCCESS)
            return session to transport
        }
        throw FastbootException("Device tidak kembali setelah reboot fastboot (timeout 90 detik)")
    }
}
