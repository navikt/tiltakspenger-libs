package no.nav.tiltakspenger.libs.json
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.Tuple4
import arrow.core.Tuple5
import arrow.core.Tuple6
import arrow.core.Tuple7
import arrow.core.Tuple8
import arrow.core.Tuple9
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Clock
import java.time.ZoneId
import java.util.Optional

/**
 * Banlist-kontrakten i [objectMapper]: alle typer i [BANLIST] skal kaste ved (de)serialisering, både top-level og nested.
 *
 * Either-spesifikke tester ligger i [JacksonArrowTest] sammen med Arrow-typene generelt.
 * Denne fila dekker resten av banlisten + [objectMapperUtenBanlist]/`enforceBanlist = false`-eskapen.
 */
internal class JacksonBanlistTest {
    private fun assertKaster(forventetIMelding: String, blokk: () -> Any?) {
        val ex = shouldThrow<Throwable>(blokk)
        val meldinger = generateSequence(ex as Throwable?) { it.cause }.mapNotNull { it.message }.toList()
        check(meldinger.any { forventetIMelding in it }) {
            "Forventet en exception i kjeden med \"$forventetIMelding\", men fant: $meldinger"
        }
    }

    // ------ Pair / Triple ------
    @Test
    fun `Pair top-level kaster`() {
        assertKaster("kotlin.Pair") { serialize("a" to 1) }
        assertKaster("kotlin.Pair") { deserialize<Pair<String, Int>>("""{"first":"a","second":1}""") }
    }

    @Test
    fun `Pair nested i DTO kaster`() {
        assertKaster("kotlin.Pair") { serialize(MedPair(navn = "x", par = "a" to 1)) }
        assertKaster("kotlin.Pair") { deserialize<MedPair>("""{"navn":"x","par":{"first":"a","second":1}}""") }
    }

    @Test
    fun `Triple top-level kaster`() {
        assertKaster("kotlin.Triple") { serialize(Triple("a", 1, true)) }
        assertKaster("kotlin.Triple") { deserialize<Triple<String, Int, Boolean>>("""{"first":"a","second":1,"third":true}""") }
    }

    @Test
    fun `Triple nested i List kaster`() {
        assertKaster("kotlin.Triple") {
            listOf(Triple("a", 1, true)).serialize()
        }
    }

    // ------ Arrow TupleN ------
    @Test
    fun `Tuple4 til Tuple9 top-level kaster`() {
        assertKaster("Tuple4") { serialize(Tuple4("a", 1, true, 2.5)) }
        assertKaster("Tuple5") { serialize(Tuple5("a", 1, true, 2.5, "z")) }
        assertKaster("Tuple6") { serialize(Tuple6("a", 1, true, 2.5, "z", 'c')) }
        assertKaster("Tuple7") { serialize(Tuple7("a", 1, true, 2.5, "z", 'c', 7L)) }
        assertKaster("Tuple8") { serialize(Tuple8("a", 1, true, 2.5, "z", 'c', 7L, 8.0f)) }
        assertKaster("Tuple9") { serialize(Tuple9("a", 1, true, 2.5, "z", 'c', 7L, 8.0f, 9)) }
    }

    @Test
    fun `Tuple4 nested i DTO kaster`() {
        assertKaster("Tuple4") {
            serialize(MedTuple4(navn = "x", tuple = Tuple4("a", 1, true, 2.5)))
        }
    }

    // ------ Result ------
    @Test
    fun `Result top-level kaster`() {
        // Uten guard ville Result.success(42) ha gått igjennom som "42" — silent unwrap er presist det vi vil unngå.
        assertKaster("kotlin.Result") { serialize(Result.success(42)) }
        assertKaster("kotlin.Result") { serialize(Result.failure<Int>(RuntimeException("noe gikk galt"))) }
    }

    @Test
    fun `Result nested i DTO kaster`() {
        assertKaster("kotlin.Result") {
            serialize(MedResult(navn = "x", resultat = Result.success(42)))
        }
    }

    // ------ Optional / Option ------
    @Test
    fun `java util Optional top-level kaster`() {
        assertKaster("java.util.Optional") { serialize(Optional.of("verdi")) }
        assertKaster("java.util.Optional") { serialize(Optional.empty<String>()) }
    }

    @Test
    fun `arrow Option top-level kaster`() {
        assertKaster("arrow.core.Option") { serialize(Some("verdi") as Option<String>) }
        assertKaster("arrow.core.Option") { serialize(None as Option<String>) }
    }

    @Test
    fun `Optional og Option nested i DTO kaster`() {
        assertKaster("java.util.Optional") {
            serialize(MedOptional(navn = "x", verdi = Optional.of("a")))
        }
        assertKaster("arrow.core.Option") {
            serialize(MedArrowOption(navn = "x", verdi = Some("a")))
        }
    }

    // ------ Sequence ------
    @Test
    fun `Sequence top-level kaster`() {
        assertKaster("Sequence") { serialize(sequenceOf(1, 2, 3)) }
    }

    @Test
    fun `Sequence nested i DTO — kjent gap, fanges ikke nested`() {
        // Sequence er bannlistet på top-level (via krevIkkeBannlistet), men Jackson håndterer Sequence-felter via iterator-protokollen før vår banlist-registrering eller serializer-modifier får sjansen.
        // Dette er en kjent begrensning, ikke en bug. Verifiserer adferden så vi fanger en endring hvis Jackson noensinne ruter annerledes.
        serialize(MedSequence(navn = "x", tall = sequenceOf(1, 2, 3))) shouldBe
            """{"navn":"x","tall":[1,2,3]}"""
    }

    // ------ Throwable ------

    @Test
    fun `Throwable top-level kaster`() {
        assertKaster("Throwable") { serialize(RuntimeException("oops")) }
    }

    @Test
    fun `Exception subklasse fanges også`() {
        assertKaster("Throwable") { serialize(IllegalArgumentException("nope")) }
    }

    // ------ Clock ------

    @Test
    fun `Clock top-level kaster`() {
        assertKaster("Clock") { serialize(Clock.systemUTC()) }
        assertKaster("Clock") { serialize(Clock.fixed(java.time.Instant.EPOCH, ZoneId.of("UTC"))) }
    }

    // ------ Thread ------

    @Test
    fun `Thread top-level kaster`() {
        assertKaster("Thread") { serialize(Thread.currentThread()) }
    }

    // ------ File ------

    @Test
    fun `File top-level kaster`() {
        assertKaster("File") { serialize(File("/tmp/test.txt")) }
    }

    // ------ Regex ------

    @Test
    fun `Regex top-level kaster`() {
        assertKaster("Regex") { serialize(Regex("abc.*")) }
    }

    // ------ Rømningsluke: enforceBanlist = false ------
    @Test
    fun `Pair og Triple round-tripper når enforceBanlist er false`() {
        // Rømningsluken lar bannlyste typer slippe gjennom — bruk kun bevisst (testing/migrering).
        serialize("a" to 1, enforceBanlist = false) shouldBe """{"first":"a","second":1}"""
        deserialize<Pair<String, Int>>("""{"first":"a","second":1}""", enforceBanlist = false) shouldBe ("a" to 1)
        serialize(Triple("a", 1, true), enforceBanlist = false) shouldBe """{"first":"a","second":1,"third":true}"""
    }

    @Test
    fun `Tuple4 round-tripper når enforceBanlist er false`() {
        serialize(Tuple4("a", 1, true, 2.5), enforceBanlist = false) shouldBe
            """{"first":"a","second":1,"third":true,"fourth":2.5}"""
        deserialize<Tuple4<String, Int, Boolean, Double>>(
            """{"first":"a","second":1,"third":true,"fourth":2.5}""",
            enforceBanlist = false,
        ) shouldBe Tuple4("a", 1, true, 2.5)
    }

    @Test
    fun `List- og Map-helperne respekterer enforceBanlist-flagget`() {
        // Receiver-formen String.deserializeList:
        """[{"first":"a","second":1}]""".deserializeList<Pair<String, Int>>(enforceBanlist = false) shouldBe
            listOf("a" to 1)
        // Free function deserializeList:
        deserializeList<Triple<String, Int, Boolean>>(
            """[{"first":"a","second":1,"third":true}]""",
            enforceBanlist = false,
        ) shouldBe listOf(Triple("a", 1, true))
        // deserializeMap:
        deserializeMap<String, Pair<String, Int>>(
            """{"x":{"first":"a","second":1}}""",
            enforceBanlist = false,
        ) shouldBe mapOf("x" to ("a" to 1))
        // *Nullable-varianter:
        deserializeListNullable<Pair<String, Int>>(null, enforceBanlist = false) shouldBe null
        deserializeMapNullable<String, Pair<String, Int>>(null, enforceBanlist = false) shouldBe null
        deserializeNullable<Pair<String, Int>>(null, enforceBanlist = false) shouldBe null
        serializeNullable(null, enforceBanlist = false) shouldBe null
        // List<T>.serialize() receiver:
        listOf("a" to 1).serialize(enforceBanlist = false) shouldBe """[{"first":"a","second":1}]"""
    }

    @Test
    fun `Sequence serialiseres som JSON-array når enforceBanlist er false`() {
        serialize(sequenceOf(1, 2, 3), enforceBanlist = false) shouldBe "[1,2,3]"
    }

    @Test
    fun `Result-success unwrappes silent uten banlist — dokumenterer hvorfor banet`() {
        // Akkurat denne adferden er hvorfor Result er bannlistet: success(42) blir til "42" på wire.
        // Man kan ikke skille det fra en Int 42 ved deserialisering — uforutsigbart.
        serialize(Result.success(42), enforceBanlist = false) shouldBe "42"
    }

    @Test
    fun `rømningsluken via objectMapperUtenBanlist gir samme resultat som flagget`() {
        objectMapperUtenBanlist.writeValueAsString("a" to 1) shouldBe """{"first":"a","second":1}"""
    }

    // ------ Banlist-konsistens ------
    @Test
    fun `BANLIST inneholder alle forventede typer`() {
        val typeNavn = BANLIST.map { it.type.qualifiedName }.toSet()
        listOf(
            "arrow.core.Either",
            "arrow.core.Option",
            "java.util.Optional",
            "kotlin.Pair",
            "kotlin.Triple",
            "arrow.core.Tuple4",
            "arrow.core.Tuple9",
            "kotlin.Result",
            "kotlin.sequences.Sequence",
            "kotlin.Throwable",
            "java.time.Clock",
            "java.lang.Thread",
            "java.io.File",
            "kotlin.text.Regex",
        ).forEach { (it in typeNavn) shouldBe true }
    }

    @Test
    fun `BANLIST-meldinger er ikke tomme og inneholder type-navn`() {
        BANLIST.forEach { entry ->
            entry.grunn.isNotBlank() shouldBe true
            // Meldingen bør referere typenavnet eller en del av det, så devs forstår hva som er galt.
            val sisteSegment = entry.type.qualifiedName!!.substringAfterLast(".")
            entry.grunn.contains(sisteSegment) shouldBe true
        }
    }
}
private data class MedPair(val navn: String, val par: Pair<String, Int>)
private data class MedTuple4(val navn: String, val tuple: Tuple4<String, Int, Boolean, Double>)
private data class MedResult(val navn: String, val resultat: Result<Int>)
private data class MedOptional(val navn: String, val verdi: Optional<String>)
private data class MedArrowOption(val navn: String, val verdi: Option<String>)
private data class MedSequence(val navn: String, val tall: Sequence<Int>)
