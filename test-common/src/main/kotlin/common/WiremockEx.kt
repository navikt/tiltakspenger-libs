package no.nav.tiltakspenger.libs.common

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import java.net.URI

/** Default loopback-host for test-WireMock. IPv4 med vilje — se [ipv4WireMockServer]. */
const val DEFAULT_WIREMOCK_HOST: String = "127.0.0.1"

/** Be WireMock velge en tilfeldig ledig (efemer) port. */
const val DYNAMIC_WIREMOCK_PORT: Int = 0

/**
 * Oppretter en WireMock-server som tvinger IPv4-loopback (127.0.0.1) både ved binding og i URL-ene den rapporterer.
 *
 * WireMocks egen [WireMockServer.baseUrl]/[WireMockServer.url] hardkoder strengen "localhost".
 * På macOS kan "localhost" resolve til IPv6 (::1) først, og en tilfeldig efemer-port kan da kollidere med en lokal IPv6-tjeneste (typisk AirPlay Receiver / Control Center, som svarer 401 Unauthorized).
 * To prosesser kan binde samme portnummer på ulike adressefamilier (IPv4 vs IPv6), så det gir ingen bind-konflikt — kun en flaky kollisjon der requesten av og til treffer feil server.
 *
 * Ved å binde til 127.0.0.1 og rapportere 127.0.0.1 i URL-ene treffer vi alltid vår egen IPv4-listener.
 *
 * Dette er den ene felles kilden til en IPv4-trygg WireMock-server for alle tiltakspenger-repoer.
 * Trenger du å styre livssyklusen selv (f.eks. starte/stoppe på egen måte), bruk denne direkte; ellers foretrekk [withWireMockServer].
 *
 * @param host adressen serveren binder til og rapporterer i URL-er. Default [DEFAULT_WIREMOCK_HOST] (IPv4-loopback) — endre kun hvis du vet hva du gjør.
 * @param port porten serveren binder til. Default [DYNAMIC_WIREMOCK_PORT] (la WireMock velge en ledig port). Sett en fast port kun når en test krever en kjent port.
 * @param configuration hook for å justere [WireMockConfiguration] videre (f.eks. `withRootDirectory`, `extensions`, `notifier`). Kjøres etter at host/port er satt.
 */
fun ipv4WireMockServer(
    host: String = DEFAULT_WIREMOCK_HOST,
    port: Int = DYNAMIC_WIREMOCK_PORT,
    configuration: WireMockConfiguration.() -> Unit = {},
): WireMockServer {
    val config = wireMockConfig()
        .bindAddress(host)
        .apply { if (port == DYNAMIC_WIREMOCK_PORT) dynamicPort() else port(port) }
        .apply(configuration)
    return object : WireMockServer(config) {
        override fun baseUrl(): String = "http://$host:${port()}"
        override fun url(path: String): String = "http://$host:${port()}/${path.removePrefix("/")}"
    }
}

/**
 * Starter en IPv4-trygg WireMock-server (se [ipv4WireMockServer]), gir den til [block], og stopper den uansett om [block] kaster.
 *
 * Funksjonen er `inline` med vilje: da inlines [block] i kallstedet, slik at den samme funksjonen dekker både synkrone tester og `suspend`-tester uten at vi trenger to ulike navn.
 *
 * @param host se [ipv4WireMockServer]. Default [DEFAULT_WIREMOCK_HOST].
 * @param port se [ipv4WireMockServer]. Default [DYNAMIC_WIREMOCK_PORT].
 * @param configuration se [ipv4WireMockServer]. `noinline` fordi den sendes videre til en ikke-inline funksjon.
 */
inline fun <T> withWireMockServer(
    host: String = DEFAULT_WIREMOCK_HOST,
    port: Int = DYNAMIC_WIREMOCK_PORT,
    noinline configuration: WireMockConfiguration.() -> Unit = {},
    block: (WireMockServer) -> T,
): T {
    val server = ipv4WireMockServer(host, port, configuration)
    server.start()
    return try {
        block(server)
    } finally {
        server.stop()
    }
}

/**
 * Starter en IPv4-trygg WireMock-server, henter base-url, stopper serveren igjen og returnerer en URI som peker mot en port der ingen lytter.
 *
 * Brukes for å teste nettverksfeil ved "server ikke kontaktbar".
 *
 * @param path path-delen som legges på URI-en.
 * @param host se [ipv4WireMockServer]. Default [DEFAULT_WIREMOCK_HOST].
 * @param configuration se [ipv4WireMockServer].
 */
fun stoppedServerUri(
    path: String,
    host: String = DEFAULT_WIREMOCK_HOST,
    configuration: WireMockConfiguration.() -> Unit = {},
): URI {
    val server = ipv4WireMockServer(host = host, configuration = configuration)
    server.start()
    val uri = URI.create("${server.baseUrl()}$path")
    server.stop()
    return uri
}
