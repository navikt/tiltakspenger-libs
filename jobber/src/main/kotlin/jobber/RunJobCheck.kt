package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import java.net.InetAddress

/**
 * Bygger [RunJobCheck]-ene som avgjør om en skeduleringsjobb skal kjøre.
 * `isReady` er et predikat som sier om appen er klar til å kjøre jobber.
 * Predikatet holdes utenfor jobber-modulen slik at modulen ikke trenger å kjenne til hvordan readiness er implementert (f.eks. Ktor-attributter).
 */
data class RunCheckFactory(
    private val leaderPodLookup: LeaderPodLookup,
    private val isReady: () -> Boolean,
) {
    fun leaderPod(): LeaderPod {
        return LeaderPod(leaderPodLookup = leaderPodLookup)
    }

    fun isReady(): IsReady {
        return IsReady(isReady = isReady)
    }
}

/**
 * En sjekk som avgjør om en skeduleringsjobb skal kjøre akkurat nå.
 * [Either.Right] betyr at jobben kan kjøre.
 * [Either.Left] betyr at jobben hoppes over, med en [JobbSkalIkkeKjøre] som forklarer hvorfor.
 */
interface RunJobCheck {
    fun shouldRun(): Either<JobbSkalIkkeKjøre, Unit>
}

/**
 * Kjører sjekkene i rekkefølge og stopper ved første sjekk som sier at jobben ikke skal kjøre.
 * Returnerer [Either.Right] kun dersom alle sjekkene godtar kjøring.
 */
fun List<RunJobCheck>.shouldRun(): Either<JobbSkalIkkeKjøre, Unit> {
    forEach { check ->
        check.shouldRun().onLeft { return it.left() }
    }
    return Unit.right()
}

/**
 * Årsak til at en skeduleringsjobb ikke skal kjøre akkurat nå.
 * Brukes for innsikt/logging i stedet for en naken `false`.
 */
sealed interface JobbSkalIkkeKjøre {
    /** Appen er ikke klar (readiness-predikatet er `false`). */
    data object IkkeKlar : JobbSkalIkkeKjøre

    /** Denne poden er ikke leder. */
    data object IkkeLederPod : JobbSkalIkkeKjøre

    /** Oppslag mot leader elector returnerte en feil. */
    data class LederpodOppslagFeilet(val feil: LeaderPodLookupFeil) : JobbSkalIkkeKjøre

    /** Oppslag mot leader elector kastet en exception. */
    data class LederpodOppslagKastet(val throwable: Throwable) : JobbSkalIkkeKjøre

    /** Oppslag av lokalt vertsnavn kastet en exception. */
    data class VertsnavnOppslagKastet(val throwable: Throwable) : JobbSkalIkkeKjøre
}

data class LeaderPod(
    private val leaderPodLookup: LeaderPodLookup,
    private val localHostNameProvider: () -> String = { InetAddress.getLocalHost().hostName },
) : RunJobCheck {
    override fun shouldRun(): Either<JobbSkalIkkeKjøre, Unit> {
        val localHostName = Either.catch { localHostNameProvider() }
            .getOrElse { return JobbSkalIkkeKjøre.VertsnavnOppslagKastet(it).left() }
        val erLeder = Either.catch { leaderPodLookup.amITheLeader(localHostName) }
            .getOrElse { return JobbSkalIkkeKjøre.LederpodOppslagKastet(it).left() }
            .getOrElse { feil -> return JobbSkalIkkeKjøre.LederpodOppslagFeilet(feil).left() }
        return if (erLeder) Unit.right() else JobbSkalIkkeKjøre.IkkeLederPod.left()
    }
}

data class IsReady(
    private val isReady: () -> Boolean,
) : RunJobCheck {
    override fun shouldRun(): Either<JobbSkalIkkeKjøre, Unit> {
        return if (isReady()) Unit.right() else JobbSkalIkkeKjøre.IkkeKlar.left()
    }
}
