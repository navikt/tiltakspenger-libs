package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface FellesPersonklientError {
    data object IngenNavnFunnet : FellesPersonklientError
    data object NavnKunneIkkeAvklares : FellesPersonklientError
    data object FødselKunneIkkeAvklares : FellesPersonklientError
    data object AdressebeskyttelseKunneIkkeAvklares : FellesPersonklientError
    data object ResponsManglerData : FellesPersonklientError
    data object FantIkkePerson : FellesPersonklientError
    data class NetworkError(val exception: Throwable) : FellesPersonklientError
    data class DeserializationException(val exception: Throwable) : FellesPersonklientError
    data class UkjentFeil(val errors: List<PdlError>) : FellesPersonklientError
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesPersonklientError
}
