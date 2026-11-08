package id.miflash.android.miflash.edl

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class ProgramEntry(
    val filename: String,
    val startSector: Long,
    val numPartitionSectors: Long,
    val fileSectorOffset: Long,
    val sectorSize: Int,
    val physicalPartition: Int,
    val label: String
)

data class PatchEntry(
    val filename: String,
    val byteOffset: Long,
    val sizeInBytes: Long,
    val startSector: Long,
    val value: String,
    val sectorSize: Int,
    val physicalPartition: Int
)

object RomPatterns {
    val rawProgram = Regex("rawprogram[0-9]{1,20}\\.xml")
    val patch = Regex("patch[0-9]{1,20}\\.xml")
    val programmer = Regex("prog_.*_firehose_.*.*|prog_.*firehose_.*.*|prog_firehose_*.elf|xbl_.*devprg_.*.*")
    val provision = Regex("provision.*\\.xml")
}

object RomScript {

    fun parseRawProgram(xmlText: String): List<ProgramEntry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xmlText)))
        val out = mutableListOf<ProgramEntry>()
        val nodes = doc.getElementsByTagName("program")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out.add(
                ProgramEntry(
                    filename = el.getAttribute("filename"),
                    startSector = el.getAttribute("start_sector").toLongOrNull() ?: 0L,
                    numPartitionSectors = el.getAttribute("num_partition_sectors").toLongOrNull() ?: 0L,
                    fileSectorOffset = el.getAttribute("file_sector_offset").toLongOrNull() ?: 0L,
                    sectorSize = el.getAttribute("sector_size_in_bytes").toIntOrNull() ?: 512,
                    physicalPartition = el.getAttribute("physical_partition_number").toIntOrNull() ?: 0,
                    label = el.getAttribute("label")
                )
            )
        }
        return out
    }

    fun parsePatch(xmlText: String): List<PatchEntry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xmlText)))
        val out = mutableListOf<PatchEntry>()
        val nodes = doc.getElementsByTagName("patch")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("filename") != "DISK") continue
            out.add(
                PatchEntry(
                    filename = el.getAttribute("filename"),
                    byteOffset = el.getAttribute("byte_offset").toLongOrNull() ?: 0L,
                    sizeInBytes = el.getAttribute("size_in_bytes").toLongOrNull() ?: 0L,
                    startSector = el.getAttribute("start_sector").toLongOrNull() ?: 0L,
                    value = el.getAttribute("value"),
                    sectorSize = el.getAttribute("sector_size_in_bytes").toIntOrNull() ?: 512,
                    physicalPartition = el.getAttribute("physical_partition_number").toIntOrNull() ?: 0
                )
            )
        }
        return out
    }

    fun parseXblGptLuns(xmlText: String): List<Int> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(xmlText)))
        val out = mutableListOf<Int>()
        val nodes = doc.getElementsByTagName("xblgpt")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            el.getAttribute("lun").toIntOrNull()?.let { out.add(it) }
        }
        return out
    }
}
