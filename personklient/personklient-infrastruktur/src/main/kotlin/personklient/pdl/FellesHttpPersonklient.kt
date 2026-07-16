package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.left
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.httpklient.rawRequestString
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.personklient.pdl.FellesPersonklientError.Ikke2xx
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-klient for å hente persondata fra PDL (persondataløsningen) via GraphQL.
 *
 * Kildekode: https://github.com/navikt/pdl
 * Dokumentasjon: https://pdl-docs.ansatt.nav.no/
 * API-spec: https://github.com/navikt/pdl/blob/master/apps/api/src/main/resources/schemas/pdl.graphqls og https://pdl-playground.dev.intern.nav.no/ og https://pdl-pip-api.intern.dev.nav.no/swagger-ui/index.html (Swagger)
 * Slack: #pdl
 * Teamkatalog: https://teamkatalogen.nav.no/team/034cbcd2-ac28-4e2e-88c8-345945933f70
 *
 * Requesten bærer fnr i GraphQL-payloaden, så selve requesten logges aldri til vanlig logg — kun til sikkerlogg (samme regel som før migreringen til [HttpKlient]).
 *
 * @param endepunkt Hele URLen til PDL-tjenesten.
 * F.eks https://pdl-api.prod-fss-pub.nais.io/graphql
 * @param tema Tema for henvendelsen.
 * F.eks "IND" for individstønad (det gamle navnet på tiltakspenger)
 * @param clock Klokken til metadata-tidsstempler i [HttpKlient].
 * Påkrevd; ingen default i produksjonskode (se AGENTS.md).
 * @param transport Nettverks-sømmen til [HttpKlient]; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
internal class FellesHttpPersonklient(
    endepunkt: String,
    clock: Clock,
    tema: String = "IND",
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 10.seconds,
    private val logg: KLogger = KotlinLogging.logger {},
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : FellesPersonklient {
    private val httpKlient: HttpKlient =
        HttpKlient(clock, HttpKlientConfig(timeout = timeout), transport)

    private val uri = URI.create(endepunkt)

    // https://behandlingskatalog.intern.nav.no/process/purpose/TILTAKSPENGER/7b1ef0b2-9d17-413e-8bc3-0efed8adc623
    private val headere = listOf(NavHeadere.tema(tema), NavHeadere.behandlingsnummer("B470"))

    override suspend fun graphqlRequest(
        token: AccessToken,
        jsonRequestBody: String,
    ): Either<FellesPersonklientError, String> {
        // TODO jah: Send med correlation id
        return httpKlient.postJson<HentPersonResponse>(
            uri = uri,
            body = SerialisertJson(jsonRequestBody),
            headere = headere,
            bearerToken = token,
        ).fold(
            ifLeft = { feil -> feil.tilFellesPersonklientErrorOgLogg().left() },
            ifRight = { respons -> respons.body.extractData() },
        )
    }

    /**
     * Mapper [HttpKlientError] til [FellesPersonklientError] og logger med samme meldinger og logg/sikkerlogg-splitt som før migreringen.
     * Vanlig logg får aldri request/respons (requesten bærer fnr); sikkerlogg får redigert request og lesbar respons fra metadataen.
     */
    private fun HttpKlientError.tilFellesPersonklientErrorOgLogg(): FellesPersonklientError = when (this) {
        is HttpKlientError.ResponsMottatt -> when (this) {
            is HttpKlientError.DeserializationError -> {
                logg.error(RuntimeException("Trigger stacktrace for debug.")) {
                    "Feil ved deserialisering av PDL-respons. status=$statusCode. Se sikkerlogg for mer kontekst."
                }
                Sikkerlogg.error(throwable) { "Feil ved deserialisering av PDL-respons. status=$statusCode. response=$body. request=$rawRequestString" }
                FellesPersonklientError.DeserializationException(throwable)
            }

            is HttpKlientError.UventetStatus -> {
                logg.error(RuntimeException("Trigger stacktrace for debug.")) {
                    "Feil status ved henting av person fra PDL. status=$statusCode. Se sikkerlogg for mer kontekst."
                }
                Sikkerlogg.error(RuntimeException("Trigger stacktrace for debug.")) {
                    "Feil status ved henting av person fra PDL. status=$statusCode. response=$body. request=$rawRequestString"
                }
                if (statusCode == 401 || statusCode == 403) {
                    logg.error(RuntimeException("Trigger stacktrace for debug.")) { "Mottok $statusCode fra PDL." }
                }
                Ikke2xx(status = statusCode, body = body)
            }
        }

        // Nettverk/timeout og feil før noe ble sendt behandles likt som før: alt som ikke er en respons fra PDL er en NetworkError.
        is HttpKlientError.IngenRespons -> nettverksfeilOgLogg(throwable)

        is HttpKlientError.RequestIkkeSendt -> nettverksfeilOgLogg(throwable)
    }

    private fun HttpKlientError.nettverksfeilOgLogg(throwable: Throwable): FellesPersonklientError.NetworkError {
        logg.error(RuntimeException("Trigger stacktrace for debug.")) {
            "Ukjent feil ved henting av person fra PDL. Se sikkerlogg for mer kontekst."
        }
        Sikkerlogg.error(throwable) { "Ukjent feil ved henting av person fra PDL. request: $rawRequestString" }
        return FellesPersonklientError.NetworkError(throwable)
    }
}
