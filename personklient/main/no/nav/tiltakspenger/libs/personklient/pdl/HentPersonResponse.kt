package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.FantIkkePerson
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.ResponsManglerData
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.UkjentFeil

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
        } else {
            this.data?.toString()?.right()
                ?: ResponsManglerData.left()
        }
    }
}
