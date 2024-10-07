package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesPersonklient {

    suspend fun hentPerson(
        fnr: Fnr,
        token: AccessToken,
        jsonRequestBody: String,
    ): Either<FellesPersonklientError, String>

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
