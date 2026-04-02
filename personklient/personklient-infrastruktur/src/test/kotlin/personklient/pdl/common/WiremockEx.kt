package no.nav.tiltakspenger.libs.personklient.pdl.common

import com.github.tomakehurst.wiremock.WireMockServer

internal fun withWireMockServer(block: (WireMockServer) -> Unit) {
    val wireMockServer = WireMockServer(0)
    wireMockServer.start()
    try {
        block(wireMockServer)
    } finally {
        wireMockServer.stop()
    }
}
