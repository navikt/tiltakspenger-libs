package no.nav.tiltakspenger.libs.json

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * SerDes-kontrakt for typer hvor utviklere ofte snubler:
 *   - Sealed class-hierarki med `@JsonTypeInfo`/`@JsonSubTypes` — Jackson sin standardmåte å rundtrippe polymorfe typer
 *   - Kotlin `object` (singleton) — serialiseres som tomt JSON-objekt med mindre det har properties
 *   - Generisk data class (`Wrapper<T>`) — fungerer top-level via `reified T`, men nested kreves konkret instantiering
 *   - Enum round-trip via `name()` + bruk som Map-nøkkel
 *
 * Hvis du legger til en ny sealed-subtype: husk å registrere den i `@JsonSubTypes` — ellers feiler deserialisering fail-loud (det er bra).
 */
internal class JacksonPolymorphismTest {

    // --- Sealed class med JsonTypeInfo ------------------------------------------------------

    @Test
    fun `sealed class round-tripper med type-diskriminator`() {
        val sirkel: Form = Form.Sirkel(radius = 5.0)
        roundTrip(sirkel, """{"type":"sirkel","radius":5.0}""")

        val rektangel: Form = Form.Rektangel(bredde = 3.0, høyde = 4.0)
        roundTrip(rektangel, """{"type":"rektangel","bredde":3.0,"høyde":4.0}""")
    }

    @Test
    fun `sealed object subtype round-tripper`() {
        // Object-subtypen har ingen properties — kun type-diskriminatoren skrives.
        val ingen: Form = Form.Ingen
        roundTrip(ingen, """{"type":"ingen"}""")
    }

    @Test
    fun `List av sealed class bevarer subtype per element`() {
        val former: List<Form> = listOf(
            Form.Sirkel(1.0),
            Form.Rektangel(2.0, 3.0),
            Form.Ingen,
        )
        roundTripList(
            former,
            """[{"type":"sirkel","radius":1.0},{"type":"rektangel","bredde":2.0,"høyde":3.0},{"type":"ingen"}]""",
        )
    }

    @Test
    fun `sealed class som felt i DTO`() {
        roundTrip(
            MedForm(navn = "x", form = Form.Sirkel(2.0)),
            """{"navn":"x","form":{"type":"sirkel","radius":2.0}}""",
        )
    }

    // --- object singleton --------------------------------------------------------------------

    @Test
    fun `Kotlin object uten properties round-tripper som tomt JSON-objekt`() {
        // En typisk "marker object" — Jackson skriver {} og leser tilbake samme singleton-instans.
        serialize(Markør) shouldBe "{}"
        deserialize<Markør>("{}") shouldBe Markør
    }

    @Test
    fun `Kotlin object i List`() {
        // Identitet bevares — singleton'en er fortsatt samme instans etter round-trip.
        roundTripList(listOf(Markør, Markør), """[{},{}]""")
    }

    // --- Generisk data class -----------------------------------------------------------------

    @Test
    fun `generisk data class top-level med reified T`() {
        // top-level fungerer fordi `inline fun <reified T>` bevarer type-argumentet til Jackson.
        roundTrip<Wrapper<Int>>(Wrapper(verdi = 42), """{"verdi":42}""")
        roundTrip<Wrapper<String>>(Wrapper(verdi = "hei"), """{"verdi":"hei"}""")
    }

    @Test
    fun `generisk data class nested i konkret DTO`() {
        // Nested fungerer fordi DTO-en spesifiserer type-argumentet — Jackson ser konkret Wrapper<Int>.
        roundTrip(
            MedWrapper(navn = "x", pakke = Wrapper(verdi = 42)),
            """{"navn":"x","pakke":{"verdi":42}}""",
        )
    }

    // --- Enum-varianter ---------------------------------------------------------------------

    @Test
    fun `enum med flere konstanter round-tripper`() {
        // Alle konstantene må kunne navngis — fanger triviell typo i name()-mapping eller endring i enum-serialisering.
        FargeEnum.entries.forEach { farge ->
            val json = """"${farge.name}""""
            serialize(farge) shouldBe json
            deserialize<FargeEnum>(json) shouldBe farge
        }
    }

    @Test
    fun `enum som Map-nøkkel`() {
        roundTripMap<FargeEnum, Int>(
            mapOf(FargeEnum.RØD to 1, FargeEnum.GRØNN to 2),
            """{"RØD":1,"GRØNN":2}""",
        )
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Form.Sirkel::class, name = "sirkel"),
    JsonSubTypes.Type(value = Form.Rektangel::class, name = "rektangel"),
    JsonSubTypes.Type(value = Form.Ingen::class, name = "ingen"),
)
private sealed interface Form {
    data class Sirkel(val radius: Double) : Form
    data class Rektangel(val bredde: Double, val høyde: Double) : Form
    data object Ingen : Form
}

private data class MedForm(val navn: String, val form: Form)

internal data object Markør

private data class Wrapper<T>(val verdi: T)
private data class MedWrapper(val navn: String, val pakke: Wrapper<Int>)

@Suppress("unused")
private enum class FargeEnum { RØD, GRØNN, BLÅ }
