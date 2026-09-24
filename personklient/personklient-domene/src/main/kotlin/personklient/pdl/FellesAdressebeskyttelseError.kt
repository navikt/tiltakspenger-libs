package no.nav.tiltakspenger.libs.personklient.pdl

sealed interface FellesAdressebeskyttelseError {
    val exception: Throwable?
    val body: String?
    val status: Int?

    data class NetworkError(override val exception: Throwable) : FellesAdressebeskyttelseError {
        override val body = null
        override val status = null
    }

    data class DeserializationException(
        override val body: String?,
        override val status: Int?,
        override val exception: Throwable,
    ) : FellesAdressebeskyttelseError

    /**
     * @param body Bør nok ikke logges til vanlig logg, siden den kan inneholde fødselsnummer og gradering.
     */
    data class Ikke2xx(
        override val status: Int,
        override val body: String?,
    ) : FellesAdressebeskyttelseError {
        override val exception = null
    }
}
