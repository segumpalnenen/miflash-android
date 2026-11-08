package id.miflash.android.common

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

interface SeekableSource : Closeable {
    val size: Long
    fun seek(position: Long)
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun readFully(buffer: ByteArray) {
        var filled = 0
        while (filled < buffer.size) {
            val n = read(buffer, filled, buffer.size - filled)
            if (n <= 0) throw EOFException("Sumber data terpotong")
            filled += n
        }
    }
}

class FileSeekableSource(file: File) : SeekableSource {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long get() = raf.length()
    override fun seek(position: Long) = raf.seek(position)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = raf.read(buffer, offset, length)
    override fun close() = raf.close()
}
