package no.nav.tiltakspenger.libs.common.personopplysning

/**
 * Organisasjonsnummer for en virksomhet en person er knyttet til, se https://info.altinn.no/starte-og-drive/starte/registrering/organisasjonsnummer/ og https://lovdata.no/dokument/LTI/lov/1994-06-03-15.
 * Kan identifisere innehaveren av et enkeltpersonforetak, og koblet til en person kan det røpe tilknytning til virksomheten og mulig oppholdssted.
 * Begrepet eies av Brønnøysundregistrene gjennom Enhetsregisteret: https://www.brreg.no/.
 * Krever ikke-blank verdi; form og kontrollsiffer valideres ikke her og må håndteres ved innlesing når kontrakten krever det.
 */
@JvmInline
value class Organisasjonsnummer(
    val verdi: String,
) : Personopplysning {
    init {
        require(verdi.isNotBlank()) { "Organisasjonsnummer kan ikke være tomt" }
    }

    override val begrunnelse: String
        get() = "Nummeret kan identifisere innehaveren av et enkeltpersonforetak. Koblet til en person kan det røpe tilknytning til en virksomhet og mulig oppholdssted."

    override fun toString(): String = "*****"
}

/**
 * Returnerer `null` for `null` eller blank verdi der kildekontrakten definerer det som fravær.
 * Er verdien påkrevd i kilden, skal adapteren avvise manglende verdi som innlesingsfeil.
 */
fun organisasjonsnummer(verdi: String?): Organisasjonsnummer? = if (verdi.isNullOrBlank()) null else Organisasjonsnummer(verdi)
