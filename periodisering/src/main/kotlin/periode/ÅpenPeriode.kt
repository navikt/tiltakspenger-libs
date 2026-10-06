package no.nav.tiltakspenger.libs.periode

import java.time.LocalDate

/**
 * En periode der [fraOgMed] og/eller [tilOgMed] kan være ukjent (`null`).
 * `null` betyr at datoen er ukjent, ikke at perioden er uendelig — det uttrykker [Periode] med `LocalDate.MIN` og `LocalDate.MAX`.
 * Overlappsspørsmål svarer derfor med [Overlapp]: [Overlapp.Ja] eller [Overlapp.Nei] der svaret er sikkert, og [Overlapp.Kanskje] der en ukjent dato gjør at begge utfall er mulige.
 */
data class ÅpenPeriode(
    val fraOgMed: LocalDate?,
    val tilOgMed: LocalDate?,
) {
    /**
     * Den lukkede perioden, eller `null` dersom [fraOgMed] eller [tilOgMed] mangler.
     * Den bygges ved konstruksjon, slik at kravene til [Periode] også gjelder når begge datoene er kjent.
     */
    val periode: Periode? = if (fraOgMed != null && tilOgMed != null) Periode(fraOgMed, tilOgMed) else null

    /**
     * Om denne perioden overlapper med [periode].
     *
     * En kjent dato som ligger i [periode] bekrefter overlapp, siden perioden i hvert fall omfatter den dagen.
     * En kjent startdato etter [periode], eller en kjent sluttdato før, avkrefter det.
     * Ellers kan den ukjente datoen gi begge utfall, og svaret er [Overlapp.Kanskje].
     */
    fun overlapperMed(periode: Periode): Overlapp {
        this.periode?.let { return if (it.overlapperMed(periode)) Overlapp.Ja else Overlapp.Nei }

        val fom = fraOgMed
        val tom = tilOgMed
        return when {
            fom != null && periode.inneholder(fom) -> Overlapp.Ja
            tom != null && periode.inneholder(tom) -> Overlapp.Ja
            fom != null && fom.isAfter(periode.tilOgMed) -> Overlapp.Nei
            tom != null && tom.isBefore(periode.fraOgMed) -> Overlapp.Nei
            else -> Overlapp.Kanskje
        }
    }

    /**
     * Om denne perioden overlapper med [other].
     *
     * Er minst én av dem lukket, svarer vi som [overlapperMed] mot en [Periode].
     * Er begge åpne, bekrefter en felles kjent dato overlapp, og ellers er svaret [Overlapp.Kanskje].
     */
    fun overlapperMed(other: ÅpenPeriode): Overlapp {
        val denne = this.periode
        val andre = other.periode
        return when {
            denne != null -> other.overlapperMed(denne)
            andre != null -> this.overlapperMed(andre)
            delerDato(other) -> Overlapp.Ja
            else -> Overlapp.Kanskje
        }
    }

    private fun delerDato(other: ÅpenPeriode): Boolean {
        val andresDatoer = listOfNotNull(other.fraOgMed, other.tilOgMed)
        return listOfNotNull(fraOgMed, tilOgMed).any { it in andresDatoer }
    }
}
