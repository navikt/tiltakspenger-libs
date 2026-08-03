package no.nav.tiltakspenger.libs.tiltaksdeltakelse.infra.http.tiltakshistorikk.felles

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.tiltakspenger.libs.tiltaksdeltakelse.Tiltakshistorikkmelding

/**
 * Klassifiserer en melding fra respons-konvolutten — kjent kode blir sin variant, ukjent bæres ordrett og varsles på.
 */
internal fun tiltakshistorikkmelding(kodeIKontrakten: String): Either<UgyldigKontraktsverdi, Tiltakshistorikkmelding> = when {
    kodeIKontrakten == Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak.kodeIKontrakten ->
        Tiltakshistorikkmelding.ManglerHistorikkFraTeamTiltak.right()

    kodeIKontrakten.isBlank() ->
        UgyldigKontraktsverdi("Blank melding fra tiltakshistorikk kan ikke bæres som ukjent kildeverdi").left()

    else -> Tiltakshistorikkmelding.Ukjent(kodeIKontrakten).right()
}
