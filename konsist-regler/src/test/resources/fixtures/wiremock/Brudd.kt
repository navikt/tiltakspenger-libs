package fixtures.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.marcinziolo.kotlin.wiremock.post
import no.nav.tiltakspenger.libs.common.withWireMockServer
import org.junit.jupiter.api.Test

internal class Brudd {

    @Test
    fun `klienttest over wiremock`() {
        withWireMockServer { server: WireMockServer ->
            server.post { }
        }
    }
}
