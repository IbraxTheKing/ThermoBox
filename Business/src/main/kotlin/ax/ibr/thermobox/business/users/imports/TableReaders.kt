package ax.ibr.thermobox.business.users.imports

/** Reads a list of people from a file, by its name and its first bytes. */
object TableReaders {

    /** Larger files are refused before being read. */
    const val MAX_BYTES = 10 * 1024 * 1024

    fun read(fileName: String, bytes: ByteArray): ImportTable {
        if (bytes.isEmpty()) throw ImportException("empty", "The file is empty.")
        if (bytes.size > MAX_BYTES) throw ImportException("too_large", "The file is larger than ${MAX_BYTES / 1024 / 1024} MB.")
        val name = fileName.lowercase()
        val zip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        // OLE2 compound file: the old Excel format (.xls)
        val ole = bytes.size > 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        return when {
            ole || name.endsWith(".xls") -> throw ImportException("xls", "Old Excel format (.xls): save the file as .xlsx or .csv.")
            name.endsWith(".ods") -> throw ImportException("unsupported", "OpenDocument sheet: save it as .xlsx or .csv.")
            zip || name.endsWith(".xlsx") || name.endsWith(".xlsm") -> XlsxReader.read(bytes)
            else -> CsvReader.read(bytes)
        }
    }
}
