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
    private fun assertKaster(forventetIMelding: String, blokk: () -> Any?) =
        assertKasterMedÅrsak(forventetIMelding, blokk)

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
    fun `Sequence nested i DTO kaster — fanges via Iterator-banet i banlisten`() {
        // Tidligere et kjent gap: Jackson håndterer Sequence-felter via iterator-protokollen, så `addSerializer(Sequence::class)` traff aldri.
        // Etter at vi la Iterator inn i banlisten fanges Sequence-felter også — Jackson henter Iterator-en, og vår modifier overstyrer dens serializer.
        assertKaster("Iterator") {
            serialize(MedSequence(navn = "x", tall = sequenceOf(1, 2, 3)))
        }
    }

    // ------ Iterator ------

    @Test
    fun `Iterator top-level kaster`() {
        // Iterator har samme problem som Sequence: engangs-konsum av kilden, ikke en stabil data-representasjon.
        assertKaster("Iterator") { serialize(listOf(1, 2, 3).iterator()) }
    }

    @Test
    fun `Iterator som felt i DTO kaster`() {
        assertKaster("Iterator") {
            serialize(MedIterator(navn = "x", it = listOf(1, 2, 3).iterator()))
        }
    }

    // ------ Stream ------

    @Test
    fun `Stream top-level kaster`() {
        assertKaster("Stream") { serialize(java.util.stream.Stream.of(1, 2, 3)) }
    }

    @Test
    fun `IntStream, LongStream og DoubleStream top-level kaster`() {
        // Alle BaseStream-subtypene skal fanges av samme banlist-entry (BaseStream).
        assertKaster("Stream") { serialize(java.util.stream.IntStream.of(1, 2, 3)) }
        assertKaster("Stream") { serialize(java.util.stream.LongStream.of(1, 2, 3)) }
        assertKaster("Stream") { serialize(java.util.stream.DoubleStream.of(1.0, 2.0, 3.0)) }
    }

    @Test
    fun `Stream som felt i DTO kaster`() {
        // Stream-felter rutes via Jacksons egne Stream-serializere som vår modifier får sjansen til å overstyre.
        assertKaster("Stream") {
            serialize(MedStream(navn = "x", strøm = java.util.stream.Stream.of(1, 2, 3)))
        }
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

    @Test
    fun `Throwable som felt i DTO kaster`() {
        assertKaster("Throwable") {
            serialize(MedThrowable(navn = "x", feil = RuntimeException("oops")))
        }
    }

    @Test
    fun `Throwable som element i List kaster`() {
        assertKaster("Throwable") {
            listOf<Throwable>(RuntimeException("a"), IllegalStateException("b")).serialize()
        }
    }

    // ------ Clock ------

    @Test
    fun `Clock top-level kaster`() {
        assertKaster("Clock") { serialize(Clock.systemUTC()) }
        assertKaster("Clock") { serialize(Clock.fixed(java.time.Instant.EPOCH, ZoneId.of("UTC"))) }
    }

    @Test
    fun `Clock som felt i DTO kaster`() {
        assertKaster("Clock") {
            serialize(MedClock(navn = "x", clock = Clock.systemUTC()))
        }
    }

    // ------ Thread ------

    @Test
    fun `Thread top-level kaster`() {
        assertKaster("Thread") { serialize(Thread.currentThread()) }
    }

    @Test
    fun `Thread som felt i DTO kaster`() {
        assertKaster("Thread") {
            serialize(MedThread(navn = "x", tråd = Thread.currentThread()))
        }
    }

    // ------ File ------

    @Test
    fun `File top-level kaster`() {
        assertKaster("File") { serialize(File("/tmp/test.txt")) }
    }

    @Test
    fun `File som felt i DTO kaster`() {
        assertKaster("File") {
            serialize(MedFile(navn = "x", fil = File("/tmp/test.txt")))
        }
    }

    @Test
    fun `File deserialisering kaster`() {
        // addDeserializer-registreringen fanger File ved deserialisering også.
        assertKaster("File") { deserialize<File>(""""/tmp/x.txt"""") }
    }

    // ------ Regex ------

    @Test
    fun `Regex top-level kaster`() {
        assertKaster("Regex") { serialize(Regex("abc.*")) }
    }

    @Test
    fun `Regex som felt i DTO kaster`() {
        assertKaster("Regex") {
            serialize(MedRegex(navn = "x", mønster = Regex("abc.*")))
        }
    }

    // ------ Deserialiserings-stien for de øvrige banlist-typene ------

    @Test
    fun `Optional, Option og Result kaster også ved deserialisering`() {
        // addDeserializer-registreringene treffer på exact type. For Either dekkes alt i JacksonArrowTest; her sjekker vi at de andre er linja ut.
        assertKaster("java.util.Optional") { deserialize<Optional<String>>(""""a"""") }
        assertKaster("arrow.core.Option") { deserialize<Option<String>>(""""a"""") }
        assertKaster("kotlin.Result") { deserialize<Result<Int>>("42") }
    }

    @Test
    fun `Tuple4 til Tuple9 kaster også ved deserialisering`() {
        assertKaster("Tuple4") {
            deserialize<Tuple4<String, Int, Boolean, Double>>(
                """{"first":"a","second":1,"third":true,"fourth":2.5}""",
            )
        }
        assertKaster("Tuple9") {
            deserialize<Tuple9<String, Int, Boolean, Double, String, String, Long, Float, Int>>(
                """{"first":"a","second":1,"third":true,"fourth":2.5,"fifth":"z","sixth":"c","seventh":7,"eighth":8.0,"ninth":9}""",
            )
        }
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

    // ------ Map-nøkler: egen key-serializer-sti i Jackson ------

    @Test
    fun `bannlyst type som Map-nøkkel kaster ved serialisering`() {
        // Uten override på key-serializer-stien ville Jackson falt tilbake til toString() for nøkkelen
        // (f.eks. "(a, 1)" for Pair) — en bannlyst type ville da lekket gjennom som streng-nøkkel.
        assertKaster("kotlin.Pair") { serialize(mapOf(("a" to 1) to "verdi")) }
        assertKaster("kotlin.Triple") { serialize(mapOf(Triple("a", 1, true) to "verdi")) }
        assertKaster("File") { serialize(mapOf(File("/tmp/x") to "verdi")) }
        assertKaster("Regex") { serialize(mapOf(Regex("abc") to "verdi")) }
    }

    @Test
    fun `bannlyst type som Map-nøkkel nested i DTO kaster`() {
        assertKaster("kotlin.Pair") {
            serialize(MedMapNøkkel(navn = "x", nøkler = mapOf(("a" to 1) to "verdi")))
        }
    }

    @Test
    fun `bannlyst type som Map-nøkkel kaster ved deserialisering`() {
        // KeyDeserializer-stien er separat fra ValueDeserializer — uten egen registrering ville
        // Jackson kunne fallt tilbake til reflection/String-konstruktør og produsert en nøkkel.
        assertKaster("File") {
            deserialize<Map<File, String>>("""{"/tmp/x":"verdi"}""")
        }
        assertKaster("Regex") {
            deserialize<Map<Regex, String>>("""{"abc":"verdi"}""")
        }
    }

    @Test
    fun `Map-nøkler round-tripper når enforceBanlist er false`() {
        // Rømningsluken må også slippe gjennom key-stien, ikke bare value-stien.
        serialize(mapOf(("a" to 1) to "verdi"), enforceBanlist = false) shouldBe
            """{"(a, 1)":"verdi"}"""
    }

    // ------ Subtyper på deserialiseringsstien ------

    @Test
    fun `subklasse av Throwable kaster ved deserialisering`() {
        // addDeserializer registrerer kun eksakt type, så uten en deserializer-modifier som
        // sjekker isAssignableFrom ville f.eks. IllegalArgumentException sluppet gjennom og
        // forsøkt vanlig bean-deserialisering — banlist-kontrakten må gjelde subtyper også.
        assertKaster("Throwable") {
            deserialize<IllegalArgumentException>("""{"message":"oops"}""")
        }
        assertKaster("Throwable") {
            deserialize<RuntimeException>("""{"message":"oops"}""")
        }
    }

    @Test
    fun `subklasse av Throwable i List og Map kaster ved deserialisering`() {
        assertKaster("Throwable") {
            deserialize<List<IllegalStateException>>("""[{"message":"a"}]""")
        }
        assertKaster("Throwable") {
            deserialize<Map<String, IllegalArgumentException>>("""{"k":{"message":"a"}}""")
        }
    }

    @Test
    fun `subklasse av bannlyst type som Map-nøkkel kaster ved deserialisering`() {
        // Subtype + key-stien: dobbelt-spesielt — uten modifyKeyDeserializer ville en
        // konkret File-subklasse ikke truffet eksakt-type-registreringen.
        assertKaster("Throwable") {
            deserialize<Map<IllegalArgumentException, String>>("""{"x":"v"}""")
        }
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
            "kotlin.collections.Iterator",
            "java.util.stream.BaseStream",
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
private data class MedIterator(val navn: String, val it: Iterator<Int>)
private data class MedStream(val navn: String, val strøm: java.util.stream.Stream<Int>)
private data class MedThrowable(val navn: String, val feil: Throwable)
private data class MedClock(val navn: String, val clock: Clock)
private data class MedThread(val navn: String, val tråd: Thread)
private data class MedFile(val navn: String, val fil: File)
private data class MedRegex(val navn: String, val mønster: Regex)
private data class MedMapNøkkel(val navn: String, val nøkler: Map<Pair<String, Int>, String>)
