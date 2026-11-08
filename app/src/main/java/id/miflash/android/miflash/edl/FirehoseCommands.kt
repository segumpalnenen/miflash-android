package id.miflash.android.miflash.edl

object FirehoseCommands {
    const val PAYLOAD_SIZE = 1048576

    fun xml(data: String): String = "<?xml version=\"1.0\" ?><data>$data</data>"

    val nop: String
        get() = xml("<nop value=\"ping\" />")

    fun configure(
        maxPayloadSize: Int,
        memoryName: String,
        skipStorageInit: Int,
        verbose: Int = 0
    ): String = xml(
        "<configure verbose=\"$verbose\" AlwaysValidate=\"0\" ZlpAwareHost=\"1\" " +
            "MaxPayloadSizeToTargetInBytes=\"$maxPayloadSize\" " +
            "MemoryName=\"$memoryName\" SkipStorageInit=\"$skipStorageInit\" />"
    )

    fun program(
        filename: String,
        startSector: Long,
        numPartitionSectors: Long,
        fileSectorOffset: Long,
        sectorSize: Int,
        physicalPartition: Int,
        label: String
    ): String = xml(
        "<program SECTOR_SIZE_IN_BYTES=\"$sectorSize\" file_sector_offset=\"$fileSectorOffset\" " +
            "filename=\"$filename\" label=\"$label\" num_partition_sectors=\"$numPartitionSectors\" " +
            "physical_partition_number=\"$physicalPartition\" start_sector=\"$startSector\" />"
    )

    fun read(
        filename: String,
        startSector: Long,
        numPartitionSectors: Long,
        sectorSize: Int,
        physicalPartition: Int
    ): String = xml(
        "<read SECTOR_SIZE_IN_BYTES=\"$sectorSize\" filename=\"$filename\" " +
            "num_partition_sectors=\"$numPartitionSectors\" physical_partition_number=\"$physicalPartition\" " +
            "start_sector=\"$startSector\" />"
    )

    fun patch(
        byteOffset: Long,
        filename: String,
        physicalPartition: Int,
        sizeInBytes: Long,
        startSector: Long,
        value: String,
        sectorSize: Int
    ): String = xml(
        "<patch SECTOR_SIZE_IN_BYTES=\"$sectorSize\" byte_offset=\"$byteOffset\" filename=\"$filename\" " +
            "physical_partition_number=\"$physicalPartition\" size_in_bytes=\"$sizeInBytes\" " +
            "start_sector=\"$startSector\" value=\"$value\" what=\"Update\" />"
    )

    fun erase(sectorSize: Int, physicalPartition: Int): String =
        xml("<erase SECTOR_SIZE_IN_BYTES=\"$sectorSize\" physical_partition_number=\"$physicalPartition\" />")

    fun setBootPartition(): String =
        xml("<setbootablestoragedrive value=\"1\" />")

    fun getStorageInfo(physicalPartition: Int = 0): String =
        xml("<getstorageinfo physical_partition_number=\"$physicalPartition\" />")

    fun getSha256Digest(
        startSector: Long,
        numPartitionSectors: Long,
        sectorSize: Int,
        physicalPartition: Int
    ): String = xml(
        "<getsha256digest SECTOR_SIZE_IN_BYTES=\"$sectorSize\" " +
            "num_partition_sectors=\"$numPartitionSectors\" physical_partition_number=\"$physicalPartition\" " +
            "start_sector=\"$startSector\" />"
    )

    val resetToEdl: String
        get() = xml("<power value=\"reset_to_edl\" DelayInSeconds=\"1\" />")

    val partialReset: String
        get() = xml("<power value=\"partial_reset\" />")

    val reqAuth: String
        get() = xml("<sig TargetName=\"req\" />")

    fun auth(signatureHex: String): String =
        xml("<sig TargetName=\"sig\" value=\"$signatureHex\" />")
}
