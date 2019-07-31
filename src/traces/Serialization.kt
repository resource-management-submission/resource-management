package traces

import java.io.DataInputStream
import java.io.DataOutputStream

fun DataOutputStream.writeMaybeLong(long: Long?) {
    writeBoolean(long != null)
    if (long != null) {
        writeLong(long)
    }
}

fun DataInputStream.readMaybeLong(): Long? = if (readBoolean()) readLong() else null

fun DataOutputStream.writeSummaries(summaries: Collection<TaskEventsSummary>) {
    writeInt(summaries.size)
    summaries.forEach { writeTaskEventSummary(it) }
}

fun DataInputStream.readSummaries(): Collection<TaskEventsSummary> =
    (0 until readInt()).map { readTaskEventSummary() }

fun DataOutputStream.writeTaskEventSummary(summary: TaskEventsSummary) {
    writeInt(summary.priority)
    writeInt(summary.schedulingClass)
    writeLong(summary.firstSubmissionTime)
    writeMaybeLong(summary.lastScheduleTime)
    writeMaybeLong(summary.finishTime)
    writeBoolean(summary.wasResubmitted)
}

fun DataInputStream.readTaskEventSummary() = TaskEventsSummary(
        priority = readInt(), schedulingClass = readInt(), firstSubmissionTime = readLong(),
        lastScheduleTime = readMaybeLong(), finishTime = readMaybeLong(), wasResubmitted = readBoolean()
)

