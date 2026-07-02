package no.nav.tiltakspenger.libs.httpklient

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.time.Duration.Companion.milliseconds

internal class HttpKlientFakeTest {
    @Test
    fun `returnerer køet respons og tar opp request`() = runTest {
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
    fun `per-request bearerToken materialiseres til effektive headere i metadata og redakteres i rawRequestString`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueStringResponse("ok")
        }
        val token = AccessToken(
            token = "hemmelig-token",
            expiresAt = Instant.now().plusSeconds(60),
        )

        val response = fake.get<String>(URI.create("http://localhost/auth")) {
            bearerToken(token)
        }.getOrFail()

        // Effektive headere (med Authorization) havner i metadata.requestHeaders med ekte token-verdi, som i produksjon.
        response.metadata.requestHeaders["Authorization"] shouldBe listOf("Bearer hemmelig-token")
        // rawRequestString redakterer sensitive headere slik at tokenet ikke lekker.
        response.metadata.rawRequestString shouldContain "Authorization: ***"
        response.metadata.rawRequestString shouldNotContain "hemmelig-token"
    }

    @Test
    fun `eksplisitt Authorization-header redakteres i rawRequestString`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueStringResponse("ok")
        }

        val response = fake.get<String>(URI.create("http://localhost/auth")) {
            header("Authorization", "Bearer manuelt-token")
            header("Cookie", "session=abc")
        }.getOrFail()

        response.metadata.requestHeaders["Authorization"] shouldBe listOf("Bearer manuelt-token")
        response.metadata.rawRequestString shouldContain "Authorization: ***"
        response.metadata.rawRequestString shouldContain "Cookie: ***"
        response.metadata.rawRequestString shouldNotContain "manuelt-token"
        response.metadata.rawRequestString shouldNotContain "session=abc"
    }

    @Test
    fun `returnerer tydelig feil hvis ingen respons er konfigurert`() = runTest {
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
    fun `DeserializationError ved type-mismatch bruker rawResponseString-override slik at body er konsistent med metadata`() = runTest {
        // I produksjon kommer DeserializationError.body alltid fra den rå respons-stringen, så faken må bruke metadata.rawResponseString (ikke body.toString()) når testen overstyrer rå respons.
        val fake = HttpKlientFake().apply {
            enqueueResponse(body = 123, rawResponseString = "raw-fra-server")
        }

        val error = fake.get<String>(URI.create("http://localhost/raw-mismatch")).swap().getOrNull()!!

        val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        deserializationError.body shouldBe "raw-fra-server"
        deserializationError.body shouldBe deserializationError.metadata.rawResponseString
    }

    @Test
    fun `kan køe custom handler og error`() = runTest {
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
    fun `Unit-respons gir tom rawResponseString i metadata`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueUnitResponse()
        }

        val response = fake.post<Unit>(URI.create("http://localhost/unit")).getOrFail()

        response.statusCode shouldBe 204
        response.metadata.rawResponseString shouldBe ""
    }

    @Test
    fun `rawResponseString kan overstyres eksplisitt`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueResponse(body = "domeneobjekt", rawResponseString = "{\"raw\":true}")
        }

        val response = fake.get<String>(URI.create("http://localhost/raw")).getOrFail()

        response.body shouldBe "domeneobjekt"
        response.metadata.rawResponseString shouldBe "{\"raw\":true}"
    }

    @Test
    fun `attempts og attemptDurations kan konfigureres og totalDuration summeres`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueResponse(
                body = "ok",
                attempts = 3,
                attemptDurations = listOf(10.milliseconds, 20.milliseconds, 30.milliseconds),
            )
        }

        val metadata = fake.get<String>(URI.create("http://localhost/retry")).getOrFail().metadata

        metadata.attempts shouldBe 3
        metadata.attemptDurations shouldBe listOf(10.milliseconds, 20.milliseconds, 30.milliseconds)
        metadata.totalDuration shouldBe 60.milliseconds
    }

    @Test
    fun `totalDuration kan settes uavhengig av attemptDurations for å modellere retry-backoff`() = runTest {
        // I produksjon inkluderer totalDuration backoff mellom forsøk, så den kan overstige summen av attemptDurations.
        // Faken må la testen modellere denne forskjellen, ikke alltid bruke summen.
        val fake = HttpKlientFake().apply {
            enqueueResponse(
                body = "ok",
                attempts = 2,
                attemptDurations = listOf(10.milliseconds, 20.milliseconds),
                totalDuration = 100.milliseconds,
            )
        }

        val metadata = fake.get<String>(URI.create("http://localhost/backoff")).getOrFail().metadata

        metadata.attemptDurations shouldBe listOf(10.milliseconds, 20.milliseconds)
        metadata.totalDuration shouldBe 100.milliseconds
    }

    @Test
    fun `enqueueResponse avviser attempts under 1`() {
        shouldThrowWithMessage<IllegalArgumentException>("attempts må være minst 1 for en utført request, var 0") {
            HttpKlientFake().enqueueResponse(body = "ok", attempts = 0)
        }
    }

    @Test
    fun `enqueueResponse avviser inkonsistent attemptDurations`() {
        shouldThrowWithMessage<IllegalArgumentException>(
            "attemptDurations.size (2) må være lik attempts (3), ellers blir timing-metadata inkonsistent",
        ) {
            HttpKlientFake().enqueueResponse(
                body = "ok",
                attempts = 3,
                attemptDurations = listOf(10.milliseconds, 20.milliseconds),
            )
        }
    }

    @Test
    fun `error-helpers avviser attempts under 1`() {
        shouldThrowWithMessage<IllegalArgumentException>("attempts må være minst 1 for en utført request, var 0") {
            HttpKlientFake().enqueueTimeout(attempts = 0)
        }
    }

    @Test
    fun `enqueueTimeout gir Timeout med utfylt metadata`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueTimeout()
        }

        val error = fake.get<String>(URI.create("http://localhost/timeout")).swap().getOrNull()!!

        val timeout = error.shouldBeInstanceOf<HttpKlientError.Timeout>()
        timeout.metadata.attempts shouldBe 1
        timeout.metadata.rawRequestString shouldContain "GET http://localhost/timeout"
    }

    @Test
    fun `enqueueNetworkError gir NetworkError med utfylt metadata`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueNetworkError()
        }

        fake.get<String>(URI.create("http://localhost/nede")).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.NetworkError>()
    }

    @Test
    fun `enqueueUventetStatus gir UventetStatus med status og body i metadata`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueUventetStatus(statusCode = 503, body = "Service Unavailable")
        }

        val error = fake.get<String>(URI.create("http://localhost/503")).swap().getOrNull()!!

        val uventetStatus = error.shouldBeInstanceOf<HttpKlientError.UventetStatus>()
        uventetStatus.statusCode shouldBe 503
        uventetStatus.body shouldBe "Service Unavailable"
        uventetStatus.metadata.statusCode shouldBe 503
        uventetStatus.metadata.rawResponseString shouldBe "Service Unavailable"
    }

    @Test
    fun `enqueueDeserializationError gir DeserializationError med status og body`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueDeserializationError(statusCode = 200, body = "ikke-gyldig-json")
        }

        val error = fake.get<String>(URI.create("http://localhost/deser")).swap().getOrNull()!!

        val deserializationError = error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
        deserializationError.statusCode shouldBe 200
        deserializationError.body shouldBe "ikke-gyldig-json"
    }

    @Test
    fun `error-helpers redakterer sensitive headere i metadata`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueUventetStatus(statusCode = 500)
        }

        val error = fake.get<String>(URI.create("http://localhost/redaksjon")) {
            header("Authorization", "Bearer hemmelig")
        }.swap().getOrNull()!!

        error.metadata.requestHeaders["Authorization"] shouldBe listOf("Bearer hemmelig")
        error.metadata.rawRequestString shouldContain "Authorization: ***"
        error.metadata.rawRequestString shouldNotContain "hemmelig"
    }

    @Test
    fun `responstype uten KClass-classifier gir kontrollert InvalidRequest i stedet for å kaste`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueStringResponse("fanget")
            enqueueStringResponse("med-ugyldig-type")
        }

        // Fang en ekte materialisert request fra et vanlig kall, slik at vi kan gjenbruke den i et direkte request()-kall.
        fake.get<String>(URI.create("http://localhost/fang")).getOrFail()
        val capturedRequest = fake.requests.single()

        // En KType uten KClass-classifier (her: classifier = null) trigger den usikre cast-grenen uten å dra inn kotlin-reflect.
        val typeUtenKClass = object : KType {
            override val classifier: KClassifier? = null
            override val arguments: List<KTypeProjection> = emptyList()
            override val isMarkedNullable: Boolean = false
            override val annotations: List<Annotation> = emptyList()
        }

        val result = fake.request<Any>(capturedRequest, typeUtenKClass)

        result.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
    }

    @Test
    fun `json-body serialiseres som i produksjon i rawRequestString`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueUnitResponse()
        }

        val response = fake.post<Unit>(URI.create("http://localhost/json")) {
            json(JsonBodyDto(id = "abc", antall = 2))
        }.getOrFail()

        // Speiler JavaHttpKlient: faktisk JSON, ikke data class-ens toString().
        response.metadata.rawRequestString shouldContain "\"id\":\"abc\""
        response.metadata.rawRequestString shouldContain "\"antall\":2"
        response.metadata.rawRequestString shouldNotContain "JsonBodyDto("
    }

    private data class JsonBodyDto(val id: String, val antall: Int)

    @Test
    fun `json-body som ikke lar seg serialisere feiler testen med tydelig melding`() = runTest {
        // En body som ikke lar seg serialisere (her: selvrefererende DTO -> uendelig rekursjon) er nesten alltid en feil i testoppsettet.
        // Faken skal da feile testen høylytt med en tydelig melding, ikke skjule det.
        val fake = HttpKlientFake().apply {
            enqueueUnitResponse()
        }

        val thrown = shouldThrow<AssertionError> {
            fake.post<Unit>(URI.create("http://localhost/json-feil")) {
                json(SelvRefererendeJsonDto())
            }
        }

        thrown.message shouldContain "HttpKlientFake klarte ikke å serialisere JSON-body"
        thrown.message shouldContain SelvRefererendeJsonDto::class.qualifiedName!!
    }

    private class SelvRefererendeJsonDto {
        @Suppress("unused")
        val self: SelvRefererendeJsonDto = this
    }

    @Test
    fun `reset sletter requests og køede responser`() = runTest {
        val fake = HttpKlientFake().apply {
            enqueueStringResponse("ok")
        }
        fake.get<String>(URI.create("http://localhost/for-reset")).getOrFail()

        fake.reset()

        fake.requests shouldHaveSize 0
        fake.get<String>(URI.create("http://localhost/etter-reset")).swap().getOrNull()!!
            .shouldBeInstanceOf<HttpKlientError.InvalidRequest>()
    }

    @Test
    fun `kan brukes fra mange coroutines parallelt uten å miste requests eller responser`() {
        // Bruker runBlocking + Dispatchers.IO for ekte parallellitet på flere tråder, slik at
        // synkroniseringen i HttpKlientFake faktisk blir utfordret (runTest kjører enkelt-trådet).
        runBlocking {
            val antall = 200
            val fake = HttpKlientFake()
            repeat(antall) { fake.enqueueStringResponse("ok-$it") }

            val resultater = withContext(Dispatchers.IO) {
                (0 until antall).map { i ->
                    async {
                        fake.get<String>(URI.create("http://localhost/parallell/$i")).getOrFail().body
                    }
                }.awaitAll()
            }

            // Hver kø-respons skal være konsumert nøyaktig én gang (ingen tapt/duplisert under race).
            resultater.toSet() shouldBe (0 until antall).map { "ok-$it" }.toSet()
            fake.requests shouldHaveSize antall
        }
    }
}
