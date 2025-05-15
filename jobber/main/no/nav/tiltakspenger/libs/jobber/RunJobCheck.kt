package no.nav.tiltakspenger.libs.jobber

import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import java.net.InetAddress

data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val attributes: Attributes,
    private val isReadyKey: AttributeKey<Boolean>,
) {
    fun leaderPod(): LeaderPod {
        return LeaderPod(leaderPodLookup = leaderPodLookup)
    }

    fun isReady(): IsReady {
        return IsReady(attributes = attributes, isReadyKey = isReadyKey)
    }
}

interface RunJobCheck {
    fun shouldRun(): Boolean
}

fun List<RunJobCheck>.shouldRun(): Boolean {
    return all { it.shouldRun() }
}

data class LeaderPod(
    private val leaderPodLookup: LeaderPodLookup,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return leaderPodLookup.amITheLeader(InetAddress.getLocalHost().hostName).isRight { it }
    }
}

data class IsReady(
    private val attributes: Attributes,
    private val isReadyKey: AttributeKey<Boolean>,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return attributes.getOrNull(isReadyKey) == true
    }
}
