package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.person.Person

interface PdlClient {

    /**
     * TODO jah: Mulig å splitte denne i en med/uten barn.
     */
    fun hentPerson(
        ident: String,
        token: String,
    ): Either<PDLClientError, Pair<Person, List<String>>>
}
