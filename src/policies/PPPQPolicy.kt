package policies

import main.plots.PlotPoint
import model.Infrastructure
import model.Job
import model.MutableJob
import model.Policy
import traces.InputGenerationParameters
import utils.ExpirationManager
import utils.PointedPQ
import java.util.Comparator
import java.util.TreeSet

data class PPPQParameters(val pessimisticFactor: Double = 0.0, val preemptionFactor: Double = 1.0)

abstract class PPPQPolicy(val workConservative: Boolean): Policy {
    protected abstract val parameters: PPPQParameters
    protected abstract val subName: String

    override val name get() = "PPPQ($subName" + (if (workConservative) ":wc" else "") + ")"

    override fun adjustForPlotPoint(point: PlotPoint) { }

    companion object {
        fun fromParameters(pessimisticFactor: Double, preemptionFactor: Double, workConservative: Boolean = false): PPPQPolicy {
            return DirectParametersPPPQPolicy(pessimisticFactor, preemptionFactor, workConservative)
        }

        fun inputOptimal(workConservative: Boolean = false) : PPPQPolicy {
            return OptimalPPPQPolicy(workConservative)
        }

        fun fromDeadlineCushion(deadlineCushion: Double, workConservative: Boolean = false): PPPQPolicy {
            return CushionPPPQPolicy(deadlineCushion, workConservative)
        }

        fun  controlled(workConservative: Boolean = false): PPPQPolicy {
            return ControlledPPPQPolicy(workConservative)
        }
    }

    class Instance (
            private val infra: Infrastructure,
            private val parameters: PPPQParameters,
            workConservative: Boolean
    ) : Policy.Instance {
        private val comparator: Comparator<Job> =
                Comparator.comparingDouble<Job> { hotUnitValue(it, infra.parameters) }.reversed()
                .thenComparingInt(Job::id)

        private val priorityQueue = PointedPQ<MutableJob>(comparator, infra.parameters.maxNumMachinesAllocated - 1)
        private val workConservativePQ = if (workConservative) TreeSet<MutableJob>(comparator) else null
        private val expirationManager = ExpirationManager<MutableJob>(expirationTimeFun = { pppqExpirationTime(it) })
        private val pqCandidates = TreeSet<MutableJob>(Comparator
                .comparingDouble<Job> { coldUnitValue(it, infra.parameters) }
                .reversed()
                .thenComparingInt(Job::id)

        )

        override fun handleArrival(arrivingJobs: Collection<MutableJob>, buffer: MutableCollection<MutableJob>) {
            arrivingJobs.filter { hotUnitValue(it, infra.parameters) > 0 }.forEach {
                buffer.add(it)
                pqCandidates.add(it)
                workConservativePQ?.add(it)
                expirationManager.add(it)
            }

            expirationManager.pollExpired().filter { buffer.contains(it) }.forEach {
                buffer.remove(it)
                priorityQueue.remove(it)
                workConservativePQ?.remove(it)
                pqCandidates.remove(it)
            }

            while (pqCandidates.isNotEmpty() && checkPreemptionCondition(pqCandidates.first())) {
                val candidate = pqCandidates.pollFirst()
                if (checkPessimisticCondition(candidate)) {
                    priorityQueue.add(candidate)
                    workConservativePQ?.remove(candidate)
                }
            }
        }

        override fun selectForProcessing(): Collection<MutableJob> {
            val nonWCResult = priorityQueue.take(infra.numMachinesAllocated)
            if (workConservativePQ != null) {
                return nonWCResult + workConservativePQ.take(infra.numMachinesAllocated - nonWCResult.size)
            } else {
                return nonWCResult
            }
        }

        override fun handleProcessingFinished() {
            expirationManager.tick()
        }

        override fun predictProcessingCapacity(): Int {
            val nonWCResult = Math.min(priorityQueue.size, infra.parameters.maxNumMachinesAllocated)
            if (nonWCResult < infra.numMachinesAllocated && workConservativePQ != null) {
                return Math.min(nonWCResult + workConservativePQ.size, infra.numMachinesAllocated)
            } else {
                return nonWCResult
            }
        }

        override fun handleBufferLeave(leavingJobs: Collection<MutableJob>) {
            leavingJobs.forEach {
                if (!priorityQueue.remove(it)) {
                    workConservativePQ?.remove(it)
                    pqCandidates.remove(it)
                }
            }
        }

        private fun checkPreemptionCondition(job: Job): Boolean =
                priorityQueue.pointed == null ||
                        coldUnitValue(job, infra.parameters) >= 0 &&
                        coldUnitValue(job, infra.parameters) >=
                                parameters.preemptionFactor *
                                        coldUnitValue(priorityQueue.pointed!!, infra.parameters)

        private fun checkPessimisticCondition(job: Job): Boolean =
                job.releaseTime + job.deadline >= (1 + parameters.pessimisticFactor) * job.initialProcessingTime + infra.parameters.vmAllocationTime + infra.currentTimeSlot

        private fun pppqExpirationTime(job: MutableJob) =
                job.releaseTime + job.deadline - job.remainingProcessingTime - infra.parameters.vmAllocationTime

    }


    override fun instantiatePolicy(infra: Infrastructure): Policy.Instance = Instance(infra, parameters, workConservative)
}

private class DirectParametersPPPQPolicy(pessimisticFactor: Double, preemptionFactor: Double, workConservative: Boolean) : PPPQPolicy(workConservative) {
    override val subName: String = "$pessimisticFactor:$preemptionFactor"
    override val parameters: PPPQParameters = PPPQParameters(pessimisticFactor, preemptionFactor)
}

private class CushionPPPQPolicy(val deadlineCushion: Double, workConservative: Boolean) : PPPQPolicy(workConservative) {
    override val parameters: PPPQParameters
    override val subName: String get() = "$deadlineCushion"

    init {
        val closestEntry = findParameterEntry(deadlineCushion)
        parameters = PPPQParameters(closestEntry.pessimisticFactor, closestEntry.preemptionFactor)
    }

    data class ParameterEntry(val cushion: Double, val competitiveness: Double, val preemptionFactor: Double, val pessimisticFactor: Double)

    companion object {
        private fun <U> findClosestEntry(value: Double, valueSelector: (U) -> Double, entries: List<U>): U {
            val closestIdx = entries.binarySearchBy(value + 1, selector = valueSelector)
            val upperBound = if (closestIdx >= 0) closestIdx else -closestIdx - 1
            return if (upperBound == 0 || upperBound < entries.size &&
                    Math.abs(valueSelector(entries[upperBound]) - value) < Math.abs(valueSelector(entries[upperBound - 1]) - value)) {
                entries[upperBound]
            } else {
                entries[upperBound - 1]
            }
        }

        fun findParameterEntry(deadlineCushion: Double): ParameterEntry {
            val entries = ClassLoader.getSystemClassLoader().getResourceAsStream("opt_params.tsv")
                    .bufferedReader().useLines {  lines ->
                lines.map {
                    val entry = it.split('\t').map(String::toDouble)
                    ParameterEntry(entry[0], entry[1], entry[2], entry[3])
                }.toList()
            }
            return findClosestEntry(deadlineCushion, ParameterEntry::cushion, entries)
        }
    }
}

private class OptimalPPPQPolicy(workConservative: Boolean) : PPPQPolicy(workConservative) {
    override var parameters: PPPQParameters = PPPQParameters()
    override val subName: String = "opt"

    override fun adjustForPlotPoint(point: PlotPoint) {
        super.adjustForPlotPoint(point)
        val entry = CushionPPPQPolicy.findParameterEntry(1 + point.inputParams.schedulingClassCushions.min()!! * point.inputParams.schedulingClassCushionsScale)
        parameters = PPPQParameters(entry.pessimisticFactor, entry.preemptionFactor)
    }
}

private class ControlledPPPQPolicy(workConservative: Boolean) : PPPQPolicy(workConservative) {
    override var parameters: PPPQParameters = PPPQParameters()
    override val subName: String = "var"

    override fun adjustForPlotPoint(point: PlotPoint) {
        super.adjustForPlotPoint(point)
        val entry = CushionPPPQPolicy.findParameterEntry(point.policyParams.pppqDeadlineCushion)
        parameters = PPPQParameters(entry.pessimisticFactor, entry.preemptionFactor)
    }
}
