package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.NonEmptyList
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.personklient.pdl.FellesSkjermingError
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FellesSkjermingsklient {
    suspend fun erSkjermetPerson(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Boolean>

    suspend fun erSkjermetPersoner(
        fnrListe: NonEmptyList<Fnr>,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Map<Fnr, Boolean>>

    companion object {
        fun create(
            endepunkt: String,
            getToken: suspend () -> AccessToken,
            clock: Clock,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): FellesSkjermingsklient = FellesHttpSkjermingsklient(
            endepunkt = endepunkt,
            getToken = getToken,
            clock = clock,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
