package no.nav.tiltakspenger.libs.personklient.skjerming

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.AuthTokenProvider
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.kall.NavHeadere
import no.nav.tiltakspenger.libs.httpklient.rawRequestString
import no.nav.tiltakspenger.libs.httpklient.tryMap
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
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
 * Requesten bærer fnr, så selve requesten logges aldri til vanlig logg — kun til sikkerlogg (samme regel som før migreringen til [HttpKlient]).
 *
 * @param clock Klokken til metadata-tidsstempler i [HttpKlient]. Påkrevd; ingen default i produksjonskode (se AGENTS.md).
 */
class FellesHttpSkjermingsklient(
    endepunkt: String,
    getToken: suspend () -> AccessToken,
    clock: Clock,
    connectTimeout: Duration = 1.seconds,
    timeout: Duration = 1.seconds,
    private val logg: KLogger = KotlinLogging.logger {},
) : FellesSkjermingsklient {

    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            connectTimeout = connectTimeout,
            timeout = timeout,
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
            .mapLeft { feil -> feil.tilFellesSkjermingErrorOgLogg(sikkerloggKontekstVedUkjentFeil = "for fnr: ${fnr.verdi}") }
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
            feil.tilFellesSkjermingErrorOgLogg(sikkerloggKontekstVedUkjentFeil = "")
        }.flatMap { respons ->
            // Fnr-mapping som feiler skal gi samme utfall som før: en DeserializationException med body og status.
            respons.tryMap { bolk -> bolk.mapKeys { (personident, _) -> Fnr.fromString(personident) } }
                .mapLeft { feil -> feil.tilFellesSkjermingErrorOgLogg(sikkerloggKontekstVedUkjentFeil = "") }
        }
    }

    /** Request-body til `/skjermet`. Bærer fnr og skal derfor aldri i vanlig logg. */
    private data class SkjermetDataRequestDTO(
        val personident: String,
    )

    /** Request-body til `/skjermetBulk`. Bærer fnr og skal derfor aldri i vanlig logg. */
    private data class SkjermetDataBolkRequestDTO(
        val personidenter: List<String>,
    )

    /**
     * Mapper [HttpKlientError] til [FellesSkjermingError] og logger med samme meldinger og logg/sikkerlogg-splitt som før migreringen.
     * [sikkerloggKontekstVedUkjentFeil] bevarer forskjellen i dagens sikkerlogg-meldinger mellom enkelt- og bulk-oppslag ved ukjente feil.
     */
    private fun HttpKlientError.tilFellesSkjermingErrorOgLogg(sikkerloggKontekstVedUkjentFeil: String): FellesSkjermingError =
        when (this) {
            is HttpKlientError.ResponsMottatt -> when (this) {
                is HttpKlientError.DeserializationError -> {
                    logg.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Kunne ikke parse skjermingssvar. status=$statusCode. Se sikkerlogg for mer kontekst."
                    }
                    Sikkerlogg.error(throwable) {
                        "Kunne ikke parse skjermingssvar. status=$statusCode. response=$body. request=$rawRequestString"
                    }
                    FellesSkjermingError.DeserializationException(throwable, body, statusCode)
                }

                is HttpKlientError.UventetStatus -> {
                    logg.error(RuntimeException("Trigger stacktrace for debug.")) {
                        "Uforventet http-status ved henting av skjerming. status=$statusCode. Se sikkerlogg for mer kontekst."
                    }
                    Sikkerlogg.error { "Uforventet http-status ved henting av skjerming. status=$statusCode. response=$body. request=$rawRequestString" }
                    FellesSkjermingError.Ikke2xx(status = statusCode, body = body)
                }
            }

            // Nettverk/timeout og feil før noe ble sendt (inkl. at getToken kaster) behandles likt som før: NetworkError.
            is HttpKlientError.IngenRespons -> nettverksfeilOgLogg(throwable, sikkerloggKontekstVedUkjentFeil)

            is HttpKlientError.RequestIkkeSendt -> nettverksfeilOgLogg(throwable, sikkerloggKontekstVedUkjentFeil)
        }

    private fun nettverksfeilOgLogg(
        throwable: Throwable,
        sikkerloggKontekst: String,
    ): FellesSkjermingError.NetworkError {
        logg.error(RuntimeException("Trigger stacktrace for debug.")) {
            "Ukjent feil ved henting av skjerming. Se sikkerlogg for mer kontekst."
        }
        Sikkerlogg.error(throwable) { "Ukjent feil ved henting av skjerming${if (sikkerloggKontekst.isBlank()) "." else " $sikkerloggKontekst"}" }
        return FellesSkjermingError.NetworkError(throwable)
    }
}
