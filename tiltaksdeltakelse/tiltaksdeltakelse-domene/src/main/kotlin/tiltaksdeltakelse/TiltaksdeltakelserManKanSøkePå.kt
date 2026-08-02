package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Deltakelsene kilden tilsier at man kan søke tiltakspenger for, slik guarden sto på [påDato].
 *
 * Innsnevringen er en **type**, ikke et filter noen husker å kalle, og den bærer datoen utvalget gjaldt — uten den ville et datoavhengig utvalg vært en løgn.
 * Uttrekket er kildens baseline: soknad-api kan legge sitt unntak *oppå* (feilregistrert `IKKE_MOTT`, se tp-tilt-31 i planen), mulig fordi kildestatusen står på hver deltakelse.
 *
 * Guarden er en ren funksjon av kildedata, i paritet med dagens søknadsflate:
 * tiltakstypen må gi rett, statusen må være kjent (ukjent kan ikke tolkes — brukbarhet, aldri utfall), og [Deltakerstatus.girRettTilÅSøke] må svare ja.
 * [Tiltaksdeltakelse.Ugyldig] når aldri guarden, siden dagens prefiltrering siler dem bort før den — også det brukbarhet, jf. F-unntaket i planen.
 */
data class TiltaksdeltakelserManKanSøkePå(
    val deltakelser: List<Tiltaksdeltakelse.GirRett>,
    val påDato: LocalDate,
) {
    init {
        val ulovlige = deltakelser.filterNot { it.somKildenTilsierKanSøkesPå(påDato) }
        require(ulovlige.isEmpty()) { "Alle deltakelsene må passere søknadsguarden for $påDato, men ${ulovlige.size} gjør ikke" }
    }
}

/**
 * Uttrekket søknaden bygger på — se [TiltaksdeltakelserManKanSøkePå].
 * De bortfiltrerte blir stående i samletypen: soknad-api lagrer dem som metadata, og en framtidig politikkutvidelse trenger dem der.
 */
fun Tiltaksdeltakelser.somKildenTilsierManKanSøkePå(påDato: LocalDate): TiltaksdeltakelserManKanSøkePå =
    TiltaksdeltakelserManKanSøkePå(
        deltakelser = girRett.filter { it.somKildenTilsierKanSøkesPå(påDato) },
        påDato = påDato,
    )

private fun Tiltaksdeltakelse.GirRett.somKildenTilsierKanSøkesPå(påDato: LocalDate): Boolean {
    val status = kildestatus
    if (status !is Kildestatus.Kjent) {
        return false
    }
    return status.deltakerstatus(fraOgMed = fraOgMed, påDato = påDato).girRettTilÅSøke
}
