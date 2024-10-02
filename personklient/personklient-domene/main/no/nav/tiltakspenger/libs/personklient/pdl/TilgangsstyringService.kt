package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.Roller
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering

interface TilgangsstyringService {
    suspend fun harTilgangTilPerson(
        fnr: Fnr,
        roller: Roller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Boolean>

    suspend fun adressebeskyttelseEnkel(fnr: Fnr): Either<KunneIkkeGjøreTilgangskontroll, List<AdressebeskyttelseGradering>?>
}
