package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

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
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import no.nav.tiltakspenger.libs.personklient.pdl.FellesAdressebeskyttelseError
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
internal class FellesHttpAdressebeskyttelseKlient(
    baseUrl: String,
    private val getToken: suspend () -> AccessToken,
    connectTimeout: Duration = 1.seconds,
    private val timeout: Duration = 1.seconds,
) : FellesAdressebeskyttelseKlient {
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

    private val personBolkUri = URI.create("$baseUrl/api/v1/personBolk")

    override suspend fun enkel(
        fnr: Fnr,
    ): Either<FellesAdressebeskyttelseError, List<AdressebeskyttelseGradering>?> {
        return bolk(listOf(fnr)).map {
            it[fnr]
        }
    }

    override suspend fun bolk(
        fnrListe: List<Fnr>,
    ): Either<FellesAdressebeskyttelseError, Map<Fnr, List<AdressebeskyttelseGradering>?>> {
        return withContext(Dispatchers.IO) {
            Either.catch {
                val request = createRequest(getToken(), fnrListe)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val body = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        val response = objectMapper.readValue<Map<String, PipPersondataResponse?>>(body)
                        response.mapValues { (_, value) -> value?.toPersonDtoGradering() }
                            .mapKeys { (key, _) -> Fnr.fromString(key) }
                    }.mapLeft {
                        FellesAdressebeskyttelseError.DeserializationException(body, status, it)
                    }
                } else {
                    FellesAdressebeskyttelseError.Ikke2xx(status = status, body = body).left()
                }
            }.mapLeft {
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesAdressebeskyttelseError.NetworkError(it)
            }.flatten()
        }
    }

    private fun createRequest(
        token: AccessToken,
        fnrListe: List<Fnr>,
    ): HttpRequest? = HttpRequest.newBuilder()
        .uri(personBolkUri)
        .timeout(timeout.toJavaDuration())
        .header("Authorization", "Bearer ${token.value}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .POST(createBody(fnrListe))
        .build()

    private fun createBody(fnrListe: List<Fnr>): HttpRequest.BodyPublisher? =
        HttpRequest.BodyPublishers.ofString(
            fnrListe.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
                transform = { "\"$it\"" },
            ),
        )
}
