package no.nav.tiltakspenger.libs.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.DatabindException
import java.time.LocalDate
import java.util.UUID

/**
 * SerDes-kontrakt for dypt nestede kolleksjoner i alle permutasjoner som utviklere realistisk forventer skal fungere:
 *   - List/Set/Map nestet i List/Set/Map (alle ni kombinasjoner)
 *   - DTO som inneholder slike strukturer
 *   - Map med ikke-String-nøkler (Int, UUID, LocalDate) — dokumentert adferd i Jackson: nøkkelen toString()-serialiseres
 *   - nullable elementer/verdier dypt i grafen
 *
 * Top-level type-bevaring (`List<T>.serialize()`, `deserializeList<T>()`, `deserializeMap<K,V>()`) er allerede dekket i [JacksonHelpersTest]; denne fila kompletterer på *dybden* og *kombinasjonene*.
 */
internal class JacksonNestedCollectionsTest {

    // --- List i List / Set / Map ------------------------------------------------------------

    @Test
    fun `List of List round-trip`() {
        roundTripList(
            listOf(listOf(1, 2), listOf(3, 4), emptyList()),
            """[[1,2],[3,4],[]]""",
        )
    }

    @Test
    fun `List of Set round-trip`() {
        roundTripList(
            listOf(setOf(1, 2), setOf(3, 4)),
            """[[1,2],[3,4]]""",
        )
    }

    @Test
    fun `List of Map round-trip`() {
        roundTripList(
            listOf(mapOf("a" to 1), mapOf("b" to 2, "c" to 3)),
            """[{"a":1},{"b":2,"c":3}]""",
        )
    }

    // --- Set i List / Set / Map -------------------------------------------------------------

    @Test
    fun `Set of List som felt i DTO`() {
        roundTrip(
            MedSetAvList(navn = "x", verdier = setOf(listOf(1, 2), listOf(3, 4))),
            """{"navn":"x","verdier":[[1,2],[3,4]]}""",
        )
    }

    @Test
    fun `Set of Set som felt i DTO`() {
        roundTrip(
            MedSetAvSet(navn = "x", verdier = setOf(setOf(1, 2), setOf(3, 4))),
            """{"navn":"x","verdier":[[1,2],[3,4]]}""",
        )
    }

    @Test
    fun `Set of Map som felt i DTO`() {
        roundTrip(
            MedSetAvMap(navn = "x", verdier = setOf(mapOf("a" to 1), mapOf("b" to 2))),
            """{"navn":"x","verdier":[{"a":1},{"b":2}]}""",
        )
    }

    // --- Map i List / Set / Map -------------------------------------------------------------

    @Test
    fun `Map of List som verdi`() {
        roundTripMap(
            mapOf("a" to listOf(1, 2), "b" to listOf(3, 4)),
            """{"a":[1,2],"b":[3,4]}""",
        )
    }

    @Test
    fun `Map of Set som verdi`() {
        roundTripMap(
            mapOf("a" to setOf(1, 2), "b" to setOf(3, 4)),
            """{"a":[1,2],"b":[3,4]}""",
        )
    }

    @Test
    fun `Map of Map som verdi`() {
        roundTripMap<String, Map<String, Int>>(
            mapOf("ytre1" to mapOf("a" to 1, "b" to 2), "ytre2" to mapOf("c" to 3)),
            """{"ytre1":{"a":1,"b":2},"ytre2":{"c":3}}""",
        )
    }

    // --- Dypt nestet DTO --------------------------------------------------------------------

    @Test
    fun `dypt nestet DTO round-tripper`() {
        roundTrip(
            DyptNestet(
                navn = "ytre",
                mellomlag = listOf(
                    Mellomlag(
                        id = "m1",
                        innerlag = mapOf(
                            "a" to setOf(1, 2),
                            "b" to setOf(3),
                        ),
                    ),
                    Mellomlag(
                        id = "m2",
                        innerlag = mapOf("c" to setOf(4, 5)),
                    ),
                ),
            ),
            """{"navn":"ytre","mellomlag":[""" +
                """{"id":"m1","innerlag":{"a":[1,2],"b":[3]}},""" +
                """{"id":"m2","innerlag":{"c":[4,5]}}""" +
                """]}""",
        )
    }

    // --- Nullable dypt i grafen -------------------------------------------------------------

    @Test
    fun `nullable elementer dypt i List of List`() {
        roundTripList<List<Int?>>(
            listOf(listOf(1, null, 3), listOf(null, null)),
            """[[1,null,3],[null,null]]""",
        )
    }

    @Test
    fun `nullable verdi dypt i Map of Map`() {
        roundTripMap<String, Map<String, Int?>>(
            mapOf("a" to mapOf("x" to 1, "y" to null)),
            """{"a":{"x":1,"y":null}}""",
        )
    }

    // --- Map med ikke-String-nøkler ---------------------------------------------------------
    //
    // Jackson serialiserer Map-nøkler som strenger uavhengig av Kotlin-type — toString() på nøkkelen.
    // Deserialisering går via en KeyDeserializer som parser tilbake.
    // Dokumenterer adferden for typer en utvikler typisk vil bruke som nøkkel.

    @Test
    fun `Map med Int-nøkkel — nøkkelen blir streng på wire, parses tilbake til Int`() {
        roundTripMap<Int, String>(
            mapOf(1 to "en", 2 to "to"),
            """{"1":"en","2":"to"}""",
        )
    }

    @Test
    fun `Map med UUID-nøkkel`() {
        val a = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val b = UUID.fromString("00000000-0000-0000-0000-000000000002")
        roundTripMap<UUID, String>(
            mapOf(a to "alpha", b to "beta"),
            """{"$a":"alpha","$b":"beta"}""",
        )
    }

    @Test
    fun `Map med LocalDate-nøkkel`() {
        roundTripMap<LocalDate, Int>(
            mapOf(LocalDate.of(2026, 1, 1) to 1, LocalDate.of(2026, 6, 30) to 6),
            """{"2026-01-01":1,"2026-06-30":6}""",
        )
    }

    @Test
    fun `Map med ulest nøkkel-type kaster fail-loud`() {
        // Sanity: en nøkkel-type uten KeyDeserializer (her: DTO uten egen factory) fail-louder ved deserialisering.
        // Vi sjekker bare at det kaster, ikke eksakt feilmelding — den er Jackson-versjons-avhengig.
        shouldThrow<DatabindException> {
            deserializeMap<NøkkelDto, Int>("""{"NøkkelDto(x=1, y=2)":1}""")
        }
    }

    // --- Ujevn / ragged data ----------------------------------------------------------------

    @Test
    fun `ujevn nesting — tomme indre kolleksjoner`() {
        roundTripList(
            listOf(emptyList<Int>(), listOf(1), emptyList()),
            """[[],[1],[]]""",
        )
        roundTripMap<String, List<Int>>(
            mapOf("a" to emptyList(), "b" to listOf(1, 2)),
            """{"a":[],"b":[1,2]}""",
        )
    }

    @Test
    fun `List of DTO med Map med List som verdi`() {
        // En realistisk "aggregat-DTO" som binder alle lagene sammen.
        val dto = MedAggregat(
            tittel = "rapport",
            datasett = listOf(
                Datasett(
                    navn = "Q1",
                    tall = mapOf("jan" to listOf(1, 2), "feb" to listOf(3)),
                ),
                Datasett(
                    navn = "Q2",
                    tall = mapOf("apr" to listOf(4, 5, 6)),
                ),
            ),
        )
        roundTrip(
            dto,
            """{"tittel":"rapport","datasett":[""" +
                """{"navn":"Q1","tall":{"jan":[1,2],"feb":[3]}},""" +
                """{"navn":"Q2","tall":{"apr":[4,5,6]}}""" +
                """]}""",
        )
    }

    @Test
    fun `tom Map og tom List dypt i DTO`() {
        roundTrip(
            MedAggregat(tittel = "tom", datasett = emptyList()),
            """{"tittel":"tom","datasett":[]}""",
        )
        roundTrip(
            Datasett(navn = "tom", tall = emptyMap()),
            """{"navn":"tom","tall":{}}""",
        )
    }

    // --- Heterogen Set-deserialisering ------------------------------------------------------

    @Test
    fun `Set duplikater i JSON gir Set uten duplikater (HashSet-semantikk)`() {
        // En del API-er sender "Set" som JSON-array; Jackson dedupliserer ved deserialisering.
        // Dokumenterer adferden så ingen blir overrasket hvis avsender sender duplikater.
        val resultat = deserialize<Set<Int>>("""[1,2,2,3,3,3]""")
        resultat shouldBe setOf(1, 2, 3)
    }
}

private data class MedSetAvList(val navn: String, val verdier: Set<List<Int>>)
private data class MedSetAvSet(val navn: String, val verdier: Set<Set<Int>>)
private data class MedSetAvMap(val navn: String, val verdier: Set<Map<String, Int>>)
private data class Mellomlag(val id: String, val innerlag: Map<String, Set<Int>>)
private data class DyptNestet(val navn: String, val mellomlag: List<Mellomlag>)
private data class Datasett(val navn: String, val tall: Map<String, List<Int>>)
private data class MedAggregat(val tittel: String, val datasett: List<Datasett>)
private data class NøkkelDto(val x: Int, val y: Int)
