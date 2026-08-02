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
     * Kildene garanterer ikke mot tomme strenger, og en adapter skal aldri kunne kaste på kildedata.
     * Inngangen er total: fravær og blank gir null, verdi gir typen.
     */
    @Test
    fun `inngangen fra fritekst gjør blank til fravær`() {
        virksomhetsnavn(null) shouldBe null
        virksomhetsnavn("") shouldBe null
        virksomhetsnavn("   ") shouldBe null
        virksomhetsnavn("Arrangør AS") shouldBe Virksomhetsnavn("Arrangør AS")

        tilknytningstittel(null) shouldBe null
        tilknytningstittel("") shouldBe null
        tilknytningstittel("   ") shouldBe null
        tilknytningstittel("Oppfølging hos Arrangør AS") shouldBe Tilknytningstittel("Oppfølging hos Arrangør AS")
    }

    @Test
    fun `tilknytningstittel maskerer seg selv`() {
        val tittel = Tilknytningstittel("Oppfølging hos Arrangør AS avd Strandveien")

        tittel.toString() shouldBe "*****"
        tittel.toString() shouldNotContain "Strandveien"
        tittel.verdi shouldBe "Oppfølging hos Arrangør AS avd Strandveien"
    }

    @Test
    fun `tom tilknytningstittel avvises - fravær uttrykkes med null`() {
        shouldThrowWithMessage<IllegalArgumentException>("Tilknytningstittel kan ikke være tom") {
            Tilknytningstittel("")
        }
        shouldThrowWithMessage<IllegalArgumentException>("Tilknytningstittel kan ikke være tom") {
            Tilknytningstittel("   ")
        }
    }

    /**
     * Begrunnelsen er grunnlaget for å avstemme typene mot PVK-ene, og må stå på hver type.
     * `when`-en er uttømmende uten `else` med vilje: legges en ny personopplysningstype til, kompilerer ikke testen før typen er med her.
     * Det gjør PVK-lista til noe kompilatoren håndhever, ikke noe noen må huske.
     */
    @Test
    fun `hver personopplysningstype begrunner hva den utleverer`() {
        fun begrunnelsen(opplysning: Personopplysning): String = when (opplysning) {
            is Fnr -> opplysning.begrunnelse
            is Virksomhetsnavn -> opplysning.begrunnelse
            is Tilknytningstittel -> opplysning.begrunnelse
        }

        begrunnelsen(Fnr.fromString("12345678901")).isNotBlank() shouldBe true
        begrunnelsen(Virksomhetsnavn("Arrangør AS")).isNotBlank() shouldBe true
        begrunnelsen(Tilknytningstittel("Oppfølging hos Arrangør AS")).isNotBlank() shouldBe true
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
