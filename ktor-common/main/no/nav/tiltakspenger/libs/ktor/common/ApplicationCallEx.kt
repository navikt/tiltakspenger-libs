package no.nav.tiltakspenger.libs.ktor.common

import arrow.core.Either
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import mu.KLogger
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.logging.sikkerlogg

suspend inline fun <reified T> ApplicationCall.withBody(
    logger: KLogger? = KotlinLogging.logger {},
    crossinline ifRight: suspend (T) -> Unit,
) {
    Either.catch {
        deserialize<T>(this.receiveText())
    }.onLeft {
        logger?.debug(RuntimeException("Trigger stacktrace for enklere debug")) { "Feil ved deserialisering av request. Se sikkerlogg for mer kontekst." }
        sikkerlogg.error(it) { "Feil ved deserialisering av request" }
        this.respond400BadRequest(
            melding = "Kunne ikke deserialisere request",
            kode = "ugyldig_request",
        )
    }.onRight { ifRight(it) }
}
