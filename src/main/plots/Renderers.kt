package main.plots

import model.Policy
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat

typealias Table = Array<Array<String>>

interface PlotRenderer {
    fun plot(points: List<PlotPoint>, policies: List<Policy>, data: Data)
}

abstract class TableRendererBase(val policiesInHeader: Boolean) : PlotRenderer {
    private val format = DecimalFormat("0.000")

    abstract fun renderTable(table: Table)

    private fun generatePoliciesInHeader(points: List<PlotPoint>, policies: List<Policy>, data: Data): Table {
        val header = arrayOf("Variable") + policies.map(Policy::name)
        val body = points.map { point ->
            arrayOf(format.format(point.value)) + policies.map {
                format.format(data[point]!![it]!!)
            }
        }
        return arrayOf(header) + body
    }

    private fun generateVariablesInHeader(points: List<PlotPoint>, policies: List<Policy>, data: Data): Table {
        val header = arrayOf("Policy") + points.map { format.format(it.value) }
        val body = policies.map { policy ->
            arrayOf(policy.name) + points.map { format.format(data[it]!![policy]!!) }
        }
        return arrayOf(header) + body
    }

    override fun plot(points: List<PlotPoint>, policies: List<Policy>, data: Data) {
        val table = if (policiesInHeader) {
            generatePoliciesInHeader(points, policies, data)
        } else {
            generateVariablesInHeader(points, policies, data)
        }
        renderTable(table)
    }
}

class TSVRenderer(
        private val filePath: Path, policiesInHeader: Boolean = true
): TableRendererBase(policiesInHeader) {
    override fun renderTable(table: Table) {
        Files.newBufferedWriter(filePath).use { writer ->
            for (row in table) {
                writer.write(row.joinToString("\t"))
                writer.newLine()
            }
        }
        println("Data is saved to $filePath")
    }
}

class TableRenderer(
        private val margin: Int = 2
): TableRendererBase(policiesInHeader = false) {
    override fun renderTable(table: Table) {
        if (table.isEmpty()) {
            lastPlot = ""
            return
        }

        val maxColumnWidth = (0 until table[0].size).map { i -> table.map { row -> row[i].length }.max()!! }
        val rowToString: (Array<String>) -> String = { row ->
            row.zip(maxColumnWidth) { value, maxWidth ->
                " ".repeat(margin + maxWidth - value.length) + value + " ".repeat(margin)
            }.joinToString("")
        }

        val lines = mutableListOf<String>()
        lines.add("-".repeat(maxColumnWidth.sum() + margin * 2 * maxColumnWidth.size))
        lines.add(rowToString(table.first()))
        lines.add("-".repeat(maxColumnWidth.sum() + margin * 2 * maxColumnWidth.size))
        lines.addAll(table.drop(1).map(rowToString))
        lines.add("-".repeat(maxColumnWidth.sum() + margin * 2 * maxColumnWidth.size))

        lastPlot = lines.joinToString("\n")
    }

    var lastPlot: String? = null
        private set
}



