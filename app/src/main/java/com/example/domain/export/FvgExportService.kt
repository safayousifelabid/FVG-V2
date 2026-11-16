package com.example.domain.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ReportExportPayload(
    val title: String,
    val organizationName: String,
    val currency: String,
    val generatedDate: String,
    val methodNotes: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val missingDataNotes: List<String> = emptyList()
)

object FvgExportService {

    fun toCsvString(payload: ReportExportPayload): String {
        val sb = StringBuilder()
        sb.appendLine("# FVG Report: ${payload.title}")
        sb.appendLine("# Organization: ${payload.organizationName} | Currency: ${payload.currency} | Date: ${payload.generatedDate}")
        sb.appendLine("# Methodology: ${payload.methodNotes}")
        if (payload.missingDataNotes.isNotEmpty()) {
            sb.appendLine("# Missing-data notes: ${payload.missingDataNotes.joinToString("; ")}")
        }
        fun esc(cell: String): String {
            val safe = cell.replace("\"", "\"\"")
            return "\"$safe\""
        }
        sb.appendLine(payload.headers.joinToString(",") { esc(it) })
        for (row in payload.rows) {
            sb.appendLine(row.joinToString(",") { esc(it) })
        }
        return sb.toString()
    }

    fun toJsonString(payload: ReportExportPayload): String {
        val root = JSONObject().apply {
            put("title", payload.title)
            put("organization", payload.organizationName)
            put("currency", payload.currency)
            put("generatedDate", payload.generatedDate)
            put("methodology", payload.methodNotes)
            put("missingDataNotes", JSONArray(payload.missingDataNotes))
            val rowsArray = JSONArray()
            for (row in payload.rows) {
                val obj = JSONObject()
                payload.headers.forEachIndexed { idx, h ->
                    obj.put(h, row.getOrElse(idx) { "" })
                }
                rowsArray.put(obj)
            }
            put("rows", rowsArray)
        }
        return root.toString(2)
    }

    fun toWorkspaceBackupJson(
        organizationName: String,
        baseCurrency: String,
        generatedAtIso: String,
        sections: Map<String, Int>,
        payloads: List<ReportExportPayload>
    ): String {
        val root = JSONObject().apply {
            put("schema", "FVG_WORKSPACE_BACKUP_V1")
            put("organization", organizationName)
            put("baseCurrency", baseCurrency)
            put("exportedAt", generatedAtIso)
            put("recordCounts", JSONObject(sections))
            val reportsArr = JSONArray()
            for (p in payloads) {
                reportsArr.put(JSONObject(toJsonString(p)))
            }
            put("datasets", reportsArr)
        }
        return root.toString(2)
    }

    fun toChatTranscriptText(
        organizationName: String,
        aiMode: String,
        generatedDate: String,
        messages: List<Pair<String, String>>
    ): String = buildString {
        appendLine("FVG — Financial Vision Guide | AI Analysis Transcript")
        appendLine("Organization: $organizationName")
        appendLine("AI Mode: $aiMode")
        appendLine("Exported: $generatedDate")
        appendLine("--------------------------------------------------")
        messages.forEachIndexed { index, (role, text) ->
            appendLine("[${index + 1}] $role:")
            appendLine(text)
            appendLine()
        }
    }

    fun writePdf(output: OutputStream, payload: ReportExportPayload) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val headerPaint = Paint().apply { color = Color.parseColor("#022A27") }
        val goldPaint = Paint().apply { color = Color.parseColor("#DDB065"); strokeWidth = 3f }
        val titlePaint = Paint().apply {
            color = Color.parseColor("#FAF2E8")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#DDB065")
            textSize = 10f
            isAntiAlias = true
        }
        val metaPaint = Paint().apply {
            color = Color.parseColor("#15423A")
            textSize = 9.5f
            isAntiAlias = true
        }
        val thPaint = Paint().apply {
            color = Color.parseColor("#022A27")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tdPaint = Paint().apply {
            color = Color.parseColor("#1D2321")
            textSize = 9f
            isAntiAlias = true
        }
        val rowBgPaint = Paint().apply { color = Color.parseColor("#FAF2E8") }

        fun drawHeader(c: Canvas) {
            c.drawRect(0f, 0f, pageWidth.toFloat(), 74f, headerPaint)
            c.drawLine(0f, 74f, pageWidth.toFloat(), 74f, goldPaint)
            c.drawText("FVG — ${payload.title}", 28f, 34f, titlePaint)
            c.drawText("${payload.organizationName}  |  ${payload.currency}  |  ${payload.generatedDate}", 28f, 56f, subtitlePaint)
        }

        drawHeader(canvas)
        var y = 98f
        canvas.drawText("Methodology: ${payload.methodNotes.take(92)}", 28f, y, metaPaint)
        y += 16f
        if (payload.missingDataNotes.isNotEmpty()) {
            canvas.drawText("Notes: ${payload.missingDataNotes.joinToString("; ").take(92)}", 28f, y, metaPaint)
            y += 16f
        }
        y += 8f

        val colCount = payload.headers.size.coerceAtLeast(1)
        val colWidth = (pageWidth - 56f) / colCount

        fun drawTableHeader(yPos: Float) {
            canvas.drawRect(24f, yPos - 14f, pageWidth - 24f, yPos + 6f, rowBgPaint)
            payload.headers.forEachIndexed { i, h ->
                canvas.drawText(h.take(18), 28f + i * colWidth, yPos, thPaint)
            }
        }

        drawTableHeader(y)
        y += 22f

        for (row in payload.rows) {
            if (y > pageHeight - 50f) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 100f
                drawTableHeader(y)
                y += 22f
            }
            row.forEachIndexed { i, cell ->
                canvas.drawText(cell.take(20), 28f + i * colWidth, y, tdPaint)
            }
            y += 18f
        }

        doc.finishPage(page)
        doc.writeTo(output)
        doc.close()
    }

    fun writeXlsx(output: OutputStream, payload: ReportExportPayload) {
        fun escXml(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun colLetter(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append(('A'.code + rem).toChar())
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        val allRows = mutableListOf<List<String>>()
        allRows.add(listOf("FVG — ${payload.title}", "Organization: ${payload.organizationName}", "Currency: ${payload.currency}", "Date: ${payload.generatedDate}"))
        allRows.add(listOf("Methodology: ${payload.methodNotes}"))
        if (payload.missingDataNotes.isNotEmpty()) {
            allRows.add(listOf("Missing-Data Notes: ${payload.missingDataNotes.joinToString("; ")}"))
        }
        allRows.add(payload.headers)
        allRows.addAll(payload.rows)

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            allRows.forEachIndexed { rIdx, row ->
                val rNum = rIdx + 1
                append("""<row r="$rNum">""")
                row.forEachIndexed { cIdx, cell ->
                    val ref = "${colLetter(cIdx)}$rNum"
                    append("""<c r="$ref" t="inlineStr"><is><t>${escXml(cell)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/package/2006/workbook" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="FVG Report" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        ZipOutputStream(output).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", wbRels)
            put("xl/worksheets/sheet1.xml", sheetXml)
        }
    }

    fun extractXlsxToCsvLines(input: InputStream): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zis.copyTo(buf)
                    entries[entry.name] = buf.toByteArray()
                }
                entry = zis.nextEntry
            }
        }
        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var inT = false
            val current = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") current.clear()
                        if (parser.name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        if (parser.name == "si") sharedStrings.add(current.toString())
                    }
                }
                event = parser.next()
            }
        }

        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
            ?: return ""

        val rowsOut = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(sheetBytes.inputStream(), "UTF-8")
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var inVal = false
        val valBuf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> cellType = parser.getAttributeValue(null, "t") ?: ""
                    "v", "t" -> {
                        inVal = true
                        valBuf.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inVal) valBuf.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> {
                        inVal = false
                        val raw = valBuf.toString()
                        val resolved = if (cellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                        } else raw
                        currentRow.add(resolved)
                    }
                    "row" -> if (currentRow.isNotEmpty()) rowsOut.add(currentRow)
                }
            }
            event = parser.next()
        }
        return rowsOut.joinToString("\n") { r -> r.joinToString(",") { it.replace(",", " ") } }
    }

    fun writeTextToUri(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for export")
    }
}
