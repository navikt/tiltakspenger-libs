package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Identifikator tildelt i Navs samhandlerregister.
 * En samhandler er en aktør med gjensidig relasjon til Nav, se BEGREP-814 i Navs begrepskatalog: https://navno.sharepoint.com/sites/begreper/SitePages/Begrep.aspx?bid=BEGREP-814.
 * Koblet til en person kan identen røpe en relasjon til behandler, verge eller institusjon.
 * Krever ikke-blank verdi; formen valideres ikke her.
 */
@JvmInline
value class Samhandlerident(
    val verdi: String,
) : Personopplysning {
    init {
        require(verdi.isNotBlank()) { "Samhandlerident kan ikke være tom" }
    }

    override val begrunnelse: String
        get() = "Koblet til en person kan identen røpe en relasjon til en samhandler, som behandler, verge eller institusjon. Hva relasjonen avslører om helse, vergemål eller sted, avhenger av samhandleren og sammenhengen."

    override fun toString(): String = "*****"
}

/**
 * Returnerer `null` for `null` eller blank verdi der kildekontrakten definerer det som fravær.
 * Er verdien påkrevd i kilden, skal adapteren avvise manglende verdi som innlesingsfeil.
 */
fun samhandlerident(verdi: String?): Samhandlerident? = if (verdi.isNullOrBlank()) null else Samhandlerident(verdi)
