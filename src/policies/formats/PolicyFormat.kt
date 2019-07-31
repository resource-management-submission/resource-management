package policies.formats

import model.Policy

interface PolicyFormat {
    companion object {
        private val knownFormats = listOf(NRAPFormat, PQFormat, PPPQFormat)
        fun instantiatePolicy(policyName: String): Policy? =
                knownFormats.map { it.instantiatePolicy(policyName) }.filterNotNull().singleOrNull()
        val description: String = knownFormats.map(PolicyFormat::description).joinToString(", ")
    }

    val description: String
    fun instantiatePolicy(policyName: String): Policy?
}