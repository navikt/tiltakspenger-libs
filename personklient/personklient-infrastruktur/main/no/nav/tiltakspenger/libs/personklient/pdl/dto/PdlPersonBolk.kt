package no.nav.tiltakspenger.libs.personklient.pdl.dto

/**
 * Representerer en person som svar fra tjenesten hentPersonBolk i PDL.
 * https://pdl-docs.ansatt.nav.no/ekstern/index.html#_hentpersonbolk
 */
data class PdlPersonBolk(
    val ident: String,
    val person: PdlPerson,
    val code: PdlPersonBolkCode,
)

enum class PdlPersonBolkCode {
    OK,
    NOT_FOUND,
    BAD_REQUEST,
}
