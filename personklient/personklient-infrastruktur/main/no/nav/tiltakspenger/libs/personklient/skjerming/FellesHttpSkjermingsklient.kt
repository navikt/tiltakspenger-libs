package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatten
import arrow.core.left
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
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

/**
 * https://skjermede-personer-pip.dev.adeo.no/swagger-ui/index.html
 */
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

    private val uriSkjermet = URI.create("$endepunkt/skjermet")
    private val uriSkjermetBulk = URI.create("$endepunkt/skjermetBulk")

    companion object {
        const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
    }

    private val objectMapper: ObjectMapper = JsonMapper.builder()
        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .build()

    override suspend fun erSkjermetPerson(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Boolean> {
        return withContext(Dispatchers.IO) {
            Either.catch {
                val jsonPayload = "{\"personident\":\"${fnr.verdi}\"}"
                val request = createRequest(correlationId, jsonPayload, uriSkjermet)

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

    override suspend fun erSkjermetPersoner(
        fnrListe: NonEmptyList<Fnr>,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Map<Fnr, Boolean>> {
        return withContext(Dispatchers.IO) {
            Either.catch {
                val jsonPayload =
                    """{"personidenter":[${fnrListe.distinct().map { "\"${it.verdi}\"" }.joinToString(",")}]}"""
                val request = createRequest(correlationId, jsonPayload, uriSkjermetBulk)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val responseJson = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        objectMapper.readValue<Map<String, Boolean>>(responseJson).mapKeys { Fnr.fromString(it.key) }
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
                sikkerlogg?.error(it) { "Ukjent feil ved henting av skjerming." }
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesSkjermingError.NetworkError(it)
            }.flatten()
        }
    }

    private suspend fun createRequest(
        correlationId: CorrelationId,
        jsonPayload: String,
        uri: URI,
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
