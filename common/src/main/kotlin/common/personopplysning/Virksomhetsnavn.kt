package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Navn på en virksomhet en person er knyttet til, typisk en tiltaksarrangør eller arbeidsgiver.
 * Kan peke ut hvor personen møter opp, noen ganger ned på gateadresse.
 * Kilden eier ordlyden, og verdien brukes til visning fremfor domenelogikk.
 * Krever ikke-blank verdi.
 */
@JvmInline
value class Virksomhetsnavn(
    val verdi: String,
) : Stedsinformasjon {
    init {
        require(verdi.isNotBlank()) { "Virksomhetsnavn kan ikke være tomt" }
    }

    override val begrunnelse: String
        get() = "Navnet på virksomheten en person er knyttet til kan peke ut hvor personen befinner seg, noen ganger ned på gateadresse. For personer med kode 6, kode 7 eller skjerming kan det være den mest sensitive opplysningen vi har om dem."

    override fun toString(): String = "*****"
}

/**
 * Returnerer `null` for `null` eller blank verdi der kildekontrakten definerer det som fravær.
 * Er verdien påkrevd i kilden, skal adapteren avvise manglende verdi som innlesingsfeil.
 */
fun virksomhetsnavn(verdi: String?): Virksomhetsnavn? = if (verdi.isNullOrBlank()) null else Virksomhetsnavn(verdi)
