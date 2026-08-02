package no.nav.tiltakspenger.libs.common

/**
 * Markerer verdier som er personopplysninger.
 *
 * Interfacet redeklarerer [toString] som abstrakt.
 * Det gjør at **kompilatoren** krever en egen implementasjon av enhver klasse som markerer seg — man kan ikke glemme maskeringen.
 * Den ene veien rundt er en `data class`, som tilfredsstiller kravet med sin genererte `toString()` og dermed lekker verdien; det hullet dekkes av konsistregelen `PersonopplysningMaskererToString`.
 *
 * Maskeringen gjelder kun `toString`.
 * Verdien er tilgjengelig gjennom typens eget felt, slik at sikkerlogg, visning og lagring henter den eksplisitt.
 * Fordi maskeringen arves av alt som inneholder feltet — en `data class` med en [Personopplysning]-property maskerer automatisk i sin genererte `toString()` — er dette sterkere enn håndskrevne overstyringer per klasse.
 *
 * Hierarkiet er `sealed` med vilje.
 * Det gjør settet av personopplysningstyper lukket og opptellbart, slik at det finnes én autoritativ liste å avstemme mot **personvernkonsekvensvurderingene (PVK)** våre.
 * Trenger en konsument en ny personopplysningstype, hører den hjemme her — og det er nettopp der en ny kategori personopplysninger bør få et blikk.
 */
sealed interface Personopplysning {
    /**
     * Hvorfor og hvordan denne verdien er en personopplysning.
     *
     * Statisk per type, ikke per instans — den beskriver kategorien, ikke verdien.
     * Kravet er abstrakt slik at en ny type ikke kan markere seg uten å si hva den utleverer om personen.
     * Teksten er grunnlaget for å avstemme typene mot PVK-ene, og skal derfor kunne leses av andre enn utviklere.
     */
    val begrunnelse: String

    override fun toString(): String
}

/**
 * Personopplysning som kan røpe hvor en person befinner seg.
 *
 * Dette er en egen kategori fordi den treffer en annen gruppe enn identifiserende opplysninger gjør.
 * For personer med adressebeskyttelse er stedet det mest sensitive vi har om dem, mens et fødselsnummer i seg selv ikke røper noe sted.
 */
sealed interface Stedsinformasjon : Personopplysning

/**
 * Navnet på en virksomhet en person er knyttet til, typisk en tiltaksarrangør eller arbeidsgiver.
 *
 * Navnet er stedsinformasjon i praksis: det peker ut hvor personen møter opp, ofte helt ned på gateadresse («Arrangør AS avd Strandveien»).
 * Verdien brukes til visning, for å kjenne igjen og skille tilknytninger fra hverandre — det skal ikke være domenelogikk på den.
 */
@JvmInline
value class Virksomhetsnavn(
    val verdi: String,
) : Stedsinformasjon {
    init {
        require(verdi.isNotBlank()) { "Virksomhetsnavn kan ikke være tomt" }
    }

    override val begrunnelse: String
        get() = "Navnet på virksomheten en person er knyttet til peker ut hvor personen befinner seg, ofte ned på gateadresse. For personer med kode 6, kode 7 eller skjerming er det den mest sensitive opplysningen vi har om dem."

    override fun toString(): String = "*****"
}

/**
 * Total inngang fra kildens fritekst: `null` og blank betyr fravær, og gir `null`.
 *
 * Kildene garanterer ikke mot tomme strenger, og en adapter skal aldri kunne kaste på kildedata.
 * Regelen «kildens tomme streng er fravær» bor derfor her, ikke på kallstedene.
 * `require`-en i [Virksomhetsnavn] består som vakt mot programmererfeil ved direkte konstruksjon.
 */
fun virksomhetsnavn(verdi: String?): Virksomhetsnavn? = if (verdi.isNullOrBlank()) null else Virksomhetsnavn(verdi)

/**
 * Leselig tittel på en tilknytning, satt sammen av kilden på formen «\<type\> hos \<virksomhet\>».
 *
 * Tittelen inneholder virksomhetsnavnet, og arver dermed stedsinformasjonen fra det.
 * Selve typen alene — «Oppfølging» — er ikke stedsinformasjon, og er derfor det som vises når mottakeren ikke skal se stedet.
 *
 * Innholdet er fritekst fra kilden og kan endre form uten varsel.
 * Den skal vises, aldri tolkes.
 */
@JvmInline
value class Tilknytningstittel(
    val verdi: String,
) : Stedsinformasjon {
    init {
        require(verdi.isNotBlank()) { "Tilknytningstittel kan ikke være tom" }
    }

    override val begrunnelse: String
        get() = "Tittelen inneholder navnet på virksomheten personen er knyttet til, og peker dermed ut hvor personen befinner seg."

    override fun toString(): String = "*****"
}

/**
 * Total inngang fra kildens fritekst: `null` og blank betyr fravær, og gir `null`.
 * Samme regel og begrunnelse som [virksomhetsnavn].
 */
fun tilknytningstittel(verdi: String?): Tilknytningstittel? = if (verdi.isNullOrBlank()) null else Tilknytningstittel(verdi)
