package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface PDLClientError {
    data object IngenNavnFunnet : PDLClientError
    data object NavnKunneIkkeAvklares : PDLClientError
    data object FødselKunneIkkeAvklares : PDLClientError
    data object AdressebeskyttelseKunneIkkeAvklares : PDLClientError
    data object ResponsManglerPerson : PDLClientError
    data object FantIkkePerson : PDLClientError
    data class NetworkError(val exception: Throwable) : PDLClientError
    data class DeserializationException(val exception: Throwable) : PDLClientError
    data class UkjentFeil(val errors: List<PdlError>) : PDLClientError
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : PDLClientError
}
