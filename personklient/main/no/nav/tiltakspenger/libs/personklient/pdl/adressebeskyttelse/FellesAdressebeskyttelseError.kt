package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

sealed interface FellesAdressebeskyttelseError {
    data class NetworkError(val exception: Throwable) : FellesAdressebeskyttelseError
    data class DeserializationException(val exception: Throwable) : FellesAdressebeskyttelseError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer og gradering.
     */
    data class Ikke2xx(
        val status: Int,
        val body: String?,
    ) : FellesAdressebeskyttelseError
}
