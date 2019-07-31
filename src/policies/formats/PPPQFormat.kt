package policies.formats

import model.Policy
import policies.PPPQPolicy
import java.util.regex.Matcher
import java.util.regex.Pattern

object PPPQFormat : PolicyFormat {
    override val description: String = "PPPQ({b:y,c,opt,var}[:wc])"

    private abstract class PPPQPattern(val pattern: Pattern) {
        abstract fun instantiate(matcher: Matcher): PPPQPolicy
    }

    private val patterns = listOf(
        object : PPPQPattern(Pattern.compile("PPPQ\\((?<pessimistic>[0-9.]+):(?<preemption>[0-9.]+)(?<wc>(:wc)?)\\)")) {
            override fun instantiate(matcher: Matcher): PPPQPolicy = PPPQPolicy.fromParameters(
                    pessimisticFactor = matcher.group("pessimistic").toDouble(),
                    preemptionFactor = matcher.group("preemption").toDouble(),
                    workConservative = matcher.group("wc").isNotEmpty()
            )
        },
        object : PPPQPattern(Pattern.compile("PPPQ\\((?<c>[0-9.]+)(?<wc>(:wc)?)\\)")) {
            override fun instantiate(matcher: Matcher): PPPQPolicy = PPPQPolicy.fromDeadlineCushion(
                    deadlineCushion = matcher.group("c").toDouble(),
                    workConservative = matcher.group("wc").isNotEmpty()
            )
        },
        object : PPPQPattern(Pattern.compile("PPPQ\\(opt(?<wc>(:wc)?)\\)")) {
            override fun instantiate(matcher: Matcher): PPPQPolicy =
                    PPPQPolicy.inputOptimal(matcher.group("wc").isNotEmpty())
        },
        object : PPPQPattern(Pattern.compile("PPPQ\\(var(?<wc>(:wc)?)\\)")) {
            override fun instantiate(matcher: Matcher): PPPQPolicy =
                PPPQPolicy.controlled(matcher.group("wc").isNotEmpty())
        }
    )

    override fun instantiatePolicy(policyName: String): Policy? =
        patterns.map {
            val matcher = it.pattern.matcher(policyName)
            if (matcher.find()) { it.instantiate(matcher) } else { null }
        }.filterNotNull().singleOrNull()
}