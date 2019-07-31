package policies.formats

import model.Policy
import policies.NRAPPolicy

object NRAPFormat : PolicyFormat {
    override val description: String = "NRAP"

    override fun instantiatePolicy(policyName: String): Policy? =
        when (policyName) {
            "NRAP" -> NRAPPolicy()
            else -> null
        }
}