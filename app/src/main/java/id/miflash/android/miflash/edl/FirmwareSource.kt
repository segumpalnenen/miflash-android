package id.miflash.android.miflash.edl

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.documentfile.provider.DocumentFile
import id.miflash.android.R
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.SeekableSource
import java.io.FileNotFoundException
import java.nio.ByteBuffer

class FirmwareException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RomFile(val name: String, private val source: SeekableSource) : SeekableSource by source

class ParcelFdSeekableSource(pfd: ParcelFileDescriptor) : SeekableSource {
    private val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
    private val channel = stream.channel
    override val size: Long = channel.size()
    override fun seek(position: Long) {
        channel.position(position)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return channel.read(ByteBuffer.wrap(buffer, offset, length))
    }
    override fun close() {
        stream.close()
    }
}

class FirmwareSource private constructor(
    val rootUri: Uri,
    val displayName: String,
    val volumeDescription: String?,
    private val files: Map<String, DocumentFile>,
    private val appContext: Context
) {

    companion object {
        const val MAX_TEXT_BYTES = 4L * 1024 * 1024

        fun open(context: Context, treeUri: Uri, logger: FlashLogger): FirmwareSource {
            val appContext = context.applicationContext
            val permitted = appContext.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
            if (!permitted) {
                throw FirmwareException("Izin folder firmware tidak tersimpan — pilih ulang folder firmware")
            }
            val root = try {
                DocumentFile.fromTreeUri(appContext, treeUri)
            } catch (e: SecurityException) {
                throw FirmwareException("Izin folder firmware hilang — pilih ulang folder firmware", e)
            } ?: throw FirmwareException("Folder firmware tidak bisa dibuka")
            val volumeDescription = try {
                appContext.getSystemService(StorageManager::class.java)
                    ?.getStorageVolume(treeUri)?.getDescription(appContext)
            } catch (e: Exception) {
                null
            }
            val displayName = root.name ?: treeUri.lastPathSegment ?: "firmware"
            val listed = try {
                root.listFiles()
            } catch (e: SecurityException) {
                throw FirmwareException("Izin folder firmware hilang — pilih ulang folder firmware", e)
            }
            val imagesDoc = listed.firstOrNull {
                it.isDirectory && it.name?.contains("images", ignoreCase = true) == true
            }
            val sourceDir = imagesDoc ?: root
            val docs = try {
                if (imagesDoc != null) sourceDir.listFiles() else listed
            } catch (e: SecurityException) {
                throw FirmwareException("Izin folder firmware hilang — pilih ulang folder firmware", e)
            }
            val files = docs.filter { it.isFile && it.name != null && isRelevant(it.name!!) }
                .associateBy { it.name!! }
            if (files.isEmpty()) {
                throw FirmwareException("Tidak ada file firmware yang dikenali di folder ini")
            }
            val source = FirmwareSource(treeUri, displayName, volumeDescription, files, appContext)
            logger.log(R.string.log_firmware_folder, source.label, files.size)
            return source
        }

        private fun isRelevant(name: String): Boolean {
            val lower = name.lowercase()
            return RomPatterns.rawProgram.matches(name) ||
                RomPatterns.patch.matches(name) ||
                RomPatterns.programmer.matches(name) ||
                RomPatterns.provision.matches(name) ||
                (lower.startsWith("flash_all") && (lower.endsWith(".sh") || lower.endsWith(".bat"))) ||
                lower.endsWith(".img") ||
                lower.endsWith(".bin") ||
                lower.endsWith(".mbn") ||
                lower.endsWith(".elf")
        }
    }

    val label: String
        get() = if (volumeDescription != null) "$displayName ($volumeDescription)" else displayName

    fun listNames(): List<String> = files.keys.sorted()

    fun contains(name: String): Boolean = files.containsKey(name)

    fun open(name: String): RomFile {
        val doc = files[name]
            ?: throw FirmwareException("File tidak ditemukan di folder firmware: $name")
        return try {
            val pfd = appContext.contentResolver.openFileDescriptor(doc.uri, "r")
                ?: throw FirmwareException("Tidak bisa membuka file: $name")
            RomFile(name, ParcelFdSeekableSource(pfd))
        } catch (e: FirmwareException) {
            throw e
        } catch (e: SecurityException) {
            throw FirmwareException("Izin folder firmware hilang — pilih ulang folder firmware", e)
        } catch (e: FileNotFoundException) {
            throw FirmwareException("File tidak ditemukan, kartu microSD mungkin dicabut: $name", e)
        }
    }

    fun readText(name: String): String {
        val doc = files[name]
            ?: throw FirmwareException("File tidak ditemukan di folder firmware: $name")
        if (doc.length() > MAX_TEXT_BYTES) {
            throw FirmwareException("File terlalu besar untuk dibaca sebagai teks: $name")
        }
        open(name).use { rom ->
            val bytes = ByteArray(rom.size.toInt())
            rom.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
