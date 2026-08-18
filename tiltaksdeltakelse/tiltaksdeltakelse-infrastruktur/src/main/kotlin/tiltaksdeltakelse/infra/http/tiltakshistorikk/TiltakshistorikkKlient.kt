package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk

import arrow.core.Either
import arrow.core.Nel
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.HttpKlientResponse
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.NorskIdentDto
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Request
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.dto.TiltakshistorikkV1Response
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Klient mot `tiltakshistorikk` fra Team Valp, som leverer tiltaksdeltakelser uavhengig av kildesystem (Arena, Komet eller Team Tiltak).
 * Portert fra `tiltakspenger-tiltak` sin `TiltakshistorikkClient` som del av at appen avvikles.
 *
 * Kildekode: https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltakshistorikk
 * Dokumentasjon: README-en i kildekode-mappa
 * API-spec: - (ingen OpenAPI-spec; Team Valp tilbyr en typet Kotlin-klient i https://github.com/navikt/mulighetsrommet/tree/main/common/tiltakshistorikk-client)
 * Slack: #team-valp
 * Teamkatalog: https://teamkatalogen.nav.no/team/aa730c95-b437-497b-b1ae-0ccf69a10997
 *
 * Klienten logger ikke selv; feillogging skjer én gang i hente-tjenesten, som har domenekonteksten.
 * [Retry.Fast.retryIkkeIdempotente] er satt fordi historikk-oppslaget går som POST, men er et rent leseoppslag — identer inn, historikk ut, ingen sideeffekter hos Team Valp.
 * Uten flagget ville `httpklient` aldri retryet POST-en.
 * Tiltakshistorikk godtar kun `200` som suksess.
 *
 * **Tidsbudsjett — les sammen med [no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl.PdlIdentklient].**
 * En henting gjør to oppslag etter hverandre: først PDL (maks 5 s), så tiltakshistorikk.
 * Summen skal lande på rundt 30 sekunder i verste fall, og ingen enkeltforsøk skal vare mer enn 10 sekunder.
 * Regnestykket her er 3 forsøk × 7 s + 2 × 100 ms backoff = 21,2 s, som med PDLs 5 s gir ~26 s og lar ~4 s stå igjen til Texas-token, introspeksjon og mapping.
 * Budsjettet er summert for hånd fordi `httpklient` ikke har en deadline over hele kallet.
 *
 * @param timeout Per-forsøk timeout, ikke totalbudsjett — se tidsbudsjettet over.
 * Tiltakshistorikk sammenstiller flere kildesystemer (Arena er den tregeste), men det hjelper ikke å vente lenger enn konsumentene våre gjør.
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class TiltakshistorikkKlient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 2.seconds,
    timeout: Duration = 7.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
            retry = Retry.Fast(maksForsøk = 3, delay = 100.milliseconds, retryIkkeIdempotente = true),
        ),
        transport = transport,
    )

    private val historikkUri = URI.create("$baseUrl/api/v1/historikk")

    /**
     * Returnerer hele [HttpKlientResponse], ikke bare kroppen, slik at hente-tjenesten har metadataen til sikkerlogg — rå respons på både suksess- og feilstier.
     */
    suspend fun hentTiltakshistorikk(
        identer: Nel<Fnr>,
        correlationId: CorrelationId,
    ): Either<HttpKlientError, HttpKlientResponse<TiltakshistorikkV1Response>> {
        return httpKlient.postJson(
            uri = historikkUri,
            body = TiltakshistorikkV1Request(
                identer = identer.map { NorskIdentDto(it.verdi) },
            ),
            headere = listOf(NavHeadere.navCallId(correlationId.value)),
            godta = Statusregel.Eksakt(200),
        )
    }
}
