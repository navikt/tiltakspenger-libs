package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.left
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.UriSynlighet
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.SerialisertJson
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
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
 * @param transport Transporten som gjør nettverkskallet; default er produksjonstransporten, tester sender inn `FakeHttpTransport`.
 */
internal class FellesHttpPersonklient(
    endepunkt: String,
    clock: Clock,
    tema: String = "IND",
    connectTimeout: Duration = 10.seconds,
    timeout: Duration = 10.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : FellesPersonklient {
    private val httpKlient: HttpKlient =
        HttpKlient(
            clock,
            // GraphQL-endepunktet er én fast sti uten path- eller query-parametre; identen ligger i spørringens variabler.
            // Da kan URIen stå i vanlig logg.
            HttpKlientConfig(timeout = timeout, uriSynlighet = UriSynlighet.VanligLogg),
            transport,
        )

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
            ifLeft = { feil -> feil.tilFellesPersonklientError().left() },
            ifRight = { respons -> respons.body.extractData() },
        )
    }

    /**
     * Mapper [HttpKlientError] til [FellesPersonklientError] uten å logge.
     *
     * Loggingen lå her før, men klienten kjenner ikke domenekonteksten (sak, behandling), så linja måtte bli generisk — «Ukjent feil ved henting av person fra PDL» dekket alt fra en connect-timeout til at tokenhentingen kastet.
     * Nå følger feilen med opp til konsumenten, som logger én gang med `HttpKlientError.loggFeil` og har både feilart, endepunkt og sin egen kontekst å skrive.
     */
    private fun HttpKlientError.tilFellesPersonklientError(): FellesPersonklientError = when (this) {
        is HttpKlientError.DeserializationError -> FellesPersonklientError.DeserializationException(this)

        is HttpKlientError.UventetStatus -> Ikke2xx(this)

        // Nettverk/timeout og feil før noe ble sendt samles som før i NetworkError; hvilken av dem det var står i httpKlientError.
        is HttpKlientError.IngenRespons,
        is HttpKlientError.RequestIkkeSendt,
        -> FellesPersonklientError.NetworkError(this)
    }
}
