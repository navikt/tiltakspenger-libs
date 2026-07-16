package no.nav.tiltakspenger.libs.httpklient.infra

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.common.getOrFail
import no.nav.tiltakspenger.libs.common.withWireMockServer
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

/**
 * Verifiserer at både connect-timeout og request-timeout fungerer korrekt — både når kallet holder seg innenfor timeouten og når det overskrider den.
 * Timeout er klient-config i v2 (ingen per-kall-overstyring); et endepunkt med avvikende behov får en egen klientinstans.
 *
 * Vi bruker `runBlocking` (ikke `runTest`) fordi vi måler reell veggklokketid: timeouten håndheves av `java.net.http.HttpClient` i ekte tid, og `runTest` ville hoppet over `delay`-basert venting.
 *
 * Regresjonsvern: en kjent bug i ktor 3.5.0 gjorde at hvert kall ventet hele timeouten før det returnerte — også når serveren svarte umiddelbart.
 * Vi vil aldri se den oppførselen her, så de positive testene sjekker eksplisitt at responsen kommer tilbake i *god tid før* timeouten utløper.
 */
internal class HttpKlientTimeoutTest {

    @Test
    fun `rask respons innenfor timeout returnerer raskt og venter ikke til timeout utløper`() {
        runBlocking {
            withWireMockServer { wiremock ->
                wiremock.stubFor(get(urlEqualTo("/rask")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody("rask")))
                val klient = testHttpKlient(timeout = 5.seconds)
                val uri = URI.create("${wiremock.baseUrl()}/rask")

                // Varm opp JVM/HttpClient/wiremock slik at målingen ikke fanger cold-start-kostnad.
                klient.getPdf(uri).getOrFail()

                val (result, elapsed) = measureTimedValue {
                    klient.getPdf(uri)
                }

                result.getOrFail().body.toString(Charsets.UTF_8) shouldBe "rask"
                // Skal komme tilbake langt raskere enn timeouten.
                // Hvis kallet ventet hele timeouten (slik ktor 3.5.0-bugen gjorde), ville dette vært ~5s.
                elapsed shouldBeLessThan 1.seconds
            }
        }
    }

    @Test
    fun `respons som overskrider request-timeout gir Timeout uten å vente på serveren`() {
        runBlocking {
            withWireMockServer { wiremock ->
                wiremock.stubFor(
                    get(urlEqualTo("/treg")).willReturn(aResponse().withStatus(200).withFixedDelay(3_000).withBody("treg")),
                )
                val klient = testHttpKlient(timeout = 100.milliseconds)
                val uri = URI.create("${wiremock.baseUrl()}/treg")

                val (result, elapsed) = measureTimedValue {
                    klient.getPdf(uri)
                }

                result.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.Timeout>()
                // Timeouten skal fyre rundt 100ms, ikke vente på serverens 3s-delay.
                elapsed shouldBeLessThan 1.seconds
            }
        }
    }

    @Test
    fun `tilkobling innenfor connect-timeout lykkes`() {
        runBlocking {
            withWireMockServer { wiremock ->
                wiremock.stubFor(get(urlEqualTo("/connect-ok")).willReturn(aResponse().withStatus(200).withBody("ok")))
                // Eksplisitt kort connect-timeout: en localhost-tilkobling skal lykkes godt innenfor.
                val klient = testHttpKlient(connectTimeout = 1.seconds, timeout = 2.seconds)

                klient.getPdf(URI.create("${wiremock.baseUrl()}/connect-ok")).getOrFail().statusCode shouldBe 200
            }
        }
    }

    @Test
    fun `tilkobling som henger begrenses av connect-timeout, ikke av request-timeout`() {
        runBlocking {
            // 192.0.2.1 er TEST-NET-1 (RFC 5737), reservert for dokumentasjon og rutes normalt ikke.
            // Et tilkoblingsforsøk dit henger til connect-timeouten fyrer (eventuelt feiler raskt med en nettverksfeil på enkelte oppsett).
            // Poenget: connect-fasen begrenses av den korte connect-timeouten og henger IKKE til den langt romsligere request-timeouten.
            val connectTimeout = 300.milliseconds
            val requestTimeout = 30.seconds
            val klient = testHttpKlient(connectTimeout = connectTimeout, timeout = requestTimeout)

            val (result, elapsed) = measureTimedValue {
                klient.getPdf(URI.create("http://192.0.2.1/henger"))
            }

            result.isLeft() shouldBe true
            // Connect-fasen feiler uten en fullstendig respons: enten connect-timeout (Timeout) eller en rask nettverksfeil (NetworkError), avhengig av oppsett.
            // Begge er IngenRespons; poenget er at vi aldri får en respons og ikke henger til request-timeouten.
            result.swap().getOrNull()!!.shouldBeInstanceOf<HttpKlientError.IngenRespons>()
            // Skal feile lenge før request-timeouten på 30s — bundet av connect-timeouten.
            elapsed shouldBeLessThan 10.seconds
        }
    }
}
