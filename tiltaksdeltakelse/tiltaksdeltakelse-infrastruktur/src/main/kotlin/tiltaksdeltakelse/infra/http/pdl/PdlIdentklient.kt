package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.pdl

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientMetadata
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.kall.Statusregel
import no.nav.tiltakspenger.libs.httpklient.infra.retry.Retry
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente nåværende og historiske folkeregisteridenter fra PDL (persondataløsningen).
 * Portert fra `tiltakspenger-tiltak` sin `PdlClient` som del av at appen avvikles.
 *
 * Kildekode: https://github.com/navikt/pdl
 * Dokumentasjon: https://pdl-docs.ansatt.nav.no/
 * API-spec: https://github.com/navikt/pdl/blob/15bdc571f0357f97f524dc496fb16217ff4aa94d/apps/api/src/main/resources/schemas/pdl.graphqls#L17 og https://pdl-playground.dev.intern.nav.no/
 * Slack: #pdl
 * Teamkatalog: https://teamkatalogen.nav.no/team/034cbcd2-ac28-4e2e-88c8-345945933f70
 *
 * Tiltakshistorikk-kontrakten krever at konsumenten selv sender alle identene deltakelser skal slås opp for, og deltakelser kan være registrert på et tidligere fødselsnummer — derfor hentes historikken med.
 * Behandlingsnummeret B470 er tiltakspengers oppføring i behandlingskatalogen: https://behandlingskatalog.intern.nav.no/process/purpose/TILTAKSPENGER/7b1ef0b2-9d17-413e-8bc3-0efed8adc623
 *
 * Klienten logger ikke selv; feillogging skjer én gang i hente-tjenesten, som har domenekonteksten.
 * Klienten har ingen retry, som klienten den er portert fra ([Retry.Ingen] er default, men skrives ut for å gjøre pariteten synlig).
 * Statusregelen er [Statusregel.Alle2xx]; GraphQL svarer av design 200 på funksjonelle feil, som leses fra errors-lista.
 *
 * **Tidsbudsjett — les sammen med [no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.TiltakshistorikkKlient].**
 * En henting gjør to oppslag etter hverandre, og summen skal lande på rundt 30 sekunder i verste fall.
 * Dette oppslaget er det billigste av de to — én ident inn, identer ut, ingen sammenstilling — og får derfor 5 sekunder uten retry.
 * Resten av budsjettet går til tiltakshistorikk, som venter på Arena og de andre kildesystemene.
 *
 * @param timeout Per-kall timeout, se tidsbudsjettet over.
 * @param transport Det eneste stedet klienten rører nettverket; default er produksjonstransporten, tester sender inn `FakeHttpTransport` slik at hele den reelle pipelinen kjører.
 */
class PdlIdentklient(
    baseUrl: String,
    clock: Clock,
    authTokenProvider: AuthTokenProvider,
    connectTimeout: Duration = 2.seconds,
    timeout: Duration = 5.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) {
    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeout,
            auth = KlientAuth.System(authTokenProvider),
            retry = Retry.Ingen,
        ),
        transport = transport,
    )

    private val graphqlUri = URI.create("$baseUrl/graphql")

    /**
     * Henter nåværende og historiske folkeregisteridenter for [fnr].
     * Venstresiden skiller et kall som feilet ([KanIkkeHenteIdenter.KallFeilet]) fra et svar vi ikke fikk brukbare identer ut av; hente-tjenesten avgjør hva som feller oppslaget og hva som får fallback.
     */
    suspend fun hentNåværendeOgHistoriskeFnr(fnr: Fnr): Either<KanIkkeHenteIdenter, Nel<Fnr>> {
        return httpKlient.postJson<GraphQLResponse<HentIdenterResponse>>(
            uri = graphqlUri,
            body = HentIdenterRequest(
                query = hentIdenterQuery,
                variables = PdlVariables(ident = fnr.verdi),
            ),
            headere = listOf(
                NavHeadere.tema("IND"),
                NavHeadere.behandlingsnummer("B470"),
            ),
            godta = Statusregel.Alle2xx,
        ).mapLeft {
            KanIkkeHenteIdenter.KallFeilet(it)
        }.flatMap { respons ->
            respons.body.tilIdenter(respons.metadata)
        }
    }

    /**
     * GraphQL svarer av design 200 OK på alle svar; funksjonelle feil ligger i errors-lista.
     * PDL svarer også `data: null` sammen med errors for enkelte feilkoder, derfor er begge deler nullable her.
     */
    private fun GraphQLResponse<HentIdenterResponse>.tilIdenter(
        metadata: HttpKlientMetadata,
    ): Either<KanIkkeHenteIdenter, Nel<Fnr>> {
        // PDL kan svare med flere feil på én gang, og da bæres alle videre — den første sier sjelden hele historien.
        val graphQLFeil = errors.orEmpty().map { it.message ?: "ukjent" }.toNonEmptyListOrNull()
        if (graphQLFeil != null) {
            return KanIkkeHenteIdenter.UtenBrukbareIdenter.GraphQLFeil(
                feilmeldinger = graphQLFeil,
                metadata = metadata,
            ).left()
        }
        val identer = data?.hentIdenter?.identer.orEmpty()
            .map { Fnr.tryFromString(it.ident) ?: return KanIkkeHenteIdenter.UtenBrukbareIdenter.UgyldigIdent(metadata).left() }
        // Eksplisitt null-sjekk i stedet for `?.right() ?:` — safe-call pluss elvis på samme linje gir en gren kover ikke kan treffe.
        val ikkeTomListe = identer.toNonEmptyListOrNull()
        return if (ikkeTomListe == null) {
            KanIkkeHenteIdenter.UtenBrukbareIdenter.FantIngenIdenter(metadata).left()
        } else {
            ikkeTomListe.right()
        }
    }
}

internal data class HentIdenterRequest(val query: String, val variables: PdlVariables)

internal data class PdlVariables(val ident: String) {
    /** Identen er et fødselsnummer, så den maskeres i logger og feilmeldinger. */
    override fun toString() = "PdlVariables(ident=*****)"
}

internal data class HentIdenterResponse(
    val hentIdenter: Identliste?,
)

internal data class Identliste(
    val identer: List<IdentInformasjon>,
)

internal data class IdentInformasjon(
    val ident: String,
)

private val hentIdenterQuery = $$"""
    query($ident: ID!){
      hentIdenter(ident: $ident, grupper: FOLKEREGISTERIDENT, historikk: true) {
          identer {
            ident
          }
        }
    }
""".trimIndent()
