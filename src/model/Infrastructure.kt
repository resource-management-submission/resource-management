package model

import utils.ExpirationManager
import utils.forEachProgress
import kotlin.collections.HashSet


class ConcreteInfrastructure(override val parameters: ModelParameters, policy: Policy) : Infrastructure {
    override var currentTimeSlot: Int = 0
        private set
    override val bufferedJobs: Collection<MutableJob> get() = _bufferedJobs
    override val numMachinesAllocated: Int
        get() = resourceManager.numMachinesReady

    val accounting = Accounting(parameters)

    private var resourceManager = ResourceManager(parameters.maxNumMachinesAllocated, parameters.vmAllocationTime)
    private val policyInstance = policy.instantiatePolicy(this)
    private val _bufferedJobs = HashSet<MutableJob>()
    private val expirationManager = ExpirationManager<MutableJob>(expirationTimeFun = { it.deadline + it.releaseTime - it.remainingProcessingTime })

    fun processTimeSlots(input: Collection<Collection<Job>>, showProgress: Boolean = false) {
        if (showProgress) {
            input.forEachProgress(20) { this.handleArrival(it.map{ it.toMutableJob(this) }) }
        } else {
            input.forEach { this.handleArrival(it.map{ it.toMutableJob(this) }) }
        }
        this.finishProcessing()
    }

    private fun handleArrival(jobs: Collection<MutableJob>) {
        doAdmission(jobs)
        doPredictionAndAllocation()
        doProcessing()
        dropDueJobs()
        currentTimeSlot++
    }

    private fun finishProcessing(maxTimeSlots: Int? = null): Boolean {
        var remainingTimeSlots = maxTimeSlots
        while (bufferedJobs.isNotEmpty() && (remainingTimeSlots == null || remainingTimeSlots > 0)) {
            handleArrival(listOf())
            if (remainingTimeSlots != null) {
                remainingTimeSlots--
            }
        }
        return bufferedJobs.isEmpty()
    }

    private fun doProcessing() {
        accounting.chargeMaintenance(resourceManager.numMachinesReady)

        val jobsToProcess = policyInstance.selectForProcessing()
        assert(jobsToProcess.size <= numMachinesAllocated)
        assert(bufferedJobs.containsAll(jobsToProcess))

        jobsToProcess.forEach(MutableJob::process)
        policyInstance.handleProcessingFinished()

        val completedJobs = jobsToProcess.filter { it.remainingProcessingTime == 0 }
        accounting.accountForCompleted(completedJobs)

        _bufferedJobs.removeAll(completedJobs)
        policyInstance.handleBufferLeave(completedJobs)

        resourceManager.tick()
    }

    private fun doAdmission(arrived: Collection<MutableJob>) {
        // TODO: make check conditional?
        //val knownJobs = _bufferedJobs + arrived
        accounting.noteArrived(arrived)
        arrived.forEach { expirationManager.add(it) }
        policyInstance.handleArrival(arrived, _bufferedJobs)
        //assert(knownJobs.containsAll(_bufferedJobs))
    }

    private fun doPredictionAndAllocation() {
        accounting.chargeAllocation(Math.max(0, resourceManager.requestCapacity(policyInstance.predictProcessingCapacity())))
    }

    private fun dropDueJobs() {
        expirationManager.tick()
        val dueJobs = expirationManager.pollExpired().filter { bufferedJobs.contains(it) }
        _bufferedJobs.removeAll(dueJobs)
        policyInstance.handleBufferLeave(dueJobs)
    }
}

