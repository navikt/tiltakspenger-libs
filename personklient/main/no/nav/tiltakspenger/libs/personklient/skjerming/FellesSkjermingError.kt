package no.nav.tiltakspenger.libs.personklient.skjerming

sealed interface FellesSkjermingError {
    data class NetworkError(val exception: Throwable) : FellesSkjermingError
    data class DeserializationException(val exception: Throwable) : FellesSkjermingError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer.
     */
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesSkjermingError
}
