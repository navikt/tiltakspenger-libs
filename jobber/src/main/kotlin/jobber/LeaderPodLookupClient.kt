package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.httpklient.throwableOrNull
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Gjør et kall til leader-elector sidecar for å sjekke om poden er leder.
 * Dersom en pod har blitt valgt som leder, vil den være det til podden er slettet fra kubernetes-clusteret.
 * Kallet gjøres med felles [HttpKlient] uten auth — sidecaren er lokal og uautentisert.
 *
 * @param electorPath Full URL til leader-elector sidecar, med eller uten `http://`-prefiks.
 * Dokumentasjonen sier den skal ligge i environment-variabelen `$ELECTOR_PATH`.
 * @param clock Klokke til tidsstempler i [HttpKlient]-metadata.
 * Påkrevd; ingen default i produksjonskode (se AGENTS.md).
 * @param connectTimeout Connect-timeout mot sidecaren; den er lokal, så default er stram.
 * @param timeout Request-timeout per kall.
 * Den gamle klienten hadde ingen request-timeout (kunne henge for alltid); et lite tak er strengt bedre for en lokal sidecar.
 * @param transport Nettverks-sømmen til [HttpKlient]; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 *
 * Docs: https://doc.nais.io/services/leader-election/
 */
class LeaderPodLookupClient(
    private val electorPath: String,
    private val logger: KLogger,
    clock: Clock,
    connectTimeout: Duration = 1.seconds,
    timeout: Duration = 5.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : LeaderPodLookup {

    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(timeout = timeout),
        transport = transport,
    )

    // Once we become leader, we cache it.
    // Per NAIS docs, a leader keeps its role until the pod is deleted from the kubernetes cluster, so this short-circuit is safe.
    private val amITheLeader = atomic(false)

    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> {
        // I dette tilfellet vil vi allerede være leder til podden slettes fra kubernetes-clusteret og vi kan trygt returnere true.
        if (amITheLeader.value) return true.right()

        val uri = Either.catch { URI.create(addProtocolIfMissing(electorPath)) }.getOrElse {
            logger.error(it) { "LeaderPodLookup: ugyldig elector path '$electorPath'." }
            return LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
        }

        return runBlocking { httpKlient.getJson<ElectorResponse>(uri) }
            .mapLeft { feil -> feil.tilLeaderPodLookupFeil(uri) }
            .map { respons ->
                val leaderHostname = respons.body.name
                logger.debug { "Fant leder med navn '$leaderHostname'. Mitt hostname er '$localHostName'." }
                (leaderHostname == localHostName).also { erLeder ->
                    if (erLeder) amITheLeader.value = true
                }
            }
    }

    private fun HttpKlientError.tilLeaderPodLookupFeil(uri: URI): LeaderPodLookupFeil = when (this) {
        is HttpKlientError.UventetStatus -> {
            logger.error { "LeaderPodLookup: Klarte ikke å kontakte leader-elector sidecar på uri $uri. Fikk status $statusCode og body: $body" }
            LeaderPodLookupFeil.Ikke2xx(statusCode, body)
        }

        is HttpKlientError.DeserializationError -> {
            logger.error { "LeaderPodLookup: json-respons fra leader-elector sidecar manglet keyen 'name' eller var ugyldig. Uri: $uri, body: $body, status: $statusCode" }
            LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer
        }

        is HttpKlientError.RequestIkkeSendt, is HttpKlientError.IngenRespons -> {
            logger.error(throwableOrNull()) { "Klarte ikke å kontakte leader-elector-containeren" }
            LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer
        }
    }

    private fun addProtocolIfMissing(endpoint: String): String {
        return if (endpoint.startsWith("http")) {
            endpoint
        } else {
            @Suppress("HttpUrlsUsage")
            "http://$endpoint"
        }
    }
}

/**
 * Responsen fra leader-elector sidecar.
 * Sidecaren sender også felter vi ikke bruker (f.eks. `last_update`); felles objectMapper ignorerer ukjente felter.
 * Manglende eller `null` `name` gir [HttpKlientError.DeserializationError], som mappes til [LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer].
 */
internal class ElectorResponse(
    val name: String,
)
