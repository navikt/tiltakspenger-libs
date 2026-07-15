package no.nav.tiltakspenger.libs.httpklient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Transport-sømmen: det eneste punktet i `httpklient` som rører nettverket.
 *
 * Alt over sømmen — auth-resolve, header-bygging, retry, skip-cache-retry, circuit breaker, statusevaluering, dekoding, metadata og redaksjon — er felles pipeline-kode som kjører uansett transport.
 * Produksjon bruker [JavaHttpTransport] (default i [HttpKlient]); tester bytter inn `FakeHttpTransport` fra testFixtures og kjører likevel hele den reelle pipelinen, i stedet for å emulere den.
 *
 * Kontrakt: [send] returnerer en [TransportRespons] for enhver fullstendig HTTP-respons (uansett status), og kaster for alt annet (timeout, IO-/nettverksfeil); transporten skal ikke pakke exceptions inn i egne typer.
 * Pipelinen fanger ikke-fatale exceptions i [runSingleAttempt] og mapper dem til [HttpKlientError] i [finalize].
 * [java.util.concurrent.CancellationException] og andre fatale feil fanges bevisst _ikke_ — Arrow `Either.catch` re-kaster dem, slik at kansellering propagerer og strukturert kansellering bevares (en fake-transport skal derfor ikke bruke CancellationException til å simulere transportfeil).
 */
fun interface HttpTransport {
    suspend fun send(request: HttpRequest): TransportRespons
}

/**
 * Rå-responsen fra transporten: statuskode, headere og ubehandlede body-bytes.
 *
 * Bevisst en liten libs-eid type i stedet for `java.net.http.HttpResponse`, slik at en fake-transport slipper å implementere JDK-interfacet.
 * `java.net.http.HttpRequest` beholdes derimot på input-siden av [HttpTransport.send] — da kjører JDK-ens egen request-validering også i tester som bytter transport.
 * Bytes holdes rå her; charset-/binær-dekoding skjer først i konverterings-/metadata-laget.
 *
 * Ikke en data class: `ByteArray` har referanselikhet i `equals`, og typen trenger ikke verdilikhet.
 */
class TransportRespons(
    val statusCode: Int,
    /** Responsens headere, rått fra serveren; pipelinen legger dem i `metadata.responseHeaders` og leser `Content-Type` herfra til charset-/binær-dekodingen. */
    val headere: Map<String, List<String>>,
    val body: ByteArray,
)

/**
 * Lager produksjonstransporten, backet av `java.net.http.HttpClient`.
 *
 * Dette er den offentlige måten å lage produksjonstransporten på, slik at klientklasser (f.eks. i `texas` og `jobber`) kan ta `transport: HttpTransport = JavaHttpTransport(...)` som konstruktørparameter med testbar default.
 * [connectTimeout] konsumeres her fordi den er en egenskap ved selve HttpClient-instansen; per-kall timeout ligger på [HttpRequest] og settes i request-byggingen.
 * Redirects følges aldri (ingen konsument bruker det); konsumenten ser eventuelle `3xx`-svar eksplisitt som [HttpKlientError.UventetStatus].
 */
@Suppress("ktlint:standard:function-naming") // Bevisst pseudo-konstruktør: leses som `JavaHttpTransport(...)` på call sites, mens implementasjonsklassen forblir privat.
fun JavaHttpTransport(connectTimeout: Duration = 10.seconds): HttpTransport = JavaHttpClientTransport(connectTimeout)

/**
 * Kallet kjøres på IO-dispatcheren — det er et transportanliggende, så en fake-transport slipper context-byttet.
 * Bodyen leses som rå bytes (`BodyHandlers.ofByteArray`) slik at binært innhold (f.eks. PDF) ikke korrupteres av charset-dekoding.
 */
private class JavaHttpClientTransport(
    connectTimeout: Duration,
) : HttpTransport {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override suspend fun send(request: HttpRequest): TransportRespons {
        val response = withContext(Dispatchers.IO) {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        }
        return TransportRespons(
            statusCode = response.statusCode(),
            headere = response.headers().map(),
            body = response.body(),
        )
    }
}
