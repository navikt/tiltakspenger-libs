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

    fun brudd(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.imports
            .filter { import -> import.name.startsWith("com.fasterxml.jackson.") }
            .filterNot { import -> import.name.startsWith("com.fasterxml.jackson.annotation.") }
            .map { import -> "${file.path}: ${import.name}" }
    }

    fun assert(scope: KoScope) = assertIngenBrudd(
        brudd(scope),
        "Bruk Jackson 3 (tools.jackson.*). Følgende Jackson 2-importer (com.fasterxml.jackson.*, unntatt .annotation) er ikke tillatt.",
    )
}
