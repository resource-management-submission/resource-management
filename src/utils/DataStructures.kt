package utils

import java.util.TreeSet

class PointedPQ<T>(comparator: Comparator<in T>, private val pointedIdx: Int) : Iterable<T> {
    val size get() = priorityQueue.size
    var pointed: T? = null
        private set

    private val priorityQueue = TreeSet<T>(comparator)
    private val memberCheckSet = hashSetOf<T>()

    override fun iterator(): Iterator<T> = priorityQueue.iterator()

    fun add(x: T) {
        if (memberCheckSet.add(x)) {
            priorityQueue.add(x)
            fixPointedAdd(x)
        }
    }

    fun remove(x: T): Boolean {
        if (!memberCheckSet.remove(x)) {
            return false
        }
        priorityQueue.remove(x)
        fixPointedRemove(x)
        return true
    }

    fun contains(x: T) = memberCheckSet.contains(x)

    private fun fixPointedAdd(x: T) {
        if (pointed == null) {
            if (priorityQueue.size - 1 == pointedIdx) {
                pointed = priorityQueue.last()
            }
        } else if (priorityQueue.comparator().compare(x, pointed) < 0){
            pointed = priorityQueue.lower(pointed)
        }
    }

    private fun fixPointedRemove(x: T) {
        if (pointed != null && priorityQueue.comparator().compare(x, pointed) <= 0) {
            pointed = priorityQueue.higher(pointed)
        }
    }
}

class ExpirationManager<T>(private val expirationTimeFun: (T) -> Int) {
    private val expired = mutableListOf<T>()
    private val waitingForExpiration = hashMapOf<Int, MutableList<T>>()
    private var currentTime = 0

    fun tick() {
        val expiringNow = waitingForExpiration.remove(currentTime) ?: listOf<T>()
        expiringNow.forEach {
            assert(expirationTimeFun(it) >= currentTime)
            if (expirationTimeFun(it) == currentTime) {
                expired.add(it)
            } else {
                add(it)
            }
        }

        currentTime++
    }

    fun add(element: T) {
        val expirationTime = expirationTimeFun(element)
        if (expirationTime < currentTime) {
            expired.add(element)
            return
        }
        if (!waitingForExpiration.containsKey(expirationTime)) {
            waitingForExpiration[expirationTime] = mutableListOf()
        }
        waitingForExpiration[expirationTime]?.add(element)
    }

    fun pollExpired(): Collection<T> {
        val result = expired.toList()
        expired.clear()
        return result
    }
}