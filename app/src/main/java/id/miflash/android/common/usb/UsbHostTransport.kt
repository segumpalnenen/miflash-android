package id.miflash.android.common.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager

class UsbHostTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) : UsbTransport {

    private var connection: UsbDeviceConnection? = null
    private var bulkIn: UsbEndpoint? = null
    private var bulkOut: UsbEndpoint? = null

    override val isOpen: Boolean
        get() = connection != null && bulkIn != null && bulkOut != null

    override fun open(): Boolean {
        val iface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { iface ->
                (0 until iface.endpointCount).any { i ->
                    iface.getEndpoint(i).type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
            } ?: return false
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

    override fun close() {
        runCatching { connection?.close() }
        connection = null
        bulkIn = null
        bulkOut = null
    }

    override fun write(data: ByteArray, offset: Int, length: Int): Boolean {
        val conn = connection ?: return false
        val out = bulkOut ?: return false
        val chunk = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        var sent = 0
        var attempts = 0
        while (sent < chunk.size && attempts < 6) {
            val n = conn.bulkTransfer(out, chunk, sent, chunk.size - sent, 2000)
            if (n < 0) {
                attempts++
                Thread.sleep(100)
                continue
            }
            sent += n
        }
        return sent == chunk.size
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
