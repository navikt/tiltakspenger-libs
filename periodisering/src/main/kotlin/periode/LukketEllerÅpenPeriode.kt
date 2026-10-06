package no.nav.tiltakspenger.libs.periode

import java.time.LocalDate

/**
 * Felles type for en [Periode], der begge datoene er kjent, og en [ÅpenPeriode], der hver av dem kan mangle.
 * `null` betyr at datoen er ukjent, ikke at perioden er uendelig.
 * Interfacet er sealed, slik at `when` over det er uttømmende.
 */
sealed interface LukketEllerÅpenPeriode {
    val fraOgMed: LocalDate?
    val tilOgMed: LocalDate?
}
