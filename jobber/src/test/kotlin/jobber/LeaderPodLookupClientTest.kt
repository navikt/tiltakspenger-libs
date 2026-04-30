package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.milliseconds

internal class LeaderPodLookupClientTest {
    private val logger = KotlinLogging.logger { }

    @Test
    fun `returnerer true når lokal pod er leder`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"name":"pod-1"}"""),
                ),
            )

            LeaderPodLookupClient(
                electorPath = wiremock.baseUrl(),
                logger = logger,
                connectTimeout = 100.milliseconds,
            ).amITheLeader("pod-1") shouldBe true.right()
        }
    }

    @Test
    fun `returnerer false når annen pod er leder`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"name":"pod-2"}"""),
                ),
            )

            LeaderPodLookupClient(
                electorPath = wiremock.baseUrl(),
                logger = logger,
                connectTimeout = 100.milliseconds,
            ).amITheLeader("pod-1") shouldBe false.right()
        }
    }

    @Test
    fun `støtter elector path uten protokoll`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"name":"pod-1"}"""),
                ),
            )

            LeaderPodLookupClient(
                electorPath = "localhost:${wiremock.port()}",
                logger = logger,
                connectTimeout = 100.milliseconds,
            ).amITheLeader("pod-1") shouldBe true.right()
        }
    }

    @Test
    fun `returnerer Ikke2xx ved status 500`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(500)
                        .withBody("feil"),
                ),
            )

            LeaderPodLookupClient(
                electorPath = wiremock.baseUrl(),
                logger = logger,
                connectTimeout = 100.milliseconds,
            ).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.Ikke2xx(500, "feil").left()
        }
    }

    @Test
    fun `returnerer ukjent svar når navn mangler`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"leader":"pod-1"}"""),
                ),
            )

            LeaderPodLookupClient(
                electorPath = wiremock.baseUrl(),
                logger = logger,
                connectTimeout = 100.milliseconds,
            ).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
        }
    }

    @Test
    fun `returnerer kunne ikke kontakte leader elector container ved ugyldig endpoint og default timeout`() {
        LeaderPodLookupClient(
            electorPath = "http://[",
            logger = logger,
        ).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
    }

    @Test
    fun `hurtigbufrer lederstatus etter første vellykkede treff`() {
        withWireMockServer { wiremock ->
            wiremock.stubFor(
                get(urlEqualTo("/")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"name":"pod-1"}"""),
                ),
            )
            val client = LeaderPodLookupClient(
                electorPath = wiremock.baseUrl(),
                logger = logger,
                connectTimeout = 100.milliseconds,
            )

            client.amITheLeader("pod-1") shouldBe true.right()

            client.amITheLeader("pod-1") shouldBe true.right()
            wiremock.verify(1, getRequestedFor(urlEqualTo("/")))
        }
    }

    @Test
    fun `isSuccess returnerer false for status under 200`() {
        val response = mockk<HttpResponse<String>>()
        every { response.statusCode() } returns 199

        response.isSuccess() shouldBe false
    }
}
