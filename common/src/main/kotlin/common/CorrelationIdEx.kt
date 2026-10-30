package no.nav.tiltakspenger.libs.common

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.slf4j.MDC

/**
 * Kjører [body] med en ny correlation-id under [mdcKey] i MDC.
 * Er tenkt brukt fra jobber og consumere som ikke er knyttet til Ktor; Ktor håndterer dette selv med `callIdMdc`.
 * Id-en legges i coroutine-konteksten med [MDCContext], slik at den følger coroutinen på tvers av trådbytter.
 * Når [body] er ferdig, får tråden tilbake MDC-en den hadde før, så id-en lekker ikke til andre tasks.
 * Id-en leses fra parameteren til [body] eller med `MDC.get(mdcKey)`.
 * Verdier [body] selv legger i MDC med `MDC.put`, forsvinner ved neste suspensjon med mindre de legges i konteksten med `withContext(MDCContext())`.
 */
suspend fun withCorrelationIdSuspend(
    mdcKey: String,
    body: suspend (CorrelationId) -> Unit,
) {
    val correlationId = CorrelationId.generate()
    val mdc = (MDC.getCopyOfContextMap() ?: emptyMap()) + (mdcKey to correlationId.toString())
    withContext(MDCContext(mdc)) {
        body(correlationId)
    }
}
