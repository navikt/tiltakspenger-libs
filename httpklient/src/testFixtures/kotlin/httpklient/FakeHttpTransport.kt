package no.nav.tiltakspenger.libs.httpklient

import no.nav.tiltakspenger.libs.json.serialize
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit

/**
 * Kø-basert [HttpTransport] for tester: svarer med køede responser/exceptions og tar opp kallene den mottar.
 *
 * Faken emulerer ingenting: den byttes inn som transport på en ekte [HttpKlient], slik at hele den reelle pipelinen kjører i test — auth-materialisering, retry-gates, statusregler, Jackson-deserialisering, metadata og redaksjon.
 * En køet `500` gir derfor automatisk `Left(UventetStatus)` fordi produksjonens statusregel evalueres, og en DTO med ødelagte annotasjoner feiler i test akkurat som i produksjon.
 *
 * Kontrakt: ren FIFO — svarene konsumeres i køet rekkefølge uavhengig av URI (bevisst; parallelle kall mot samme fake gir nondeterministisk tildeling og skal heller bruke én klient/fake per mål).
 * Tom kø kaster [AssertionError] med metode og URI, slik at et manglende testoppsett feiler høylytt i stedet for å gi en tilfeldig defaultverdi.
 * Merk at retry/skip-cache-retry konsumerer ett køet svar per forsøk — en test med `Retry.Fast(maksForsøk = 2)` må køe to svar for å øve begge forsøkene.
 * Trådsikker: kø og opptak beskyttes av [lock], så faken kan deles mellom coroutines i en test (jf. at [HttpKlient] selv kan brukes parallelt).
 *
 * Simulér transportfeil med [leggIKøKast] og JDK-exceptions (`HttpTimeoutException` → [HttpKlientError.Timeout], `IOException` → [HttpKlientError.NetworkError]).
 * Ikke bruk `CancellationException` til dette — den fanges bevisst ikke av pipelinen (se kontrakten på [HttpTransport]).
 */
class FakeHttpTransport : HttpTransport {
    private val lock = Any()
    private val kø = ArrayDeque<KøetSvar>()
    private val mutableMottatteKall = mutableListOf<MottattKall>()

    /** Kallene transporten har mottatt, i mottatt rekkefølge, med ferdig materialiserte headere (inkl. `Authorization` fra pipelinen). */
    val mottatteKall: List<MottattKall> get() = synchronized(lock) { mutableMottatteKall.toList() }

    fun reset() {
        synchronized(lock) {
            kø.clear()
            mutableMottatteKall.clear()
        }
    }

    /**
     * Ett mottatt kall: den ferdig bygde [request]en fra pipelinen, pluss request-bodyen drenert til bytes ved mottak.
     * [bodyBytes] er tom for kall uten body (GET).
     */
    class MottattKall(
        val request: HttpRequest,
        val bodyBytes: ByteArray,
    ) {
        val metode: String get() = request.method()
        val uri: URI get() = request.uri()
        val bodyTekst: String get() = bodyBytes.toString(Charsets.UTF_8)
    }

    /**
     * Køer en JSON-respons ved å serialisere [dto] med felles objectMapper til ekte bytes.
     * Responsen går gjennom produksjonens Jackson-deserialisering i testen — annotasjonsfeil og form-avvik fanges dermed for ekte.
     */
    fun leggIKøJson(dto: Any, statusCode: Int = 200) {
        require(dto !is String) { "Bruk leggIKøJson(json: String) for ferdig JSON — denne overloaden serialiserer DTO-er." }
        leggIKøBytes(serialize(dto).toByteArray(), contentType = "application/json", statusCode = statusCode)
    }

    /** Køer en ferdig JSON-streng som sendes verbatim som respons-body. */
    fun leggIKøJson(json: String, statusCode: Int = 200) {
        leggIKøBytes(json.toByteArray(), contentType = "application/json", statusCode = statusCode)
    }

    /** Køer en respons med gitt status og tekst-body — typisk feilstatuser (`500`, `403` med feil-body osv.). */
    fun leggIKøStatus(statusCode: Int, body: String = "", contentType: String = "application/json") {
        leggIKøBytes(body.toByteArray(), contentType = contentType, statusCode = statusCode)
    }

    /** Køer en binær respons (f.eks. en PDF). */
    fun leggIKøBytes(bytes: ByteArray, contentType: String, statusCode: Int = 200) {
        leggIKø(
            TransportRespons(
                statusCode = statusCode,
                headere = mapOf("Content-Type" to listOf(contentType)),
                body = bytes,
            ),
        )
    }

    /** Køer en respons uten body og uten `Content-Type` (typisk `204`). */
    fun leggIKøTomRespons(statusCode: Int = 204) {
        leggIKø(TransportRespons(statusCode = statusCode, headere = emptyMap(), body = ByteArray(0)))
    }

    /** Køer en vilkårlig [TransportRespons] for full kontroll over status, headere og bytes. */
    fun leggIKø(respons: TransportRespons) {
        synchronized(lock) { kø += KøetSvar.Respons(respons) }
    }

    /** Køer en exception som kastes fra transporten — pipelinen mapper den til [HttpKlientError.Timeout]/[HttpKlientError.NetworkError]. */
    fun leggIKøKast(throwable: Throwable) {
        synchronized(lock) { kø += KøetSvar.Kast(throwable) }
    }

    override suspend fun send(request: HttpRequest): TransportRespons {
        // Bodyen dreneres utenfor låsen (kan blokkere kort), men opptak + uttak av svar skjer atomisk under låsen.
        val bodyBytes = drenBodyBytes(request)
        val svar = synchronized(lock) {
            mutableMottatteKall += MottattKall(request, bodyBytes)
            kø.removeFirstOrNull()
        } ?: throw AssertionError("FakeHttpTransport mangler køet svar for ${request.method()} ${request.uri()}")
        return when (svar) {
            is KøetSvar.Respons -> svar.respons
            is KøetSvar.Kast -> throw svar.throwable
        }
    }

    private sealed interface KøetSvar {
        data class Respons(val respons: TransportRespons) : KøetSvar

        data class Kast(val throwable: Throwable) : KøetSvar
    }

    /**
     * Drenerer requestens `BodyPublisher` til bytes via en [Flow.Subscriber] som samler ByteBuffers.
     * JDK-ens publishers (`ofString`/`noBody`) leverer synkront ved subscribe, men vi venter med timeout for å være robuste mot andre implementasjoner.
     */
    private fun drenBodyBytes(request: HttpRequest): ByteArray {
        val publisher = request.bodyPublisher().orElse(null) ?: return ByteArray(0)
        if (publisher.contentLength() == 0L) return ByteArray(0)
        val buffer = ByteArrayOutputStream()
        val ferdig = CountDownLatch(1)
        var feil: Throwable? = null
        publisher.subscribe(
            object : Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)

                override fun onNext(item: ByteBuffer) {
                    val bytes = ByteArray(item.remaining())
                    item.get(bytes)
                    buffer.write(bytes)
                }

                override fun onError(throwable: Throwable) {
                    feil = throwable
                    ferdig.countDown()
                }

                override fun onComplete() = ferdig.countDown()
            },
        )
        if (!ferdig.await(5, TimeUnit.SECONDS)) {
            throw AssertionError("Timet ut ved drenering av request-body i FakeHttpTransport for ${request.method()} ${request.uri()}")
        }
        feil?.let { throw AssertionError("Klarte ikke å lese request-body i FakeHttpTransport for ${request.method()} ${request.uri()}", it) }
        return buffer.toByteArray()
    }
}
