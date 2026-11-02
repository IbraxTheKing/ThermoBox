package ax.ibr.thermobox.business.users.imports

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Excel workbooks (.xlsx, Office Open XML), read with the JDK alone: the zip, the shared texts and
 * the first sheet of the workbook (the first line gives the columns). Text cells, figures (written
 * without « .0 »), booleans and formulas (their last value) are read; formats and styles are not.
 *
 * Defended against hostile files: no DTD nor external entity in the XML, at most
 * [MAX_UNZIPPED] bytes unzipped in all, at most [MAX_ENTRIES] entries in the zip.
 *
 * The old Excel files (.xls, before 2007) are another format (BIFF): refused with a message
 * asking to save them as .xlsx or .csv.
 */
object XlsxReader {

    private const val MAX_UNZIPPED = 100L * 1024 * 1024
    private const val MAX_ENTRIES = 2_000

    fun read(bytes: ByteArray): ImportTable {
        val parts = unzip(bytes)
        val workbook = parts["xl/workbook.xml"]?.let(::xml) ?: throw ImportException("not_xlsx", "Not an Excel workbook (.xlsx).")
        val sheetPath = firstSheet(workbook, parts["xl/_rels/workbook.xml.rels"]?.let(::xml))
        val sheet = parts[sheetPath]?.let(::xml) ?: throw ImportException("not_xlsx", "The workbook has no sheet.")
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(xml(it)) } ?: emptyList()
        val lines = cells(sheet, shared)
        if (lines.isEmpty()) throw ImportException("empty", "The first sheet is empty.")
        if (lines.size - 1 > ImportTable.MAX_ROWS) throw ImportException("too_many_rows", "More than ${ImportTable.MAX_ROWS} lines.")
        val rows = lines.drop(1)
        return ImportTable(lines[0].second, rows.map { it.second }, rows.map { it.first })
    }

    /** The XML parts of the zip that are needed (the others are skipped, still counted). */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        var total = 0L
        var entries = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) throw ImportException("not_xlsx", "Too many parts in the workbook.")
                    val name = entry.name.removePrefix("/")
                    val wanted = name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" || name == "xl/sharedStrings.xml" ||
                        (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                    val buffer = ByteArray(64 * 1024)
                    val data = if (wanted) java.io.ByteArrayOutputStream() else null
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_UNZIPPED) throw ImportException("too_large", "The workbook is too large once unzipped.")
                        data?.write(buffer, 0, n)
                    }
                    if (data != null) out[name] = data.toByteArray()
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw ImportException("not_xlsx", "Not an Excel workbook (.xlsx).")
        }
        if (out.isEmpty()) throw ImportException("not_xlsx", "Not an Excel workbook (.xlsx).")
        return out
    }

    private fun xml(bytes: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        f.setFeature("http://xml.org/sax/features/external-general-entities", false)
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        f.isXIncludeAware = false
        f.isExpandEntityReferences = false
        return try {
            f.newDocumentBuilder().apply {
                // Errors are thrown, not printed (to the console of the server)
                setErrorHandler(object : org.xml.sax.ErrorHandler {
                    override fun warning(e: org.xml.sax.SAXParseException) = Unit
                    override fun error(e: org.xml.sax.SAXParseException) = throw e
                    override fun fatalError(e: org.xml.sax.SAXParseException) = throw e
                })
            }.parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw ImportException("not_xlsx", "The workbook is damaged.")
        }
    }

    private fun children(parent: Element, local: String): List<Element> {
        val out = mutableListOf<Element>()
        var n = parent.firstChild
        while (n != null) {
            if (n is Element && (n.localName ?: n.nodeName) == local) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun descendants(parent: Element, local: String): List<Element> {
        val list = parent.getElementsByTagNameNS("*", local)
        return List(list.length) { list.item(it) as Element }
    }

    /** Path of the first sheet in the order of the workbook (not necessarily sheet1.xml). */
    private fun firstSheet(workbook: Document, rels: Document?): String {
        val sheet = descendants(workbook.documentElement, "sheet").firstOrNull()
        val id = sheet?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        val target = rels?.let { r ->
            descendants(r.documentElement, "Relationship").firstOrNull { it.getAttribute("Id") == id }?.getAttribute("Target")
        }
        return when {
            target.isNullOrEmpty() -> "xl/worksheets/sheet1.xml"
            target.startsWith("/") -> target.removePrefix("/")
            else -> "xl/" + target.removePrefix("./")
        }
    }

    /** The texts of the cells of type `s`, rich texts joined. */
    private fun sharedStrings(doc: Document): List<String> =
        children(doc.documentElement, "si").map { si -> text(si) }

    /** The text of a `<si>` or `<is>`: its `<t>`, or the `<t>` of its runs, not the phonetic ones. */
    private fun text(e: Element): String {
        val direct = children(e, "t")
        if (direct.isNotEmpty()) return direct.joinToString("") { it.textContent }
        return children(e, "r").joinToString("") { r -> children(r, "t").joinToString("") { it.textContent } }
    }

    /** The lines that have something, with their number in the sheet (the `r` of the row). */
    private fun cells(sheet: Document, shared: List<String>): List<Pair<Int, List<String>>> {
        val data = descendants(sheet.documentElement, "sheetData").firstOrNull() ?: return emptyList()
        val lines = mutableListOf<Pair<Int, List<String>>>()
        var number = 0
        for (row in children(data, "row")) {
            number = row.getAttribute("r").toIntOrNull() ?: (number + 1)
            val line = mutableListOf<String>()
            var next = 0
            for (c in children(row, "c")) {
                val col = c.getAttribute("r").takeIf { it.isNotEmpty() }?.let(::column) ?: next
                if (col >= ImportTable.MAX_COLUMNS) throw ImportException("too_many_columns", "More than ${ImportTable.MAX_COLUMNS} columns.")
                while (line.size < col) line += ""
                line += value(c, shared)
                next = col + 1
            }
            if (line.any { it.isNotBlank() }) lines += number to line
            if (lines.size > ImportTable.MAX_ROWS + 1) throw ImportException("too_many_rows", "More than ${ImportTable.MAX_ROWS} lines.")
        }
        return lines
    }

    private fun value(c: Element, shared: List<String>): String {
        val v = children(c, "v").firstOrNull()?.textContent
        return when (c.getAttribute("t")) {
            "s" -> v?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> children(c, "is").firstOrNull()?.let(::text).orEmpty()
            "b" -> if (v == "1") "TRUE" else "FALSE"
            "str", "e" -> v.orEmpty()
            else -> v?.let(::number).orEmpty()
        }
    }

    /** 12345.0 → "12345", 1.5E3 → "1500": as a person typed them. */
    private fun number(v: String): String = try {
        BigDecimal(v.trim()).stripTrailingZeros().toPlainString()
    } catch (e: NumberFormatException) {
        v
    }

    /** "AB12" → 27 (from 0). */
    internal fun column(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }
}
