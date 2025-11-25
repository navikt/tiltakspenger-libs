package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesPersonklient {

    @Deprecated("Bruk graphqlRequest() istedenfor. Hvorvidt requesten er en hentPerson eller annen query bestemmes av jsonRequestBody. Dermed er det unødvendig at ident/fnr er en egen parameter her.")
    suspend fun hentPerson(
        fnr: Fnr,
        token: AccessToken,
        jsonRequestBody: String,
    ): Either<FellesPersonklientError, String>

    suspend fun graphqlRequest(
        token: AccessToken,
        jsonRequestBody: String,
    ): Either<FellesPersonklientError, String>

    companion object {
        fun create(
            endepunkt: String,
            // Individstønad (det gamle navnet på tiltakspenger)
            tema: String = "IND",
            connectTimeout: Duration = 10.seconds,
            timeout: Duration = 10.seconds,
        ): FellesPersonklient = FellesHttpPersonklient(
            endepunkt = endepunkt,
            tema = tema,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
