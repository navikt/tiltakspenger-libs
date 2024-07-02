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
import no.nav.tiltakspenger.libs.person.Person
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
 */
internal class FellesHttpPersonklient(
    private val endepunkt: String,
    private val tema: String = "IND",
    connectTimeout: Duration = 20.seconds,
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

    override fun hentPerson(
        ident: String,
        token: String,
    ): Either<FellesPersonklientError, Pair<Person, List<String>>> {
        return Either.catch {
            // TODO jah: Send med correlation id
            val request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Tema", tema)
                .header("behandlingsnummer", "B470")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(hentPersonQuery(ident))))
                .build()

            client.send(request, HttpResponse.BodyHandlers.ofString()).let { httpResponse ->
                val body = httpResponse.body()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        objectMapper.readValue<HentPersonResponse>(body)
                    }.mapLeft {
                        FellesPersonklientError.DeserializationException(it)
                    }.map { it.toPerson() }.flatten()
                } else {
                    Ikke2xx(status = httpResponse.statusCode(), body = body).left()
                }
            }
        }.mapLeft { FellesPersonklientError.NetworkError(it) }.flatten()
    }
}

internal fun <T> HttpResponse<T>.isSuccess(): Boolean = this.statusCode() in 200..299
