package no.nav.tiltakspenger.libs.personklient.tilgangsstyring

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mu.KLogger
import mu.KotlinLogging
import no.nav.tiltakspenger.libs.common.AccessToken
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.Saksbehandlerroller
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
    private val logger: KLogger? = KotlinLogging.logger {},
    private val sikkerlogg: KLogger? = no.nav.tiltakspenger.libs.logging.sikkerlogg,
) : TilgangsstyringService {

    private val harLoggetManglerISkjermingPip = mutableSetOf<Fnr>()
    private val harLoggetManglerIPdlPip = mutableSetOf<Fnr>()

    // TODO jah: Legg på cache for å unngå å kalle PDL/Skjerming flere ganger for samme person.

    override suspend fun harTilgangTilPersoner(
        fnrListe: NonEmptyList<Fnr>,
        roller: Saksbehandlerroller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Map<Fnr, Boolean>> {
        return coroutineScope {
            val adressebeskyttelseDeferred = async(Dispatchers.IO) {
                fellesPersonTilgangsstyringsklient.bolk(fnrListe)
            }
            val erSkjermetPersonDeferred = async(Dispatchers.IO) {
                skjermingClient.erSkjermetPersoner(fnrListe = fnrListe, correlationId = correlationId)
            }
            val adressebeskyttelse = adressebeskyttelseDeferred.await().getOrElse {
                return@coroutineScope KunneIkkeGjøreTilgangskontroll.Adressebeskyttelse(it).left()
            }
            val erSkjermetPerson = erSkjermetPersonDeferred.await().getOrElse {
                return@coroutineScope KunneIkkeGjøreTilgangskontroll.Skjerming(it).left()
            }
            fnrListe.map { fnr ->
                val adressebeskyttelser = adressebeskyttelse[fnr] ?: return@map (fnr to false).also {
                    if (fnr !in harLoggetManglerIPdlPip) {
                        harLoggetManglerIPdlPip.add(fnr)
                        logger?.error(RuntimeException("Trigger stacktrace for enklere debug. ")) { "Fant ikke fnr i pdl-pip. Antar at saksbehandler ikke har tilgang. Hvis dette er produksjon bør det følges opp. Se sikkerlogg for mer kontekst." }
                        sikkerlogg?.error { "Fant ikke fnr ${fnr.verdi} i pdl-pip. Antar at saksbehandler ikke har tilgang. Hvis dette er produksjon bør det følges opp." }
                    }
                }
                val erPersonSkjermet = erSkjermetPerson[fnr] ?: return@map (fnr to false).also {
                    if (fnr !in harLoggetManglerISkjermingPip) {
                        harLoggetManglerISkjermingPip.add(fnr)
                        logger?.error(RuntimeException("Trigger stacktrace for enklere debug. ")) { "Fant ikke fnr i skjerming-pip. Antar at saksbehandler ikke har tilgang. Hvis dette er produksjon bør det følges opp. Se sikkerlogg for mer kontekst." }
                        sikkerlogg?.error { "Fant ikke fnr ${fnr.verdi} i skjerming-pip. Antar at saksbehandler ikke har tilgang. Hvis dette er produksjon bør det følges opp." }
                    }
                }
                if (erPersonSkjermet && !roller.harSkjerming()) {
                    return@map (fnr to false)
                }
                (fnr to adressebeskyttelser.harTilgangTilPerson(roller))
            }.toMap().right()
        }
    }

    override suspend fun harTilgangTilPerson(
        fnr: Fnr,
        roller: Saksbehandlerroller,
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

    override suspend fun adressebeskyttelseEnkel(
        fnr: Fnr,
    ): Either<KunneIkkeGjøreTilgangskontroll, List<AdressebeskyttelseGradering>?> {
        val adressebeskyttelse = fellesPersonTilgangsstyringsklient.enkel(fnr)
            .getOrElse { return KunneIkkeGjøreTilgangskontroll.Adressebeskyttelse(it).left() }
            ?: return KunneIkkeGjøreTilgangskontroll.UkjentIdent.left()
        return adressebeskyttelse.right()
    }

    companion object {
        /**
         * Kun ment og kalle denne med systembrukere
         */
        fun create(
            skjermingBaseUrl: String,
            pdlPipBaseUrl: String,
            getPdlPipToken: suspend () -> AccessToken,
            getSkjermingToken: suspend () -> AccessToken,
            connectTimeout: Duration = 1.seconds,
            timeout: Duration = 1.seconds,
            sikkerlogg: KLogger?,
            fellesPersonTilgangsstyringsklient: FellesAdressebeskyttelseKlient = FellesAdressebeskyttelseKlient.create(
                baseUrl = pdlPipBaseUrl,
                getToken = getPdlPipToken,
                connectTimeout = connectTimeout,
                timeout = timeout,
                sikkerlogg = sikkerlogg,
            ),
            skjermingClient: FellesSkjermingsklient = FellesSkjermingsklient.create(
                endepunkt = skjermingBaseUrl,
                getToken = getSkjermingToken,
                connectTimeout = connectTimeout,
                timeout = timeout,
                sikkerlogg = sikkerlogg,
            ),

        ): TilgangsstyringService {
            return TilgangsstyringServiceImpl(
                fellesPersonTilgangsstyringsklient = fellesPersonTilgangsstyringsklient,
                skjermingClient = skjermingClient,
            )
        }
    }
}

private fun List<AdressebeskyttelseGradering>.harTilgangTilPerson(roller: Saksbehandlerroller): Boolean {
    return this.all {
        when (it) {
            AdressebeskyttelseGradering.FORTROLIG -> roller.harFortroligAdresse()
            AdressebeskyttelseGradering.STRENGT_FORTROLIG, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND -> roller.harStrengtFortroligAdresse()
            AdressebeskyttelseGradering.UGRADERT -> true
        }
    }
}
