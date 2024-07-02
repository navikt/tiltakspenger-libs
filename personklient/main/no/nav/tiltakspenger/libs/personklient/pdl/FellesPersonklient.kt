package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.person.Person
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesPersonklient {

    /**
     * TODO jah: Mulig å splitte denne i en med/uten barn.
     */
    fun hentPerson(
        ident: String,
        token: String,
    ): Either<FellesPersonklientError, Pair<Person, List<String>>>

    companion object {
        fun create(
            endepunkt: String,
            tema: String = "IND",
            connectTimeout: Duration = 20.seconds,
        ): FellesPersonklient = FellesHttpPersonklient(
            endepunkt = endepunkt,
            tema = tema,
            connectTimeout = connectTimeout,
        )
    }
}
