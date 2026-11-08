package id.miflash.android.miflash.edl

import id.miflash.android.common.SeekableSource
import id.miflash.android.common.usb.UsbTransport

class SaharaException(message: String) : Exception(message)

class SaharaSession(private val transport: UsbTransport) {

    fun downloadProgrammer(source: SeekableSource, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val fileSize = source.size
        waitHello()
        transport.write(SaharaPackets.buildHelloResponse(0))
        var done = false
        var guard = 0
        while (!done) {
            if (guard++ > 10000) throw SaharaException("Sahara transfer tidak selesai")
            val packet = readPacket() ?: throw SaharaException("Tidak ada respons Sahara")
            val header = SaharaPackets.parseHeader(packet)
                ?: throw SaharaException("Paket Sahara tidak valid")
            when (header.command) {
                SaharaPackets.READ_DATA -> {
                    val req = SaharaPackets.parseReadData(packet)
                        ?: throw SaharaException("Paket read-data tidak valid")
                    sendFileChunk(source, req.offset, req.length)
                    onProgress(req.offset + req.length, fileSize)
                }
                SaharaPackets.READ_DATA_64 -> {
                    val req = SaharaPackets.parseReadData64(packet)
                        ?: throw SaharaException("Paket read-data 64-bit tidak valid")
                    sendFileChunk(source, req.offset, req.length)
                    onProgress(req.offset + req.length, fileSize)
                }
                SaharaPackets.END_TRANSFER -> {
                    val status = SaharaPackets.parseEndTransferStatus(packet)
                    if (status != 0) throw SaharaException("End-transfer status $status")
                    done = true
                }
                else -> Unit
            }
        }
        transport.write(SaharaPackets.buildDone())
        val response = readPacket() ?: throw SaharaException("Tidak ada done-response")
        val header = SaharaPackets.parseHeader(response)
        if (header?.command != SaharaPackets.DONE_RESPONSE) {
            throw SaharaException("Programmer transfer error ${header?.command}")
        }
        Thread.sleep(1000)
    }

    private fun waitHello() {
        var attempts = 0
        while (attempts < 10) {
            val packet = readPacket(timeoutMs = 1000)
            if (packet != null) {
                val header = SaharaPackets.parseHeader(packet)
                if (header?.command == SaharaPackets.HELLO) return
            }
            attempts++
            if (attempts < 10) {
                transport.discardBuffers()
                transport.write(SaharaPackets.buildHelloResponse(3))
                transport.write(SaharaPackets.buildSwitchMode(0))
                Thread.sleep(500)
            }
        }
        throw SaharaException("cannot receive hello packet")
    }

    private fun sendFileChunk(source: SeekableSource, offset: Long, length: Long) {
        if (length <= 0 || length > 16 * 1024 * 1024) {
            throw SaharaException("Panjang chunk Sahara tidak wajar: $length")
        }
        val buf = ByteArray(length.toInt())
        source.seek(offset)
        var filled = 0
        while (filled < buf.size) {
            val n = source.read(buf, filled, buf.size - filled)
            if (n <= 0) throw SaharaException("File programmer terpotong")
            filled += n
        }
        if (!transport.write(buf)) throw SaharaException("Gagal mengirim chunk programmer")
    }

    private fun readPacket(timeoutMs: Int = 5000): ByteArray? {
        val header = transport.readExact(8, timeoutMs) ?: return null
        val parsed = SaharaPackets.parseHeader(header) ?: return null
        if (parsed.length < 8 || parsed.length > 65536) return header
        val rest = transport.readExact(parsed.length - 8, timeoutMs) ?: return header
        return header + rest
    }
}
