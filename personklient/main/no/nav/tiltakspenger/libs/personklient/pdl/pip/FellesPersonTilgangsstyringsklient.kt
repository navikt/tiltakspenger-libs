package no.nav.tiltakspenger.libs.personklient.pdl.pip

import arrow.core.Either
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente adressebeskyttelse fra PDL
 */
interface FellesPersonTilgangsstyringsklient {
    /**
     * @return null dersom identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun enkel(ident: String, token: String): Either<FellesPipError, List<AdressebeskyttelseGradering>?>

    /**
     * @return garanterer at alle identer i inputlisten er med i outputlisten. Null betyr at identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun bolk(
        identer: List<String>,
        token: String,
    ): Either<FellesPipError, Map<String, List<AdressebeskyttelseGradering>?>>

    companion object {
        fun create(
            endepunkt: String,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): FellesPersonTilgangsstyringsklient = FellesHttpPersonTilgangsstyringKlient(
            endepunkt = endepunkt,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
