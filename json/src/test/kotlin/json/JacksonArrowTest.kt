package no.nav.tiltakspenger.libs.json

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.NonEmptySet
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.nonEmptySetOf
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tools.jackson.databind.DatabindException

/**
 * SerDes-kontrakt for typer fra `arrow.core` som faktisk brukes i `tiltakspenger`-monorepo:
 *   - [NonEmptyList] / [NonEmptySet] (via `arrow-core-jackson` sin [arrow.integrations.jackson.module.NonEmptyCollectionsModule])
 *   - [Either] er eksplisitt _ikke_ støttet — verken serialisering eller deserialisering.
 *
 * Andre Arrow-typer (`Tuple4..6`, `Atomic`) er i bruk i koden vår, men har ingen JSON-kontrakt vi forplikter oss til.
 * De er bevisst ikke testet her; legg til hvis behovet oppstår.
 */
internal class JacksonArrowTest {

    // --- NonEmptyList ------------------------------------------------------------------------

    @Test
    fun `NonEmptyList top-level round-trip`() {
        roundTrip(nonEmptyListOf("a", "b", "c"), """["a","b","c"]""")
    }

    @Test
    fun `NonEmptyList med ett element`() {
        roundTrip(nonEmptyListOf("kake"), """["kake"]""")
    }

    @Test
    fun `NonEmptyList som felt i DTO`() {
        roundTrip(
            MedNel(navn = "x", verdier = nonEmptyListOf("a", "b")),
            """{"navn":"x","verdier":["a","b"]}""",
        )
    }

    @Test
    fun `liste av NonEmptyList`() {
        roundTripList(
            listOf(nonEmptyListOf("a"), nonEmptyListOf("b", "c")),
            """[["a"],["b","c"]]""",
        )
    }

    @Test
    fun `Map med NonEmptyList som verdi`() {
        roundTripMap(
            mapOf("x" to nonEmptyListOf("a", "b")),
            """{"x":["a","b"]}""",
        )
    }

    @Test
    fun `tom NonEmptyList kaster ved deserialisering`() {
        shouldThrow<DatabindException> {
            deserialize<NonEmptyList<String>>("[]")
        }
    }

    // --- NonEmptySet -------------------------------------------------------------------------

    @Test
    fun `NonEmptySet top-level round-trip`() {
        roundTrip(nonEmptySetOf(1, 2, 3), """[1,2,3]""")
    }

    @Test
    fun `NonEmptySet som felt i DTO`() {
        roundTrip(
            MedNes(navn = "x", tall = nonEmptySetOf(1, 2)),
            """{"navn":"x","tall":[1,2]}""",
        )
    }

    @Test
    fun `tom NonEmptySet kaster ved deserialisering`() {
        shouldThrow<DatabindException> {
            deserialize<NonEmptySet<Int>>("[]")
        }
    }

    // --- Either: eksplisitt ikke støttet ----------------------------------------------------
    //
    // All bruk av Either i JSON skal kaste — både ved serialisering og deserialisering, både top-level og nested (DTO-felt, List/Set/Map/Iterable).
    // Jackson kan pakke unntaket fra (de)serializeren i en wrapper-exception, så for nested-tilfeller sjekker vi at meldingen er bevart i cause-kjeden — ikke eksakt exception-type.

    // -- Serialisering --

    @Test
    fun `serialisering av Either Right kaster`() {
        val ex = shouldThrow<IllegalArgumentException> {
            serialize(42.right())
        }
        ex.message shouldBe EITHER_FEILMELDING
    }

    @Test
    fun `serialisering av Either Left kaster`() {
        shouldThrow<IllegalArgumentException> {
            serialize("feil".left())
        }
    }

    @Test
    fun `serializeNullable av Either kaster`() {
        shouldThrow<IllegalArgumentException> {
            serializeNullable(42.right() as Either<String, Int>)
        }
    }

    @Test
    fun `serializeNullable av null Either gir null uten å kaste`() {
        // Guarden trigger først når verdien faktisk er en Either-instans — null slipper gjennom.
        serializeNullable(null as Either<String, Int>?) shouldBe null
    }

    @Test
    fun `Either som felt i DTO kaster ved serialisering`() {
        val ex = shouldThrow<Throwable> {
            serialize(MedEither(navn = "x", resultat = 42.right()))
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som element i List kaster ved serialisering`() {
        val ex = shouldThrow<Throwable> {
            listOf<Either<String, Int>>(1.right(), 2.right()).serialize()
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som verdi i Map kaster ved serialisering`() {
        val ex = shouldThrow<Throwable> {
            serialize(mapOf("a" to 1.right()) as Map<String, Either<String, Int>>)
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som element i Set kaster ved serialisering`() {
        val ex = shouldThrow<Throwable> {
            serialize(setOf<Either<String, Int>>(1.right()))
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either nested dypt i en DTO kaster ved serialisering`() {
        val dto = MedNestetEither(
            ytre = "ytre",
            indre = listOf(MedEither(navn = "a", resultat = 1.right())),
        )
        val ex = shouldThrow<Throwable> { serialize(dto) }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    // -- Deserialisering --

    @Test
    fun `deserialisering av Either top-level kaster`() {
        val ex = shouldThrow<Throwable> {
            deserialize<Either<String, Int>>("""{"right":42}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som felt i DTO kaster ved deserialisering`() {
        val ex = shouldThrow<Throwable> {
            deserialize<MedEither>("""{"navn":"x","resultat":{"right":42}}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som element i List kaster ved deserialisering`() {
        val ex = shouldThrow<Throwable> {
            deserializeList<Either<String, Int>>("""[{"right":1}]""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `Either som verdi i Map kaster ved deserialisering`() {
        val ex = shouldThrow<Throwable> {
            deserializeMap<String, Either<String, Int>>("""{"a":{"right":1}}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    // -- Nullable-helperne: Either skal kaste her også --

    @Test
    fun `serializeNullable av DTO med Either-felt kaster`() {
        val ex = shouldThrow<Throwable> {
            serializeNullable(MedEither(navn = "x", resultat = 42.right()))
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `deserializeNullable av Either top-level kaster`() {
        val ex = shouldThrow<Throwable> {
            deserializeNullable<Either<String, Int>>("""{"right":42}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `deserializeNullable av DTO med Either-felt kaster`() {
        val ex = shouldThrow<Throwable> {
            deserializeNullable<MedEither>("""{"navn":"x","resultat":{"right":42}}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `deserializeListNullable av Either top-level kaster`() {
        val ex = shouldThrow<Throwable> {
            deserializeListNullable<Either<String, Int>>("""[{"right":1}]""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `deserializeMapNullable av Either top-level kaster`() {
        val ex = shouldThrow<Throwable> {
            deserializeMapNullable<String, Either<String, Int>>("""{"a":{"right":1}}""")
        }
        ex.feilårsakInneholder(EITHER_FEILMELDING)
    }

    @Test
    fun `nullable-helperne returnerer null uten å kaste for null-input`() {
        // Selv om Either er sperret, skal *Nullable-helperne fortsatt returnere null for null-input — guarden trigger først når en faktisk Either-instans dukker opp.
        deserializeNullable<Either<String, Int>>(null) shouldBe null
        deserializeListNullable<Either<String, Int>>(null) shouldBe null
        deserializeMapNullable<String, Either<String, Int>>(null) shouldBe null
    }

    // -- lesTre: går utenom type-bindingen og treffer ikke Either-guarden --

    @Test
    fun `lesTre parser Either-formet JSON som vanlig JsonNode uten å kaste`() {
        // lesTre returnerer en JsonNode (rå tre-struktur) og materialiserer aldri Either.
        // Guarden bryr seg ikke — det er det riktige: lesTre vet ikke at JSON-objektet "betyr" Either.
        val node = lesTre("""{"right":42}""")
        node.get("right").asInt() shouldBe 42
    }
}

private fun Throwable.feilårsakInneholder(forventet: String) {
    val meldinger = generateSequence(this as Throwable?) { it.cause }
        .mapNotNull { it.message }
        .toList()
    check(meldinger.any { forventet in it }) {
        "Forventet at en exception i kjeden inneholdt meldingen \"$forventet\", men fant: $meldinger"
    }
}

private data class MedNel(
    val navn: String,
    val verdier: NonEmptyList<String>,
)

private data class MedNes(
    val navn: String,
    val tall: NonEmptySet<Int>,
)

private data class MedEither(
    val navn: String,
    val resultat: Either<String, Int>,
)

private data class MedNestetEither(
    val ytre: String,
    val indre: List<MedEither>,
)
