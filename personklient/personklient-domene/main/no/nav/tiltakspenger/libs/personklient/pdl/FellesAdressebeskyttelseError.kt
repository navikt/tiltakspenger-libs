package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface FellesAdressebeskyttelseError {
    val exception: Throwable?

    data class NetworkError(override val exception: Throwable) : FellesAdressebeskyttelseError
    data class DeserializationException(override val exception: Throwable) : FellesAdressebeskyttelseError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer og gradering.
     */
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesAdressebeskyttelseError {
        override val exception = null
    }
}
