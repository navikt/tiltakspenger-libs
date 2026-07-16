package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * SerDes-kontrakt for typer som ikke er en data class og ikke en av de "store" stdlib-kategoriene:
 *   - Inline value classes (samme mønster som typed IDs i `common`-modulen)
 *   - `Char`
 *   - `Array<T>` og primitive arrays (`IntArray`, `LongArray`, `DoubleArray`, `BooleanArray`)
 *   - `ByteArray` — Jackson koder som Base64-streng (ikke JSON-array)
 *   - `Collection<T>` / `Iterable<T>` — read-only abstrakte typer, fungerer ved serialisering
 *
 * Hvis du legger til en ny rar type her: husk å assertere *eksakt* JSON-format, ikke bare round-trip.
 * Symmetrisk brutte sider av (de)serialiseringen kan henge sammen uten at wire-formatet er det vi tror.
 */
internal class JacksonExtraTypesTest {

    // --- Inline value class -----------------------------------------------------------------
    //
    // Følger samme mønster som typed IDs (`SakId`, `BehandlingId`, ...) i `common`-modulen: én underliggende verdi (her String).
    // Jackson Kotlin-modulen "unwrapper" inline value classes automatisk, slik at de serialiseres som sin underliggende primitiv — uten behov for @JsonValue/@JsonCreator-annotasjoner.

    @Test
    fun `value class round-tripper som streng-primitiv via Jackson Kotlin-modulens value class-støtte`() {
        roundTrip(KundeId("k_123"), """"k_123"""")
    }

    @Test
    fun `value class som felt i DTO`() {
        roundTrip(
            MedKundeId(navn = "x", id = KundeId("k_42")),
            """{"navn":"x","id":"k_42"}""",
        )
    }

    @Test
    fun `List av value class round-tripper`() {
        roundTripList(
            listOf(KundeId("k_1"), KundeId("k_2")),
            """["k_1","k_2"]""",
        )
    }

    @Test
    fun `Map med value class som verdi`() {
        roundTripMap<String, KundeId>(
            mapOf("a" to KundeId("k_1")),
            """{"a":"k_1"}""",
        )
    }

    // --- Char --------------------------------------------------------------------------------

    @Test
    fun `Char top-level round-tripper som streng`() {
        // Jackson Kotlin-modulen håndterer Char som én-tegns-streng — enkleste forventning.
        roundTrip('a', """"a"""")
        roundTrip('Å', """"Å"""")
    }

    @Test
    fun `Char som felt i DTO`() {
        roundTrip(MedChar(navn = "x", initial = 'B'), """{"navn":"x","initial":"B"}""")
    }

    // --- Arrays ------------------------------------------------------------------------------
    //
    // Arrays serialiseres som JSON-array.
    // Equality på Array<T> er referanse-basert, så vi sammenligner via toList().
    // Det gjør round-trip via [roundTrip] upraktisk; vi gjør serialize + deserialize separat.

    @Test
    fun `Array av Int — serialiseres som JSON-array`() {
        val a = arrayOf(1, 2, 3)
        serialize(a) shouldBe """[1,2,3]"""
        deserialize<Array<Int>>("""[1,2,3]""").toList() shouldBe a.toList()
    }

    @Test
    fun `Array av DTO`() {
        val a = arrayOf(EnkelDto("a"), EnkelDto("b"))
        serialize(a) shouldBe """[{"navn":"a"},{"navn":"b"}]"""
        deserialize<Array<EnkelDto>>("""[{"navn":"a"},{"navn":"b"}]""").toList() shouldBe a.toList()
    }

    @Test
    fun `IntArray, LongArray, DoubleArray, BooleanArray — alle som JSON-array`() {
        serialize(intArrayOf(1, 2, 3)) shouldBe """[1,2,3]"""
        deserialize<IntArray>("""[1,2,3]""").toList() shouldBe listOf(1, 2, 3)

        serialize(longArrayOf(1L, 2L)) shouldBe """[1,2]"""
        deserialize<LongArray>("""[1,2]""").toList() shouldBe listOf(1L, 2L)

        serialize(doubleArrayOf(1.5, 2.5)) shouldBe """[1.5,2.5]"""
        deserialize<DoubleArray>("""[1.5,2.5]""").toList() shouldBe listOf(1.5, 2.5)

        serialize(booleanArrayOf(true, false)) shouldBe """[true,false]"""
        deserialize<BooleanArray>("""[true,false]""").toList() shouldBe listOf(true, false)
    }

    // --- ByteArray (Base64) ------------------------------------------------------------------

    @Test
    fun `ByteArray serialiseres som Base64-streng — IKKE JSON-array`() {
        // Dette er Jackson sin defaultadferd og gjelder også når ByteArray er felt i en DTO.
        // Kommer som overraskelse hvis man forventer "[1,2,3]"; dokumentert eksplisitt her.
        val data = byteArrayOf(1, 2, 3)
        val base64 = Base64.getEncoder().encodeToString(data)
        serialize(data) shouldBe """"$base64""""
        deserialize<ByteArray>(""""$base64"""").toList() shouldBe data.toList()
    }

    @Test
    fun `ByteArray som felt i DTO — Base64-streng`() {
        val data = byteArrayOf(0x42, 0x43)
        val base64 = Base64.getEncoder().encodeToString(data)
        serialize(MedByteArray(navn = "x", data = data)) shouldBe
            """{"navn":"x","data":"$base64"}"""
    }

    // --- Collection / Iterable ---------------------------------------------------------------

    @Test
    fun `Collection-felt i DTO — kan deklarere read-only abstrakt type`() {
        // Mange API-er bruker `Collection<T>` for å unngå å forplikte seg til en konkret implementasjon.
        // Jackson håndterer dette greit på output-siden; deserialisering velger ArrayList.
        val dto = MedCollection(navn = "x", verdier = listOf(1, 2, 3))
        serialize(dto) shouldBe """{"navn":"x","verdier":[1,2,3]}"""
        val lest = deserialize<MedCollection>("""{"navn":"x","verdier":[1,2,3]}""")
        lest.navn shouldBe "x"
        lest.verdier.toList() shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `Iterable-felt i DTO — Jackson serialiserer ved å iterere én gang`() {
        // En `Iterable<T>` over en List er deterministisk; over en Sequence er det "ikke ditt problem" — Sequence ville blitt fanget av banlisten.
        val dto = MedIterable(navn = "x", verdier = listOf(1, 2, 3))
        serialize(dto) shouldBe """{"navn":"x","verdier":[1,2,3]}"""
    }
}

@JvmInline
internal value class KundeId(val verdi: String)

private data class MedKundeId(val navn: String, val id: KundeId)
private data class MedChar(val navn: String, val initial: Char)
private data class EnkelDto(val navn: String)
private data class MedByteArray(val navn: String, val data: ByteArray) {
    override fun equals(other: Any?) = other is MedByteArray && navn == other.navn && data.contentEquals(other.data)
    override fun hashCode() = 31 * navn.hashCode() + data.contentHashCode()
}

private data class MedCollection(val navn: String, val verdier: Collection<Int>)
private data class MedIterable(val navn: String, val verdier: Iterable<Int>)
