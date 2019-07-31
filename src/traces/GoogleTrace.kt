package traces

data class TaskID(val jobID: Long, val taskIdx: Int)

enum class EventType {
    SUBMIT, SCHEDULE, EVICT, FAIL, FINISH, KILL, LOST, UPDATE_PENDING, UPDATE_RUNNING
}

class TaskEvent(
        val timestamp: Long,
        val id: TaskID,
        val eventType: EventType,
        val schedulingClass: Int,
        val priority: Int
) {
    companion object {
        fun fromCSVLine(line: String): TaskEvent {
            val fields = line.split(",")
            return TaskEvent(
                    fields[0].toLong(), TaskID(fields[2].toLong(), fields[3].toInt()),
                    EventType.values()[fields[5].toInt()], fields[7].toInt(), fields[8].toInt())
        }
    }
}
