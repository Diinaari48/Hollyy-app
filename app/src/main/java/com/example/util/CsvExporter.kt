package com.example.util

import com.example.data.model.Item
import java.util.Locale

object CsvExporter {

    /**
     * Exports a list of medicines to the standardized CSV format:
     * Item,cost,Magaca systemka,qiimaha
     */
    fun exportToCsv(items: List<Item>): String {
        val sb = StringBuilder()
        sb.append("Item,cost,Magaca systemka,qiimaha\n")

        for (item in items) {
            val name = escapeCsv(item.name)
            val cost = String.format(Locale.US, "%.2f", item.cost)
            val sysName = escapeCsv(item.systemName ?: "")
            val qiimaha = String.format(Locale.US, "%.2f", item.price)

            sb.append("$name,$cost,$sysName,$qiimaha\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
