package no.nav.tiltakspenger.libs.personklient.pdl

import arrow.core.Either
import arrow.core.NonEmptyList
import no.nav.tiltakspenger.libs.common.CorrelationId
import no.nav.tiltakspenger.libs.common.Fnr
import no.nav.tiltakspenger.libs.common.Saksbehandlerroller
import no.nav.tiltakspenger.libs.person.AdressebeskyttelseGradering

interface TilgangsstyringService {
    /**
     * Sjekker kode 6, 7 og egen ansatt (skjermet).
     */
    suspend fun harTilgangTilPerson(
        fnr: Fnr,
        roller: Saksbehandlerroller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Boolean>

    /**
     * Sjekker kode 6, 7 og egen ansatt (skjermet).
     */
    suspend fun harTilgangTilPersoner(
        fnrListe: NonEmptyList<Fnr>,
        roller: Saksbehandlerroller,
        correlationId: CorrelationId,
    ): Either<KunneIkkeGjøreTilgangskontroll, Map<Fnr, Boolean>>

    /** Sjekker kun kode 6 og 7. */
    suspend fun adressebeskyttelseEnkel(
        fnr: Fnr,
    ): Either<KunneIkkeGjøreTilgangskontroll, List<AdressebeskyttelseGradering>?>
}
