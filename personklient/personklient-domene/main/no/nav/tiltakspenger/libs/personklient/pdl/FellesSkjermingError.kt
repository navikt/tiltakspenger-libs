package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface FellesSkjermingError {
    val exception: Throwable?
    val body: String?
    val status: Int?
    data class NetworkError(
        override val exception: Throwable,
    ) : FellesSkjermingError {
        override val body = null
        override val status = null
    }
    data class DeserializationException(
        override val exception: Throwable,
        override val body: String?,
        override val status: Int?,
    ) : FellesSkjermingError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer.
     */
    data class Ikke2xx(
        override val status: Int,
        override val body: String?,
    ) : FellesSkjermingError {
        override val exception = null
    }
}
