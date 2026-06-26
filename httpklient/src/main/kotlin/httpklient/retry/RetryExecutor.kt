package no.nav.tiltakspenger.libs.httpklient.retry

import arrow.resilience.Schedule
import kotlinx.coroutines.delay
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientRequest
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

internal data class RetryExecutionResult(
    val lastResult: AttemptResult,
    val attempts: Int,
    val attemptDurations: List<Duration>,
    val totalDuration: Duration,
)

internal sealed interface AttemptResult {
    data class Success(val response: java.net.http.HttpResponse<String>) : AttemptResult
    data class Failure(val failure: AttemptOutcome.Failure) : AttemptResult
}

internal fun AttemptResult.toAttemptOutcome(): AttemptOutcome = when (this) {
    is AttemptResult.Success -> AttemptOutcome.Status(response.statusCode())
    is AttemptResult.Failure -> failure
}

internal class RetryExecutor(
    private val clock: Clock,
) {
    /**
     * Kjører [attempt] minst én gang og bruker [retryConfig] sin Arrow [Schedule] til å avgjøre ventetid og retry-budsjett.
     * [AttemptOutcome.retryable] og [RetryConfig.retryOn] er harde gates før schedulen spørres, slik at utfall som ikke faktisk retry-es ikke bruker retry-budsjett.
     * [isSuccessfulResponse] er det effektive `successStatus`-predikatet for requesten; en respons som allerede regnes som suksess retry-es aldri, selv om statuskoden ligger i den retryable mengden (f.eks. en konsument som godtar `503` som suksess).
     */
    suspend fun execute(
        request: HttpKlientRequest,
        retryConfig: RetryConfig,
        isSuccessfulResponse: (Int) -> Boolean,
        attempt: suspend () -> AttemptResult,
    ): RetryExecutionResult {
        val attemptDurations = mutableListOf<Duration>()
        val totalStart = clock.instant()
        var attemptNumber = 0
        var lastResult: AttemptResult
        var scheduleStep: suspend (AttemptOutcome) -> Schedule.Decision<AttemptOutcome, *> =
            retryConfig.schedule::invoke

        while (true) {
            attemptNumber++
            val attemptStart = clock.instant()
            lastResult = attempt()
            val attemptDuration = JavaDuration.between(attemptStart, clock.instant()).toKotlinDuration()
            attemptDurations += attemptDuration

            val outcome = lastResult.toAttemptOutcome()
            if (outcome is AttemptOutcome.Status && isSuccessfulResponse(outcome.statusCode)) break
            if (!outcome.retryable) break
            if (!retryConfig.retryOn(RetryDecisionContext(request.method, attemptNumber, outcome))) break

            when (val decision = scheduleStep(outcome)) {
                is Schedule.Decision.Done -> break

                is Schedule.Decision.Continue -> {
                    if (decision.delay > Duration.ZERO) {
                        delay(decision.delay)
                    }
                    scheduleStep = decision.step
                }
            }
        }

        return RetryExecutionResult(
            lastResult = lastResult,
            attempts = attemptNumber,
            attemptDurations = attemptDurations.toList(),
            totalDuration = JavaDuration.between(totalStart, clock.instant()).toKotlinDuration(),
        )
    }
}

internal fun RetryConfig.notifyExcessiveRetries(
    attempts: Int,
    totalDuration: Duration,
    attemptDurations: List<Duration>,
    finalStatusCode: Int?,
    finalError: HttpKlientError?,
) {
    val threshold = excessiveRetriesThreshold ?: return
    val retriesUsed = (attempts - 1).coerceAtLeast(0)
    if (retriesUsed < threshold) return
    onExcessiveRetries(
        RetryOutcome(
            attempts = attempts,
            totalDuration = totalDuration,
            attemptDurations = attemptDurations,
            finalStatusCode = finalStatusCode,
            finalError = finalError,
        ),
    )
}
