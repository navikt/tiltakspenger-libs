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
import no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse.FellesAdressebeskyttelseError
import no.nav.tiltakspenger.libs.personklient.pdl.adressebeskyttelse.FellesAdressebeskyttelseKlient
import no.nav.tiltakspenger.libs.personklient.skjerming.FellesSkjermingError
import no.nav.tiltakspenger.libs.personklient.skjerming.FellesSkjermingsklient

/**
 * Optimalisering jah: Kan legge på cache her for å unngå å kalle PDL/Skjerming flere ganger for samme person.
 */
class TilgangsstyringService(
    private val fellesPersonTilgangsstyringsklient: FellesAdressebeskyttelseKlient,
    private val skjermingClient: FellesSkjermingsklient,
) {
    suspend fun harTilgangTilPerson(
        fnr: Fnr,
        token: AccessToken,
        roller: Roller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Boolean> {
        return coroutineScope {
            val adressebeskyttelseDeferred = async(Dispatchers.IO) {
                fellesPersonTilgangsstyringsklient.enkel(fnr, token)
            }

            val erSkjermetPersonDeferred = async(Dispatchers.IO) {
                skjermingClient.erSkjermetPerson(fnr = fnr, token = token, correlationId = correlationId)
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
}

sealed interface KunneIkkeGjøreTilgangskontroll {
    data object UkjentIdent : KunneIkkeGjøreTilgangskontroll
    data class Adressebeskyttelse(val underliggende: FellesAdressebeskyttelseError) : KunneIkkeGjøreTilgangskontroll
    data class Skjerming(val underliggende: FellesSkjermingError) : KunneIkkeGjøreTilgangskontroll
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
