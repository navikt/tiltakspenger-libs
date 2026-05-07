package no.nav.tiltakspenger.libs.httpklient

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
        val builder = RequestBuilder(URI.create("http://localhost/test"))
        val builtRequest = builder.build(HttpMethod.GET)

        builtRequest.successStatus shouldBe null
        builtRequest.loggingConfig shouldBe null
        builder.authToken shouldBe null
    }

    @Test
    fun `request builder kan lage public snapshot`() {
        val request = RequestBuilder(URI.create("http://localhost/test"))
            .apply {
                addHeader("X-Test", "1")
                json("{}")
                bearerToken(testAccessToken("token"))
            }.snapshot(typeOf<String>(), HttpMethod.POST)

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
}
