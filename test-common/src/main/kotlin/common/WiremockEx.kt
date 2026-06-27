package no.nav.tiltakspenger.libs.common

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import java.net.URI

/** Default loopback-host for test-WireMock. IPv4 med vilje — se [ipv4WireMockServer]. */
const val DEFAULT_WIREMOCK_HOST: String = "127.0.0.1"

/**
 * Oppretter en WireMock-server som tvinger IPv4-loopback (127.0.0.1) både ved binding og i URL-ene den rapporterer.
 *
 * WireMocks egen [WireMockServer.baseUrl]/[WireMockServer.url] hardkoder strengen "localhost".
 * På macOS kan "localhost" resolve til IPv6 (::1) først, og en tilfeldig efemer-port kan da kollidere med en lokal IPv6-tjeneste (typisk AirPlay Receiver / Control Center, som svarer 401 Unauthorized).
 * To prosesser kan binde samme portnummer på ulike adressefamilier (IPv4 vs IPv6), så det gir ingen bind-konflikt — kun en flaky kollisjon der requesten av og til treffer feil server.
 *
 * Ved å binde til 127.0.0.1 og rapportere 127.0.0.1 i URL-ene treffer vi alltid vår egen IPv4-listener.
 *
 * Serveren får alltid en tilfeldig ledig (efemer) port via `dynamicPort()`.
 * Det er bevisst: hvert kall får sin egen port, så en testsuite kan kjøre parallelt uten bind-konflikt.
 * Vi tilbyr med vilje ingen fast-port-parameter — to parallelle tester som deler en fast port ville kollidert. Trenger du absolutt en fast port, sett den selv via [configuration] og eier konsekvensene.
 *
 * Dette er den ene felles kilden til en IPv4-trygg WireMock-server for alle tiltakspenger-repoer.
 * Trenger du å styre livssyklusen selv (f.eks. starte/stoppe på egen måte), bruk denne direkte; ellers foretrekk [withWireMockServer].
 *
 * @param host adressen serveren binder til og rapporterer i URL-er. Default [DEFAULT_WIREMOCK_HOST] (IPv4-loopback) — endre kun hvis du vet hva du gjør.
 * @param configuration hook for å justere [WireMockConfiguration] videre (f.eks. `withRootDirectory`, `extensions`, `notifier`). Kjøres etter at host/port er satt.
 */
fun ipv4WireMockServer(
    host: String = DEFAULT_WIREMOCK_HOST,
    configuration: WireMockConfiguration.() -> Unit = {},
): WireMockServer {
    val config = wireMockConfig()
        .bindAddress(host)
        .dynamicPort()
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
 * Hvert kall starter sin egen server på en egen dynamisk port, så flere tester kan kjøre [withWireMockServer] samtidig i en parallell suite.
 * [block] må fullføre/awaite alt nettverksarbeid før det returnerer: serveren stoppes umiddelbart etterpå (synkront, med kun ~1 s graceful drain), så en request som fortsatt er i flukt — eller en lazy respons-body som leses senere — vil feile.
 *
 * @param host se [ipv4WireMockServer]. Default [DEFAULT_WIREMOCK_HOST].
 * @param configuration se [ipv4WireMockServer]. `noinline` fordi den sendes videre til en ikke-inline funksjon.
 */
inline fun <T> withWireMockServer(
    host: String = DEFAULT_WIREMOCK_HOST,
    noinline configuration: WireMockConfiguration.() -> Unit = {},
    block: (WireMockServer) -> T,
): T {
    val server = ipv4WireMockServer(host, configuration)
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
