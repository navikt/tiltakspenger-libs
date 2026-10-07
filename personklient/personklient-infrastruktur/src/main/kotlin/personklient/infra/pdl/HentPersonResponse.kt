package no.nav.tiltakspenger.libs.personklient.infra.pdl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.personklient.infra.pdl.FellesPersonklientError.FantIkkePerson
import no.nav.tiltakspenger.libs.personklient.infra.pdl.FellesPersonklientError.ResponsManglerData
import no.nav.tiltakspenger.libs.personklient.infra.pdl.FellesPersonklientError.UkjentFeil
import tools.jackson.databind.JsonNode

internal const val FANT_IKKE_PERSON = "Fant ikke person"

internal data class HentPersonResponse(
    val data: JsonNode? = null,
    val errors: List<PdlError> = emptyList(),
) {
    fun extractData(): Either<FellesPersonklientError, String> {
        return if (this.errors.isNotEmpty()) {
            if (errors.any { it.message == FANT_IKKE_PERSON }) {
                FantIkkePerson.left()
            } else {
                UkjentFeil(this.errors).left()
            }
        } else if (data == null) {
            ResponsManglerData.left()
        } else {
            data.toString().right()
        }
    }
}
