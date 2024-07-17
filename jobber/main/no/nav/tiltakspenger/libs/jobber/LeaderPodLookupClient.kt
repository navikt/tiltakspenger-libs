package no.nav.tiltakspenger.libs.jobber

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.atomicfu.atomic
import mu.KLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Gjør et kall til leader-elector sidecar for å sjekke om poden er leder.
 * Dersom en pod har blitt valgt som leder, vil den være det til podden er slettet fra kubernetes clusteret.
 *
 * @param electorPath Full URL til leader-elector sidecar. Dokumentasjonen sier den skal ligge i environment variable $ELECTOR_PATH
 * Docs: https://doc.nais.io/services/leader-election/
 */
internal class LeaderPodLookupClient(
    private val electorPath: String,
    private val logger: KLogger,
    connectTimeout: kotlin.time.Duration = 1.seconds,
) : LeaderPodLookup {

    private val objectMapper = ObjectMapper()

    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val amITheLeader = atomic(false)

    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> {
        // I dette tilfellet vil vi allerede være leder til podden slettes fra kubernetes clusteret og vi kan trygt returnere true.
        if (amITheLeader.value) return true.right()

        return Either.catch {
            val uri = URI.create(addProtocolIfMissing(electorPath))
            val request = HttpRequest.newBuilder().uri(uri).GET().build()
            client.send(request, HttpResponse.BodyHandlers.ofString()).let { httpResponse ->
                val body = httpResponse.body()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        logger.error("LeaderPodLookup: json-respons fra leader-elector sidecar manglet keyen 'name'. Uri: $uri, body: $body, status: ${httpResponse.statusCode()}")
                        objectMapper.readTree(body).get("name").asText(null)
                    }.mapLeft {
                        LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer
                    }
                } else {
                    logger.error("LeaderPodLookup: Klarte ikke å kontakte leader-elector sidecar på uri $uri. Fikk status ${httpResponse.statusCode()} og body: $body")
                    LeaderPodLookupFeil.Ikke2xx(httpResponse.statusCode(), body).left()
                }
            }
        }.mapLeft {
            logger.error("Klarte ikke å kontakte leader-elector-containeren", it)
            LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer
        }.flatten().map { leaderHostname ->
            logger.debug { "Fant leder med navn '$leaderHostname'. Mitt hostname er '$localHostName'." }
            (leaderHostname == localHostName).also {
                if (it) amITheLeader.value = true
            }
        }
    }

    private fun addProtocolIfMissing(endpoint: String): String {
        return if (endpoint.startsWith("http")) {
            endpoint
        } else {
            "http://$endpoint"
        }
    }
}

internal fun <T> HttpResponse<T>.isSuccess(): Boolean = this.statusCode() in 200..299
