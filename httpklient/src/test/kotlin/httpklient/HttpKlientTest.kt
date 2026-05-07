package no.nav.tiltakspenger.libs.httpklient

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientTest {
    @Test
    fun `reified request extension uten builder delegerer til interface`() = runTest {
        val klient = RecordingHttpKlient()

        klient.request<String>(URI.create("http://localhost/request"), HttpMethod.GET).rightOrThrow()

        klient.calls.shouldContainExactly(
            RecordedCall(
                uri = URI.create("http://localhost/request"),
                responseType = typeOf<String>(),
                request = HttpKlientRequest(
                    uri = URI.create("http://localhost/request"),
                    method = HttpMethod.GET,
                    headers = emptyMap(),
                    body = null,
                    timeout = null,
                    authToken = null,
                ),
            ),
        )
    }

    @Test
    fun `reified request extension med builder delegerer til interface`() = runTest {
        val klient = RecordingHttpKlient()

        klient.request<String>(URI.create("http://localhost/request-builder"), HttpMethod.POST) {
            body("raw")
            timeout = 123.milliseconds
        }.rightOrThrow()

        klient.calls.single().request.method shouldBe HttpMethod.POST
        klient.calls.single().request.body shouldBe HttpKlientRequest.Body.Raw("raw")
        klient.calls.single().request.timeout shouldBe 123.milliseconds
    }

    @Test
    fun `verb extensions setter riktig metode`() = runTest {
        val klient = RecordingHttpKlient()
        val uri = URI.create("http://localhost/verb")

        klient.get<String>(uri).rightOrThrow()
        klient.post<String>(uri).rightOrThrow()
        klient.put<String>(uri).rightOrThrow()
        klient.patch<String>(uri).rightOrThrow()
        klient.delete<String>(uri).rightOrThrow()
        klient.head<String>(uri).rightOrThrow()
        klient.options<String>(uri).rightOrThrow()

        klient.calls.map { it.request.method } shouldBe listOf(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
        )
    }

    @Test
    fun `request snapshot dekker alle body-varianter`() {
        RequestBuilder(URI.create("http://localhost/raw"))
            .apply { body("raw") }
            .snapshot(typeOf<String>(), HttpMethod.GET)
            .body shouldBe HttpKlientRequest.Body.Raw("raw")

        RequestBuilder(URI.create("http://localhost/raw-json"))
            .apply { json("{}") }
            .snapshot(typeOf<String>(), HttpMethod.GET)
            .body shouldBe HttpKlientRequest.Body.RawJson("{}")

        val dto = TestRequestDto(id = "id", antall = 1)
        RequestBuilder(URI.create("http://localhost/json"))
            .apply { json(dto) }
            .snapshot(typeOf<String>(), HttpMethod.GET)
            .body shouldBe HttpKlientRequest.Body.Json(dto)

        RequestBuilder(URI.create("http://localhost/no-body"))
            .snapshot(typeOf<String>(), HttpMethod.GET)
            .body shouldBe null
    }
}

private data class RecordedCall(
    val uri: URI,
    val responseType: KType,
    val request: HttpKlientRequest,
)

private class RecordingHttpKlient : HttpKlient {
    val calls = mutableListOf<RecordedCall>()

    override suspend fun <Response : Any> unreifiedRequest(
        uri: URI,
        method: HttpMethod,
        responseType: KType,
        build: RequestBuilder.() -> Unit,
    ): Either<HttpKlientError, HttpKlientResponse<Response>> {
        val request = RequestBuilder(uri).apply(build).snapshot(responseType, method)
        calls += RecordedCall(uri = uri, responseType = responseType, request = request)
        @Suppress("UNCHECKED_CAST")
        return HttpKlientResponse(
            statusCode = 200,
            body = "ok" as Response,
            metadata = HttpKlientMetadata(
                rawRequestString = "${request.method} ${request.uri}",
                rawResponseString = "ok",
                requestHeaders = request.headers,
                responseHeaders = emptyMap(),
                statusCode = 200,
                attempts = 1,
                attemptDurations = emptyList(),
                totalDuration = Duration.ZERO,
            ),
        ).right()
    }
}

private fun <A> Either<HttpKlientError, A>.rightOrThrow(): A = fold(
    ifLeft = { error("Forventet Right, fikk $it") },
    ifRight = { it },
)
