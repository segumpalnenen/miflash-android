package id.miflash.android.miflash.edl

import java.nio.ByteBuffer
import java.nio.ByteOrder

object SaharaPackets {
    const val HELLO = 1
    const val HELLO_RESPONSE = 2
    const val READ_DATA = 3
    const val END_TRANSFER = 4
    const val DONE = 5
    const val DONE_RESPONSE = 6
    const val RESET = 7
    const val RESET_RESPONSE = 8
    const val CMD_READY = 11
    const val SWITCH_MODE = 12
    const val READ_DATA_64 = 18
    const val RESET_STATE_MACHINE = 19

    fun buildHelloResponse(mode: Int): ByteArray {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(HELLO_RESPONSE)
        buf.putInt(48)
        buf.putInt(2)
        buf.putInt(1)
        buf.putInt(0)
        buf.putInt(mode)
        return buf.array()
    }

    fun buildSwitchMode(mode: Int): ByteArray {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(SWITCH_MODE)
        buf.putInt(12)
        buf.putInt(mode)
        return buf.array()
    }

    fun buildDone(): ByteArray {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(DONE)
        buf.putInt(8)
        return buf.array()
    }

    data class PacketHeader(val command: Int, val length: Int)

    fun parseHeader(data: ByteArray): PacketHeader? {
        if (data.size < 8) return null
        val buf = ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        return PacketHeader(buf.int, buf.int)
    }

    data class ReadDataRequest(val imageId: Long, val offset: Long, val length: Long)

    fun parseReadData(data: ByteArray): ReadDataRequest? {
        if (data.size < 20) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.int
        buf.int
        val imageId = buf.int.toLong() and 0xFFFFFFFFL
        val offset = buf.int.toLong() and 0xFFFFFFFFL
        val length = buf.int.toLong() and 0xFFFFFFFFL
        return ReadDataRequest(imageId, offset, length)
    }

    fun parseReadData64(data: ByteArray): ReadDataRequest? {
        if (data.size < 32) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.int
        buf.int
        val imageId = buf.long
        val offset = buf.long
        val length = buf.long
        return ReadDataRequest(imageId, offset, length)
    }

    fun parseEndTransferStatus(data: ByteArray): Int {
        if (data.size < 12) return -1
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.int
        buf.int
        return buf.int
    }
}
