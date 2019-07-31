package main.plots

import model.Accounting
import model.ConcreteInfrastructure
import model.ModelParameters
import model.Policy
import policies.PolicyParameters
import traces.InputGenerationParameters
import traces.TaskEventsSummary
import traces.generateInput
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors


data class PlotPoint(
        val inputParams: InputGenerationParameters,
        val modelParameters: ModelParameters,
        val value: Double,
        val policyParams: PolicyParameters = PolicyParameters()
) {
    private val DECIMAL_FORMAT = DecimalFormat("0.###")
    override fun toString(): String =
            "input=$inputParams, model=$modelParameters, policy=$policyParams, where var = ${DECIMAL_FORMAT.format(value)}"
}

typealias PointGeneratingFunction = (Double, InputGenerationParameters, ModelParameters) -> PlotPoint
typealias ValueGeneratingFunction = (Accounting) -> Double

class Plotter(
        val summaries: Collection<TaskEventsSummary>,
        val baseInputParams: InputGenerationParameters,
        val baseModelParams: ModelParameters) {

    enum class RunStrategy(val func: (List<Double>) -> Double) {
        MEAN({it.average()}),
        MEDIAN({it.sorted()[it.size / 2]}),
    }

    private var xs: List<PlotPoint> = listOf()

    var numRuns = 1
    var runStrategy = RunStrategy.MEAN
    var policies: List<Policy> = listOf()
    var numThreads: Int = 1
        set(value) {
            if (value < 1) {
                throw IllegalArgumentException("Number of threads must be at least 1")
            } else {
                field = value
            }
        }

    fun setXs(values: List<Double>, xGen: PointGeneratingFunction) {
        xs = values.map { xGen(it, baseInputParams, baseModelParams) }
    }

    fun createPlot(yGen: ValueGeneratingFunction, renderers: Collection<PlotRenderer>) {
        val ys = calculateYs(yGen)
        renderers.forEach { it.plot(xs, policies, ys) }
    }

    private fun calculateYs(yGen: ValueGeneratingFunction): Data {
        val executor = Executors.newFixedThreadPool(numThreads)
        val futures = xs.associateBy({ it }) { point ->
            policies.associateBy({ it }) { policy ->
                executor.submit(Callable<Double> {
                    println("processing $point by ${policy.name}")
                    val values = generateSequence {
                        generateInput(summaries, point.inputParams, Random(point.inputParams.hashCode().toLong()))
                    }.take(numRuns).map {
                        policy.adjustForPlotPoint(point)
                        val infra = ConcreteInfrastructure(point.modelParameters, policy)
                        infra.processTimeSlots(it.timeSlots, showProgress = numThreads == 1)
                        yGen(infra.accounting)
                    }.toList()
                    runStrategy.func(values)
                })
            }
        }
        executor.shutdown()
        return futures.mapValues { it.value.mapValues { it.value.get() } }
    }
}



