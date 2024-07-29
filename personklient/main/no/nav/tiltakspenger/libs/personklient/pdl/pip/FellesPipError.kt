package no.nav.tiltakspenger.libs.personklient.pdl.pip

sealed interface FellesPipError {
    data class NetworkError(val exception: Throwable) : FellesPipError
    data class DeserializationException(val exception: Throwable) : FellesPipError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer og gradering.
     */
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesPipError
}
