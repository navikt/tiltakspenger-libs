package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import mu.KLogger
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.personklient.pdl.FellesSkjermingError
import no.nav.tiltakspenger.libs.personklient.pdl.isSuccess
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class FellesHttpSkjermingsklient(
    endepunkt: String,
    private val getToken: suspend () -> AccessToken,
    connectTimeout: Duration = 1.seconds,
    private val timeout: Duration = 1.seconds,
    private val logg: KLogger? = KotlinLogging.logger {},
    private val sikkerlogg: KLogger?,
) : FellesSkjermingsklient {

    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val uri = URI.create("$endepunkt/skjermet")

    companion object {
        const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
    }

    override suspend fun erSkjermetPerson(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Boolean> {
        return withContext(Dispatchers.IO) {
            Either.catch {
                val jsonPayload = "{\"personident\":\"${fnr.verdi}\"}"
                val request = createRequest(correlationId, jsonPayload)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val responseJson = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        responseJson.lowercase().toBooleanStrict()
                    }.mapLeft {
                        logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                            "Kunne ikke parse skjermingssvar. status=$status. Se sikkerlogg for mer kontekst."
                        }
                        sikkerlogg?.error(it) {
                            "Kunne ikke parse skjermingssvar. status=$status. response=$responseJson. request=$jsonPayload"
                        }
                        FellesSkjermingError.DeserializationException(it, responseJson, status)
                    }
                } else {
                    logg?.error(RuntimeException("Trigger stacktrace for debug.")) { "Uforventet http-status ved henting av skjerming. status=$status. Se sikkerlogg for mer kontekst." }
                    sikkerlogg?.error { "Uforventet http-status ved henting av skjerming. status=$status. response=$responseJson. request=$jsonPayload" }
                    FellesSkjermingError.Ikke2xx(status = status, body = responseJson).left()
                }
            }.mapLeft {
                logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                    "Ukjent feil ved henting av skjerming. Se sikkerlogg for mer kontekst."
                }
                sikkerlogg?.error(it) { "Ukjent feil ved henting av skjerming for fnr: ${fnr.verdi}" }
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesSkjermingError.NetworkError(it)
            }.flatten()
        }
    }

    private suspend fun createRequest(
        correlationId: CorrelationId,
        jsonPayload: String,
    ): HttpRequest? {
        val token: String = getToken().token
        return HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout.toJavaDuration())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header(NAV_CALL_ID_HEADER, correlationId.value)
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build()
    }
}
