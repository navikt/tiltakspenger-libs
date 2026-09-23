package fixtures.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import org.junit.jupiter.api.Test

internal class BruddWhitelistet {

    @Test
    fun `bevisst wire-format-test`() {
        val server = WireMockServer()
        println(server)
    }
}
