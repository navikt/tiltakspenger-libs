package no.nav.tiltakspenger.libs.personklient.pdl

import com.github.tomakehurst.wiremock.WireMockServer

private fun withWireMockServer(block: (WireMockServer) -> Unit) {
    val wireMockServer = WireMockServer(0)
    wireMockServer.start()
    try {
        block(wireMockServer)
    } finally {
        wireMockServer.stop()
    }
}
