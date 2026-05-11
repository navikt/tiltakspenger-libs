package no.nav.tiltakspenger.libs.json

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Tester `json`-modulens egne hjelpefunksjoner (rundt [objectMapper]):
 *   - [serializeNullable] / [deserializeNullable] / [deserializeListNullable] / [deserializeMapNullable]
 *   - [deserializeList] / [deserializeMap] for nestede generic-typer og nullable element-typer
 *   - [List.serialize] receiver-utvidelsen
 *   - [lesTre] for `JsonNode`-tilgang
 *
 * Skiller mellom "nullable input" (hele JSON-strengen kan være null) og "nullable elementer" (JSON `null` som verdi inni en liste/map).
 * Begge er gyldig JSON (RFC 8259) men håndteres ulikt: den første via `*Nullable`-helperne, den andre via at type-parameteren er nullable (`T?`).
 *
 * For type-spesifikk JSON-kontrakt, se [JacksonArrowTest], [JacksonJavaTimeTest], [JacksonJavaTest], [JacksonKotlinTest] og [JacksonKotlinTimeTest].
 */
internal class JacksonHelpersTest {

    // --- Nullable-varianter -----------------------------------------------------------------

    @Test
    fun `serializeNullable og deserializeNullable håndterer null`() {
        serializeNullable(null) shouldBe null
        serializeNullable("hei") shouldBe """"hei""""
        deserializeNullable<String>(null) shouldBe null
        deserializeNullable<Int>("42") shouldBe 42
    }

    @Test
    fun `deserializeListNullable håndterer null input`() {
        // "Nullable" her gjelder selve input-strengen (hele lista kan mangle), ikke null-elementer inni lista — det er separat (se under).
        deserializeListNullable<Int>(null) shouldBe null
        deserializeListNullable<Int>("[1,2,3]") shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `deserializeMapNullable håndterer null input`() {
        deserializeMapNullable<String, Int>(null) shouldBe null
        deserializeMapNullable<String, Int>("""{"a":1}""") shouldBe mapOf("a" to 1)
    }

    // --- JSON null som element-verdi i kolleksjoner ----------------------------------------
    //
    // JSON (RFC 8259) tillater `null` som verdi hvor som helst.
    // Deserialisering til Kotlin-typer krever at element-typen er nullable (`T?`); ellers er det fail-loud.
    // Disse testene verifiserer at helper-funksjonene propagerer `T?` korrekt — uavhengig av om input-strengen i seg selv er null.

    @Test
    fun `deserializeList med nullable element-type godtar JSON null i lista`() {
        deserializeList<Int?>("""[1,null,3]""") shouldBe listOf(1, null, 3)
    }

    @Test
    fun `deserializeMap med nullable verdi-type godtar JSON null som verdi`() {
        deserializeMap<String, Int?>(
            """{"a":1,"b":null}""",
        ) shouldBe mapOf("a" to 1, "b" to null)
    }

    @Test
    fun `deserializeListNullable med nullable element-type — begge lagene nullable`() {
        deserializeListNullable<Int?>(null) shouldBe null
        deserializeListNullable<Int?>("""[1,null,3]""") shouldBe listOf(1, null, 3)
    }

    @Test
    fun `deserializeMapNullable med nullable verdi-type — begge lagene nullable`() {
        deserializeMapNullable<String, Int?>(null) shouldBe null
        deserializeMapNullable<String, Int?>(
            """{"a":1,"b":null}""",
        ) shouldBe mapOf("a" to 1, "b" to null)
    }

    @Test
    fun `deserializeList aksepterer JSON null også når element-typen er Int — Kotlin-nullability erases`() {
        // Helper-funksjonene bruker `typeOf<List<T>>().javaType`, som ender opp som `List<Integer>`.
        // Kotlin sin T vs T? forsvinner i Java-broen, og Jackson tillater null som element.
        // Resultat: en `List<Int>` med null-verdier — typesystemet i Kotlin lyver.
        // For fail-loud kontroll: bruk en DTO med non-nullable felt, da griper Kotlin-modulen inn (se [JacksonKotlinTest.null for non-nullable Kotlin-felt kaster]).
        @Suppress("UNCHECKED_CAST")
        val resultat = deserializeList<Int>("""[1,null,3]""") as List<Int?>
        resultat shouldBe listOf(1, null, 3)
    }

    @Test
    fun `List receiver-serialize beholder JSON null i output`() {
        listOf(1, null, 3).serialize() shouldBe """[1,null,3]"""
    }

    // --- List / Map med generic element-typer -----------------------------------------------

    @Test
    fun `deserializeList og deserializeMap for primitiver`() {
        deserializeList<Int>("[1,2,3]") shouldBe listOf(1, 2, 3)
        deserializeMap<String, Int>("""{"a":1,"b":2}""") shouldBe mapOf("a" to 1, "b" to 2)
        // Receiver-formen String.deserializeList med default-flagg dekker en separat code-path enn free-function.
        "[1,2,3]".deserializeList<Int>() shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `deserializeList bevarer nestet generic-type-info`() {
        deserializeList<List<Int>>("""[[1,2],[3,4]]""") shouldBe listOf(listOf(1, 2), listOf(3, 4))
    }

    @Test
    fun `deserializeMap bevarer nestet generic-type-info`() {
        deserializeMap<String, List<Int>>(
            """{"a":[1,2],"b":[3,4]}""",
        ) shouldBe mapOf("a" to listOf(1, 2), "b" to listOf(3, 4))
    }

    @Test
    fun `List receiver-serialize bevarer element-type`() {
        listOf(listOf(1, 2), listOf(3, 4)).serialize() shouldBe """[[1,2],[3,4]]"""
        listOf(Punkt(x = 1, y = 2)).serialize() shouldBe """[{"x":1,"y":2}]"""
    }

    // --- lesTre ------------------------------------------------------------------------------

    @Test
    fun `lesTre gir JsonNode-tilgang til vilkårlig struktur`() {
        val node = lesTre("""{"navn":"x","tall":[1,2,3]}""")
        node.get("navn").asString() shouldBe "x"
        node.get("tall").size() shouldBe 3
        node.get("tall").get(1).asInt() shouldBe 2
    }
}

private data class Punkt(val x: Int, val y: Int)
