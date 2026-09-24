package no.nav.tiltakspenger.libs.personklient.pdl

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.throwableOrNull

/**
 * Feil fra skjermingsoppslaget.
 *
 * Hver variant bærer [httpKlientError] videre urørt — med endepunkt, feilart, antall forsøk, varighet og rå request/respons.
 * Klienten logger ikke selv: den vet hverken hvilken sak, hvilket barn eller hvilken saksbehandler kallet gjaldt, og en logglinje uten den konteksten er ikke til å feilsøke på.
 * Konsumenten logger i stedet én gang, fra laget som har domenekonteksten, med `HttpKlientError.loggFeil`.
 *
 * [exception], [body] og [status] er utledet fra [httpKlientError], for konsumenter som bare trenger den ene verdien.
 */
sealed interface FellesSkjermingError {
    val httpKlientError: HttpKlientError
    val exception: Throwable?
    val body: String?
    val status: Int?

    /**
     * Vi fikk aldri et svar fra skjerming.
     * Dekker både forsøk som ikke nådde fram (timeout, nettverksfeil) og feil før noe ble sendt (typisk at tokenhentingen kastet); [httpKlientError] skiller dem fra hverandre.
     * Navnet er beholdt fra før migreringen til `httpklient`, siden konsumentene matcher på det.
     */
    data class NetworkError(
        override val httpKlientError: HttpKlientError,
    ) : FellesSkjermingError {
        override val exception = httpKlientError.throwableOrNull()
        override val body = null
        override val status = null
    }

    data class DeserializationException(
        override val httpKlientError: HttpKlientError.DeserializationError,
    ) : FellesSkjermingError {
        override val exception = httpKlientError.throwable
        override val body = httpKlientError.body
        override val status = httpKlientError.statusCode
    }

    /**
     * @property body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer.
     */
    data class Ikke2xx(
        override val httpKlientError: HttpKlientError.UventetStatus,
    ) : FellesSkjermingError {
        override val exception = null
        override val body = httpKlientError.body
        override val status = httpKlientError.statusCode
    }
}
