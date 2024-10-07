package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface FellesSkjermingError {
    val exception: Throwable?
    data class NetworkError(override val exception: Throwable) : FellesSkjermingError
    data class DeserializationException(override val exception: Throwable) : FellesSkjermingError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer.
     */
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesSkjermingError {
        override val exception = null
    }
}
