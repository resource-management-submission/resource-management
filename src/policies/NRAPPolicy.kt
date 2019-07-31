package policies

import main.plots.PlotPoint
import model.Infrastructure
import model.MutableJob
import model.Policy
import traces.InputGenerationParameters

class NRAPPolicy : Policy {
    override val name: String = "NRAP"

    class Instance(private val infra: Infrastructure) : Policy.Instance {

        override fun handleArrival(arrivingJobs: Collection<MutableJob>, buffer: MutableCollection<MutableJob>) {
            arrivingJobs.filter { hotUnitValue(it, infra.parameters) > 0 }.forEach {
                buffer.add(it)
            }
        }

        override fun selectForProcessing(): Collection<MutableJob> {
            return infra.bufferedJobs.take(infra.numMachinesAllocated)
        }
        override fun handleProcessingFinished() { }

        override fun predictProcessingCapacity(): Int {
            return Math.min(infra.bufferedJobs.size, infra.parameters.maxNumMachinesAllocated)
        }

        override fun handleBufferLeave(leavingJobs: Collection<MutableJob>) { }
    }

    override fun adjustForPlotPoint(point: PlotPoint) { }

    override fun instantiatePolicy(infra: Infrastructure): Policy.Instance {
        return Instance(infra)
    }
}

