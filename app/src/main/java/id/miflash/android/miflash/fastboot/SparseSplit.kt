package id.miflash.android.miflash.fastboot

import id.miflash.android.common.SeekableSource
import id.miflash.android.miflash.edl.SparseImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseSplit {

    const val RAW_BLOCK_SIZE = 4096
    const val MAX_RAW_CHUNK_BYTES = 64L * 1024 * 1024
    const val MAX_RAW_CHUNK_BLOCKS = (MAX_RAW_CHUNK_BYTES / RAW_BLOCK_SIZE).toInt()

    private data class ChunkDef(
        val type: Int,
        val blocks: Int,
        val dataOffset: Long,
        val dataSize: Long
    ) {
        val totalSize: Long get() = SparseImage.CHUNK_HEADER_SIZE + dataSize

        fun headerBytes(): ByteArray {
            val buf = ByteBuffer.allocate(SparseImage.CHUNK_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(type.toShort())
            buf.putShort(0)
            buf.putInt(blocks)
            buf.putInt(totalSize.toInt())
            return buf.array()
        }
    }

    private data class Segment(
        val virtualStart: Long,
        val length: Long,
        val fileOffset: Long,
        val bytes: ByteArray?
    )

    private class SparsePieceSource(
        private val source: SeekableSource,
        chunks: List<ChunkDef>,
        header: SparseImage.Header
    ) : SeekableSource {

        private val segments: List<Segment>
        private var position: Long = 0

        override val size: Long

        init {
            val totalBlocks = chunks.sumOf { it.blocks.toLong() }.toInt()
            val pieceHeader = header.copy(
                totalBlocks = totalBlocks,
                totalChunks = chunks.size,
                imageChecksum = 0
            )
            val headerBytes = ByteBuffer.allocate(SparseImage.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(pieceHeader.magic.toInt())
                putShort(pieceHeader.majorVersion.toShort())
                putShort(pieceHeader.minorVersion.toShort())
                putShort(pieceHeader.fileHeaderSize.toShort())
                putShort(pieceHeader.chunkHeaderSize.toShort())
                putInt(pieceHeader.blockSize)
                putInt(pieceHeader.totalBlocks)
                putInt(pieceHeader.totalChunks)
                putInt(pieceHeader.imageChecksum.toInt())
            }.array()
            val segs = mutableListOf<Segment>()
            var cursor = 0L
            segs.add(Segment(0L, SparseImage.HEADER_SIZE.toLong(), -1L, headerBytes))
            cursor += SparseImage.HEADER_SIZE
            for (chunk in chunks) {
                segs.add(Segment(cursor, SparseImage.CHUNK_HEADER_SIZE.toLong(), -1L, chunk.headerBytes()))
                cursor += SparseImage.CHUNK_HEADER_SIZE
                if (chunk.dataSize > 0) {
                    segs.add(Segment(cursor, chunk.dataSize, chunk.dataOffset, null))
                    cursor += chunk.dataSize
                }
            }
            segments = segs
            size = cursor
        }

        override fun seek(position: Long) {
            this.position = position.coerceIn(0L, size)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            val want = minOf(length.toLong(), size - position).toInt()
            var filled = 0
            while (filled < want) {
                val pos = position + filled
                val seg = segments.firstOrNull { pos >= it.virtualStart && pos < it.virtualStart + it.length }
                    ?: break
                val segOffset = (pos - seg.virtualStart).toInt()
                val take = minOf(want - filled, (seg.length - segOffset).toInt())
                if (seg.bytes != null) {
                    seg.bytes.copyInto(buffer, offset + filled, segOffset, segOffset + take)
                } else {
                    val filePos = seg.fileOffset + segOffset
                    source.seek(filePos)
                    val tmp = ByteArray(take)
                    var got = 0
                    while (got < take) {
                        val n = source.read(tmp, got, take - got)
                        if (n <= 0) break
                        got += n
                    }
                    tmp.copyInto(buffer, offset + filled, 0, got)
                    if (got < take) {
                        buffer.fill(0, offset + filled + got, offset + filled + take)
                    }
                }
                filled += take
            }
            position += filled
            return filled
        }

        override fun close() {
        }
    }

    fun split(source: SeekableSource, maxPieceSize: Long): List<SeekableSource> {
        if (maxPieceSize < SparseImage.HEADER_SIZE + SparseImage.CHUNK_HEADER_SIZE + RAW_BLOCK_SIZE) {
            throw FastbootException("max-download-size device terlalu kecil: $maxPieceSize")
        }
        val sparse = isSparse(source)
        val header: SparseImage.Header
        val chunks: List<ChunkDef>
        if (sparse) {
            header = readHeader(source)
            chunks = readChunkTable(source, header)
            if (source.size <= maxPieceSize) return listOf(source)
        } else {
            if (source.size <= maxPieceSize) return listOf(source)
            header = SparseImage.Header(
                magic = SparseImage.MAGIC,
                majorVersion = 1,
                minorVersion = 0,
                fileHeaderSize = SparseImage.HEADER_SIZE,
                chunkHeaderSize = SparseImage.CHUNK_HEADER_SIZE,
                blockSize = RAW_BLOCK_SIZE,
                totalBlocks = ((source.size + RAW_BLOCK_SIZE - 1) / RAW_BLOCK_SIZE).toInt(),
                totalChunks = 0,
                imageChecksum = 0
            )
            chunks = rawChunks(source, header)
        }
        val normalized = normalizeChunks(chunks, header.blockSize, maxPieceSize)
        val groups = groupChunks(normalized, maxPieceSize)
        return groups.map { SparsePieceSource(source, it, header) }
    }

    fun isSparse(source: SeekableSource): Boolean {
        if (source.size < SparseImage.HEADER_SIZE) return false
        val head = ByteArray(SparseImage.HEADER_SIZE)
        source.seek(0)
        source.readFully(head)
        val header = SparseImage.parseHeader(head) ?: return false
        return header.majorVersion == 1
    }

    private fun readHeader(source: SeekableSource): SparseImage.Header {
        val head = ByteArray(SparseImage.HEADER_SIZE)
        source.seek(0)
        source.readFully(head)
        return SparseImage.parseHeader(head)
            ?: throw FastbootException("Header sparse tidak valid")
    }

    private fun readChunkTable(source: SeekableSource, header: SparseImage.Header): List<ChunkDef> {
        val chunks = mutableListOf<ChunkDef>()
        var filePos = SparseImage.HEADER_SIZE.toLong()
        val raw = ByteArray(SparseImage.CHUNK_HEADER_SIZE)
        repeat(header.totalChunks) {
            source.seek(filePos)
            source.readFully(raw)
            val chunkHeader = SparseImage.parseChunkHeader(raw, 0)
                ?: throw FastbootException("Header chunk sparse tidak valid")
            val dataSize = when (chunkHeader.type) {
                SparseImage.CHUNK_RAW -> chunkHeader.chunkSizeBlocks.toLong() * header.blockSize
                SparseImage.CHUNK_FILL -> 4L
                SparseImage.CHUNK_DONTCARE -> 0L
                else -> throw FastbootException("Tipe chunk sparse tidak dikenal: ${chunkHeader.type}")
            }
            if (chunkHeader.totalSize.toLong() != SparseImage.CHUNK_HEADER_SIZE + dataSize) {
                throw FastbootException("Ukuran chunk sparse tidak konsisten")
            }
            chunks.add(
                ChunkDef(
                    type = chunkHeader.type,
                    blocks = chunkHeader.chunkSizeBlocks,
                    dataOffset = filePos + SparseImage.CHUNK_HEADER_SIZE,
                    dataSize = dataSize
                )
            )
            filePos += chunkHeader.totalSize
        }
        return chunks
    }

    private fun rawChunks(source: SeekableSource, header: SparseImage.Header): List<ChunkDef> {
        val chunks = mutableListOf<ChunkDef>()
        var remainingBlocks = header.totalBlocks
        var filePos = 0L
        while (remainingBlocks > 0) {
            val take = minOf(remainingBlocks, MAX_RAW_CHUNK_BLOCKS)
            chunks.add(
                ChunkDef(
                    type = SparseImage.CHUNK_RAW,
                    blocks = take,
                    dataOffset = filePos,
                    dataSize = take.toLong() * header.blockSize
                )
            )
            filePos += take.toLong() * header.blockSize
            remainingBlocks -= take
        }
        return chunks
    }

    private fun normalizeChunks(
        chunks: List<ChunkDef>,
        blockSize: Int,
        maxPieceSize: Long
    ): List<ChunkDef> {
        val out = mutableListOf<ChunkDef>()
        val maxChunkBytes = maxPieceSize - SparseImage.HEADER_SIZE - SparseImage.CHUNK_HEADER_SIZE
        for (chunk in chunks) {
            if (chunk.type != SparseImage.CHUNK_RAW || chunk.totalSize <= maxChunkBytes) {
                out.add(chunk)
                continue
            }
            val blocksPerPiece = (maxChunkBytes / blockSize).toInt().coerceAtLeast(1)
            var left = chunk.blocks
            var dataPos = chunk.dataOffset
            while (left > 0) {
                val take = minOf(left, blocksPerPiece)
                out.add(
                    ChunkDef(
                        type = SparseImage.CHUNK_RAW,
                        blocks = take,
                        dataOffset = dataPos,
                        dataSize = take.toLong() * blockSize
                    )
                )
                dataPos += take.toLong() * blockSize
                left -= take
            }
        }
        return out
    }

    private fun groupChunks(chunks: List<ChunkDef>, maxPieceSize: Long): List<List<ChunkDef>> {
        val groups = mutableListOf<List<ChunkDef>>()
        var current = mutableListOf<ChunkDef>()
        var currentSize = SparseImage.HEADER_SIZE.toLong()
        for (chunk in chunks) {
            if (current.isNotEmpty() && currentSize + chunk.totalSize > maxPieceSize) {
                groups.add(current)
                current = mutableListOf()
                currentSize = SparseImage.HEADER_SIZE.toLong()
            }
            current.add(chunk)
            currentSize += chunk.totalSize
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }
}
