package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesSkjermingsklient {
    suspend fun erSkjermetPerson(
        token: AccessToken,
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Boolean>

    companion object {
        fun create(
            endepunkt: String,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): FellesSkjermingsklient = FellesHttpSkjermingsklient(
            endepunkt = endepunkt,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
