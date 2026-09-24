package no.nav.tiltakspenger.libs.personklient.pdl.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Representerer en person som svar fra tjenesten hentPersonBolk i PDL.
 * https://pdl-docs.ansatt.nav.no/ekstern/index.html#_hentpersonbolk
 */
data class PdlPersonBolk(
    val ident: String,
    val person: PdlPerson?,
    val code: PdlPersonBolkCode,
)

enum class PdlPersonBolkCode {
    @JsonProperty("ok")
    OK,

    @JsonProperty("not_found")
    NOT_FOUND,

    @JsonProperty("bad_request")
    BAD_REQUEST,
}
