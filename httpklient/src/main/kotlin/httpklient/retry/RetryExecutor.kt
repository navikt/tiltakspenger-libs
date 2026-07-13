package no.nav.tiltakspenger.libs.httpklient.retry

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.resilience.Schedule
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientLoggingConfig
import no.nav.tiltakspenger.libs.httpklient.HttpKlientRequest
import no.nav.tiltakspenger.libs.httpklient.TransportRespons
import no.nav.tiltakspenger.libs.httpklient.logExcessiveRetries
import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.TimeSource

internal data class RetryExecutionResult(
    val lastResult: AttemptResult,
    /** Ett [Forsøk] per faktisk utført HTTP-forsøk, i den rekkefølgen de ble kjørt. En [NonEmptyList] fordi [RetryExecutor.execute] alltid kjører minst ett forsøk. */
    val forsøk: NonEmptyList<Forsøk>,
    val totalDuration: Duration,
) {
    /** Antall utførte HTTP-forsøk. */
    val attempts: Int get() = forsøk.size

    /** Varighet per forsøk, i samme rekkefølge som de ble utført. */
    val attemptDurations: List<Duration> get() = forsøk.map { it.varighet }

    /** Tidspunkt for start på det første HTTP-forsøket (samme semantikk som `nå(clock)`). */
    val requestSendt: LocalDateTime get() = forsøk.head.start

    /** Tidspunkt for slutt på det siste HTTP-forsøket (samme semantikk som `nå(clock)`). */
    val responsMottatt: LocalDateTime get() = forsøk.last().slutt
}

/**
 * Utfallet av ett HTTP-forsøk: enten en [AttemptOutcome.Failure] (venstre) eller en fullført HTTP-respons fra transporten (høyre).
 * Modellert som en Arrow [Either] slik at konsumenter får hele Either-API-et; de beskrivende `Success`/`Failure`-navnene erstattes av `left`/`right`.
 * [TransportRespons] bærer rå bytes slik at binært innhold ikke korrupteres; dekoding til tekst skjer først i konverterings-/metadata-laget.
 */
internal typealias AttemptResult = Either<AttemptOutcome.Failure, TransportRespons>

internal fun AttemptResult.toAttemptOutcome(): AttemptOutcome =
    fold({ it }, { AttemptOutcome.Status(it.statusCode) })

/**
 * Ett faktisk utført HTTP-forsøk, med tidsvindu og målt varighet.
 *
 * @property start Veggklokke-tidspunkt (via `nå(clock)`) da forsøket startet.
 * @property slutt Veggklokke-tidspunkt (via `nå(clock)`) da forsøket var ferdig.
 * @property varighet Monotont målt varighet (via [TimeSource]); immun mot klokkejustering, i motsetning til `slutt - start`.
 */
internal data class Forsøk(
    val start: LocalDateTime,
    val slutt: LocalDateTime,
    val varighet: Duration,
)

internal class RetryExecutor(
    private val clock: Clock,
    private val timeSource: TimeSource,
) {
    /**
     * Kjører [attempt] minst én gang og bruker [retryConfig] sin Arrow [Schedule] til å avgjøre ventetid og retry-budsjett.
     * [AttemptOutcome.retryable] og [RetryConfig.retryOn] er harde gates før schedulen spørres, slik at utfall som ikke faktisk retry-es ikke bruker retry-budsjett.
     * [isSuccessfulResponse] er det effektive `successStatus`-predikatet for requesten; en respons som allerede regnes som suksess retry-es aldri, selv om statuskoden ligger i den retryable mengden (f.eks. en konsument som godtar `503` som suksess).
     *
     * Løkka er skrevet som en `while(true)`-driver som steg for steg konsulterer Arrow-schedulens [Schedule.Decision]-API — samme mønster som Arrows egne drivere (`retryOrElseEither`), slik at policy-algebraen (exponential/jittered/`and`/`or`) fortsatt eies av [RetryConfig.schedule] mens vi eier selve løkka.
     * `currentCoroutineContext().ensureActive()` kjøres rett før hvert forsøk (i `kjørForsøk`) og gir prompt kansellering også ved zero-delay-retries, der [delay] aldri nås.
     * Første forsøk kjøres før løkka og seeder en [NonEmptyList] av [Forsøk]; påfølgende forsøk legges til. Dermed er [RetryExecutionResult.forsøk] aldri tom per type, uten defensive null-sjekker.
     *
     * Veggklokke-tidsstempler ([Forsøk.start]/[Forsøk.slutt]) måles via `nå(clock)`, mens varigheter måles monotont via [timeSource] slik at de er immune mot klokkejustering.
     */
    suspend fun execute(
        request: HttpKlientRequest,
        retryConfig: RetryConfig,
        isSuccessfulResponse: (Int) -> Boolean,
        attempt: suspend () -> AttemptResult,
    ): RetryExecutionResult {
        val totalMark = timeSource.markNow()
        var scheduleStep: suspend (AttemptOutcome) -> Schedule.Decision<AttemptOutcome, *> = retryConfig.schedule::invoke

        suspend fun kjørForsøk(): Pair<AttemptResult, Forsøk> {
            currentCoroutineContext().ensureActive()
            val start = nå(clock)
            val mark = timeSource.markNow()
            val result = attempt()
            return result to Forsøk(start = start, slutt = nå(clock), varighet = mark.elapsedNow())
        }

        val (førsteResultat, førsteForsøk) = kjørForsøk()
        var result = førsteResultat
        var forsøk = nonEmptyListOf(førsteForsøk)

        while (true) {
            val outcome = result.toAttemptOutcome()

            // Harde gates før schedulen: et utfall som ikke skal retry-es bruker heller ikke retry-budsjett (schedulen steppes ikke).
            val ferdig = (outcome is AttemptOutcome.Status && isSuccessfulResponse(outcome.statusCode)) ||
                !outcome.retryable ||
                !retryConfig.retryOn(RetryDecisionContext(request.method, forsøk.size, outcome))

            val skalStoppe = ferdig || when (val decision = scheduleStep(outcome)) {
                is Schedule.Decision.Done -> true

                is Schedule.Decision.Continue -> {
                    if (decision.delay > Duration.ZERO) {
                        delay(decision.delay)
                    }
                    scheduleStep = decision.step
                    false
                }
            }

            if (skalStoppe) {
                return RetryExecutionResult(
                    lastResult = result,
                    forsøk = forsøk,
                    totalDuration = totalMark.elapsedNow(),
                )
            }

            val (nesteResultat, nesteForsøk) = kjørForsøk()
            result = nesteResultat
            forsøk += nesteForsøk
        }
    }
}

internal fun RetryConfig.notifyExcessiveRetries(
    loggingConfig: HttpKlientLoggingConfig,
    attempts: Int,
    totalDuration: Duration,
    attemptDurations: List<Duration>,
    finalStatusCode: Int?,
    finalError: HttpKlientError?,
) {
    val threshold = excessiveRetriesThreshold ?: return
    val retriesUsed = (attempts - 1).coerceAtLeast(0)
    if (retriesUsed < threshold) return
    val outcome = RetryOutcome(
        attempts = attempts,
        totalDuration = totalDuration,
        attemptDurations = attemptDurations,
        finalStatusCode = finalStatusCode,
        finalError = finalError,
    )
    // En egen hook tar over ansvaret (og får hele RetryOutcome); ellers logger vi default via klientens loggingConfig.
    onExcessiveRetries?.invoke(outcome) ?: loggingConfig.logExcessiveRetries(outcome)
}
