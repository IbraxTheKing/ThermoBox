package ax.ibr.thermobox.business.users.imports

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReadersTest {

    @Test
    fun `csv of Excel in French, separator, quotes and lines inside`() {
        val csv = "﻿Nom;Prénom;Courriel\r\nDupont;Jean;jean.dupont@ex.fr\r\n\"Le Gall; fils\";\"Anne \"\"Nanou\"\"\nMarie\";a@b.fr\r\n;;\r\n"
        val t = TableReaders.read("liste.csv", csv.toByteArray())
        assertEquals(listOf("Nom", "Prénom", "Courriel"), t.columns)
        assertEquals(2, t.rows.size)
        assertEquals(listOf("Le Gall; fils", "Anne \"Nanou\"\nMarie", "a@b.fr"), t.rows[1])
    }

    @Test
    fun `csv in Windows-1252 and with commas`() {
        val bytes = "nom,prénom\nHéloïse,Zoé\n".toByteArray(charset("windows-1252"))
        val t = CsvReader.read(bytes)
        assertEquals(listOf("nom", "prénom"), t.columns)
        assertEquals(listOf("Héloïse", "Zoé"), t.rows.single())
    }

    @Test
    fun `columns without a name or twice the same get one`() {
        val t = CsvReader.read("Nom,,Nom\na,b,c\n".toByteArray())
        assertEquals(listOf("Nom", "Colonne 2", "Nom (2)"), t.columns)
    }

    @Test
    fun `xlsx with shared texts, inline texts, figures and a gap`() {
        val t = TableReaders.read("personnel.xlsx", xlsx())
        assertEquals(listOf("Prénom", "Nom", "Matricule", "Courriel"), t.columns)
        assertEquals(listOf("Élodie", "Martin", "1042", ""), t.rows[0])
        assertEquals(listOf("Paul", "", "17", "p@ex.fr"), t.rows[1])
        // Their numbers in the sheet, the empty line 3 counted
        assertEquals(listOf(2, 4), t.lines)
    }

    @Test
    fun `the lines keep their number in the file, empty ones counted`() {
        val t = CsvReader.read("\n\nNom;Prénom\nDupont;Jean\n;\n\nMartin;Paul\n".toByteArray())
        assertEquals(listOf("Nom", "Prénom"), t.columns)
        assertEquals(listOf(4, 7), t.lines)
    }

    @Test
    fun `old excel, broken zips and doctypes are refused`() {
        val xls = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0)
        assertEquals("xls", assertThrows(ImportException::class.java) { TableReaders.read("vieux.xls", xls) }.code)
        assertEquals("not_xlsx", assertThrows(ImportException::class.java) { TableReaders.read("x.xlsx", "PK nothing".toByteArray()) }.code)
        val evil = zip(
            "xl/workbook.xml" to """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><workbook>&e;</workbook>""",
        )
        assertEquals("not_xlsx", assertThrows(ImportException::class.java) { XlsxReader.read(evil) }.code)
        assertEquals("empty", assertThrows(ImportException::class.java) { TableReaders.read("x.csv", ByteArray(0)) }.code)
    }

    @Test
    fun `columns of cells`() {
        assertEquals(0, XlsxReader.column("A1"))
        assertEquals(25, XlsxReader.column("Z9"))
        assertEquals(27, XlsxReader.column("AB12"))
    }

    companion object {
        private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        fun zip(vararg parts: Pair<String, String>): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { z ->
                for ((name, text) in parts) {
                    z.putNextEntry(ZipEntry(name))
                    z.write(text.toByteArray())
                    z.closeEntry()
                }
            }
            return out.toByteArray()
        }

        /** A workbook whose first sheet (by order) is sheet2.xml. */
        fun xlsx(): ByteArray = zip(
            "[Content_Types].xml" to "<Types/>",
            "xl/workbook.xml" to """<workbook xmlns="$NS" xmlns:r="$R"><sheets><sheet name="Personnel" sheetId="2" r:id="rId7"/><sheet name="Autre" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId7" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="$NS"><si><t>Prénom</t></si><si><t>Nom</t></si><si><r><t>Éло</t></r><r><t>die</t></r></si><si><t>Martin</t></si></sst>""".replace("Éло", "Élo"),
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$NS"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Mauvaise feuille</t></is></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<worksheet xmlns="$NS"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="inlineStr"><is><t>Matricule</t></is></c><c r="D1" t="str"><v>Courriel</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c><c r="C2"><v>1042.0</v></c></row>
                <row r="4"><c r="A4" t="inlineStr"><is><t>Paul</t></is></c><c r="C4"><v>1.7E1</v></c><c r="D4" t="inlineStr"><is><t>p@ex.fr</t></is></c></row>
                </sheetData></worksheet>""",
        )
    }
}
