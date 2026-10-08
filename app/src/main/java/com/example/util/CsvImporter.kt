package com.example.util

import com.example.data.model.Item

data class SkippedRowInfo(
    val lineNumber: Int,
    val lineText: String,
    val reason: String
)

data class BulkImportPreviewResult(
    val totalLinesParsed: Int,
    val validItems: List<Item>,
    val skippedRows: List<SkippedRowInfo>
)

object CsvImporter {

    /**
     * Parses raw pasted CSV/TSV text.
     * Expected columns: Item, cost, Magaca systemka, macamil, qiimaha
     */
    fun parse(rawText: String): BulkImportPreviewResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return BulkImportPreviewResult(0, emptyList(), emptyList())
        }

        val delimiter = detectDelimiter(lines.first())
        val firstRowTokens = splitLine(lines.first(), delimiter)

        val hasHeader = isHeaderRow(firstRowTokens)
        val dataLines = if (hasHeader) lines.drop(1) else lines

        // Header mapping
        val colMap = if (hasHeader) {
            mapHeaderIndices(firstRowTokens)
        } else {
            // Default expected order: Item(0), cost(1), Magaca systemka(2), macamil(3), qiimaha(4)
            DefaultColumnMap(nameIdx = 0, costIdx = 1, sysNameIdx = 2, macamilIdx = 3, priceIdx = 4)
        }

        val validItems = mutableListOf<Item>()
        val skippedRows = mutableListOf<SkippedRowInfo>()

        dataLines.forEachIndexed { index, line ->
            val lineNumber = if (hasHeader) index + 2 else index + 1
            val tokens = splitLine(line, delimiter)

            if (tokens.isEmpty()) {
                skippedRows.add(SkippedRowInfo(lineNumber, line, "Khad maran"))
                return@forEachIndexed
            }

            val name = tokens.getOrNull(colMap.nameIdx)?.trim() ?: ""
            if (name.isEmpty()) {
                skippedRows.add(SkippedRowInfo(lineNumber, line, "Magaca dawada ayaa maqan"))
                return@forEachIndexed
            }

            val rawPrice = tokens.getOrNull(colMap.priceIdx)?.trim()?.replace("$", "")?.replace(",", ".") ?: ""
            val price = rawPrice.toDoubleOrNull()
            if (price == null || price <= 0.0) {
                skippedRows.add(SkippedRowInfo(lineNumber, line, "Qiimaha iibka ('qiimaha') ma saxna ama waa maqan yahay"))
                return@forEachIndexed
            }

            val rawCost = tokens.getOrNull(colMap.costIdx)?.trim()?.replace("$", "")?.replace(",", ".") ?: ""
            val cost = rawCost.toDoubleOrNull() ?: 0.0

            val rawMacamil = tokens.getOrNull(colMap.macamilIdx)?.trim()?.replace("$", "")?.replace(",", ".") ?: ""
            val macamil = rawMacamil.toDoubleOrNull() ?: 0.0

            val systemName = tokens.getOrNull(colMap.sysNameIdx)?.trim()?.ifBlank { null }

            validItems.add(
                Item(
                    name = name,
                    systemName = systemName,
                    cost = cost,
                    wholesalePrice = macamil,
                    price = price
                )
            )
        }

        return BulkImportPreviewResult(
            totalLinesParsed = lines.size,
            validItems = validItems,
            skippedRows = skippedRows
        )
    }

    private data class DefaultColumnMap(
        val nameIdx: Int,
        val costIdx: Int,
        val sysNameIdx: Int,
        val macamilIdx: Int,
        val priceIdx: Int
    )

    private fun detectDelimiter(firstLine: String): Char {
        val commaCount = firstLine.count { it == ',' }
        val tabCount = firstLine.count { it == '\t' }
        val semicolonCount = firstLine.count { it == ';' }
        val pipeCount = firstLine.count { it == '|' }

        return when {
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            semicolonCount > commaCount && semicolonCount > pipeCount -> ';'
            pipeCount > commaCount -> '|'
            else -> ','
        }
    }

    private fun isHeaderRow(tokens: List<String>): Boolean {
        val lowerTokens = tokens.map { it.lowercase().trim() }
        return lowerTokens.any {
            it.contains("item") || it.contains("magac") || it.contains("cost") ||
                    it.contains("qiimo") || it.contains("macamil") || it.contains("price")
        }
    }

    private fun mapHeaderIndices(headers: List<String>): DefaultColumnMap {
        var nameIdx = 0
        var costIdx = 1
        var sysNameIdx = 2
        var macamilIdx = 3
        var priceIdx = 4

        headers.forEachIndexed { i, h ->
            val clean = h.lowercase().trim()
            when {
                clean == "item" || clean.contains("magaca caadiga") || (clean.contains("magac") && !clean.contains("system")) -> {
                    nameIdx = i
                }
                clean.contains("system") || clean.contains("sistem") -> {
                    sysNameIdx = i
                }
                clean.contains("cost") || clean.contains("kharash") -> {
                    costIdx = i
                }
                clean.contains("macamil") || clean.contains("wholesale") -> {
                    macamilIdx = i
                }
                clean.contains("qiimaha") || clean.contains("qiimo") || clean.contains("price") -> {
                    priceIdx = i
                }
            }
        }

        return DefaultColumnMap(nameIdx, costIdx, sysNameIdx, macamilIdx, priceIdx)
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '\"' -> {
                    inQuotes = !inQuotes
                }
                char == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> {
                    sb.append(char)
                }
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
