package no.nav.tiltakspenger.libs.httpklient
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerDecisionContext
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerOnRetryableErrors
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerOpeningStrategy
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.CircuitBreakerPredicate
import no.nav.tiltakspenger.libs.httpklient.circuitbreaker.NeverRecordCircuitBreakerFailure
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/**
 * Circuit breakeren konfigureres per klientinstans i v2 (per-request-aksen er borte).
 * Pipeline-oppførselen testes over [FakeHttpTransport]; antall kall som faktisk nådde transporten tilsvarer antall HTTP-kall på wire.
 */
internal class HttpKlientCircuitBreakerTest {
    private val uri = URI.create("http://cb.test/ressurs")
    private val okJson = """{"status":"ok","antall":1}"""

    @Test
    fun `default CircuitBreakerConfig gjør ingenting`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(transport, circuitBreaker = CircuitBreakerConfig.None)

        klient.getJson<TestResponseDto>(uri)
        klient.getJson<TestResponseDto>(uri)

        transport.mottatteKall shouldHaveSize 2
    }

    @Test
    fun `åpner etter konfigurert antall retryable feil og avviser uten nytt HTTP-kall`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, "nei")
        transport.leggIKøStatus(503, "nei")
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "ustabil",
                maxFailures = 2,
                resetTimeout = 100.milliseconds,
            ),
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        val rejected = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val circuitBreakerOpen = rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
        circuitBreakerOpen.retryable shouldBe false
        circuitBreakerOpen.metadata.attempts shouldBe 0
        circuitBreakerOpen.metadata.attemptDurations.shouldBeEmpty()
        circuitBreakerOpen.metadata.statusCode shouldBe null
        circuitBreakerOpen.metadata.rawRequestString shouldBe "GET $uri\nAccept: application/json"
        transport.mottatteKall shouldHaveSize 2
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `resetter etter resetTimeout og lukker ved vellykket half-open kall`() = runTest {
        val timeSource = TestTimeSource()
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøJson(okJson)
        var halfOpen = 0
        var closed = 0
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "kommer-seg",
                maxFailures = 1,
                resetTimeout = 10.milliseconds,
            ).withTimeSource(timeSource)
                .doOnHalfOpen { halfOpen++ }
                .doOnClosed { closed++ },
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
        timeSource += 11.milliseconds

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.body shouldBe TestResponseDto(status = "ok", antall = 1)
        halfOpen shouldBe 1
        closed shouldBe 1
        transport.mottatteKall shouldHaveSize 2
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `feiler half-open kall åpner breakeren igjen og avviser neste kall uten HTTP`() = runTest {
        val timeSource = TestTimeSource()
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøStatus(503)
        var halfOpen = 0
        var open = 0
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "fortsatt-syk",
                maxFailures = 1,
                resetTimeout = 10.milliseconds,
            ).withTimeSource(timeSource)
                .doOnHalfOpen { halfOpen++ }
                .doOnOpen { open++ },
        )

        // Første feil åpner breakeren.
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()

        // Etter reset slipper half-open ett prøvekall gjennom — som også feiler, så breakeren åpner igjen.
        timeSource += 11.milliseconds
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

        // Neste kall avvises igjen uten nytt HTTP-kall.
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()

        halfOpen shouldBe 1
        open shouldBe 2
        // Kun det første kallet og half-open-prøvekallet traff transporten.
        transport.mottatteKall shouldHaveSize 2
    }

    @Test
    fun `count maxFailures 1 åpner etter første registrerte feil`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "count-off-by-one",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            ),
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        val rejected = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `slidingWindow maxFailures 1 åpner etter første registrerte feil`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.slidingWindow(
                name = "sliding-off-by-one",
                maxFailures = 1,
                windowDuration = 100.milliseconds,
                resetTimeout = 100.milliseconds,
            ),
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        val rejected = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `circuit breaker state er lokal per HttpKlient-instans`() = runTest {
        val transport1 = FakeHttpTransport()
        transport1.leggIKøStatus(503)
        val transport2 = FakeHttpTransport()
        transport2.leggIKøStatus(503)
        val config = CircuitBreakerConfig.count(
            name = "lokal",
            maxFailures = 1,
            resetTimeout = 100.milliseconds,
        )
        val klient1 = fakeHttpKlient(transport1, circuitBreaker = config)
        val klient2 = fakeHttpKlient(transport2, circuitBreaker = config)

        klient1.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        klient1.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
        // Samme config-verdi, men egen instans: klient2s breaker er upåvirket av klient1s feil.
        klient2.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

        transport1.mottatteKall shouldHaveSize 1
        transport2.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `circuit breaker teller sluttresultat etter retry`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            retry = Retry.Fast(maksForsøk = 2, delay = ZERO),
            circuitBreaker = CircuitBreakerConfig.count(
                name = "retry-og-circuit",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            ),
        )

        val first = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        val second = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        first.shouldBeInstanceOf<HttpKlientError.UventetStatus>().metadata.attempts shouldBe 2
        second.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>().metadata.attempts shouldBe 0
        transport.mottatteKall shouldHaveSize 2
    }

    @Test
    fun `failurePredicate styrer hva som teller mot circuit breaker`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "fire-null-fire",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            )
                .withFailurePredicate(NeverRecordCircuitBreakerFailure),
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.UventetStatus>()

        transport.mottatteKall shouldHaveSize 2
    }

    @Test
    fun `callbacks for open og rejected kalles`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        var open = 0
        var rejected = 0
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "callbacks",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            )
                .doOnOpen { open++ }
                .doOnRejectedTask { rejected++ },
        )

        klient.getJson<TestResponseDto>(uri)
        klient.getJson<TestResponseDto>(uri)

        open shouldBe 1
        rejected shouldBe 1
    }

    @Test
    fun `ikke-sentinel exception fra protectEither-blokken propagerer`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "predicate-kaster",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            ).withFailurePredicate { error("predikat-feil") },
        )

        shouldThrowWithMessage<IllegalStateException>("predikat-feil") {
            klient.getJson<TestResponseDto>(uri)
        }
    }

    @Test
    fun `CircuitBreakerConfig fluent helpers oppdaterer kun relevante felter`() {
        val failurePredicate: CircuitBreakerPredicate = { false }
        val onRejected: suspend () -> Unit = {}
        val onClosed: suspend () -> Unit = {}
        val onHalfOpen: suspend () -> Unit = {}
        val onOpen: suspend () -> Unit = {}
        val config = CircuitBreakerConfig.count(name = "fluent", maxFailures = 2, resetTimeout = 100.milliseconds)
            .withFailurePredicate(failurePredicate)
            .withExponentialBackoff(factor = 2.0, maxResetTimeout = 1_000.milliseconds)
            .doOnRejectedTask(onRejected)
            .doOnClosed(onClosed)
            .doOnHalfOpen(onHalfOpen)
            .doOnOpen(onOpen)

        config.failurePredicate shouldBe failurePredicate
        config.exponentialBackoffFactor shouldBe 2.0
        config.maxResetTimeout shouldBe 1_000.milliseconds
        config.onRejected shouldBe onRejected
        config.onClosed shouldBe onClosed
        config.onHalfOpen shouldBe onHalfOpen
        config.onOpen shouldBe onOpen
    }

    @Test
    fun `CircuitBreakerConfig factories og validering`() {
        CircuitBreakerConfig.count(name = "count", maxFailures = 1, resetTimeout = 1.milliseconds)
            .openingStrategy.shouldBeInstanceOf<CircuitBreakerOpeningStrategy.Count>()
        CircuitBreakerConfig.slidingWindow(
            name = "sliding",
            maxFailures = 2,
            windowDuration = 10.milliseconds,
            resetTimeout = 1.milliseconds,
        ).openingStrategy.shouldBeInstanceOf<CircuitBreakerOpeningStrategy.SlidingWindow>()
        CircuitBreakerOnRetryableErrors(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.UventetStatus(503, "", tomMetadata())),
        ) shouldBe true
        CircuitBreakerOnRetryableErrors(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.UventetStatus(404, "", tomMetadata())),
        ) shouldBe false
        NeverRecordCircuitBreakerFailure(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.UventetStatus(503, "", tomMetadata())),
        ) shouldBe false

        shouldThrowWithMessage<IllegalArgumentException>("name kan ikke være blank") {
            CircuitBreakerConfig.count(name = " ", maxFailures = 1, resetTimeout = 1.milliseconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maxFailures må være positiv, var 0") {
            CircuitBreakerConfig.count(name = "invalid-count", maxFailures = 0, resetTimeout = 1.milliseconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maxFailures må være positiv, var 0") {
            CircuitBreakerConfig.slidingWindow(
                name = "invalid-sliding",
                maxFailures = 0,
                windowDuration = 1.milliseconds,
                resetTimeout = 1.milliseconds,
            )
        }
        shouldThrowWithMessage<IllegalArgumentException>("windowDuration må være positiv, var 0s") {
            CircuitBreakerConfig.slidingWindow(
                name = "invalid-window",
                maxFailures = 1,
                windowDuration = ZERO,
                resetTimeout = 1.milliseconds,
            )
        }
        shouldThrowWithMessage<IllegalArgumentException>("resetTimeout må være positiv, var 0s") {
            CircuitBreakerConfig.count(name = "invalid-reset", maxFailures = 1, resetTimeout = ZERO)
        }
        shouldThrowWithMessage<IllegalArgumentException>("exponentialBackoffFactor må være >= 1.0, var 0.9") {
            CircuitBreakerConfig.count(name = "invalid-factor", maxFailures = 1, resetTimeout = 1.milliseconds)
                .withExponentialBackoff(factor = 0.9)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maxResetTimeout (1ms) må være >= resetTimeout (2ms)") {
            CircuitBreakerConfig.count(name = "invalid-max-reset", maxFailures = 1, resetTimeout = 2.milliseconds)
                .withExponentialBackoff(factor = 1.0, maxResetTimeout = 1.milliseconds)
        }
    }

    @Test
    fun `HttpKlientError CircuitBreakerOpen har retryable false`() {
        val rejected = arrow.resilience.CircuitBreaker.ExecutionRejected(
            "open",
            arrow.resilience.CircuitBreaker.State.Closed(
                arrow.resilience.CircuitBreaker.OpeningStrategy.Count(1),
            ),
        )

        HttpKlientError.CircuitBreakerOpen(rejected, tomMetadata()).retryable shouldBe false
    }

    @Test
    fun `ikke-retryable status (404) åpner ikke breakeren med default-predikat`() = runTest {
        val transport = FakeHttpTransport()
        repeat(3) { transport.leggIKøStatus(404) }
        val klient = fakeHttpKlient(
            transport = transport,
            circuitBreaker = CircuitBreakerConfig.count(
                name = "fnf-default",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            ),
        )

        // Default-predikatet teller bare retryable feil; 404 er permanent, så breakeren forblir lukket og alle tre kallene treffer transporten.
        repeat(3) {
            klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        }

        transport.mottatteKall shouldHaveSize 3
    }

    @Test
    fun `forbigående feil som lykkes via retry holder breakeren lukket`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøJson(okJson)
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(
            transport = transport,
            retry = Retry.Fast(maksForsøk = 2, delay = ZERO),
            circuitBreaker = CircuitBreakerConfig.count(
                name = "forb",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            ),
        )

        // Første kall feiler én gang (503) og lykkes på retry; sluttresultatet er suksess, så breakeren registrerer ingen feil.
        klient.getJson<TestResponseDto>(uri).getOrFail().body shouldBe TestResponseDto(status = "ok", antall = 1)
        // Breakeren er fortsatt lukket, så neste kall går rett gjennom.
        klient.getJson<TestResponseDto>(uri).getOrFail().body shouldBe TestResponseDto(status = "ok", antall = 1)
    }
}
