package no.nav.tiltakspenger.libs.httpklient

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.tiltakspenger.libs.common.fixedClock
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.reflect.typeOf

internal class HttpKlientDefaultsTest {
    @Test
    fun `kan opprette klient med default timeouts`() {
        HttpKlient(clock = fixedClock).shouldBeInstanceOf<HttpKlient>()
    }

    @Test
    fun `HttpKlient-factoryen returnerer en HttpKlient`() {
        val klient: HttpKlient = HttpKlient(clock = fixedClock)

        klient.shouldBeInstanceOf<HttpKlient>()
    }

    @Test
    fun `request builder eksponerer nullable overrides`() {
        val materialized = RequestBuilder(URI.create("http://localhost/test")).materialize(typeOf<String>(), HttpMethod.GET)

        materialized.successStatus shouldBe null
        materialized.loggingConfig shouldBe null
        materialized.authToken shouldBe null
    }

    @Test
    fun `request builder kan materialisere requesten`() {
        val request = RequestBuilder(URI.create("http://localhost/test"))
            .apply {
                addHeader("X-Test", "1")
                json("{}")
                bearerToken(testAccessToken("token"))
            }.materialize(typeOf<String>(), HttpMethod.POST)

        request.uri shouldBe URI.create("http://localhost/test")
        request.method shouldBe HttpMethod.POST
        request.headers["X-Test"] shouldBe listOf("1")
        request.headers["Accept"] shouldBe null
        request.headers["Content-Type"] shouldBe listOf("application/json")
        request.body shouldBe HttpKlientRequest.Body.RawJson("{}")
        request.timeout shouldBe null
        request.authToken shouldBe testAccessToken("token")
    }

    @Test
    fun `HttpStatusSuccess is2xx returnerer true for 2xx og false ellers`() {
        HttpStatusSuccess.is2xx(199) shouldBe false
        HttpStatusSuccess.is2xx(200) shouldBe true
        HttpStatusSuccess.is2xx(299) shouldBe true
        HttpStatusSuccess.is2xx(300) shouldBe false
        HttpStatusSuccess.is2xx(404) shouldBe false
        HttpStatusSuccess.is2xx(500) shouldBe false
    }

    @Test
    fun `HttpStatusSuccess exactly godtar kun oppgitte koder`() {
        val predicate = HttpStatusSuccess.exactly(200, 201, 204)
        predicate(200) shouldBe true
        predicate(201) shouldBe true
        predicate(204) shouldBe true
        predicate(202) shouldBe false
        predicate(404) shouldBe false
    }

    @Test
    fun `HttpStatusSuccess exactly krever minst en kode`() {
        shouldThrow<IllegalArgumentException> { HttpStatusSuccess.exactly() }
    }

    @Test
    fun `HttpStatusSuccess inRange godtar koder i intervallet`() {
        val predicate = HttpStatusSuccess.inRange(200..204)
        predicate(199) shouldBe false
        predicate(200) shouldBe true
        predicate(204) shouldBe true
        predicate(205) shouldBe false
    }

    @Test
    fun `RequestBuilder successStatus vararg-overload bygger exactly-predikat`() {
        val builder = RequestBuilder(URI.create("http://localhost/test")).apply { successStatus(201, 202) }
        val predicate = builder.materialize(typeOf<String>(), HttpMethod.GET).successStatus!!
        predicate(201) shouldBe true
        predicate(202) shouldBe true
        predicate(200) shouldBe false
    }

    @Test
    fun `RequestBuilder successStatus range-overload bygger inRange-predikat`() {
        val builder = RequestBuilder(URI.create("http://localhost/test")).apply { successStatus(200..204) }
        val predicate = builder.materialize(typeOf<String>(), HttpMethod.GET).successStatus!!
        predicate(200) shouldBe true
        predicate(204) shouldBe true
        predicate(205) shouldBe false
    }
}
