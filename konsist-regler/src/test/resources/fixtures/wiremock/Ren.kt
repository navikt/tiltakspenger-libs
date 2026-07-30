package fixtures.wiremock

import no.nav.tiltakspenger.libs.httpklient.infra.transport.FakeHttpTransport
import org.junit.jupiter.api.Test

internal class Ren {

    // En kommentar som nevner WireMock skal ikke flagges — deteksjonen er import-basert.
    @Test
    fun `klienttest over faken`() {
        val transport = FakeHttpTransport()
        val omtale = "import com.github.tomakehurst.wiremock.WireMockServer i en streng teller ikke"
        println("$transport $omtale")
    }
}
