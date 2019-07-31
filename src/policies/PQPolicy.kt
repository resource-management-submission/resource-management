package policies

import main.plots.PlotPoint
import model.Infrastructure
import model.MutableJob
import model.Policy
import traces.InputGenerationParameters
import java.util.Comparator
import java.util.TreeSet

class PQPolicy(
        comparatorName: String,
        private val cmp: Comparator<MutableJob>
) : Policy {
    override fun adjustForPlotPoint(point: PlotPoint) { }

    override val name = "PQ($comparatorName)"

    inner class Instance(private val infra: Infrastructure) : Policy.Instance {
        private val scheduledForProcessing = arrayListOf<MutableJob>()
        private val priorityQueue = TreeSet<MutableJob>(cmp.thenComparingInt { it.id })

        override fun handleArrival(arrivingJobs: Collection<MutableJob>, buffer: MutableCollection<MutableJob>) {
            val admittedJobs = arrivingJobs.filter{ hotUnitValue(it, infra.parameters) > 0}
            priorityQueue.addAll(admittedJobs)
            buffer.addAll(admittedJobs)
        }

        override fun selectForProcessing(): Collection<MutableJob> {
            scheduledForProcessing.addAll(
                    generateSequence { priorityQueue.pollFirst() }.take(infra.numMachinesAllocated)
            )
            return scheduledForProcessing.toList()
        }

        override fun handleProcessingFinished() {
            priorityQueue.addAll(scheduledForProcessing)
            scheduledForProcessing.clear()
        }

        override fun predictProcessingCapacity(): Int {
            return Math.min(infra.bufferedJobs.size, infra.parameters.maxNumMachinesAllocated)
        }

        override fun handleBufferLeave(leavingJobs: Collection<MutableJob>) {
            priorityQueue.removeAll(leavingJobs)
        }
    }

    override fun instantiatePolicy(infra: Infrastructure): Policy.Instance {
        return Instance(infra)
    }
}
