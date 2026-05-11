package no.nav.tiltakspenger.libs.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.core.exc.InputCoercionException
import tools.jackson.databind.DatabindException
import tools.jackson.databind.exc.InvalidFormatException

/**
 * SerDes-kontrakt for Kotlin-språkfeatures via Jackson Kotlin-modulen:
 *   - primitiver (Int, Long, Double, Boolean, String)
 *   - data class-felter, nullable-felter, manglende/ukjente felt
 *   - kolleksjoner (List, Set, Map) inkl. nullable elementer
 *   - enum-fail-on-numbers (vår eksplisitte konfig)
 *   - særnorske tegn og backtick-property-navn (KotlinPropertyNameAsImplicitName)
 *
 * `kotlin.time.Duration` ligger i [JacksonKotlinTimeTest].
 */
internal class JacksonKotlinTest {

    // --- Primitiver -------------------------------------------------------------------------

    @Test
    fun `primitiver round-trip`() {
        roundTrip(42, "42")
        roundTrip(Long.MAX_VALUE, "9223372036854775807")
        roundTrip(1.5, "1.5")
        roundTrip(true, "true")
        roundTrip("noe tekst", """"noe tekst"""")
    }

    // --- Data class og nullable -------------------------------------------------------------

    @Test
    fun `data class med nullable felt`() {
        roundTrip(MedNullable("med-verdi", 5), """{"navn":"med-verdi","antall":5}""")
        roundTrip(MedNullable("uten-verdi", null), """{"navn":"uten-verdi","antall":null}""")

        // Manglende felt aksepteres for nullable.
        deserialize<MedNullable>("""{"navn":"x"}""") shouldBe MedNullable("x", null)
    }

    @Test
    fun `ukjente felt droppes stille (FAIL_ON_UNKNOWN_PROPERTIES er av)`() {
        // Bevisst forward-compat-valg: nye felt fra avsendere bryter ikke deserialisering.
        // Bakdel: typo i feltnavn oppdages ikke.
        // Denne testen dokumenterer adferden eksplisitt.
        deserialize<MedNullable>(
            """{"navn":"x","antall":1,"ukjentFelt":"ignoreres"}""",
        ) shouldBe MedNullable(navn = "x", antall = 1)
    }

    @Test
    fun `manglende påkrevd felt kaster`() {
        shouldThrow<DatabindException> {
            deserialize<MedNullable>("""{"antall":1}""")
        }
    }

    @Test
    fun `null for non-nullable Kotlin-felt kaster`() {
        shouldThrow<DatabindException> {
            deserialize<MedNullable>("""{"navn":null,"antall":1}""")
        }
    }

    // --- Property-navn (KotlinPropertyNameAsImplicitName) -----------------------------------

    @Test
    fun `særnorske tegn i property-navn bevares`() {
        roundTrip(
            MedSærnorsk(id = "12345", kanIverksette = false, årsak = "test"),
            """{"id":"12345","kanIverksette":false,"årsak":"test"}""",
        )
    }

    @Test
    fun `backtick-property-navn bevares (krever KotlinPropertyNameAsImplicitName)`() {
        // `klasse` er et reservert Kotlin-ord — backticks er Kotlin-måten å bruke det som navn på.
        // Uten KotlinPropertyNameAsImplicitName ville Jackson sett konstruktor-parameternavnet, ikke property-navnet, og kunne gitt "arg0" eller lignende.
        roundTrip(
            MedBacktick(klasse = "A", type = "B"),
            """{"klasse":"A","type":"B"}""",
        )
    }

    // --- Enum -------------------------------------------------------------------------------

    @Test
    fun `enum round-trip som navn-streng`() {
        roundTrip(TestEnum.A, """"A"""")
    }

    @Test
    fun `enum med tall som JSON-verdi feiler eksplisitt`() {
        // FAIL_ON_NUMBERS_FOR_ENUMS skal være på — tall mappes ikke implisitt til ordinal.
        shouldThrow<InvalidFormatException> {
            deserialize<TestEnum>("0")
        }
    }

    // --- Numerisk overflow og presisjonstap ------------------------------------------------
    //
    // Jackson håndhever range-grensene for `Int` og `Long` ved deserialisering — verdier utenfor
    // intervallet kaster [InputCoercionException] (fail-loud, ikke silent wrap-around).
    // MEN: desimaler trunkeres stille til heltall (1.5 → 1). Det er forward-compat-vennlig
    // (gammel klient kan sende "5.0"), men maskerer reelle bugs hvis avsender mente å sende et heltall.

    @Test
    fun `Int — verdi over Int MAX_VALUE kaster`() {
        // 2147483648 = Int.MAX_VALUE + 1 — minste verdi som ikke får plass i Int.
        shouldThrow<InputCoercionException> { deserialize<Int>("2147483648") }
        // Long.MAX_VALUE — godt over Int-range.
        shouldThrow<InputCoercionException> { deserialize<Int>("9223372036854775807") }
        // Større enn Long.MAX_VALUE også — Jackson bruker BigInteger internt for å sjekke range.
        shouldThrow<InputCoercionException> { deserialize<Int>("12345678901234567890") }
    }

    @Test
    fun `Int — verdi under Int MIN_VALUE kaster`() {
        // -2147483649 = Int.MIN_VALUE - 1.
        shouldThrow<InputCoercionException> { deserialize<Int>("-2147483649") }
    }

    @Test
    fun `Long — verdi over Long MAX_VALUE kaster`() {
        // 9223372036854775808 = Long.MAX_VALUE + 1.
        shouldThrow<InputCoercionException> { deserialize<Long>("9223372036854775808") }
        shouldThrow<InputCoercionException> { deserialize<Long>("12345678901234567890") }
    }

    @Test
    fun `Long MAX_VALUE round-tripper`() {
        // Sanity: nettopp på grensen — skal gå.
        roundTrip(Long.MAX_VALUE, "9223372036854775807")
        roundTrip(Long.MIN_VALUE, "-9223372036854775808")
    }

    @Test
    fun `overflow i DTO-felt kaster også`() {
        // Range-sjekken trigger uavhengig av om typen er top-level eller nested i en DTO.
        shouldThrow<InputCoercionException> {
            deserialize<MedInt>("""{"n":9223372036854775807}""")
        }
        shouldThrow<InputCoercionException> {
            deserialize<MedLong>("""{"n":12345678901234567890}""")
        }
    }

    @Test
    fun `overflow i List-element kaster også`() {
        shouldThrow<InputCoercionException> {
            deserializeList<Int>("[1,9223372036854775807,3]")
        }
    }

    @Test
    fun `desimaler trunkeres stille til Int og Long — kjent silent-loss`() {
        // ACCEPT_FLOAT_AS_INT er på som default i Jackson, og desimal-delen kastes bort uten advarsel.
        // Dette er bevisst forward-compat-adferd, men kan maskere bugs hvis avsender mente noe annet.
        // Hvis du trenger fail-loud, bruk BigDecimal som felttype og valider eksplisitt.
        deserialize<Int>("1.5") shouldBe 1
        deserialize<Int>("1.9") shouldBe 1
        deserialize<Int>("1.0") shouldBe 1
        deserialize<Long>("1.5") shouldBe 1L
    }

    // --- Kolleksjoner -----------------------------------------------------------------------

    @Test
    fun `tomme kolleksjoner`() {
        roundTrip(emptyList<Int>(), "[]")
        roundTrip(emptySet<Int>(), "[]")
        roundTrip(emptyMap<String, Int>(), "{}")
    }

    @Test
    fun `nullable elementer i List og Map`() {
        roundTrip<List<Int?>>(listOf(1, null, 3), """[1,null,3]""")
        roundTrip<Map<String, Int?>>(mapOf("a" to 1, "b" to null), """{"a":1,"b":null}""")
    }

    @Test
    fun `Set round-trip`() {
        roundTrip(setOf(1, 2, 3), """[1,2,3]""")
    }

    @Test
    fun `generiske kolleksjoner bevarer element-type`() {
        roundTrip(
            listOf(MedNullable(navn = "a", antall = 1)),
            """[{"navn":"a","antall":1}]""",
        )
        roundTrip(
            mapOf("x" to MedNullable(navn = "a", antall = 1)),
            """{"x":{"navn":"a","antall":1}}""",
        )
    }
}

private data class MedNullable(
    val navn: String,
    val antall: Int?,
)

private data class MedSærnorsk(
    val id: String,
    val kanIverksette: Boolean,
    val årsak: String,
)

private data class MedBacktick(
    val klasse: String,
    val type: String,
)

@Suppress("unused")
private enum class TestEnum { A, B }

private data class MedInt(val n: Int)
private data class MedLong(val n: Long)
