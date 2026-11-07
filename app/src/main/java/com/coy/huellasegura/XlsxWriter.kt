package com.coy.huellasegura

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Escritor mínimo de archivos .xlsx (Excel): un zip con XML, sin librerías externas (Apache POI pesa mucho y no
 * se lleva bien con Android). Soporta varias hojas, texto, números, encabezado en negrita, primera fila fija,
 * filtros automáticos y ancho de columna según el contenido.
 */
object XlsxWriter {

    /**
     * Una hoja. [rows] son las filas; cada celda es un texto, un número o null (vacía).
     * Con [table] la primera fila es el encabezado de una tabla: va en negrita, queda fija y lleva filtros.
     * Las filas que empiezan con una celda de tipo [Title] se muestran como título de sección (negrita).
     */
    class Sheet(val name: String, val rows: List<List<Any?>>, val table: Boolean = true)

    /** Celda de texto en negrita (títulos de sección en la hoja de resumen). */
    class Title(val text: String)

    fun write(file: File, sheets: List<Sheet>) {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { index, sheet -> entry("xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet)) }
        }
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private const val ROOT_RELS = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL_NS/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    // 0 = normal, 1 = encabezado (negrita, fondo verde claro, borde inferior), 2 = título de sección (negrita)
    private const val STYLES = XML +
        "<styleSheet xmlns=\"$NS\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD6E6DA\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" +
        "<border><left/><right/><top/><bottom style=\"thin\"><color auto=\"1\"/></bottom><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"

    private fun contentTypes(sheets: Int): String = XML +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        (1..sheets).joinToString("") {
            "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        } + "</Types>"

    private fun workbook(sheets: List<Sheet>): String = XML +
        "<workbook xmlns=\"$NS\" xmlns:r=\"$REL_NS\"><sheets>" +
        sheets.mapIndexed { i, s -> "<sheet name=\"${escape(safeName(s.name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") +
        "</sheets></workbook>"

    private fun workbookRels(sheets: Int): String = XML +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        (1..sheets).joinToString("") {
            "<Relationship Id=\"rId$it\" Type=\"$REL_NS/worksheet\" Target=\"worksheets/sheet$it.xml\"/>"
        } +
        "<Relationship Id=\"rId${sheets + 1}\" Type=\"$REL_NS/styles\" Target=\"styles.xml\"/></Relationships>"

    private fun sheetXml(sheet: Sheet): String {
        val rows = sheet.rows
        val columns = rows.maxOfOrNull { it.size } ?: 0
        val sb = StringBuilder(XML)
        sb.append("<worksheet xmlns=\"$NS\">")
        if (sheet.table && rows.isNotEmpty()) {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        }
        if (columns > 0) {
            sb.append("<cols>")
            for (c in 0 until columns) {
                val longest = rows.maxOf { row -> textLength(row.getOrNull(c)) }
                val width = (longest + 2).coerceIn(10, 60)
                sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val ref = "${columnName(c)}${r + 1}"
                val headerStyle = sheet.table && r == 0
                when (value) {
                    null -> Unit
                    is Title -> sb.append("<c r=\"$ref\" s=\"2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value.text)}</t></is></c>")
                    is Number -> sb.append("<c r=\"$ref\"><v>${numberText(value)}</v></c>")
                    else -> sb.append(
                        "<c r=\"$ref\"${if (headerStyle) " s=\"1\"" else ""} t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value.toString())}</t></is></c>"
                    )
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (sheet.table && rows.size > 1 && columns > 0) {
            sb.append("<autoFilter ref=\"A1:${columnName(columns - 1)}${rows.size}\"/>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun textLength(value: Any?): Int = when (value) {
        null -> 0
        is Title -> value.text.length
        else -> value.toString().lines().maxOfOrNull { it.length } ?: 0
    }

    private fun numberText(n: Number): String = when (n) {
        is Int, is Long, is Short, is Byte -> n.toString()
        else -> n.toDouble().let { if (it.isNaN() || it.isInfinite()) "0" else java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }
    }

    /** A, B, … Z, AA, AB… */
    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + n % 26)
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    /** Excel no admite más de 31 caracteres ni  [ ] : * ? / \  en el nombre de una hoja. */
    private fun safeName(name: String): String = name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31).ifBlank { "Hoja" }

    /** Escapa XML y quita los caracteres de control que Excel no acepta. */
    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch < ' ' && ch != '\n' && ch != '\r' && ch != '\t' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
