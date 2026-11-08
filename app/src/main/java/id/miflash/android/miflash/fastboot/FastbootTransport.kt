package id.miflash.android.miflash.fastboot

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import id.miflash.android.common.usb.UsbTransport

class FastbootTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) : UsbTransport {

    companion object {
        const val INTERFACE_CLASS = 255
        const val INTERFACE_SUBCLASS = 66
        const val INTERFACE_PROTOCOL = 3
        const val CHUNK_SIZE = 16384
    }

    private var connection: UsbDeviceConnection? = null
    private var bulkIn: UsbEndpoint? = null
    private var bulkOut: UsbEndpoint? = null

    override val isOpen: Boolean
        get() = connection != null && bulkIn != null && bulkOut != null

    override fun open(): Boolean {
        val iface = findFastbootInterface() ?: return false
        val conn = usbManager.openDevice(device) ?: return false
        if (!conn.claimInterface(iface, true)) {
            conn.close()
            return false
        }
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
            if (ep.direction == UsbConstants.USB_DIR_OUT) outEp = ep
        }
        if (inEp == null || outEp == null) {
            conn.close()
            return false
        }
        connection = conn
        bulkIn = inEp
        bulkOut = outEp
        return true
    }

    private fun findFastbootInterface(): UsbInterface? {
        var fallback: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == INTERFACE_CLASS &&
                iface.interfaceSubclass == INTERFACE_SUBCLASS &&
                iface.interfaceProtocol == INTERFACE_PROTOCOL
            ) {
                return iface
            }
            if (fallback == null) {
                for (j in 0 until iface.endpointCount) {
                    if (iface.getEndpoint(j).type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        fallback = iface
                        break
                    }
                }
            }
        }
        return fallback
    }

    override fun close() {
        runCatching { connection?.close() }
        connection = null
        bulkIn = null
        bulkOut = null
    }

    override fun write(data: ByteArray, offset: Int, length: Int): Boolean {
        val conn = connection ?: return false
        val out = bulkOut ?: return false
        var sent = 0
        var attempts = 0
        while (sent < length && attempts < 6) {
            val want = minOf(CHUNK_SIZE, length - sent)
            val n = conn.bulkTransfer(out, data, offset + sent, want, 10000)
            if (n < 0) {
                attempts++
                Thread.sleep(100)
                continue
            }
            sent += n
        }
        return sent == length
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val input = bulkIn ?: return -1
        return conn.bulkTransfer(input, buffer, buffer.size, timeoutMs)
    }

    override fun readExact(size: Int, timeoutMs: Int): ByteArray? {
        val out = ByteArray(size)
        var filled = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        val chunk = ByteArray(4096)
        while (filled < size) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(0)
            if (remaining <= 0) return null
            val want = minOf(chunk.size, size - filled)
            val buf = if (want == chunk.size) chunk else ByteArray(want)
            val n = read(buf, remaining)
            if (n < 0) return null
            if (n == 0) {
                Thread.sleep(50)
                continue
            }
            buf.copyInto(out, filled, 0, n)
            filled += n
        }
        return out
    }

    override fun discardBuffers() {
    }
}
