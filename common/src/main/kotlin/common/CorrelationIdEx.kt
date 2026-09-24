package no.nav.tiltakspenger.libs.common

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC

/**
 * Denne er kun tenkt brukt fra coroutines/jobber/consumers som ikke er knyttet til Ktor.
 * Ktor håndterer dette ved hjelp av io.ktor.server.plugins.callid.callIdMdc
 *
 * Overskriver den nåværende key-value paret i MDC.
 * Fjerner keyen etter body() har kjørt ferdig.
 */
fun withCorrelationId(
    logger: KLogger,
    mdcKey: String,
    body: (CorrelationId) -> Unit,
) {
    runBlocking {
        withCorrelationIdSuspend(logger, mdcKey, body)
    }
}

/** Henter [mdcKey] fra MDC dersom den finnes eller genererer en ny og legger den på MDC. */
fun getOrCreateCorrelationIdFromThreadLocal(
    logger: KLogger,
    mdcKey: String,
): CorrelationId {
    return getCorrelationIdFromThreadLocal(logger, mdcKey)
        ?: CorrelationId.generate().also {
            MDC.put(mdcKey, it.toString())
        }
}

suspend fun withCorrelationIdSuspend(
    logger: KLogger,
    mdcKey: String,
    body: suspend (CorrelationId) -> Unit,
) {
    val correlationId = CorrelationId.generate()
    try {
        MDC.put(mdcKey, correlationId.toString())
        body(correlationId)
    } finally {
        Either.catch {
            MDC.remove(mdcKey)
        }.onLeft {
            logger.error(it) { "En ukjent feil skjedde når vi prøvde fjerne $mdcKey fra MDC." }
        }
    }
}

private fun getCorrelationIdFromThreadLocal(
    logger: KLogger,
    mdcKey: String,
): CorrelationId? {
    return MDC.get(mdcKey)?.let { CorrelationId(it) } ?: null.also {
        logger.error(RuntimeException("Genererer en stacktrace for enklere debugging.")) { "Mangler '$mdcKey' på MDC. Er dette et asynk-kall? Da må det settes manuelt, så tidlig som mulig." }
    }
}
