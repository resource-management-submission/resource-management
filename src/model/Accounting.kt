package model

import policies.hotUnitValue

class Accounting(val parameters: ModelParameters) {
    var numAllocations: Int = 0
        private set
    var totalMaintentanceTime: Int = 0
        private set
    var numJobsCompleted: Int = 0
        private set
    var totalValueCompleted: Double = 0.0
        private set
    var optimalUpperBound: Double = 0.0
        private set

    val cost: Double get() = numAllocations * parameters.allocationCost + totalMaintentanceTime * parameters.maintenanceCost
    val revenue: Double get() = totalValueCompleted
    val objective: Double get() = revenue - cost
    val objectivePercentage: Double get() = objective / optimalUpperBound

    fun chargeAllocation(machinesToAllocate: Int) {
        numAllocations += machinesToAllocate
    }

    fun chargeMaintenance(machinesToMaintain: Int) {
        totalMaintentanceTime += machinesToMaintain
    }

    fun noteArrived(arrivedJobs: Collection<MutableJob>) {
        optimalUpperBound += arrivedJobs.map {
            hotUnitValue(it, parameters) * it.initialProcessingTime
        }.filter { it > 0 }.sum()
    }

    fun accountForCompleted(completedJobs: Collection<MutableJob>) {
        totalValueCompleted += completedJobs.map(Job::value).sum()
        numJobsCompleted += completedJobs.size
    }
}