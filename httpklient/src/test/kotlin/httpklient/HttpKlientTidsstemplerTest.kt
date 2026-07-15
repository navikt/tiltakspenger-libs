package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.TikkendeKlokke
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.nå
import no.nav.tiltakspenger.libs.common.stoppedServerUri
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientTidsstemplerTest {
    private val okJson = """{"status":"ok","antall":1}"""

    @Test
    fun `vellykket kall uten auth-provider setter request- og respons-tidsstempler men ikke auth`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val response = klient.getJson<TestResponseDto>(URI.create("http://tid.test/ok")).getOrFail()

        val tidsstempler = response.metadata.tidsstempler
        tidsstempler.authStartet.shouldBeNull()
        tidsstempler.authFullført.shouldBeNull()
        tidsstempler.requestSendt.shouldNotBeNull()
        tidsstempler.responsMottatt.shouldNotBeNull()
    }

    @Test
    fun `vellykket kall med auth-provider setter alle fire tidsstempler i stigende rekkefølge`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        // TikkendeKlokke tikker 1 sekund per instant()-kall, så tidsstemplene blir strengt stigende.
        val klient = fakeHttpKlient(
            transport = transport,
            clock = TikkendeKlokke(),
            auth = KlientAuth.System(authTokenProvider { testAccessToken("t") }),
        )

        val response = klient.getJson<TestResponseDto>(URI.create("http://tid.test/a")).getOrFail()

        val tidsstempler = response.metadata.tidsstempler
        val authStartet = tidsstempler.authStartet.shouldNotBeNull()
        val authFullført = tidsstempler.authFullført.shouldNotBeNull()
        val requestSendt = tidsstempler.requestSendt.shouldNotBeNull()
        val responsMottatt = tidsstempler.responsMottatt.shouldNotBeNull()

        (authStartet < authFullført) shouldBe true
        (authFullført < requestSendt) shouldBe true
        (requestSendt < responsMottatt) shouldBe true
    }

    @Test
    fun `auth-feil setter auth-tidsstempler men verken request eller respons`() = runTest {
        val klient = fakeHttpKlient(
            transport = FakeHttpTransport(),
            clock = TikkendeKlokke(),
            auth = KlientAuth.System(authTokenProvider { throw IllegalStateException("token-endepunkt nede") }),
        )

        val error = klient.getJson<TestResponseDto>(URI.create("http://tid.test/e")).swap().getOrNull()!!

        val tidsstempler = error.shouldBeInstanceOf<HttpKlientError.AuthError>().metadata.tidsstempler
        val authStartet = tidsstempler.authStartet.shouldNotBeNull()
        val authFullført = tidsstempler.authFullført.shouldNotBeNull()
        (authStartet < authFullført) shouldBe true
        tidsstempler.requestSendt.shouldBeNull()
        tidsstempler.responsMottatt.shouldBeNull()
    }

    @Test
    fun `nettverksfeil setter requestSendt men ikke responsMottatt`() = runTest {
        val uri = stoppedServerUri("/stoppet")
        val klient = testHttpKlient(timeout = 500.milliseconds)

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val tidsstempler = error.shouldBeInstanceOf<HttpKlientError.NetworkError>().metadata.tidsstempler
        tidsstempler.requestSendt.shouldNotBeNull()
        tidsstempler.responsMottatt.shouldBeNull()
    }

    @Test
    fun `tidsstempler er lesbare via convenience-aksessor på error`() = runTest {
        val uri = stoppedServerUri("/stoppet2")
        val klient = testHttpKlient(timeout = 500.milliseconds)

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        error.tidsstempler shouldBe error.metadata.tidsstempler
    }

    @Test
    fun `respons på første forsøk men timeout på siste gir requestSendt men ikke responsMottatt`() = runTest {
        withWireMockServer { wiremock ->
            // Forsøk 1 gir en reell 503-respons; retry-forsøket henger forbi timeouten og gir en feil uten respons.
            wiremock.stubFor(
                get(urlEqualTo("/blandet"))
                    .inScenario("blandet").whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("treg"),
            )
            wiremock.stubFor(
                get(urlEqualTo("/blandet"))
                    .inScenario("blandet").whenScenarioStateIs("treg")
                    .willReturn(aResponse().withStatus(200).withFixedDelay(3000)),
            )
            val klient = testHttpKlient(
                timeout = 500.milliseconds,
                retry = Retry.Fast(maksForsøk = 2, delay = Duration.ZERO),
            )

            val error = klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/blandet")).swap().getOrNull()!!

            error.metadata.attempts shouldBe 2
            val tidsstempler = error.metadata.tidsstempler
            // Selv om forsøk 1 ga en respons, reflekterer metadata det ENDELIGE utfallet — en feil uten respons.
            tidsstempler.requestSendt.shouldNotBeNull()
            tidsstempler.responsMottatt.shouldBeNull()
        }
    }

    @Test
    fun `vellykket kall uten provider har ingen auth-tidsstempler selv med fixedClock`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport, clock = fixedClock)

        val response = klient.getJson<TestResponseDto>(URI.create("http://tid.test/fixed")).getOrFail()

        // Med fixedClock er alle instant()-kall like, så request/respons-tidsstemplene er like men fortsatt satt.
        val tidsstempler = response.metadata.tidsstempler
        tidsstempler.requestSendt shouldBe nå(fixedClock)
        tidsstempler.responsMottatt shouldBe nå(fixedClock)
    }
}
