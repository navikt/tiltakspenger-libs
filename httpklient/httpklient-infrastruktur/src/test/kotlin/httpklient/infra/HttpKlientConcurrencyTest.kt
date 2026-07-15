package no.nav.tiltakspenger.libs.httpklient.infra
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientConcurrencyTest {
    @Test
    fun `samme HttpKlient-instans kan brukes fra mange coroutines parallelt`() {
        val antallRequests = 50
        runBlocking {
            withWireMockServer { wiremock ->
                repeat(antallRequests) { i ->
                    wiremock.stubFor(
                        get(urlEqualTo("/parallell/$i")).willReturn(
                            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"status":"svar","antall":$i}"""),
                        ),
                    )
                }
                val klient = testHttpKlient(connectTimeout = 5.seconds, timeout = 5.seconds)
                val antallFullfort = AtomicInteger(0)

                val resultater = withContext(Dispatchers.IO) {
                    (0 until antallRequests).map { i ->
                        async {
                            klient.getJson<TestResponseDto>(URI.create("${wiremock.baseUrl()}/parallell/$i")).getOrFail().body.antall
                                .also { antallFullfort.incrementAndGet() }
                        }
                    }.awaitAll()
                }

                antallFullfort.get() shouldBe antallRequests
                resultater shouldBe (0 until antallRequests).toList()
            }
        }
    }
}
