package com.coy.huellasegura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Document
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * El escritor de Excel (.xlsx) hecho a mano: se escribe un archivo de verdad, se abre como zip y se revisa que el XML
 * sea válido y que las celdas, estilos y nombres de hoja estén como Excel los espera.
 */
class XlsxWriterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun write(vararg sheets: XlsxWriter.Sheet): File {
        val file = File(folder.root, "prueba.xlsx")
        XlsxWriter.write(file, sheets.toList())
        return file
    }

    private fun entry(file: File, name: String): String =
        ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(name)).readBytes().toString(Charsets.UTF_8) }

    private fun parse(xml: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())

    @Test
    fun `el archivo es un zip con las partes de un xlsx`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("a"))))
        val names = ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }
        listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml")
            .forEach { assertTrue("falta $it", it in names) }
    }

    @Test
    fun `todo el xml generado esta bien formado`() {
        val file = write(
            XlsxWriter.Sheet("Resumen", listOf(listOf(XlsxWriter.Title("Título"), null, 5), emptyList(), listOf("x", 1.5)), table = false),
            XlsxWriter.Sheet("Datos", listOf(listOf("Nombre", "Edad"), listOf("Luna", 3), listOf("Toby", 5))),
        )
        ZipFile(file).use { zip ->
            zip.entries().toList().forEach { parse(zip.getInputStream(it).readBytes().toString(Charsets.UTF_8)) }
        }
    }

    @Test
    fun `cada hoja queda en el libro con su nombre`() {
        val file = write(XlsxWriter.Sheet("Resumen", listOf(listOf("a"))), XlsxWriter.Sheet("Reportes", listOf(listOf("b"))))
        val workbook = entry(file, "xl/workbook.xml")
        assertTrue(workbook.contains("name=\"Resumen\""))
        assertTrue(workbook.contains("name=\"Reportes\""))
        assertEquals(2, Regex("<sheet ").findAll(workbook).count())
    }

    @Test
    fun `el nombre de la hoja no lleva caracteres que Excel rechaza ni pasa de 31`() {
        val file = write(XlsxWriter.Sheet("Perdidos: [y] encontrados / animales muy muy largos", listOf(listOf("a"))))
        val workbook = entry(file, "xl/workbook.xml")
        val name = Regex("name=\"([^\"]*)\"").find(workbook)!!.groupValues[1]
        assertTrue("nombre = $name", name.length <= 31)
        assertFalse(name.any { it in "[]:*?/\\" })
    }

    @Test
    fun `el texto con caracteres especiales se escapa`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("Perro & Gato <3 \"ok\""))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Perro &amp; Gato &lt;3 &quot;ok&quot;"))
        parse(sheet) // sigue siendo xml válido
    }

    @Test
    fun `los caracteres de control que Excel no acepta se quitan`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("a\u0000b\u0007c\td"))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertFalse(sheet.contains("\u0000"))
        assertTrue(sheet.contains("abc\td"))
        parse(sheet)
    }

    @Test
    fun `los numeros van como numeros y sin decimales de mas`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("n", "valor"), listOf("uno", 14.7), listOf("dos", 5), listOf("tres", 0.5))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("<v>14.7</v>"))
        assertTrue(sheet.contains("<v>5</v>"))
        assertTrue(sheet.contains("<v>0.5</v>"))
    }

    @Test
    fun `los encabezados de una tabla llevan estilo, fila fija y filtro`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("A", "B", "C"), listOf(1, 2, 3), listOf(4, 5, 6))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("r=\"A1\" s=\"1\""))           // encabezado con estilo
        assertTrue(sheet.contains("state=\"frozen\""))            // primera fila fija
        assertTrue(sheet.contains("<autoFilter ref=\"A1:C3\"/>")) // filtros sobre toda la tabla
    }

    @Test
    fun `la hoja de resumen no lleva filtros y sus titulos van en negrita`() {
        val file = write(XlsxWriter.Sheet("Resumen", listOf(listOf(XlsxWriter.Title("Resumen"), "x"), listOf("Total", 34)), table = false))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertFalse(sheet.contains("autoFilter"))
        assertTrue(sheet.contains("r=\"A1\" s=\"2\""))
    }

    @Test
    fun `las columnas pasan de la Z a AA sin romperse`() {
        val fila = (1..30).map { "c$it" }
        val file = write(XlsxWriter.Sheet("Ancha", listOf(fila, fila)))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("r=\"Z1\""))
        assertTrue(sheet.contains("r=\"AA1\""))
        assertTrue(sheet.contains("r=\"AD2\""))
        assertTrue(sheet.contains("<autoFilter ref=\"A1:AD2\"/>"))
    }

    @Test
    fun `las celdas vacias no se escriben`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("a", null, "c"))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("r=\"A1\""))
        assertFalse(sheet.contains("r=\"B1\""))
        assertTrue(sheet.contains("r=\"C1\""))
    }

    @Test
    fun `el ancho de cada columna sigue su contenido`() {
        val file = write(XlsxWriter.Sheet("Datos", listOf(listOf("Corto", "Una descripcion bastante larga de un reporte"))))
        val sheet = entry(file, "xl/worksheets/sheet1.xml")
        val widths = Regex("<col min=\"(\\d)\" max=\"\\d\" width=\"(\\d+)\"").findAll(sheet).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        assertTrue(widths.getValue("2") > widths.getValue("1"))
        assertTrue(widths.getValue("2") <= 60)
    }
}
