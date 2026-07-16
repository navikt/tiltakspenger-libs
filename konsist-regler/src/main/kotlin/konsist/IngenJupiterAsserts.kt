package no.nav.tiltakspenger.libs.konsist

import com.lemonappdev.konsist.api.container.KoScope

/**
 * Vi bruker Kotest assertions.
 * Jupiter assertion-importer som `org.junit.jupiter.api.Assertions` og `org.junit.jupiter.api.assertThrows` skal ikke brukes.
 *
 * Kjøres typisk med `scopeFromTest()`.
 */
object IngenJupiterAsserts {

    fun brudd(scope: KoScope): List<String> = scope.kildefiler().flatMap { file ->
        file.imports
            .filter { import ->
                import.name == "org.junit.jupiter.api.Assertions" ||
                    import.name.startsWith("org.junit.jupiter.api.Assertions.") ||
                    import.name.startsWith("org.junit.jupiter.api.assert")
            }
            .map { import -> "${file.path}: ${import.name}" }
    }

    fun assert(scope: KoScope) = assertIngenBrudd(
        brudd(scope),
        "Bruk Kotest assertions (io.kotest.matchers.* / io.kotest.assertions.*). Følgende Jupiter Assertions-importer er ikke tillatt.",
    )
}
