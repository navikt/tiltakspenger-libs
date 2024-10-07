package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.flatten
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
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
                val request = createRequest(fnr, correlationId)

                val httpResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
                val body = httpResponse.body()
                val status = httpResponse.statusCode()
                if (httpResponse.isSuccess()) {
                    Either.catch {
                        body.lowercase().toBooleanStrict()
                    }.mapLeft {
                        FellesSkjermingError.DeserializationException(it, body, status)
                    }
                } else {
                    FellesSkjermingError.Ikke2xx(status = status, body = body).left()
                }
            }.mapLeft {
                // Either.catch slipper igjennom CancellationException som er ønskelig.
                FellesSkjermingError.NetworkError(it)
            }.flatten()
        }
    }

    private fun createRequest(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): HttpRequest? = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(timeout.toJavaDuration())
        .header("Authorization", "Bearer $getToken()")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header(NAV_CALL_ID_HEADER, correlationId.value)
        .POST(HttpRequest.BodyPublishers.ofString("{\"personident\":\"${fnr.verdi}\"}"))
        .build()
}
