package no.nav.tiltakspenger.libs.tiltaksdeltakelse

import java.time.LocalDate

/**
 * Om en deltakelse kan søkes på, med begrunnelse til visning.
 *
 * Reglene bor her og bare her, delt mellom konsumentene: søknaden bruker dem til å velge ut deltakelser, og saksbehandling-api viser dem — for eksempel ved manuell registrering, der saksbehandler skal se «denne statusen gir ikke bruker rett til å søke fordi …».
 * Ett regelsett gir lik oppførsel og like tekster begge steder; unntak aktiveres her, aldri lokalt hos én konsument.
 *
 * Begrunnelsene er statiske per regel og skal kunne leses av andre enn utviklere — samme mønster som `Personopplysning.begrunnelse` og [UkjentKildeverdi.hva].
 *
 * [KanSøkesPåVedUnntak] produseres av feilregistrert-unntaket for Arena `IKKE_MOTT`.
 * Det er stedet de to aksene skiller lag med vilje: fag avklarte at «ikke møtt» ikke er deltakelse og derfor ikke gir rett til innvilgelse, men ingen har sagt at bruker skal miste retten til å *søke*.
 * Å nekte søknaden ville flyttet en høy terskel — å få Arena til å rette en feilregistrering — over på bruker, før noen i det hele tatt har sett på saken.
 */
sealed interface Søkbarhet {
    /** Kilden tilsier at bruker kan søke. */
    data object KanSøkesPå : Søkbarhet

    /**
     * Politikk-unntak: tolkningen av kilden sier nei, men bruker får søke likevel.
     * Begrunnelsen sier hvorfor unntaket finnes, og vises til saksbehandler.
     */
    data class KanSøkesPåVedUnntak(
        val begrunnelse: String,
    ) : Søkbarhet {
        init {
            require(begrunnelse.isNotBlank()) { "En søkbarhetsregel må begrunne seg" }
        }
    }

    /** Bruker kan ikke søke på deltakelsen — begrunnelsen er visningsteksten. */
    data class KanIkkeSøkesPå(
        val begrunnelse: String,
    ) : Søkbarhet {
        init {
            require(begrunnelse.isNotBlank()) { "En søkbarhetsregel må begrunne seg" }
        }
    }
}

/**
 * Regelsettet: hva som gjør en deltakelse søkbar, og hvorfor ikke.
 *
 * Rekkefølgen speiler fabrikken: datakvalitet slår ut først, deretter tiltakstypeaksen, til slutt statusaksen.
 * Har en deltakelse flere grunner til nei, svarer vi med den første stoppende — [ukjenteKildeverdier] viser resten.
 * Svaret er en spørring på kildedata og dato, aldri en lagret sannhet — samme regel som for `deltakerstatus`.
 */
fun Tiltaksdeltakelse.søkbarhet(påDato: LocalDate): Søkbarhet = when (this) {
    is Tiltaksdeltakelse.Ugyldig -> Søkbarhet.KanIkkeSøkesPå(BEGRUNNELSE_UGYLDIGE_DATOER)

    is Tiltaksdeltakelse.UkjentTiltakstype -> Søkbarhet.KanIkkeSøkesPå(BEGRUNNELSE_UKJENT_TILTAKSKODE)

    is Tiltaksdeltakelse.GirIkkeRett -> Søkbarhet.KanIkkeSøkesPå(BEGRUNNELSE_TILTAKSTYPE_UTEN_RETT)

    is Tiltaksdeltakelse.GirRett.MedPeriode,
    is Tiltaksdeltakelse.GirRett.UtenPeriode,
    -> søkbarhetForStatusen(påDato)
}

private fun Tiltaksdeltakelse.søkbarhetForStatusen(påDato: LocalDate): Søkbarhet {
    val status = kildestatus
    if (status !is Kildestatus.Kjent) {
        return Søkbarhet.KanIkkeSøkesPå(BEGRUNNELSE_UKJENT_KILDESTATUS)
    }
    if (status.deltakerstatus(fraOgMed = fraOgMed, påDato = påDato).girRettTilÅSøke) {
        return Søkbarhet.KanSøkesPå
    }
    if (status is Arenastatus.Kjent && status.type == Arenastatus.Type.IKKE_MOTT) {
        return Søkbarhet.KanSøkesPåVedUnntak(BEGRUNNELSE_ARENA_IKKE_MOTT)
    }
    return Søkbarhet.KanIkkeSøkesPå(BEGRUNNELSE_STATUS_UTEN_RETT)
}

private const val BEGRUNNELSE_UGYLDIGE_DATOER =
    "Datoene fra kilden henger ikke sammen, så deltakelsen kan ikke brukes i en søknad før kilden har rettet dem."

private const val BEGRUNNELSE_UKJENT_TILTAKSKODE =
    "Tiltakskoden fra kilden er ikke i tabellene våre ennå, og deltakelsen må vurderes manuelt før bruker kan søke."

private const val BEGRUNNELSE_TILTAKSTYPE_UTEN_RETT =
    "Tiltakstypen gir ikke rett til tiltakspenger."

private const val BEGRUNNELSE_UKJENT_KILDESTATUS =
    "Statusen fra kilden er en kode vi ikke kjenner igjen ennå, og den må mappes før bruker kan søke."

private const val BEGRUNNELSE_STATUS_UTEN_RETT =
    "Statusen hos kilden tilsier at bruker hverken deltar eller har fått tildelt plass, og da gir deltakelsen ikke rett til å søke."

private const val BEGRUNNELSE_ARENA_IKKE_MOTT =
    "Arena har registrert at bruker ikke møtte. Det kan være feilregistrert, og terskelen for å få det rettet i Arena er høy, så bruker får søke og saksbehandler vurderer."
