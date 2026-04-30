package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
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
    private val localHostNameProvider: () -> String = { InetAddress.getLocalHost().hostName },
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return Either.catch { localHostNameProvider() }
            .fold(
                ifLeft = { false },
                ifRight = { localHostName ->
                    Either.catch { leaderPodLookup.amITheLeader(localHostName) }
                        .map { it.isRight { isLeader -> isLeader } }
                        .fold(
                            ifLeft = { false },
                            ifRight = { it },
                        )
                },
            )
    }
}

// TODO jah: Vurder om RunJobCheck bør kunne returnere en domenefeil i stedet for bare Boolean for bedre innsikt i hvorfor en jobb ikke kjører.
data class IsReady(
    private val attributes: Attributes,
    private val isReadyKey: AttributeKey<Boolean>,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return attributes.getOrNull(isReadyKey) == true
    }
}
