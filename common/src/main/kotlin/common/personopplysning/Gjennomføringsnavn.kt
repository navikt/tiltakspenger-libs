package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Navnet på en tiltaksgjennomføring, slik kilden har registrert det, for eksempel «AMO-kurs i sveis, Strandveien».
 * Fritekst satt av den som registrerte gjennomføringen, uten fast form og uten garanti for hva den inneholder.
 * For eldre gjennomføringer fra Arena kan den inneholde persondata, for eksempel navnet på deltakeren.
 * Kilden eier friteksten, og verdien skal bare vises.
 * Krever ikke-blank verdi.
 */
@JvmInline
value class Gjennomføringsnavn(
    val verdi: String,
) : Personopplysning {
    init {
        require(verdi.isNotBlank()) { "Gjennomføringsnavn kan ikke være tomt" }
    }

    override val begrunnelse: String
        get() = "Navnet er fritekst og kan peke ut hvor personen deltar på tiltak. For eldre gjennomføringer fra Arena kan det også inneholde persondata, som navnet på deltakeren."

    override fun toString(): String = "*****"
}

/**
 * Returnerer `null` for `null` eller blank verdi der kildekontrakten definerer det som fravær.
 * Er verdien påkrevd i kilden, skal adapteren avvise manglende verdi som innlesingsfeil.
 */
fun gjennomføringsnavn(verdi: String?): Gjennomføringsnavn? = if (verdi.isNullOrBlank()) null else Gjennomføringsnavn(verdi)
