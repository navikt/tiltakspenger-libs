package no.nav.tiltakspenger.libs.jobber

import java.net.InetAddress

data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val applicationIsReady: () -> Boolean,
) {
    fun leaderPod(): LeaderPod {
        return LeaderPod(leaderPodLookup = leaderPodLookup)
    }

    fun isReady(): IsReady {
        return IsReady { applicationIsReady() }
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
    private val applicationIsReady: () -> Boolean,
) : RunJobCheck {
    override fun shouldRun(): Boolean {
        return applicationIsReady()
    }
}
