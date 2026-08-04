package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.UriSynlighet
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.httpklient.tryMap
import no.nav.tiltakspenger.libs.personklient.pdl.FellesSkjermingError
import java.net.URI
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * HTTP-klient for å sjekke om personer er skjermet (egen ansatt) via skjermede-personer-pip.
 *
 * Kildekode: https://github.com/navikt/skjerming
 * Dokumentasjon: https://navikt.github.io/skjerming/
 * API-spec: https://skjermede-personer-pip.dev.adeo.no/swagger-ui/index.html
 * Slack: #skjermingsløsningen
 * Teamkatalog: https://teamkatalogen.nav.no/tag/Skjermingsl%C3%B8sningen
 *
 * Requesten bærer fnr, så den logges aldri til vanlig logg — kun til sikkerlogg.
 * URIen er derimot frikjent ([UriSynlighet.VanligLogg]): begge endepunktene er faste stier, og identene ligger i bodyen.
 *
 * Klienten logger ikke selv; se [tilFellesSkjermingError].
 *
 * @param clock Klokken til metadata-tidsstempler i [HttpKlient].
 * Påkrevd; ingen default i produksjonskode (se AGENTS.md).
 * @param connectTimeout Tid til å få opp forbindelsen.
 * Sto på 1 s, som en kald TCP/TLS-oppkobling mot en ekstern host fint kan overskride — og siden klienten ikke retryer, veltet ett tregt oppkoblingsforsøk hele tilgangskontrollen.
 * @param timeout Tid til å få svaret.
 */
class FellesHttpSkjermingsklient(
    endepunkt: String,
    getToken: suspend () -> AccessToken,
    clock: Clock,
    connectTimeout: Duration = 3.seconds,
    timeout: Duration = 5.seconds,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = connectTimeout),
) : FellesSkjermingsklient {

    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        transport = transport,
        config = HttpKlientConfig(
            timeout = timeout,
            // Begge endepunktene er faste stier uten path- eller query-parametre; identene ligger i request-bodyen.
            // Da kan URIen stå i vanlig logg, og feillogger navngir endepunktet i klartekst.
            uriSynlighet = UriSynlighet.VanligLogg,
            auth = KlientAuth.System(
                object : AuthTokenProvider {
                    // getToken-lambdaen er frossen offentlig API og har ingen skip-cache-semantikk, så parameteren ignoreres bevisst.
                    override suspend fun hentToken(skipCache: Boolean): AccessToken = getToken()
                },
            ),
            // Før migreringen fantes ingen ny-token-retry ved 401, og getToken har ukjent cache-semantikk — paritet fremfor doble kall.
            skipCacheRetryStatuses = emptySet(),
        ),
    )

    private val uriSkjermet = URI.create("$endepunkt/skjermet")
    private val uriSkjermetBulk = URI.create("$endepunkt/skjermetBulk")

    companion object {
        @Suppress("unused")
        const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
    }

    override suspend fun erSkjermetPerson(
        fnr: Fnr,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Boolean> {
        return httpKlient.postJson<Boolean>(
            uri = uriSkjermet,
            body = SkjermetDataRequestDTO(personident = fnr.verdi),
            headere = listOf(NavHeadere.navCallId(correlationId.value)),
        ).map { respons -> respons.body }
            .mapLeft { feil -> feil.tilFellesSkjermingError() }
    }

    override suspend fun erSkjermetPersoner(
        fnrListe: NonEmptyList<Fnr>,
        correlationId: CorrelationId,
    ): Either<FellesSkjermingError, Map<Fnr, Boolean>> {
        return httpKlient.postJson<Map<String, Boolean>>(
            uri = uriSkjermetBulk,
            body = SkjermetDataBolkRequestDTO(personidenter = fnrListe.distinct().map { it.verdi }),
            headere = listOf(NavHeadere.navCallId(correlationId.value)),
        ).mapLeft { feil ->
            feil.tilFellesSkjermingError()
        }.flatMap { respons ->
            // Fnr-mapping som feiler skal gi samme utfall som før: en DeserializationException med body og status.
            respons.tryMap { bolk -> bolk.mapKeys { (personident, _) -> Fnr.fromString(personident) } }
                .mapLeft { feil -> feil.tilFellesSkjermingError() }
        }
    }

    /**
     * Request-body til `/skjermet`.
     * Bærer fnr og skal derfor aldri i vanlig logg.
     */
    private data class SkjermetDataRequestDTO(
        val personident: String,
    )

    /**
     * Request-body til `/skjermetBulk`.
     * Bærer fnr og skal derfor aldri i vanlig logg.
     */
    private data class SkjermetDataBolkRequestDTO(
        val personidenter: List<String>,
    )

    /**
     * Mapper [HttpKlientError] til [FellesSkjermingError] uten å logge.
     *
     * Loggingen lå her før, men klienten kjenner ikke domenekonteksten (sak, barn, saksbehandler), så linja måtte bli generisk — «Ukjent feil ved henting av skjerming» dekket alt fra en connect-timeout til at tokenhentingen kastet.
     * Nå følger feilen med opp til konsumenten, som logger én gang med `HttpKlientError.loggFeil` og har både feilart, endepunkt og sin egen kontekst å skrive.
     */
    private fun HttpKlientError.tilFellesSkjermingError(): FellesSkjermingError =
        when (this) {
            is HttpKlientError.DeserializationError -> FellesSkjermingError.DeserializationException(this)

            is HttpKlientError.UventetStatus -> FellesSkjermingError.Ikke2xx(this)

            // Nettverk/timeout og feil før noe ble sendt (inkl. at getToken kaster) samles som før i NetworkError; hvilken av dem det var står i httpKlientError.
            is HttpKlientError.IngenRespons,
            is HttpKlientError.RequestIkkeSendt,
            -> FellesSkjermingError.NetworkError(this)
        }
}
