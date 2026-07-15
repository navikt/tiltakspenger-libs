package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import kotlin.time.Duration

data class HttpKlientResponse<out Body>(
    val statusCode: Int,
    val body: Body,
    val metadata: HttpKlientMetadata,
) {
    init {
        require(statusCode in 100..999) { "statusCode må være en tresifret HTTP-statuskode" }
    }

    /**
     * Convenience-aksessorer som peker rett inn i [metadata].
     * Lar konsumenter slippe å skrive `response.metadata.requestHeaders` osv., samtidig som vi beholder [HttpKlientMetadata] som eneste datatype og sannhetskilde for disse feltene.
     */
    val rawRequestString: String get() = metadata.rawRequestString

    /**
     * Garantert non-null på suksess: pipelinen setter alltid en lesbar respons-string når en respons finnes, og en [HttpKlientResponse] finnes bare når serveren faktisk svarte.
     * Fjerner behovet for `!!` hos konsumenter som persisterer rå request/respons (Kabal, utbetaling).
     */
    val rawResponseString: String get() = checkNotNull(metadata.rawResponseString) { "Invariant brutt: rawResponseString skal alltid være satt på en suksess-respons" }

    val requestHeaders: Map<String, List<String>> get() = metadata.requestHeaders
    val responseHeaders: Map<String, List<String>> get() = metadata.responseHeaders
    val attempts: Int get() = metadata.attempts
    val attemptDurations: List<Duration> get() = metadata.attemptDurations
    val totalDuration: Duration get() = metadata.totalDuration
}

/**
 * Domene-mapping som kan feile → typet [HttpKlientError.DeserializationError] med responsens metadata.
 * Erstatter håndbygde `Either.catch` + `DeserializationError(...)`-blokker på call sites (utbetaling/kontorhistorikk-mønsteret): mapping-feil får samme form og kontekst som klientens egne deserialiseringsfeil.
 */
fun <T, R> HttpKlientResponse<T>.tilDomene(map: (T) -> R): Either<HttpKlientError.DeserializationError, R> =
    Either.catch { map(body) }.mapLeft { e ->
        HttpKlientError.DeserializationError(
            throwable = e,
            body = rawResponseString,
            statusCode = statusCode,
            metadata = metadata,
        )
    }

/**
 * Suksess-sti-logging til sikkerlogg for kritiske klienter som skal ha sporbarhet også når kallet lykkes (datadeling-paritet).
 * Requesten er redigert (auth/sensitive headere maskert) og responsen er lesbar tekst med binær-placeholder, så innholdet er trygt for sikkerlogg.
 */
fun HttpKlientResponse<*>.loggTilSikkerlogg(melding: String) {
    // Bruker invariant-aksessorene (ikke metadata direkte): et invariant-brudd skal feile tydelig, ikke bli et stille «Response: null» i sikkerlogg.
    Sikkerlogg.info { "$melding Status: $statusCode, forsøk: $attempts. Request: $rawRequestString. Response: $rawResponseString." }
}
