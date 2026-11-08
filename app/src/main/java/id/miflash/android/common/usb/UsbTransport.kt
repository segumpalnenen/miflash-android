package id.miflash.android.common.usb

interface UsbTransport {
    val isOpen: Boolean
    fun open(): Boolean
    fun close()
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Boolean
    fun read(buffer: ByteArray, timeoutMs: Int = 1000): Int
    fun readExact(size: Int, timeoutMs: Int = 5000): ByteArray?
    fun discardBuffers()
}
