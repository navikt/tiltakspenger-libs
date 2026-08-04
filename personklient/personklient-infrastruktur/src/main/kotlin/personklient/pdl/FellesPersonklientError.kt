package no.nav.tiltakspenger.libs.personklient.pdl

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.throwableOrNull

sealed interface FellesPersonklientError {
    /**
     * Feilene som kommer fra selve HTTP-kallet mot PDL, i motsetning til de som kommer av innholdet i et svar vi forsto.
     *
     * De bærer [httpKlientError] videre urørt — med endepunkt, feilart, antall forsøk, varighet og rå request/respons.
     * Klienten logger ikke selv: den vet ikke hvilken sak eller behandling oppslaget gjaldt, og en logglinje uten den konteksten er ikke til å feilsøke på.
     * Konsumenten logger i stedet én gang, fra laget som har domenekonteksten, med `HttpKlientError.loggFeil`.
     */
    sealed interface Kallfeil : FellesPersonklientError {
        val httpKlientError: HttpKlientError
    }

    data object IngenNavnFunnet : FellesPersonklientError
    data object NavnKunneIkkeAvklares : FellesPersonklientError
    data object FødselKunneIkkeAvklares : FellesPersonklientError
    data object AdressebeskyttelseKunneIkkeAvklares : FellesPersonklientError
    data object ResponsManglerData : FellesPersonklientError
    data object FantIkkePerson : FellesPersonklientError

    /**
     * Vi fikk aldri et svar fra PDL.
     * Dekker både forsøk som ikke nådde fram (timeout, nettverksfeil) og feil før noe ble sendt; [httpKlientError] skiller dem fra hverandre.
     */
    data class NetworkError(
        override val httpKlientError: HttpKlientError,
    ) : Kallfeil {
        val exception: Throwable? = httpKlientError.throwableOrNull()
    }

    data class DeserializationException(
        override val httpKlientError: HttpKlientError.DeserializationError,
    ) : Kallfeil {
        val exception: Throwable = httpKlientError.throwable
    }

    data class UkjentFeil(val errors: List<PdlError>) : FellesPersonklientError

    /**
     * @property body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer.
     */
    data class Ikke2xx(
        override val httpKlientError: HttpKlientError.UventetStatus,
    ) : Kallfeil {
        val status: Int = httpKlientError.statusCode
        val body: String = httpKlientError.body
    }
}
