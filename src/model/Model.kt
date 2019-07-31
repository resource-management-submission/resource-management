package model

import main.plots.PlotPoint
import java.text.DecimalFormat

open class Job(
        val id: Int,
        val releaseTime: Int,
        val deadline: Int,
        val value: Double,
        val initialProcessingTime: Int
) {
    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Job && id == other.id
    }
}

fun Job.toMutableJob(infra: Infrastructure): MutableJob {
    return MutableJob(this, infra)
}

class MutableJob(job: Job, val infra: Infrastructure) : Job(job.id, job.releaseTime, job.deadline, job.value, job.initialProcessingTime) {
    var remainingProcessingTime: Int = initialProcessingTime
        private set
    val remainingDeadline: Int get() = releaseTime + deadline - infra.currentTimeSlot


    fun process() {
        assert(remainingProcessingTime > 0)
        remainingProcessingTime--
    }
}

data class ModelParameters (
        val vmAllocationTime: Int,
        val maxNumMachinesAllocated: Int,
        val allocationCost: Double,
        val maintenanceCost: Double
) {
    override fun toString(): String {
        val format = DecimalFormat("0.###")
        return "(f=${vmAllocationTime}dT,B=$maxNumMachinesAllocated," +
            "a=${format.format(allocationCost)},m=${format.format(maintenanceCost)}/dT)"
    }
}

fun ModelParameters.rescaleTimeSlot(scale: Double) = ModelParameters(
        vmAllocationTime = (vmAllocationTime / scale).toInt(),
        maxNumMachinesAllocated = maxNumMachinesAllocated,
        allocationCost = allocationCost,
        maintenanceCost = maintenanceCost * scale
)

interface Infrastructure {
    val parameters: ModelParameters

    val numMachinesAllocated: Int
    val currentTimeSlot: Int
    val bufferedJobs: Collection<MutableJob>
}

interface Policy {
    interface Instance {
        fun handleArrival(arrivingJobs: Collection<MutableJob>, buffer: MutableCollection<MutableJob>)
        fun selectForProcessing(): Collection<MutableJob>
        fun handleProcessingFinished()
        fun predictProcessingCapacity(): Int

        fun handleBufferLeave(leavingJobs: Collection<MutableJob>)
    }

    val name: String
    fun adjustForPlotPoint(point: PlotPoint)
    fun instantiatePolicy(infra: Infrastructure): Instance
}
