package id.miflash.android.miflash.edl

import id.miflash.android.R
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.UiText
import id.miflash.android.common.LogKind
import id.miflash.android.miflash.FlashOption
import id.miflash.android.miflash.service.FlashProgress
import id.miflash.android.common.usb.UsbTransport
import java.io.IOException

class EdlFlasher(
    private val transport: UsbTransport,
    private val signer: SlaSigner
) {

    companion object {
        const val BUFFER_SECTORS = 256
        const val SECTOR_SIZE_UFS = 4096
        const val SECTOR_SIZE_EMMC = 512
    }

    fun interface ProgressListener {
        fun onProgress(fraction: Float, message: UiText)
    }

    fun flash(
        source: FirmwareSource,
        option: FlashOption = FlashOption.CLEAN_ALL,
        logger: FlashLogger? = null,
        listener: ProgressListener? = null
    ) {
        if (!transport.isOpen && !transport.open()) {
            throw FirehoseException("Tidak bisa membuka koneksi USB ke device")
        }
        try {
            val programmerName = source.listNames().firstOrNull { RomPatterns.programmer.matches(it) }
                ?: throw FirehoseException("File programmer firehose tidak ditemukan di folder firmware")
            val storageType = if (programmerName.contains("ufs", ignoreCase = true)) "ufs" else "emmc"
            val sectorSize = if (storageType == "ufs") SECTOR_SIZE_UFS else SECTOR_SIZE_EMMC

            listener?.onProgress(0.05f, UiText.en(R.string.progress_sahara_programmer))
            source.open(programmerName).use { programmer ->
                SaharaSession(transport).downloadProgrammer(programmer)
            }
            Thread.sleep(1000)

            val session = FirehoseSession(transport)
            session.sectorSize = sectorSize
            listener?.onProgress(0.15f, UiText.en(R.string.progress_firehose_ping))
            session.ping()

            listener?.onProgress(0.2f, UiText.en(R.string.progress_firehose_configure))
            configureWithAuth(session, sectorSize, storageType, skipStorageInit = 0)

            if (storageType == "ufs") {
                session.sendCommand(FirehoseCommands.setBootPartition(), checkAck = true)
            }

            listener?.onProgress(0.3f, UiText.en(R.string.progress_firehose_write))
            downloadImages(session, source, sectorSize, option, logger, listener)

            listener?.onProgress(0.95f, UiText.en(R.string.progress_firehose_reset))
            session.sendCommand(FirehoseCommands.partialReset, checkAck = true)
            Thread.sleep(1000)
            listener?.onProgress(1f, UiText.en(R.string.flash_done))
        } catch (e: IOException) {
            throw FirmwareException(
                "Gagal membaca file firmware — kartu microSD mungkin dicabut atau izin folder hilang",
                e
            )
        } finally {
            transport.close()
        }
    }

    private fun configureWithAuth(
        session: FirehoseSession,
        sectorSize: Int,
        storageType: String,
        skipStorageInit: Int
    ) {
        val maxPayload = sectorSize * BUFFER_SECTORS
        val first = runCatching {
            session.sendCommand(
                FirehoseCommands.configure(maxPayload, storageType, skipStorageInit),
                checkAck = true
            )
        }
        val response = first.getOrNull()
        if (response?.ack == true) return
        val needsAuth = response?.needsAuth == true || session.lastSawAuthError
        if (!needsAuth) {
            throw FirehoseException("configure failed!!!")
        }
        session.authenticate(signer)
        val retry = session.sendCommand(
            FirehoseCommands.configure(maxPayload, storageType, skipStorageInit),
            checkAck = true
        )
        if (retry?.ack != true) throw FirehoseException("configure failed!!!")
    }

    private fun downloadImages(
        session: FirehoseSession,
        source: FirmwareSource,
        sectorSize: Int,
        option: FlashOption,
        logger: FlashLogger?,
        listener: ProgressListener?
    ) {
        val rawPrograms = source.listNames().filter { RomPatterns.rawProgram.matches(it) }
        val patches = source.listNames().filter { RomPatterns.patch.matches(it) }
        if (rawPrograms.isEmpty()) throw FirehoseException("rawprogram*.xml tidak ditemukan")
        val transfer = FileTransfer(session, sectorSize, BUFFER_SECTORS)
        val skipped = mutableListOf<String>()
        val plan = mutableListOf<ProgramEntry>()
        for (rawName in rawPrograms) {
            val entries = RomScript.parseRawProgram(source.readText(rawName))
            for (entry in entries) {
                val isUserdata = entry.label.equals("userdata", ignoreCase = true) ||
                    entry.filename.startsWith("userdata", ignoreCase = true)
                if (option == FlashOption.SAVE_USERDATA && isUserdata) {
                    skipped.add(entry.label.ifBlank { entry.filename })
                    continue
                }
                plan.add(entry)
            }
        }
        if (skipped.isNotEmpty()) {
            logger?.log(R.string.log_partitions_skipped, skipped.distinct().joinToString(", "), kind = LogKind.WARNING)
        }
        FlashProgress.setPartitions(plan.map { it.label.ifBlank { it.filename } })
        val totalEntries = plan.size.coerceAtLeast(1)
        var doneEntries = 0
        for ((index, entry) in plan.withIndex()) {
            FlashProgress.checkCancelled()
            val displayName = entry.label.ifBlank { entry.filename }
            FlashProgress.markPartitionStarted(index)
            listener?.onProgress(0.3f + 0.65f * doneEntries / totalEntries.toFloat(), UiText.en(R.string.progress_flash_partition, displayName))
            val reportEntryProgress: (Long, Long) -> Unit = { sent, total ->
                val entryFraction = if (total > 0) sent.toFloat() / total else 0f
                val overall = 0.3f + 0.65f * (doneEntries + entryFraction) / totalEntries
                listener?.onProgress(overall, UiText.en(R.string.progress_flash_partition, displayName))
            }
            source.open(entry.filename).use { imageFile ->
                if (SparseImage.isSparse(imageFile)) transfer.writeSparseFile(entry, imageFile, reportEntryProgress)
                else transfer.writeFile(entry, imageFile, reportEntryProgress)
            }
            FlashProgress.markPartitionDone(index)
            doneEntries++
        }
        for ((index, rawName) in rawPrograms.withIndex()) {
            val patchName = patches.getOrNull(index) ?: continue
            val patchEntries = RomScript.parsePatch(source.readText(patchName))
            for (entry in patchEntries) {
                FlashProgress.checkCancelled()
                session.sendCommand(
                    FirehoseCommands.patch(
                        byteOffset = entry.byteOffset,
                        filename = entry.filename,
                        physicalPartition = entry.physicalPartition,
                        sizeInBytes = entry.sizeInBytes,
                        startSector = entry.startSector,
                        value = entry.value,
                        sectorSize = entry.sectorSize
                    ),
                    checkAck = true
                )
            }
        }
    }
}
