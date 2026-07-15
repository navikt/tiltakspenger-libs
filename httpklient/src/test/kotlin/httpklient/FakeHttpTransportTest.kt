package no.nav.tiltakspenger.libs.httpklient

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.getOrFail
import org.junit.jupiter.api.Test
import java.net.URI

/**
 * Kontraktstester for [FakeHttpTransport] — testinfraen konsumentene skal stole på i steg 3+.
 * Kontrakten: ren FIFO, høylytt feil ved tom kø, ekte serialisering i leggIKøJson, komplett body-drenering og trådsikkerhet.
 */
internal class FakeHttpTransportTest {
    private val uri = URI.create("http://fake.test/ressurs")

    @Test
    fun `tom kø kaster AssertionError med metode og URI`() = runTest {
        val transport = FakeHttpTransport()
        val klient = fakeHttpKlient(transport)

        shouldThrowWithMessage<AssertionError>("FakeHttpTransport mangler køet svar for GET $uri") {
            klient.getJson<TestResponseDto>(uri)
        }
    }

    @Test
    fun `svar konsumeres i FIFO-rekkefølge uavhengig av URI`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"forste","antall":1}""")
        transport.leggIKøJson("""{"status":"andre","antall":2}""")
        val klient = fakeHttpKlient(transport)

        klient.getJson<TestResponseDto>(URI.create("http://fake.test/a")).getOrFail().body.status shouldBe "forste"
        klient.getJson<TestResponseDto>(URI.create("http://fake.test/b")).getOrFail().body.status shouldBe "andre"
    }

    @Test
    fun `leggIKøJson med dto serialiserer med felles objectMapper - roundtrip gjennom ekte Jackson`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson(TestResponseDto(status = "ekte", antall = 7))
        val klient = fakeHttpKlient(transport)

        klient.getJson<TestResponseDto>(uri).getOrFail().body shouldBe TestResponseDto(status = "ekte", antall = 7)
    }

    @Test
    fun `dto som ikke matcher forventet form gir ekte DeserializationError - ikke stille cast`() = runTest {
        // Med gamle HttpKlientFake ble en køet DTO castet med isInstance; nå kjører produksjonens Jackson og form-avvik fanges for ekte.
        val transport = FakeHttpTransport()
        transport.leggIKøJson(TestRequestDto(id = "feil-form", antall = 1))
        val klient = fakeHttpKlient(transport)

        val error = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!

        error.shouldBeInstanceOf<HttpKlientError.DeserializationError>()
    }

    @Test
    fun `leggIKøJson med String-argument til dto-overloaden kaster med pekepinn`() {
        val transport = FakeHttpTransport()

        shouldThrowWithMessage<IllegalArgumentException>("Bruk leggIKøJson(json: String) for ferdig JSON — denne overloaden serialiserer DTO-er.") {
            transport.leggIKøJson("""{"status":"ok"}""" as Any)
        }
    }

    @Test
    fun `reset tømmer både kø og mottatte kall`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":1}""")
        val klient = fakeHttpKlient(transport)
        klient.getJson<TestResponseDto>(uri).getOrFail()
        transport.leggIKøJson("""{"status":"ubrukt","antall":0}""")

        transport.reset()

        transport.mottatteKall shouldHaveSize 0
        shouldThrowWithMessage<AssertionError>("FakeHttpTransport mangler køet svar for GET $uri") {
            klient.getJson<TestResponseDto>(uri)
        }
    }

    @Test
    fun `request-body dreneres til bodyBytes og bodyTekst ved mottak`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)

        klient.postJsonUtenSvar(uri, TestRequestDto(id = "abc", antall = 2)).getOrFail()

        val kall = transport.mottatteKall.single()
        kall.metode shouldBe "POST"
        kall.uri shouldBe uri
        kall.bodyTekst shouldBe """{"id":"abc","antall":2}"""
    }

    @Test
    fun `kall uten body gir tom bodyBytes`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"status":"ok","antall":1}""")
        val klient = fakeHttpKlient(transport)

        klient.getJson<TestResponseDto>(uri).getOrFail()

        transport.mottatteKall.single().bodyBytes.size shouldBe 0
    }

    @Test
    fun `stor request-body dreneres komplett`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)
        val storVerdi = "x".repeat(1_000_000)

        klient.postJsonUtenSvar(uri, SerialisertJson("""{"stor":"$storVerdi"}""")).getOrFail()

        transport.mottatteKall.single().bodyBytes.size shouldBe storVerdi.length + """{"stor":""}""".length
    }

    @Test
    fun `tom tekst-body gir tom bodyBytes uten drenering`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val klient = fakeHttpKlient(transport)

        klient.postTekst<Unit>(uri, tekst = "").getOrFail()

        transport.mottatteKall.single().bodyBytes.size shouldBe 0
    }

    @Test
    fun `publisher som feiler under drenering gir AssertionError med årsak`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val request = java.net.http.HttpRequest.newBuilder().uri(uri).method("POST", FeilendePublisher()).build()

        shouldThrowWithMessage<AssertionError>("Klarte ikke å lese request-body i FakeHttpTransport for POST $uri") {
            transport.send(request)
        }
    }

    @Test
    fun `publisher som aldri fullfører gir AssertionError etter drenerings-timeout`() = runTest {
        // Bevisst treg test (~5 s): dreneringens timeout-vern er siste skanse mot at en defekt publisher henger alle konsumenttester.
        val transport = FakeHttpTransport()
        transport.leggIKøTomRespons()
        val request = java.net.http.HttpRequest.newBuilder().uri(uri).method("POST", HengendePublisher()).build()

        shouldThrowWithMessage<AssertionError>("Timet ut ved drenering av request-body i FakeHttpTransport for POST $uri") {
            transport.send(request)
        }
    }

    @Test
    fun `faken er trådsikker under parallelle kall`() {
        // Speiler HttpKlientConcurrencyTest: samme klient og fake deles av mange coroutines på ekte tråder.
        val antall = 50
        val transport = FakeHttpTransport()
        repeat(antall) { transport.leggIKøJson("""{"status":"p","antall":1}""") }
        val klient = fakeHttpKlient(transport)

        runBlocking {
            withContext(Dispatchers.IO) {
                (0 until antall).map { i ->
                    async {
                        klient.getJson<TestResponseDto>(URI.create("http://fake.test/parallell/$i")).getOrFail()
                    }
                }.awaitAll()
            }
        }

        transport.mottatteKall shouldHaveSize antall
    }

    @Test
    fun `leggIKøStatus og leggIKøTomRespons setter status og tom body`() = runTest {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(500, "teknisk feil", contentType = "text/plain")
        transport.leggIKøTomRespons(statusCode = 204)
        val klient = fakeHttpKlient(transport)

        val feil = klient.getJson<TestResponseDto>(uri).swap().getOrNull()!!
        feil.shouldBeInstanceOf<HttpKlientError.UventetStatus>().body shouldBe "teknisk feil"

        val tom = klient.getJsonEllerNull<TestResponseDto>(uri).getOrFail()
        tom.statusCode shouldBe 204
        tom.body shouldBe null
    }
}

/** Publisher som feiler ved drenering — simulerer en defekt BodyPublisher-implementasjon. */
private class FeilendePublisher : java.net.http.HttpRequest.BodyPublisher {
    override fun contentLength(): Long = 10L

    override fun subscribe(subscriber: java.util.concurrent.Flow.Subscriber<in java.nio.ByteBuffer>) {
        subscriber.onSubscribe(IngenSubscription)
        subscriber.onError(IllegalStateException("publisher-feil"))
    }
}

/** Publisher som aldri leverer noe — trigger drenerings-timeouten. */
private class HengendePublisher : java.net.http.HttpRequest.BodyPublisher {
    override fun contentLength(): Long = 10L

    override fun subscribe(subscriber: java.util.concurrent.Flow.Subscriber<in java.nio.ByteBuffer>) {
        subscriber.onSubscribe(IngenSubscription)
    }
}

private object IngenSubscription : java.util.concurrent.Flow.Subscription {
    override fun request(n: Long) {}

    override fun cancel() {}
}
