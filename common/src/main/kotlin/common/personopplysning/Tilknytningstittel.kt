package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Leselig tittel på en tilknytning, satt sammen av kilden på formen «\<type\> hos \<virksomhet\>».
 * Kan røpe virksomhetsnavnet og dermed hvor personen befinner seg.
 * Kilden eier friteksten, som kan endre form uten varsel og bare skal vises.
 * Krever ikke-blank verdi.
 */
@JvmInline
value class Tilknytningstittel(
    val verdi: String,
) : Stedsinformasjon {
    init {
        require(verdi.isNotBlank()) { "Tilknytningstittel kan ikke være tom" }
    }

    override val begrunnelse: String
        get() = "Tittelen inneholder navnet på virksomheten personen er knyttet til. Den kan dermed peke ut hvor personen befinner seg."

    override fun toString(): String = "*****"
}

/**
 * Returnerer `null` for `null` eller blank verdi der kildekontrakten definerer det som fravær.
 * Er verdien påkrevd i kilden, skal adapteren avvise manglende verdi som innlesingsfeil.
 */
fun tilknytningstittel(verdi: String?): Tilknytningstittel? = if (verdi.isNullOrBlank()) null else Tilknytningstittel(verdi)
