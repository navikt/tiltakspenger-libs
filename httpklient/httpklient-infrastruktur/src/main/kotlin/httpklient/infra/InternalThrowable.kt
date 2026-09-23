package no.nav.tiltakspenger.libs.httpklient.infra

import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.Timeoutfase
import no.nav.tiltakspenger.libs.httpklient.infra.retry.AttemptOutcome
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * Oversetter en JDK-exception til vår egen utfallstype.
 *
 * Dette er det eneste stedet timeout-fasen avgjøres.
 * JDK-en skiller de to timeoutene i typen, og det er eneste sted skillet finnes — stacktracen er identisk (begge lages på klientens `SelectorManager`-tråd) og meldingen er «HTTP connect timed out» i begge tilfeller.
 * Klassifiserer vi ikke her, må skillet gjettes tilbake fra `throwable` lenger ut i kjeden: spesialisert → generalisert → spesialisert, der den generaliserte typen aldri burde ha kastet informasjonen den fikk inn.
 *
 * Rekkefølgen i `when`-en er semantisk: `HttpConnectTimeoutException` arver `HttpTimeoutException`, så den må stå først.
 */
internal fun Throwable.toAttemptFailure(): AttemptOutcome.Failure {
    return when (val unwrapped = unwrapCompletionException()) {
        is HttpConnectTimeoutException -> AttemptOutcome.Timeout(unwrapped, Timeoutfase.Oppkobling)
        is HttpTimeoutException -> AttemptOutcome.Timeout(unwrapped, Timeoutfase.Svar)
        else -> AttemptOutcome.NetworkError(unwrapped)
    }
}

internal fun AttemptOutcome.Failure.toHttpKlientError(metadata: HttpKlientMetadata): HttpKlientError = when (this) {
    is AttemptOutcome.Timeout -> HttpKlientError.Timeout(throwable = throwable, fase = fase, metadata = metadata)
    is AttemptOutcome.NetworkError -> HttpKlientError.NetworkError(throwable = throwable, metadata = metadata)
}

private fun Throwable.unwrapCompletionException(): Throwable {
    return when (this) {
        is CompletionException -> cause?.unwrapCompletionException() ?: this
        is ExecutionException -> cause?.unwrapCompletionException() ?: this
        else -> this
    }
}
