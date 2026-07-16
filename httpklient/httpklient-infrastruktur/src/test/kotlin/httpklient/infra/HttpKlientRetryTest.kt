package no.nav.tiltakspenger.libs.httpklient.infra

import arrow.resilience.Schedule
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.TikkendeTidskilde
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientTidsstempler
import no.nav.tiltakspenger.libs.httpklient.infra.kall.HttpMethod
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.AttemptOutcome
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.retry.RetryConfig
import no.nav.tiltakspenger.libs.httpklient.infra.retry.erIdempotent
import no.nav.tiltakspenger.libs.httpklient.infra.retry.toRetryConfig
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpTimeoutException
import kotlin.random.Random
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientRetryTest {
    private val uri = URI.create("http://retry.test/ressurs")
    private val okJson = """{"status":"ok","antall":1}"""

    @Test
    fun `default (Retry Ingen) retry-er ikke`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, "nei")
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        ikke2xx.metadata.attempts shouldBe 1
        ikke2xx.metadata.attemptDurations shouldHaveSize 1
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `respons godtatt av godta retry-es ikke selv om statusen er i retryable-mengden`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503, """{"status":"degradert","antall":0}""")
        // Konsumenten godtar 503 som suksess; da skal retry-loopen ikke brenne budsjett på den.
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 3, delay = ZERO))

        val response = klient.getJson<TestResponseDto>(uri, godta = Statusregel.Eksakt(200, 503)).getOrFail()

        response.statusCode shouldBe 503
        response.metadata.attempts shouldBe 1
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `alle retryable statuskoder retry-es, naboer gjør det ikke`() = runTest {
        suspend fun attemptsForStatus(status: Int): Int {
            val transport = FakeHttpTransport()
            // Retry konsumerer ett køet svar per forsøk; kø nok til begge forsøkene.
            transport.leggIKøStatus(status)
            transport.leggIKøStatus(status)
            val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 2, delay = ZERO))
            return klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!.metadata.attempts
        }

        listOf(408, 425, 429, 500, 502, 503, 504).forEach { status ->
            withClue("status $status skal være retryable") { attemptsForStatus(status) shouldBe 2 }
        }
        listOf(400, 404, 501, 505).forEach { status ->
            withClue("status $status skal ikke være retryable") { attemptsForStatus(status) shouldBe 1 }
        }
    }

    @Test
    fun `retry lykkes på andre forsøk over fake transport`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 2, delay = ZERO))

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.statusCode shouldBe 200
        response.body shouldBe TestResponseDto(status = "ok", antall = 1)
        response.metadata.attempts shouldBe 2
        response.metadata.attemptDurations shouldHaveSize 2
    }

    @Test
    fun `retry lykkes på andre forsøk ende-til-ende via WireMock`() = runTest {
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
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(okJson)),
            )
            val klient = testHttpKlient(retry = Retry.Fast(maksForsøk = 2, delay = ZERO))

            val response = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/y")).getOrFail()

            response.statusCode shouldBe 200
            response.metadata.attempts shouldBe 2
        }
    }

    @Test
    fun `bruker opp alle forsøk og returnerer siste feil`() = runTest {
        val transport = FakeHttpTransport()
        repeat(3) { transport.leggIKøStatus(503) }
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 3, delay = ZERO))

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val ikke2xx = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        ikke2xx.metadata.attempts shouldBe 3
        ikke2xx.metadata.attemptDurations shouldHaveSize 3
    }

    @Test
    fun `POST og PATCH retry-es ikke uten retryIkkeIdempotente - GET og PUT retry-es`() = runTest {
        suspend fun attemptsFor(kall: suspend (HttpKlient) -> Unit): Int {
            val transport = FakeHttpTransport()
            transport.leggIKøStatus(503)
            transport.leggIKøStatus(503)
            val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 2, delay = ZERO))
            kall(klient)
            return transport.mottatteKall.size
        }

        // Idempotens-gaten: POST/PATCH kan ha sideeffekter og retry-es aldri uten eksplisitt opt-in.
        attemptsFor { it.postJsonUtenSvar(uri, TestRequestDto(id = "a", antall = 1)) } shouldBe 1
        attemptsFor { it.patchJsonUtenSvar(uri, TestRequestDto(id = "a", antall = 1)) } shouldBe 1
        attemptsFor { it.getJson<TestResponseDto>(uri) } shouldBe 2
        attemptsFor { it.putJsonUtenSvar(uri, TestRequestDto(id = "a", antall = 1)) } shouldBe 2
    }

    @Test
    fun `retryIkkeIdempotente er eksplisitt opt-in for POST mot dedup-endepunkt`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøTomRespons(statusCode = 201)
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 2, delay = ZERO, retryIkkeIdempotente = true))

        val response = klient.postJsonUtenSvar(uri, TestRequestDto(id = "a", antall = 1)).getOrFail()

        response.statusCode shouldBe 201
        response.metadata.attempts shouldBe 2
    }

    @Test
    fun `Retry Standard har samme idempotens-gate som Fast`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(transport, retry = Retry.Standard(maksForsøk = 3, grunnDelay = ZERO, maksDelay = ZERO))

        klient.postJsonUtenSvar(uri, TestRequestDto(id = "a", antall = 1))

        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `retry på NetworkError fra transporten`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøKast(IOException("connection reset"))
        transport.leggIKøKast(IOException("connection reset"))
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 3, delay = ZERO))

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.metadata.attempts shouldBe 3
    }

    @Test
    fun `CompletionException og ExecutionException pakkes ut før feilmapping`() = runTest {
        // java.net.http kan levere feil pakket i CompletionException/ExecutionException; feiltypen skal bære den underliggende årsaken.
        val transport = FakeHttpTransport()
        transport.leggIKøKast(java.util.concurrent.CompletionException(HttpTimeoutException("pakket timeout")))
        transport.leggIKøKast(java.util.concurrent.ExecutionException(IOException("pakket io-feil")))
        val klient = fakeHttpKlient(transport)

        val timeout = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        timeout.shouldBeInstanceOf<HttpKlientError.Timeout>().throwable.message shouldBe "pakket timeout"

        val nettverksfeil = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        nettverksfeil.shouldBeInstanceOf<HttpKlientError.NetworkError>().throwable.message shouldBe "pakket io-feil"
    }

    @Test
    fun `retry på Timeout fra transporten`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøKast(HttpTimeoutException("timeout"))
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 2, delay = ZERO))

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.metadata.attempts shouldBe 2
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler brukes til virtuell-tid-assertions på retry-delays.
    fun `Retry Fast venter konfigurert tid mellom forsøk`() = runTest {
        val transport = FakeHttpTransport()
        repeat(3) { transport.leggIKøStatus(503) }
        val klient = fakeHttpKlient(transport, retry = Retry.Fast(maksForsøk = 3, delay = 10.milliseconds))

        val før = testScheduler.currentTime
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        val virtuellTidBrukt = testScheduler.currentTime - før

        // 3 forsøk = 2 ventinger à 10 ms i virtuell tid, uten jitter.
        virtuellTidBrukt shouldBe 20
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // testScheduler brukes til virtuell-tid-assertions på retry-delays.
    fun `Retry Standard bruker eksponentiell backoff med jitter innenfor grensene og hardt tak`() = runTest {
        val transport = FakeHttpTransport()
        repeat(3) { transport.leggIKøStatus(503) }
        val klient = fakeHttpKlient(transport, retry = Retry.Standard(maksForsøk = 3, grunnDelay = 100.milliseconds, maksDelay = 150.milliseconds))

        val før = testScheduler.currentTime
        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        val virtuellTidBrukt = testScheduler.currentTime - før

        // Delay 1: 100 ms × jitter(0.5–1.5) cappet på 150 → [50, 150]. Delay 2: 200 ms × jitter cappet → [100, 150]. Totalt [150, 300].
        virtuellTidBrukt shouldBeInRange 150L..300L
    }

    @Test
    fun `toRetryConfig med samme seed gir deterministisk jitter`() = runTest {
        suspend fun førsteDelay(seed: Int): kotlin.time.Duration {
            val schedule = Retry.Standard(maksForsøk = 3, grunnDelay = 100.milliseconds, maksDelay = 10.seconds)
                .toRetryConfig(Random(seed))
                .schedule
            return when (val decision = schedule.invoke(AttemptOutcome.Status(503))) {
                is Schedule.Decision.Continue -> decision.delay
                is Schedule.Decision.Done -> error("forventet Continue")
            }
        }

        førsteDelay(seed = 42) shouldBe førsteDelay(seed = 42)
        // Med et annet seed skal jitteret (praktisk talt alltid) gi en annen delay; likhet her ville betydd at random-parameteren ikke brukes.
        (førsteDelay(seed = 42) == førsteDelay(seed = 1337)) shouldBe false
    }

    @Test
    fun `Retry Fast og Standard validerer input`() {
        shouldThrowWithMessage<IllegalArgumentException>("maksForsøk må være minst 1, var 0") {
            Retry.Fast(maksForsøk = 0)
        }
        shouldThrowWithMessage<IllegalArgumentException>("delay kan ikke være negativ, var -1ms") {
            Retry.Fast(maksForsøk = 1, delay = (-1).milliseconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maksForsøk må være minst 1, var 0") {
            Retry.Standard(maksForsøk = 0)
        }
        shouldThrowWithMessage<IllegalArgumentException>("grunnDelay kan ikke være negativ, var -1ms") {
            Retry.Standard(maksForsøk = 1, grunnDelay = (-1).milliseconds)
        }
        shouldThrowWithMessage<IllegalArgumentException>("maksDelay (50ms) må være >= grunnDelay (100ms)") {
            Retry.Standard(maksForsøk = 1, grunnDelay = 100.milliseconds, maksDelay = 50.milliseconds)
        }
    }

    @Test
    fun `loop retry-er ikke ikke-retryable utfall selv om predikatet sier ja`() = runTest {
        // Den harde gaten ligger foran retryOn-predikatet: et ikke-retryable utfall (404) skal aldri konsultere predikatet.
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(404)
        var predicateCalls = 0
        val retryConfig = RetryConfig(
            schedule = Schedule.recurs(5L),
            retryOn = {
                predicateCalls++
                true
            },
        )
        val klient = fakeHttpKlient(transport)
        val request = byggHttpKlientRequest(
            metode = HttpMethod.GET,
            uri = uri,
            headere = emptyList(),
            bearerToken = null,
            godta = Statusregel.Alle2xx,
            body = HttpKlientRequest.Body.Ingen,
            responsFormat = ResponsFormat.IngenBody,
        )
        val prepared = request.toJavaHttpRequest(1.seconds, request.headers, HttpKlientTidsstempler.INGEN).getOrNull()!!

        val result = no.nav.tiltakspenger.libs.httpklient.infra.retry.RetryExecutor(klient.clock, klient.config.timeSource)
            .execute(request, retryConfig, isSuccessfulResponse = request::erSuksessStatus) { klient.runSingleAttempt(prepared.request) }

        result.attempts shouldBe 1
        predicateCalls shouldBe 0
    }

    @Test
    fun `erIdempotent dekker alle metoder`() {
        HttpMethod.GET.erIdempotent() shouldBe true
        HttpMethod.PUT.erIdempotent() shouldBe true
        HttpMethod.POST.erIdempotent() shouldBe false
        HttpMethod.PATCH.erIdempotent() shouldBe false
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
    fun `per-forsøk varigheter måles monotont via TimeSource`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(503)
        transport.leggIKøStatus(503)
        val klient = fakeHttpKlient(
            transport = transport,
            retry = Retry.Fast(maksForsøk = 2, delay = ZERO),
            timeSource = TikkendeTidskilde(),
        )

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        // TikkendeTidskilde rykker den delte forløpte tiden 1 sekund per avlesning (elapsedNow); markNow rykker den ikke.
        // RetryExecutor leser varighet én gang per forsøk pluss én gang for total-vinduet, så hvert forsøk «varer» 1 sekund.
        // Total-vinduet spenner over alle tre avlesningene (to forsøk + total) og blir derfor 3 sekunder.
        error.metadata.attemptDurations shouldContainExactly listOf(1.seconds, 1.seconds)
        error.metadata.totalDuration shouldBe 3.seconds
    }
}
