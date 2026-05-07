package no.nav.tiltakspenger.libs.httpklient

import arrow.core.left
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

internal class HttpKlientFakeTest {
    @Test
    fun `returnerer koet response og tar opp request`() = runTest {
        val fake: HttpKlient = HttpKlientFake().apply {
            enqueueStringResponse("ok")
        }

        val response = fake.get<String>(URI.create("http://localhost/test")) {
            header("X-Test", "1")
        }.getOrFail()

        response.statusCode shouldBe 200
        response.body shouldBe "ok"
        response.metadata.rawRequestString shouldContain "GET http://localhost/test"
        val request = (fake as HttpKlientFake).requests.single()
        request.method shouldBe HttpMethod.GET
        request.headers["X-Test"] shouldBe listOf("1")
    }

    @Test
    fun `returnerer tydelig feil hvis ingen response er konfigurert`() = runTest {
        val fake = HttpKlientFake()

        val error = fake.get<String>(URI.create("http://localhost/mangler")).swap().getOrNull()!!

        val invalidRequest = error.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
        invalidRequest.throwable.message shouldBe
            "HttpKlientFake mangler konfigurert respons for GET http://localhost/mangler"
        invalidRequest.metadata.attempts shouldBe 0
    }

    @Test
    fun `type mismatch blir DeserializationError`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueResponse(123)
        }

        val error = fake.get<String>(URI.create("http://localhost/feil-type")).swap().getOrNull()!!

        val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        deserializationError.statusCode shouldBe 200
        deserializationError.body shouldBe "123"
    }

    @Test
    fun `kan koee custom handler og error`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueue { request ->
                HttpKlientError.NetworkError(
                    throwable = RuntimeException("nede"),
                    metadata = HttpKlientMetadata(
                        rawRequestString = "${request.method} ${request.uri}",
                        rawResponseString = null,
                        requestHeaders = request.headers,
                        responseHeaders = emptyMap(),
                        statusCode = null,
                        attempts = 1,
                        attemptDurations = emptyList(),
                        totalDuration = kotlin.time.Duration.ZERO,
                    ),
                ).left()
            }
            enqueueUnitResponse()
        }

        fake.get<String>(URI.create("http://localhost/1")).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.NetworkError>()
        fake.post<Unit>(URI.create("http://localhost/2")).getOrFail().statusCode shouldBe 204

        fake.requests shouldHaveSize 2
        fake.requests[0].method shouldBe HttpMethod.GET
        fake.requests[1].method shouldBe HttpMethod.POST
    }

    @Test
    fun `reset sletter requests og koede responses`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueStringResponse("ok")
        }
        fake.get<String>(URI.create("http://localhost/for-reset")).getOrFail()

        fake.reset()

        fake.requests shouldHaveSize 0
        fake.get<String>(URI.create("http://localhost/etter-reset")).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
    }
}
