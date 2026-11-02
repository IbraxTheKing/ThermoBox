package ax.ibr.thermobox.business.users.imports

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * CSV files (RFC 4180), as spreadsheets and HR software export them: separator `;` (Excel in
 * French), `,` or tab, found on the first line; fields between quotes with `""` inside, line breaks
 * in them; UTF-8 (with or without BOM), else Windows-1252 (Excel « CSV » of Windows).
 */
object CsvReader {

    fun read(bytes: ByteArray): ImportTable {
        val text = decode(bytes)
        val separator = separator(text)
        val lines = parse(text, separator)
        // The columns: the first line that is not empty
        val head = lines.indexOfFirst { l -> l.any { it.isNotBlank() } }
        if (head < 0) throw ImportException("empty", "The file is empty.")
        if (lines.size - head - 1 > ImportTable.MAX_ROWS) throw ImportException("too_many_rows", "More than ${ImportTable.MAX_ROWS} lines.")
        if (lines[head].size > ImportTable.MAX_COLUMNS) throw ImportException("too_many_columns", "More than ${ImportTable.MAX_COLUMNS} columns.")
        val rows = lines.drop(head + 1)
        return ImportTable(lines[head], rows, rows.indices.map { it + head + 2 })
    }

    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    /** The separator most used on the first line that is not empty (outside quotes). */
    internal fun separator(text: String): Char {
        val counts = mutableMapOf(';' to 0, ',' to 0, '\t' to 0, '|' to 0)
        var quoted = false
        var started = false
        for (c in text) {
            if (!started && (c == '\n' || c == '\r' || c == ' ')) continue
            started = true
            if (c == '"') quoted = !quoted
            else if (!quoted && (c == '\n' || c == '\r')) break
            else if (!quoted && c in counts) counts[c] = counts[c]!! + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    internal fun parse(text: String, separator: Char): List<List<String>> {
        val lines = mutableListOf<List<String>>()
        var line = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endField() {
            line += field.toString()
            field.setLength(0)
        }
        fun endLine() {
            endField()
            lines += line
            line = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> if (field.isEmpty()) quoted = true else field.append(c)
                separator -> endField()
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endLine()
                }
                '\n' -> endLine()
                else -> field.append(c)
            }
            if (lines.size > ImportTable.MAX_ROWS + 1) throw ImportException("too_many_rows", "More than ${ImportTable.MAX_ROWS} lines.")
            i++
        }
        if (field.isNotEmpty() || line.isNotEmpty()) endLine()
        return lines
    }
}
