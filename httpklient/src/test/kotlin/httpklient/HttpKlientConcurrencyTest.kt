package no.nav.tiltakspenger.libs.httpklient

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
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientConcurrencyTest {
    @Test
    fun `samme HttpKlient-instans kan brukes fra mange coroutines parallelt`() {
        val antallRequests = 50
        runBlocking {
            withWireMock { wiremock ->
                repeat(antallRequests) { i ->
                    wiremock.stubFor(
                        get(urlEqualTo("/parallell/$i")).willReturn(
                            aResponse().withStatus(200).withBody("svar-$i"),
                        ),
                    )
                }
                val klient = testHttpKlient(connectTimeout = 5.seconds, timeout = 5.seconds)
                val antallFullfort = AtomicInteger(0)

                val resultater = withContext(Dispatchers.IO) {
                    (0 until antallRequests).map { i ->
                        async {
                            klient.get<String>(URI.create("${wiremock.baseUrl()}/parallell/$i")).getOrFail().body
                                .also { antallFullfort.incrementAndGet() }
                        }
                    }.awaitAll()
                }

                antallFullfort.get() shouldBe antallRequests
                resultater shouldBe (0 until antallRequests).map { "svar-$it" }
            }
        }
    }
}
