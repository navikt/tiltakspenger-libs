package no.nav.tiltakspenger.libs.httpklient

import arrow.resilience.Schedule
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.random.Random
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

/**
 * Liten hjelper for tester som bare trenger en schedule som tillater N retries uten ventetid.
 */
private fun recursSchedule(maxRetries: Int): Schedule<AttemptOutcome, *> =
    Schedule.recurs(maxRetries.toLong())

internal class HttpKlientRetryTest {

    @Test
    fun `default RetryConfig retry-er ikke`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(503).withBody("nei")))
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/x")).swap().getOrNull()!!

            val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            ikke2xx.metadata.attempts shouldBe 1
            ikke2xx.metadata.attemptDurations shouldHaveSize 1
        }
    }

    @Test
    fun `retry lykkes paa andre forsoek`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/y"))
                    .inScenario("retry-y").whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("ok"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/y"))
                    .inScenario("retry-y").whenScenarioStateIs("ok")
                    .willReturn(aResponse().withStatus(200).withBody("hei")),
            )
            val klient = testHttpKlient(
                retryConfig = RetryConfig(
                    schedule = recursSchedule(2),
                    retryOn = RetryOnServerErrorsAndNetwork,
                ),
            )

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/y")).getOrFail()

            response.statusCode shouldBe 200
            response.body shouldBe "hei"
            response.metadata.attempts shouldBe 2
            response.metadata.attemptDurations shouldHaveSize 2
        }
    }

    @Test
    fun `bruker opp alle retries og returnerer siste feil`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/z")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2), retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/z")).swap().getOrNull()!!

            val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            ikke2xx.metadata.attempts shouldBe 3
            ikke2xx.metadata.attemptDurations shouldHaveSize 3
        }
    }

    @Test
    fun `4xx blir ikke retry-et med default-predikat for idempotente metoder`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/q")).willReturn(aResponse().withStatus(404)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2), retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/q")).swap().getOrNull()!!

            error.metadata.attempts shouldBe 1
        }
    }

    @Test
    fun `POST blir ikke retry-et med default-predikat`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/p")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2), retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.post<String>(URI.create("${wiremock.baseUrl()}/p")) { body("x") }.swap().getOrNull()!!

            error.metadata.attempts shouldBe 1
        }
    }

    @Test
    fun `POST kan retry-es med custom predikat`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(post(urlEqualTo("/p2")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(
                    schedule = recursSchedule(1),
                    retryOn = {
                        val outcome = it.outcome
                        outcome is AttemptOutcome.Status && outcome.statusCode == 503
                    },
                ),
            )

            val error = klient.post<String>(URI.create("${wiremock.baseUrl()}/p2")) { body("x") }.swap().getOrNull()!!

            error.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `retry paa NetworkError`() = runTest {
        val uri = stoppedServerUri("/dod")
        var calls = 0
        val klient = testHttpKlient(
            retryConfig = RetryConfig(
                schedule = recursSchedule(2),
                retryOn = { ctx ->
                    calls++
                    ctx.outcome is AttemptOutcome.Failure
                },
            ),
        )

        val error = klient.get<String>(uri).swap().getOrNull()!!

        (error is HttpKlientError.NetworkError || error is HttpKlientError.Timeout) shouldBe true
        error.metadata.attempts shouldBe 3
        calls shouldBe 3
    }

    @Test
    fun `per-request retryConfig overstyrer klient-default`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/o")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(retryConfig = RetryConfig.None)

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/o")) {
                retryConfig = RetryConfig(schedule = recursSchedule(1), retryOn = RetryOnServerErrorsAndNetwork)
            }.swap().getOrNull()!!

            error.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `excessiveRetries-callback firer naar terskelen er passert`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/e")).willReturn(aResponse().withStatus(503)))
            var notified: RetryOutcome? = null
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2))
                    .withRetryOn(RetryOnServerErrorsAndNetwork)
                    .notifyOnExcessiveRetries(threshold = 2) { notified = it },
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/e"))

            val outcome = notified!!
            outcome.attempts shouldBe 3
            outcome.finalStatusCode shouldBe 503
            outcome.finalError.shouldBeInstanceOf<HttpKlientError.Ikke2xx>()
            outcome.attemptDurations shouldHaveSize 3
        }
    }

    @Test
    fun `excessiveRetries-callback firer ikke under terskelen`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/u")).willReturn(aResponse().withStatus(200).withBody("ok")))
            var notified = false
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2))
                    .withRetryOn(RetryOnServerErrorsAndNetwork)
                    .notifyOnExcessiveRetries(threshold = 1) { notified = true },
            )

            klient.get<String>(URI.create("${wiremock.baseUrl()}/u")).getOrFail()

            notified shouldBe false
        }
    }

    @Test
    fun `excessiveRetries-callback firer for NetworkError-utfall`() = runTest {
        val uri = stoppedServerUri("/n")
        var notified: RetryOutcome? = null
        val klient = testHttpKlient(
            retryConfig = RetryConfig(schedule = recursSchedule(1))
                .withRetryOn(RetryOnServerErrorsAndNetwork)
                .notifyOnExcessiveRetries(threshold = 1) { notified = it },
        )

        klient.get<String>(uri)

        val outcome = notified!!
        outcome.finalStatusCode shouldBe null
        outcome.finalError.shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }

    @Test
    fun `default onExcessiveRetries logger uten aa kaste`() {
        defaultLogExcessiveRetries(
            RetryOutcome(
                attempts = 3,
                totalDuration = 100.milliseconds,
                attemptDurations = listOf(40.milliseconds, 30.milliseconds, 30.milliseconds),
                finalStatusCode = 503,
                finalError = null,
            ),
        )
    }

    @Test
    fun `RetryConfig validerer input`() {
        shouldThrowWithMessage<IllegalArgumentException>("excessiveRetriesThreshold kan ikke være negativ, var -1") {
            RetryConfig(schedule = Schedule.recurs(0L), excessiveRetriesThreshold = -1)
        }
        shouldThrowWithMessage<IllegalArgumentException>("excessiveRetriesThreshold kan ikke være negativ, var -1") {
            RetryConfig(schedule = Schedule.recurs(0L)).notifyOnExcessiveRetries(threshold = -1)
        }
    }

    @Test
    fun `RetryConfig fluent helpers oppdaterer kun relevante felter`() {
        val callback: (RetryOutcome) -> Unit = {}
        val config = RetryConfig(schedule = recursSchedule(3))
            .withRetryOn(RetryOnServerErrorsAndNetwork)
            .notifyOnExcessiveRetries(threshold = 2, onExcessiveRetries = callback)

        config.retryOn shouldBe RetryOnServerErrorsAndNetwork
        config.excessiveRetriesThreshold shouldBe 2
        config.onExcessiveRetries shouldBe callback

        val utenVarsling = config.withoutExcessiveRetriesNotification()

        utenVarsling.schedule shouldBe config.schedule
        utenVarsling.retryOn shouldBe RetryOnServerErrorsAndNetwork
        utenVarsling.excessiveRetriesThreshold shouldBe null
        utenVarsling.onExcessiveRetries shouldBe callback
    }

    @Test
    fun `RetryConfig exponential factory bygger schedule som retry-er`() = runTest {
        val cfg = RetryConfig.exponential(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 10.milliseconds,
            jitter = false,
            random = Random(seed = 1),
        )
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/exp")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(retryConfig = cfg)
            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/exp")).swap().getOrNull()!!
            error.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `RetryConfig exponential med jitter bygger schedule`() = runTest {
        val cfg = RetryConfig.exponential(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 5.milliseconds,
            jitter = true,
            random = Random(seed = 7),
        )
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/jit")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(retryConfig = cfg)
            klient.get<String>(URI.create("${wiremock.baseUrl()}/jit")).swap().getOrNull()!!
                .metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `RetryConfig exponential validerer input`() {
        shouldThrowWithMessage<IllegalArgumentException>("maxRetries kan ikke være negativ, var -1") {
            RetryConfig.exponential(maxRetries = -1)
        }
        shouldThrowWithMessage<IllegalArgumentException>("initialDelay kan ikke være negativ, var -1ms") {
            RetryConfig.exponential(maxRetries = 1, initialDelay = (-1).milliseconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maxDelay (50ms) må være >= initialDelay (100ms)") {
            RetryConfig.exponential(maxRetries = 1, initialDelay = 100.milliseconds, maxDelay = 50.milliseconds)
        }
    }

    @Test
    fun `RetryConfig fixed factory venter mellom forsoek`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/b")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig.fixed(maxRetries = 1, delay = 5.milliseconds),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/b")).swap().getOrNull()!!

            error.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `RetryConfig fixed validerer input`() {
        shouldThrowWithMessage<IllegalArgumentException>("maxRetries kan ikke være negativ, var -1") {
            RetryConfig.fixed(maxRetries = -1, delay = ZERO)
        }
        shouldThrowWithMessage<IllegalArgumentException>("delay kan ikke være negativ, var -1ms") {
            RetryConfig.fixed(maxRetries = 1, delay = (-1).milliseconds)
        }
    }

    @Test
    fun `NeverRetry returnerer false for alle utfall`() {
        NeverRetry(RetryDecisionContext(HttpMethod.GET, 1, AttemptOutcome.Status(500))) shouldBe false
    }

    @Test
    fun `RetryOnServerErrorsAndNetwork tillater idempotente metoder og avviser andre`() {
        val anyOutcome = AttemptOutcome.Status(500)
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.GET, 1, anyOutcome)) shouldBe true
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.HEAD, 1, anyOutcome)) shouldBe true
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.OPTIONS, 1, anyOutcome)) shouldBe true
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.PUT, 1, anyOutcome)) shouldBe true
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.DELETE, 1, anyOutcome)) shouldBe true
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.POST, 1, anyOutcome)) shouldBe false
        RetryOnServerErrorsAndNetwork(RetryDecisionContext(HttpMethod.PATCH, 1, anyOutcome)) shouldBe false
    }

    @Test
    fun `isIdempotent dekker alle metoder`() {
        HttpMethod.GET.isIdempotent() shouldBe true
        HttpMethod.HEAD.isIdempotent() shouldBe true
        HttpMethod.OPTIONS.isIdempotent() shouldBe true
        HttpMethod.PUT.isIdempotent() shouldBe true
        HttpMethod.DELETE.isIdempotent() shouldBe true
        HttpMethod.POST.isIdempotent() shouldBe false
        HttpMethod.PATCH.isIdempotent() shouldBe false
    }

    @Test
    fun `HttpKlientMetadata avviser negative attempts`() {
        shouldThrowWithMessage<IllegalArgumentException>("attempts kan ikke være negativ, var -1") {
            tomMetadata().copy(attempts = -1)
        }
    }

    @Test
    fun `retryable-flagg paa HttpKlientError`() {
        val md = tomMetadata()
        HttpKlientError.Timeout(RuntimeException(), md).retryable shouldBe true
        HttpKlientError.NetworkError(RuntimeException(), md).retryable shouldBe true
        HttpKlientError.InvalidRequest(RuntimeException(), md).retryable shouldBe false
        HttpKlientError.SerializationError(RuntimeException(), md).retryable shouldBe false
        HttpKlientError.DeserializationError(RuntimeException(), "x", 200, md).retryable shouldBe false
        HttpKlientError.Ikke2xx(429, "", md).retryable shouldBe true
        HttpKlientError.Ikke2xx(500, "", md).retryable shouldBe true
        HttpKlientError.Ikke2xx(599, "", md).retryable shouldBe true
        HttpKlientError.Ikke2xx(400, "", md).retryable shouldBe false
        HttpKlientError.Ikke2xx(404, "", md).retryable shouldBe false
        HttpKlientError.Ikke2xx(304, "", md).retryable shouldBe false
        HttpKlientError.AuthError(RuntimeException(), md).retryable shouldBe false
    }

    @Test
    fun `retryable-flagg paa AttemptOutcome`() {
        AttemptOutcome.Status(200).retryable shouldBe false
        AttemptOutcome.Status(404).retryable shouldBe false
        AttemptOutcome.Status(429).retryable shouldBe true
        AttemptOutcome.Status(500).retryable shouldBe true
        AttemptOutcome.Status(503).retryable shouldBe true
        AttemptOutcome.Timeout(RuntimeException()).retryable shouldBe true
        AttemptOutcome.NetworkError(RuntimeException()).retryable shouldBe true
    }

    @Test
    fun `loop retry-er ikke ikke-retryable utfall selv om predikatet sier ja`() = runTest {
        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/g")).willReturn(aResponse().withStatus(404)))
            var predicateCalls = 0
            val klient = testHttpKlient(
                retryConfig = RetryConfig(
                    schedule = recursSchedule(5),
                    retryOn = {
                        predicateCalls++
                        true
                    },
                ),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/g")).swap().getOrNull()!!

            error.metadata.attempts shouldBe 1
            predicateCalls shouldBe 0
        }
    }

    @Test
    fun `Schedule fra Arrow kan brukes direkte uten faktorymetoder`() = runTest {
        // Demonstrerer at konsumenter kan bygge sin egen Schedule.
        val schedule: Schedule<AttemptOutcome, *> =
            Schedule.spaced<AttemptOutcome>(1.milliseconds)
                .zipLeft(Schedule.recurs(2L))

        withWireMock { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/s")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = schedule, retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/s")).swap().getOrNull()!!
            error.metadata.attempts shouldBe 3
        }
    }
}
