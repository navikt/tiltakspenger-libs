package no.nav.tiltakspenger.libs.httpklient

import java.net.http.HttpTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

internal fun Throwable.toAttemptFailure(): AttemptOutcome.Failure {
    return when (val unwrapped = unwrapCompletionException()) {
        is HttpTimeoutException -> AttemptOutcome.Timeout(unwrapped)
        else -> AttemptOutcome.NetworkError(unwrapped)
    }
}

internal fun AttemptOutcome.Failure.toHttpKlientError(metadata: HttpKlientMetadata): HttpKlientError = when (this) {
    is AttemptOutcome.Timeout -> HttpKlientError.Timeout(throwable = throwable, metadata = metadata)
    is AttemptOutcome.NetworkError -> HttpKlientError.NetworkError(throwable = throwable, metadata = metadata)
}

private fun Throwable.unwrapCompletionException(): Throwable {
    return when (this) {
        is CompletionException -> cause?.unwrapCompletionException() ?: this
        is ExecutionException -> cause?.unwrapCompletionException() ?: this
        else -> this
    }
}
