package policies

import model.*
import java.text.DecimalFormat

data class PolicyParameters(val pppqDeadlineCushion: Double = 1.0) {
    private val format = DecimalFormat("0.###")
    override fun toString(): String = "(PPPQ's c = ${format.format(pppqDeadlineCushion)})"
}

fun hotUnitValue(job: Job, params: ModelParameters): Double {
    return job.value / job.initialProcessingTime - params.maintenanceCost
}

fun coldUnitValue(job: Job, params: ModelParameters): Double {
    return (job.value - params.allocationCost) / job.initialProcessingTime - params.maintenanceCost
}

