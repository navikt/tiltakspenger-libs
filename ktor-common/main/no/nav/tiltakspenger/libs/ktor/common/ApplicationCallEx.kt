package no.nav.tiltakspenger.libs.ktor.common

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import no.nav.tiltakspenger.libs.json.deserialize
import no.nav.tiltakspenger.libs.logging.Sikkerlogg

suspend inline fun <reified T> ApplicationCall.withBody(
    logger: KLogger? = KotlinLogging.logger {},
    crossinline ifRight: suspend (T) -> Unit,
) {
    Either.catch {
        deserialize<T>(this.receiveText())
    }.onLeft {
        logger?.debug(RuntimeException("Trigger stacktrace for enklere debug")) { "Feil ved deserialisering av request. Se sikkerlogg for mer kontekst." }
        Sikkerlogg.error(it) { "Feil ved deserialisering av request" }
        this.respond400BadRequest(
            melding = "Kunne ikke deserialisere request",
            kode = "ugyldig_request",
        )
    }.onRight { ifRight(it) }
}
