package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.stoppedServerUri
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class HttpKlientFaultTest {
    @Test
    fun `returnerer NetworkError når server ikke kan kontaktes`() = runTest {
        val uri = stoppedServerUri("/stoppet")
        val klient = testHttpKlient()

        val error = klient.getPdf(uri).swap().getOrNull()!!

        val networkError = error.shouldBeInstanceOf<HttpKlientError.NetworkError>()
        networkError.metadata.rawRequestString shouldBe "GET $uri\nAccept: application/pdf"
        networkError.metadata.statusCode shouldBe null
    }

    @Test
    fun `returnerer NetworkError når tilkoblingen resettes`() = runTest {
        assertFaultGirNetworkError { socket ->
            // SO_LINGER=0 gjør at close() sender TCP RST i stedet for FIN.
            socket.setSoLinger(true, 0)
            socket.close()
        }
    }

    @Test
    fun `returnerer NetworkError ved tom respons`() = runTest {
        assertFaultGirNetworkError { socket ->
            socket.close()
        }
    }

    @Test
    fun `returnerer NetworkError ved malformet chunket respons`() = runTest {
        assertFaultGirNetworkError { socket ->
            socket.getOutputStream().skrivOgFlush("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nIKKE-EN-CHUNK\r\n")
            socket.close()
        }
    }

    @Test
    fun `returnerer NetworkError ved søppeldata før lukking`() = runTest {
        assertFaultGirNetworkError { socket ->
            socket.getOutputStream().skrivOgFlush("dette er ikke http")
            socket.close()
        }
    }

    @Test
    fun `propagerer CancellationException når coroutine kanselleres under request`() {
        // Vi må bruke runBlocking (ikke runTest) for å få ekte tid og cancellation propagering gjennom Dispatchers.IO.
        // wiremock holder responsen i 2 sekunder, vi kansellerer etter 100ms.
        runBlocking {
            withWireMockServer { wiremock ->
                wiremock.stubFor(
                    get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(2_000).withBody("aldri")),
                )
                val klient = testHttpKlient(timeout = 5.seconds)

                val deferred = async(Dispatchers.IO) {
                    klient.getPdf(URI.create("${wiremock.baseUrl()}/slow"))
                }
                delay(100.milliseconds)
                deferred.cancel(CancellationException("test"))

                shouldThrow<CancellationException> {
                    deferred.await()
                }
            }
        }
    }
}

/**
 * Starter en rå TCP-server på IPv4-loopback som leser requesten ferdig og deretter utfører [fault] på socketen, og asserter at klienten oversetter bruddet til [HttpKlientError.NetworkError].
 * Vi bruker rå sockets i stedet for WireMocks `Fault`-enum fordi jetty12-varianten av WireMock ikke støtter fault injection — den svarer med en ren HTTP 500 i stedet for å bryte forbindelsen.
 * Accept-løkka håndterer flere tilkoblinger, slik at en eventuell klient-retry treffer samme fault i stedet for å henge.
 */
private suspend fun assertFaultGirNetworkError(fault: (Socket) -> Unit) {
    withContext(Dispatchers.IO) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            thread(isDaemon = true, name = "fault-server") {
                try {
                    while (true) {
                        server.accept().use { socket ->
                            socket.lesRequestHeadere()
                            fault(socket)
                        }
                    }
                } catch (_: Exception) {
                    // ServerSocket.close() avbryter accept() — normal stopp.
                }
            }
            val klient = testHttpKlient(timeout = 1_000.milliseconds)
            val uri = URI.create("http://127.0.0.1:${server.localPort}/fault")

            val error = klient.getPdf(uri).swap().getOrNull()!!

            error.shouldBeInstanceOf<HttpKlientError.NetworkError>()
        }
    }
}

/** Leser til slutten av request-headerne (tom linje), slik at faulten treffer etter at klienten har sendt hele requesten. */
private fun Socket.lesRequestHeadere() {
    val inn = getInputStream()
    var sisteFireBytes = 0
    while (true) {
        val byte = inn.read()
        if (byte == -1) return
        sisteFireBytes = (sisteFireBytes shl 8) or byte
        if (sisteFireBytes == 0x0D0A0D0A) return
    }
}

private fun OutputStream.skrivOgFlush(tekst: String) {
    write(tekst.toByteArray(Charsets.ISO_8859_1))
    flush()
}
