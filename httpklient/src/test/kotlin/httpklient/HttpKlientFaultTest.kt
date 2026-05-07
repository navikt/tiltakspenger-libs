package no.nav.tiltakspenger.libs.httpklient

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientFaultTest {
    @Test
    fun `returnerer NetworkError naar server ikke kan kontaktes`() = runTest {
        val uri = stoppedServerUri("/stoppet")
        val klient = testHttpKlient()

        val error = klient.get<String>(uri).swap().getOrNull()!!

        val networkError = error.shouldBeInstanceOf<HttpKlientError.NetworkError>()
        networkError.metadata.rawRequestString shouldBe "GET $uri"
        networkError.metadata.statusCode shouldBe null
    }

    @Test
    fun `returnerer NetworkError ved CONNECTION_RESET_BY_PEER`() = runTest {
        assertFaultGirNetworkError(Fault.CONNECTION_RESET_BY_PEER, "/reset")
    }

    @Test
    fun `returnerer NetworkError ved EMPTY_RESPONSE`() = runTest {
        assertFaultGirNetworkError(Fault.EMPTY_RESPONSE, "/empty")
    }

    @Test
    fun `returnerer NetworkError ved MALFORMED_RESPONSE_CHUNK`() = runTest {
        assertFaultGirNetworkError(Fault.MALFORMED_RESPONSE_CHUNK, "/malformed")
    }

    @Test
    fun `returnerer NetworkError ved RANDOM_DATA_THEN_CLOSE`() = runTest {
        assertFaultGirNetworkError(Fault.RANDOM_DATA_THEN_CLOSE, "/random")
    }

    @Test
    fun `propagerer CancellationException naar coroutine kanselleres under request`() {
        // Vi må bruke runBlocking (ikke runTest) for å få ekte tid og cancellation propagering
        // gjennom Dispatchers.IO. wiremock holder responsen i 2 sekunder, vi kansellerer etter 100ms.
        runBlocking {
            withWireMock { wiremock ->
                wiremock.stubFor(
                    get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(2_000).withBody("aldri")),
                )
                val klient = testHttpKlient(timeout = 5.seconds)

                val deferred = async(Dispatchers.IO) {
                    klient.get<String>(URI.create("${wiremock.baseUrl()}/slow"))
                }
                delay(100)
                deferred.cancel(CancellationException("test"))

                shouldThrow<CancellationException> {
                    deferred.await()
                }
            }
        }
    }
}

private suspend fun assertFaultGirNetworkError(fault: Fault, path: String) {
    withWireMock { wiremock ->
        wiremock.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withFault(fault)))
        val klient = testHttpKlient(timeout = 1_000.milliseconds)

        val error = klient.get<String>(URI.create("${wiremock.baseUrl()}$path")).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }
}
