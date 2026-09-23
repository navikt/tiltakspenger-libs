package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Vi bruker JUnit 5 (Jupiter).
 * JUnit 4 (`junit.framework.*` og `org.junit.*` utenom `org.junit.jupiter.*` / `org.junit.platform.*`) skal ikke brukes i testkode.
 *
 * Kjøres typisk med `scopeFromTest()`.
 */
object IngenJUnit4 {

    /** Importprefiksene som er JUnit 4; et repo med en egen JUnit 4-avlegger legger prefikset til. */
    val standardForbudtePrefikser = setOf("junit.framework.", "org.junit.")

    /** Unntakene innenfor de forbudte prefiksene — Jupiter og platform er JUnit 5. */
    val standardTillattePrefikser = setOf("org.junit.jupiter.", "org.junit.platform.")

    fun brudd(
        scope: KoScope,
        ekstraForbudtePrefikser: Set<String> = emptySet(),
        ekstraTillattePrefikser: Set<String> = emptySet(),
    ): List<String> {
        val forbudte = standardForbudtePrefikser + ekstraForbudtePrefikser
        val tillatte = standardTillattePrefikser + ekstraTillattePrefikser
        return scope.kildefiler().flatMap { file ->
            file.imports
                .filter { import -> forbudte.any { prefiks -> import.name.startsWith(prefiks) } }
                .filterNot { import -> tillatte.any { prefiks -> import.name.startsWith(prefiks) } }
                .map { import -> "${file.path}: ${import.name}" }
        }
    }

    fun assert(
        scope: KoScope,
        ekstraForbudtePrefikser: Set<String> = emptySet(),
        ekstraTillattePrefikser: Set<String> = emptySet(),
    ) = assertIngenBrudd(
        brudd(scope, ekstraForbudtePrefikser, ekstraTillattePrefikser),
        "Bruk JUnit 5 (org.junit.jupiter.*). Følgende JUnit 4-importer er ikke tillatt.",
    )
}
