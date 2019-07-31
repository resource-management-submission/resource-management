package main

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.PathConverter
import main.plots.PlotCommand
import traces.*
import model.ConcreteInfrastructure
import model.ModelParameters
import model.Policy
import policies.formats.PolicyFormat
import utils.printInputStats
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

class GenericArgs {
    @Parameter(names = arrayOf("--help"), help = true)
    var help: Boolean = false

    @Parameter(names = arrayOf("--policy"), description = "Which policies to run")
    var policies: List<String> = arrayListOf("PQ(v/w)", "PQ(v)", "PQ(-w)", 
        "PQ(-d)", "PPPQ(100)", "PPPQ(100:wc)", "PPPQ(100)", "PPPQ(opt)", "PPPQ(opt:wc)")

    @Parameter(names = arrayOf("--list-policies"), description = "List of the known policies")
    var listPolicies = false
}

class InputArgs {
    @Parameter(names = arrayOf("--time-slot-duration"), description = "The duration of a time slot in seconds")
    var timeSlotDuration: Long = 1

    @Parameter(names = arrayOf("--processing-scale"), description = "The factor by which to scale processing time")
    var processingScale: Double = 1.0

    @Parameter(names = arrayOf("--value-scale"), description = "The factor by which to scale priority")
    var valueScale: Double = 1.0

    @Parameter(names = arrayOf("--value-exponent"), description = "The base for exponential values")
    var valueExponent: Double? = null

    @Parameter(names = arrayOf("--deadline-cushions"), arity = 4, description = "The multiplicative cushions for a deadline")
    var schedulingClassCushions: List<Double> = arrayListOf(1.0, 2.0, 4.0, 10.0)

    @Parameter(names = arrayOf("--deadline-cushions-scale"), description = "The multiplicative factor for easy deadline adjustments")
    var schedulingClassCushionsScale: Double = 1.0

    @Parameter(names = arrayOf("--deadline-random"), description = "Whether to generate deadlines randomly?")
    var schedulingCushionsRandomized: Boolean = false

    @Parameter(names = arrayOf("--summaries"), description = "Serialized summaries", required = true, converter = PathConverter::class)
    var summaries: Path? = null


    val inputGenerationParameters: InputGenerationParameters
        get() = InputGenerationParameters (
            timeSlotDuration = timeSlotDuration * InputGenerationParameters.ONE_SECOND,
            processingScale = processingScale,
            schedulingClassCushions = schedulingClassCushions,
            schedulingClassCushionsScale = schedulingClassCushionsScale,
            schedulingCushionsRandomized = schedulingCushionsRandomized,
            valueScale = valueScale,
            valueExpBase = valueExponent
        )

    fun readSummaries(): Collection<TaskEventsSummary> {
        return DataInputStream(Files.newInputStream(summaries)).use(DataInputStream::readSummaries)
    }
}

class ModelArgs {
    @Parameter(names = arrayOf("--vm-allocation-time"), description = "Number of seconds for VM allocation")
    var vmAllocationTime: Double = 0.0

    @Parameter(names = arrayOf("--num-machines"), description = "Maximal number of machines to allocate")
    var maxNumMachinesAllocated: Int = 0

    @Parameter(names = arrayOf("--allocation-cost"), description = "The cost of allocating a single VM")
    var allocationCost: Double = 0.0

    @Parameter(names = arrayOf("--maintenance-cost"), description = "The cost (per second) of keeping a single VM running")
    var maintenanceCost: Double = 0.0

    fun toModelParameters(inputParams: InputGenerationParameters) = ModelParameters(
            vmAllocationTime = inputParams.secondsToTimeSlots(vmAllocationTime),
            maxNumMachinesAllocated = maxNumMachinesAllocated,
            allocationCost = allocationCost,
            maintenanceCost = inputParams.perSecondToPerTimeSlot(maintenanceCost)
    )
}

@Parameters
object CreateSummaryCommand {
    val name = "create-summaries"
    fun run(inputArgs: InputArgs) {
        val summaries = generateSummaries(System.`in`.bufferedReader().lineSequence().map { TaskEvent.Companion.fromCSVLine(it)})
        DataOutputStream(Files.newOutputStream(inputArgs.summaries)).use { it.writeSummaries(summaries.values) }
    }
}

@Parameters
object RunCommand {
    val name = "run"

    fun run(inputArgs: InputArgs, modelArgs: ModelArgs, policies: List<Policy>) {
        val summaries = inputArgs.readSummaries()
        val input = generateInput(summaries, inputArgs.inputGenerationParameters)
        val modelParams = modelArgs.toModelParameters(input.parameters)

        for (policy in policies) {
            val infra = ConcreteInfrastructure(modelParams, policy)
            infra.processTimeSlots(input.timeSlots, showProgress = true)
            println("${policy.name}: " +
                    "objective = ${infra.accounting.objective} " +
                    "jobs completed = ${infra.accounting.numJobsCompleted}"
            )
        }
    }
}

@Parameters
object PrintStatsCommand {
    val name = "print-stats"

    fun run(inputArgs: InputArgs, modelArgs: ModelArgs) {
        val summaries = inputArgs.readSummaries()
        val input = generateInput(summaries, inputArgs.inputGenerationParameters)
        val modelParameters = modelArgs.toModelParameters(inputArgs.inputGenerationParameters)

        printInputStats(input, modelParameters)
    }
}


fun main(args: Array<String>) {
    val modelArgs = ModelArgs()
    val inputGenerationArgs = InputArgs()
    val genericArgs = GenericArgs()

    val cmdLine = JCommander.newBuilder()
            .addObject(modelArgs)
            .addObject(inputGenerationArgs)
            .addObject(genericArgs)
            .addCommand(PlotCommand.name, PlotCommand)
            .addCommand(CreateSummaryCommand.name, CreateSummaryCommand)
            .addCommand(RunCommand.name, RunCommand)
            .addCommand(PrintStatsCommand.name, PrintStatsCommand)
            .build()
    cmdLine.parse(*args)

    if (genericArgs.help) {
        cmdLine.usage()
        return
    }

    if (genericArgs.listPolicies) {
        println("Available policies: ${PolicyFormat.description}")
        return
    }
    val policies = genericArgs.policies.asSequence().map { name ->
            PolicyFormat.instantiatePolicy(name) ?: run {
                System.err.println("Unknown policy: $name")
                System.err.println("Available policies: ${PolicyFormat.description}")
                exitProcess(1)
            }
        }.toList()

    when (cmdLine.parsedCommand) {
        PrintStatsCommand.name -> {
            PrintStatsCommand.run(inputGenerationArgs, modelArgs)
        }
        RunCommand.name -> {
            RunCommand.run(inputGenerationArgs, modelArgs, policies)
        }
        CreateSummaryCommand.name -> {
            CreateSummaryCommand.run(inputGenerationArgs)
        }
        PlotCommand.name -> {
            PlotCommand.run(inputGenerationArgs, modelArgs, policies)
        }
    }
}
