package no.nav.tiltakspenger.libs.httpklient

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.retry.RetryConfig
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpTimeoutException
import kotlin.time.Duration

/**
 * Verifiserer transport-sømmen: en test som bytter ut [HttpTransport] skal kjøre HELE den reelle pipelinen (auth-materialisering, retry, statusevaluering, dekoding, metadata) — ikke en emulering av den.
 * [JavaHttpTransport] selv dekkes av hele WireMock-suiten, som går via default-transporten.
 */
internal class HttpTransportTest {

    /**
     * Kø-basert fake-transport: svarer med køede responser/exceptions og tar opp requestene den mottar.
     * Forløper for fake-transporten som skal erstatte `HttpKlientFake` når sømmen blir public.
     */
    private class KøTransport : HttpTransport {
        val mottatteRequests = mutableListOf<HttpRequest>()
        private val kø = ArrayDeque<() -> TransportRespons>()

        fun leggIKø(statusCode: Int, body: String, headere: Map<String, List<String>> = jsonContentType) {
            leggIKøBytes(statusCode = statusCode, body = body.toByteArray(), headere = headere)
        }

        fun leggIKøBytes(statusCode: Int, body: ByteArray, headere: Map<String, List<String>>) {
            kø += { TransportRespons(statusCode = statusCode, headere = headere, body = body) }
        }

        fun leggIKøKast(throwable: Throwable) {
            kø += { throw throwable }
        }

        override suspend fun send(request: HttpRequest): TransportRespons {
            mottatteRequests += request
            val neste = checkNotNull(kø.removeFirstOrNull()) {
                "KøTransport mangler køet svar for ${request.method()} ${request.uri()}"
            }
            return neste()
        }

        companion object {
            val jsonContentType = mapOf("Content-Type" to listOf("application/json"))
        }
    }

    private fun klientMedTransport(
        transport: HttpTransport,
        retryConfig: RetryConfig = RetryConfig.None,
        authTokenProvider: AuthTokenProvider? = null,
    ): HttpKlient = JavaHttpKlient(
        config = HttpKlient.HttpKlientConfig(fixedClock).apply {
            this.defaultRetry = retryConfig
            this.authTokenProvider = authTokenProvider
        },
        transport = transport,
    )

    // ASCII-hostnavn med vilje: JDK-ens HttpRequest-validering kjører gjennom sømmen også i test, og avviser ikke-ASCII-hostnavn (f.eks. `transport-søm.test`) med InvalidRequest.
    private val uri = URI.create("http://transport-som.test/ressurs")

    @Test
    fun `hele pipelinen kjører over en byttet transport - JSON deserialiseres og metadata bygges av produksjonskoden`() = runTest {
        val transport = KøTransport()
        transport.leggIKø(statusCode = 200, body = """{"status":"ok","antall":2}""")
        val klient = klientMedTransport(transport)

        val respons = klient.get<TestResponseDto>(uri).getOrFail()

        respons.body shouldBe TestResponseDto(status = "ok", antall = 2)
        respons.statusCode shouldBe 200
        respons.metadata.rawResponseString shouldBe """{"status":"ok","antall":2}"""
        respons.metadata.attempts shouldBe 1
        transport.mottatteRequests.single().uri() shouldBe uri
    }

    @Test
    fun `statusregelen evalueres av pipelinen, ikke transporten - 500 fra transporten gir UventetStatus`() = runTest {
        val transport = KøTransport()
        transport.leggIKø(statusCode = 500, body = "teknisk feil")
        val klient = klientMedTransport(transport)

        val feil = klient.get<TestResponseDto>(uri).swap().getOrNull()!!

        val uventetStatus = feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 500
        uventetStatus.body shouldBe "teknisk feil"
    }

    @Test
    fun `retry-motoren kjører over transporten - 503 etterfulgt av 200 gir suksess på andre forsøk`() = runTest {
        val transport = KøTransport()
        transport.leggIKø(statusCode = 503, body = "midlertidig utilgjengelig")
        transport.leggIKø(statusCode = 200, body = """{"status":"ok","antall":1}""")
        val klient = klientMedTransport(transport, retryConfig = RetryConfig.fixed(maxRetries = 1, delay = Duration.ZERO))

        val respons = klient.get<TestResponseDto>(uri).getOrFail()

        respons.body shouldBe TestResponseDto(status = "ok", antall = 1)
        respons.metadata.attempts shouldBe 2
        transport.mottatteRequests.size shouldBe 2
    }

    @Test
    fun `exception fra transporten mappes til NetworkError av pipelinen`() = runTest {
        val transport = KøTransport()
        transport.leggIKøKast(IOException("simulert nettverksfeil"))
        val klient = klientMedTransport(transport)

        val feil = klient.get<TestResponseDto>(uri).swap().getOrNull()!!

        val nettverksfeil = feil.shouldBeInstanceOf<HttpKlientError.NetworkError>()
        nettverksfeil.throwable.message shouldBe "simulert nettverksfeil"
        nettverksfeil.metadata.attempts shouldBe 1
    }

    @Test
    fun `HttpTimeoutException fra transporten mappes til Timeout av pipelinen`() = runTest {
        val transport = KøTransport()
        transport.leggIKøKast(HttpTimeoutException("simulert timeout"))
        val klient = klientMedTransport(transport)

        val feil = klient.get<TestResponseDto>(uri).swap().getOrNull()!!

        val timeout = feil.shouldBeInstanceOf<HttpKlientError.Timeout>()
        timeout.throwable.message shouldBe "simulert timeout"
    }

    @Test
    fun `binære bytes bevares eksakt gjennom TransportRespons, og metadata får binær-placeholderen`() = runTest {
        // "%PDF" etterfulgt av bytes som ikke er gyldig UTF-8 — ville blitt korruptert av en tekst-rundtur.
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, -1, 0x00, -2)
        val transport = KøTransport()
        transport.leggIKøBytes(statusCode = 200, body = pdfBytes, headere = mapOf("Content-Type" to listOf("application/pdf")))
        val klient = klientMedTransport(transport)

        val respons = klient.get<ByteArray>(uri).getOrFail()

        respons.body.toList() shouldBe pdfBytes.toList()
        respons.metadata.rawResponseString shouldBe "<binær respons, 7 bytes>"
    }

    @Test
    fun `auth-provideren materialiseres til Authorization-header i requesten transporten mottar`() = runTest {
        val transport = KøTransport()
        transport.leggIKø(statusCode = 200, body = """{"status":"ok","antall":0}""")
        val klient = klientMedTransport(
            transport = transport,
            authTokenProvider = authTokenProvider { testAccessToken("hemmelig-token") },
        )

        klient.get<TestResponseDto>(uri).getOrFail()

        transport.mottatteRequests.single().headers().firstValue("Authorization").get() shouldBe "Bearer hemmelig-token"
    }
}
