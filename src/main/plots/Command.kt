package main.plots

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.PathConverter
import main.InputArgs
import main.ModelArgs
import model.*
import policies.PolicyParameters
import traces.InputGenerationParameters
import traces.perSecondToPerTimeSlot
import traces.secondsToTimeSlots
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.DecimalFormat
import kotlin.system.exitProcess
typealias Data = Map<PlotPoint, Map<Policy, Double>>

private val DECIMAL_FORMAT = DecimalFormat("0.###")

@Parameters(commandDescription = "Plot some data")
object PlotCommand {
    val name = "plot"

    @Parameter(names = arrayOf("--variable", "-x"), required = true)
    private var variableName: String? = null

    @Parameter(names = arrayOf("--value", "-y"), required = true)
    private var valueName: String? = null

    @Parameter(names = arrayOf("--range"), description = "range start:end:num_segments[:exp]", required = true, converter = RangeConverter::class)
    private var range: Range = Range()

    @Parameter(names = arrayOf("--dir"), description = "directory, where to save plots", converter = PathConverter::class)
    private var dir: Path = Paths.get("plots")

    @Parameter(names = arrayOf("--runs-per-input"), description = "Number of runs for each input parameter (random)")
    private var runsPerInput: Int = 1

    @Parameter(names = arrayOf("--num-threads"), description = "the number of threads to use")
    private var numThreads: Int = 1

    @Parameter(names = arrayOf("--run-strategy"), description = "How to combine multiple runs")
    private var runStrategy: String = "mean"

    fun run(inputArgs: InputArgs, modelArgs: ModelArgs, policies: List<Policy>) {
        // initialize these here for early error detection
        val xGen = getPointGeneratingFunction()
        val yGen = getValueGeneratingFunction()

        val summaries = inputArgs.readSummaries()
        val baseInputParams = inputArgs.inputGenerationParameters
        val baseModelParams = modelArgs.toModelParameters(baseInputParams)

        val plotter = Plotter(summaries, baseInputParams, baseModelParams)
        plotter.setXs(range.toList(), xGen)
        plotter.policies = policies
        plotter.numThreads = numThreads
        plotter.numRuns = runsPerInput
        plotter.runStrategy = Plotter.RunStrategy.valueOf(runStrategy.toUpperCase())

        val tableRenderer = TableRenderer(margin = 2)
        val baseFileName = generateName(inputArgs, modelArgs)

        if (!Files.exists(dir)) {
            Files.createDirectory(dir)
        }

        plotter.createPlot(yGen, listOf(
                TSVRenderer(dir.resolve("$baseFileName.t.tsv"), policiesInHeader = false),
                TSVRenderer(dir.resolve("$baseFileName.tsv"), policiesInHeader = true),
                tableRenderer
        ))

        println(tableRenderer.lastPlot)
    }

    private fun getPointGeneratingFunction(): PointGeneratingFunction =
        if (ALL_VARIABLES.contains(variableName)) {
            ALL_VARIABLES[variableName]!!
        } else {
            System.err.println("Unknown variable name: $variableName")
            System.err.println("Available variables: " + ALL_VARIABLES.keys.joinToString(", "))
            exitProcess(1)
        }

    private fun getValueGeneratingFunction(): ValueGeneratingFunction =
        if (ALL_VALUES.containsKey(valueName)) {
            ALL_VALUES[valueName]!!
        } else {
            System.err.println("Unknown value name: $valueName")
            System.err.println("Available values: " + ALL_VALUES.keys.joinToString(", "))
            exitProcess(1)
        }

    private val ALL_VALUES = mapOf<String, ValueGeneratingFunction> (
            "objective" to Accounting::objective,
            "objective-percentage" to Accounting::objectivePercentage
    )

    private val ALL_VARIABLES = mapOf<String, PointGeneratingFunction> (
            "value-scale" to { x, input, model -> PlotPoint(input.copy(valueScale = x), model, x) },
            "allocation-cost" to { x, input, model -> PlotPoint(input, model.copy(allocationCost = x), x) },
            "maintenance-cost" to { x, input, model -> PlotPoint(input, model.copy(
                    maintenanceCost = input.perSecondToPerTimeSlot(x)
            ), x) },
            "num-machines" to { x, input, model -> PlotPoint(input, model.copy(maxNumMachinesAllocated = x.toInt()), x) },
            "time-slot-duration" to { x, input, model -> PlotPoint(
                    input.copy(timeSlotDuration = x.toLong() * InputGenerationParameters.ONE_SECOND),
                    model.rescaleTimeSlot(x * InputGenerationParameters.ONE_SECOND / input.timeSlotDuration), x
            )},
            "deadline-scale" to { x, input, model -> PlotPoint(input.copy(schedulingClassCushionsScale = x), model, x) },
            "vm-allocation-time" to { x, input, model -> PlotPoint(input, model.copy(
                vmAllocationTime = input.secondsToTimeSlots(x)
            ), x) },
            "pppq-c" to { x, input, model -> PlotPoint(input, model, x, policyParams = PolicyParameters(pppqDeadlineCushion = x))}
    )

    private fun generateName(inputArgs: InputArgs, modelArgs: ModelArgs): String {
        val entryList = mutableListOf<String>()
        entryList.add(inputArgs.summaries?.fileName.toString())
        entryList.add("dt${inputArgs.timeSlotDuration}")
        entryList.add("vs${DECIMAL_FORMAT.format(inputArgs.valueScale)}")
        entryList.add("ws${DECIMAL_FORMAT.format(inputArgs.processingScale)}")
        if (inputArgs.valueExponent != null) {
            entryList.add("vexp${DECIMAL_FORMAT.format(inputArgs.valueExponent!!)}")
        }
        entryList.addAll(inputArgs.schedulingClassCushions.zip('a'..'d').map{
            (v, i) -> "s$i${DECIMAL_FORMAT.format(v)}"
        })
        entryList.add("ss${DECIMAL_FORMAT.format(inputArgs.schedulingClassCushionsScale)}")
        if (inputArgs.schedulingCushionsRandomized) {
            entryList.add("ssrnd$runsPerInput")
        }
        entryList.add("f${DECIMAL_FORMAT.format(modelArgs.vmAllocationTime)}")
        entryList.add("B${modelArgs.maxNumMachinesAllocated}")
        entryList.add("a${DECIMAL_FORMAT.format(modelArgs.allocationCost)}")
        entryList.add("m${DECIMAL_FORMAT.format(modelArgs.maintenanceCost)}")
        entryList.add("$valueName($variableName[${range.toString().replace(':', '_')}])")
        return entryList.joinToString("_")
    }
}

private data class Range(val start: Double = 0.0, val end: Double = 0.0, val numSegments: Int = 1, val exponential: Boolean = false) {
    init {
        if (start > end) {
            throw IllegalArgumentException("start must not be greater than end")
        }
        if (numSegments <= 0) {
            throw IllegalArgumentException("number of segments must be greater than zero")
        }
    }

    override fun toString(): String =
            listOf(start, end, numSegments).map { DECIMAL_FORMAT.format(it) }.joinToString(":") +
                    if (exponential) ":exp" else ""

    fun toList(): List<Double> =
            (0..numSegments).map {
                if (exponential) {
                    start * Math.pow(Math.pow(end / start, 1.0 / numSegments), it.toDouble())
                } else {
                    start + it * (end - start) / numSegments
                }
            }
}

private class RangeConverter : IStringConverter<Range> {
    override fun convert(str: String): Range {
        val values = str.split(":")
        return Range(
                start = values[0].toDouble(), end = values[1].toDouble(), numSegments = values[2].toInt(),
                exponential = values.size == 4 && values[3] == "exp"
        )
    }
}
