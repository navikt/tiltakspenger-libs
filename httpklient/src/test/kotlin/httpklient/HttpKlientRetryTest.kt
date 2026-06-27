package no.nav.tiltakspenger.libs.httpklient
import arrow.resilience.Schedule
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.stoppedServerUri
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.retry.AttemptOutcome
import no.nav.tiltakspenger.libs.httpklient.retry.NeverRetry
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import no.nav.tiltakspenger.libs.httpklient.retry.RetryDecisionContext
import no.nav.tiltakspenger.libs.httpklient.retry.RetryOnServerErrorsAndNetwork
import no.nav.tiltakspenger.libs.httpklient.retry.RetryOutcome
import no.nav.tiltakspenger.libs.httpklient.retry.defaultLogExcessiveRetries
import no.nav.tiltakspenger.libs.httpklient.retry.isIdempotent
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.random.Random
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Liten hjelper for tester som bare trenger en schedule som tillater N retries uten ventetid.
 */
private fun recursSchedule(maxRetries: Int): Schedule<AttemptOutcome, *> =
    Schedule.recurs(maxRetries.toLong())

internal class HttpKlientRetryTest {

    @Test
    fun `default RetryConfig retry-er ikke`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/x")).willReturn(aResponse().withStatus(503).withBody("nei")))
            val klient = testHttpKlient()

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/x")).swap().getOrNull()!!

            val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            ikke2xx.metadata.attempts shouldBe 1
            ikke2xx.metadata.attemptDurations shouldHaveSize 1
        }
    }

    @Test
    fun `RetryConfig uten eksplisitt retryOn retry-er ikke som default`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/default-retryon")).willReturn(aResponse().withStatus(503)))
            // Default retryOn er NeverRetry: en bar RetryConfig(schedule = ...) retry-er ikke før konsumenten eksplisitt opt-iner.
            // GET er idempotent og 503 er en retry-bar status, så det er kun default-predikatet (NeverRetry) som hindrer retry her.
            val klient = testHttpKlient(retryConfig = RetryConfig(schedule = recursSchedule(2)))

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/default-retryon")).swap().getOrNull()!!

            error.metadata.attempts shouldBe 1
        }
    }

    @Test
    fun `respons godtatt av successStatus retry-es ikke selv om statusen er i retryable-mengden`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/503-er-ok")).willReturn(aResponse().withStatus(503).withBody("degradert")))
            // Konsumenten godtar 503 som suksess; da skal retry-loopen ikke brenne budsjett på den.
            val klient = testHttpKlient(
                successStatus = { it == 503 || it in 200..299 },
                retryConfig = RetryConfig(schedule = recursSchedule(3), retryOn = { true }),
            )

            val response = klient.get<String>(URI.create("${wiremock.baseUrl()}/503-er-ok")).getOrFail()

            response.statusCode shouldBe 503
            response.body shouldBe "degradert"
            response.metadata.attempts shouldBe 1
            wiremock.verify(1, getRequestedFor(urlEqualTo("/503-er-ok")))
        }
    }

    @Test
    fun `alle retryable statuskoder retry-es, naboer gjør det ikke`() = runTest {
        suspend fun attemptsForStatus(status: Int): Int = withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/status")).willReturn(aResponse().withStatus(status)))
            val klient = testHttpKlient(retryConfig = RetryConfig(schedule = recursSchedule(1), retryOn = { true }))
            klient.get<String>(URI.create("${wiremock.baseUrl()}/status")).swap().getOrNull()!!.metadata.attempts
        }

        listOf(408, 425, 429, 500, 502, 503, 504).forEach { status ->
            withClue("status $status skal være retryable") { attemptsForStatus(status) shouldBe 2 }
        }
        listOf(400, 404, 501, 505).forEach { status ->
            withClue("status $status skal ikke være retryable") { attemptsForStatus(status) shouldBe 1 }
        }
    }

    @Test
    fun `retry lykkes på andre forsøk`() = runTest {
        withWireMockServer { wiremock ->
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
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/z")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(2), retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/z")).swap().getOrNull()!!

            val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            ikke2xx.metadata.attempts shouldBe 3
            ikke2xx.metadata.attemptDurations shouldHaveSize 3
        }
    }

    @Test
    fun `4xx blir ikke retry-et med default-predikat for idempotente metoder`() = runTest {
        withWireMockServer { wiremock ->
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
        withWireMockServer { wiremock ->
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
        withWireMockServer { wiremock ->
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
    fun `retry på NetworkError`() = runTest {
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
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/o")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(retryConfig = RetryConfig.None)

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/o")) {
                retryConfig = RetryConfig(schedule = recursSchedule(1), retryOn = RetryOnServerErrorsAndNetwork)
            }.swap().getOrNull()!!

            error.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `excessiveRetries-callback firer når terskelen er passert`() = runTest {
        withWireMockServer { wiremock ->
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
            outcome.finalError.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
            outcome.attemptDurations shouldHaveSize 3
        }
    }

    @Test
    fun `excessiveRetries-callback firer ikke under terskelen`() = runTest {
        withWireMockServer { wiremock ->
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
    fun `default onExcessiveRetries logger uten å kaste`() {
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
        shouldThrowWithMessage<IllegalArgumentException>("excessiveRetriesThreshold må være minst 1 (terskelen telles i antall retries), var -1") {
            RetryConfig(schedule = Schedule.recurs(0L), excessiveRetriesThreshold = -1)
        }
        shouldThrowWithMessage<IllegalArgumentException>("excessiveRetriesThreshold må være minst 1 (terskelen telles i antall retries), var -1") {
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
        withWireMockServer { wiremock ->
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
        withWireMockServer { wiremock ->
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
    fun `RetryConfig fixed factory venter mellom forsøk`() = runTest {
        withWireMockServer { wiremock ->
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
    fun `retryable-flagg på HttpKlientError`() {
        val md = tomMetadata()
        HttpKlientError.Timeout(RuntimeException(), md).retryable shouldBe true
        HttpKlientError.NetworkError(RuntimeException(), md).retryable shouldBe true
        HttpKlientError.InvalidRequest(RuntimeException(), md).retryable shouldBe false
        HttpKlientError.SerializationError(RuntimeException(), md).retryable shouldBe false
        HttpKlientError.DeserializationError(RuntimeException(), "x", 200, md).retryable shouldBe false
        HttpKlientError.UventetStatus(408, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(425, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(429, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(500, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(502, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(503, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(504, "", md).retryable shouldBe true
        HttpKlientError.UventetStatus(501, "", md).retryable shouldBe false
        HttpKlientError.UventetStatus(599, "", md).retryable shouldBe false
        HttpKlientError.UventetStatus(400, "", md).retryable shouldBe false
        HttpKlientError.UventetStatus(404, "", md).retryable shouldBe false
        HttpKlientError.UventetStatus(304, "", md).retryable shouldBe false
        HttpKlientError.AuthError(RuntimeException(), md).retryable shouldBe false
    }

    @Test
    fun `retryable-flagg på AttemptOutcome`() {
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
        withWireMockServer { wiremock ->
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

        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/s")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = schedule, retryOn = RetryOnServerErrorsAndNetwork),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/s")).swap().getOrNull()!!
            error.metadata.attempts shouldBe 3
        }
    }

    @Test
    fun `per-forsøk varigheter måles mot klokka`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/tikk")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig(schedule = recursSchedule(1), retryOn = RetryOnServerErrorsAndNetwork),
                clock = TikkendeKlokke(),
            )

            val error = klient.get<String>(URI.create("${wiremock.baseUrl()}/tikk")).swap().getOrNull()!!

            // TikkendeKlokke tikker 1 sekund per instant()-kall, og RetryExecutor leser klokka én gang ved start og slutt av hvert forsøk.
            // Det gir deterministiske varigheter: hvert forsøk «varer» 1 sekund, og total tid spenner over alle seks instant()-kallene.
            error.metadata.attemptDurations shouldContainExactly listOf(1.seconds, 1.seconds)
            error.metadata.totalDuration shouldBe 5.seconds
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `fixed backoff venter konfigurert tid mellom forsøk`() = runTest {
        withWireMockServer { wiremock ->
            wiremock.stubFor(get(urlEqualTo("/backoff")).willReturn(aResponse().withStatus(503)))
            val klient = testHttpKlient(
                retryConfig = RetryConfig.fixed(maxRetries = 2, delay = 10.milliseconds, retryOn = RetryOnServerErrorsAndNetwork),
            )

            val før = testScheduler.currentTime
            klient.get<String>(URI.create("${wiremock.baseUrl()}/backoff")).swap().getOrNull()!!
            val virtuellTidBrukt = testScheduler.currentTime - før

            // 3 forsøk = 2 retries, hver med 10 ms backoff i virtuell tid (de faktiske HTTP-kallene bruker ikke virtuell tid).
            virtuellTidBrukt shouldBe 20
        }
    }
}
