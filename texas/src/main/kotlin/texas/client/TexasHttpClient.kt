package no.nav.tiltakspenger.libs.texas.client

import arrow.core.getOrElse
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.httpklient.HttpKlientError
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlient
import no.nav.tiltakspenger.libs.httpklient.infra.HttpKlientConfig
import no.nav.tiltakspenger.libs.httpklient.infra.kall.KlientAuth
import no.nav.tiltakspenger.libs.httpklient.infra.transport.HttpTransport
import no.nav.tiltakspenger.libs.httpklient.infra.transport.JavaHttpTransport
import no.nav.tiltakspenger.libs.httpklient.rawResponseString
import no.nav.tiltakspenger.libs.httpklient.throwableOrNull
import no.nav.tiltakspenger.libs.logging.Sikkerlogg
import no.nav.tiltakspenger.libs.texas.IdentityProvider
import no.nav.tiltakspenger.libs.texas.log
import java.net.URI
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * [TexasClient] mot NAIS Texas-sidecaren (token exchange as a service), bygget på felles [HttpKlient].
 *
 * ## Statuser fra token-endepunktene
 * `/api/v1/token` ([getSystemToken]) og `/api/v1/token/exchange` ([exchangeToken]) svarer per Texas' OpenAPI-spec med `200 OK` (token), `400 Bad Request` eller `500 Internal Server Error` — se [OpenAPI-spec](https://doc.nais.io/auth/reference/#openapi-specification).
 * Texas' token-endepunkter returnerer altså _ikke_ `401`/`403`.
 * En `401 Unauthorized`/`403 Forbidden` kommer fra _target-API-et_ du kaller med det utstedte tokenet, ikke fra Texas selv.
 *
 * ## Feilkontrakt mot konsumentene
 * [TexasClient]-kontrakten er uendret: metodene returnerer verdien eller kaster.
 * Alle Texas-endepunktene snakker JSON begge veier; ikke-2xx eller udeserialiserbar respons logges (med responskode, som før) og kastes videre — underliggende exception der én finnes, ellers en [RuntimeException] med responskoden.
 * Denne klienten kan ikke selv bruke token-basert auth ([KlientAuth.Ingen]) — det er den som lager tokens.
 *
 * ## Caching og `skip_cache` (skipCache)
 * Texas cacher tokens: endepunktet returnerer alltid et cachet token hvis det finnes, og aldri et utløpt token.
 * `skipCache = true` setter `skip_cache` i requesten og tvinger Texas til å gå forbi cachen og hente et ferskt token fra identity provideren (f.eks. Entra ID).
 * Per NAIS-docs er dette kun nødvendig når target-API-et avviser tokenet, f.eks. fordi tilganger har endret seg siden tokenet ble utstedt — se [Consume internal API as an application](https://doc.nais.io/auth/entra-id/how-to/consume-m2m/#acquire-token).
 * Beslutningen om _når_ et avvist token skal trigge et nytt kall med `skipCache = true` (typisk ved `401` fra target-API-et) ligger hos kalleren, f.eks. `httpklient` sin skip-cache-retry sammen med `HttpKlientConfig.skipCacheRetryStatuses` og [TexasSystemTokenProvider].
 *
 * @param transport Kun for tester: bytt inn `FakeHttpTransport` fra httpklient sine testFixtures, så kjører resten av klientens config og hele pipelinen uendret; default er produksjonstransporten.
 */
class TexasHttpClient(
    introspectionUrl: String,
    tokenUrl: String,
    tokenExchangeUrl: String,
    timeoutSeconds: Long = 5L,
    private val clock: Clock,
    transport: HttpTransport = JavaHttpTransport(connectTimeout = timeoutSeconds.seconds),
) : TexasClient {
    private val introspectionUri = URI.create(introspectionUrl)
    private val tokenUri = URI.create(tokenUrl)
    private val tokenExchangeUri = URI.create(tokenExchangeUrl)

    private val httpKlient: HttpKlient = HttpKlient(
        clock = clock,
        config = HttpKlientConfig(
            timeout = timeoutSeconds.seconds,
            auth = KlientAuth.Ingen,
        ),
        transport = transport,
    )

    override suspend fun introspectToken(
        token: String,
        identityProvider: IdentityProvider,
    ): TexasIntrospectionResponse {
        val texasIntrospectionRequest = TexasIntrospectionRequest(
            identityProvider = identityProvider.value,
            token = token,
        )
        return httpKlient.postJson<TexasIntrospectionResponse>(introspectionUri, texasIntrospectionRequest)
            .getOrElse { feil ->
                kastTexasFeil(
                    statusMelding = "Kall for autentisering mot Texas feilet",
                    melding = "Kall for autentisering mot Texas feilet",
                    feil = feil,
                )
            }
            .body
    }

    override suspend fun getSystemToken(
        audienceTarget: String,
        identityProvider: IdentityProvider,
        rewriteAudienceTarget: Boolean,
        skipCache: Boolean,
    ): AccessToken {
        val texasTokenRequest = TexasTokenRequest(
            identityProvider = identityProvider.value,
            target = if (rewriteAudienceTarget) {
                val target = audienceTarget.replace(':', '.')
                "api://$target/.default"
            } else {
                audienceTarget
            },
            skipCache = skipCache,
        )
        return httpKlient.postJson<TexasTokenResponse>(tokenUri, texasTokenRequest)
            .getOrElse { feil ->
                // Meldingstekstene beholdes ordrett fra gammel klient (inkl. den manglende «for»-en) i tilfelle logg-baserte søk/alerts.
                kastTexasFeil(
                    statusMelding = "Kall for å hente token mot Texas feilet",
                    melding = "Kall å hente token mot Texas feilet",
                    feil = feil,
                )
            }
            .body
            .toAccessToken(clock = clock)
    }

    override suspend fun exchangeToken(
        userToken: String,
        audienceTarget: String,
        identityProvider: IdentityProvider,
        skipCache: Boolean,
    ): AccessToken {
        val texasExchangeTokenRequest = TexasExchangeTokenRequest(
            identityProvider = identityProvider.value,
            target = audienceTarget,
            userToken = userToken,
            skipCache = skipCache,
        )
        return httpKlient.postJson<TexasTokenResponse>(tokenExchangeUri, texasExchangeTokenRequest)
            .getOrElse { feil ->
                kastTexasFeil(
                    statusMelding = "Kall for å veksle token mot Texas feilet",
                    melding = "Kall å veksle token mot Texas feilet",
                    feil = feil,
                )
            }
            .body
            .toAccessToken(clock = clock)
    }

    /**
     * Bevarer den gamle klientens feilkontrakt: logg (responskode når serveren svarte, ellers kun generell linje) + sikkerlogg, og kast videre.
     * En [HttpKlientError] med underliggende exception (nettverk, timeout, deserialisering) kaster den originale exceptionen, som før.
     * [HttpKlientError.UventetStatus] har ingen exception og kaster en [RuntimeException] med responskoden — mot gammel klient er dette en forbedring fra en tilfeldig deserialiseringsfeil av feil-bodyen.
     */
    private fun kastTexasFeil(statusMelding: String, melding: String, feil: HttpKlientError): Nothing {
        if (feil is HttpKlientError.ResponsMottatt) {
            log.error { "$statusMelding, responskode ${feil.statusCode}" }
        }
        log.error { "$melding, se sikker logg for detaljer" }
        val throwable = feil.throwableOrNull()
        Sikkerlogg.error(throwable) { "$melding, melding: ${throwable?.message ?: feil.rawResponseString}" }
        throw throwable ?: RuntimeException("$melding, responskode ${feil.metadata.statusCode}")
    }
}
