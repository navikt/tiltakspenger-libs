package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Deltakelsene man kan søke tiltakspenger for, slik reglene sto på [påDato].
 *
 * Innsnevringen er en **type**, ikke et filter noen husker å kalle, og den bærer datoen utvalget gjaldt — uten den ville et datoavhengig utvalg vært en løgn.
 * Reglene bor i [Søkbarhet], delt mellom søknaden og manuell registrering i saksbehandling-api — unntak aktiveres der, aldri lokalt hos én konsument (tp-tilt-31 og -35 i planen).
 *
 * Guarden er i paritet med dagens søknadsflate:
 * tiltakstypen må gi rett, statusen må være kjent (ukjent kan ikke tolkes — brukbarhet, aldri utfall), og [Deltakerstatus.girRettTilÅSøke] må svare ja.
 * [Tiltaksdeltakelse.Ugyldig] når aldri guarden, siden dagens prefiltrering siler dem bort før den — også det brukbarhet, jf. F-unntaket i planen.
 */
data class TiltaksdeltakelserManKanSøkePå(
    val deltakelser: List<Tiltaksdeltakelse.GirRett>,
    val påDato: LocalDate,
) {
    init {
        val ulovlige = deltakelser.filter { it.søkbarhet(påDato) is Søkbarhet.KanIkkeSøkesPå }
        require(ulovlige.isEmpty()) { "Alle deltakelsene må passere søknadsguarden for $påDato, men ${ulovlige.size} gjør ikke" }
    }
}

/**
 * Uttrekket søknaden bygger på — se [TiltaksdeltakelserManKanSøkePå].
 * Tar med alt [Søkbarhet] ikke sier nei til, slik at et framtidig unntak følger med automatisk.
 * De bortfiltrerte blir stående i samletypen: soknad-api lagrer dem som metadata, og en framtidig politikkutvidelse trenger dem der.
 */
fun Tiltaksdeltakelser.somKildenTilsierManKanSøkePå(påDato: LocalDate): TiltaksdeltakelserManKanSøkePå =
    TiltaksdeltakelserManKanSøkePå(
        deltakelser = girRett.filter { it.søkbarhet(påDato) !is Søkbarhet.KanIkkeSøkesPå },
        påDato = påDato,
    )
