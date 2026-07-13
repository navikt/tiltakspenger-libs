package no.nav.tiltakspenger.libs.httpklient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Transport-sømmen: det eneste punktet i `httpklient` som rører nettverket.
 *
 * Alt over sømmen — auth-resolve, header-bygging, retry, skip-cache-retry, circuit breaker, statusevaluering, dekoding, metadata og logging — er felles pipeline-kode som kjører uansett transport.
 * Produksjon bruker [JavaHttpTransport]; tester kan bytte inn en kø-basert transport og likevel kjøre hele den reelle pipelinen, i stedet for å emulere den.
 *
 * Kontrakt: [send] returnerer en [TransportRespons] for enhver fullstendig HTTP-respons (uansett status), og kaster for alt annet (timeout, IO-/nettverksfeil); transporten skal ikke pakke exceptions inn i egne typer.
 * Pipelinen fanger ikke-fatale exceptions i [runSingleAttempt] og mapper dem til [HttpKlientError] i [finalize].
 * [java.util.concurrent.CancellationException] og andre fatale feil fanges bevisst _ikke_ — Arrow `Either.catch` re-kaster dem, slik at kansellering propagerer og strukturert kansellering bevares (en fake-transport skal derfor ikke bruke CancellationException til å simulere transportfeil).
 */
internal fun interface HttpTransport {
    suspend fun send(request: HttpRequest): TransportRespons
}

/**
 * Rå-responsen fra transporten: statuskode, headere og ubehandlede body-bytes.
 *
 * Bevisst en liten libs-eid type i stedet for `java.net.http.HttpResponse`, slik at en fake-transport slipper å implementere JDK-interfacet.
 * `java.net.http.HttpRequest` beholdes derimot på input-siden av [HttpTransport.send] — da kjører JDK-ens egen request-validering også i tester som bytter transport.
 * Bytes holdes rå her; charset-/binær-dekoding skjer først i konverterings-/metadata-laget ([toTypedResponse] og [finalize]).
 *
 * Ikke en data class: `ByteArray` har referanselikhet i `equals`, og typen trenger ikke verdilikhet.
 */
internal class TransportRespons(
    val statusCode: Int,
    val headere: Map<String, List<String>>,
    val body: ByteArray,
)

/**
 * Produksjonstransporten, backet av `java.net.http.HttpClient`.
 *
 * [connectTimeout] og `followRedirects` konsumeres her fordi de er egenskaper ved selve HttpClient-instansen; per-request timeout ligger på [HttpRequest] og settes i request-byggingen.
 * Kallet kjøres på IO-dispatcheren — det er et transportanliggende, så en fake-transport slipper context-byttet.
 * Bodyen leses som rå bytes (`BodyHandlers.ofByteArray`) slik at binært innhold (f.eks. PDF) ikke korrupteres av charset-dekoding.
 */
internal class JavaHttpTransport(
    connectTimeout: Duration,
    followRedirects: HttpClient.Redirect,
) : HttpTransport {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(followRedirects)
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
