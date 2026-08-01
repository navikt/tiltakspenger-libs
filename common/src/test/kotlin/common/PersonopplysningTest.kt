package no.nav.tiltakspenger.libs.common

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

internal class PersonopplysningTest {
    @Test
    fun `virksomhetsnavn maskerer seg selv`() {
        val navn = Virksomhetsnavn("Arrangør AS avd Strandveien")

        navn.toString() shouldBe "*****"
        navn.toString() shouldNotContain "Strandveien"
        navn.verdi shouldBe "Arrangør AS avd Strandveien"
    }

    /**
     * Maskeringen arves av alt som inneholder feltet.
     * Det er derfor typen er markeringen: en klasse som bærer stedsinformasjon trenger ikke huske å overstyre noe.
     */
    @Test
    fun `maskeringen arves av data-klasser som inneholder verdien`() {
        data class Tilknytning(val virksomhet: Virksomhetsnavn?)

        Tilknytning(Virksomhetsnavn("Arrangør AS avd Strandveien")).toString() shouldBe "Tilknytning(virksomhet=*****)"
        Tilknytning(null).toString() shouldBe "Tilknytning(virksomhet=null)"
    }

    @Test
    fun `tomt virksomhetsnavn avvises - fravær uttrykkes med null`() {
        shouldThrowWithMessage<IllegalArgumentException>("Virksomhetsnavn kan ikke være tomt") {
            Virksomhetsnavn("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("Virksomhetsnavn kan ikke være tomt") {
            Virksomhetsnavn("   ")
        }
    }

    /**
     * Begrunnelsen er grunnlaget for å avstemme typene mot PVK-ene, og må stå på hver type.
     * Fødselsnummer og stedsinformasjon utleverer forskjellige ting om en person, og skal derfor begrunnes hver for seg.
     */
    @Test
    fun `hver personopplysning begrunner hva den utleverer`() {
        val personopplysninger: List<Personopplysning> = listOf(
            Virksomhetsnavn("Arrangør AS"),
            Fnr.fromString("12345678901"),
        )

        personopplysninger.forEach { opplysning ->
            opplysning.begrunnelse.isNotBlank() shouldBe true
        }
    }

    /**
     * At `Fnr` er personopplysning, men ikke stedsinformasjon, står det ingen test på med vilje.
     * Kompilatoren avviser `fnr is Stedsinformasjon` som «always false» — den beviser skillet, og en runtime-test ville vært svakere enn garantien vi allerede har.
     * En funksjon som tar `Stedsinformasjon` kan derfor ikke få et fødselsnummer, og det er hele poenget med kategorien.
     */
    @Test
    fun `stedsinformasjon kan brukes der kategorien kreves`() {
        fun stedsinformasjonen(sted: Stedsinformasjon): String = sted.begrunnelse

        stedsinformasjonen(Virksomhetsnavn("Arrangør AS")).isNotBlank() shouldBe true
    }
}
