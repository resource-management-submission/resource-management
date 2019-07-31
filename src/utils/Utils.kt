package utils

import model.Job
import model.ModelParameters
import policies.coldUnitValue
import policies.hotUnitValue
import traces.Input
import traces.InputGenerationParameters

data class SequenceStats(val min: Double, val max: Double, val mean: Double, val sd: Double, val median: Double)

fun  sequenceStats(input: Iterable<Double>): SequenceStats {
    val sorted = input.sorted()
    val mean = sorted.sum() / sorted.size
    val sd = Math.sqrt(sorted.map { (it - mean) * (it - mean) }.sum() / sorted.size)
    return SequenceStats(min = input.min()!!, max = input.max()!!, mean = mean, sd = sd, median = sorted[sorted.size / 2])
}

fun <T> Collection<T>.forEachProgress(length: Int, action: (T) -> Unit) {
    val start = System.currentTimeMillis()
    this.forEachIndexed { idx, x ->
        action(x)
        val numDone = (idx + 1) * length / this.size
        print("|" + "=".repeat(numDone) + " ".repeat(length - numDone) + "|\r")
    }
    val end = System.currentTimeMillis()
    println("|" + "=".repeat(length) + "| done in ${end - start}ms")
}

fun printInputStats(input: Input, modelParameters: ModelParameters) {
    val jobsPerTimeSlot = input.timeSlots.map { it.size }.filter { it != 0 }.map(Int::toDouble)
    val processingTimes = input.timeSlots.flatMap {
        it.map{ it.initialProcessingTime * input.parameters.timeSlotDuration / (InputGenerationParameters.ONE_SECOND * 60).toDouble() }
    }
    val values = input.timeSlots.flatMap{ it.map(Job::value) }
    val deadlineCushions = input.timeSlots.flatMap { it.map { it.deadline.toDouble() / it.initialProcessingTime }}
    val huvs = input.timeSlots.flatMap { it.map{ hotUnitValue(it, modelParameters) }}
    val cuvs = input.timeSlots.flatMap { it.map{ coldUnitValue(it, modelParameters) }}

    println("Parameters: ${input.parameters}" )
    println("Total number of jobs: ${jobsPerTimeSlot.sum().toInt()}")
    println("Duration: ${input.timeSlots.size * input.parameters.timeSlotDuration / (InputGenerationParameters.ONE_SECOND * 60).toDouble()} minutes")
    println("Number of time slots: ${input.timeSlots.size}")
    println("Values:  ${sequenceStats(values)}")
    println("Jobs per time slot: ${sequenceStats(jobsPerTimeSlot)}")
    println("Processing times (minutes): ${sequenceStats(processingTimes)}")
    println("Deadline cushions: ${sequenceStats(deadlineCushions)}")
    println("Hot unit values: ${sequenceStats(huvs)}")
    println("Cold unit values: ${sequenceStats(cuvs)}")
}