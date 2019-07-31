package policies.formats

import model.MutableJob
import model.Policy
import policies.PQPolicy
import java.util.Comparator
import java.util.regex.Pattern

object PQFormat : PolicyFormat {
    override val description: String = "PQ([v/w|v/wi|-d|v|-w|v/d])"
    private val PATTERN = Pattern.compile("PQ\\((?<id>.*)\\)")

    override fun instantiatePolicy(policyName: String): Policy? {
        val matcher = PATTERN.matcher(policyName)
        if (matcher.find()) {
            return when (matcher.group("id")) {
                "v/w" -> PQPolicy("v/w", Comparator.comparingDouble<MutableJob> { it.value / it.remainingProcessingTime }.reversed())
                "v/d" -> PQPolicy("v/d", Comparator.comparingDouble<MutableJob> { it.value / (it.remainingDeadline + 1) }.reversed())
                "v/wi" -> PQPolicy("v/wi", Comparator.comparingDouble<MutableJob> { it.value / it.initialProcessingTime }.reversed())
                "-d" -> PQPolicy("-d", Comparator.comparingInt<MutableJob> { it.remainingDeadline })
                "v" -> PQPolicy("v", Comparator.comparingDouble<MutableJob> { it.value }.reversed())
                "-w" -> PQPolicy("-w", Comparator.comparingInt<MutableJob> { it.remainingProcessingTime })
                else -> null
            }
        } else {
            return null
        }
    }
}