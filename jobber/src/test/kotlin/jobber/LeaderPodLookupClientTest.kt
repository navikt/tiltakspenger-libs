package no.nav.tiltakspenger.libs.jobber

import arrow.core.left
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.matchers.shouldBe
import no.nav.tiltakspenger.libs.common.fixedClock
import no.nav.tiltakspenger.libs.httpklient.FakeHttpTransport
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI

/**
 * Testene bytter kun transporten ([FakeHttpTransport]), slik at hele den reelle [no.nav.tiltakspenger.libs.httpklient.HttpKlient]-pipelinen kjører — inkludert statusregel, Jackson-deserialisering og feilmapping.
 */
internal class LeaderPodLookupClientTest {
    private val logger = KotlinLogging.logger { }

    private fun klient(
        electorPath: String,
        transport: FakeHttpTransport,
    ) = LeaderPodLookupClient(
        electorPath = electorPath,
        logger = logger,
        clock = fixedClock,
        transport = transport,
    )

    @Test
    fun `returnerer true når lokal pod er leder`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":"pod-1"}""")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe true.right()

        transport.mottatteKall.single().uri shouldBe URI.create("http://elector.local")
    }

    @Test
    fun `returnerer false når annen pod er leder`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":"pod-2"}""")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe false.right()
    }

    @Test
    fun `tolererer ukjente felter fra sidecaren, slik den ekte elector-responsen har`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":"pod-1","last_update":"2024-01-01T00:00:00Z"}""")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe true.right()
    }

    @Test
    fun `støtter elector path uten protokoll`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":"pod-1"}""")

        klient("elector.local", transport).amITheLeader("pod-1") shouldBe true.right()

        transport.mottatteKall.single().uri shouldBe URI.create("http://elector.local")
    }

    @Test
    fun `returnerer Ikke2xx ved status 500`() {
        val transport = FakeHttpTransport()
        transport.leggIKøStatus(statusCode = 500, body = "feil", contentType = "text/plain")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.Ikke2xx(500, "feil").left()
    }

    @Test
    fun `returnerer ukjent svar når navn mangler`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"leader":"pod-1"}""")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
    }

    @Test
    fun `returnerer ukjent svar når navn er null`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":null}""")

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
    }

    @Test
    fun `returnerer kunne ikke kontakte ved transportfeil`() {
        val transport = FakeHttpTransport()
        transport.leggIKøKast(IOException("simulert nettverksfeil"))

        klient("http://elector.local", transport).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
    }

    @Test
    fun `returnerer kunne ikke kontakte ved ugyldig elector path`() {
        val transport = FakeHttpTransport()

        klient("http://[", transport).amITheLeader("pod-1") shouldBe LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()

        transport.mottatteKall shouldBe emptyList()
    }

    @Test
    fun `hurtigbufrer lederstatus etter første vellykkede treff`() {
        val transport = FakeHttpTransport()
        transport.leggIKøJson("""{"name":"pod-1"}""")
        val client = klient("http://elector.local", transport)

        client.amITheLeader("pod-1") shouldBe true.right()

        client.amITheLeader("pod-1") shouldBe true.right()
        transport.mottatteKall.size shouldBe 1
    }

    @Test
    fun `kan konstrueres uten transport og bruker da produksjonstransporten`() {
        LeaderPodLookupClient(
            electorPath = "http://localhost:0",
            logger = logger,
            clock = fixedClock,
        )
    }
}
