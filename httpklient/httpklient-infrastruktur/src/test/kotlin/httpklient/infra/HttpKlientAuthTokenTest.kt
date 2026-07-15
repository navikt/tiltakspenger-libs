package no.nav.tiltakspenger.libs.httpklient.infra

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Auth-materialisering og skip-cache-retry testes over [FakeHttpTransport]: hele den reelle pipelinen kjører, og transporten viser nøyaktig hvilken `Authorization`-header som ville gått på wire.
 */
internal class HttpKlientAuthTokenTest {
    private val uri = URI.create("http://auth.test/ressurs")
    private val okJson = """{"status":"ok","antall":1}"""

    private fun FakeHttpTransport.authorizationHeader(kallIndex: Int = 0): String? =
        mottatteKall[kallIndex].request.headers().firstValue("Authorization").orElse(null)

    @Test
    fun `bearer-token sendes ekte til transporten men maskeres i metadata`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        val response = klient.getJson<TestResponseDto>(uri, bearerToken = testAccessToken("hemmelig-token")).getOrFail()

        // Transporten (og dermed serveren) mottar det ekte tokenet.
        transport.authorizationHeader() shouldBe "Bearer hemmelig-token"
        // Men rawRequestString (som ofte logges) maskerer det.
        response.metadata.rawRequestString.shouldNotContain("hemmelig-token")
        response.metadata.rawRequestString.shouldContain("Authorization: ***")
    }

    @Test
    fun `KlientAuth System gir Bearer-header på hver request`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        transport.leggIKøJson(okJson)
        var calls = 0
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider {
                    calls++
                    testAccessToken("tok-$calls")
                },
            ),
        )

        klient.getJson<TestResponseDto>(uri).getOrFail()
        klient.getJson<TestResponseDto>(uri).getOrFail()

        transport.authorizationHeader(0) shouldBe "Bearer tok-1"
        transport.authorizationHeader(1) shouldBe "Bearer tok-2"
        calls shouldBe 2
    }

    @Test
    fun `per-kall bearerToken overstyrer KlientAuth System`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(authTokenProvider { error("provider skal ikke kalles når kallet setter token") }),
        )

        klient.getJson<TestResponseDto>(uri, bearerToken = testAccessToken("override")).getOrFail()

        transport.authorizationHeader() shouldBe "Bearer override"
    }

    @Test
    fun `KlientAuth Ingen gir ingen Authorization-header`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val klient = fakeHttpKlient(transport)

        klient.getJson<TestResponseDto>(uri).getOrFail()

        transport.authorizationHeader() shouldBe null
    }

    @Test
    fun `provider som kaster gir AuthError uten HTTP-kall`() = runTest {
        val transport = FakeHttpTransport()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(authTokenProvider { throw IllegalStateException("token-endepunkt nede") }),
        )

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        val authError = error.shouldBeInstanceOf<HttpKlientError.AuthError>()
        authError.throwable.message shouldBe "token-endepunkt nede"
        authError.retryable shouldBe false
        authError.metadata.attempts shouldBe 0
        transport.mottatteKall shouldHaveSize 0
    }

    @Test
    fun `401 utløser ett nytt forsøk der provider kalles med skipCache=true`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(401)
        transport.leggIKøJson(okJson)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("tok-skip=$skipCache")
                },
            ),
        )

        val response = klient.getJson<TestResponseDto>(uri).getOrFail()

        response.body shouldBe TestResponseDto(status = "ok", antall = 1)
        skipCacheArgs shouldBe listOf(false, true)
        transport.mottatteKall shouldHaveSize 2
        transport.authorizationHeader(1) shouldBe "Bearer tok-skip=true"
    }

    @Test
    fun `403 er ikke med i default skipCacheRetryStatuses og gir ingen retry`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(403)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 403
        skipCacheArgs shouldBe listOf(false)
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `403 kan opt-es inn i skipCacheRetryStatuses`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(403)
        transport.leggIKøJson(okJson)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            skipCacheRetryStatuses = setOf(401, 403),
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        klient.getJson<TestResponseDto>(uri).getOrFail().body shouldBe TestResponseDto(status = "ok", antall = 1)
        skipCacheArgs shouldBe listOf(false, true)
    }

    @Test
    fun `tom skipCacheRetryStatuses slår av skip-cache-retry`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(401)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            skipCacheRetryStatuses = emptySet(),
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        skipCacheArgs shouldBe listOf(false)
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `suksess på første forsøk gir ingen skip-cache-retry`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(okJson)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        klient.getJson<TestResponseDto>(uri).getOrFail()

        skipCacheArgs shouldBe listOf(false)
        transport.mottatteKall shouldHaveSize 1
    }

    @Test
    fun `vedvarende 401 gjør kun ett ekstra forsøk`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(401)
        transport.leggIKøStatus(401)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.UventetStatus>().statusCode shouldBe 401
        skipCacheArgs shouldBe listOf(false, true)
        transport.mottatteKall shouldHaveSize 2
    }

    @Test
    fun `godta som aksepterer 401 gir ingen skip-cache-retry`() = runTest {
        // Skip-cache-retryen leser kun status fra en Left: en konsument som med vilje godtar 401 som suksess skal ikke få et uventet ekstra HTTP-kall.
        val transport = FakeHttpTransport()
        transport.leggIKøBytes("ok".toByteArray(), contentType = "application/pdf", statusCode = 401)
        val skipCacheArgs = mutableListOf<Boolean>()
        val klient = fakeHttpKlient(
            transport = transport,
            auth = KlientAuth.System(
                authTokenProvider { skipCache ->
                    skipCacheArgs.add(skipCache)
                    testAccessToken("t")
                },
            ),
        )

        val response = klient.getPdf(uri, godta = Statusregel.Eksakt(401)).getOrFail()

        response.statusCode shouldBe 401
        skipCacheArgs shouldBe listOf(false)
        transport.mottatteKall shouldHaveSize 1
    }
}
