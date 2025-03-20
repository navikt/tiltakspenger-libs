package no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse

import arrow.core.Either
import io.github.oshai.kotlinlogging.KLogger
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import no.nav.tiltakspenger.libs.personklient.pdl.FellesAdressebeskyttelseError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Klient for å hente adressebeskyttelse fra PDL
 */
interface FellesAdressebeskyttelseKlient {
    /**
     * @return null dersom identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun enkel(fnr: Fnr): Either<FellesAdressebeskyttelseError, List<AdressebeskyttelseGradering>?>

    /**
     * @return garanterer at alle identer i inputlisten er med i outputlisten. Null betyr at identen ikke finnes. Tom liste dersom identen ikke har adressebeskyttelse.
     */
    suspend fun bolk(
        fnrListe: List<Fnr>,
    ): Either<FellesAdressebeskyttelseError, Map<Fnr, List<AdressebeskyttelseGradering>?>>

    companion object {
        fun create(
            baseUrl: String,
            getToken: suspend () -> AccessToken,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
            sikkerlogg: KLogger?,
        ): FellesAdressebeskyttelseKlient = FellesHttpAdressebeskyttelseKlient(
            baseUrl = baseUrl,
            getToken = getToken,
            connectTimeout = connectTimeout,
            timeout = timeout,
            sikkerlogg = sikkerlogg,
        )
    }
}
