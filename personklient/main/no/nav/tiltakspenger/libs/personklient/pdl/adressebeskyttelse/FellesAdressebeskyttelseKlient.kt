package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente adressebeskyttelse fra PDL
 */
interface FellesAdressebeskyttelseKlient {
    /**
     * @return null dersom identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun enkel(fnr: Fnr, token: AccessToken): Either<FellesAdressebeskyttelseError, List<AdressebeskyttelseGradering>?>

    /**
     * @return garanterer at alle identer i inputlisten er med i outputlisten. Null betyr at identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun bolk(
        fnrListe: List<Fnr>,
        token: AccessToken,
    ): Either<FellesAdressebeskyttelseError, Map<Fnr, List<AdressebeskyttelseGradering>?>>

    companion object {
        fun create(
            endepunkt: String,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): FellesAdressebeskyttelseKlient = FellesHttpAdressebeskyttelseKlient(
            endepunkt = endepunkt,
            connectTimeout = connectTimeout,
            timeout = timeout,
        )
    }
}
