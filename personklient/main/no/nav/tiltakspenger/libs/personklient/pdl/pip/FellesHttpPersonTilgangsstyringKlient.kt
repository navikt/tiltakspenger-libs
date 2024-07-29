package no.nav.tiltakspenger.libs.personklient.pdl.pip

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import no.nav.tiltakspenger.libs.personklient.pdl.isSuccess
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * https://pdl-pip-api.dev.intern.nav.no/swagger-ui/index.html#/
 */
internal class FellesHttpPersonTilgangsstyringKlient(
    endepunkt: String,
    connectTimeout: Duration = 1.seconds,
    private val timeout: Duration = 1.seconds,
) : FellesPersonTilgangsstyringsklient {
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

    override suspend fun enkel(
        ident: String,
        token: String,
    ): Either<FellesPipError, List<AdressebeskyttelseGradering>?> {
        return bolk(listOf(ident), token).map {
            it[ident]
        }
    }

    override suspend fun bolk(
        identer: List<String>,
        token: String,
    ): Either<FellesPipError, Map<String, List<AdressebeskyttelseGradering>?>> {
        return withContext(Dispatchers.IO) {
            Either.catch {
                val request = createRequest(token, identer)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val body = httpResponse.body()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        objectMapper.readValue<Map<String, PipPersondataResponse?>>(body)
                    }.mapLeft {
                        FellesPipError.DeserializationException(it)
                    }.map {
                        it.mapValues { (_, value) -> value?.toPersonDtoGradering() }
                    }
                } else {
                    FellesPipError.Ikke2xx(status = httpResponse.statusCode(), body = body).left()
                }
            }.mapLeft {
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesPipError.NetworkError(it)
            }.flatten()
        }
    }

    private fun createRequest(
        token: String,
        identer: List<String>,
    ): HttpRequest? = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(timeout.toJavaDuration())
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(createBody(identer))
        .build()

    private fun createBody(identer: List<String>): HttpRequest.BodyPublisher? =
        HttpRequest.BodyPublishers.ofString(
            identer.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
                transform = { "\"$it\"" },
            ),
        )
}
