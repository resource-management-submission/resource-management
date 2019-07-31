package traces

import model.Job
import java.text.DecimalFormat
import java.util.*

data class InputGenerationParameters(
        val timeSlotDuration: Long,
        val processingScale: Double,
        val valueScale: Double,
        val schedulingClassCushions: List<Double>,
        val schedulingClassCushionsScale: Double,
        val schedulingCushionsRandomized: Boolean,
        val valueExpBase: Double?
) {
    companion object {
        val ONE_SECOND = 1_000_000
    }

    override fun toString(): String {
        val format = DecimalFormat("0.###")

        return "(dT=${timeSlotDuration / ONE_SECOND}s" +
               ",vscale=${format.format(valueScale)}" +
               ",wscale=${format.format(processingScale)}" +
                (if (valueExpBase != null) ",vexp=${format.format(valueExpBase)}" else "") +
               ",cs=[" + schedulingClassCushions.map{format.format(it)}.joinToString(",") + "]" +
               ",css=${format.format(schedulingClassCushionsScale)}"+ ")"
    }
}

fun InputGenerationParameters.effectiveSchedulingCushion(schedulingClass: Int): Double =
        schedulingClassCushions[schedulingClass] * schedulingClassCushionsScale

fun InputGenerationParameters.secondsToTimeSlots(duration: Double): Int =
        Math.ceil((duration * InputGenerationParameters.ONE_SECOND) / timeSlotDuration).toInt()

fun InputGenerationParameters.perSecondToPerTimeSlot(value: Double): Double =
        (value / InputGenerationParameters.ONE_SECOND) * timeSlotDuration

data class Input(val parameters: InputGenerationParameters, val timeSlots: List<List<Job>>)

data class TaskEventsSummary(
        val priority: Int,
        val schedulingClass: Int,
        val firstSubmissionTime: Long,
        var lastScheduleTime: Long? = null,
        var finishTime: Long? = null,
        var wasResubmitted: Boolean = false
)

fun generateSummaries(taskEvents: Sequence<TaskEvent>): Map<TaskID, TaskEventsSummary> {
    val eventSummaries = HashMap<TaskID, TaskEventsSummary>()

    taskEvents.forEach {
        when (it.eventType) {
            EventType.SUBMIT ->
                if (!eventSummaries.contains(it.id)) {
                    eventSummaries.put(it.id, TaskEventsSummary(it.priority, it.schedulingClass, it.timestamp))
                } else {
                    eventSummaries[it.id]?.wasResubmitted = true
                }
            EventType.FINISH -> eventSummaries[it.id]?.finishTime = it.timestamp
            EventType.SCHEDULE -> eventSummaries[it.id]?.lastScheduleTime = it.timestamp

            else -> Unit
        }
    }

    println("Total number of tasks: ${eventSummaries.size}")
    val startedDuringMonitor = eventSummaries.filterValues { it.firstSubmissionTime > 0 }
    println("Number of tasks started after 0: ${startedDuringMonitor.size}")
    // Surprisingly, there are finished jobs that were never scheduled!!!
    val finished = startedDuringMonitor.filterValues { it.lastScheduleTime != null && it.finishTime != null }
    println("Number of those that have finished: ${finished.size}")
    val wasNotResubmitted = finished.filterValues { !it.wasResubmitted }
    println("Number of those that were not resubmitted: ${wasNotResubmitted.size}")

    return wasNotResubmitted
}

fun generateInput(summaries: Collection<TaskEventsSummary>, parameters: InputGenerationParameters, rnd: Random = Random()): Input {
    val result = mutableListOf<List<Job>>()

    var currentTimeSlotStart: Long = summaries.map { it.firstSubmissionTime }.min()!!
    var currentTimeSlot = mutableListOf<Job>()
    var lastID = 0

    summaries.sortedBy { it.firstSubmissionTime }.forEach {
        while (it.firstSubmissionTime >= currentTimeSlotStart + parameters.timeSlotDuration) {
            result.add(currentTimeSlot)
            currentTimeSlot = mutableListOf()
            currentTimeSlotStart += parameters.timeSlotDuration
        }

        val releaseTime = result.size
        val processingTime = Math.ceil((it.finishTime!! - it.lastScheduleTime!!) * parameters.processingScale / parameters.timeSlotDuration.toDouble())
        val value = parameters.valueScale * if (parameters.valueExpBase == null)
            (it.priority + 1.0) else Math.pow(parameters.valueExpBase, it.priority.toDouble())

        val schedulingCushion = if (!parameters.schedulingCushionsRandomized) {
            parameters.effectiveSchedulingCushion(it.schedulingClass)
        } else {
            val lower = parameters.effectiveSchedulingCushion(it.schedulingClass)
            rnd.nextDouble() * lower + lower
        }

        currentTimeSlot.add(Job(
                id = lastID++,
                releaseTime = releaseTime,
                deadline = Math.ceil(processingTime * (1 + schedulingCushion)).toInt(),
                value = value,
                initialProcessingTime = processingTime.toInt()
        ))
    }
    if (!currentTimeSlot.isEmpty()) {
        result.add(currentTimeSlot)
    }

    return Input(parameters, result)
}

