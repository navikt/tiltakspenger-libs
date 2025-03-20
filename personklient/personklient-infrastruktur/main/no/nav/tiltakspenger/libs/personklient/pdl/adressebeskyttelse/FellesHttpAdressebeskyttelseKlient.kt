package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.json.objectMapper
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
    private val logg: KLogger? = KotlinLogging.logger {},
    private val sikkerlogg: KLogger?,
) : FellesAdressebeskyttelseKlient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout.toJavaDuration())
        .followRedirects(HttpClient.Redirect.NEVER)
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
                val jsonPayload = createJsonPayload(fnrListe)
                val request = createRequest(getToken(), jsonPayload)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val responseBody = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        val response = objectMapper.readValue<Map<String, PipPersondataResponse?>>(responseBody)
                        response.mapValues { (_, value) -> value?.toPersonDtoGradering() }
                            .mapKeys { (key, _) -> Fnr.fromString(key) }
                    }.mapLeft {
                        logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                            "Kunne ikke parse adressebeskyttelsesvar. status=$status. Se sikkerlogg for mer kontekst."
                        }
                        sikkerlogg?.error(it) {
                            "Kunne ikke parse adressebeskyttelsesvar. status=$status. response=$responseBody. request=$jsonPayload"
                        }
                        FellesAdressebeskyttelseError.DeserializationException(responseBody, status, it)
                    }
                } else {
                    logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Kunne ikke hente adressebeskyttelse fra pdl-pip. status=$status. Se sikkerlogg for mer kontekst."
                    }
                    sikkerlogg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Kunne ikke hente adressebeskyttelse fra pdl-pip. status=$status. response=$responseBody. request=$jsonPayload"
                    }
                    FellesAdressebeskyttelseError.Ikke2xx(status = status, body = responseBody).left()
                }
            }.mapLeft {
                logg?.error(RuntimeException("Trigger stacktrace for debug.")) {
                    "Ukjent feil ved henting av adressebeskyttelse fra pdl-pip."
                }
                sikkerlogg?.error(it) { "Ukjent feil ved henting av adressebeskyttelse fra pdl-pip. fnrListe: $fnrListe" }
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesAdressebeskyttelseError.NetworkError(it)
            }.flatten()
        }
    }

    private fun createRequest(
        token: AccessToken,
        jsonPayload: String,
    ): HttpRequest? {
        return HttpRequest.newBuilder()
            .uri(personBolkUri)
            .timeout(timeout.toJavaDuration())
            .header("Authorization", "Bearer ${token.token}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build()
    }

    private fun createJsonPayload(fnrListe: List<Fnr>): String = fnrListe.distinct().joinToString(
        separator = ",",
        prefix = "[",
        postfix = "]",
        transform = { "\"${it.verdi}\"" },
    )
}
