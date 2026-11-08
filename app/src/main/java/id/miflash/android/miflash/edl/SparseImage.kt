package id.miflash.android.miflash.edl

import id.miflash.android.common.SeekableSource
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseImage {
    const val MAGIC: Long = 0xED26FF3AL
    const val CHUNK_RAW: Int = 0xCAC1
    const val CHUNK_FILL: Int = 0xCAC2
    const val CHUNK_DONTCARE: Int = 0xCAC3
    const val HEADER_SIZE: Int = 28
    const val CHUNK_HEADER_SIZE: Int = 12

    data class Header(
        val magic: Long,
        val majorVersion: Int,
        val minorVersion: Int,
        val fileHeaderSize: Int,
        val chunkHeaderSize: Int,
        val blockSize: Int,
        val totalBlocks: Int,
        val totalChunks: Int,
        val imageChecksum: Long
    )

    data class ChunkHeader(
        val type: Int,
        val chunkSizeBlocks: Int,
        val totalSize: Int
    )

    fun parseHeader(data: ByteArray): Header? {
        if (data.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(data, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int.toLong() and 0xFFFFFFFFL
        if (magic != MAGIC) return null
        return Header(
            magic = magic,
            majorVersion = buf.short.toInt() and 0xFFFF,
            minorVersion = buf.short.toInt() and 0xFFFF,
            fileHeaderSize = buf.short.toInt() and 0xFFFF,
            chunkHeaderSize = buf.short.toInt() and 0xFFFF,
            blockSize = buf.int,
            totalBlocks = buf.int,
            totalChunks = buf.int,
            imageChecksum = buf.int.toLong() and 0xFFFFFFFFL
        )
    }

    fun parseChunkHeader(data: ByteArray, offset: Int): ChunkHeader? {
        if (data.size < offset + CHUNK_HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(data, offset, CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        return ChunkHeader(
            type = buf.short.toInt() and 0xFFFF,
            chunkSizeBlocks = buf.int,
            totalSize = buf.int
        )
    }

    fun isSparse(rom: RomFile): Boolean {
        if (rom.size < HEADER_SIZE) return false
        val head = ByteArray(HEADER_SIZE)
        try {
            rom.seek(0)
            rom.readFully(head)
        } catch (e: Exception) {
            throw FirmwareException("Gagal membaca file ${rom.name} — kartu microSD mungkin dicabut", e)
        }
        val header = parseHeader(head) ?: return false
        return header.majorVersion == 1
    }

    fun validate(header: Header, sectorSize: Int): Boolean {
        if (header.majorVersion != 1) return false
        if (header.blockSize <= 0) return false
        if (header.blockSize % sectorSize != 0) return false
        return true
    }
}
