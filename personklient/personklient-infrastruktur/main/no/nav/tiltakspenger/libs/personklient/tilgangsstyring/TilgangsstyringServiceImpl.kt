package no.nav.tiltakspenger.libs.personklient.tilgangsstyring

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.Roller
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering
import no.nav.tiltakspenger.libs.personklient.pdl.KunneIkkeGjøreTilgangskontroll
import no.nav.tiltakspenger.libs.personklient.pdl.TilgangsstyringService
import no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse.FellesAdressebeskyttelseKlient
import no.nav.tiltakspenger.libs.personklient.skjerming.FellesSkjermingsklient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Optimalisering jah: Kan legge på cache her for å unngå å kalle PDL/Skjerming flere ganger for samme person.
 */
class TilgangsstyringServiceImpl(
    private val fellesPersonTilgangsstyringsklient: FellesAdressebeskyttelseKlient,
    private val skjermingClient: FellesSkjermingsklient,
) : TilgangsstyringService {
    override suspend fun harTilgangTilPerson(
        fnr: Fnr,
        roller: Roller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Boolean> {
        return coroutineScope {
            val adressebeskyttelseDeferred = async(Dispatchers.IO) {
                fellesPersonTilgangsstyringsklient.enkel(fnr)
            }

            val erSkjermetPersonDeferred = async(Dispatchers.IO) {
                skjermingClient.erSkjermetPerson(fnr = fnr, correlationId = correlationId)
            }

            val adressebeskyttelse = adressebeskyttelseDeferred.await().getOrElse {
                return@coroutineScope KunneIkkeGjøreTilgangskontroll.Adressebeskyttelse(it).left()
            } ?: return@coroutineScope KunneIkkeGjøreTilgangskontroll.UkjentIdent.left()

            val erSkjermetPerson = erSkjermetPersonDeferred.await().getOrElse {
                return@coroutineScope KunneIkkeGjøreTilgangskontroll.Skjerming(it).left()
            }
            if (erSkjermetPerson && !roller.harSkjerming()) {
                return@coroutineScope false.right()
            }
            if (!adressebeskyttelse.harTilgangTilPerson(roller)) {
                return@coroutineScope false.right()
            }
            return@coroutineScope true.right()
        }
    }

    companion object {
        /**
         * Kun ment og kalle denne med systembrukere
         */
        fun create(
            skjermingBaseUrl: String,
            pdlPipUrl: String,
            getPdlPipToken: suspend () -> AccessToken,
            getSkjermingToken: suspend () -> AccessToken,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
        ): TilgangsstyringService {
            return TilgangsstyringServiceImpl(
                fellesPersonTilgangsstyringsklient = FellesAdressebeskyttelseKlient.create(
                    endepunkt = pdlPipUrl,
                    getToken = getPdlPipToken,
                    connectTimeout = connectTimeout,
                    timeout = timeout,
                ),
                skjermingClient = FellesSkjermingsklient.create(
                    endepunkt = skjermingBaseUrl,
                    getToken = getSkjermingToken,
                    connectTimeout = connectTimeout,
                    timeout = timeout,
                ),
            )
        }
    }
}

private fun List<AdressebeskyttelseGradering>.harTilgangTilPerson(roller: Roller): Boolean {
    return this.all {
        when (it) {
            AdressebeskyttelseGradering.FORTROLIG -> roller.harFortroligAdresse()
            AdressebeskyttelseGradering.STRENGT_FORTROLIG, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> roller.harStrengtFortroligAdresse()
            AdressebeskyttelseGradering.UGRADERT -> true
        }
    }
}
