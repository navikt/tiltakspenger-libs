package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.CorrelationId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Generisk task executor som kjører tasks med gitt intervall.
 * Tanken er at den kan brukes til å kjøre tasks som genererer meldekort, sender brev, etc.
 *
 * Garantier:
 *  1. Tasks innenfor én syklus kjører seriellt i listerekkefølgen. Neste task starter ikke før forrige er ferdig
 *     (vi bruker [runBlocking] rundt hver task slik at tråden blokkerer til den er ferdig).
 *  2. Skedulereren starter ikke en ny syklus før forrige syklus er ferdig. Dette er garantert av at
 *     [no.nav.tiltakspenger.libs.jobber.startStoppableJob] internt bruker en enkelt-trådet [java.util.Timer]
 *     som kun kan eksekvere én [java.util.TimerTask] om gangen.
 *  3. Det går minst `intervall` mellom starten av to sykluser (fixed-delay scheduling via [java.util.Timer.schedule]).
 *     Hvis en syklus tar lengre enn `intervall`, starter neste syklus så snart forrige er ferdig.
 *  4. En feil i én task stopper ikke senere tasks i samme syklus eller fremtidige sykluser. Hver task
 *     wrappes i [Either.catch] og loggføres som feil.
 */
class TaskExecutor(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            runCheckFactory: RunCheckFactory,
            tasks: List<suspend (CorrelationId) -> Any?>,
            initialDelay: Duration = 1.minutes,
            intervall: Duration = 10.seconds,
            /** Ref callIdMdc(CALL_ID_MDC_KEY) i KtorSetup.kt */
            mdcCallIdKey: String,
            runJobCheck: List<RunJobCheck> = listOf(runCheckFactory.isReady(), runCheckFactory.leaderPod()),
            jobName: String = "taskExecutor",
            /** Denne kjører så ofte at vi ønsker ikke bli spammet av logging. */
            enableDebuggingLogging: Boolean = false,
            logger: KLogger = KotlinLogging.logger { },
        ): TaskExecutor {
            return TaskExecutor(
                startStoppableJob(
                    jobName = jobName,
                    initialDelay = initialDelay.toJavaDuration(),
                    intervall = intervall.toJavaDuration(),
                    logger = logger,
                    mdcCallIdKey = mdcCallIdKey,
                    runJobCheck = runJobCheck,
                    enableDebuggingLogging = enableDebuggingLogging,
                    job = { correlationId ->
                        tasks.forEach { task ->
                            // Vi ønsker ikke at en jobb skal spenne ben for andre jobber.
                            // Hvis du endrer denne til at hver jobb kjøres parallellt:
                            //   1. Pass på at vi har nok tilgjengelige databasesesjoner
                            //   2. Nå har du åpnet for at databasekallene kan gå i beina på hverandre, så du må gruppere ting som leser/muterer de samme verdiene så de ikke kjører parallellt, eventuelt passe på å bruke databaselåser.
                            Either.catch {
                                runBlocking { task(correlationId) }
                            }.mapLeft { throwable ->
                                logger.error(throwable) { "Feil ved kjøring av task. correlationId: $correlationId" }
                            }
                        }
                    },
                ),
            )
        }
    }
}
