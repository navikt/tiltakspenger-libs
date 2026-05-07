package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

internal class HttpKlientCircuitBreakerTest {

    @Test
    fun `default CircuitBreakerConfig gjor ingenting`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/default")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(circuitBreakerConfig = CircuitBreakerConfig.None)

            klient.get<String>(URI.create("${wiremock.baseUrl()}/default"))
            klient.get<String>(URI.create("${wiremock.baseUrl()}/default"))

            wiremock.verify(2, getRequestedFor(urlEqualTo("/default")))
        }
    }

    @Test
    fun `aapner etter konfigurert antall retryable feil og avviser uten nytt HTTP-kall`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/ustabil")).willReturn(aResponse().withStatus(503).withBody("nei")))
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "ustabil",
                    maxFailures = 2,
                    resetTimeout = 100.milliseconds,
                ),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/ustabil")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            klient.get<String>(URI.create("${wiremock.baseUrl()}/ustabil")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            val rejected = klient.get<String>(URI.create("${wiremock.baseUrl()}/ustabil")).swap().getOrNull()!!

            val circuitBreakerOpen = rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            circuitBreakerOpen.retryable shouldBe false
            circuitBreakerOpen.metadata.attempts shouldBe 0
            circuitBreakerOpen.metadata.attemptDurations.shouldBeEmpty()
            circuitBreakerOpen.metadata.statusCode shouldBe null
            circuitBreakerOpen.metadata.rawRequestString shouldBe "GET ${wiremock.baseUrl()}/ustabil"
            wiremock.verify(2, getRequestedFor(urlEqualTo("/ustabil")))
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `resetter etter resetTimeout og lukker ved vellykket half-open kall`() = runTest {
        val timeSource = TestTimeSource()
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/kommer-seg"))
                    .inScenario("circuit-reset").whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("ok"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/kommer-seg"))
                    .inScenario("circuit-reset").whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200).withBody("frisk")),
            )
            var halfOpen = 0
            var closed = 0
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "kommer-seg",
                    maxFailures = 1,
                    resetTimeout = 10.milliseconds,
                ).withTimeSource(timeSource)
                    .doOnHalfOpen { halfOpen++ }
                    .doOnClosed { closed++ },
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/kommer-seg")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            klient.get<String>(URI.create("${wiremock.baseUrl()}/kommer-seg")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            timeSource += 11.milliseconds

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/kommer-seg")).getOrFail()

            response.body shouldBe "frisk"
            halfOpen shouldBe 1
            closed shouldBe 1
            wiremock.verify(2, getRequestedFor(urlEqualTo("/kommer-seg")))
        }
    }

    @Test
    fun `count maxFailures 1 aapner etter foerste registrerte feil`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/count-off-by-one")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "count-off-by-one",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/count-off-by-one")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            val rejected = klient.get<String>(URI.create("${wiremock.baseUrl()}/count-off-by-one")).swap().getOrNull()!!

            rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            wiremock.verify(1, getRequestedFor(urlEqualTo("/count-off-by-one")))
        }
    }

    @Test
    fun `slidingWindow maxFailures 1 aapner etter foerste registrerte feil`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/sliding-off-by-one")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.slidingWindow(
                    name = "sliding-off-by-one",
                    maxFailures = 1,
                    windowDuration = 100.milliseconds,
                    resetTimeout = 100.milliseconds,
                ),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/sliding-off-by-one")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            val rejected = klient.get<String>(URI.create("${wiremock.baseUrl()}/sliding-off-by-one")).swap().getOrNull()!!

            rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            wiremock.verify(1, getRequestedFor(urlEqualTo("/sliding-off-by-one")))
        }
    }

    @Test
    fun `circuit breaker state er lokal per JavaHttpKlient-instans`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/lokal")).willReturn(aResponse().withStatus(503)))
            val config = CircuitBreakerConfig.count(
                name = "lokal",
                maxFailures = 1,
                resetTimeout = 100.milliseconds,
            )
            val klient1 = testHttpKlient(circuitBreakerConfig = config)
            val klient2 = testHttpKlient(circuitBreakerConfig = config)

            klient1.get<String>(URI.create("${wiremock.baseUrl()}/lokal")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            klient1.get<String>(URI.create("${wiremock.baseUrl()}/lokal")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            klient2.get<String>(URI.create("${wiremock.baseUrl()}/lokal")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()

            wiremock.verify(2, getRequestedFor(urlEqualTo("/lokal")))
        }
    }

    @Test
    fun `per-request circuitBreakerConfig overstyrer klient-default`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/override")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(circuitBreakerConfig = CircuitBreakerConfig.None)

            klient.get<String>(URI.create("${wiremock.baseUrl()}/override")) {
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "override",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ).withFailurePredicate { ctx -> ctx.error.retryable }
            }.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            val rejected = klient.get<String>(URI.create("${wiremock.baseUrl()}/override")) {
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "override",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ).withFailurePredicate { ctx -> ctx.error.retryable }
            }.swap().getOrNull()!!

            rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            wiremock.verify(1, getRequestedFor(urlEqualTo("/override")))
        }
    }

    @Test
    fun `inline per-request configs med nye lambdaer deler breaker via navn`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/inline-lambdaer")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(circuitBreakerConfig = CircuitBreakerConfig.None)

            klient.get<String>(URI.create("${wiremock.baseUrl()}/inline-lambdaer")) {
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "inline-lambdaer",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ).withFailurePredicate { ctx -> ctx.error.retryable }
            }.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            val rejected = klient.get<String>(URI.create("${wiremock.baseUrl()}/inline-lambdaer")) {
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "inline-lambdaer",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ).withFailurePredicate { ctx -> ctx.error.retryable }
            }.swap().getOrNull()!!

            rejected.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>()
            wiremock.verify(1, getRequestedFor(urlEqualTo("/inline-lambdaer")))
        }
    }

    @Test
    fun `circuit breaker teller sluttresultat etter retry`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/retry-og-circuit")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig.fixed(
                    maxRetries = 1,
                    delay = ZERO,
                    retryOn = RetryOnServerErrorsAndNetwork,
                ),
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "retry-og-circuit",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ),
            )

            val first = klient.get<String>(URI.create("${wiremock.baseUrl()}/retry-og-circuit")).swap().getOrNull()!!
            val second = klient.get<String>(URI.create("${wiremock.baseUrl()}/retry-og-circuit")).swap().getOrNull()!!

            first.shouldBeInstanceOf<HttpKlientError.Ikke2xx>().metadata.attempts shouldBe 2
            second.shouldBeInstanceOf<HttpKlientError.CircuitBreakerOpen>().metadata.attempts shouldBe 0
            wiremock.verify(2, getRequestedFor(urlEqualTo("/retry-og-circuit")))
        }
    }

    @Test
    fun `failurePredicate styrer hva som teller mot circuit breaker`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/fire-null-fire")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "fire-null-fire",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                )
                    .withFailurePredicate(NeverRecordCircuitBreakerFailure),
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/fire-null-fire")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            klient.get<String>(URI.create("${wiremock.baseUrl()}/fire-null-fire")).swap().getOrNull()!!
                .shouldBeInstanceOf<HttpKlientError.Ikke2xx>()

            wiremock.verify(2, getRequestedFor(urlEqualTo("/fire-null-fire")))
        }
    }

    @Test
    fun `callbacks for open og rejected kalles`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/callbacks")).willReturn(aResponse().withStatus(503)))
            var open = 0
            var rejected = 0
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "callbacks",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                )
                    .doOnOpen { open++ }
                    .doOnRejectedTask { rejected++ },
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/callbacks"))
            klient.get<String>(URI.create("${wiremock.baseUrl()}/callbacks"))

            open shouldBe 1
            rejected shouldBe 1
        }
    }

    @Test
    fun `ikke-sentinel exception fra protectEither-blokken propagerer`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/predicate-kaster")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                circuitBreakerConfig = CircuitBreakerConfig.count(
                    name = "predicate-kaster",
                    maxFailures = 1,
                    resetTimeout = 100.milliseconds,
                ).withFailurePredicate { error("predikat-feil") },
            )

            shouldThrowWithMessage<IllegalStateException>("predikat-feil") {
                klient.get<String>(URI.create("${wiremock.baseUrl()}/predicate-kaster"))
            }
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
            .openingStrategy.shouldBeInstanceOf<arrow.resilience.CircuitBreaker.OpeningStrategy.Count>()
        CircuitBreakerConfig.slidingWindow(
            name = "sliding",
            maxFailures = 2,
            windowDuration = 10.milliseconds,
            resetTimeout = 1.milliseconds,
        ).openingStrategy.shouldBeInstanceOf<arrow.resilience.CircuitBreaker.OpeningStrategy.SlidingWindow>()
        CircuitBreakerOnRetryableErrors(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.Ikke2xx(503, "", tomMetadata())),
        ) shouldBe true
        CircuitBreakerOnRetryableErrors(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.Ikke2xx(404, "", tomMetadata())),
        ) shouldBe false
        NeverRecordCircuitBreakerFailure(
            CircuitBreakerDecisionContext(HttpMethod.GET, URI.create("http://localhost"), HttpKlientError.Ikke2xx(503, "", tomMetadata())),
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
}
