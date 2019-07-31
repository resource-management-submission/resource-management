package model

import java.util.*

class ResourceManager(private val maxCapacity: Int, private val allocationTime: Int) {
    var numMachinesReady: Int = 0
        private set
    val numMachinesPending get() = pendingMachines.size
    private val pendingMachines = ArrayDeque<Machine>()

    fun tick() {
        pendingMachines.forEach { it.tick() }
        checkIfReady()
    }

    fun requestCapacity(numMachines: Int): Int {
        val delta = numMachines - (numMachinesPending + numMachinesReady)
        if (delta < 0) {
            deallocate(-delta)
        } else {
            allocate(delta)
        }
        return delta
    }

    fun allocate(numMachines: Int) {
        assert(numMachines >= 0)
        assert(numMachinesReady + pendingMachines.size + numMachines <= maxCapacity)

        pendingMachines.addAll(generateSequence { Machine(allocationTime) }.take(numMachines))
        checkIfReady()
    }

    fun deallocate(numMachines: Int) {
        assert(numMachines >= 0)
        assert(numMachines <= numMachinesReady + pendingMachines.size)
        generateSequence { pendingMachines.pollLast() } + generateSequence { numMachinesReady-- }
                .take(numMachines)
    }

    private fun checkIfReady() {
        val ready = pendingMachines.takeWhile(Machine::isReady)
        pendingMachines.removeAll(ready)
        numMachinesReady += ready.size
    }

    private class Machine(allocationTime: Int) {
        val isReady: Boolean get() = timeBeforeReady == 0
        private var timeBeforeReady: Int = allocationTime

        fun tick() {
            if (!isReady) {
                timeBeforeReady--
            }
        }
    }
}