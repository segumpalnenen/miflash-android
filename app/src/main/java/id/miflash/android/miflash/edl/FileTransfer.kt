package id.miflash.android.miflash.edl

import id.miflash.android.miflash.service.FlashProgress
import id.miflash.android.common.SeekableSource

class FileTransfer(
    private val session: FirehoseSession,
    private val sectorSize: Int,
    private val bufferSectors: Int = 256
) {

    fun writeFile(entry: ProgramEntry, source: SeekableSource, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val total = source.size
        session.sendCommand(
            FirehoseCommands.program(
                filename = entry.filename,
                startSector = entry.startSector,
                numPartitionSectors = entry.numPartitionSectors,
                fileSectorOffset = entry.fileSectorOffset,
                sectorSize = entry.sectorSize,
                physicalPartition = entry.physicalPartition,
                label = entry.label
            ),
            checkAck = true
        )
        streamRaw(source, entry.fileSectorOffset * entry.sectorSize, total, onProgress)
        if (!session.waitSingleAck()) throw FirehoseException("did not detect ACK from target.")
    }

    fun writeSparseFile(entry: ProgramEntry, source: SeekableSource, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val headerBytes = ByteArray(SparseImage.HEADER_SIZE)
        source.seek(0)
        source.readFully(headerBytes)
        val header = SparseImage.parseHeader(headerBytes)
            ?: throw FirehoseException("Header sparse tidak valid: ${entry.filename}")
        if (!SparseImage.validate(header, entry.sectorSize)) {
            throw FirehoseException("Header sparse tidak valid: ${entry.filename}")
        }
        session.sendCommand(
            FirehoseCommands.program(
                filename = entry.filename,
                startSector = entry.startSector,
                numPartitionSectors = entry.numPartitionSectors,
                fileSectorOffset = entry.fileSectorOffset,
                sectorSize = entry.sectorSize,
                physicalPartition = entry.physicalPartition,
                label = entry.label
            ),
            checkAck = true
        )
        var offset = SparseImage.HEADER_SIZE.toLong()
        var chunksSinceAck = 0
        var sectorsWritten = 0L
        repeat(header.totalChunks) {
            FlashProgress.checkCancelled()
            val chunkHead = ByteArray(SparseImage.CHUNK_HEADER_SIZE)
            source.seek(offset)
            source.readFully(chunkHead)
            val chunk = SparseImage.parseChunkHeader(chunkHead, 0)
                ?: throw FirehoseException("Header chunk sparse tidak valid")
            offset += SparseImage.CHUNK_HEADER_SIZE
            val dataSize = chunk.totalSize - SparseImage.CHUNK_HEADER_SIZE
            when (chunk.type) {
                SparseImage.CHUNK_RAW -> {
                    streamRaw(source, offset, dataSize.toLong()) { sent, _ ->
                        onProgress(sectorsWritten * entry.sectorSize + sent, header.totalBlocks * header.blockSize.toLong())
                    }
                    sectorsWritten += chunk.chunkSizeBlocks
                    chunksSinceAck++
                }
                SparseImage.CHUNK_FILL -> {
                    val fill = ByteArray(4)
                    source.seek(offset)
                    source.readFully(fill)
                    val totalFill = chunk.chunkSizeBlocks.toLong() * header.blockSize
                    streamFill(fill, totalFill)
                    sectorsWritten += chunk.chunkSizeBlocks
                    chunksSinceAck++
                }
                SparseImage.CHUNK_DONTCARE -> {
                    sectorsWritten += chunk.chunkSizeBlocks
                }
                else -> throw FirehoseException("Tipe chunk sparse tidak dikenal: ${chunk.type}")
            }
            offset += dataSize
            if (chunksSinceAck >= 10) {
                session.waitAck(chunksSinceAck)
                chunksSinceAck = 0
            }
        }
        if (chunksSinceAck > 0) session.waitAck(chunksSinceAck)
    }

    private fun streamRaw(
        source: SeekableSource,
        startOffset: Long,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        val chunkSize = sectorSize * bufferSectors
        val buf = ByteArray(chunkSize)
        var remaining = totalBytes
        var fileOffset = startOffset
        var sent = 0L
        source.seek(fileOffset)
        while (remaining > 0) {
            FlashProgress.checkCancelled()
            val want = minOf(chunkSize.toLong(), remaining).toInt()
            val read = source.read(buf, 0, want)
            if (read <= 0) throw FirehoseException("File image terpotong")
            if (!sendBytes(buf, read)) throw FirehoseException("Gagal mengirim data image")
            remaining -= read
            sent += read
            onProgress(sent, totalBytes)
        }
    }

    private fun streamFill(fill: ByteArray, totalBytes: Long) {
        val chunkSize = sectorSize * bufferSectors
        val buf = ByteArray(chunkSize)
        for (i in buf.indices) buf[i] = fill[i % fill.size]
        var remaining = totalBytes
        while (remaining > 0) {
            FlashProgress.checkCancelled()
            val want = minOf(chunkSize.toLong(), remaining).toInt()
            if (!sendBytes(buf, want)) throw FirehoseException("Gagal mengirim data fill")
            remaining -= want
        }
    }

    private fun sendBytes(data: ByteArray, length: Int): Boolean {
        return session.sendRaw(data, length)
    }
}
