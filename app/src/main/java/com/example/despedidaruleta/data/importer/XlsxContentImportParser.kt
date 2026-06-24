package com.example.despedidaruleta.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.despedidaruleta.domain.model.ImportPreview
import com.example.despedidaruleta.domain.model.ImportRow
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.repository.ContentImportParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxContentImportParser(
    private val context: Context
) : ContentImportParser {
    override suspend fun parse(uri: Uri, fallbackCategory: RouletteCategory?): ImportPreview = withContext(Dispatchers.IO) {
        val fileName = resolveDisplayName(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val rows = if (fileName.endsWith(".csv", ignoreCase = true) || mimeType.contains("csv", ignoreCase = true)) {
            parseCsv(uri, fallbackCategory)
        } else {
            parseXlsx(uri, fallbackCategory)
        }
        ImportPreview(fileName = fileName, rows = rows)
    }

    private fun parseXlsx(uri: Uri, fallbackCategory: RouletteCategory?): List<ImportRow> {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val output = ByteArrayOutputStream()
                        zip.copyTo(output)
                        entries[entry.name] = output.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }?.value
            ?: return emptyList()
        val rawRows = parseSheetRows(sheetBytes, sharedStrings)
        return rawRows.toImportRows(fallbackCategory)
    }

    private fun parseCsv(uri: Uri, fallbackCategory: RouletteCategory?): List<ImportRow> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val rawRows = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { splitDelimitedLine(it) }
            .toList()
        return rawRows.toImportRows(fallbackCategory)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = newParser(bytes)
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var insideSi = false
        var insideText = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> {
                        insideSi = true
                        current.clear()
                    }
                    "t" -> if (insideSi) insideText = true
                }
                XmlPullParser.TEXT -> if (insideText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> insideText = false
                    "si" -> {
                        insideSi = false
                        values += current.toString()
                    }
                }
            }
        }
        return values
    }

    private fun parseSheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = newParser(bytes)
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var rowNumber = 0
        var cells = mutableMapOf<Int, String>()
        var cellRef = ""
        var cellType = ""
        var cellText = StringBuilder()
        var insideValue = false
        var insideInlineText = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rows.size + 1)
                        cells = mutableMapOf()
                    }
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r").orEmpty()
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellText = StringBuilder()
                    }
                    "v" -> insideValue = true
                    "t" -> if (cellType == "inlineStr") insideInlineText = true
                }
                XmlPullParser.TEXT -> {
                    if (insideValue || insideInlineText) cellText.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> insideValue = false
                    "t" -> insideInlineText = false
                    "c" -> {
                        val column = columnIndex(cellRef)
                        val raw = cellText.toString()
                        val value = if (cellType == "s") sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                        if (column >= 0 && value.isNotBlank()) cells[column] = value.trim()
                    }
                    "row" -> {
                        val maxColumn = cells.keys.maxOrNull() ?: -1
                        if (maxColumn >= 0) {
                            rows += rowNumber to (0..maxColumn).map { cells[it].orEmpty() }
                        }
                    }
                }
            }
        }
        return rows.sortedBy { it.first }.map { it.second }
    }

    private fun List<List<String>>.toImportRows(fallbackCategory: RouletteCategory?): List<ImportRow> {
        val dataRows = if (firstOrNull()?.isHeaderRow() == true) drop(1) else this
        var generatedNumber = 1
        return dataRows.mapIndexedNotNull { index, row ->
            val compact = row.map { it.trim() }
            if (compact.all { it.isBlank() }) return@mapIndexedNotNull null
            val sourceRow = index + 1 + if (firstOrNull()?.isHeaderRow() == true) 1 else 0
            val parsed = if (fallbackCategory != null) {
                val firstNumber = compact.firstOrNull()?.toIntOrNull()
                val text = if (firstNumber != null) compact.drop(1).joinToString(" ").trim() else compact.joinToString(" ").trim()
                val number = firstNumber ?: generatedNumber++
                ImportRow(sourceRow = sourceRow, category = fallbackCategory, number = number, text = text)
            } else {
                val category = compact.getOrNull(0)?.let { RouletteCategory.parse(it) }
                val number = compact.getOrNull(1)?.toIntOrNull()
                val text = compact.drop(2).joinToString(" ").trim()
                ImportRow(sourceRow = sourceRow, category = category, number = number, text = text)
            }
            parsed.validate()
        }
    }

    private fun ImportRow.validate(): ImportRow = when {
        category == null -> copy(error = "Categoria no reconocida")
        number == null || number <= 0 -> copy(error = "Numero obligatorio y mayor que cero")
        text.isBlank() -> copy(error = "Texto obligatorio")
        text.length > 400 -> copy(error = "Texto demasiado largo: maximo 400 caracteres")
        else -> this
    }

    private fun List<String>.isHeaderRow(): Boolean {
        val normalized = joinToString("|") { it.trim().lowercase() }
        return listOf("categoria", "category", "numero", "number", "texto", "text").any { normalized.contains(it) }
    }

    private fun splitDelimitedLine(line: String): List<String> {
        val delimiter = when {
            line.contains(';') -> ';'
            line.contains('\t') -> '\t'
            else -> ','
        }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result += current.toString().trim().trim('"')
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result += current.toString().trim().trim('"')
        return result
    }

    private fun columnIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isBlank()) return -1
        var value = 0
        for (char in letters) {
            value = value * 26 + (char - 'A' + 1)
        }
        return value - 1
    }

    private fun newParser(bytes: ByteArray): XmlPullParser {
        return XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment ?: "import.xlsx"
    }
}
