package no.nav.tiltakspenger.libs.httpklient.infra

import no.nav.tiltakspenger.libs.httpklient.infra.circuitbreaker.CircuitBreakerConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Konfigurasjonen til en `HttpKlient` — ren data, satt én gang per klientinstans.
 *
 * Det finnes bevisst ingen per-kall-overstyringer: et endepunkt med avvikende behov (f.eks. utbetalings trege simulering) får en egen klientinstans med egen config.
 * To instanser er enklere å resonnere om enn per-request-aksene den gamle `RequestBuilder`-en hadde.
 *
 * Redirects følges aldri (ingen konsument bruker det); konsumenten ser eventuelle `3xx`-svar eksplisitt som [no.nav.tiltakspenger.libs.httpklient.HttpKlientError.UventetStatus].
 */
data class HttpKlientConfig(
    /** Timeout per kall (request-timeout på `java.net.http.HttpRequest`). */
    val timeout: Duration = 30.seconds,

    /**
     * Hvordan `Authorization`-headeren settes.
     * Se [KlientAuth].
     */
    val auth: KlientAuth = KlientAuth.Ingen,

    /**
     * Retry-oppførsel.
     * Default [Retry.Ingen] — retry er en aktiv beslutning per klient.
     */
    val retry: Retry = Retry.Ingen,

    /**
     * Circuit breaker for denne klientinstansen.
     * Default [CircuitBreakerConfig.None]; state er lokal per instans og caches per breaker-navn.
     */
    val circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig.None,

    /**
     * HTTP-statuser som utløser skip-cache-retryen beskrevet på [no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider].
     *
     * Default er kun `401 Unauthorized` — den statusen betyr utvetydig at tokenet ble avvist av autentiseringen, og et ferskt token kan hjelpe.
     * `403 Forbidden` er bevisst _ikke_ med som default: for mange API-er er `403` et persistent tilgangsavslag (ABAC, manglende rolle på ressursen — ikke på tokenet), og en skip-cache-retry ville da doblet trafikken uten å hjelpe.
     * Konsumenter der `403` faktisk betyr «token-permissions har endret seg» kan opt-e inn med `setOf(401, 403)`; tom mengde slår retryen helt av.
     */
    val skipCacheRetryStatuses: Set<Int> = setOf(401),

    /**
     * Tidskilde for varighetsmåling (per-forsøk-varigheter og total retry-tid).
     * Bevisst atskilt fra klientens `clock`: en [TimeSource] er monoton og immun mot klokkejustering, i motsetning til veggklokken.
     * Default er [TimeSource.Monotonic]; sett en [kotlin.time.TestTimeSource] for deterministiske tester.
     */
    val timeSource: TimeSource = TimeSource.Monotonic,
)
