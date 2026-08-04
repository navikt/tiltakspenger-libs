package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Vi bruker Jackson 3 (`tools.jackson.*`).
 * Jackson 2 (`com.fasterxml.jackson.*`) ligger gjerne på classpath transitivt via tredjeparts-libs, men skal ikke brukes direkte.
 * Eneste lovlige unntak er `com.fasterxml.jackson.annotation.*` — annotasjons-artefakten deles mellom Jackson 2 og 3 og brukes også av Jackson 3.
 *
 * Kalleren velger scope: `scopeFromProduction()` for kun produksjonskode, `scopeFromProject()` for alt.
 */
object IngenJackson2 {

    /** Importprefiksene som er Jackson 2; et repo som drar inn en annen Jackson 2-artefakt legger prefikset til. */
    val standardForbudtePrefikser = setOf("com.fasterxml.jackson.")

    /** Unntakene innenfor de forbudte prefiksene — annotasjons-artefakten deles mellom Jackson 2 og 3. */
    val standardTillattePrefikser = setOf("com.fasterxml.jackson.annotation.")

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
        "Bruk Jackson 3 (tools.jackson.*). Følgende Jackson 2-importer (com.fasterxml.jackson.*, unntatt .annotation) er ikke tillatt.",
    )
}
