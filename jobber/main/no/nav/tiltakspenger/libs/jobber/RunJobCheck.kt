package no.nav.tiltakspenger.libs.jobber

import java.net.InetAddress

data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
) {
    fun leaderPod(): LeaderPod {
        return LeaderPod(leaderPodLookup = leaderPodLookup)
    }
}

interface RunJobCheck {
    fun shouldRun(): Boolean
}

fun List<RunJobCheck>.shouldRun(): Boolean {
    return map { it.shouldRun() }.all { it }
}

data class LeaderPod(
    private val leaderPodLookup: LeaderPodLookup,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return leaderPodLookup.amITheLeader(InetAddress.getLocalHost().hostName).isRight { it }
    }
}
