package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KLogger
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.Ikke2xx
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * @param endepunkt Hele URLen til PDL-tjenesten. F.eks https://pdl-api.prod-fss-pub.nais.io/graphql
 * @param tema Tema for henvendelsen. F.eks "IND" for individstønad (det gamle navnet på tiltakspenger)
 */
internal class FellesHttpPersonklient(
    private val endepunkt: String,
    private val tema: String = "IND",
    connectTimeout: Duration = 1.seconds,
    private val timeout: Duration = 1.seconds,
    private val logg: KLogger? = KotlinLogging.logger {},
    private val sikkerlogg: KLogger?,
) : FellesPersonklient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val objectMapper: ObjectMapper = JsonMapper.builder()
        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .build()

    private val uri = URI.create(endepunkt)

    override suspend fun hentPerson(
        fnr: Fnr,
        token: AccessToken,
        jsonRequestBody: String,
    ): Either<FellesPersonklientError, String> {
        return Either.catch {
            // TODO jah: Send med correlation id
            val request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer ${token.value}")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Tema", tema)
                // https://behandlingskatalog.intern.nav.no/process/purpose/TILTAKSPENGER/7b1ef0b2-9d17-413e-8bc3-0efed8adc623
                .header("behandlingsnummer", "B470")
                .timeout(timeout.toJavaDuration())
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let { httpResponse ->
                val responseBody = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        objectMapper.readValue<HentPersonResponse>(responseBody)
                    }.mapLeft {
                        logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                            "Feil ved deserialisering av PDL-respons. status=$status. Se sikkerlogg for mer kontekst."
                        }
                        sikkerlogg?.error(it) { "Feil ved deserialisering av PDL-respons. status=$status. response=$responseBody. request=$jsonRequestBody" }
                        FellesPersonklientError.DeserializationException(it)
                    }.map { it.extractData() }.flatten()
                } else {
                    logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Feil status ved henting av person fra PDL. status=$status. Se sikkerlogg for mer kontekst."
                    }
                    sikkerlogg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Feil status ved henting av person fra PDL. status=$status. response=$responseBody. request=$jsonRequestBody"
                    }
                    Ikke2xx(status = status, body = responseBody).left()
                }
            }
        }.mapLeft {
            logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                "Ukjent feil ved henting av person fra PDL. Se sikkerlogg for mer kontekst."
            }
            sikkerlogg?.error(it) { "Ukjent feil ved henting av person fra PDL. request: $jsonRequestBody" }
            FellesPersonklientError.NetworkError(it)
        }.flatten()
    }
}

internal fun <T> HttpResponse<T>.isSuccess(): Boolean = this.statusCode() in 200..299
