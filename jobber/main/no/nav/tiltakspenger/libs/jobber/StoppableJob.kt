package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.withCorrelationId
import java.time.Duration
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.fixedRateTimer

interface StoppableJob {
    val jobName: String

    /**
     * Idempotent. Forventer bare at ingen nye jobber blir startet. Pågående kjører ferdig.
     */
    fun stop()
}

/**
 * Starter en jobb som venter [initialDelay] før den kjører jobben i et fast [intervall].
 *Vil starte en daemon thread som betyr at VMen ikke vil vente på denne tråden for å avslutte.
 *
 * @param job Wrappes i en correlationId og en try-catch for å logge eventuelle feil.
 * @param runJobCheck Liste av RunJobCheck som må returnere true for at jobben skal kjøre. Ved tom liste, kjøres jobben alltid.
 * @param mdcCallIdKey Key for MDC hvor vi putter correlation id.
 */
fun startStoppableJob(
    jobName: String,
    initialDelay: Duration,
    intervall: Duration,
    logger: KLogger,
    mdcCallIdKey: String,
    runJobCheck: List<RunJobCheck>,
    enableDebuggingLogging: Boolean = true,
    job: (CorrelationId) -> Unit,
): StoppableJob {
    logger.info { "Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Initial delay: ${initialDelay.toMinutes()} minutt(er)" }
    return startStoppableJob(
        jobName = jobName,
        log = logger,
        mdcCallIdKey = mdcCallIdKey,
        runJobCheck = runJobCheck,
        enableDebuggingLogging = enableDebuggingLogging,
        job = job,

    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
            action = it,
        )
    }
}

/**
 * Starter en jobb som venter til et gitt tidspunkt ([startAt]) før den kjører jobben i et fast [intervall].
 * Vil starte en daemon thread som betyr at VMen ikke vil vente på denne tråden for å avslutte.
 *
 * @param job Wrappes i en correlationId og en try-catch for å logge eventuelle feil.
 * @param runJobCheck Liste av RunJobCheck som må returnere true for at jobben skal kjøre. Default er en tom liste. Ved tom liste, kjøres jobben alltid.
 */
fun startStoppableJob(
    jobName: String,
    startAt: Date,
    intervall: Duration,
    logger: KLogger,
    mdcCallIdKey: String,
    runJobCheck: List<RunJobCheck>,
    enableDebuggingLogging: Boolean = true,
    job: (CorrelationId) -> Unit,
): StoppableJob {
    logger.info { "Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Starter kl. $startAt." }
    return startStoppableJob(
        jobName = jobName,
        log = logger,
        mdcCallIdKey = mdcCallIdKey,
        runJobCheck = runJobCheck,
        job = job,
        enableDebuggingLogging = enableDebuggingLogging,
    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = startAt,
            period = intervall.toMillis(),
            action = it,
        )
    }
}

private fun startStoppableJob(
    jobName: String,
    log: KLogger,
    mdcCallIdKey: String,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> Unit,
    enableDebuggingLogging: Boolean,
    scheduleJob: (TimerTask.() -> Unit) -> Timer,
): StoppableJob {
    return scheduleJob {
        Either.catch {
            if (runJobCheck.shouldRun()) {
                if (enableDebuggingLogging) {
                    log.debug { "Kjører skeduleringsjobb '$jobName'." }
                }
                withCorrelationId(log, mdcCallIdKey) { job(it) }
                if (enableDebuggingLogging) {
                    log.debug { "Fullførte skeduleringsjobb '$jobName'." }
                }
            } else {
                if (enableDebuggingLogging) {
                    log.debug { "Skeduleringsjobb '$jobName' kjører ikke pga. startKriterier i runJobCheck. Eksempelvis er vi ikke leader pod." }
                }
            }
        }.onLeft {
            log.error(it) { "Skeduleringsjobb '$jobName' feilet med stacktrace:" }
        }
    }.let { timer ->
        object : StoppableJob {
            override val jobName = jobName
            override fun stop() {
                Either.catch {
                    timer.cancel()
                }.onRight {
                    log.info {
                        "Skeduleringsjobb '$jobName' stoppet. Pågående kjøringer ferdigstilles."
                    }
                }.onLeft {
                    log.error(it) { "Skeduleringsjobb '$jobName': Feil ved kall til stop()/kanseller Timer." }
                }
            }
        }
    }
}
