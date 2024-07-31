package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.person.Person
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesPersonklient {

    /**
     * TODO jah: Mulig å splitte denne i en med/uten barn.
     */
    suspend fun hentPerson(
        fnr: Fnr,
        token: String,
    ): Either<FellesPersonklientError, Pair<Person, List<String>>>

    companion object {
        fun create(
            endepunkt: String,
            // Individstønad (det gamle navnet på tiltakspenger)
            tema: String = "IND",
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): FellesPersonklient = FellesHttpPersonklient(
            endepunkt = endepunkt,
            tema = tema,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
